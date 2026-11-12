// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef TBS_NATIVE_USBFS_CORE_H
#define TBS_NATIVE_USBFS_CORE_H
#include <stddef.h>
#include <stdint.h>

#define TBS_DEPTH 8
#define TBS_CHUNK 4096
#define TBS_MAX_CHUNK 8192
#define TBS_MAX_REQUESTS 256
#define TBS_MAX_BYTES (TBS_MAX_REQUESTS * TBS_CHUNK)
#define TBS_MAX_EVENTS 4096
#define TBS_STATUS_UNKNOWN INT32_MIN

enum tbs_result { TBS_WAIT = -10, TBS_ERROR = -11, TBS_UNCERTAIN = -12, TBS_LIMIT = -13 };
enum tbs_event_kind { TBS_SUBMIT = 1, TBS_REAP = 2, TBS_DISCARD = 3, TBS_GUARD = 4, TBS_COPY = 5 };
struct tbs_meta {
    int status, actual, requested, type, endpoint, packets;
    unsigned flags, stream_id;
    void *buffer, *context;
};
struct tbs_syscall { int result, error; };
struct tbs_ops {
    void *opaque;
    void *(*allocate)(void *opaque);
    void (*release)(void *opaque, void *request);
    void (*prepare)(void *request, unsigned char *buffer, int capacity, void *context);
    struct tbs_syscall (*submit)(void *opaque, void *request);
    struct tbs_syscall (*reap)(void *opaque, void **request);
    struct tbs_syscall (*discard)(void *opaque, void *request);
    void (*metadata)(void *request, struct tbs_meta *meta);
    int64_t (*nanos)(void *opaque);
};
struct tbs_event {
    int kind, slot, result, error, status, actual, requested, capacity, endpoint;
    unsigned flags;
    int64_t sequence, before, after;
};
struct tbs_slot {
    unsigned char *buffer;
    void *request;
    int capacity, pending, ready;
    int64_t sequence;
    struct tbs_meta meta;
};
struct tbs_core {
    struct tbs_ops ops;
    struct tbs_slot slots[TBS_DEPTH];
    void *requests[TBS_MAX_REQUESTS];
    struct tbs_event events[TBS_MAX_EVENTS];
    int allocations, attempted, submitted, completed, outstanding, event_count, event_cursor;
    int chunk_bytes, budget, stopped, uncertain, draining, released, last_errno, last_return;
    int last_status, last_actual, last_slot;
    int64_t last_sequence, expected, delivered, cleanup_bytes;
};
int tbs_init(struct tbs_core *core, struct tbs_ops ops, int budget);
int tbs_init_sized(struct tbs_core *core, struct tbs_ops ops, int budget, int chunk_bytes);
int tbs_fill(struct tbs_core *core);
int tbs_poll(struct tbs_core *core);
int tbs_take(struct tbs_core *core, unsigned char *destination, size_t capacity);
void tbs_stop(struct tbs_core *core);
void tbs_quarantine(struct tbs_core *core);
/* Refuses to free any allocation when a completion/ownership proof is missing. */
int tbs_destroy(struct tbs_core *core);
#endif
