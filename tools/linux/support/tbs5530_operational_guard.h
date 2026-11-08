/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_OPERATIONAL_GUARD_H
#define TBS5530_OPERATIONAL_GUARD_H

#define TBS_OP_SESSION_MS 120000ULL
#define TBS_OP_RECEIVE_MS 10000ULL
#define TBS_OP_HEARTBEAT_MS 3000ULL
#define TBS_OP_BYTE_CAP (32ULL * 1024 * 1024)
#define TBS_OP_URB_SIZE 65536U
#define TBS_OP_URB_COUNT 8U

struct tbs_op_profile {
    unsigned int vid, pid, bus, device, high_speed, configuration;
    unsigned int configurations, interfaces, alternate_settings;
    unsigned int interface_number, alternate, interface_class;
    unsigned int interface_subclass, interface_protocol, ep0_size, endpoints;
    unsigned int ep81_type, ep81_size, ep81_interval;
    unsigned int ep82_type, ep82_size, ep82_interval;
    const unsigned char *digest;
};

static const unsigned char tbs_op_descriptor_hash[32] = {
    0x22,0xe2,0xf1,0xdf,0x9f,0x66,0xc5,0x8d,0xbb,0x6c,0x55,0x4e,0x40,0x0b,0x6b,0x8a,
    0xbc,0xb7,0x8e,0xe4,0xf2,0x77,0xe0,0x36,0x79,0xbc,0x0f,0xac,0x7a,0x25,0xad,0xd5
};

struct tbs_op_state {
    unsigned long long admitted_ms, receive_ms;
    unsigned long long session_limit, receive_limit, byte_limit;
    unsigned long long requested, actual;
    unsigned int admitted, receive_used, receiving;
    unsigned int attempts, submitted, reaped, outstanding, pending_read;
    unsigned int empty_success_credit, empty_credits_used;
    unsigned int empty_successes, empty_resubmits;
    unsigned int first_rx_seen, first_rx_length;
    int first_rx_status;
    int fault;
};

static int tbs_op_profile_check(const struct tbs_op_profile *p,
                                unsigned int expected_bus,
                                unsigned int expected_device)
{
    unsigned int i, diff = 0;
    if (!p || !expected_bus || !expected_device || !p->digest) return -EINVAL;
    if (p->vid != 0x734c || p->pid != 0x5530 ||
        p->bus != expected_bus || p->device != expected_device ||
        !p->high_speed || p->configuration != 1 || p->configurations != 1 ||
        p->interfaces != 1 || p->alternate_settings != 1 ||
        p->interface_number != 0 || p->alternate != 0 ||
        p->interface_class != 0xff || p->interface_subclass != 0 ||
        p->interface_protocol != 0 || p->ep0_size != 64 || p->endpoints != 2 ||
        p->ep81_type != 2 || p->ep81_size != 512 || p->ep81_interval != 0 ||
        p->ep82_type != 2 || p->ep82_size != 512 || p->ep82_interval != 1)
        return -ENODEV;
    for (i = 0; i < 32; i++) diff |= p->digest[i] ^ tbs_op_descriptor_hash[i];
    return diff ? -EKEYREJECTED : 0;
}

static int tbs_op_fail(struct tbs_op_state *s, int error)
{
    if (!s->fault) s->fault = error < 0 ? error : -EIO;
    s->receiving = 0;
    return s->fault;
}

static int tbs_op_gate(struct tbs_op_state *s, unsigned long long now,
                       int consent, int stopped, unsigned long long heartbeat)
{
    if (s->fault) return s->fault;
    if (!s->admitted) return tbs_op_fail(s, -EPERM);
    if (!consent || stopped) return tbs_op_fail(s, -ECANCELED);
    if (!heartbeat || now < heartbeat || now < s->admitted_ms ||
        now - heartbeat >= TBS_OP_HEARTBEAT_MS ||
        now - s->admitted_ms >= s->session_limit ||
        (s->receiving && (now < s->receive_ms ||
                         now - s->receive_ms >= s->receive_limit)))
        return tbs_op_fail(s, -ETIMEDOUT);
    return 0;
}

static int tbs_op_begin(struct tbs_op_state *s, unsigned long long now,
                        int enabled, int operational, int bootstrap,
                        int consent, int stopped, unsigned long long heartbeat,
                        unsigned int *spent)
{
    if (!spent || !enabled || !operational || bootstrap) return -EPERM;
    if (*spent) return -EALREADY;
    if (!s->session_limit) s->session_limit = TBS_OP_SESSION_MS;
    if (!s->receive_limit) s->receive_limit = TBS_OP_RECEIVE_MS;
    if (!s->byte_limit) s->byte_limit = TBS_OP_BYTE_CAP;
    if (s->session_limit > TBS_OP_SESSION_MS ||
        s->receive_limit > TBS_OP_RECEIVE_MS ||
        s->byte_limit > TBS_OP_BYTE_CAP || s->byte_limit < TBS_OP_URB_SIZE ||
        s->empty_success_credit > 1)
        return -EINVAL;
    s->admitted = 1;
    s->admitted_ms = now;
    if (tbs_op_gate(s, now, consent, stopped, heartbeat)) return s->fault;
    *spent = 1;
    return 0;
}

static int tbs_op_control_check(const struct tbs_op_state *s,
                                unsigned int request, unsigned int value,
                                unsigned int index, const unsigned char *data,
                                unsigned int length, int read)
{
    if (value || index || !data || !length || length > 50) return -EINVAL;
    if ((request==0x80 || request==0x90) && length>=2 &&
        data[1]!=0xc8 && data[1]!=0xcc && data[1]!=0x88 &&
        data[1]!=0x20 && data[1]!=0xc0) return -EPERM;
    if (request == 0x91 && read && s->pending_read == length) return 0;
    if (s->pending_read || read) return -EPERM;
    if (request == 0x80 && length >= 3 && data[0] == length - 1 &&
        !(data[1] & 1)) return 0;
    if (request == 0x90 && length == 3 && data[0] && data[0] <= 50 &&
        !(data[1] & 1)) return 0;
    if (request == 0x8a && length == 2 && data[0] == 0x0a && data[1] == 0)
        return 0;
    return -EPERM;
}

static int tbs_op_control_result(struct tbs_op_state *s, unsigned int request,
                                 const unsigned char *data, unsigned int length,
                                 int result)
{
    if (result != (int)length) return tbs_op_fail(s, result < 0 ? result : -EIO);
    if (request == 0x90) s->pending_read = data[0];
    if (request == 0x91) s->pending_read = 0;
    return s->fault;
}

static int tbs_op_receive_begin(struct tbs_op_state *s, unsigned long long now)
{
    if (s->fault) return s->fault;
    if (s->receive_used) return tbs_op_fail(s, -EALREADY);
    s->receive_used = 1;
    s->receiving = 1;
    s->receive_ms = now;
    return 0;
}

static int tbs_op_reserve(struct tbs_op_state *s)
{
    if (s->fault) return s->fault;
    if (!s->receiving) return tbs_op_fail(s, -EPIPE);
    if (s->outstanding >= TBS_OP_URB_COUNT ||
        s->requested + TBS_OP_URB_SIZE > s->byte_limit)
        return tbs_op_fail(s, -EFBIG);
    s->attempts++;
    s->outstanding++;
    s->requested += TBS_OP_URB_SIZE;
    return 0;
}

static int tbs_op_complete(struct tbs_op_state *s, int status,
                           unsigned int actual)
{
    if (!s->outstanding) return tbs_op_fail(s, -EPROTO);
    s->outstanding--;
    s->reaped++;
    if (!s->first_rx_seen) {
        s->first_rx_seen = 1;
        s->first_rx_status = status;
        s->first_rx_length = actual;
    }
    if (!status && !actual) s->empty_successes++;
    if (s->fault) return s->fault;
    if (status) return tbs_op_fail(s, status < 0 ? status : -EIO);
    if (!actual) {
        if (s->empty_credits_used >= s->empty_success_credit)
            return tbs_op_fail(s, -ENODATA);
        s->empty_credits_used++;
        return 1; /* Empty accepted for resubmit only, never demux delivery. */
    }
    if (actual > TBS_OP_URB_SIZE || s->actual + actual > s->byte_limit)
        return tbs_op_fail(s, -EOVERFLOW);
    s->actual += actual;
    return 0;
}
#endif
