// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_ts_core.h"
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/usbdevice_fs.h>
#include <poll.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

struct ts_receiver { struct tbs_ts stream; int fd; };
static void *allocate(void *opaque) { (void)opaque; return calloc(1, sizeof(struct usbdevfs_urb)); }
static void release(void *opaque, void *request) { (void)opaque; free(request); }
static void prepare(void *request, unsigned char *buffer, int capacity, void *context) {
    struct usbdevfs_urb *u = request;
    u->type = USBDEVFS_URB_TYPE_BULK; u->endpoint = 0x82; u->status = -EINPROGRESS;
    u->buffer = buffer; u->buffer_length = capacity; u->actual_length = 0;
    u->flags = 0; u->number_of_packets = 0; u->stream_id = 0; u->usercontext = context;
}
static struct tbs_ts_call submit(void *opaque, void *request) {
    int rc = ioctl(((struct ts_receiver *)opaque)->fd, USBDEVFS_SUBMITURB, request);
    return (struct tbs_ts_call){rc, rc < 0 ? errno : 0};
}
static struct tbs_ts_call reap(void *opaque, void **request) {
    int rc = ioctl(((struct ts_receiver *)opaque)->fd, USBDEVFS_REAPURBNDELAY, request);
    return (struct tbs_ts_call){rc, rc < 0 ? errno : 0};
}
static struct tbs_ts_call discard(void *opaque, void *request) {
    int rc = ioctl(((struct ts_receiver *)opaque)->fd, USBDEVFS_DISCARDURB, request);
    return (struct tbs_ts_call){rc, rc < 0 ? errno : 0};
}
static void metadata(void *request, struct tbs_ts_meta *m) {
    struct usbdevfs_urb *u = request;
    *m = (struct tbs_ts_meta){u->status, u->actual_length, u->buffer_length, u->type,
        u->endpoint, u->number_of_packets, u->flags, u->stream_id, u->buffer, u->usercontext};
}
static int64_t nanos(void *opaque) {
    (void)opaque; struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t) != 0) return -1;
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}
static struct tbs_ts_call wait_ready(void *opaque, int millis) {
    struct pollfd p = {((struct ts_receiver *)opaque)->fd, POLLOUT | POLLERR | POLLHUP, 0};
    int rc = poll(&p, 1, millis);
    if (rc > 0 && (p.revents & POLLNVAL)) return (struct tbs_ts_call){-1, EBADF};
    if (rc > 0 && (p.revents & (POLLERR | POLLHUP))) return (struct tbs_ts_call){-1, ENODEV};
    return (struct tbs_ts_call){rc, rc < 0 ? errno : 0};
}
static void close_owned(void *opaque) {
    struct ts_receiver *r = opaque;
    if (r->fd >= 0) {
        /* Do not retry Linux close on EINTR: the descriptor is already released.
         * Its last usbfs reference synchronously kills outstanding requests. */
        close(r->fd); r->fd = -1;
    }
}
static struct ts_receiver *get(jlong handle) { return (struct ts_receiver *)(uintptr_t)handle; }
static void io_error(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls) (*env)->ThrowNew(env, cls, message);
}
#define JNI(name) Java_info_martinmarinov_usbxfer_NativeUsbfsByteSource_##name
JNIEXPORT jlong JNICALL JNI(nativeCreate)(JNIEnv *env, jclass cls, jint fd, jint iface, jint alt, jint depth) {
    (void)cls;
    if (fd < 0 || iface != 0 || alt != 0 || (depth != 16 && depth != 32)) {
        io_error(env, "Invalid dedicated USB connection/interface"); return 0;
    }
    struct ts_receiver *r = calloc(1, sizeof(*r));
    if (!r) { io_error(env, "Native stream allocation failed"); return 0; }
    r->fd = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    if (r->fd < 0) { free(r); io_error(env, "Cannot own streaming USB fd"); return 0; }
    /* Pinned operational descriptors contain only interface 0 / alternate 0.
     * Do not send an unnecessary standard SET_INTERFACE (request 11) after reload. */
    unsigned interface_number = 0, endpoint = 0x82;
    if (ioctl(r->fd, USBDEVFS_CLAIMINTERFACE, &interface_number) < 0
            || ioctl(r->fd, USBDEVFS_CLEAR_HALT, &endpoint) < 0) {
        close_owned(r); free(r); io_error(env, "Cannot prepare streaming USB interface/endpoint"); return 0;
    }
    struct tbs_ts_ops ops = {r, allocate, release, prepare, submit, reap, discard, metadata, nanos, wait_ready, close_owned};
    if (tbs_ts_init_config(&r->stream, ops, depth) != 0) {
        free(r); io_error(env, "Cannot preallocate streaming ring/URBs"); return 0;
    }
    return (jlong)(uintptr_t)r;
}
JNIEXPORT jint JNICALL JNI(nativeRun)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    struct tbs_ts *s = &get(handle)->stream;
    if (tbs_ts_run(s) != 0) return -EINVAL;
    return atomic_load(&s->stop);
}
/* Only the Java worker calls this after nativeRun returns, before close can
 * destroy its handle. No live snapshots or allocations are added to the loop. */
JNIEXPORT jintArray JNICALL JNI(nativeErrorDetails)(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    struct tbs_ts *s = &get(handle)->stream;
    if (atomic_load_explicit(&s->state, memory_order_acquire) != 2) {
        io_error(env, "Native TS error details require completed run"); return NULL;
    }
    jint values[] = {atomic_load(&s->stop), s->status, s->error};
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 3, values);
    return result;
}
JNIEXPORT void JNICALL JNI(nativeStop)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls; tbs_ts_stop(&get(handle)->stream);
}
JNIEXPORT jint JNICALL JNI(nativeRead)(JNIEnv *env, jclass cls, jlong handle, jbyteArray bytes) {
    (void)cls;
    if (!bytes || (*env)->GetArrayLength(env, bytes) < TBS_TS_CHUNK) {
        io_error(env, "Native TS consumer buffer too small"); return -EIO;
    }
    struct tbs_ts *s = &get(handle)->stream;
    const unsigned char *data = NULL;
    int count = tbs_ts_peek(s, &data);
    if (count > 0) {
        (*env)->SetByteArrayRegion(env, bytes, 0, count, (const jbyte *)data);
        if (!(*env)->ExceptionCheck(env)) tbs_ts_consume(s);
        if (atomic_load(&s->stop) > TBS_TS_STOP) return -EIO;
    }
    return count;
}
/* Header: capacity,count,overflow,clockInvalid,finished,reason,begin,end; then triples
 * monotonicNanos,status,length. Record slots are immutable after release-publish. */
JNIEXPORT jlongArray JNICALL JNI(nativeCompletions)(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    struct tbs_ts *s = &get(handle)->stream;
    int finished = atomic_load_explicit(&s->state, memory_order_acquire) == 2;
    unsigned count = atomic_load_explicit(&s->completion_count, memory_order_acquire);
    jlong header[] = {TBS_TS_COMPLETIONS, count,
        atomic_load_explicit(&s->completion_overflow, memory_order_acquire),
        atomic_load_explicit(&s->completion_clock_invalid, memory_order_acquire),
        finished, atomic_load(&s->stop), atomic_load(&s->begin_nanos), atomic_load(&s->end_nanos)};
    jlongArray result = (*env)->NewLongArray(env, 8 + (jsize)count * 3);
    if (!result) return NULL;
    (*env)->SetLongArrayRegion(env, result, 0, 8, header);
    for (unsigned offset = 0; offset < count; offset += 64) {
        unsigned n = count - offset; if (n > 64) n = 64;
        jlong batch[64 * 3];
        for (unsigned i = 0; i < n; ++i) {
            const struct tbs_ts_completion *c = &s->completions[offset + i];
            batch[i * 3] = c->monotonic_nanos;
            batch[i * 3 + 1] = c->status;
            batch[i * 3 + 2] = c->length;
        }
        (*env)->SetLongArrayRegion(env, result, 8 + (jsize)offset * 3, (jsize)n * 3, batch);
        if ((*env)->ExceptionCheck(env)) return NULL;
    }
    return result;
}
JNIEXPORT jboolean JNICALL JNI(nativeDestroy)(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    struct ts_receiver *r = get(handle);
    int drain_failed = r->stream.drain_failed;
    if (tbs_ts_destroy(&r->stream) != 0) {
        io_error(env, "Cannot free active native stream"); return JNI_FALSE;
    }
    free(r);
    return drain_failed ? JNI_FALSE : JNI_TRUE;
}
