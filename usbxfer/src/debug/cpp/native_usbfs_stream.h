// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef TBS_NATIVE_USBFS_STREAM_H
#define TBS_NATIVE_USBFS_STREAM_H
#include <stddef.h>
#include <stdint.h>
#include <stdatomic.h>

#define TBS_STREAM_MAX_DEPTH 32
#define TBS_STREAM_MAX_BYTES (16 * 1024 * 1024)
#define TBS_STREAM_UNKNOWN INT32_MIN
#define TBS_STREAM_EVENT_WIDTH 14
#define TBS_STREAM_RATE 3437500LL /* Reference model: 27.5 Mbit/s, not measured hardware. */

enum tbs_stream_reason {
    TBS_STREAM_RUNNING, TBS_STREAM_STOP, TBS_STREAM_DEADLINE, TBS_STREAM_BYTE_CAP,
    TBS_STREAM_STATUS, TBS_STREAM_LENGTH, TBS_STREAM_SYSCALL, TBS_STREAM_IDENTITY,
    TBS_STREAM_EVENT_CAP, TBS_STREAM_CLOCK
};
struct tbs_stream_call { int result, error; };
struct tbs_stream_meta {
    int status, actual, requested, type, endpoint, packets;
    unsigned flags, stream_id;
    void *buffer, *context;
};
struct tbs_stream_ops {
    void *opaque;
    void *(*allocate)(void *opaque);
    void (*release)(void *opaque, void *request);
    void (*prepare)(void *request, unsigned char *buffer, int capacity, void *context);
    struct tbs_stream_call (*submit)(void *opaque, void *request);
    struct tbs_stream_call (*reap)(void *opaque, void **request);
    struct tbs_stream_call (*discard)(void *opaque, void *request);
    void (*metadata)(void *request, struct tbs_stream_meta *meta);
    int64_t (*nanos)(void *opaque);
    struct tbs_stream_call (*wait)(void *opaque, int millis);
};
struct tbs_stream_event { int64_t v[TBS_STREAM_EVENT_WIDTH]; };
struct tbs_stream_slot {
    void *request;
    unsigned char *buffer;
    int pending;
    uint64_t sequence;
};
struct tbs_stream {
    struct tbs_stream_ops ops;
    struct tbs_stream_slot slots[TBS_STREAM_MAX_DEPTH];
    unsigned char *data;
    struct tbs_stream_event *events;
    int64_t *resubmit;
    int depth, chunk, max_bytes, duration_ms, event_capacity, event_count, latency_count;
    int attempted, submitted, completed, outstanding, delivered, cleanup, uncertain;
    int last_errno, last_status, last_actual, last_slot, last_return;
    int first_status, first_errno, min_outstanding, started, finished, released;
    uint64_t next_sequence, expected, last_sequence;
    int64_t begin, elapsed, last_progress, max_resubmit, median_resubmit;
    int64_t starvation_count, zero_outstanding_count, horizon_count;
    atomic_int stop;
    atomic_llong published[14];
};
int tbs_stream_init(struct tbs_stream *s, struct tbs_stream_ops ops,
                    int depth, int chunk, int max_bytes, int duration_ms);
void tbs_stream_request_stop(struct tbs_stream *s);
int tbs_stream_run(struct tbs_stream *s);
/* Thread-safe snapshot; outstanding is submitted-minus-reaped, NOT hardware queue occupancy. */
void tbs_stream_snapshot(const struct tbs_stream *s, int64_t values[14]);
/* A failed ownership proof intentionally retains all kernel-referenced allocations. */
int tbs_stream_destroy(struct tbs_stream *s);
const char *tbs_stream_reason_name(int reason);
#endif
