/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_MIN_GUARD_H
#define TBS5530_MIN_GUARD_H
#include <crypto/sha2.h>
#include <linux/ktime.h>
#include <linux/workqueue.h>

static bool minimal_enable, allow_cold_firmware, operational_handoff, stop;
static unsigned int expected_busnum, expected_devnum;
static unsigned long heartbeat;
static atomic64_t heartbeat_at;
static atomic_t probe_spent = ATOMIC_INIT(0);
static DEFINE_MUTEX(min_live_lock);
static struct tbs5530_dev *min_live;
static int tbs_m_telemetry(char *buffer, const struct kernel_param *kp)
{
	struct tbs5530_dev *dev;
	int count;
	mutex_lock(&min_live_lock);
	dev = min_live;
	if (dev) count = scnprintf(buffer, PAGE_SIZE,
		"bound=1 streaming=%u submitted=%d reaped=%d outstanding=%d bytes=%lld fault=%d firmware_calls=%u\n",
		READ_ONCE(dev->streaming), atomic_read(&dev->min_submitted), atomic_read(&dev->min_reaped),
		atomic_read(&dev->min_outstanding), atomic64_read(&dev->min_bytes),
		atomic_read(&dev->min_fault), dev->min_fw_calls);
	else count = scnprintf(buffer, PAGE_SIZE, "bound=0 consumed=%d\n", atomic_read(&probe_spent));
	mutex_unlock(&min_live_lock);
	return count;
}
static const struct kernel_param_ops tbs_m_telemetry_ops = { .get = tbs_m_telemetry };
module_param_cb(telemetry, &tbs_m_telemetry_ops, NULL, 0400);
module_param(minimal_enable, bool, 0400);
module_param(allow_cold_firmware, bool, 0400);
module_param(operational_handoff, bool, 0400);
module_param(expected_busnum, uint, 0400);
module_param(expected_devnum, uint, 0400);

static u64 tbs_m_now(void) { return ktime_to_ms(ktime_get_boottime()); }
static int tbs_m_stop(const char *value, const struct kernel_param *kp)
{
	bool next;
	if (kstrtobool(value, &next) || !next) return -EINVAL;
	WRITE_ONCE(stop, true);
	return 0;
}
static const struct kernel_param_ops tbs_m_stop_ops = {
	.set = tbs_m_stop, .get = param_get_bool,
};
module_param_cb(stop, &tbs_m_stop_ops, &stop, 0600);
static int tbs_m_heartbeat(const char *value, const struct kernel_param *kp)
{
	unsigned long next;
	if (kstrtoul(value, 10, &next) || !next || next <= READ_ONCE(heartbeat) || READ_ONCE(stop))
		return -EINVAL;
	WRITE_ONCE(heartbeat, next);
	atomic64_set(&heartbeat_at, tbs_m_now());
	return 0;
}
static const struct kernel_param_ops tbs_m_heartbeat_ops = {
	.set = tbs_m_heartbeat, .get = param_get_ulong,
};
module_param_cb(heartbeat, &tbs_m_heartbeat_ops, &heartbeat, 0600);

static int tbs_m_fail(struct tbs5530_dev *dev, int error)
{
	atomic_cmpxchg(&dev->min_fault, 0, error);
	return atomic_read(&dev->min_fault);
}
static int tbs_m_gate(struct tbs5530_dev *dev)
{
	u64 now = tbs_m_now(), pulse = atomic64_read(&heartbeat_at);
	if (atomic_read(&dev->min_fault)) return atomic_read(&dev->min_fault);
	if (READ_ONCE(stop)) return tbs_m_fail(dev, -ECANCELED);
	if (!pulse || now < pulse || now - pulse >= 2000 ||
	    now - dev->min_admitted >= 120000 ||
	    (dev->min_started && now - dev->min_receive >= 10000))
		return tbs_m_fail(dev, -ETIMEDOUT);
	return 0;
}
static void tbs5530_stop_streaming(struct tbs5530_dev *dev);
static void tbs_m_watch(struct work_struct *work)
{
	struct tbs5530_dev *dev = container_of(to_delayed_work(work), struct tbs5530_dev, min_watch);
	if (tbs_m_gate(dev)) tbs5530_stop_streaming(dev);
	else schedule_delayed_work(&dev->min_watch, msecs_to_jiffies(100));
}
static void tbs_m_disable(struct tbs5530_dev *dev)
{
	unsigned long flags;
	spin_lock_irqsave(&dev->min_submit_lock, flags);
	dev->streaming = false;
	spin_unlock_irqrestore(&dev->min_submit_lock, flags);
}
static int tbs_m_submit(struct tbs5530_dev *dev, struct urb *urb)
{
	unsigned long flags;
	int ret;
	spin_lock_irqsave(&dev->min_submit_lock, flags);
	ret = tbs_m_gate(dev);
	if (!ret && !dev->streaming) ret = -ECANCELED;
	if (!ret && atomic_read(&dev->min_submitted) >= 4096) ret = tbs_m_fail(dev, -E2BIG);
	if (!ret) {
		atomic_inc(&dev->min_submitted);
		atomic_inc(&dev->min_outstanding);
		ret = usb_submit_urb(urb, GFP_ATOMIC);
		if (ret) {
			atomic_dec(&dev->min_outstanding);
			tbs_m_fail(dev, ret);
		}
	}
	spin_unlock_irqrestore(&dev->min_submit_lock, flags);
	return ret;
}
static int tbs_m_control(struct tbs5530_dev *dev, u8 request, u16 value,
		       u16 index, const u8 *data, u16 len, int flags)
{
	int ret = tbs_m_gate(dev);
	if (ret) return ret;
	if (index || !len || !data) return tbs_m_fail(dev, -EPERM);
	if (request == 0xa0 && dev->min_loading && flags == 1) {
		unsigned int n = dev->min_fw_calls;
		u16 address = n == 0 || n == 130 ? 0x7f92 : n == 1 || n == 131 ? 0xe600 : (n - 2) * 64;
		if (n >= 132 || value != address || len != (n < 2 || n >= 130 ? 1 : 64) ||
		    ((n < 2 || n >= 130) && data[0] != (n < 2 ? 1 : 0)))
			return tbs_m_fail(dev, -EPERM);
		return 0;
	}
	if (value) return tbs_m_fail(dev, -EPERM);
	if (request == 0x8a && flags == 1 && len == 2 && data[0] == 10 && data[1] == 0) return 0;
	if (request == 0x80 || request == 0x90) {
		if (len < 2 || (data[1] != 0xc8 && data[1] != 0xcc && data[1] != 0x88 && data[1] != 0x20 && data[1] != 0xc0))
			return tbs_m_fail(dev, -EPERM);
	}
	if (request == 0x90 && flags == 1 && len == 3 && data[0] && data[0] <= 50) return 0;
	if (request == 0x91 && flags == 0 && len <= 50) return 0;
	if (request == 0x80 && flags == 1 && len >= 3 && len <= 50 && data[0] == len - 1) return 0;
	return tbs_m_fail(dev, -EPERM);
}
static int tbs_m_admit(struct usb_interface *intf)
{
	struct usb_device *udev = interface_to_usbdev(intf);
	struct usb_host_interface *alt = intf->cur_altsetting;
	int i, found = 0;
	if (!minimal_enable || allow_cold_firmware == operational_handoff || READ_ONCE(stop) ||
	    !READ_ONCE(heartbeat) || udev->bus->busnum != expected_busnum || udev->devnum != expected_devnum ||
	    udev->descriptor.idVendor != cpu_to_le16(0x734c) ||
	    udev->descriptor.idProduct != cpu_to_le16(0x5530) ||
	    udev->speed != USB_SPEED_HIGH || !udev->actconfig ||
	    udev->actconfig->desc.bConfigurationValue != 1 ||
	    alt->desc.bInterfaceNumber || alt->desc.bAlternateSetting)
		return -EPERM;
	for (i = 0; i < alt->desc.bNumEndpoints; i++) {
		struct usb_endpoint_descriptor *ep = &alt->endpoint[i].desc;
		if (ep->bEndpointAddress == 0x82 && usb_endpoint_is_bulk_in(ep) && usb_endpoint_maxp(ep) == 512)
			found++;
	}
	if (operational_handoff) {
		static const u8 operational_hash[32] = {
			0x22,0xe2,0xf1,0xdf,0x9f,0x66,0xc5,0x8d,0xbb,0x6c,0x55,0x4e,0x40,0x0b,0x6b,0x8a,
			0xbc,0xb7,0x8e,0xe4,0xf2,0x77,0xe0,0x36,0x79,0xbc,0x0f,0xac,0x7a,0x25,0xad,0xd5
		};
		u8 profile[50], actual[32];
		if (found != 1 || alt->desc.bNumEndpoints != 2 || udev->descriptor.bNumConfigurations != 1 ||
		    le16_to_cpu(udev->actconfig->desc.wTotalLength) != 32 || !udev->rawdescriptors[0]) return -EPERM;
		memcpy(profile, &udev->descriptor, 18); memcpy(profile + 18, udev->rawdescriptors[0], 32);
		sha256(profile, sizeof(profile), actual);
		if (memcmp(actual, operational_hash, sizeof(actual))) return -EPERM;
		return atomic_cmpxchg(&probe_spent, 0, 1) ? -EPERM : 0;
	}
	if (found) return -EPERM;
	{
		static const u8 cold_hash[32] = {
			0xe2,0x88,0x7b,0xad,0x8b,0x5a,0x0e,0x8d,0xca,0xaa,0x69,0xbd,0x63,0x6a,0x01,0xa2,
			0xd1,0x1e,0x53,0xfe,0x4b,0x53,0x2b,0x5b,0x14,0x82,0x8a,0x2d,0x3d,0xf9,0x52,0xde
		};
		u8 profile[189], actual[32];
		if (alt->desc.bNumEndpoints || le16_to_cpu(udev->actconfig->desc.wTotalLength) != 171 ||
		    udev->descriptor.bNumConfigurations != 1 || !udev->rawdescriptors[0]) return -EPERM;
		memcpy(profile, &udev->descriptor, 18);
		memcpy(profile + 18, udev->rawdescriptors[0], 171);
		sha256(profile, sizeof(profile), actual);
		if (memcmp(actual, cold_hash, sizeof(actual))) return -EPERM;
	}
	if (atomic_cmpxchg(&probe_spent, 0, 1)) return -EPERM;
	return 0;
}
static bool tbs_m_firmware_valid(const struct firmware *fw)
{
	static const u8 expected[32] = {
		0xb9,0x7c,0xf2,0x08,0xbb,0x58,0x6f,0x70,0x6b,0x26,0xaa,0x54,0x50,0xde,0x6b,0xb9,
		0x73,0x75,0xa3,0xe3,0xea,0xbe,0x83,0x3c,0xdd,0xb0,0xae,0x3a,0x4f,0x69,0xd0,0xdc
	};
	u8 actual[32];
	if (fw->size != 8192) return false;
	sha256(fw->data, fw->size, actual);
	return !memcmp(actual, expected, sizeof(actual));
}
static int tbs_m_profile(struct dvb_frontend *fe)
{
	struct dtv_frontend_properties *p = &fe->dtv_property_cache;
	struct tbs5530_dev *dev = fe->dvb->priv;
	if (tbs_m_gate(dev)) return atomic_read(&dev->min_fault);
	if (p->delivery_system != SYS_DVBT2 || p->frequency != 594000000 ||
	    p->bandwidth_hz != 8000000 || p->stream_id != 0)
		return tbs_m_fail(dev, -EPERM);
	return 0;
}
static int tbs_m_set_frontend(struct dvb_frontend *fe)
{
	struct tbs5530_dev *dev = fe->dvb->priv;
	int ret = tbs_m_profile(fe);
	return ret ? ret : dev->fe_ter_ops_orig.set_frontend(fe);
}
static int tbs_m_tune(struct dvb_frontend *fe, bool re_tune, unsigned int mode_flags,
		      unsigned int *delay, enum fe_status *status)
{
	struct tbs5530_dev *dev = fe->dvb->priv;
	int ret = tbs_m_profile(fe);
	return ret ? ret : dev->fe_ter_ops_orig.tune(fe, re_tune, mode_flags, delay, status);
}
#endif
