// SPDX-License-Identifier: GPL-2.0-only
#define TBS_ORIGINAL_FAKE_ONLY 1
#include "../ReceiveOriginalK36.c"
#include <assert.h>
#ifdef NDEBUG
#error Fake tests require assertions
#endif
_Static_assert(sizeof(((struct dvb_frontend_info *)0)->name) == 128, "FE_GET_INFO name must have 128 bytes");
_Static_assert(sizeof(ORIGINAL_NAME) <= sizeof(((struct dvb_frontend_info *)0)->name), "Original name must fit including NUL");

#define USB "/sys/devices/pci0000:00/usb1/1-2"
#define INTERFACE USB "/1-2:1.0"
#define OTHER_USB "/sys/devices/pci0000:00/usb1/1-3"
#define DEV_ROOT "/dev/dvb/adapter"

enum scenario {
    GOOD_BYTES, BYTE_CAP, ADAPTER_255, CANONICAL_ALIAS,
    WRONG_VID, WRONG_PID, MALFORMED_VID, PARTIAL_IDS, DENIED_IDS,
    WRONG_PARENT, PARENT_PREFIX, MISSING_SYSFS, OUTSIDE_SYSFS,
    MALFORMED_DEV, WRONG_NODE, SYMLINK_NODE, CHANGED_FD,
    WRONG_NAME, NONTERMINATED_NAME, PROBE_INFO_ERROR, RW_NAME_CHANGED,
    OUTPUT_EXISTS, OUTPUT_SYMLINK, PROBE_OPEN_ERROR, RW_OPEN_ERROR,
    LOCK_FAIL, LATE_LOCK, STATUS_ERROR, TUNE_ERROR, PROPERTY_ERROR,
    DEMUX_PARENT, DVR_PARENT, DEMUX_OPEN_ERROR, DVR_OPEN_ERROR, FILTER_ERROR,
    POLL_ERROR, POLL_ERR_EVENT, POLL_HUP_EVENT, POLL_NVAL_EVENT, POLL_IDLE,
    POLL_OTHER, POLL_EINTR, LATE_POLL, READ_ZERO, READ_ERROR, READ_OVERFLOW,
    READ_EAGAIN, READ_EINTR, WRITE_ERROR, WRITE_ZERO, WRITE_SHORT, WRITE_EINTR,
    PARTIAL_WRITE_ERROR, STOP_PROBE, STOP_LOCK, STOP_POLL, STOP_READ, STOP_WRITE,
    STOP_INT, STOP_CLEANUP, DMX_STOP_ERROR, CLOSE_PROBE_ERROR, CLOSE_FE_ERROR,
    CLOSE_DEMUX_ERROR, CLOSE_DVR_ERROR, CLOSE_OUTPUT_ERROR, SYNC_ERROR,
    CLOCK_TUNE_ERROR, CLOCK_LOCK_ERROR, CLOCK_RECEIVE_ERROR, CLOCK_BACKWARD,
    SLEEP_ERROR, DEVICE_CHANGED, NO_PARENT, MALFORMED_PID, INVALID_ADAPTER,
    SYSFS_DVB_CHILDREN, SYSFS_USB_DEVICE, NAME_SUFFIX, NAME_TRUNCATED,
    SCENARIOS
};
static const char *const names[] = {
    "bytes", "byte_cap", "adapter_255", "canonical_alias",
    "wrong_vid", "wrong_pid", "malformed_vid", "partial_ids", "denied_ids",
    "wrong_parent", "parent_prefix", "missing_sysfs", "outside_sysfs",
    "malformed_dev", "wrong_node", "symlink_node", "changed_fd",
    "wrong_name", "unterminated_name", "probe_info_error", "rw_name_changed",
    "output_exists", "output_symlink", "probe_open_error", "rw_open_error",
    "lock_fail", "late_lock", "status_error", "tune_error", "property_error",
    "demux_parent", "dvr_parent", "demux_open_error", "dvr_open_error", "filter_error",
    "poll_error", "pollerr", "pollhup", "pollnval", "poll_idle",
    "poll_other", "poll_eintr", "late_poll", "read_zero", "read_error", "read_overflow",
    "read_eagain", "read_eintr", "write_error", "write_zero", "write_short", "write_eintr",
    "partial_write_error", "stop_probe", "stop_lock", "stop_poll", "stop_read", "stop_write",
    "stop_int", "stop_cleanup", "dmx_stop_error", "close_probe_error", "close_fe_error",
    "close_demux_error", "close_dvr_error", "close_output_error", "fsync_error",
    "clock_tune_error", "clock_lock_error", "clock_receive_error", "clock_backward",
    "sleep_error", "device_changed", "no_parent", "malformed_pid", "invalid_adapter",
    "sysfs_dvb_children", "sysfs_usb_device", "name_suffix", "name_truncated"
};
static struct {
    enum scenario scenario;
    int opened[5], closed[5], infos, tuned, statuses, filters, stops, syncs;
    int reads, polls, writes, device_checks, clocks;
    int64_t ticks, lock_start, receive_start;
    size_t saved;
    unsigned int adapter, parent_preopens, parent_postopens;
} fake;

static int fail(int error) { errno = error; return -1; }
static void copy_text(char *out, size_t capacity, const char *text) {
    assert(strlen(text) < capacity);
    strcpy(out, text);
}
static int node_id(const char *path) {
    if (strstr(path, "frontend0")) return 0;
    if (strstr(path, "demux0")) return 3;
    if (strstr(path, "dvr0")) return 4;
    assert(!"Unexpected node"); return -1;
}
static int fake_resolve(const char *path, char *out, size_t capacity) {
    if (!strcmp(path, USB) || !strcmp(path, OTHER_USB) || !strcmp(path, USB "0")) {
        copy_text(out, capacity, path); return 0;
    }
    if (!strcmp(path, "/sys/bus/usb/devices/1-2")) { copy_text(out, capacity, USB); return 0; }
    char prefix[80];
    snprintf(prefix, sizeof(prefix), "/sys/class/dvb/dvb%u.", fake.adapter);
    assert(!strncmp(path, prefix, strlen(prefix)) && strstr(path, "/device"));
    fake.device_checks++;
    if (fake.scenario == MISSING_SYSFS) return fail(ENOENT);
    if (fake.scenario == DEVICE_CHANGED && fake.infos) return fail(ENODEV);
    if (fake.scenario == SYSFS_DVB_CHILDREN) {
        char resolved[256];
        int id = node_id(path);
        int n = snprintf(resolved, sizeof(resolved), INTERFACE "/dvb/dvb%u.%s", fake.adapter,
                         id == 0 ? "frontend0" : id == 3 ? "demux0" : "dvr0");
        assert(n > 0 && (size_t)n < sizeof(resolved));
        copy_text(out, capacity, resolved);
        return 0;
    }
    const char *resolved = fake.scenario == SYSFS_USB_DEVICE ? USB : INTERFACE;
    if (fake.scenario == OUTSIDE_SYSFS) resolved = "/somewhere/1-2";
    if (fake.scenario == DEMUX_PARENT && node_id(path) == 3) resolved = OTHER_USB;
    if (fake.scenario == DVR_PARENT && node_id(path) == 4) resolved = OTHER_USB;
    copy_text(out, capacity, resolved);
    return 0;
}
static int fake_text(const char *path, char *out, size_t capacity) {
    if (strstr(path, "/sys/class/dvb/")) {
        assert(strstr(path, "/dev"));
        char value[64];
        snprintf(value, sizeof(value), "212:%u\n", fake.adapter * 4 + (unsigned int)node_id(path));
        if (fake.scenario == MALFORMED_DEV) strcpy(value, "212:0junk\n");
        copy_text(out, capacity, value); return 0;
    }
    int vendor = strstr(path, "/idVendor") != NULL;
    assert(vendor || strstr(path, "/idProduct"));
    if (!strncmp(path, INTERFACE "/", strlen(INTERFACE) + 1)) return fail(ENOENT);
    if (fake.scenario == NO_PARENT) return fail(ENOENT);
    assert(!strncmp(path, USB "/", strlen(USB) + 1) ||
           !strncmp(path, OTHER_USB "/", strlen(OTHER_USB) + 1));
    if (fake.scenario == PARTIAL_IDS && !vendor) return fail(ENOENT);
    if (fake.scenario == DENIED_IDS) return fail(EACCES);
    const char *value = vendor ? "734c\n" : "5530\n";
    if (fake.scenario == WRONG_VID && vendor) value = "1234\n";
    if (fake.scenario == WRONG_PID && !vendor) value = "5531\n";
    if (fake.scenario == MALFORMED_VID && vendor) value = "734c garbage";
    if (fake.scenario == MALFORMED_PID && !vendor) value = "5530 garbage";
    copy_text(out, capacity, value); return 0;
}
static int fake_node(const char *path, int fd, unsigned int major_id, unsigned int minor_id) {
    int id = node_id(path);
    char expected[100];
    snprintf(expected, sizeof(expected), DEV_ROOT "%u/%s", fake.adapter,
             id == 0 ? "frontend0" : id == 3 ? "demux0" : "dvr0");
    assert(!strcmp(path, expected) && major_id == 212 && minor_id == fake.adapter * 4 + (unsigned int)id);
    if (fake.scenario == WRONG_NODE || fake.scenario == SYMLINK_NODE) return fail(ENODEV);
    if (fd >= 0) {
        assert(fd >= 100 && fd <= 104 && fake.opened[fd - 100]);
        if (fake.scenario == CHANGED_FD) return fail(ENODEV);
        fake.parent_postopens |= 1U << id;
    } else fake.parent_preopens |= 1U << id;
    return 0;
}
static int fake_open(const char *path, int flags, unsigned int mode) {
    int id;
    assert((flags & (O_CLOEXEC | O_NOFOLLOW)) == (O_CLOEXEC | O_NOFOLLOW));
    if (!strcmp(path, "synthetic.ts")) {
        assert(fake.infos == 1 && fake.closed[0] && !fake.tuned);
        assert((flags & (O_WRONLY | O_CREAT | O_EXCL)) == (O_WRONLY | O_CREAT | O_EXCL) && mode == 0600);
        if (fake.scenario == OUTPUT_EXISTS || fake.scenario == OUTPUT_SYMLINK) return fail(EEXIST);
        id = 1;
    } else {
        assert(flags & O_NONBLOCK);
        id = node_id(path);
        if (id == 0) {
            if ((flags & O_ACCMODE) == O_RDONLY) {
                assert(!fake.infos && !fake.tuned);
                if (fake.scenario == PROBE_OPEN_ERROR) return fail(EACCES);
            } else {
                assert((flags & O_ACCMODE) == O_RDWR && fake.infos == 1 && fake.closed[0]);
                if (fake.scenario == RW_OPEN_ERROR) return fail(EACCES);
                id = 2;
            }
        } else {
            assert(fake.tuned && fake.statuses && fake.infos == 2);
            if (id == 3 && fake.scenario == DEMUX_OPEN_ERROR) return fail(EACCES);
            if (id == 4 && fake.scenario == DVR_OPEN_ERROR) return fail(EACCES);
            assert((flags & O_ACCMODE) == (id == 3 ? O_RDWR : O_RDONLY));
        }
    }
    assert(!stopped && !fake.opened[id]);
    fake.opened[id] = 1;
    return 100 + id;
}
static int fake_close(int fd) {
    int id = fd - 100;
    assert(id >= 0 && id < 5 && fake.opened[id] && !fake.closed[id]);
    if (id >= 2 && fake.filters) assert(fake.stops == 1);
    fake.closed[id]++;
    if ((id == 0 && fake.scenario == CLOSE_PROBE_ERROR) ||
        (id == 1 && fake.scenario == CLOSE_OUTPUT_ERROR) ||
        (id == 2 && fake.scenario == CLOSE_FE_ERROR) ||
        (id == 3 && fake.scenario == CLOSE_DEMUX_ERROR) ||
        (id == 4 && fake.scenario == CLOSE_DVR_ERROR)) return fail(EIO);
    return 0;
}
static int fake_sync(int fd) {
    assert(fd == 101 && fake.opened[1] && !fake.closed[1]);
    for (int i = 2; i < 5; ++i) assert(fake.opened[i] == fake.closed[i]);
    fake.syncs++;
    if (fake.scenario == SYNC_ERROR) return fail(EIO);
    return 0;
}
static int fake_control(int fd, unsigned long request, void *arg) {
    assert(fd >= 100 && fd <= 104 && fake.opened[fd - 100] && !fake.closed[fd - 100]);
    switch (request) {
        case FE_GET_INFO: {
            assert(fd == 100 || fd == 102);
            struct dvb_frontend_info *info = arg;
            fake.infos++;
            if (fake.scenario == PROBE_INFO_ERROR) return fail(EIO);
            if (fake.scenario == NONTERMINATED_NAME) memset(info->name, 'X', sizeof(info->name));
            else strcpy(info->name, fake.scenario == WRONG_NAME ||
                        (fake.scenario == RW_NAME_CHANGED && fd == 102) ? "Wrong frontend" : ORIGINAL_NAME);
            if (fake.scenario == NAME_SUFFIX) strcat(info->name, "X");
            if (fake.scenario == NAME_TRUNCATED) info->name[sizeof(ORIGINAL_NAME) - 2] = '\0';
            if (fake.scenario == STOP_PROBE) on_signal(SIGTERM);
            return 0;
        }
        case FE_SET_PROPERTY: {
            assert(fd == 102 && fake.infos == 2 && !fake.tuned && !stopped);
            const struct dtv_properties *p = arg;
            assert(p->num == 6 && p->props[0].cmd == DTV_CLEAR && p->props[5].cmd == DTV_TUNE);
            assert(p->props[1].cmd == DTV_DELIVERY_SYSTEM && p->props[1].u.data == SYS_DVBT2);
            assert(p->props[2].cmd == DTV_FREQUENCY && p->props[2].u.data == 594000000);
            assert(p->props[3].cmd == DTV_BANDWIDTH_HZ && p->props[3].u.data == 8000000);
            assert(p->props[4].cmd == DTV_STREAM_ID && p->props[4].u.data == 0);
            fake.tuned++; fake.lock_start = fake.ticks;
            if (fake.scenario == TUNE_ERROR) return fail(EIO);
            if (fake.scenario == PROPERTY_ERROR) p->props[4].result = -EINVAL;
            return 0;
        }
        case FE_READ_STATUS:
            assert(fd == 102 && fake.tuned);
            assert(fake.ticks - fake.lock_start < LOCK_NS);
            fake.statuses++;
            if (fake.scenario == STATUS_ERROR) return fail(EIO);
            *(fe_status_t *)arg = fake.scenario == LOCK_FAIL || fake.scenario == SLEEP_ERROR ? 0 : FE_HAS_LOCK;
            if (fake.scenario == LATE_LOCK) fake.ticks += LOCK_NS;
            if (fake.scenario == STOP_LOCK) on_signal(SIGTERM);
            return 0;
        case DMX_SET_PES_FILTER: {
            const struct dmx_pes_filter_params *p = arg;
            assert(fd == 103 && fake.opened[4] && !stopped);
            assert(p->pid == 0x2000 && p->input == DMX_IN_FRONTEND && p->output == DMX_OUT_TS_TAP);
            assert(p->pes_type == DMX_PES_OTHER && p->flags == DMX_IMMEDIATE_START);
            fake.filters++; fake.receive_start = fake.ticks;
            if (fake.scenario == FILTER_ERROR) return fail(EIO);
            return 0;
        }
        case DMX_STOP:
            assert(fd == 103 && fake.filters == 1 && !fake.stops);
            fake.stops++;
            if (fake.scenario == STOP_CLEANUP) on_signal(SIGTERM);
            if (fake.scenario == DMX_STOP_ERROR) return fail(EIO);
            return 0;
        default: assert(!"Forbidden ioctl: voltage/tone/DiSEqC or unknown operation"); return -1;
    }
}
static int64_t fake_clock(void) {
    fake.clocks++;
    if (fake.scenario == CLOCK_TUNE_ERROR && !fake.tuned) return -1;
    if (fake.scenario == CLOCK_LOCK_ERROR && fake.tuned && !fake.statuses) return -1;
    if (fake.scenario == CLOCK_RECEIVE_ERROR && fake.filters) return -1;
    if (fake.scenario == CLOCK_BACKWARD && fake.polls) return fake.receive_start - 1;
    fake.ticks++;
    return fake.ticks;
}
static int fake_pause(void) {
    if (fake.scenario == SLEEP_ERROR) return fail(EIO);
    fake.ticks += INT64_C(100000000);
    return 0;
}
static int fake_wait(int fd, int timeout, short *events) {
    assert(fd == 104 && fake.filters == 1 && timeout > 0 && timeout <= 100);
    assert(fake.ticks - fake.receive_start < RECEIVE_NS + INT64_C(250000000));
    fake.polls++; *events = POLLIN;
    fake.ticks += fake.scenario == BYTE_CAP ? INT64_C(1000) : (int64_t)timeout * INT64_C(1000000);
    switch (fake.scenario) {
        case POLL_ERROR: return fail(EIO);
        case POLL_ERR_EVENT: *events = POLLERR; break;
        case POLL_HUP_EVENT: *events = POLLHUP; break;
        case POLL_NVAL_EVENT: *events = POLLNVAL; break;
        case POLL_IDLE: return 0;
        case POLL_OTHER: *events = 0; break;
        case POLL_EINTR: if (fake.polls == 1) return fail(EINTR); break;
        case LATE_POLL: fake.ticks += RECEIVE_NS; break;
        case STOP_POLL: on_signal(SIGTERM); break;
        case STOP_INT: on_signal(SIGINT); break;
        default: break;
    }
    return 1;
}
static ssize_t fake_read(int fd, void *buffer, size_t count) {
    assert(fd == 104 && count && count <= 65536 && count <= MAX_BYTES - fake.saved);
    assert(!stopped && fake.ticks - fake.receive_start < RECEIVE_NS + INT64_C(250000000));
    fake.reads++;
    if (fake.scenario == READ_ZERO) return 0;
    if (fake.scenario == READ_ERROR) return fail(EIO);
    if (fake.scenario == READ_OVERFLOW) return fail(EOVERFLOW);
    if (fake.scenario == READ_EAGAIN && fake.reads == 1) return fail(EAGAIN);
    if (fake.scenario == READ_EINTR && fake.reads == 1) return fail(EINTR);
    if (fake.scenario == STOP_READ) on_signal(SIGTERM);
    memset(buffer, 0x47, count);
    return (ssize_t)count;
}
static ssize_t fake_write(int fd, const void *buffer, size_t count) {
    assert(fd == 101 && buffer && count && !stopped && fake.filters);
    fake.writes++;
    if (fake.scenario == WRITE_ERROR || (fake.scenario == PARTIAL_WRITE_ERROR && fake.writes > 1)) return fail(EIO);
    if (fake.scenario == WRITE_ZERO) return 0;
    if (fake.scenario == WRITE_EINTR && fake.writes == 1) return fail(EINTR);
    if (fake.scenario == WRITE_SHORT || fake.scenario == PARTIAL_WRITE_ERROR) count = count > 17 ? 17 : count;
    if (fake.scenario == STOP_WRITE) on_signal(SIGTERM);
    fake.saved += count;
    return (ssize_t)count;
}
static const struct receiver_ops ops = {
    fake_resolve, fake_text, fake_node, fake_open, fake_close, fake_sync, fake_control,
    fake_clock, fake_pause, fake_wait, fake_read, fake_write
};

static void selftests(void) {
    unsigned int adapter, major_id, minor_id;
    struct dtv_property profile[6];
    const char *invalid[] = {"", "-1", "+1", "256", "99999999999999999999", " 0", "0 ", "1x", "1/../2"};
    assert(parse_adapter("0", &adapter) == 0 && adapter == 0);
    assert(parse_adapter("255", &adapter) == 0 && adapter == 255);
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i) assert(parse_adapter(invalid[i], &adapter));
    assert(parse_adapter(NULL, &adapter));
    assert(within(1, 1, LOCK_NS) && !within(-1, 0, LOCK_NS));
    assert(!within(1, 0, LOCK_NS) && !within(0, LOCK_NS, LOCK_NS));
    assert(!within(0, RECEIVE_NS, RECEIVE_NS));
    assert(next_size(0, 65536) == 65536 && next_size(MAX_BYTES - 17, 65536) == 17);
    assert(!next_size(MAX_BYTES, 65536) && !next_size(MAX_BYTES + 1, 65536));
    assert(hex_id("734C\n", 0x734c) && !hex_id("734cX", 0x734c));
    assert(!hex_id("734", 0x734c) && !hex_id("", 0x734c) && !hex_id("734c\nX", 0x734c));
    assert(parse_dev("212:255\n", &major_id, &minor_id) == 0 && major_id == 212 && minor_id == 255);
    const char *bad_dev[] = {"", "-1:2", "1:", "1:2x", "1:2\nX", "999999999999:1", "1:999999999999"};
    for (size_t i = 0; i < sizeof(bad_dev) / sizeof(bad_dev[0]); ++i) assert(parse_dev(bad_dev[i], &major_id, &minor_id));
    make_profile(profile);
    assert(profile[0].cmd == DTV_CLEAR && profile[1].u.data == SYS_DVBT2);
    assert(profile[2].u.data == 594000000 && profile[3].u.data == 8000000);
    assert(profile[4].cmd == DTV_STREAM_ID && profile[4].u.data == 0 && profile[5].cmd == DTV_TUNE);
    on_signal(SIGTERM); assert(stopped); stopped = 0;
    on_signal(SIGINT); assert(stopped); stopped = 0;
    puts("PASS selftests: adapter, identity parsing, K36/8MHz/DVB-T2/manual PLP0, deadlines, byte cap, signals");
    printf("PASS FE_GET_INFO name: %zu-byte field, %zu-byte original including NUL\n",
           sizeof(((struct dvb_frontend_info *)0)->name), sizeof(ORIGINAL_NAME));
}
static int success(enum scenario scenario) {
    return scenario == GOOD_BYTES || scenario == BYTE_CAP || scenario == ADAPTER_255 ||
        scenario == CANONICAL_ALIAS || scenario == POLL_EINTR || scenario == READ_EAGAIN ||
        scenario == READ_EINTR || scenario == WRITE_SHORT || scenario == WRITE_EINTR ||
        scenario == SYSFS_DVB_CHILDREN || scenario == SYSFS_USB_DEVICE;
}
static void flow(enum scenario scenario) {
    const char *expected = scenario == WRONG_PARENT ? OTHER_USB : scenario == PARENT_PREFIX ? USB "0" :
                           scenario == CANONICAL_ALIAS ? "/sys/bus/usb/devices/1-2" : USB;
    memset(&fake, 0, sizeof(fake)); stopped = 0; fake.scenario = scenario;
    fake.adapter = scenario == ADAPTER_255 ? 255 : 0;
    assert(sizeof(names) / sizeof(names[0]) == SCENARIOS);
    fprintf(stderr, "{\"event\":\"fake_case\",\"case\":\"%s\"}\n", names[scenario]);
    int result = receive_original(&ops, scenario == INVALID_ADAPTER ? "256" : fake.adapter ? "255" : "0", expected, "synthetic.ts");
    assert(result == (scenario == INVALID_ADAPTER ? 2 : success(scenario) ? 0 : 1));
    for (int i = 0; i < 5; ++i) assert(fake.opened[i] == fake.closed[i]);
    assert(fake.filters == fake.stops && fake.syncs == fake.opened[1]);
    assert(fake.saved <= MAX_BYTES);
    if (success(scenario)) {
        unsigned int all_nodes = (1U << 0) | (1U << 3) | (1U << 4);
        assert(fake.saved > 0 && fake.tuned == 1 && fake.filters == 1);
        assert((fake.parent_preopens & all_nodes) == all_nodes && (fake.parent_postopens & all_nodes) == all_nodes);
    }
    if (scenario == BYTE_CAP) assert(fake.saved == MAX_BYTES);
    if (scenario <= CHANGED_FD && scenario >= WRONG_VID) assert(!fake.tuned && !fake.opened[1] && !fake.opened[2]);
    if (scenario == WRONG_NAME || scenario == NONTERMINATED_NAME || scenario == PROBE_INFO_ERROR ||
        scenario == STOP_PROBE || scenario == DEVICE_CHANGED || scenario == NAME_SUFFIX ||
        scenario == NAME_TRUNCATED) assert(!fake.tuned && !fake.opened[1] && !fake.opened[2]);
    if (scenario == DEMUX_PARENT) assert(!fake.opened[3] && !fake.filters);
    if (scenario == DVR_PARENT) assert(!fake.opened[4] && !fake.filters);
    if (scenario == LOCK_FAIL || scenario == LATE_LOCK || scenario == STATUS_ERROR || scenario == STOP_LOCK) assert(!fake.opened[3]);
    if (scenario == POLL_IDLE || scenario == POLL_OTHER || scenario == LATE_POLL || scenario == STOP_POLL || scenario == STOP_INT) assert(!fake.reads);
    if (scenario == READ_ZERO || scenario == READ_ERROR || scenario == READ_OVERFLOW) assert(fake.reads == 1 && !fake.saved);
    if (scenario == PARTIAL_WRITE_ERROR) assert(fake.saved == 17);
    fprintf(stderr, "{\"event\":\"fake_expect\",\"case\":\"%s\",\"bytes\":%zu,\"result\":%d,\"stops\":%d}\n",
            names[scenario], fake.saved, result, fake.stops);
}
int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "--selftest")) { selftests(); return 0; }
    assert(argc == 1);
    selftests();
    for (int i = 0; i < SCENARIOS; ++i) flow((enum scenario)i);
    printf("PASS %d mocked flows; no receiver device, USB, SSH or VM calls\n", SCENARIOS);
    return 0;
}
