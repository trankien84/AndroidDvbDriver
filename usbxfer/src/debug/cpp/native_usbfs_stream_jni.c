// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_usbfs_stream.h"
#include <jni.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <poll.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <time.h>

struct stream_receiver { struct tbs_stream stream; int fd; };
static void *allocate(void *opaque) { (void)opaque; return calloc(1, sizeof(struct usbdevfs_urb)); }
static void release(void *opaque, void *request) { (void)opaque; free(request); }
static void prepare(void *request, unsigned char *buffer, int capacity, void *context) {
    struct usbdevfs_urb *u = request;
    u->type = USBDEVFS_URB_TYPE_BULK; u->endpoint = 0x82; u->status = -EINPROGRESS;
    u->buffer = buffer; u->buffer_length = capacity; u->actual_length = 0;
    u->flags = 0; u->number_of_packets = 0; u->stream_id = 0; u->usercontext = context;
}
static struct tbs_stream_call submit(void *opaque, void *request) {
    int rc = ioctl(((struct stream_receiver *)opaque)->fd, USBDEVFS_SUBMITURB, request);
    return (struct tbs_stream_call){rc, rc < 0 ? errno : 0};
}
static struct tbs_stream_call reap(void *opaque, void **request) {
    int rc = ioctl(((struct stream_receiver *)opaque)->fd, USBDEVFS_REAPURBNDELAY, request);
    return (struct tbs_stream_call){rc, rc < 0 ? errno : 0};
}
static struct tbs_stream_call discard(void *opaque, void *request) {
    int rc = ioctl(((struct stream_receiver *)opaque)->fd, USBDEVFS_DISCARDURB, request);
    return (struct tbs_stream_call){rc, rc < 0 ? errno : 0};
}
static void metadata(void *request, struct tbs_stream_meta *m) {
    struct usbdevfs_urb *u = request;
    *m = (struct tbs_stream_meta){u->status, u->actual_length, u->buffer_length, u->type,
        u->endpoint, u->number_of_packets, u->flags, u->stream_id, u->buffer, u->usercontext};
}
static int64_t nanos(void *opaque) {
    (void)opaque; struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t) != 0) return -1;
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}
static struct tbs_stream_call wait_ready(void *opaque, int millis) {
    /* usbfs signals completed URBs with POLLOUT, not bulk payload readability. */
    struct pollfd p = {((struct stream_receiver *)opaque)->fd, POLLOUT | POLLERR | POLLHUP, 0};
    int rc = poll(&p, 1, millis);
    if (rc > 0 && (p.revents & POLLNVAL)) return (struct tbs_stream_call){-1, EBADF};
    if (rc > 0 && (p.revents & (POLLERR | POLLHUP))) return (struct tbs_stream_call){-1, ENODEV};
    return (struct tbs_stream_call){rc, rc < 0 ? errno : 0};
}
static struct stream_receiver *get(jlong h) { return (struct stream_receiver *)(uintptr_t)h; }
static void io_error(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls) (*env)->ThrowNew(env, cls, message);
}
#define JNI(name) Java_info_martinmarinov_usbxfer_NativeUsbfsStream_##name
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jclass cls, jint fd, jint depth, jint chunk,
                                    jint max_bytes, jint duration_ms) {
    (void)env; (void)cls;
    if (fd < 0) return 0;
    struct stream_receiver *r = calloc(1, sizeof(*r));
    if (!r) return 0;
    r->fd = fd;
    struct tbs_stream_ops ops = {r, allocate, release, prepare, submit, reap, discard, metadata, nanos, wait_ready};
    if (tbs_stream_init(&r->stream, ops, depth, chunk, max_bytes, duration_ms) != 0) { free(r); return 0; }
    return (jlong)(uintptr_t)r;
}
JNIEXPORT void JNICALL JNI(run)(JNIEnv *env, jclass cls, jlong h) {
    (void)cls;
    if (tbs_stream_run(&get(h)->stream) != 0) io_error(env, "Native stream cannot be run twice");
}
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; tbs_stream_request_stop(&get(h)->stream);
}
JNIEXPORT jint JNICALL JNI(copy)(JNIEnv *env, jclass cls, jlong h, jbyteArray bytes) {
    (void)cls; struct tbs_stream *s = &get(h)->stream;
    if (!s->finished || !bytes || (*env)->GetArrayLength(env, bytes) < s->delivered) {
        io_error(env, "Native stream copy requires a drained receive and preallocated capacity"); return 0;
    }
    (*env)->SetByteArrayRegion(env, bytes, 0, s->delivered, (const jbyte *)s->data);
    return s->delivered;
}
JNIEXPORT jlongArray JNICALL JNI(state)(JNIEnv *env, jclass cls, jlong h) {
    (void)cls; int64_t values[14]; jlong data[14];
    tbs_stream_snapshot(&get(h)->stream, values);
    for (int i = 0; i < 14; ++i) data[i] = values[i];
    jlongArray a = (*env)->NewLongArray(env, 14);
    if (a) (*env)->SetLongArrayRegion(env, a, 0, 14, data);
    return a;
}
JNIEXPORT jlongArray JNICALL JNI(metrics)(JNIEnv *env, jclass cls, jlong h) {
    (void)cls; struct tbs_stream *s = &get(h)->stream;
    jlong values[] = {s->submitted, s->completed, s->elapsed, s->starvation_count,
        s->max_resubmit, s->median_resubmit, s->min_outstanding, s->first_status,
        s->first_errno, s->outstanding, s->uncertain, atomic_load(&s->stop),
        s->zero_outstanding_count, s->horizon_count};
    jlongArray a = (*env)->NewLongArray(env, 14);
    if (a) (*env)->SetLongArrayRegion(env, a, 0, 14, values);
    return a;
}
JNIEXPORT jlongArray JNICALL JNI(eventBatch)(JNIEnv *env, jclass cls, jlong h, jint offset) {
    (void)cls; struct tbs_stream *s = &get(h)->stream;
    if (!s->finished || offset < 0 || offset > s->event_count) {
        io_error(env, "Native stream events require a drained receive"); return NULL;
    }
    int count = s->event_count - offset;
    if (count > 64) count = 64;
    jlong values[64 * TBS_STREAM_EVENT_WIDTH];
    for (int i = 0; i < count; ++i) for (int j = 0; j < TBS_STREAM_EVENT_WIDTH; ++j)
        values[i * TBS_STREAM_EVENT_WIDTH + j] = s->events[offset + i].v[j];
    jlongArray a = (*env)->NewLongArray(env, count * TBS_STREAM_EVENT_WIDTH);
    if (a) (*env)->SetLongArrayRegion(env, a, 0, count * TBS_STREAM_EVENT_WIDTH, values);
    return a;
}
JNIEXPORT jboolean JNICALL JNI(destroy)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls; struct stream_receiver *r = get(h);
    if (tbs_stream_destroy(&r->stream) != 0) return JNI_FALSE;
    free(r); return JNI_TRUE;
}
