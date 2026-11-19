// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_fx2_reload_core.h"
#include <jni.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>

struct reload_context { JNIEnv *env; jobject guard; jmethodID require; int fd; };
static int require_guard(void *opaque) {
    struct reload_context *c = opaque;
    (*c->env)->CallVoidMethod(c->env, c->guard, c->require);
    return (*c->env)->ExceptionCheck(c->env) ? -1 : 0;
}
static int write_step(void *opaque, const struct tbs_reload_step *step, int *error) {
    struct reload_context *c = opaque;
    struct usbdevfs_ctrltransfer control = {0};
    control.bRequestType = step->type; control.bRequest = step->request;
    control.wValue = step->value; control.wIndex = step->index;
    control.wLength = step->length; control.timeout = step->timeout; control.data = (void *)step->payload;
    int rc = ioctl(c->fd, USBDEVFS_CONTROL, &control);
    *error = rc < 0 ? errno : 0;
    return rc;
}

JNIEXPORT jlongArray JNICALL Java_info_martinmarinov_usbxfer_NativeUsbfsControl_nativeReload(
        JNIEnv *env, jclass cls, jint fd, jbyteArray image, jobject guard) {
    (void)cls;
    if (fd < 0 || !image || !guard || (*env)->GetArrayLength(env, image) != 8192) return NULL;
    jclass guard_class = (*env)->GetObjectClass(env, guard);
    if (!guard_class) return NULL;
    jmethodID require = (*env)->GetMethodID(env, guard_class, "require", "()V");
    if (!require) return NULL;
    unsigned char firmware[8192];
    (*env)->GetByteArrayRegion(env, image, 0, 8192, (jbyte *)firmware);
    if ((*env)->ExceptionCheck(env)) return NULL;
    jlongArray result = (*env)->NewLongArray(env, 4);
    if (!result) return NULL;
    struct reload_context context = {env, guard, require, fd};
    struct tbs_reload_ops ops = {&context, require_guard, write_step};
    struct tbs_reload_result completed;
    tbs_reload_run(firmware, sizeof(firmware), ops, &completed);
    if ((*env)->ExceptionCheck(env)) return NULL;
    jlong row[] = {completed.completed, completed.ordinal, completed.result, completed.error};
    (*env)->SetLongArrayRegion(env, result, 0, 4, row);
    return result;
}
