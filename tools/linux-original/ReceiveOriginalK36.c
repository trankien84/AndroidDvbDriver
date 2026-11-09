// SPDX-License-Identifier: GPL-2.0-only
#define _XOPEN_SOURCE 700
#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#if defined(TBS_ORIGINAL_FAKE_ONLY) && defined(_WIN32)
#include "tests/dvb-test-platform.h"
#else
#include <linux/dvb/dmx.h>
#include <linux/dvb/frontend.h>
#include <poll.h>
#include <sys/ioctl.h>
#endif

#define MAX_BYTES (16U * 1024U * 1024U)
#define LOCK_NS INT64_C(10000000000)
#define RECEIVE_NS INT64_C(10000000000)
#define PATH_CAP 4096
/* cxd28xx c5fa27eecbe80633cbb365cc44656daea1c0e2e3, tbs5530.c:tbs5530_dvb_init. */
#define ORIGINAL_NAME "TurboSight TBS 5530 DVB-T/T2/C/C2,ISDB-T/C,ATSC,J83B,ATSC3"

struct receiver_ops {
    int (*resolve)(const char *, char *, size_t);
    int (*text)(const char *, char *, size_t);
    int (*node)(const char *, int, unsigned int, unsigned int);
    int (*open)(const char *, int, unsigned int);
    int (*close)(int);
    int (*sync)(int);
    int (*control)(int, unsigned long, void *);
    int64_t (*clock)(void);
    int (*pause)(void);
    int (*wait)(int, int, short *);
    ssize_t (*read)(int, void *, size_t);
    ssize_t (*write)(int, const void *, size_t);
};

static volatile sig_atomic_t stopped;
static void on_signal(int value) { (void)value; stopped = 1; }

static int parse_adapter(const char *text, unsigned int *adapter) {
    unsigned int value = 0;
    if (!text || !*text) return -1;
    for (; *text; ++text) {
        if (*text < '0' || *text > '9') return -1;
        value = value * 10 + (unsigned int)(*text - '0');
        if (value > 255) return -1;
    }
    *adapter = value;
    return 0;
}

static int within(int64_t begin, int64_t current, int64_t budget) {
    return begin >= 0 && current >= begin && current - begin < budget;
}

static size_t next_size(size_t total, size_t capacity) {
    if (total >= MAX_BYTES) return 0;
    return MAX_BYTES - total < capacity ? MAX_BYTES - total : capacity;
}

static void make_profile(struct dtv_property values[6]) {
    memset(values, 0, sizeof(*values) * 6);
    values[0].cmd = DTV_CLEAR;
    values[1].cmd = DTV_DELIVERY_SYSTEM; values[1].u.data = SYS_DVBT2;
    values[2].cmd = DTV_FREQUENCY; values[2].u.data = 594000000;
    values[3].cmd = DTV_BANDWIDTH_HZ; values[3].u.data = 8000000;
    values[4].cmd = DTV_STREAM_ID; values[4].u.data = 0;
    values[5].cmd = DTV_TUNE;
}

static int in_sys_devices(const char *path) {
    return strncmp(path, "/sys/devices/", 13) == 0;
}

static int append_path(char *out, size_t size, const char *root, const char *leaf) {
    int n = snprintf(out, size, "%s/%s", root, leaf);
    if (n < 0 || (size_t)n >= size) { errno = ENAMETOOLONG; return -1; }
    return 0;
}

static int hex_id(const char *text, unsigned int expected) {
    unsigned int value = 0;
    for (size_t i = 0; i < 4; ++i) {
        unsigned int digit;
        if (text[i] >= '0' && text[i] <= '9') digit = (unsigned int)(text[i] - '0');
        else if (text[i] >= 'a' && text[i] <= 'f') digit = (unsigned int)(text[i] - 'a') + 10;
        else if (text[i] >= 'A' && text[i] <= 'F') digit = (unsigned int)(text[i] - 'A') + 10;
        else return 0;
        value = value * 16 + digit;
    }
    return value == expected && (!text[4] || (text[4] == '\n' && !text[5]));
}

static int usb_parent(const struct receiver_ops *ops, const char *device, char parent[PATH_CAP]) {
    char path[PATH_CAP], vendor[32], product[32];
    if (ops->resolve(device, parent, PATH_CAP) || !in_sys_devices(parent)) return -1;
    while (in_sys_devices(parent)) {
        int have_vendor, have_product, vendor_error, product_error;
        if (append_path(path, sizeof(path), parent, "idVendor")) return -1;
        have_vendor = ops->text(path, vendor, sizeof(vendor)); vendor_error = errno;
        if (append_path(path, sizeof(path), parent, "idProduct")) return -1;
        have_product = ops->text(path, product, sizeof(product)); product_error = errno;
        if (!have_vendor || !have_product) {
            if (have_vendor || have_product || !hex_id(vendor, 0x734c) || !hex_id(product, 0x5530)) return -1;
            return 0;
        }
        if (vendor_error != ENOENT || product_error != ENOENT) return -1;
        char *slash = strrchr(parent, '/');
        if (!slash) return -1;
        *slash = '\0';
    }
    return -1;
}

static int parse_dev(const char *text, unsigned int *major_id, unsigned int *minor_id) {
    unsigned int values[2] = {0, 0};
    for (unsigned int field = 0; field < 2; ++field) {
        if (*text < '0' || *text > '9') return -1;
        while (*text >= '0' && *text <= '9') {
            unsigned int digit = (unsigned int)(*text++ - '0');
            if (values[field] > (UINT32_MAX - digit) / 10) return -1;
            values[field] = values[field] * 10 + digit;
        }
        if (!field && *text++ != ':') return -1;
    }
    if (*text && !(*text == '\n' && !text[1])) return -1;
    *major_id = values[0]; *minor_id = values[1];
    return 0;
}

static int check_node(const struct receiver_ops *ops, unsigned int adapter, const char *name,
                      const char *expected, int fd, char node_path[PATH_CAP]) {
    char base[128], path[PATH_CAP], parent[PATH_CAP], value[64];
    unsigned int major_id, minor_id;
    int n = snprintf(base, sizeof(base), "/sys/class/dvb/dvb%u.%s", adapter, name);
    if (n < 0 || (size_t)n >= sizeof(base) ||
        append_path(path, sizeof(path), base, "device") || usb_parent(ops, path, parent) ||
        strcmp(parent, expected) || append_path(path, sizeof(path), base, "dev") ||
        ops->text(path, value, sizeof(value)) || parse_dev(value, &major_id, &minor_id)) return -1;
    n = snprintf(node_path, PATH_CAP, "/dev/dvb/adapter%u/%s", adapter, name);
    if (n < 0 || n >= PATH_CAP) return -1;
    return ops->node(node_path, fd, major_id, minor_id);
}

static int guarded_open(const struct receiver_ops *ops, unsigned int adapter, const char *name,
                        const char *expected, int flags) {
    char path[PATH_CAP];
    int fd;
    if (stopped || check_node(ops, adapter, name, expected, -1, path)) return -1;
    if (stopped) return -1;
    fd = ops->open(path, flags | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK, 0);
    if (fd >= 0 && check_node(ops, adapter, name, expected, fd, path)) {
        ops->close(fd);
        return -1;
    }
    return fd;
}

static int frontend_info(const struct receiver_ops *ops, int fd) {
    struct dvb_frontend_info info;
    memset(&info, 0, sizeof(info));
    if (ops->control(fd, FE_GET_INFO, &info)) return -1;
    return memchr(info.name, '\0', sizeof(info.name)) && !strcmp(info.name, ORIGINAL_NAME) ? 0 : -1;
}

static int receive_original(const struct receiver_ops *ops, const char *adapter_arg,
                            const char *expected_arg, const char *output_path) {
    unsigned int adapter;
    char expected[PATH_CAP], path[PATH_CAP];
    unsigned char bytes[65536];
    struct dtv_property profile[6];
    struct dtv_properties properties;
    struct dmx_pes_filter_params filter;
    fe_status_t status = 0;
    int probe = -1, fe = -1, demux = -1, dvr = -1, output = -1;
    int result = 1, filter_attempted = 0, lock = 0;
    int64_t lock_begin, receive_begin, current;
    size_t total = 0;
    const char *reason = "identity";
    if (parse_adapter(adapter_arg, &adapter) || !expected_arg || expected_arg[0] != '/' ||
        !output_path || !*output_path) { reason = "arguments"; result = 2; goto cleanup; }
    if (stopped) { reason = "stopped"; goto cleanup; }
    if (ops->resolve(expected_arg, expected, sizeof(expected)) || !in_sys_devices(expected) ||
        check_node(ops, adapter, "frontend0", expected, -1, path)) goto cleanup;
    probe = guarded_open(ops, adapter, "frontend0", expected, O_RDONLY);
    if (probe < 0 || stopped || frontend_info(ops, probe)) goto cleanup;
    if (ops->close(probe)) { probe = -1; reason = "close_probe"; goto cleanup; }
    probe = -1;
    if (stopped) { reason = "stopped"; goto cleanup; }
    if (check_node(ops, adapter, "frontend0", expected, -1, path)) goto cleanup;
    if (stopped) { reason = "stopped"; goto cleanup; }
    /* The read-only name probe and USB ancestry guard precede every writable open. */
    output = ops->open(output_path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (output < 0) { reason = "output_open"; goto cleanup; }
    fe = guarded_open(ops, adapter, "frontend0", expected, O_RDWR);
    if (fe < 0 || stopped || frontend_info(ops, fe) ||
        check_node(ops, adapter, "frontend0", expected, fe, path)) goto cleanup;
    make_profile(profile); properties.num = 6; properties.props = profile;
    lock_begin = ops->clock();
    reason = "tune";
    if (stopped || lock_begin < 0 || ops->control(fe, FE_SET_PROPERTY, &properties)) goto cleanup;
    for (size_t i = 0; i < 6; ++i) if (profile[i].result) goto cleanup;
    reason = "lock_timeout";
    while (!stopped) {
        current = ops->clock();
        if (current < lock_begin) { reason = "clock"; goto cleanup; }
        if (!within(lock_begin, current, LOCK_NS)) break;
        if (ops->control(fe, FE_READ_STATUS, &status)) { reason = "status"; goto cleanup; }
        current = ops->clock();
        if (current < lock_begin) { reason = "clock"; goto cleanup; }
        if (!within(lock_begin, current, LOCK_NS)) break;
        if (status & FE_HAS_LOCK) { lock = 1; break; }
        if (ops->pause() && errno != EINTR) { reason = "sleep"; goto cleanup; }
    }
    fprintf(stderr, "{\"event\":\"lock\",\"clock\":\"CLOCK_MONOTONIC\",\"ns\":%lld,\"status\":%u,\"locked\":%s}\n",
            (long long)ops->clock(), (unsigned int)status, lock ? "true" : "false");
    if (stopped || !lock) goto cleanup;
    demux = guarded_open(ops, adapter, "demux0", expected, O_RDWR);
    if (demux < 0 || stopped) { reason = "demux_open"; goto cleanup; }
    dvr = guarded_open(ops, adapter, "dvr0", expected, O_RDONLY);
    if (dvr < 0 || stopped) { reason = "dvr_open"; goto cleanup; }
    memset(&filter, 0, sizeof(filter));
    filter.pid = 0x2000; filter.input = DMX_IN_FRONTEND; filter.output = DMX_OUT_TS_TAP;
    filter.pes_type = DMX_PES_OTHER; filter.flags = DMX_IMMEDIATE_START;
    if (stopped) { reason = "stopped"; goto cleanup; }
    filter_attempted = 1;
    if (ops->control(demux, DMX_SET_PES_FILTER, &filter)) { reason = "filter"; goto cleanup; }
    receive_begin = ops->clock();
    if (receive_begin < 0) { reason = "clock"; goto cleanup; }
    reason = "receive_timeout";
    while (!stopped && total < MAX_BYTES) {
        short events = 0;
        int ready, timeout;
        ssize_t count;
        current = ops->clock();
        if (current < receive_begin) { reason = "clock"; goto cleanup; }
        if (!within(receive_begin, current, RECEIVE_NS)) break;
        timeout = (int)((RECEIVE_NS - (current - receive_begin)) / INT64_C(1000000));
        if (timeout > 100) timeout = 100;
        if (!timeout) continue;
        ready = ops->wait(dvr, timeout, &events);
        if (stopped) break;
        if (ready < 0) { if (errno == EINTR) continue; reason = "poll"; goto cleanup; }
        if (!ready) continue;
        if (events & (POLLERR | POLLHUP | POLLNVAL)) { reason = "poll_events"; goto cleanup; }
        if (!(events & POLLIN)) continue;
        current = ops->clock();
        if (current < receive_begin) { reason = "clock"; goto cleanup; }
        if (!within(receive_begin, current, RECEIVE_NS)) break;
        count = ops->read(dvr, bytes, next_size(total, sizeof(bytes)));
        if (count < 0) {
            if (errno == EAGAIN || errno == EINTR) continue;
            reason = "read"; goto cleanup;
        }
        if (!count) { reason = "read_zero"; goto cleanup; }
        if ((size_t)count > next_size(total, sizeof(bytes))) { reason = "read_size"; goto cleanup; }
        for (size_t offset = 0; offset < (size_t)count;) {
            ssize_t written;
            current = ops->clock();
            if (stopped) { reason = "stopped"; goto cleanup; }
            if (current < receive_begin) { reason = "clock"; goto cleanup; }
            if (!within(receive_begin, current, RECEIVE_NS)) { reason = "write_timeout"; goto cleanup; }
            written = ops->write(output, bytes + offset, (size_t)count - offset);
            if (written < 0 && errno == EINTR) continue;
            if (written <= 0 || (size_t)written > (size_t)count - offset) { reason = "write"; goto cleanup; }
            offset += (size_t)written;
            total += (size_t)written;
        }
    }
    if (total == MAX_BYTES) reason = "byte_cap";
    result = total && !stopped ? 0 : 1;
cleanup:
    if (stopped) { reason = "stopped"; result = 1; }
    if (filter_attempted && ops->control(demux, DMX_STOP, NULL)) { result = 1; reason = "dmx_stop"; }
    if (dvr >= 0 && ops->close(dvr)) { result = 1; reason = "close_dvr"; }
    if (demux >= 0 && ops->close(demux)) { result = 1; reason = "close_demux"; }
    if (fe >= 0 && ops->close(fe)) { result = 1; reason = "close_frontend"; }
    if (probe >= 0 && ops->close(probe)) { result = 1; reason = "close_probe"; }
    if (output >= 0) {
        if (ops->sync(output)) { result = 1; reason = "fsync"; }
        if (ops->close(output)) { result = 1; reason = "close_output"; }
    }
    if (stopped) result = 1;
    fprintf(stderr, "{\"event\":\"receive_end\",\"clock\":\"CLOCK_MONOTONIC\",\"ns\":%lld,\"bytes\":%zu,\"result\":%d,\"reason\":\"%s\",\"stopped\":%s}\n",
            (long long)ops->clock(), total, result, reason, stopped ? "true" : "false");
    return result;
}

#ifndef TBS_ORIGINAL_FAKE_ONLY
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <time.h>
#include <unistd.h>

static int real_resolve(const char *path, char *out, size_t capacity) {
    char resolved[PATH_CAP];
    if (!realpath(path, resolved)) return -1;
    if (strlen(resolved) >= capacity) { errno = ENAMETOOLONG; return -1; }
    strcpy(out, resolved);
    return 0;
}

static int real_text(const char *path, char *out, size_t capacity) {
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    ssize_t n;
    int saved;
    if (fd < 0) return -1;
    do { n = read(fd, out, capacity - 1); } while (n < 0 && errno == EINTR && !stopped);
    saved = errno;
    if (close(fd)) return -1;
    if (n < 0) { errno = saved; return -1; }
    if (!n || (size_t)n == capacity - 1 || memchr(out, '\0', (size_t)n)) { errno = EINVAL; return -1; }
    out[n] = '\0';
    return 0;
}

static int real_node(const char *path, int fd, unsigned int major_id, unsigned int minor_id) {
    struct stat value;
    if (fd < 0 ? lstat(path, &value) : fstat(fd, &value)) return -1;
    if (!S_ISCHR(value.st_mode) || major(value.st_rdev) != major_id || minor(value.st_rdev) != minor_id) {
        errno = ENODEV; return -1;
    }
    return 0;
}

static int real_open(const char *path, int flags, unsigned int mode) { return open(path, flags, (mode_t)mode); }
static int real_control(int fd, unsigned long request, void *arg) {
    switch (request) {
        case FE_GET_INFO: case FE_SET_PROPERTY: case FE_READ_STATUS:
        case DMX_SET_PES_FILTER: case DMX_STOP: return ioctl(fd, request, arg);
        default: errno = EPERM; return -1;
    }
}
static int64_t real_clock(void) {
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value)) return -1;
    return (int64_t)value.tv_sec * INT64_C(1000000000) + value.tv_nsec;
}
static int real_pause(void) {
    struct timespec value = {0, 100000000};
    return nanosleep(&value, NULL);
}
static int real_wait(int fd, int timeout, short *events) {
    struct pollfd value = {fd, POLLIN, 0};
    int result = poll(&value, 1, timeout);
    *events = value.revents;
    return result;
}
int main(int argc, char **argv) {
    struct sigaction action;
    const struct receiver_ops ops = {
        real_resolve, real_text, real_node, real_open, close, fsync, real_control,
        real_clock, real_pause, real_wait, read, write
    };
    if (argc != 4) {
        fprintf(stderr, "Usage: ReceiveOriginalK36 <adapter 0..255> <expected USB parent /sys/devices/...> <new TS file>\n");
        return 2;
    }
    memset(&action, 0, sizeof(action)); action.sa_handler = on_signal;
    if (sigemptyset(&action.sa_mask) || sigaction(SIGINT, &action, NULL) || sigaction(SIGTERM, &action, NULL)) {
        perror("signal handlers"); return 1;
    }
    return receive_original(&ops, argv[1], argv[2], argv[3]);
}
#endif
