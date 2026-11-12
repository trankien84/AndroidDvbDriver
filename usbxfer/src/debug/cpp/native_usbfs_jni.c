// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_core.h"
#include <jni.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <time.h>

struct receiver { struct tbs_core core; int fd; };
static void *allocate(void *opaque) { (void)opaque; return calloc(1, sizeof(struct usbdevfs_urb)); }
static void release(void *opaque, void *request) { (void)opaque; free(request); }
static void prepare(void *request, unsigned char *buffer, int capacity, void *context) {
    struct usbdevfs_urb *urb = request;
    urb->type = USBDEVFS_URB_TYPE_BULK; urb->endpoint = 0x82;
    urb->status = -EINPROGRESS; urb->buffer = buffer; urb->buffer_length = capacity;
    urb->flags = 0; urb->number_of_packets = 0; urb->stream_id = 0; urb->usercontext = context;
}
static struct tbs_syscall submit(void *opaque, void *request) {
    int rc = ioctl(((struct receiver *)opaque)->fd, USBDEVFS_SUBMITURB, request);
    int saved_errno = rc < 0 ? errno : 0;
    return (struct tbs_syscall){rc, saved_errno};
}
static struct tbs_syscall reap(void *opaque, void **request) {
    int rc = ioctl(((struct receiver *)opaque)->fd, USBDEVFS_REAPURBNDELAY, request);
    int saved_errno = rc < 0 ? errno : 0;
    return (struct tbs_syscall){rc, saved_errno};
}
static struct tbs_syscall discard(void *opaque, void *request) {
    int rc = ioctl(((struct receiver *)opaque)->fd, USBDEVFS_DISCARDURB, request);
    int saved_errno = rc < 0 ? errno : 0;
    return (struct tbs_syscall){rc, saved_errno};
}
static void metadata(void *request, struct tbs_meta *m) {
    struct usbdevfs_urb *urb = request;
    *m = (struct tbs_meta){urb->status, urb->actual_length, urb->buffer_length,
        urb->type, urb->endpoint, urb->number_of_packets, urb->flags, urb->stream_id, urb->buffer, urb->usercontext};
}
static int64_t nanos(void *opaque) {
    (void)opaque; struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t) != 0) return -1;
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}
static struct receiver *get(jlong handle) { return (struct receiver *)(uintptr_t)handle; }
#define JNI(name) Java_info_martinmarinov_usbxfer_NativeUsbfsUrb_##name
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jclass cls, jint fd, jint budget, jint chunk_bytes) {
    (void)env; (void)cls;
    if (fd < 0 || budget < TBS_DEPTH || budget > TBS_MAX_REQUESTS
            || (chunk_bytes != TBS_CHUNK && chunk_bytes != TBS_MAX_CHUNK)
            || budget > TBS_MAX_BYTES / chunk_bytes) return 0;
    struct receiver *r = calloc(1, sizeof(*r));
    if (!r) return 0;
    r->fd = fd;
    struct tbs_ops ops = {r, allocate, release, prepare, submit, reap, discard, metadata, nanos};
    if (tbs_init_sized(&r->core, ops, budget, chunk_bytes) != 0) { free(r); return 0; }
    return (jlong)(uintptr_t)r;
}
JNIEXPORT jint JNICALL JNI(fill)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; return tbs_fill(&get(h)->core);
}
JNIEXPORT jint JNICALL JNI(poll)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; return tbs_poll(&get(h)->core);
}
JNIEXPORT jint JNICALL JNI(take)(JNIEnv *env, jclass cls, jlong h, jbyteArray bytes) {
    (void)cls; struct tbs_core *c = &get(h)->core;
    if (!bytes || (*env)->GetArrayLength(env, bytes) < c->chunk_bytes) { tbs_quarantine(c); return TBS_ERROR; }
    unsigned char copy[TBS_MAX_CHUNK];
    int count = tbs_take(c, copy, sizeof(copy));
    if (count > 0) {
        (*env)->SetByteArrayRegion(env, bytes, 0, count, (const jbyte *)copy);
        if ((*env)->ExceptionCheck(env)) { tbs_stop(c); return TBS_ERROR; }
    }
    return count;
}
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; tbs_stop(&get(h)->core);
}
JNIEXPORT void JNICALL JNI(quarantine)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; tbs_quarantine(&get(h)->core);
}
JNIEXPORT jlongArray JNICALL JNI(events)(JNIEnv *env, jclass cls, jlong h) {
    (void)cls; struct tbs_core *c = &get(h)->core;
    /* At most one 8-slot discard batch plus one operation is returned at a time. */
    int n = c->event_count - c->event_cursor;
    if (n > 16) n = 16;
    jlong data[16 * 14];
    for (int i = 0; i < n; ++i) {
        struct tbs_event *e = &c->events[c->event_cursor + i];
        jlong row[] = {e->kind,e->slot,e->result,e->error,e->status,e->actual,e->requested,e->capacity,
            e->endpoint,e->flags,e->sequence,e->before,e->after,c->outstanding};
        for (int j = 0; j < 14; ++j) data[i * 14 + j] = row[j];
    }
    jlongArray result = (*env)->NewLongArray(env, n * 14);
    if (!result) return NULL;
    (*env)->SetLongArrayRegion(env, result, 0, n * 14, data);
    if (!(*env)->ExceptionCheck(env)) c->event_cursor += n;
    return result;
}
JNIEXPORT jlongArray JNICALL JNI(snapshot)(JNIEnv *env, jclass cls, jlong h) {
    (void)cls; struct tbs_core *c = &get(h)->core;
    jlong data[] = {c->attempted,c->submitted,c->completed,c->outstanding,c->delivered,
        c->cleanup_bytes,c->uncertain,c->last_errno,c->event_count,c->last_status,c->last_actual,
        c->last_slot,c->last_sequence,c->last_return};
    jlongArray result = (*env)->NewLongArray(env, 14);
    if (result) (*env)->SetLongArrayRegion(env, result, 0, 14, data);
    return result;
}
JNIEXPORT jboolean JNICALL JNI(destroy)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; struct receiver *r = get(h);
    if (tbs_destroy(&r->core) != 0) return JNI_FALSE;
    free(r); return JNI_TRUE;
}
