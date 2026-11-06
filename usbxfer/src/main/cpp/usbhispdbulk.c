#include <jni.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <errno.h>
#include <string.h>
#include <android/log.h>

#define TAG "JNI_HiSpeedBulk"

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_UsbHiSpeedBulk_jni_1clearHalt82(JNIEnv *env, jclass type, jint fd) {
    unsigned int endpoint = 0x82;
    if (fd < 0) return -EINVAL;
    int result = ioctl(fd, USBDEVFS_CLEAR_HALT, &endpoint);
    int saved_errno = errno;
    return result < 0 ? -saved_errno : result;
}

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_UsbHiSpeedBulk_jni_1readBulk82(JNIEnv *env, jclass type, jint fd,
        jbyteArray bytes, jint length, jint timeout, jintArray error) {
    if (error == NULL || (*env)->GetArrayLength(env, error) != 1) return -1;
    jint saved_errno = 0;
    int result = -1;
    if (fd < 0 || bytes == NULL || length < 1 || length > 16384 || timeout < 1 || timeout > 2000
            || length > (*env)->GetArrayLength(env, bytes)) {
        saved_errno = EINVAL;
    } else {
        jbyte *buffer = (*env)->GetByteArrayElements(env, bytes, NULL);
        if (buffer == NULL) return -1;
        struct usbdevfs_bulktransfer transfer = {0};
        transfer.ep = 0x82;
        transfer.len = length;
        transfer.timeout = timeout;
        transfer.data = buffer;
        result = ioctl(fd, USBDEVFS_BULK, &transfer);
        saved_errno = result < 0 ? errno : 0;
        (*env)->ReleaseByteArrayElements(env, bytes, buffer, result < 0 ? JNI_ABORT : 0);
    }
    (*env)->SetIntArrayRegion(env, error, 0, 1, &saved_errno);
    return result;
}

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_UsbHiSpeedBulk_jni_1setInterface(JNIEnv *env, jclass type, jint fd,
                                                        jint interfaceId, jint alternateSettings) {
    struct usbdevfs_setinterface setint;
    setint.altsetting = (unsigned int) alternateSettings;
    setint.interface = (unsigned int) interfaceId;

    if (ioctl(fd, USBDEVFS_SETINTERFACE, &setint)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ioctl error %d text %s", errno, strerror(errno));
        return -errno;
    } else {
        return 0;
    }
}