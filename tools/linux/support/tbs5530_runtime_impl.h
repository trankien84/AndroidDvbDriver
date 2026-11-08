/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_RUNTIME_IMPL_H
#define TBS5530_RUNTIME_IMPL_H
#include <linux/ktime.h>
#include <linux/moduleparam.h>
#include <linux/wait.h>

static bool operational_only, consent, stop;
static unsigned int expected_busnum, expected_devnum;
static unsigned int session_ms = 120000, receive_ms = 10000;
static unsigned int empty_success_credit;
static unsigned long max_bytes = 33554432;
static char *expected_profile_sha256;
static unsigned long heartbeat;
static atomic64_t heartbeat_at;
static atomic_t probe_spent = ATOMIC_INIT(0);
static DEFINE_MUTEX(live_lock);
static struct tbs5530_dev *live_device;
module_param(operational_only, bool, 0400);
module_param(expected_busnum, uint, 0400);
module_param(expected_devnum, uint, 0400);
module_param(session_ms, uint, 0400);
module_param(receive_ms, uint, 0400);
module_param(empty_success_credit, uint, 0400);
module_param(max_bytes, ulong, 0400);
module_param(expected_profile_sha256, charp, 0400);

static unsigned long long tbs_now_ms(void)
{
    return ktime_to_ms(ktime_get_boottime());
}
static void tbs5530_drain_worker(struct work_struct *work);
static void tbs5530_monitor_worker(struct work_struct *work);

static int tbs_param_consent(const char *value, const struct kernel_param *kp)
{
    bool next;
    if (kstrtobool(value, &next)) return -EINVAL;
    if (atomic_read(&probe_spent) && next) return -EPERM;
    WRITE_ONCE(consent, next);
    if (!next && atomic_read(&probe_spent)) WRITE_ONCE(stop, true);
    return 0;
}
static int tbs_param_stop(const char *value, const struct kernel_param *kp)
{
    bool next;
    if (kstrtobool(value, &next)) return -EINVAL;
    if (!next) return -EPERM;
    WRITE_ONCE(stop, true);
    return 0;
}
static int tbs_param_heartbeat(const char *value, const struct kernel_param *kp)
{
    unsigned long next;
    if (kstrtoul(value, 0, &next) || !next) return -EINVAL;
    if (next <= READ_ONCE(heartbeat)) return -EINVAL;
    if (READ_ONCE(stop)) return -ECANCELED;
    WRITE_ONCE(heartbeat, next);
    atomic64_set(&heartbeat_at, tbs_now_ms());
    return 0;
}
static const struct kernel_param_ops consent_ops = { .set=tbs_param_consent, .get=param_get_bool };
static const struct kernel_param_ops stop_ops = { .set=tbs_param_stop, .get=param_get_bool };
static const struct kernel_param_ops heartbeat_ops = { .set=tbs_param_heartbeat, .get=param_get_ulong };
module_param_cb(consent, &consent_ops, &consent, 0600);
module_param_cb(stop, &stop_ops, &stop, 0600);
module_param_cb(heartbeat, &heartbeat_ops, &heartbeat, 0600);

/* Caller holds runtime.lock; all terminal reasons disable both control and receive. */
static int tbs5530_gate_locked(struct tbs5530_dev *dev)
{
    struct tbs5530_runtime *r = &dev->runtime;
    unsigned long long heartbeat_time=atomic64_read(&heartbeat_at);
    int ret;
    if (r->shutdown) return tbs_op_fail(&r->policy, -ENODEV);
    ret = tbs_op_gate(&r->policy, tbs_now_ms(), READ_ONCE(consent),
                      READ_ONCE(stop), heartbeat_time);
    if (ret) schedule_work(&r->drain_work);
    return ret;
}
static int tbs5530_fault(struct tbs5530_dev *dev, int error)
{
    struct tbs5530_runtime *r = &dev->runtime;
    unsigned long flags;
    int ret;
    spin_lock_irqsave(&r->lock, flags);
    ret = tbs_op_fail(&r->policy, error);
    if (!r->shutdown) schedule_work(&r->drain_work);
    spin_unlock_irqrestore(&r->lock, flags);
    return ret;
}
static int tbs5530_gate(struct tbs5530_dev *dev)
{
    unsigned long flags;
    int ret;
    spin_lock_irqsave(&dev->runtime.lock, flags);
    ret = tbs5530_gate_locked(dev);
    spin_unlock_irqrestore(&dev->runtime.lock, flags);
    return ret;
}
static int tbs5530_finish(struct tbs5530_dev *dev, int ret)
{
    if (ret) return tbs5530_fault(dev, ret);
    return tbs5530_gate(dev);
}

static char terminal_telemetry[1024];
static int tbs5530_format_telemetry(char *buffer, struct tbs5530_dev *dev)
{
    struct tbs5530_runtime *r=&dev->runtime;
    return scnprintf(buffer, 1024,
        "mode=%s consumed=%d admitted=%u bound=%u fault=%d controls=%u control_requested=%u control_actual=%u last_control=%d first_failed_control=%u first_failed_result=%d suppressed_controls=%u submit_attempts=%u submitted=%u reaped=%u outstanding=%u requested_bytes=%llu actual_bytes=%llu last_status=%d last_requested=%u last_actual=%u first_rx_seen=%u first_rx_status=%d first_rx_length=%u empty_success_credit=%u empty_successes=%u empty_credits_used=%u empty_resubmits=%u receiving=%u stopped=%u drained=%u quarantined=%u\n",
        operational_only ? "operational" : "bootstrap", atomic_read(&probe_spent),
        r->policy.admitted, !r->shutdown, r->policy.fault, r->controls, r->control_requested,
        r->control_actual, r->last_control_result, r->first_failed_control,
        r->first_failed_result, r->suppressed_controls, r->policy.attempts,
        r->policy.submitted, r->policy.reaped, r->policy.outstanding,
        r->policy.requested, r->policy.actual, r->last_status,
        r->last_requested, r->last_actual,
        r->policy.first_rx_seen, r->policy.first_rx_status, r->policy.first_rx_length,
        r->policy.empty_success_credit, r->policy.empty_successes,
        r->policy.empty_credits_used, r->policy.empty_resubmits, r->policy.receiving,
        READ_ONCE(stop), r->drained, r->quarantined);
}
static int tbs5530_telemetry(char *buffer, const struct kernel_param *kp)
{
    struct tbs5530_dev *dev;
    struct tbs5530_runtime *r;
    unsigned long flags;
    int ret;
    mutex_lock(&live_lock);
    dev = live_device;
    if (!dev && terminal_telemetry[0]) {
        ret=scnprintf(buffer,PAGE_SIZE,"%s",terminal_telemetry);
        goto out;
    }
    if (!dev) {
        ret = scnprintf(buffer, PAGE_SIZE, "mode=%s consumed=%d admitted=0 bound=0\n",
                         operational_only ? "operational" : "bootstrap",
                         atomic_read(&probe_spent));
        goto out;
    }
    r = &dev->runtime;
    spin_lock_irqsave(&r->lock, flags);
    ret=tbs5530_format_telemetry(buffer,dev);
    spin_unlock_irqrestore(&r->lock, flags);
out:
    mutex_unlock(&live_lock);
    return ret;
}
static const struct kernel_param_ops telemetry_ops = { .get=tbs5530_telemetry };
module_param_cb(telemetry, &telemetry_ops, NULL, 0400);

static int tbs5530_operational_admission(struct usb_interface *intf,
                                        struct tbs5530_dev *dev)
{
    struct usb_device *u = dev->udev;
    struct usb_host_interface *a = intf->cur_altsetting;
    struct tbs_op_profile p = {0};
    unsigned char raw[50], digest[32], expected_digest[32];
    unsigned int i, spent = 0;
    unsigned long long heartbeat_time;
    int ret;
    if (!u->actconfig || !u->rawdescriptors || !u->rawdescriptors[0] || !a)
        return -ENODEV;
    if (!expected_profile_sha256 || strlen(expected_profile_sha256) != 64 ||
        hex2bin(expected_digest, expected_profile_sha256, 32)) return -EINVAL;
    if (memcmp(expected_digest, tbs_op_descriptor_hash, 32)) return -EKEYREJECTED;
    if (le16_to_cpu(u->actconfig->desc.wTotalLength) != 32 ||
        u->actconfig != &u->config[0]) return -ENODEV;
    memcpy(raw, &u->descriptor, 18);
    memcpy(raw + 18, u->rawdescriptors[0], 32);
    sha256(raw, sizeof(raw), digest);
    p.vid=le16_to_cpu(u->descriptor.idVendor); p.pid=le16_to_cpu(u->descriptor.idProduct);
    p.bus=u->bus->busnum; p.device=u->devnum; p.high_speed=u->speed==USB_SPEED_HIGH;
    p.configuration=u->actconfig->desc.bConfigurationValue;
    p.configurations=u->descriptor.bNumConfigurations;
    p.interfaces=u->actconfig->desc.bNumInterfaces;
    p.alternate_settings=intf->num_altsetting;
    p.interface_number=a->desc.bInterfaceNumber; p.alternate=a->desc.bAlternateSetting;
    p.interface_class=a->desc.bInterfaceClass; p.interface_subclass=a->desc.bInterfaceSubClass;
    p.interface_protocol=a->desc.bInterfaceProtocol; p.ep0_size=u->descriptor.bMaxPacketSize0;
    p.endpoints=a->desc.bNumEndpoints; p.digest=digest;
    if (p.endpoints != 2) return -ENODEV;
    for (i=0; i<p.endpoints; i++) {
        struct usb_endpoint_descriptor *e=&a->endpoint[i].desc;
        if (e->bEndpointAddress==0x81) {
            p.ep81_type=usb_endpoint_type(e); p.ep81_size=usb_endpoint_maxp(e); p.ep81_interval=e->bInterval;
        } else if (e->bEndpointAddress==0x82) {
            p.ep82_type=usb_endpoint_type(e); p.ep82_size=usb_endpoint_maxp(e); p.ep82_interval=e->bInterval;
        } else return -ENODEV;
    }
    ret=tbs_op_profile_check(&p, expected_busnum, expected_devnum);
    if (ret) return ret;
    dev->runtime.policy.session_limit=session_ms;
    dev->runtime.policy.receive_limit=receive_ms;
    dev->runtime.policy.byte_limit=max_bytes;
    dev->runtime.policy.empty_success_credit=empty_success_credit;
    if (empty_success_credit>1) return -EINVAL;
    if (!session_ms || !receive_ms || max_bytes < TBS_OP_URB_SIZE ||
        max_bytes % TBS_OP_URB_SIZE) return -EINVAL;
    heartbeat_time=atomic64_read(&heartbeat_at);
    ret=tbs_op_begin(&dev->runtime.policy, tbs_now_ms(), diagnostic_enable,
                     operational_only, allow_firmware_bootstrap, READ_ONCE(consent),
                     READ_ONCE(stop), heartbeat_time, &spent);
    if (ret) return ret;
    /* Irreversible module-local consumption precedes any vendor or I2C operation. */
    if (atomic_cmpxchg(&probe_spent, 0, 1)) return -EALREADY;
    return 0;
}
#endif
