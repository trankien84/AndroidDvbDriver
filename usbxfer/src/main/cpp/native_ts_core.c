// SPDX-License-Identifier: GPL-2.0-or-later
/* Continuous production variant of debug/native_usbfs_stream.c's submit/reap
 * state machine: no capture history, limits, file writer, or hot-path allocation. */
#include "native_ts_core.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>

static void fail(struct tbs_ts *s, int reason, int error) {
    int expected = TBS_TS_RUNNING;
    if (!s->error && error) s->error = error;
    atomic_compare_exchange_strong(&s->stop, &expected, reason);
}
void tbs_ts_stop(struct tbs_ts *s) {
    int expected = TBS_TS_RUNNING;
    atomic_compare_exchange_strong(&s->stop, &expected, TBS_TS_STOP);
}
static void close_owned(struct tbs_ts *s) {
    if (!s->closed) {
        s->ops.close(s->ops.opaque);
        s->closed = 1;
        s->outstanding = 0;
        for (int i = 0; i < s->depth; ++i) s->slots[i].pending = 0;
    }
}
int tbs_ts_init(struct tbs_ts *s, struct tbs_ts_ops ops) {
    return tbs_ts_init_config(s, ops, TBS_TS_DEPTH);
}
int tbs_ts_init_config(struct tbs_ts *s, struct tbs_ts_ops ops, int depth) {
    if (!s || (depth != 16 && depth != 32) || !ops.allocate || !ops.release || !ops.prepare || !ops.submit || !ops.reap
            || !ops.discard || !ops.metadata || !ops.nanos || !ops.wait || !ops.close) return -EINVAL;
    memset(s, 0, sizeof(*s));
    s->ops = ops; s->depth = depth;
    atomic_init(&s->begin_nanos, -1); atomic_init(&s->end_nanos, -1);
    atomic_init(&s->head, 0); atomic_init(&s->tail, 0);
    atomic_init(&s->stop, TBS_TS_RUNNING); atomic_init(&s->state, 0);
    atomic_init(&s->completion_count, 0); atomic_init(&s->completion_overflow, 0);
    atomic_init(&s->completion_clock_invalid, 0);
    s->last_completion_nanos = -1;
    s->sequence = s->expected = 1;
    if (!atomic_is_lock_free(&s->head) || !atomic_is_lock_free(&s->tail)
            || !atomic_is_lock_free(&s->stop)) {
        tbs_ts_destroy(s); return -ENOTSUP;
    }
    s->ring = malloc((size_t)TBS_TS_RING_SLOTS * TBS_TS_CHUNK);
    s->completions = calloc(TBS_TS_COMPLETIONS, sizeof(*s->completions));
    if (!s->ring || !s->completions) { tbs_ts_destroy(s); return -ENOMEM; }
    for (int i = 0; i < s->depth; ++i) {
        struct tbs_ts_slot *p = &s->slots[i];
        p->buffer = malloc(TBS_TS_CHUNK);
        p->request = ops.allocate(ops.opaque);
        for (int j = 0; j < i; ++j) if (p->request == s->slots[j].request) {
            p->request = NULL; break;
        }
        if (!p->buffer || !p->request) { tbs_ts_destroy(s); return -ENOMEM; }
    }
    return 0;
}
static int valid(struct tbs_ts_slot *p, struct tbs_ts_meta *m) {
    return m->requested == TBS_TS_CHUNK && m->type == 3 && m->endpoint == 0x82
        && m->packets == 0 && m->flags == 0 && m->stream_id == 0
        && m->buffer == p->buffer && m->context == (void *)p->sequence;
}
static int submit_slot(struct tbs_ts *s, int at) {
    if (atomic_load(&s->stop) != TBS_TS_RUNNING) return 0;
    struct tbs_ts_slot *p = &s->slots[at];
    if (p->pending) { fail(s, TBS_TS_IDENTITY, 0); return 0; }
    p->sequence = s->sequence++;
    s->ops.prepare(p->request, p->buffer, TBS_TS_CHUNK, (void *)p->sequence);
    struct tbs_ts_meta m;
    s->ops.metadata(p->request, &m);
    if (!valid(p, &m)) { fail(s, TBS_TS_IDENTITY, 0); return 0; }
    if (atomic_load(&s->stop) != TBS_TS_RUNNING) return 0;
    struct tbs_ts_call c = s->ops.submit(s->ops.opaque, p->request);
    if (c.result != 0) { fail(s, TBS_TS_SYSCALL, c.error); return 0; }
    p->pending = 1; ++s->outstanding;
    return 1;
}
/* Userspace observation after successful REAPURBNDELAY, not a kernel IRQ timestamp. */
static void record_completion(struct tbs_ts *s, const struct tbs_ts_meta *m) {
    unsigned count = atomic_load_explicit(&s->completion_count, memory_order_relaxed);
    if (count == TBS_TS_COMPLETIONS) {
        atomic_store_explicit(&s->completion_overflow, 1, memory_order_release);
        return;
    }
    int64_t time = s->ops.nanos(s->ops.opaque);
    if (time < 0 || time < s->last_completion_nanos)
        atomic_store_explicit(&s->completion_clock_invalid, 1, memory_order_release);
    s->last_completion_nanos = time;
    s->completions[count] = (struct tbs_ts_completion){time, m->status, m->actual};
    atomic_store_explicit(&s->completion_count, count + 1, memory_order_release);
}
/* Never dereference a pointer returned by the kernel until it matches our pool. */
static int reap_one(struct tbs_ts *s, struct tbs_ts_meta *m, int draining) {
    void *request = NULL;
    struct tbs_ts_call c = s->ops.reap(s->ops.opaque, &request);
    if (c.result < 0 && (c.error == EAGAIN || c.error == EINTR)) return -1;
    if (c.result != 0) { fail(s, TBS_TS_SYSCALL, c.error); return -2; }
    int at;
    for (at = 0; at < s->depth; ++at) if (s->slots[at].request == request) break;
    if (at == s->depth || !s->slots[at].pending) {
        fail(s, TBS_TS_IDENTITY, 0); return -2;
    }
    struct tbs_ts_slot *p = &s->slots[at];
    /* REAPURB returned ownership of this exact allocation even if metadata is bad. */
    p->pending = 0; --s->outstanding; ++s->completed;
    s->ops.metadata(request, m);
    if (!draining) record_completion(s, m);
    if (!valid(p, m) || m->status == -EINPROGRESS) {
        fail(s, TBS_TS_IDENTITY, 0); return -2;
    }
    return at;
}
static void drain(struct tbs_ts *s) {
    for (int i = 0; i < s->depth; ++i) if (s->slots[i].pending)
        s->ops.discard(s->ops.opaque, s->slots[i].request);
    int64_t begin = s->ops.nanos(s->ops.opaque);
    /* Both time and attempt caps bound cancellation even with a broken clock. */
    for (int i = 0; s->outstanding && i < 512; ++i) {
        int64_t time = s->ops.nanos(s->ops.opaque);
        if (begin < 0 || time < begin || time - begin >= 1000000000LL) break;
        struct tbs_ts_meta m;
        int at = reap_one(s, &m, 1);
        if (at == -2) break;
        if (at == -1) {
            struct tbs_ts_call c = s->ops.wait(s->ops.opaque, 2);
            if (c.result < 0 && c.error != EINTR) break;
        }
    }
    s->drain_failed = s->outstanding != 0;
    /* Unlike retaining debug quarantine allocations indefinitely, sole-fd close
     * synchronously kills pending usbfs URBs before any request/buffer is freed. */
    close_owned(s);
}
int tbs_ts_run(struct tbs_ts *s) {
    int expected = 0;
    if (!s || s->closed || !atomic_compare_exchange_strong(&s->state, &expected, 1)) return -EINVAL;
    atomic_store(&s->begin_nanos, s->ops.nanos(s->ops.opaque));
    s->last_completion_nanos = atomic_load(&s->begin_nanos);
    if (atomic_load(&s->begin_nanos) < 0) atomic_store(&s->completion_clock_invalid, 1);
    for (int i = 0; i < s->depth; ++i) if (!submit_slot(s, i)) break;
    while (atomic_load(&s->stop) == TBS_TS_RUNNING) {
        struct tbs_ts_meta m;
        int at = reap_one(s, &m, 0);
        if (at == -2) break;
        if (at == -1) {
            struct tbs_ts_call c = s->ops.wait(s->ops.opaque, 2);
            if (c.result < 0 && c.error != EINTR) fail(s, TBS_TS_SYSCALL, c.error);
            continue;
        }
        if (m.status != 0) { s->status = m.status; fail(s, TBS_TS_STATUS, 0); break; }
        if (m.actual < 0 || m.actual > TBS_TS_CHUNK) { fail(s, TBS_TS_LENGTH, 0); break; }
        if (s->slots[at].sequence != s->expected++) { fail(s, TBS_TS_IDENTITY, 0); break; }
        if (atomic_load(&s->stop) != TBS_TS_RUNNING) break;
        if (m.actual > 0) {
            unsigned head = atomic_load_explicit(&s->head, memory_order_relaxed);
            unsigned tail = atomic_load_explicit(&s->tail, memory_order_acquire);
            if (head - tail >= TBS_TS_RING_SLOTS) { fail(s, TBS_TS_OVERFLOW, 0); break; }
            unsigned index = head % TBS_TS_RING_SLOTS;
            memcpy(s->ring + (size_t)index * TBS_TS_CHUNK, s->slots[at].buffer, (size_t)m.actual);
            s->lengths[index] = m.actual;
            s->bytes += (unsigned)m.actual;
            atomic_store_explicit(&s->head, head + 1, memory_order_release);
        }
        if (!submit_slot(s, at)) break;
    }
    int64_t end = s->ops.nanos(s->ops.opaque);
    atomic_store(&s->end_nanos, end);
    if (end < atomic_load(&s->begin_nanos) || end < s->last_completion_nanos)
        atomic_store(&s->completion_clock_invalid, 1);
    drain(s);
    atomic_store_explicit(&s->state, 2, memory_order_release);
    return 0;
}
int tbs_ts_peek(struct tbs_ts *s, const unsigned char **bytes) {
    int stop = atomic_load(&s->stop);
    if (stop != TBS_TS_RUNNING) return stop == TBS_TS_STOP ? -ECANCELED : -EIO;
    unsigned tail = atomic_load_explicit(&s->tail, memory_order_relaxed);
    unsigned head = atomic_load_explicit(&s->head, memory_order_acquire);
    if (head == tail) return 0;
    unsigned at = tail % TBS_TS_RING_SLOTS;
    *bytes = s->ring + (size_t)at * TBS_TS_CHUNK;
    return s->lengths[at];
}
void tbs_ts_consume(struct tbs_ts *s) {
    unsigned tail = atomic_load_explicit(&s->tail, memory_order_relaxed);
    atomic_store_explicit(&s->tail, tail + 1, memory_order_release);
}
int tbs_ts_destroy(struct tbs_ts *s) {
    if (!s) return 0;
    if (atomic_load_explicit(&s->state, memory_order_acquire) == 1) return -EBUSY;
    close_owned(s);
    for (int i = 0; i < s->depth; ++i) {
        if (s->slots[i].request) s->ops.release(s->ops.opaque, s->slots[i].request);
        free(s->slots[i].buffer);
        s->slots[i].request = NULL; s->slots[i].buffer = NULL;
    }
    free(s->ring); s->ring = NULL;
    free(s->completions); s->completions = NULL;
    return 0;
}
