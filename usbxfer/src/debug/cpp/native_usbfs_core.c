// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_core.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>

static void record(struct tbs_core *c, int kind, int slot, struct tbs_syscall call,
                   int64_t before, int64_t after, int completed) {
    if (c->event_count == TBS_MAX_EVENTS) { c->stopped = c->uncertain = 1; return; }
    struct tbs_event *e = &c->events[c->event_count++];
    memset(e, 0, sizeof(*e));
    e->kind = kind; e->slot = slot; e->result = call.result; e->error = call.error;
    e->before = before; e->after = after; e->status = TBS_STATUS_UNKNOWN;
    e->requested = c->chunk_bytes; e->capacity = c->chunk_bytes; e->endpoint = 0x82;
    if (slot >= 0) {
        struct tbs_slot *s = &c->slots[slot];
        e->capacity = s->capacity;
        e->sequence = s->sequence;
        if (completed) {
            e->status = s->meta.status; e->actual = s->meta.actual;
            e->requested = s->meta.requested; e->endpoint = s->meta.endpoint;
            e->flags = s->meta.flags;
        }
    }
    if (before < 0 || after < before) c->stopped = c->uncertain = 1;
}

int tbs_init(struct tbs_core *c, struct tbs_ops ops, int budget) {
    return tbs_init_sized(c, ops, budget, TBS_CHUNK);
}

int tbs_init_sized(struct tbs_core *c, struct tbs_ops ops, int budget, int chunk_bytes) {
    if (!c || budget < TBS_DEPTH || budget > TBS_MAX_REQUESTS
            || (chunk_bytes != TBS_CHUNK && chunk_bytes != TBS_MAX_CHUNK)
            || budget > TBS_MAX_BYTES / chunk_bytes) return TBS_ERROR;
    memset(c, 0, sizeof(*c)); c->ops = ops; c->budget = budget; c->last_errno = -1;
    c->chunk_bytes = chunk_bytes;
    c->expected = 1; c->last_status = TBS_STATUS_UNKNOWN; c->last_slot = -1;
    c->last_return = TBS_STATUS_UNKNOWN;
    for (int i = 0; i < TBS_DEPTH; ++i) {
        c->slots[i].buffer = calloc(1, (size_t)chunk_bytes);
        if (!c->slots[i].buffer) { tbs_destroy(c); return TBS_ERROR; }
        c->slots[i].capacity = chunk_bytes;
    }
    return 0;
}

static int valid(const struct tbs_core *c, const struct tbs_slot *s) {
    const struct tbs_meta *m = &s->meta;
    return (c->chunk_bytes == TBS_CHUNK || c->chunk_bytes == TBS_MAX_CHUNK)
            && s->capacity == c->chunk_bytes && m->requested == s->capacity
            && m->actual >= 0 && m->actual <= m->requested
            && m->type == 3 && m->endpoint == 0x82
            && m->packets == 0 && m->stream_id == 0 && m->flags == 0
            && m->buffer == s->buffer && m->context == s;
}

int tbs_fill(struct tbs_core *c) {
    if (c->stopped || c->uncertain || c->released) return TBS_ERROR;
    /* One submission per call lets the caller recheck consent between syscalls. */
    for (int i = 0; i < TBS_DEPTH && c->attempted < c->budget; ++i) {
        struct tbs_slot *s = &c->slots[i];
        if (s->pending || s->ready) continue;
        void *request = c->ops.allocate(c->ops.opaque);
        if (!request) { c->stopped = 1; return TBS_ERROR; }
        c->requests[c->allocations++] = request;
        s->request = request; s->sequence = ++c->attempted;
        /* Never reuse a retired URB address; delayed duplicates cannot masquerade as a new submit. */
        c->ops.prepare(request, s->buffer, s->capacity, s);
        c->ops.metadata(request, &s->meta);
        if (!valid(c, s) || s->meta.status != -EINPROGRESS || s->meta.actual != 0) {
            c->stopped = c->uncertain = 1; return TBS_UNCERTAIN;
        }
        int64_t before = c->ops.nanos(c->ops.opaque);
        struct tbs_syscall call = c->ops.submit(c->ops.opaque, request);
        c->last_errno = call.error; c->last_return = call.result;
        if (call.result == 0) { s->pending = 1; c->outstanding++; c->submitted++; }
        else {
            c->stopped = 1;
            if (call.result != -1 || call.error <= 0) { s->pending = 1; c->outstanding++; c->uncertain = 1; }
        }
        record(c, TBS_SUBMIT, i, call, before, c->ops.nanos(c->ops.opaque), 0);
        return call.result == 0 && !c->uncertain ? 1 : TBS_ERROR;
    }
    return 0;
}

int tbs_poll(struct tbs_core *c) {
    if (c->released || c->uncertain || (!c->draining && c->stopped)) return TBS_ERROR;
    void *request = NULL;
    int64_t before = c->ops.nanos(c->ops.opaque);
    struct tbs_syscall call = c->ops.reap(c->ops.opaque, &request);
    if (!c->draining) { c->last_errno = call.error; c->last_return = call.result; }
    int64_t after = c->ops.nanos(c->ops.opaque);
    if (call.result != 0) {
        record(c, TBS_REAP, -1, call, before, after, 0);
        if (call.result == -1 && (call.error == EAGAIN || call.error == EINTR)) return TBS_WAIT;
        c->stopped = 1;
        return TBS_ERROR;
    }
    int slot = -1;
    for (int i = 0; i < TBS_DEPTH; ++i) if (c->slots[i].request == request && request) { slot = i; break; }
    if (slot < 0 || !c->slots[slot].pending) {
        record(c, TBS_REAP, slot, call, before, after, 0);
        c->stopped = c->uncertain = 1; return TBS_UNCERTAIN;
    }
    struct tbs_slot *s = &c->slots[slot];
    /* No kernel-supplied pointer is dereferenced before membership and pending checks. */
    c->ops.metadata(request, &s->meta);
    if (!c->draining) {
        c->last_status = s->meta.status; c->last_actual = s->meta.actual;
        c->last_slot = slot; c->last_sequence = s->sequence;
    }
    s->pending = 0; c->outstanding--; c->completed++;
    record(c, TBS_REAP, slot, call, before, after, 1);
    if (!valid(c, s) || s->meta.status > 0 || s->meta.status == -EINPROGRESS) {
        c->stopped = c->uncertain = 1; return TBS_UNCERTAIN;
    }
    if (c->draining) { c->cleanup_bytes += s->meta.actual; return 0; }
    s->ready = 1;
    if (s->meta.status != 0 || s->meta.actual == 0) c->stopped = 1;
    return 0;
}

int tbs_take(struct tbs_core *c, unsigned char *destination, size_t capacity) {
    if (c->released || c->uncertain || c->draining || !destination || capacity < (size_t)c->chunk_bytes) return TBS_ERROR;
    for (int i = 0; i < TBS_DEPTH; ++i) {
        struct tbs_slot *s = &c->slots[i];
        if (!s->ready || s->sequence != c->expected) continue;
        if (!valid(c, s)) { c->stopped = c->uncertain = 1; return TBS_UNCERTAIN; }
        if (s->meta.status != 0) return TBS_ERROR;
        if (s->meta.actual == 0) return 0;
        /* An error in any out-of-order completion invalidates delivery, even earlier ready data. */
        if (c->stopped) return TBS_ERROR;
        memcpy(destination, s->buffer, (size_t)s->meta.actual);
        int64_t now = c->ops.nanos(c->ops.opaque);
        record(c, TBS_COPY, i, (struct tbs_syscall){s->meta.actual, 0}, now, now, 1);
        c->delivered += s->meta.actual; s->ready = 0; c->expected++;
        return s->meta.actual;
    }
    if (c->stopped) return TBS_ERROR;
    return c->expected > c->budget ? TBS_LIMIT : TBS_WAIT;
}

void tbs_stop(struct tbs_core *c) {
    if (c->draining || c->released) return;
    c->stopped = c->draining = 1;
    for (int i = 0; i < TBS_DEPTH; ++i) {
        struct tbs_slot *s = &c->slots[i];
        if (s->ready) { c->cleanup_bytes += s->meta.actual; s->ready = 0; }
        if (!s->pending) continue;
        int64_t before = c->ops.nanos(c->ops.opaque);
        struct tbs_syscall call = c->ops.discard(c->ops.opaque, s->request);
        record(c, TBS_DISCARD, i, call, before, c->ops.nanos(c->ops.opaque), 0);
        /* Even successful DISCARD, EINVAL, or ENODEV does not prove a reap occurred. */
    }
}
void tbs_quarantine(struct tbs_core *c) { c->stopped = c->uncertain = 1; }
int tbs_destroy(struct tbs_core *c) {
    if (c->released) return 0;
    if (c->outstanding || c->uncertain) return TBS_UNCERTAIN;
    for (int i = 0; i < c->allocations; ++i) c->ops.release(c->ops.opaque, c->requests[i]);
    for (int i = 0; i < TBS_DEPTH; ++i) { free(c->slots[i].buffer); c->slots[i].buffer = NULL; }
    c->released = 1; return 0;
}
