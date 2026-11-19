// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_ts_core.h"
#include "native_fx2_reload_plan.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

struct request { struct tbs_ts_meta meta; uint64_t order; int pending, cancelled; };
struct model {
    struct tbs_ts stream;
    struct request *requests[TBS_TS_MAX_DEPTH];
    int configured_depth;
    int allocations, releases, closed, submissions, reaps, discards;
    int alloc_failure, submit_failure, reap_failure, wait_failure, bad_identity, bad_length;
    int error_at, stop_at, stuck_drain, draining, idle, consume, stop_on_submit, duplicate_alloc;
    int bad_clock, frozen_clock, drain_reap_error, waits;
    int64_t now, rate, next_at, deadline, received;
};
static unsigned char pattern(uint64_t at) { return at % 188 == 0 ? 0x47 : (unsigned char)(at % 251); }
static void *allocate(void *opaque) {
    struct model *f = opaque;
    if (f->alloc_failure == f->allocations + 1) return NULL;
    if (f->duplicate_alloc && f->allocations) return f->requests[0];
    struct request *r = calloc(1, sizeof(*r)); assert(r);
    f->requests[f->allocations++] = r; return r;
}
static void release(void *opaque, void *request) {
    struct model *f = opaque;
    assert(f->closed); // Kernel ownership must end before free, even on failed drain.
    for (int i = 0; i < f->allocations; ++i) if (f->requests[i] == request) f->requests[i] = NULL;
    ++f->releases; free(request);
}
static void prepare(void *request, unsigned char *buffer, int size, void *context) {
    struct request *r = request; assert(!r->pending);
    r->meta = (struct tbs_ts_meta){-EINPROGRESS, 0, size, 3, 0x82, 0, 0, 0, buffer, context};
    r->cancelled = 0;
}
static struct tbs_ts_call submit(void *opaque, void *request) {
    struct model *f = opaque; struct request *r = request;
    ++f->submissions;
    assert(!f->closed && f->allocations == f->stream.depth && f->stream.ring);
    if (f->submissions == f->submit_failure) return (struct tbs_ts_call){-1, ENOMEM};
    r->pending = 1; r->order = (uint64_t)f->submissions;
    if (f->stop_on_submit) tbs_ts_stop(&f->stream);
    return (struct tbs_ts_call){0, 0};
}
static void consume(struct model *f) {
    if (!f->consume || f->draining) return;
    const unsigned char *bytes;
    int n;
    while ((n = tbs_ts_peek(&f->stream, &bytes)) > 0) {
        for (int i = 0; i < n; ++i) assert(bytes[i] == pattern((uint64_t)f->received + (unsigned)i));
        f->received += n;
        tbs_ts_consume(&f->stream);
    }
}
static struct tbs_ts_call reap(void *opaque, void **request) {
    struct model *f = opaque;
    consume(f);
    if (f->draining && f->drain_reap_error) return (struct tbs_ts_call){-1, EIO};
    if (f->draining && f->stuck_drain) return (struct tbs_ts_call){-1, EAGAIN};
    if (!f->draining && f->reap_failure && f->reaps + 1 == f->reap_failure)
        return (struct tbs_ts_call){-1, ENODEV};
    if (!f->draining && (f->idle || f->now < f->next_at)) return (struct tbs_ts_call){-1, EAGAIN};
    struct request *best = NULL;
    for (int i = 0; i < f->allocations; ++i) {
        struct request *r = f->requests[i];
        if (r && r->pending && (!best || r->order < best->order)) best = r;
    }
    if (!best) return (struct tbs_ts_call){-1, EAGAIN};
    best->pending = 0;
    best->meta.actual = TBS_TS_CHUNK;
    best->meta.status = f->draining ? -ECONNRESET : 0;
    ++f->reaps; *request = best;
    if (!f->draining) {
        uint64_t offset = (uint64_t)(f->reaps - 1) * TBS_TS_CHUNK;
        for (int i = 0; i < TBS_TS_CHUNK; ++i) ((unsigned char *)best->meta.buffer)[i] = pattern(offset + (unsigned)i);
        f->next_at = (int64_t)(f->reaps + 1) * TBS_TS_CHUNK * 1000000000LL / f->rate;
        if (f->error_at == f->reaps) best->meta.status = -EOVERFLOW;
        if (f->bad_length) best->meta.actual = TBS_TS_CHUNK + 1;
        if (f->bad_identity) { best->pending = 1; *request = (void *)(uintptr_t)1; }
        if (f->stop_at == f->reaps) tbs_ts_stop(&f->stream);
    }
    return (struct tbs_ts_call){0, 0};
}
static struct tbs_ts_call discard(void *opaque, void *request) {
    struct model *f = opaque; struct request *r = request;
    assert(r->pending); r->cancelled = 1; ++f->discards; f->draining = 1;
    return (struct tbs_ts_call){-1, EINVAL}; // Reap/close still owns final cancellation proof.
}
static void metadata(void *request, struct tbs_ts_meta *m) { *m = ((struct request *)request)->meta; }
static int64_t nanos(void *opaque) {
    struct model *f = opaque;
    return f->bad_clock ? -1 : f->frozen_clock ? 0 : f->now;
}
static struct tbs_ts_call wait_ready(void *opaque, int millis) {
    struct model *f = opaque; ++f->waits;
    f->now += (int64_t)millis * 1000000;
    if (!f->draining && f->now >= f->deadline) tbs_ts_stop(&f->stream);
    if (!f->draining && f->wait_failure) return (struct tbs_ts_call){-1, ENODEV};
    return (struct tbs_ts_call){0, 0};
}
static void close_owned(void *opaque) {
    struct model *f = opaque; assert(!f->closed); ++f->closed;
    for (int i = 0; i < f->allocations; ++i) if (f->requests[i]) f->requests[i]->pending = 0;
}
static void init(struct model *f) {
    f->rate = f->rate ? f->rate : 3437500;
    f->deadline = f->deadline ? f->deadline : 10000000000LL;
    f->next_at = TBS_TS_CHUNK * 1000000000LL / f->rate;
    struct tbs_ts_ops ops = {f, allocate, release, prepare, submit, reap, discard, metadata, nanos, wait_ready, close_owned};
    int rc = f->configured_depth ? tbs_ts_init_config(&f->stream, ops, f->configured_depth)
        : tbs_ts_init(&f->stream, ops);
    assert(rc == ((f->alloc_failure || f->duplicate_alloc) ? -ENOMEM : 0));
}
static void finish(struct model *f, int reason) {
    assert(atomic_load(&f->stream.stop) == reason);
    assert(f->closed == 1 && f->stream.outstanding == 0);
    assert(tbs_ts_destroy(&f->stream) == 0);
    assert(tbs_ts_destroy(&f->stream) == 0);
    assert(f->allocations == f->releases);
}
static void rate_test(int64_t rate, int depth) {
    struct model f = {.rate = rate, .consume = 1, .configured_depth = depth}; init(&f);
    assert(tbs_ts_run(&f.stream) == 0);
    assert(f.received == (int64_t)f.stream.bytes);
    assert(f.received >= rate * 10 - 2 * TBS_TS_CHUNK && f.received <= rate * 10);
    assert(!f.stream.drain_failed && f.submissions > 2000);
    unsigned records = atomic_load(&f.stream.completion_count);
    assert(records > 2000 && records < TBS_TS_COMPLETIONS);
    assert(!atomic_load(&f.stream.completion_overflow) && !atomic_load(&f.stream.completion_clock_invalid));
    for (unsigned i = 0; i < records; ++i) {
        assert(f.stream.completions[i].status == 0 && f.stream.completions[i].length == TBS_TS_CHUNK);
        assert(f.stream.completions[i].monotonic_nanos >= 0);
        if (i) assert(f.stream.completions[i].monotonic_nanos >= f.stream.completions[i - 1].monotonic_nanos);
    }
    assert(atomic_load(&f.stream.begin_nanos) == 0);
    assert(atomic_load(&f.stream.end_nanos) == f.deadline);
    printf("PASS cadence %.1f Mbps depth=%d: %lld bytes, 10s simulated, no loss, bounded telemetry\n", rate * 8.0 / 1000000, depth, (long long)f.received);
    finish(&f, TBS_TS_STOP);
}
static void *run_thread(void *opaque) {
    assert(tbs_ts_run(&((struct model *)opaque)->stream) == 0); return NULL;
}
static void concurrent_stop_test(void) {
    struct model f = {.idle = 1, .deadline = INT64_MAX}; init(&f);
    pthread_t worker; assert(pthread_create(&worker, NULL, run_thread, &f) == 0);
    while (atomic_load(&f.stream.state) != 1) { }
    assert(tbs_ts_destroy(&f.stream) == -EBUSY);
    tbs_ts_stop(&f.stream); tbs_ts_stop(&f.stream);
    assert(pthread_join(worker, NULL) == 0);
    finish(&f, TBS_TS_STOP);
}
static void telemetry_publication_test(void) {
    struct model f = {.consume = 1, .configured_depth = 32}; init(&f);
    pthread_t worker; assert(pthread_create(&worker, NULL, run_thread, &f) == 0);
    unsigned seen = 0;
    while (atomic_load_explicit(&f.stream.state, memory_order_acquire) != 2) {
        unsigned count = atomic_load_explicit(&f.stream.completion_count, memory_order_acquire);
        for (; seen < count; ++seen) {
            assert(f.stream.completions[seen].length == TBS_TS_CHUNK);
            assert(f.stream.completions[seen].status == 0);
            if (seen) assert(f.stream.completions[seen].monotonic_nanos >= f.stream.completions[seen - 1].monotonic_nanos);
        }
    }
    assert(pthread_join(worker, NULL) == 0);
    assert(atomic_load(&f.stream.completion_count) > 2000);
    finish(&f, TBS_TS_STOP);
}
int main(void) {
    telemetry_publication_test();
    rate_test(3437500, 16); rate_test(4450000, 16);
    rate_test(3437500, 32); rate_test(4450000, 32);
    for (int i = 0; i < 100; ++i) concurrent_stop_test();
    struct model wrap = {.consume = 1, .stop_at = 400}; init(&wrap);
    atomic_store(&wrap.stream.head, UINT32_MAX - 128);
    atomic_store(&wrap.stream.tail, UINT32_MAX - 128);
    wrap.stream.sequence = wrap.stream.expected = UINTPTR_MAX - 128;
    tbs_ts_run(&wrap.stream);
    assert(wrap.received == (int64_t)wrap.stream.bytes && atomic_load(&wrap.stream.head) < 400);
    finish(&wrap, TBS_TS_STOP);
    struct model trace_full = {.consume = 1, .stop_at = 2}; init(&trace_full);
    /* Exercise capacity boundary without allocating unbounded history. */
    atomic_store(&trace_full.stream.completion_count, TBS_TS_COMPLETIONS - 1);
    tbs_ts_run(&trace_full.stream);
    assert(atomic_load(&trace_full.stream.completion_count) == TBS_TS_COMPLETIONS);
    assert(atomic_load(&trace_full.stream.completion_overflow));
    assert(trace_full.stream.completions[TBS_TS_COMPLETIONS - 1].length == TBS_TS_CHUNK);
    finish(&trace_full, TBS_TS_STOP);
    struct model overflow = {0}; init(&overflow); tbs_ts_run(&overflow.stream);
    assert(overflow.stream.bytes == (uint64_t)TBS_TS_RING_SLOTS * TBS_TS_CHUNK);
    const unsigned char *bytes; assert(tbs_ts_peek(&overflow.stream, &bytes) == -EIO);
    finish(&overflow, TBS_TS_OVERFLOW);
    struct model error = {.error_at = 4, .consume = 1}; init(&error); tbs_ts_run(&error.stream);
    assert(error.stream.status == -EOVERFLOW && error.submissions == 19);
    assert(atomic_load(&error.stream.completion_count) == 4);
    assert(error.stream.completions[3].status == -EOVERFLOW);
    finish(&error, TBS_TS_STATUS);
    struct model idle = {.idle = 1}; init(&idle); tbs_ts_stop(&idle.stream); tbs_ts_run(&idle.stream);
    assert(!idle.submissions); finish(&idle, TBS_TS_STOP);
    struct model stop = {.idle = 1, .deadline = 2000000}; init(&stop); tbs_ts_run(&stop.stream);
    assert(stop.discards == 16 && !stop.stream.drain_failed); finish(&stop, TBS_TS_STOP);
    struct model startup = {.stop_on_submit = 1}; init(&startup); tbs_ts_run(&startup.stream);
    assert(startup.submissions == 1 && startup.discards == 1); finish(&startup, TBS_TS_STOP);
    struct model stuck = {.error_at = 1, .stuck_drain = 1}; init(&stuck); tbs_ts_run(&stuck.stream);
    assert(stuck.stream.drain_failed && stuck.now <= 1010000000LL); finish(&stuck, TBS_TS_STATUS);
    struct model frozen = {.error_at = 1, .stuck_drain = 1, .frozen_clock = 1}; init(&frozen); tbs_ts_run(&frozen.stream);
    assert(frozen.stream.drain_failed && frozen.waits < 520); finish(&frozen, TBS_TS_STATUS);
    struct model clock = {.error_at = 1, .bad_clock = 1}; init(&clock); tbs_ts_run(&clock.stream);
    assert(clock.stream.drain_failed && atomic_load(&clock.stream.completion_clock_invalid));
    assert(clock.stream.completions[0].monotonic_nanos == -1); finish(&clock, TBS_TS_STATUS);
    struct model drain_error = {.error_at = 1, .drain_reap_error = 1}; init(&drain_error); tbs_ts_run(&drain_error.stream);
    assert(drain_error.stream.drain_failed); finish(&drain_error, TBS_TS_STATUS);
    struct model syscall = {.reap_failure = 1}; init(&syscall); tbs_ts_run(&syscall.stream); finish(&syscall, TBS_TS_SYSCALL);
    struct model submit_error = {.submit_failure = 5}; init(&submit_error); tbs_ts_run(&submit_error.stream);
    assert(submit_error.discards == 4); finish(&submit_error, TBS_TS_SYSCALL);
    struct model wait_error = {.idle = 1, .wait_failure = 1}; init(&wait_error); tbs_ts_run(&wait_error.stream); finish(&wait_error, TBS_TS_SYSCALL);
    struct model identity = {.bad_identity = 1}; init(&identity); tbs_ts_run(&identity.stream); finish(&identity, TBS_TS_IDENTITY);
    struct model length = {.bad_length = 1}; init(&length); tbs_ts_run(&length.stream); finish(&length, TBS_TS_LENGTH);
    for (int at = 1; at <= TBS_TS_DEPTH; ++at) {
        struct model allocation = {.alloc_failure = at}; init(&allocation); finish(&allocation, TBS_TS_RUNNING);
    }
    struct model duplicate = {.duplicate_alloc = 1}; init(&duplicate); finish(&duplicate, TBS_TS_RUNNING);
    struct model unused = {0}; init(&unused); assert(tbs_ts_destroy(&unused.stream) == 0);
    assert(unused.closed && unused.allocations == unused.releases);
    unsigned char image[8192] = {0}, reset;
    for (int ordinal = 1; ordinal <= 132; ++ordinal) {
        struct tbs_reload_step step;
        assert(tbs_reload_step(ordinal, image, sizeof(image), &reset, &step) == 0);
        assert(step.type == 0x40 && step.request == 0xa0 && !step.index && step.timeout == 2000);
        if (ordinal <= 2 || ordinal >= 131) {
            assert(step.value == ((ordinal & 1) ? 0x7f92 : 0xe600));
            assert(step.length == 1 && *step.payload == (ordinal <= 2));
        } else assert(step.value == (ordinal - 3) * 64 && step.payload == image + step.value && step.length == 64);
    }
    struct tbs_reload_step step;
    assert(tbs_reload_step(0, image, 8192, &reset, &step) < 0);
    assert(tbs_reload_step(133, image, 8192, &reset, &step) < 0);
    assert(tbs_reload_step(3, image, 8191, &reset, &step) < 0);
    puts("PASS stop/error/drain/overflow/allocation/identity/fixed132");
    return 0;
}
