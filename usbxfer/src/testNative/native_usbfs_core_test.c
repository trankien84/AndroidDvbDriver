// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_core.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct request { struct tbs_meta meta; int cancelled; };
struct fake {
    struct tbs_core core;
    void *next;
    int submit_error, reap_error, discard_error, submits, discards, frees, metadata_calls;
    int auto_drain;
    int64_t now;
};
static void *allocate(void *v) { (void)v; return calloc(1, sizeof(struct request)); }
static void release(void *v, void *r) { ((struct fake *)v)->frees++; free(r); }
static void prepare(void *r, unsigned char *bytes, void *context) {
    ((struct request *)r)->meta = (struct tbs_meta){-EINPROGRESS,0,TBS_CHUNK,3,0x82,0,0,bytes,context};
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
    assert(tbs_init(&f->core, ops, budget) == 0); return f;
}
static void fill(struct fake *f) { for (int i = 0; i < 8; ++i) assert(tbs_fill(&f->core) == 1); }
static void completion(struct fake *f, int slot, int status, int count) {
    struct request *r = f->core.slots[slot].request;
    r->meta.status = status; r->meta.actual = count;
    memset(f->core.slots[slot].buffer, slot + 1, TBS_CHUNK); f->next = r;
}
static void cleanup(struct fake *f) {
    tbs_stop(&f->core); f->auto_drain = 1; f->reap_error = 0;
    while (f->core.outstanding && !f->core.uncertain) assert(tbs_poll(&f->core) == 0);
    assert(tbs_destroy(&f->core) == 0); assert(f->frees == f->core.allocations); free(f);
}
static void fake_recovery(struct fake *f) {
    /* Only this fake kernel has no outstanding references; production never clears quarantine. */
    assert(tbs_destroy(&f->core) == TBS_UNCERTAIN);
    assert(f->frees == 0); f->core.outstanding = 0; f->core.uncertain = 0;
    assert(tbs_destroy(&f->core) == 0); free(f);
}
static void test_zero_short_error(void) {
    int statuses[] = {0,0,-75,-EPIPE,-EPROTO,-ECONNRESET,-ENODEV};
    int lengths[] = {0,17,23,0,0,0,0};
    for (int n = 0; n < 7; ++n) {
        struct fake *f = fixture(16); fill(f);
        struct { unsigned char before[32], bytes[TBS_CHUNK], after[32]; } canary;
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
    struct fake *f = fixture(16); fill(f); unsigned char bytes[TBS_CHUNK];
    completion(f,1,0,100); assert(tbs_poll(&f->core) == 0); assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_WAIT);
    completion(f,0,0,10); void *old = f->next;
    assert(tbs_poll(&f->core) == 0); assert(tbs_take(&f->core,bytes,sizeof(bytes)) == 10); assert(bytes[0] == 1);
    assert(tbs_fill(&f->core) == 1); assert(old != f->core.slots[0].request);
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == 100); assert(bytes[0] == 2);
    f->next = old; assert(tbs_poll(&f->core) == TBS_UNCERTAIN); fake_recovery(f);
}
static void test_invalid(void) {
    for (int scenario = 0; scenario < 9; ++scenario) {
        struct fake *f = fixture(8); fill(f); completion(f,0,0,1);
        struct request *r = f->next;
        if (scenario == 0) r->meta.actual = -1;
        if (scenario == 1) r->meta.actual = TBS_CHUNK + 1;
        if (scenario == 2) r->meta.requested = TBS_CHUNK + 1;
        if (scenario == 3) r->meta.buffer = (void *)(uintptr_t)1;
        if (scenario == 4) r->meta.context = (void *)(uintptr_t)1;
        if (scenario == 5) r->meta.endpoint = 0x81;
        if (scenario == 6) r->meta.packets = 8;
        if (scenario == 7) r->meta.status = 1;
        if (scenario == 8) f->next = (void *)(uintptr_t)1;
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
    f = fixture(8); fill(f); f->discard_error = EINVAL; f->auto_drain = 1;
    tbs_stop(&f->core); assert(f->core.outstanding == 8); cleanup(f);
    f = fixture(8); fill(f); tbs_stop(&f->core);
    for (int i = 0; i < 128; ++i) assert(tbs_poll(&f->core) == TBS_WAIT);
    tbs_quarantine(&f->core); fake_recovery(f);
}
static void test_copy_capacity_and_budget(void) {
    struct fake *f = fixture(8); fill(f); unsigned char bytes[TBS_CHUNK]; memset(bytes,0x5a,sizeof(bytes));
    completion(f,0,0,TBS_CHUNK); assert(tbs_poll(&f->core) == 0);
    assert(tbs_take(&f->core,bytes,8) == TBS_ERROR); assert(bytes[0] == 0x5a);
    assert(tbs_take(&f->core,bytes,sizeof(bytes)) == TBS_CHUNK);
    assert(tbs_fill(&f->core) == 0); assert(f->submits == 8); cleanup(f);
}
int main(void) {
    test_zero_short_error(); test_order_and_retired_duplicate(); test_invalid();
    test_syscall_and_drain(); test_copy_capacity_and_budget();
    puts("PASS: native usbfs core fault injection (real core, canary bounds)"); return 0;
}
