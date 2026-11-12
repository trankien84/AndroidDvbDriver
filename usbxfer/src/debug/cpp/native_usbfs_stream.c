// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_stream.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>

static void publish(struct tbs_stream *s) {
    int64_t v[] = {s->attempted, s->submitted, s->completed, s->outstanding, s->delivered,
        s->cleanup, s->uncertain, s->last_errno, s->event_count, s->last_status,
        s->last_actual, s->last_slot, (int64_t)s->last_sequence, s->last_return};
    for (int i = 0; i < 14; ++i) atomic_store(&s->published[i], v[i]);
}
void tbs_stream_snapshot(const struct tbs_stream *s, int64_t v[14]) {
    for (int i = 0; i < 14; ++i) v[i] = atomic_load(&s->published[i]);
}
static void stop_with(struct tbs_stream *s, int reason) {
    int expected = TBS_STREAM_RUNNING;
    atomic_compare_exchange_strong(&s->stop, &expected, reason);
}
void tbs_stream_request_stop(struct tbs_stream *s) { stop_with(s, TBS_STREAM_STOP); }
static void error_with(struct tbs_stream *s, int reason, int error) {
    if (!s->first_errno && error) s->first_errno = error;
    stop_with(s, reason);
}
static int64_t now(struct tbs_stream *s) {
    int64_t value = s->ops.nanos(s->ops.opaque);
    if (value < 0 || (s->started && value < s->begin)) {
        error_with(s, TBS_STREAM_CLOCK, 0);
        return -1;
    }
    return value;
}
static void record(struct tbs_stream *s, int kind, int slot, struct tbs_stream_call call,
                   int64_t before, int64_t after, const struct tbs_stream_meta *m) {
    if (s->event_count >= s->event_capacity) {
        stop_with(s, TBS_STREAM_EVENT_CAP);
        return;
    }
    struct tbs_stream_slot *p = slot < 0 ? NULL : &s->slots[slot];
    int64_t v[] = {kind, slot, call.result, call.error, m ? m->status : TBS_STREAM_UNKNOWN,
        m ? m->actual : 0, m ? m->requested : s->chunk, s->chunk,
        m ? m->endpoint : 0x82, m ? m->flags : 0, p ? (int64_t)p->sequence : 0,
        before, after, s->outstanding};
    memcpy(s->events[s->event_count++].v, v, sizeof(v));
    if (before < 0 || after < before) error_with(s, TBS_STREAM_CLOCK, 0);
}
int tbs_stream_init(struct tbs_stream *s, struct tbs_stream_ops ops,
                    int depth, int chunk, int max_bytes, int duration_ms) {
    if (!s || depth < 8 || depth > 32 || chunk < 4096 || chunk > 65536 || chunk % 512
            || max_bytes < 1 || max_bytes > TBS_STREAM_MAX_BYTES || duration_ms < 1 || duration_ms > 10000
            || !ops.allocate || !ops.release || !ops.prepare || !ops.submit || !ops.reap
            || !ops.discard || !ops.metadata || !ops.nanos || !ops.wait) return -EINVAL;
    memset(s, 0, sizeof(*s));
    atomic_init(&s->stop, TBS_STREAM_RUNNING);
    for (int i = 0; i < 14; ++i) atomic_init(&s->published[i], 0);
    s->ops = ops; s->depth = depth; s->chunk = chunk;
    s->max_bytes = max_bytes; s->duration_ms = duration_ms;
    s->event_capacity = 2 * (max_bytes / 512 + depth) + 4 * depth + 64;
    s->next_sequence = s->expected = 1;
    s->first_status = 0; s->last_status = TBS_STREAM_UNKNOWN;
    s->last_slot = -1; s->min_outstanding = depth;
    s->data = malloc((size_t)max_bytes);
    s->events = calloc((size_t)s->event_capacity, sizeof(*s->events));
    s->resubmit = calloc((size_t)s->event_capacity, sizeof(*s->resubmit));
    if (!s->data || !s->events || !s->resubmit) goto fail;
    for (int i = 0; i < depth; ++i) {
        struct tbs_stream_slot *p = &s->slots[i];
        p->buffer = malloc((size_t)chunk); p->request = ops.allocate(ops.opaque);
        if (!p->buffer || !p->request) goto fail;
        for (int j = 0; j < i; ++j) if (p->request == s->slots[j].request) {
            p->request = NULL;
            goto fail;
        }
    }
    publish(s);
    return 0;
fail:
    tbs_stream_destroy(s);
    return -ENOMEM;
}
static int valid(struct tbs_stream *s, struct tbs_stream_slot *p, struct tbs_stream_meta *m) {
    return m->requested == s->chunk && m->type == 3 && m->endpoint == 0x82
        && m->packets == 0 && m->flags == 0 && m->stream_id == 0 && m->buffer == p->buffer
        && m->context == (void *)(uintptr_t)p->sequence;
}
static int active(struct tbs_stream *s) {
    int64_t t = now(s);
    if (t < 0) return 0;
    if (t - s->begin >= s->duration_ms * 1000000LL) stop_with(s, TBS_STREAM_DEADLINE);
    return atomic_load(&s->stop) == TBS_STREAM_RUNNING;
}
static int submit_slot(struct tbs_stream *s, int slot) {
    struct tbs_stream_slot *p = &s->slots[slot];
    if (!active(s)) return 0;
    if (p->pending || s->next_sequence > UINT32_MAX) {
        s->uncertain = 1; stop_with(s, TBS_STREAM_IDENTITY); return 0;
    }
    p->sequence = s->next_sequence++;
    s->ops.prepare(p->request, p->buffer, s->chunk, (void *)(uintptr_t)p->sequence);
    struct tbs_stream_meta m;
    s->ops.metadata(p->request, &m);
    if (!valid(s, p, &m)) { stop_with(s, TBS_STREAM_IDENTITY); return 0; }
    /* Reserved audit space includes every possible cancellation and final reap. */
    if (s->event_capacity - s->event_count <= 2 * s->depth + 2) {
        stop_with(s, TBS_STREAM_EVENT_CAP); return 0;
    }
    int64_t before = now(s);
    if (before < 0 || !active(s)) return 0;
    ++s->attempted;
    struct tbs_stream_call c = s->ops.submit(s->ops.opaque, p->request);
    int64_t after = now(s);
    s->last_errno = c.error; s->last_return = c.result;
    if (c.result == 0) { p->pending = 1; ++s->submitted; ++s->outstanding; }
    else error_with(s, TBS_STREAM_SYSCALL, c.error);
    record(s, 1, slot, c, before, after, NULL);
    publish(s);
    return c.result == 0 && atomic_load(&s->stop) == TBS_STREAM_RUNNING;
}
static void wait_ready(struct tbs_stream *s, int draining) {
    struct tbs_stream_call c = s->ops.wait(s->ops.opaque, 2);
    if (c.result < 0) {
        s->last_errno = c.error; s->last_return = c.result;
        if (!draining) error_with(s, TBS_STREAM_SYSCALL, c.error);
    }
}
/* Returns slot, -1 for EAGAIN, -2 for an untrusted completion/syscall failure. */
static int reap_one(struct tbs_stream *s, int draining, int64_t *reaped_at, struct tbs_stream_meta *m) {
    void *request = NULL;
    int64_t before = now(s);
    struct tbs_stream_call c = s->ops.reap(s->ops.opaque, &request);
    int64_t after = now(s);
    s->last_errno = c.error; s->last_return = c.result;
    if (c.result < 0 && c.error == EAGAIN) return -1;
    if (c.result != 0) {
        record(s, 2, -1, c, before, after, NULL);
        if (!draining) error_with(s, TBS_STREAM_SYSCALL, c.error);
        return -2;
    }
    int slot = -1;
    for (int i = 0; i < s->depth; ++i) if (s->slots[i].request == request) { slot = i; break; }
    if (slot < 0 || !s->slots[slot].pending) {
        record(s, 2, -1, c, before, after, NULL);
        s->uncertain = 1; stop_with(s, TBS_STREAM_IDENTITY); return -2;
    }
    struct tbs_stream_slot *p = &s->slots[slot];
    s->ops.metadata(request, m);
    if (!valid(s, p, m) || m->status == -EINPROGRESS) {
        record(s, 2, slot, c, before, after, m);
        s->uncertain = 1; stop_with(s, TBS_STREAM_IDENTITY); return -2;
    }
    p->pending = 0; --s->outstanding; ++s->completed;
    s->last_status = m->status; s->last_actual = m->actual;
    s->last_slot = slot; s->last_sequence = p->sequence;
    record(s, 2, slot, c, before, after, m);
    *reaped_at = after;
    if (!draining && p->sequence != s->expected++) {
        s->uncertain = 1; stop_with(s, TBS_STREAM_IDENTITY); return -2;
    }
    return slot;
}
static void drain(struct tbs_stream *s) {
    int64_t begin = now(s);
    for (int i = 0; i < s->depth; ++i) if (s->slots[i].pending) {
        int64_t before = now(s);
        if (begin < 0 || before < begin || before - begin >= 1000000000LL) break;
        struct tbs_stream_call c = s->ops.discard(s->ops.opaque, s->slots[i].request);
        record(s, 3, i, c, before, now(s), NULL);
    }
    /* Iteration cap also bounds a faulty/stalled host clock; no forced free. */
    for (int tries = 0; s->outstanding && tries < 65536; ++tries) {
        int64_t t = now(s);
        if (begin < 0 || t < begin || t - begin >= 1000000000LL) break;
        struct tbs_stream_meta m; int64_t reaped_at;
        int slot = reap_one(s, 1, &reaped_at, &m);
        if (slot >= 0 && m.actual > 0 && m.actual <= s->chunk) s->cleanup += m.actual;
        else if (slot < 0) {
            if (slot == -2) break;
            wait_ready(s, 1);
        }
    }
    if (s->outstanding) s->uncertain = 1;
    publish(s);
}
static int compare_latency(const void *a, const void *b) {
    int64_t x = *(const int64_t *)a, y = *(const int64_t *)b;
    return (x > y) - (x < y);
}
int tbs_stream_run(struct tbs_stream *s) {
    if (!s || s->started || s->released) return -EINVAL;
    s->begin = now(s); s->started = 1; s->last_progress = s->begin;
    if (s->begin < 0) stop_with(s, TBS_STREAM_CLOCK);
    for (int i = 0; i < s->depth; ++i) if (!submit_slot(s, i)) break;
    while (active(s)) {
        int64_t reaped_at; struct tbs_stream_meta m;
        int slot = reap_one(s, 0, &reaped_at, &m);
        if (slot < 0) {
            if (slot == -2) break;
            wait_ready(s, 0); continue;
        }
        if (s->outstanding < s->min_outstanding) s->min_outstanding = s->outstanding;
        if (!s->outstanding) { ++s->zero_outstanding_count; ++s->starvation_count; }
        /* Horizon is a risk indicator under an assumed rate, never proof of hardware starvation.
         * Completed-but-unreaped requests are invisible to the submitted-minus-reaped counter. */
        int64_t horizon = (int64_t)s->depth * s->chunk * 1000000000LL / TBS_STREAM_RATE;
        if (reaped_at - s->last_progress > horizon) { ++s->horizon_count; ++s->starvation_count; }
        s->last_progress = reaped_at;
        if (m.status != 0) {
            if (s->first_status == 0) s->first_status = m.status;
            stop_with(s, TBS_STREAM_STATUS); break;
        }
        if (m.actual <= 0 || m.actual > s->chunk) { stop_with(s, TBS_STREAM_LENGTH); break; }
        if (!active(s)) break;
        int count = m.actual;
        if (count > s->max_bytes - s->delivered) count = s->max_bytes - s->delivered;
        memcpy(s->data + s->delivered, s->slots[slot].buffer, (size_t)count);
        s->delivered += count;
        if (s->delivered >= s->max_bytes) { stop_with(s, TBS_STREAM_BYTE_CAP); break; }
        if (!submit_slot(s, slot)) break;
        int64_t latency = now(s) - reaped_at;
        if (latency < 0) { stop_with(s, TBS_STREAM_CLOCK); break; }
        s->resubmit[s->latency_count++] = latency;
        if (latency > s->max_resubmit) s->max_resubmit = latency;
        publish(s);
    }
    int64_t end = now(s);
    s->elapsed = end >= s->begin ? end - s->begin : 0;
    drain(s);
    qsort(s->resubmit, (size_t)s->latency_count, sizeof(*s->resubmit), compare_latency);
    if (s->latency_count) {
        int mid = s->latency_count / 2;
        s->median_resubmit = s->resubmit[mid];
        if (!(s->latency_count % 2))
            s->median_resubmit = s->resubmit[mid - 1] + (s->resubmit[mid] - s->resubmit[mid - 1]) / 2;
    }
    s->finished = 1; publish(s);
    return 0;
}
int tbs_stream_destroy(struct tbs_stream *s) {
    if (!s || s->released) return 0;
    if (s->outstanding || s->uncertain) return -EBUSY;
    for (int i = 0; i < s->depth; ++i) {
        if (s->slots[i].request) s->ops.release(s->ops.opaque, s->slots[i].request);
        free(s->slots[i].buffer);
        s->slots[i].request = NULL; s->slots[i].buffer = NULL;
    }
    free(s->data); free(s->events); free(s->resubmit);
    s->data = NULL; s->events = NULL; s->resubmit = NULL; s->released = 1;
    return 0;
}
const char *tbs_stream_reason_name(int reason) {
    static const char *const names[] = {"RUNNING", "STOP", "DEADLINE", "BYTE_LIMIT", "COMPLETION_STATUS",
        "INVALID_LENGTH", "SYSCALL", "IDENTITY", "EVENT_CAPACITY", "CLOCK"};
    return reason >= 0 && reason <= TBS_STREAM_CLOCK ? names[reason] : "UNKNOWN";
}
