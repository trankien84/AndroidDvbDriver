/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_RUNTIME_IO_H
#define TBS5530_RUNTIME_IO_H

/* control_lock spans both 0x90 and 0x91, not only individual USB calls. */
static int tbs5530_control_locked(struct tbs5530_dev *dev, u8 request,
                                  u16 value, u16 index, u8 *data, u16 len,
                                  int read)
{
    struct tbs5530_runtime *r=&dev->runtime;
    unsigned long flags;
    unsigned long long now, remaining, heartbeat_remaining, heartbeat_time;
    unsigned int timeout;
    unsigned char *buffer;
    int ret;
    spin_lock_irqsave(&r->lock, flags);
    ret=tbs5530_gate_locked(dev);
    if (!ret) ret=tbs_op_control_check(&r->policy, request, value, index, data, len, read);
    if (ret) {
        r->suppressed_controls++;
        tbs_op_fail(&r->policy, ret);
        if (!r->shutdown) schedule_work(&r->drain_work);
        spin_unlock_irqrestore(&r->lock, flags);
        return ret;
    }
    heartbeat_time=atomic64_read(&heartbeat_at);
    now=tbs_now_ms();
    if (now-r->policy.admitted_ms>=r->policy.session_limit ||
        now-heartbeat_time>=TBS_OP_HEARTBEAT_MS ||
        (r->policy.receiving && now-r->policy.receive_ms>=r->policy.receive_limit)) {
        ret=tbs_op_fail(&r->policy,-ETIMEDOUT);
        schedule_work(&r->drain_work);
        spin_unlock_irqrestore(&r->lock,flags);
        return ret;
    }
    remaining=r->policy.session_limit-(now-r->policy.admitted_ms);
    heartbeat_remaining=TBS_OP_HEARTBEAT_MS-(now-heartbeat_time);
    remaining=min(remaining, heartbeat_remaining);
    if (r->policy.receiving)
        remaining=min(remaining, r->policy.receive_limit-(now-r->policy.receive_ms));
    timeout=(unsigned int)min(remaining, 2000ULL);
    if (!timeout) {
        ret=tbs_op_fail(&r->policy, -ETIMEDOUT);
        schedule_work(&r->drain_work);
        spin_unlock_irqrestore(&r->lock, flags);
        return ret;
    }
    r->drained=false;
    spin_unlock_irqrestore(&r->lock, flags);
    buffer=kzalloc(len,GFP_KERNEL);
    if (!buffer) return tbs5530_fault(dev,-ENOMEM);
    if (!read) memcpy(buffer, data, len);
    ret=tbs5530_gate(dev);
    if (ret) { kfree(buffer); return ret; }
    spin_lock_irqsave(&r->lock,flags);
    r->controls++;
    r->control_requested+=len;
    spin_unlock_irqrestore(&r->lock,flags);
    ret=usb_control_msg(dev->udev,
          read ? usb_rcvctrlpipe(dev->udev,0) : usb_sndctrlpipe(dev->udev,0),
          request, (read ? USB_DIR_IN : USB_DIR_OUT) | USB_TYPE_VENDOR,
          value, index, buffer, len, timeout);
    spin_lock_irqsave(&r->lock, flags);
    r->last_control_result=ret;
    if (ret>0) r->control_actual+=ret;
    if (ret!=len && !r->first_failed_control) {
        r->first_failed_control=r->controls;
        r->first_failed_result=ret;
    }
    ret=tbs_op_control_result(&r->policy, request, data, len, ret);
    if (!ret) ret=tbs5530_gate_locked(dev);
    if (ret && !r->shutdown) schedule_work(&r->drain_work);
    spin_unlock_irqrestore(&r->lock, flags);
    if (!ret && read) memcpy(data, buffer, len);
    kfree(buffer);
    return ret ? ret : len;
}
static int tbs5530_op_rw(struct tbs5530_dev *dev, u8 request, u16 value,
                         u16 index, u8 *data, u16 len, int read)
{
    int ret;
    mutex_lock(&dev->runtime.control_lock);
    ret=tbs5530_control_locked(dev, request, value, index, data, len, read);
    mutex_unlock(&dev->runtime.control_lock);
    return ret;
}
static int tbs5530_i2c_transfer(struct i2c_adapter *adap,
                                struct i2c_msg msg[], int num)
{
    struct tbs5530_dev *dev=i2c_get_adapdata(adap);
    u8 output[50], input[50];
    int ret;
    if (!dev) return -ENODEV;
    if (!msg || (num!=1 && num!=2)) return tbs5530_fault(dev,-EOPNOTSUPP);
    if (!msg[0].buf || msg[0].flags || !msg[0].len ||
        msg[0].len>48 || msg[0].addr>0x7f)
        return tbs5530_fault(dev,-EINVAL);
    if (msg[0].addr!=0x64 && msg[0].addr!=0x66 && msg[0].addr!=0x44 &&
        msg[0].addr!=0x10 && msg[0].addr!=0x60)
        return tbs5530_fault(dev,-EPERM);
    if (num==2 && (msg[0].len!=1 || !msg[1].buf ||
        msg[1].flags!=I2C_M_RD || msg[1].addr!=msg[0].addr ||
        !msg[1].len || msg[1].len>50)) return tbs5530_fault(dev,-EINVAL);
    mutex_lock(&dev->runtime.control_lock);
    if (num==2) {
        output[0]=msg[1].len; output[1]=msg[0].addr<<1; output[2]=msg[0].buf[0];
        ret=tbs5530_control_locked(dev,0x90,0,0,output,3,0);
        if (ret==3) {
            ret=tbs5530_control_locked(dev,0x91,0,0,input,msg[1].len,1);
            if (ret==msg[1].len) memcpy(msg[1].buf,input,msg[1].len);
        }
        if (ret==msg[1].len) ret=num;
    } else {
        output[0]=msg[0].len+1; output[1]=msg[0].addr<<1;
        memcpy(output+2,msg[0].buf,msg[0].len);
        ret=tbs5530_control_locked(dev,0x80,0,0,output,msg[0].len+2,0);
        if (ret==msg[0].len+2) ret=num;
    }
    mutex_unlock(&dev->runtime.control_lock);
    return ret;
}
static u32 tbs5530_i2c_func(struct i2c_adapter *adapter) { return I2C_FUNC_I2C; }
static struct i2c_algorithm tbs5530_i2c_algo={
    .master_xfer=tbs5530_i2c_transfer, .functionality=tbs5530_i2c_func
};

static int tbs5530_slot(struct tbs5530_dev *dev, struct urb *urb)
{
    int i;
    for (i=0;i<NUM_URBS;i++) if (dev->urbs[i]==urb) return i;
    return -1;
}
/* runtime.lock serializes reservation/submit with terminal flags and completions. */
static int tbs5530_submit_locked(struct tbs5530_dev *dev, int slot, bool empty_success)
{
    struct tbs5530_runtime *r=&dev->runtime;
    int ret=tbs5530_gate_locked(dev);
    if (!ret) ret=tbs_op_reserve(&r->policy);
    if (ret) goto fail;
    if (r->in_flight[slot]) { ret=-EPROTO; r->policy.outstanding--; goto fail; }
    r->in_flight[slot]=true;
    r->drained=false;
    ret=usb_submit_urb(dev->urbs[slot],GFP_ATOMIC);
    if (ret) {
        r->in_flight[slot]=false;
        r->policy.outstanding--;
        goto fail;
    }
    r->policy.submitted++;
    if (empty_success) r->policy.empty_resubmits++;
    return 0;
fail:
    tbs_op_fail(&r->policy,ret);
    if (!r->shutdown) schedule_work(&r->drain_work);
    return ret;
}
static void tbs5530_urb_complete(struct urb *urb)
{
    struct tbs5530_dev *dev=urb->context;
    struct tbs5530_runtime *r=&dev->runtime;
    unsigned long flags;
    int slot=tbs5530_slot(dev,urb), ret;
    bool deliver=false, resubmit=false, empty_success=false;
    spin_lock_irqsave(&r->lock,flags);
    if (slot<0 || !r->in_flight[slot]) {
        tbs_op_fail(&r->policy,-EPROTO);
        goto finish;
    }
    r->in_flight[slot]=false;
    r->last_status=urb->status;
    r->last_requested=urb->transfer_buffer_length;
    r->last_actual=urb->actual_length;
    ret=tbs_op_complete(&r->policy,urb->status,urb->actual_length);
    if (ret>=0) {
        empty_success=ret==1;
        ret=tbs5530_gate_locked(dev);
        if (!ret) { resubmit=true; deliver=!empty_success; }
    }
finish:
    if (r->policy.fault && !r->shutdown) schedule_work(&r->drain_work);
    spin_unlock_irqrestore(&r->lock,flags);
    if (resubmit && tbs5530_gate(dev)) { deliver=false; resubmit=false; }
    if (deliver)
        dvb_dmx_swfilter(&dev->demux,urb->transfer_buffer,urb->actual_length);
    spin_lock_irqsave(&r->lock,flags);
    if (resubmit && !r->shutdown && !r->policy.fault)
        tbs5530_submit_locked(dev,slot,empty_success);
    spin_unlock_irqrestore(&r->lock,flags);
}
static void tbs5530_drain(struct tbs5530_dev *dev)
{
    struct tbs5530_runtime *r=&dev->runtime;
    unsigned long flags;
    int i;
    mutex_lock(&r->receive_lock);
    mutex_lock(&r->control_lock);
    mutex_unlock(&r->control_lock);
    for (i=0;i<NUM_URBS;i++) if (dev->urbs[i]) usb_kill_urb(dev->urbs[i]);
    spin_lock_irqsave(&r->lock,flags);
    r->drained=r->policy.outstanding==0;
    if (!r->drained) r->quarantined=true;
    spin_unlock_irqrestore(&r->lock,flags);
    mutex_unlock(&r->receive_lock);
}
static void tbs5530_drain_worker(struct work_struct *work)
{
    struct tbs5530_runtime *r=container_of(work,struct tbs5530_runtime,drain_work);
    struct tbs5530_dev *dev=container_of(r,struct tbs5530_dev,runtime);
    tbs5530_drain(dev);
}
static void tbs5530_monitor_worker(struct work_struct *work)
{
    struct tbs5530_runtime *r=container_of(to_delayed_work(work),struct tbs5530_runtime,monitor_work);
    struct tbs5530_dev *dev=container_of(r,struct tbs5530_dev,runtime);
    unsigned long flags;
    spin_lock_irqsave(&r->lock,flags);
    if (!tbs5530_gate_locked(dev) && !r->shutdown)
        schedule_delayed_work(&r->monitor_work,msecs_to_jiffies(50));
    spin_unlock_irqrestore(&r->lock,flags);
}
static int tbs5530_start_streaming(struct tbs5530_dev *dev)
{
    struct tbs5530_runtime *r=&dev->runtime;
    unsigned long flags;
    u8 data[2]={0x0a,0};
    int i, ret;
    mutex_lock(&r->receive_lock);
    spin_lock_irqsave(&r->lock,flags);
    ret=tbs5530_gate_locked(dev);
    if (!ret) ret=tbs_op_receive_begin(&r->policy,tbs_now_ms());
    if (ret) tbs_op_fail(&r->policy,ret);
    spin_unlock_irqrestore(&r->lock,flags);
    if (ret) goto out;
    ret=tbs5530_op_rw(dev,0x8a,0,0,data,2,0);
    if (ret!=2) goto out;
    for (i=0;i<NUM_URBS;i++) {
        usb_fill_bulk_urb(dev->urbs[i],dev->udev,usb_rcvbulkpipe(dev->udev,0x82),
                          dev->urb_bufs[i],URB_BUF_SIZE,tbs5530_urb_complete,dev);
        spin_lock_irqsave(&r->lock,flags);
        ret=tbs5530_submit_locked(dev,i,false);
        spin_unlock_irqrestore(&r->lock,flags);
        if (ret) break;
    }
out:
    mutex_unlock(&r->receive_lock);
    return ret==2 ? 0 : ret;
}
static void tbs5530_stop_streaming(struct tbs5530_dev *dev)
{
    tbs5530_fault(dev,-ECANCELED);
    flush_work(&dev->runtime.drain_work);
}
static int tbs5530_start_feed(struct dvb_demux_feed *feed)
{
    struct tbs5530_dev *dev=feed->demux->priv;
    int ret;
    if (dev->feed_count) return tbs5530_fault(dev,-EALREADY);
    ret=tbs5530_start_streaming(dev);
    if (!ret) dev->feed_count=1;
    return ret;
}
static int tbs5530_stop_feed(struct dvb_demux_feed *feed)
{
    struct tbs5530_dev *dev=feed->demux->priv;
    dev->feed_count=0;
    tbs5530_stop_streaming(dev);
    return 0;
}
static void tbs5530_runtime_init(struct tbs5530_dev *dev)
{
    spin_lock_init(&dev->runtime.lock);
    mutex_init(&dev->runtime.control_lock);
    mutex_init(&dev->runtime.receive_lock);
    INIT_WORK(&dev->runtime.drain_work,tbs5530_drain_worker);
    INIT_DELAYED_WORK(&dev->runtime.monitor_work,tbs5530_monitor_worker);
    dev->runtime.drained=true;
}
static bool tbs5530_runtime_shutdown(struct tbs5530_dev *dev)
{
    struct tbs5530_runtime *r=&dev->runtime;
    unsigned long flags;
    mutex_lock(&live_lock);
    spin_lock_irqsave(&r->lock,flags);
    r->shutdown=true;
    tbs_op_fail(&r->policy,-ENODEV);
    spin_unlock_irqrestore(&r->lock,flags);
    if (live_device==dev) live_device=NULL;
    mutex_unlock(&live_lock);
    cancel_delayed_work_sync(&r->monitor_work);
    cancel_work_sync(&r->drain_work);
    mutex_lock(&r->control_lock);
    mutex_unlock(&r->control_lock);
    tbs5530_drain(dev);
    mutex_lock(&live_lock);
    spin_lock_irqsave(&r->lock,flags);
    tbs5530_format_telemetry(terminal_telemetry,dev);
    spin_unlock_irqrestore(&r->lock,flags);
    mutex_unlock(&live_lock);
    return r->drained && !r->quarantined;
}
#endif
