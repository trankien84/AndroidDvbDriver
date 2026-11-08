// SPDX-License-Identifier: GPL-2.0-only
#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <linux/dvb/dmx.h>
#include <linux/dvb/frontend.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>
#include <poll.h>
#include <assert.h>
static int scenario, initialized, locks, tuned, started, stopped, closed, writes;
static int64_t ticks;
static void finish(void) {
    assert(closed == (scenario == 1 ? 2 : scenario == 2 ? 2 : 4));
    assert(started == 0 || stopped == 1);
    assert(scenario != 1 || tuned == 0);
    assert(scenario != 2 || (tuned == 1 && writes == 0));
    fprintf(stderr, "FAKE_ONLY_RESULT scenario=%d closed=%d tuned=%d stop=%d writes=%d\n", scenario, closed, tuned, stopped, writes);
}
static void initialize(void) {
    if (initialized) return;
    initialized = 1; const char *value = getenv("TBS_FAKE_CASE");
    assert(value); scenario = atoi(value); assert(scenario >= 0 && scenario <= 4);
    atexit(finish);
}
int __wrap_open(const char *path, int flags, ...) {
    initialize();
    if (strcmp(path, "synthetic.ts") == 0) { assert(flags & O_EXCL); return 100; }
    if (strcmp(path, "/dev/dvb/adapter0/frontend0") == 0) return 101;
    if (strcmp(path, "/dev/dvb/adapter0/demux0") == 0) return 102;
    if (strcmp(path, "/dev/dvb/adapter0/dvr0") == 0) return 103;
    assert(!"Unexpected path"); return -1;
}
int __wrap_close(int fd) { assert(fd >= 100 && fd <= 103); closed++; return 0; }
int __wrap_fsync(int fd) { assert(fd == 100); return 0; }
int __wrap_ioctl(int fd, unsigned long request, ...) {
    va_list args; va_start(args, request);
    if (request == FE_GET_INFO) {
        assert(fd == 101); struct dvb_frontend_info *info = va_arg(args, struct dvb_frontend_info *);
        strcpy(info->name, scenario == 1 ? "Wrong frontend" : "TBS5530 guarded DVB-T2 K36 diagnostic");
    } else if (request == FE_SET_PROPERTY) {
        assert(fd == 101); struct dtv_properties *p = va_arg(args, struct dtv_properties *);
        assert(p->num == 6 && p->props[1].cmd == DTV_DELIVERY_SYSTEM && p->props[1].u.data == SYS_DVBT2);
        assert(p->props[2].cmd == DTV_FREQUENCY && p->props[2].u.data == 594000000);
        assert(p->props[3].cmd == DTV_BANDWIDTH_HZ && p->props[3].u.data == 8000000);
        assert(p->props[4].cmd == DTV_STREAM_ID && p->props[4].u.data == 0);
        tuned++;
    } else if (request == FE_READ_STATUS) {
        assert(fd == 101); fe_status_t *status = va_arg(args, fe_status_t *);
        *status = scenario == 2 ? 0 : FE_HAS_LOCK; locks++;
    } else if (request == DMX_SET_PES_FILTER) {
        assert(fd == 102); struct dmx_pes_filter_params *p = va_arg(args, struct dmx_pes_filter_params *);
        assert(p->pid == 0x2000 && p->input == DMX_IN_FRONTEND && p->output == DMX_OUT_TS_TAP);
        started++;
    } else if (request == DMX_STOP) { assert(fd == 102); stopped++; }
    else { assert(!"Unexpected ioctl, including any voltage/tone/DiSEqC/power operation"); }
    va_end(args); return 0;
}
int __wrap_clock_gettime(clockid_t clock, struct timespec *value) {
    assert(clock == CLOCK_MONOTONIC); ticks += INT64_C(250000000);
    value->tv_sec = ticks / INT64_C(1000000000); value->tv_nsec = ticks % INT64_C(1000000000); return 0;
}
int __wrap_nanosleep(const struct timespec *request, struct timespec *remain) { (void)request; (void)remain; return 0; }
int __wrap_poll(struct pollfd *fds, nfds_t count, int timeout) {
    assert(count == 1 && fds[0].fd == 103 && timeout <= 100);
    fds[0].revents = scenario == 3 ? POLLERR : POLLIN; return 1;
}
ssize_t __wrap_read(int fd, void *buffer, size_t count) {
    assert(fd == 103 && count <= 65536);
    if (scenario == 4) { errno = EOVERFLOW; return -1; }
    memset(buffer, 0x47, count); return (ssize_t)count;
}
ssize_t __wrap_write(int fd, const void *buffer, size_t count) {
    assert(fd == 100 && buffer && count <= 65536); writes++; return (ssize_t)count;
}
