// SPDX-License-Identifier: GPL-2.0-only
#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <linux/dvb/dmx.h>
#include <linux/dvb/frontend.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#define MAX_BYTES (16U * 1024U * 1024U)
#define LOCK_NS INT64_C(10000000000)
#define RECEIVE_NS INT64_C(10000000000)
static volatile sig_atomic_t stopped;
static void on_signal(int value) { (void)value; stopped = 1; }
static int64_t now_ns(void) {
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value)) return -1;
    return (int64_t)value.tv_sec * INT64_C(1000000000) + value.tv_nsec;
}
static int within(int64_t begin, int64_t current, int64_t budget) {
    return begin >= 0 && current >= begin && current - begin < budget;
}
static size_t next_size(size_t total, size_t capacity) {
    return total >= MAX_BYTES ? 0 : (MAX_BYTES - total < capacity ? MAX_BYTES - total : capacity);
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
#ifdef TBS_SELF_TEST
#include <assert.h>
int main(void) {
    struct dtv_property p[6]; make_profile(p);
    assert(p[0].cmd == DTV_CLEAR && p[5].cmd == DTV_TUNE);
    assert(p[1].cmd == DTV_DELIVERY_SYSTEM && p[1].u.data == SYS_DVBT2);
    assert(p[2].cmd == DTV_FREQUENCY && p[2].u.data == 594000000);
    assert(p[3].cmd == DTV_BANDWIDTH_HZ && p[3].u.data == 8000000);
    assert(p[4].cmd == DTV_STREAM_ID && p[4].u.data == 0);
    assert(within(1, 1, LOCK_NS) && !within(1, 0, LOCK_NS));
    assert(!within(-1, 0, LOCK_NS) && !within(0, LOCK_NS, LOCK_NS));
    assert(next_size(0, 65536) == 65536 && next_size(MAX_BYTES - 17, 65536) == 17);
    assert(next_size(MAX_BYTES, 65536) == 0 && next_size(MAX_BYTES + 1, 65536) == 0);
    assert(now_ns() >= 0);
    on_signal(SIGTERM); assert(stopped);
    puts("PASS receive policy: exact K36/PLP0, deadline, byte cap, signal; no device I/O");
    return 0;
}
#else
static int open_node(const char *root, unsigned int adapter, const char *name, int flags) {
    char path[160];
    int count = snprintf(path, sizeof(path), "%s/adapter%u/%s", root, adapter, name);
    if (count < 0 || (size_t)count >= sizeof(path)) { errno = ENAMETOOLONG; return -1; }
    return open(path, flags | O_CLOEXEC);
}
int main(int argc, char **argv) {
    unsigned char bytes[65536];
    struct dtv_property profile[6]; struct dtv_properties properties;
    struct dvb_frontend_info info; struct dmx_pes_filter_params filter;
    struct sigaction action; fe_status_t status = 0;
    int fe = -1, demux = -1, dvr = -1, output = -1, rc = 1, running = 0;
    size_t total = 0; unsigned int adapter; char *end;
    int64_t begin = -1, receive_begin = -1; long parsed;
    if (argc != 3 || argv[1][0] == '\0') {
        fprintf(stderr, "Usage: ReceiveK36 <verified adapter 0..255> <new local TS file>\n"); return 2;
    }
    errno = 0; parsed = strtol(argv[1], &end, 10);
    if (errno || *end || parsed < 0 || parsed > 255) return 2;
    adapter = (unsigned int)parsed;
    memset(&action, 0, sizeof(action)); action.sa_handler = on_signal;
    sigemptyset(&action.sa_mask); sigaction(SIGINT, &action, NULL); sigaction(SIGTERM, &action, NULL);
    output = open(argv[2], O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (output < 0) { perror("new output"); goto cleanup; }
    fe = open_node("/dev/dvb", adapter, "frontend0", O_RDWR | O_NONBLOCK);
    if (fe < 0) { perror("frontend"); goto cleanup; }
    memset(&info, 0, sizeof(info));
    if (ioctl(fe, FE_GET_INFO, &info) || strcmp(info.name, "TBS5530 guarded DVB-T2 K36 diagnostic")) {
        fprintf(stderr, "Frontend identity mismatch or FE_GET_INFO failure\n"); goto cleanup;
    }
    make_profile(profile); properties.num = 6; properties.props = profile;
    begin = now_ns();
    if (begin < 0 || ioctl(fe, FE_SET_PROPERTY, &properties)) { perror("exact profile"); goto cleanup; }
    for (size_t i = 0; i < 6; ++i) {
        if (profile[i].result < 0) { fprintf(stderr, "Property failed: %u\n", profile[i].cmd); goto cleanup; }
    }
    while (!stopped && within(begin, now_ns(), LOCK_NS)) {
        struct timespec pause = {0, 250000000};
        if (ioctl(fe, FE_READ_STATUS, &status)) { perror("frontend status"); goto cleanup; }
        if (status & FE_HAS_LOCK) break;
        nanosleep(&pause, NULL);
    }
    fprintf(stderr, "{\"event\":\"lock\",\"clock\":\"CLOCK_MONOTONIC\",\"ns\":%lld,\"status\":%u}\n", (long long)now_ns(), (unsigned int)status);
    if (stopped || !(status & FE_HAS_LOCK)) { fprintf(stderr, "No lock inside 10s bound\n"); goto cleanup; }
    demux = open_node("/dev/dvb", adapter, "demux0", O_RDWR | O_NONBLOCK);
    dvr = open_node("/dev/dvb", adapter, "dvr0", O_RDONLY | O_NONBLOCK);
    if (demux < 0 || dvr < 0) { perror("demux/dvr"); goto cleanup; }
    memset(&filter, 0, sizeof(filter)); filter.pid = 0x2000;
    filter.input = DMX_IN_FRONTEND; filter.output = DMX_OUT_TS_TAP;
    filter.pes_type = DMX_PES_OTHER; filter.flags = DMX_IMMEDIATE_START;
    if (ioctl(demux, DMX_SET_PES_FILTER, &filter)) { perror("all PID filter"); goto cleanup; }
    running = 1; receive_begin = now_ns();
    while (!stopped && within(receive_begin, now_ns(), RECEIVE_NS) && total < MAX_BYTES) {
        struct pollfd fd = {dvr, POLLIN, 0};
        int ready = poll(&fd, 1, 100);
        if (ready < 0) { if (errno == EINTR) continue; perror("poll"); goto cleanup; }
        if (!ready) continue;
        if (fd.revents & (POLLERR | POLLHUP | POLLNVAL)) { fprintf(stderr, "DVR poll error: %d\n", fd.revents); goto cleanup; }
        ssize_t count = read(dvr, bytes, next_size(total, sizeof(bytes)));
        if (count < 0) { if (errno == EAGAIN || errno == EINTR) continue; perror("read"); goto cleanup; }
        if (!count) { fprintf(stderr, "DVR returned zero; no retry\n"); goto cleanup; }
        size_t offset = 0;
        while (offset < (size_t)count) {
            ssize_t written = write(output, bytes + offset, (size_t)count - offset);
            if (written < 0 && errno == EINTR && !stopped) continue;
            if (written <= 0) { perror("write"); goto cleanup; }
            offset += (size_t)written;
        }
        total += (size_t)count;
    }
    rc = stopped || total == 0 ? 1 : 0;
cleanup:
    if (running && ioctl(demux, DMX_STOP)) { perror("DMX_STOP"); rc = 1; }
    if (dvr >= 0 && close(dvr)) rc = 1;
    if (demux >= 0 && close(demux)) rc = 1;
    if (fe >= 0 && close(fe)) rc = 1;
    if (output >= 0) { if (fsync(output)) rc = 1; if (close(output)) rc = 1; }
    fprintf(stderr, "{\"event\":\"receive_end\",\"clock\":\"CLOCK_MONOTONIC\",\"ns\":%lld,\"bytes\":%zu,\"result\":%d}\n", (long long)now_ns(), total, rc);
    return rc;
}
#endif
