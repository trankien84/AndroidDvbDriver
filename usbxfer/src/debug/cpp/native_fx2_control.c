// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include "native_fx2_plan.h"
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <time.h>

struct fx2_control { int fd, ordinal, stopped; unsigned char image[8192]; };
static int64_t boot_nanos(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_BOOTTIME, &t) != 0) return -1;
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}
#define JNI(name) Java_info_martinmarinov_usbxfer_NativeFx2Control_##name
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jclass cls, jint fd, jbyteArray image) {
    (void)cls;
    if (fd < 0 || !image || (*env)->GetArrayLength(env, image) != 8192) return 0;
    struct fx2_control *c = calloc(1, sizeof(*c));
    if (!c) return 0;
    c->fd = fd;
    (*env)->GetByteArrayRegion(env, image, 0, 8192, (jbyte *)c->image);
    if ((*env)->ExceptionCheck(env)) { free(c); return 0; }
    return (jlong)(uintptr_t)c;
}
JNIEXPORT jlongArray JNICALL JNI(next)(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    struct fx2_control *c = (struct fx2_control *)(uintptr_t)handle;
    if (!c || c->stopped || c->ordinal >= 132) return NULL;
    /* Allocate the return array before dispatch: allocation failure must not lose a native result. */
    jlongArray out = (*env)->NewLongArray(env, 7);
    if (!out) { c->stopped = 1; return NULL; }
    int ordinal = ++c->ordinal;
    unsigned char reset;
    struct tbs_fx2_step step;
    if (tbs_fx2_step(ordinal, c->image, sizeof(c->image), &reset, &step) != 0) { c->stopped = 1; return NULL; }
    int value = step.value, length = step.length;
    struct usbdevfs_ctrltransfer control = {0};
    control.bRequestType = step.type; control.bRequest = step.request; control.wValue = value;
    control.wIndex = step.index; control.wLength = length; control.timeout = step.timeout; control.data = (void *)step.payload;
    int64_t before = boot_nanos();
    if (before < 0) { c->stopped = 1; return NULL; }
    int result = ioctl(c->fd, USBDEVFS_CONTROL, &control);
    int saved_errno = result < 0 ? errno : 0;
    int64_t after = boot_nanos();
    if (result != length || saved_errno != 0 || after < before || ordinal == 132) c->stopped = 1;
    jlong row[] = {ordinal, value, length, result, saved_errno, before, after};
    (*env)->SetLongArrayRegion(env, out, 0, 7, row);
    return out;
}
JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    struct fx2_control *c = (struct fx2_control *)(uintptr_t)handle;
    if (c) { c->stopped = 1; free(c); }
}
