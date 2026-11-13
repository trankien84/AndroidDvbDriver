// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_stream.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct request {
    struct tbs_stream_meta meta;
    int active, ready, cancelled, filled;
    int64_t available;
};
struct model {
    struct tbs_stream stream;
    struct request *requests[32];
    int allocations, frees, fail_alloc_at, duplicate_alloc, submissions, discards, reaps;
    int fail_submit_at, fail_reap_at, fail_wait, status_at, status, bad_at, bad_kind;
    int stop_at, stuck_drain, discard_error, idle, draining, foreign;
    int scripted, short_bytes, consume_every, writer_map_index;
    int64_t cadence, writer_bytes, writer_chunks;
    int64_t now, produced, dropped, latency, jitter, prior_available, syscall_cost, stop_time;
    unsigned char fifo[8192];
    int fifo_size, fifo_count, fifo_head;
};
static int tests;
#define MODEL_RATE 3437500LL /* Synthetic source cadence only, never a runtime starvation assumption. */
static unsigned char ts_byte(int64_t index) {
    int offset = (int)(index % 188); int64_t packet = index / 188;
    if (!offset) return 0x47;
    if (offset == 1) return 0;
    if (offset == 2) return 0x20;
    if (offset == 3) return (unsigned char)(0x10 | (packet & 15));
    return (unsigned char)(0x80 | ((packet + offset) & 0x3f));
}
static struct request *next_active(struct model *f) {
    struct request *best = NULL;
    for (int i = 0; i < f->allocations; ++i) {
        struct request *r = f->requests[i];
        if (r && r->active && (!best || (uintptr_t)r->meta.context < (uintptr_t)best->meta.context)) best = r;
    }
    return best;
}
static void push_byte(struct model *f, unsigned char byte, int64_t at) {
    struct request *r = next_active(f);
    if (r) {
        ((unsigned char *)r->meta.buffer)[r->filled++] = byte;
        if (r->filled == r->meta.requested) {
            r->active = 0; r->ready = 1; r->meta.actual = r->filled; r->meta.status = 0;
            int64_t jitter = f->jitter ? (int64_t)((uintptr_t)r->meta.context % 7) * f->jitter / 6 : 0;
            r->available = at + f->latency + jitter;
            if (r->available <= f->prior_available) r->available = f->prior_available + 1;
            f->prior_available = r->available;
        }
    } else if (f->fifo_count < f->fifo_size) {
        f->fifo[(f->fifo_head + f->fifo_count++) % f->fifo_size] = byte;
    } else ++f->dropped;
}
static void advance(struct model *f, int64_t duration) {
    int64_t target = f->now + duration;
    if (!f->idle) {
        int64_t count = target * MODEL_RATE / 1000000000LL;
        while (f->produced < count) {
            int64_t index = f->produced++;
            push_byte(f, ts_byte(index), (index + 1) * 1000000000LL / MODEL_RATE);
        }
    }
    f->now = target;
    if ((f->stop_time && f->now >= f->stop_time) || (f->stop_at && f->reaps >= f->stop_at))
        tbs_stream_request_stop(&f->stream);
}
static void *allocate(void *opaque) {
    struct model *f = opaque;
    if (f->fail_alloc_at == f->allocations + 1) return NULL;
    if (f->duplicate_alloc && f->allocations) return f->requests[0];
    struct request *r = calloc(1, sizeof(*r)); assert(r);
    f->requests[f->allocations++] = r;
    return r;
}
static void release(void *opaque, void *request) {
    struct model *f = opaque;
    for (int i = 0; i < f->allocations; ++i) if (f->requests[i] == request) f->requests[i] = NULL;
    ++f->frees; free(request);
}
static void prepare(void *request, unsigned char *buffer, int capacity, void *context) {
    struct request *r = request;
    assert(!r->active && !r->ready);
    r->meta = (struct tbs_stream_meta){-EINPROGRESS, 0, capacity, 3, 0x82, 0, 0, 0, buffer, context};
    r->filled = r->cancelled = 0;
}
static struct tbs_stream_call submit(void *opaque, void *request) {
    struct model *f = opaque; struct request *r = request;
    assert((f->stream.data || f->stream.ring) && f->stream.events && f->stream.resubmit);
    assert(f->stream.depth == f->allocations);
    ++f->submissions;
    advance(f, f->syscall_cost);
    if (f->fail_submit_at == f->submissions) return (struct tbs_stream_call){-1, ENOMEM};
    r->active = 1;
    while (f->fifo_count && r->active) {
        unsigned char byte = f->fifo[f->fifo_head];
        f->fifo_head = (f->fifo_head + 1) % f->fifo_size; --f->fifo_count;
        push_byte(f, byte, f->now);
    }
    return (struct tbs_stream_call){0, 0};
}
static void consume_writer(struct model *f) {
    const unsigned char *bytes;
    int count;
    while ((count = tbs_stream_writer_peek(&f->stream, &bytes)) > 0) {
        while (f->writer_map_index < f->stream.mapping_count && f->stream.mappings[f->writer_map_index][4])
            ++f->writer_map_index;
        assert(f->writer_map_index < f->stream.mapping_count);
        int64_t *m = f->stream.mappings[f->writer_map_index++];
        assert(m[2] == f->writer_bytes && m[3] == count);
        assert(bytes[0] == (unsigned char)((m[0] - 1) & 255));
        f->writer_bytes += count; ++f->writer_chunks;
        tbs_stream_writer_consume(&f->stream);
    }
}
static struct tbs_stream_call reap(void *opaque, void **request) {
    struct model *f = opaque;
    advance(f, f->syscall_cost);
    if (f->scripted && !f->draining) {
        if (f->consume_every > 0 && f->reaps % f->consume_every == 0) consume_writer(f);
        advance(f, f->cadence);
        struct request *r = next_active(f);
        assert(r);
        r->active = 0; r->ready = 1; r->available = f->now;
        r->meta.actual = f->short_bytes ? f->short_bytes : r->meta.requested;
        r->meta.status = 0;
        memset(r->meta.buffer, f->reaps & 255, (size_t)r->meta.actual);
    }
    if (!f->draining && f->fail_reap_at && f->reaps + 1 == f->fail_reap_at)
        return (struct tbs_stream_call){-1, ENODEV};
    if (f->draining && f->stuck_drain) return (struct tbs_stream_call){-1, EAGAIN};
    struct request *best = NULL;
    for (int i = 0; i < f->allocations; ++i) {
        struct request *r = f->requests[i];
        if (r && r->ready && r->available <= f->now
                && (!best || (uintptr_t)r->meta.context < (uintptr_t)best->meta.context)) best = r;
    }
    if (!best) return (struct tbs_stream_call){-1, EAGAIN};
    best->ready = 0; ++f->reaps; *request = best;
    if (!f->draining && f->status_at == f->reaps) best->meta.status = f->status;
    if (!f->draining && f->bad_at == f->reaps) {
        switch (f->bad_kind) {
            case 0: best->meta.actual = 0; break;
            case 1: best->meta.actual = -1; break;
            case 2: best->meta.actual = best->meta.requested + 1; break;
            case 3: best->meta.context = (void *)(uintptr_t)999999; break;
            case 4: best->meta.buffer = (void *)(uintptr_t)1; break;
            case 5: best->meta.requested--; break;
            case 6: best->meta.endpoint = 0x81; break;
            case 7: best->meta.flags = 1; break;
            case 8: best->meta.type = 0; break;
            case 9: best->meta.stream_id = 1; break;
            case 10: best->meta.packets = 1; break;
            case 11: *request = &f->foreign; break;
            case 12: *request = f->requests[1]; break;
            case 13: best->meta.context = (void *)((uintptr_t)best->meta.context - f->stream.depth); break;
            case 14: best->meta.status = -EINPROGRESS; break;
            case 15: *request = f->requests[0]; break;
            default: abort();
        }
    }
    advance(f, 0);
    return (struct tbs_stream_call){0, 0};
}
static struct tbs_stream_call discard(void *opaque, void *request) {
    struct model *f = opaque; struct request *r = request;
    f->draining = 1; ++f->discards;
    r->active = 0; r->ready = 1; r->cancelled = 1;
    r->available = f->now; r->meta.status = -ECONNRESET; r->meta.actual = r->filled;
    return (struct tbs_stream_call){f->discard_error ? -1 : 0, f->discard_error};
}
static void metadata(void *request, struct tbs_stream_meta *m) { *m = ((struct request *)request)->meta; }
static int64_t nanos(void *opaque) { return ((struct model *)opaque)->now; }
static struct tbs_stream_call wait_ready(void *opaque, int millis) {
    struct model *f = opaque; advance(f, millis * 1000000LL);
    return (struct tbs_stream_call){f->fail_wait ? -1 : 0, f->fail_wait ? EIO : 0};
}
static struct tbs_stream_ops ops(struct model *f) {
    return (struct tbs_stream_ops){f, allocate, release, prepare, submit, reap, discard, metadata, nanos, wait_ready};
}
static struct model *fixture(int depth, int chunk, int max_bytes, int duration) {
    struct model *f = calloc(1, sizeof(*f)); assert(f);
    f->fifo_size = 2048; f->syscall_cost = 5000; f->latency = 200000;
    assert(tbs_stream_init(&f->stream, ops(f), depth, chunk, max_bytes, duration) == 0);
    return f;
}
static void cleanup(struct model *f) {
    assert(tbs_stream_destroy(&f->stream) == 0);
    assert(f->allocations == f->frees);
    assert(!f->stream.data && !f->stream.events && !f->stream.resubmit);
    assert(tbs_stream_destroy(&f->stream) == 0);
    free(f);
}
static void fake_kernel_shutdown(struct model *f) {
    assert(f->stream.uncertain);
    assert(tbs_stream_destroy(&f->stream) == -EBUSY);
    assert(f->frees == 0 && (f->stream.data || f->stream.ring));
    /* Host simulation only: the fake kernel is now destroyed, unlike a live fd. */
    f->stream.outstanding = f->stream.uncertain = 0;
    cleanup(f);
}
static void run(struct model *f) { assert(tbs_stream_run(&f->stream) == 0); ++tests; }
static void test_default_jitter_and_fifo(void) {
    struct model *f = fixture(16, 16384, 4 * 1024 * 1024, 2000);
    f->jitter = 30000000;
    run(f);
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_BYTE_CAP);
    assert(strcmp(tbs_stream_reason_name(atomic_load(&f->stream.stop)), "BYTE_LIMIT") == 0);
    assert(f->stream.first_status == 0 && f->stream.first_errno == 0);
    assert(f->stream.delivered == 4 * 1024 * 1024 && f->dropped == 0);
    assert(f->stream.starvation_count == 0 && !f->stream.uncertain && !f->stream.outstanding);
    for (int i = 0; i < f->stream.delivered; ++i) assert(f->stream.data[i] == ts_byte(i));
    assert(f->stream.min_outstanding == 15);
    assert(f->stream.max_resubmit > 0 && f->stream.max_resubmit < 1000000);
    assert(f->stream.median_resubmit <= f->stream.max_resubmit);
    printf("continuous: bytes=%d drops=%lld starvation_risk=%lld min_software_outstanding=%d max_resubmit_ns=%lld median_ns=%lld elapsed_ns=%lld\n",
        f->stream.delivered, (long long)f->dropped, (long long)f->stream.starvation_count,
        f->stream.min_outstanding, (long long)f->stream.max_resubmit, (long long)f->stream.median_resubmit, (long long)f->stream.elapsed);
    cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 1000); f->latency = 150000000;
    run(f);
    assert(f->dropped > 0 && f->stream.horizon_count == 0 && f->stream.starvation_count == 0);
    assert(f->stream.min_outstanding > 0 && f->stream.zero_outstanding_count == 0);
    printf("beyond-reserve: model_drops=%lld disabled_horizon=%lld software_min=%d (source loss does not imply software zero)\n",
        (long long)f->dropped, (long long)f->stream.horizon_count, f->stream.min_outstanding);
    cleanup(f);
    f = fixture(16, 16384, 65536, 1000); f->fifo_size = 512; f->jitter = 30000000;
    run(f); assert(f->dropped == 0); cleanup(f);
}
static void test_legacy_35ms_seams(void) {
    struct model *f = fixture(8, 4096, 65536, 2000);
    unsigned char raw[16 * 4096];
    struct request *r = f->requests[0];
    for (int i = 0; i < 16; ++i) {
        prepare(r, f->stream.slots[0].buffer, 4096, (void *)(uintptr_t)(i + 1));
        assert(submit(f, r).result == 0);
        advance(f, 35000000);
        void *result = NULL; assert(reap(f, &result).result == 0 && result == r);
        memcpy(raw + i * 4096, r->meta.buffer, 4096);
        advance(f, 35000000);
    }
    int near = 0;
    /* Detect sync phase changes from actual captured transport bytes, not a counter formula. */
    int phase = 0;
    for (size_t i = 0; i + 188 * 3 < sizeof(raw); ++i) {
        if (raw[i] == 0x47 && raw[i + 188] == 0x47 && raw[i + 376] == 0x47) {
            int next = (int)(i % 188);
            if (next != phase) { if ((i % 4096) >= 2048 && (i % 4096) < 2236) ++near; phase = next; }
            i += 188 * 2;
        }
    }
    assert(f->dropped > 0 && near >= 14);
    printf("legacy-35ms: bytes=%zu drops=%lld sync_phase_seams_near_2048=%d fifo=%d\n",
        sizeof(raw), (long long)f->dropped, near, f->fifo_size);
    ++tests; cleanup(f);
}
static void test_status_first_middle(void) {
    int statuses[] = {-75, -71, -ENODEV, -EPIPE, 1};
    for (size_t i = 0; i < sizeof(statuses) / sizeof(statuses[0]); ++i) for (int at = 1; at <= 5; at += 4) {
        struct model *f = fixture(16, 16384, 1024 * 1024, 2000);
        f->status = statuses[i]; f->status_at = at; run(f);
        assert(atomic_load(&f->stream.stop) == TBS_STREAM_STATUS);
        assert(f->stream.first_status == statuses[i] && f->stream.first_errno == 0);
        assert(f->stream.delivered == (at - 1) * 16384);
        assert(f->submissions == 16 + at - 1 && !f->stream.outstanding && !f->stream.uncertain);
        cleanup(f);
    }
}
static void test_bad_completion(void) {
    for (int kind = 0; kind <= 15; ++kind) {
        struct model *f = fixture(16, 16384, 1024 * 1024, 2000);
        f->bad_at = kind == 15 ? 2 : 1; f->bad_kind = kind; run(f);
        assert(f->submissions == 16 + f->bad_at - 1 && f->stream.delivered == (f->bad_at - 1) * 16384);
        if (kind <= 2) { assert(atomic_load(&f->stream.stop) == TBS_STREAM_LENGTH); cleanup(f); }
        else { assert(f->stream.uncertain); fake_kernel_shutdown(f); }
    }
}
static void test_stops_deadline_and_drain(void) {
    struct model *f = fixture(16, 16384, 1024 * 1024, 2000);
    tbs_stream_request_stop(&f->stream); run(f);
    assert(!f->submissions && atomic_load(&f->stream.stop) == TBS_STREAM_STOP); cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 2000); f->stop_at = 4; run(f);
    assert(f->stream.delivered == 3 * 16384 && f->submissions == 19);
    assert(!f->stream.outstanding && atomic_load(&f->stream.stop) == TBS_STREAM_STOP); cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 2000); f->idle = 1; f->stop_time = 100000000; run(f);
    assert(f->stream.elapsed >= 100000000 && f->stream.elapsed < 103000000); cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 25); f->idle = 1; run(f);
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_DEADLINE);
    assert(f->stream.elapsed >= 25000000 && f->stream.elapsed < 28000000); cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 25); f->idle = 1; f->stuck_drain = 1; run(f);
    assert(f->stream.uncertain && f->stream.outstanding == 16);
    assert(f->now - f->stream.elapsed >= 1000000000 && f->now - f->stream.elapsed < 1003000000);
    fake_kernel_shutdown(f);
    int errors[] = {0, EINVAL, ENODEV, EIO};
    for (size_t i = 0; i < sizeof(errors) / sizeof(errors[0]); ++i) {
        f = fixture(8, 4096, 10000, 1000); f->discard_error = errors[i]; run(f);
        assert(!f->stream.uncertain && !f->stream.outstanding); cleanup(f);
    }
}
static void test_syscalls_and_limits(void) {
    for (int at = 1; at <= 10; at += 9) {
        struct model *f = fixture(16, 16384, 1024 * 1024, 2000);
        f->fail_submit_at = at; run(f);
        assert(f->stream.first_errno == ENOMEM && f->stream.submitted == at - 1); cleanup(f);
        f = fixture(16, 16384, 1024 * 1024, 2000); f->fail_reap_at = at; run(f);
        assert(f->stream.first_errno == ENODEV && f->stream.delivered == (at - 1) * 16384); cleanup(f);
    }
    struct model *f = fixture(16, 16384, 1024 * 1024, 2000); f->fail_wait = 1; run(f);
    assert(f->stream.first_errno == EIO && atomic_load(&f->stream.stop) == TBS_STREAM_SYSCALL); cleanup(f);
    f = fixture(16, 16384, 1024 * 1024, 2000); f->stream.event_capacity = 96; run(f);
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_EVENT_CAP);
    assert(f->stream.event_count <= 96 && f->stream.delivered > 0);
    assert(!f->stream.uncertain && !f->stream.outstanding); cleanup(f);
    int configs[][4] = {{8, 4096, 1, 1}, {32, 65536, 65537, 10000}, {8, 4608, 10001, 1000}};
    for (size_t i = 0; i < sizeof(configs) / sizeof(configs[0]); ++i) {
        f = fixture(configs[i][0], configs[i][1], configs[i][2], configs[i][3]); run(f);
        assert(f->stream.delivered <= configs[i][2]); cleanup(f);
    }
    int invalid[][4] = {{7,4096,1,1},{33,4096,1,1},{8,4095,1,1},{8,65537,1,1},
        {8,4097,1,1},{8,4096,0,1},{8,4096,TBS_STREAM_MAX_BYTES+1,1},{8,4096,1,0},{8,4096,1,10001}};
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i) {
        f = calloc(1, sizeof(*f)); assert(f);
        assert(tbs_stream_init(&f->stream, ops(f), invalid[i][0], invalid[i][1], invalid[i][2], invalid[i][3]) == -EINVAL);
        assert(f->allocations == 0); free(f); ++tests;
    }
    for (int at = 1; at <= 16; ++at) {
        f = calloc(1, sizeof(*f)); assert(f); f->fail_alloc_at = at;
        assert(tbs_stream_init(&f->stream, ops(f), 16, 16384, 100000, 2000) == -ENOMEM);
        assert(f->allocations == f->frees); free(f); ++tests;
    }
    f = calloc(1, sizeof(*f)); assert(f); f->duplicate_alloc = 1;
    assert(tbs_stream_init(&f->stream, ops(f), 16, 16384, 100000, 2000) == -ENOMEM);
    assert(f->allocations == 1 && f->frees == 1); free(f); ++tests;
}
static struct model *file_fixture(int depth, int max_bytes, int duration) {
    struct model *f = calloc(1, sizeof(*f)); assert(f);
    f->idle = 1; f->scripted = 1; f->cadence = 4766254; f->syscall_cost = 5000;
    assert(tbs_stream_init_file(&f->stream, ops(f), depth, 16384, max_bytes, duration) == 0);
    assert(!f->stream.data && f->stream.ring);
    assert(tbs_stream_writer_attach(&f->stream) == 0);
    assert(tbs_stream_writer_attach(&f->stream) == -EINVAL);
    return f;
}
static void file_cleanup(struct model *f) {
    tbs_stream_writer_detach(&f->stream);
    if (f->stream.uncertain) fake_kernel_shutdown(f); else cleanup(f);
}
static void test_file_long_capture(void) {
    for (int depth = 16; depth <= 32; depth += 16) {
        struct model *f = file_fixture(depth, TBS_STREAM_FILE_MAX_BYTES, 60000);
        f->consume_every = 1;
        run(f); consume_writer(f);
        assert(atomic_load(&f->stream.stop) == TBS_STREAM_DEADLINE);
        assert(f->stream.elapsed >= 60000000000LL && f->stream.elapsed < 60010000000LL);
        assert(f->stream.mapping_count > 12000 && f->stream.mapping_count < 13000);
        assert(f->writer_bytes == f->stream.delivered && f->stream.writer_drop_bytes == 0);
        assert(f->stream.delivered > 190000000 && !f->stream.outstanding && !f->stream.uncertain);
        for (int i = 0; i < f->stream.mapping_count; ++i) {
            int64_t *m = f->stream.mappings[i];
            assert(m[0] == i + 1 && m[1] == (int64_t)i * 16384 && m[2] == m[1]);
            assert(m[3] == 16384 && m[4] == 0 && m[5] > 0);
        }
        size_t bounded = TBS_STREAM_RING_BYTES + (size_t)f->stream.event_capacity *
            (sizeof(struct tbs_stream_event) + sizeof(int64_t))
            + TBS_STREAM_FILE_COMPLETIONS * sizeof(*f->stream.mappings) + (size_t)depth * 16384;
        assert(bounded < 16 * 1024 * 1024);
        assert(tbs_stream_destroy(&f->stream) == -EBUSY); /* JNI consumer still attached. */
        printf("file60s: depth=%d received=%d written=%lld mappings=%d bounded_native_bytes=%zu drops=0\n",
            depth, f->stream.delivered, (long long)f->writer_bytes, f->stream.mapping_count, bounded);
        file_cleanup(f);
    }
}
static void test_file_backpressure_and_exhaustion(void) {
    struct model *f = file_fixture(16, 8 * 1024 * 1024 + 123, 60000);
    run(f); /* Consumer deliberately never scheduled: producer still reaches the byte cap. */
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_BYTE_CAP);
    assert(f->stream.writer_queued_bytes == TBS_STREAM_RING_BYTES);
    assert(f->stream.writer_drop_bytes == f->stream.delivered - TBS_STREAM_RING_BYTES);
    assert(f->stream.elapsed < 3000000000LL && f->stream.max_resubmit < 1000000);
    for (int i = 0; i < f->stream.mapping_count; ++i) {
        int64_t *m = f->stream.mappings[i];
        assert(m[1] == (int64_t)i * 16384 && m[4] == (i >= 256));
        assert(m[2] == (i < 256 ? m[1] : -1));
    }
    consume_writer(f); assert(f->writer_bytes == TBS_STREAM_RING_BYTES);
    printf("slow-writer: received=%d written=%lld exact_drop=%lld max_resubmit_ns=%lld\n",
        f->stream.delivered, (long long)f->writer_bytes, (long long)f->stream.writer_drop_bytes,
        (long long)f->stream.max_resubmit);
    file_cleanup(f);
    f = file_fixture(16, 8 * 1024 * 1024 + 123, 60000); f->consume_every = 300;
    run(f); consume_writer(f);
    assert(f->stream.writer_drop_bytes == 44 * 16384);
    assert(f->stream.mappings[299][2] == -1 && f->stream.mappings[299][4] == 1);
    assert(f->stream.mappings[300][1] == 300 * 16384 && f->stream.mappings[300][2] == 256 * 16384);
    assert(f->stream.mappings[300][4] == 0 && f->writer_bytes + f->stream.writer_drop_bytes == f->stream.delivered);
    file_cleanup(f);
    f = file_fixture(32, TBS_STREAM_FILE_MAX_BYTES, 60000);
    f->short_bytes = 512; f->cadence = 1000; run(f);
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_EVENT_CAP);
    assert(f->stream.mapping_count <= TBS_STREAM_FILE_COMPLETIONS);
    assert(!f->stream.outstanding && !f->stream.uncertain);
    assert(f->stream.writer_drop_bytes + f->stream.writer_queued_bytes == f->stream.delivered);
    file_cleanup(f);
    f = file_fixture(16, TBS_STREAM_FILE_MAX_BYTES, 60000); f->status_at = 4; f->status = -75; run(f);
    assert(f->stream.first_status == -75 && f->stream.delivered == 3 * 16384); file_cleanup(f);
    f = file_fixture(16, TBS_STREAM_FILE_MAX_BYTES, 60000); f->stop_at = 3; run(f);
    assert(atomic_load(&f->stream.stop) == TBS_STREAM_STOP); file_cleanup(f);
    f = file_fixture(16, TBS_STREAM_FILE_MAX_BYTES, 25); f->stuck_drain = 1; run(f);
    assert(f->stream.uncertain && f->stream.outstanding); file_cleanup(f);
    int invalid[][4] = {{8,16384,1,60000},{16,4096,1,60000},{16,16384,TBS_STREAM_FILE_MAX_BYTES+1,60000},
        {32,16384,1,60001},{16,16384,0,60000}};
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i) {
        f = calloc(1, sizeof(*f)); assert(f);
        assert(tbs_stream_init_file(&f->stream, ops(f), invalid[i][0], invalid[i][1], invalid[i][2], invalid[i][3]) == -EINVAL);
        assert(f->allocations == 0); free(f); ++tests;
    }
}
int main(void) {
    test_file_long_capture(); test_file_backpressure_and_exhaustion();
    test_default_jitter_and_fifo(); test_legacy_35ms_seams(); test_status_first_middle();
    test_bad_completion(); test_stops_deadline_and_drain(); test_syscalls_and_limits();
    printf("PASS %d native stream cases; real C core, synthetic TS/finite FIFO; no hardware claim\n", tests);
    return 0;
}
