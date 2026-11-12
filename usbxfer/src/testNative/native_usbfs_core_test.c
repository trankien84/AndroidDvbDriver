// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_core.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int test_chunk = TBS_CHUNK;
struct request { struct tbs_meta meta; int cancelled, bad_prepare; };
struct fake {
    struct tbs_core core;
    void *next;
    int submit_error, reap_error, discard_error, submits, discards, frees, metadata_calls;
    int auto_drain, prepare_fault;
    int64_t now;
};
static void *allocate(void *v) {
    struct request *r = calloc(1, sizeof(*r));
    if (r) r->bad_prepare = ((struct fake *)v)->prepare_fault;
    return r;
}
static void release(void *v, void *r) { ((struct fake *)v)->frees++; free(r); }
static void prepare(void *r, unsigned char *bytes, int capacity, void *context) {
    struct request *request = r;
    request->meta = (struct tbs_meta){-EINPROGRESS,0,capacity,3,0x82,0,0,0,bytes,context};
    if (request->bad_prepare) request->meta.requested++;
}
static struct tbs_syscall submit(void *v, void *r) {
    (void)r; struct fake *f = v; f->submits++;
    return f->submit_error ? (struct tbs_syscall){-1,f->submit_error} : (struct tbs_syscall){0,0};
}
static struct tbs_syscall reap(void *v, void **r) {
    struct fake *f = v;
    if (f->reap_error) return (struct tbs_syscall){-1,f->reap_error};
    if (f->next) { *r = f->next; f->next = NULL; return (struct tbs_syscall){0,0}; }
    if (f->auto_drain) for (int i = 0; i < TBS_DEPTH; ++i) if (f->core.slots[i].pending) {
        struct request *request = f->core.slots[i].request;
        request->meta.status = -ECONNRESET; request->meta.actual = 0;
        *r = request; return (struct tbs_syscall){0,0};
    }
    return (struct tbs_syscall){-1,EAGAIN};
}
static struct tbs_syscall discard(void *v, void *r) {
    struct fake *f = v; f->discards++; ((struct request *)r)->cancelled = 1;
    return f->discard_error ? (struct tbs_syscall){-1,f->discard_error} : (struct tbs_syscall){0,0};
}
static void metadata(void *r, struct tbs_meta *m) { *m = ((struct request *)r)->meta; }
static int64_t nanos(void *v) { return ++((struct fake *)v)->now; }
static struct fake *fixture(int budget) {
    struct fake *f = calloc(1, sizeof(*f)); assert(f);
    struct tbs_ops ops = {f,allocate,release,prepare,submit,reap,discard,metadata,nanos};
    assert(tbs_init_sized(&f->core, ops, budget, test_chunk) == 0); return f;
}
static void fill(struct fake *f) { for (int i = 0; i < 8; ++i) assert(tbs_fill(&f->core) == 1); }
static void completion(struct fake *f, int slot, int status, int count) {
    struct request *r = f->core.slots[slot].request;
    r->meta.status = status; r->meta.actual = count;
    memset(f->core.slots[slot].buffer, slot + 1, (size_t)test_chunk); f->next = r;
}
static void cleanup(struct fake *f) {
    tbs_stop(&f->core); f->auto_drain = 1; f->reap_error = 0;
    while (f->core.outstanding && !f->core.uncertain) assert(tbs_poll(&f->core) == 0);
    assert(tbs_destroy(&f->core) == 0); assert(f->frees == f->core.allocations);
    assert(tbs_destroy(&f->core) == 0);
    assert(tbs_fill(&f->core) == TBS_ERROR && tbs_poll(&f->core) == TBS_ERROR);
    unsigned char bytes[TBS_MAX_CHUNK];
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_ERROR); free(f);
}
static void fake_recovery(struct fake *f) {
    /* Only this fake kernel has no outstanding references; production never clears quarantine. */
    assert(tbs_destroy(&f->core) == TBS_UNCERTAIN);
    assert(f->frees == 0); f->core.outstanding = 0; f->core.uncertain = 0;
    assert(tbs_destroy(&f->core) == 0); free(f);
}
static void test_zero_short_error(void) {
    int statuses[] = {0,0,-75,-EPIPE,-EPROTO,-ECONNRESET,-ENODEV,-ENOENT,-ECANCELED};
    int lengths[] = {0,17,23,0,0,0,0,0,0};
    for (size_t n = 0; n < sizeof(statuses) / sizeof(statuses[0]); ++n) {
        struct fake *f = fixture(16); fill(f);
        struct { unsigned char before[32], bytes[TBS_MAX_CHUNK], after[32]; } canary;
        memset(&canary, 0xa5, sizeof(canary));
        completion(f,0,statuses[n],lengths[n]); assert(tbs_poll(&f->core) == 0);
        assert(f->core.events[8].status == statuses[n]);
        assert(f->core.events[8].error == 0 && f->core.last_errno == 0);
        int count = tbs_take(&f->core,canary.bytes,sizeof(canary.bytes));
        assert(count == (statuses[n] ? TBS_ERROR : lengths[n]));
        for (int i = 0; i < 32; ++i) assert(canary.before[i] == 0xa5 && canary.after[i] == 0xa5);
        if (count <= 0) { assert(canary.bytes[0] == 0xa5); assert(tbs_fill(&f->core) < 0); assert(f->submits == 8); }
        else { assert(canary.bytes[0] == 1 && canary.bytes[17] == 0xa5); assert(tbs_fill(&f->core) == 1); }
        cleanup(f);
    }
}
static void test_order_and_retired_duplicate(void) {
    struct fake *f = fixture(16); fill(f); unsigned char bytes[TBS_MAX_CHUNK];
    completion(f,1,0,100); assert(tbs_poll(&f->core) == 0); assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_WAIT);
    completion(f,0,0,10); void *old = f->next;
    assert(tbs_poll(&f->core) == 0); assert(tbs_take(&f->core,bytes,sizeof(bytes)) == 10); assert(bytes[0] == 1);
    assert(tbs_fill(&f->core) == 1); assert(old != f->core.slots[0].request);
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == 100); assert(bytes[0] == 2);
    f->next = old; assert(tbs_poll(&f->core) == TBS_UNCERTAIN); fake_recovery(f);
}
static void test_invalid(void) {
    for (int scenario = 0; scenario < 17; ++scenario) {
        struct fake *f = fixture(8); fill(f); completion(f,0,0,1);
        struct request *r = f->next;
        if (scenario == 0) r->meta.actual = -1;
        if (scenario == 1) r->meta.actual = test_chunk + 1;
        if (scenario == 2) r->meta.requested = test_chunk + 1;
        if (scenario == 3) r->meta.buffer = (void *)(uintptr_t)1;
        if (scenario == 4) r->meta.context = (void *)(uintptr_t)1;
        if (scenario == 5) r->meta.endpoint = 0x81;
        if (scenario == 6) r->meta.packets = 8;
        if (scenario == 7) r->meta.status = 1;
        if (scenario == 8) f->next = (void *)(uintptr_t)1;
        if (scenario == 9) r->meta.stream_id = 1;
        if (scenario == 10) r->meta.flags = 1;
        if (scenario == 11) r->meta.type = 0;
        if (scenario == 12) r->meta.requested = test_chunk == TBS_CHUNK ? TBS_MAX_CHUNK : TBS_CHUNK;
        if (scenario == 13) r->meta.status = -EINPROGRESS;
        if (scenario == 14) f->core.slots[0].capacity--;
        if (scenario == 15) r->meta.requested = 0;
        if (scenario == 16) r->meta.requested = -1;
        assert(tbs_poll(&f->core) == TBS_UNCERTAIN); fake_recovery(f);
    }
    struct fake *f = fixture(8); fill(f); completion(f,0,0,1); void *same = f->next;
    assert(tbs_poll(&f->core) == 0); f->next = same;
    assert(tbs_poll(&f->core) == TBS_UNCERTAIN); fake_recovery(f);
}
static void test_syscall_and_drain(void) {
    struct fake *f = fixture(8); f->submit_error = ENOMEM;
    assert(tbs_fill(&f->core) == TBS_ERROR); assert(f->core.outstanding == 0);
    assert(f->core.events[0].error == ENOMEM && f->core.events[0].status == TBS_STATUS_UNKNOWN); cleanup(f);
    f = fixture(8); assert(tbs_fill(&f->core) == 1); f->submit_error = EPIPE;
    assert(tbs_fill(&f->core) == TBS_ERROR); assert(f->core.outstanding == 1); cleanup(f);
    f = fixture(8); fill(f); assert(tbs_poll(&f->core) == TBS_WAIT);
    assert(f->core.last_errno == EAGAIN); f->reap_error = ENODEV;
    assert(tbs_poll(&f->core) == TBS_ERROR); assert(f->core.outstanding == 8);
    tbs_stop(&f->core); assert(f->discards == 8); assert(tbs_poll(&f->core) == TBS_ERROR); fake_recovery(f);
    int discard_errors[] = {0,EINVAL,ENODEV,EIO};
    for (size_t i = 0; i < sizeof(discard_errors) / sizeof(discard_errors[0]); ++i) {
        f = fixture(8); fill(f); f->discard_error = discard_errors[i]; f->auto_drain = 1;
        tbs_stop(&f->core); assert(f->core.outstanding == 8);
        assert(tbs_destroy(&f->core) == TBS_UNCERTAIN && f->frees == 0); cleanup(f);
    }
    f = fixture(8); fill(f); tbs_stop(&f->core);
    for (int i = 0; i < 128; ++i) assert(tbs_poll(&f->core) == TBS_WAIT);
    tbs_quarantine(&f->core); fake_recovery(f);
}
static void test_copy_capacity_and_budget(void) {
    struct fake *f = fixture(8); fill(f); unsigned char bytes[TBS_MAX_CHUNK]; memset(bytes,0x5a,sizeof(bytes));
    completion(f,0,0,test_chunk); assert(tbs_poll(&f->core) == 0);
    assert(tbs_take(&f->core,bytes,(size_t)test_chunk - 1) == TBS_ERROR); assert(bytes[0] == 0x5a);
    assert(tbs_take(&f->core,NULL,(size_t)test_chunk) == TBS_ERROR);
    assert(tbs_take(&f->core,bytes,(size_t)test_chunk) == test_chunk);
    for (int i = 0; i < test_chunk; ++i) assert(bytes[i] == 1);
    for (int i = test_chunk; i < TBS_MAX_CHUNK; ++i) assert(bytes[i] == 0x5a);
    assert(tbs_fill(&f->core) == 0); assert(f->submits == 8); cleanup(f);
}
static void test_depth_capacity_and_max_submissions(void) {
    int budget = TBS_MAX_BYTES / test_chunk;
    struct fake *f = fixture(budget); fill(f);
    assert(f->core.outstanding == TBS_DEPTH); assert(tbs_fill(&f->core) == 0);
    assert(tbs_destroy(&f->core) == TBS_UNCERTAIN && f->frees == 0);
    unsigned char bytes[TBS_MAX_CHUNK];
    for (int sequence = 1; sequence <= budget; ++sequence) {
        int slot = (sequence - 1) % TBS_DEPTH;
        assert(f->core.slots[slot].capacity == test_chunk);
        struct request *r = f->core.slots[slot].request;
        assert(r->meta.requested == test_chunk && r->meta.buffer == f->core.slots[slot].buffer);
        assert(r->meta.packets == 0 && r->meta.stream_id == 0);
        completion(f,slot,0,test_chunk); assert(tbs_poll(&f->core) == 0);
        assert(tbs_take(&f->core,bytes,(size_t)test_chunk) == test_chunk);
        assert(tbs_fill(&f->core) == (sequence <= budget - TBS_DEPTH ? 1 : 0));
        assert(f->core.outstanding <= TBS_DEPTH);
    }
    assert(f->submits == budget && f->core.allocations == budget);
    assert(f->core.delivered == TBS_MAX_BYTES);
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_LIMIT);
    for (int i = 0; i < f->core.event_count; ++i) {
        assert(f->core.events[i].requested == test_chunk && f->core.events[i].capacity == test_chunk);
    }
    cleanup(f);
}
static void test_first_negative_invalidates_ready_and_never_resubmits(void) {
    struct fake *f = fixture(16); fill(f); unsigned char bytes[TBS_MAX_CHUNK];
    memset(bytes,0xa5,sizeof(bytes));
    completion(f,0,0,test_chunk); assert(tbs_poll(&f->core) == 0);
    completion(f,5,-75,23); assert(tbs_poll(&f->core) == 0);
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_ERROR && bytes[0] == 0xa5);
    assert(tbs_fill(&f->core) == TBS_ERROR && f->submits == TBS_DEPTH);
    assert(tbs_poll(&f->core) == TBS_ERROR);
    tbs_stop(&f->core);
    assert(f->discards == 6 && f->core.cleanup_bytes == test_chunk + 23);
    assert(tbs_destroy(&f->core) == TBS_UNCERTAIN && f->frees == 0);
    cleanup(f);
}
static void test_drain_validates_metadata_and_does_not_deliver(void) {
    struct fake *f = fixture(8); fill(f); tbs_stop(&f->core);
    completion(f,0,-ECONNRESET,test_chunk); assert(tbs_poll(&f->core) == 0);
    unsigned char bytes[TBS_MAX_CHUNK]; memset(bytes,0xa5,sizeof(bytes));
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_ERROR && bytes[0] == 0xa5);
    assert(f->core.cleanup_bytes == test_chunk && f->core.delivered == 0);
    cleanup(f);
    f = fixture(8); fill(f); tbs_stop(&f->core);
    completion(f,0,-ECONNRESET,test_chunk + 1);
    assert(tbs_poll(&f->core) == TBS_UNCERTAIN); fake_recovery(f);
    f = fixture(8); f->prepare_fault = 1;
    assert(tbs_fill(&f->core) == TBS_UNCERTAIN && f->submits == 0);
    fake_recovery(f);
}
static void test_initialization_bounds_and_default(void) {
    struct fake *f = calloc(1, sizeof(*f)); assert(f);
    struct tbs_ops ops = {f,allocate,release,prepare,submit,reap,discard,metadata,nanos};
    int invalid[] = {-1,0,1,2048,4095,4097,8191,8193,16384,65536};
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i) {
        assert(tbs_init_sized(&f->core,ops,8,invalid[i]) == TBS_ERROR);
    }
    assert(tbs_init_sized(&f->core,ops,7,test_chunk) == TBS_ERROR);
    assert(tbs_init_sized(&f->core,ops,257,test_chunk) == TBS_ERROR);
    assert(tbs_init_sized(&f->core,ops,TBS_MAX_BYTES / test_chunk + 1,test_chunk) == TBS_ERROR);
    assert(tbs_init_sized(NULL,ops,8,test_chunk) == TBS_ERROR);
    assert(tbs_init(&f->core,ops,8) == 0 && f->core.chunk_bytes == TBS_CHUNK);
    for (int i = 0; i < TBS_DEPTH; ++i) assert(f->core.slots[i].capacity == TBS_CHUNK);
    cleanup(f);
}
int main(void) {
    for (test_chunk = TBS_CHUNK; test_chunk <= TBS_MAX_CHUNK; test_chunk *= 2) {
        test_initialization_bounds_and_default();
        test_zero_short_error(); test_order_and_retired_duplicate(); test_invalid();
        test_syscall_and_drain(); test_copy_capacity_and_budget();
        test_depth_capacity_and_max_submissions();
        test_first_negative_invalidates_ready_and_never_resubmits();
        test_drain_validates_metadata_and_does_not_deliver();
        printf("PASS: native usbfs core fault injection, 8x%d (real core, canary bounds)\n",test_chunk);
    }
    return 0;
}
