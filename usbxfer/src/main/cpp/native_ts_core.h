// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef TBS_NATIVE_TS_CORE_H
#define TBS_NATIVE_TS_CORE_H
#include <stdint.h>
#include <stdatomic.h>

#define TBS_TS_DEPTH 16
#define TBS_TS_MAX_DEPTH 32
#define TBS_TS_CHUNK 16384
#define TBS_TS_RING_SLOTS 256
#define TBS_TS_COMPLETIONS 65536

enum tbs_ts_reason {
    TBS_TS_RUNNING, TBS_TS_STOP, TBS_TS_STATUS, TBS_TS_LENGTH,
    TBS_TS_SYSCALL, TBS_TS_IDENTITY, TBS_TS_OVERFLOW, TBS_TS_CLOCK
};
struct tbs_ts_call { int result, error; };
struct tbs_ts_meta {
    int status, actual, requested, type, endpoint, packets;
    unsigned flags, stream_id;
    void *buffer, *context;
};
struct tbs_ts_ops {
    void *opaque;
    void *(*allocate)(void *opaque);
    void (*release)(void *opaque, void *request);
    void (*prepare)(void *request, unsigned char *buffer, int size, void *context);
    struct tbs_ts_call (*submit)(void *opaque, void *request);
    struct tbs_ts_call (*reap)(void *opaque, void **request);
    struct tbs_ts_call (*discard)(void *opaque, void *request);
    void (*metadata)(void *request, struct tbs_ts_meta *meta);
    int64_t (*nanos)(void *opaque);
    struct tbs_ts_call (*wait)(void *opaque, int millis);
    /* Must synchronously relinquish ALL kernel references, even after failed drain.
     * Linux usbfs close of the sole open file description has this property. */
    void (*close)(void *opaque);
};
struct tbs_ts_completion { int64_t monotonic_nanos; int status, length; };
struct tbs_ts_slot { void *request; unsigned char *buffer; uintptr_t sequence; int pending; };
struct tbs_ts {
    struct tbs_ts_ops ops;
    struct tbs_ts_slot slots[TBS_TS_MAX_DEPTH];
    int depth;
    atomic_llong begin_nanos, end_nanos;
    unsigned char *ring;
    struct tbs_ts_completion *completions;
    atomic_uint completion_count;
    atomic_int completion_overflow, completion_clock_invalid;
    int64_t last_completion_nanos;
    int lengths[TBS_TS_RING_SLOTS];
    atomic_uint head, tail;
    atomic_int stop, state;
    uintptr_t sequence, expected;
    int outstanding, status, error, drain_failed, closed;
    uint64_t completed, bytes;
};
/* init takes ownership of ops.close after validating ops, including allocation failures. */
int tbs_ts_init(struct tbs_ts *s, struct tbs_ts_ops ops);
int tbs_ts_init_config(struct tbs_ts *s, struct tbs_ts_ops ops, int depth);
void tbs_ts_stop(struct tbs_ts *s);
int tbs_ts_run(struct tbs_ts *s);
/* Single consumer; copy/consume may run concurrently with run, never destroy.
 * 0=empty, -ECANCELED=stopped, -EIO=failed. Buffered data is rejected after error. */
int tbs_ts_peek(struct tbs_ts *s, const unsigned char **bytes);
void tbs_ts_consume(struct tbs_ts *s);
/* Caller joins run and excludes the consumer before destroy. */
int tbs_ts_destroy(struct tbs_ts *s);
#endif
