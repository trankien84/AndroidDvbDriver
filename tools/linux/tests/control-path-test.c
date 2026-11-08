/* SPDX-License-Identifier: GPL-2.0-only */
#include <assert.h>
#include <stdbool.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "tbs5530_operational_guard.h"
typedef unsigned char u8; typedef unsigned short u16;
struct mutex { int held; }; struct work_struct { int queued; };
struct tbs5530_runtime {
    int lock; struct mutex control_lock; struct work_struct drain_work;
    struct tbs_op_state policy; int shutdown, drained;
    unsigned int controls,control_requested,control_actual,first_failed_control,suppressed_controls;
    int first_failed_result,last_control_result;
};
struct tbs5530_dev { struct tbs5530_runtime runtime; void *udev; };
struct i2c_adapter { struct tbs5530_dev *data; };
struct i2c_msg { unsigned short addr,flags,len;unsigned char *buf; };
#define I2C_M_RD 1
#define GFP_KERNEL 0
#define USB_DIR_IN 128
#define USB_DIR_OUT 0
#define USB_TYPE_VENDOR 64
#define TBS_OP_TEST 1
#define min(a,b) ((a)<(b)?(a):(b))
#define spin_lock_irqsave(lock,flags) do { (void)(lock); (flags)=0; } while(0)
#define spin_unlock_irqrestore(lock,flags) do { (void)(lock); (void)(flags); } while(0)
static unsigned long long now=1000, heartbeat_at=1000;
static unsigned int raw_calls, fail_at, tests;
static int fake_result, stop_value, consent_value=1;
#define CHECK(x) do { assert(x);tests++; } while(0)
static void mutex_lock(struct mutex *m) { assert(!m->held);m->held=1; }
static void mutex_unlock(struct mutex *m) { assert(m->held);m->held=0; }
static void schedule_work(struct work_struct *w) { w->queued=1; }
static unsigned long long tbs_now_ms(void) { return now; }
static unsigned long long atomic64_read(unsigned long long *p) { return *p; }
static unsigned int usb_rcvctrlpipe(void *u,int p) { (void)u;(void)p;return 1; }
static unsigned int usb_sndctrlpipe(void *u,int p) { (void)u;(void)p;return 0; }
static void *kzalloc(unsigned int n,int f) { (void)f;return calloc(1,n); }
static void kfree(void *p) { free(p); }
static int usb_control_msg(void *u,unsigned int pipe,unsigned int request,
                           unsigned int type,unsigned int value,unsigned int index,
                           void *data,unsigned int length,unsigned int timeout)
{
    (void)u;(void)pipe;(void)request;(void)type;(void)value;(void)index;(void)data;
    assert(timeout && timeout<=2000);raw_calls++;
    return raw_calls==fail_at ? fake_result : (int)length;
}
static struct tbs5530_dev *i2c_get_adapdata(struct i2c_adapter *a) { return a->data; }
static int tbs5530_gate_locked(struct tbs5530_dev *d)
{ return tbs_op_gate(&d->runtime.policy,now,consent_value,stop_value,heartbeat_at); }
static int tbs5530_gate(struct tbs5530_dev *d) { return tbs5530_gate_locked(d); }
static int tbs5530_fault(struct tbs5530_dev *d,int ret) { return tbs_op_fail(&d->runtime.policy,ret); }
static int tbs5530_finish(struct tbs5530_dev *d,int ret)
{ return ret?tbs5530_fault(d,ret):tbs5530_gate(d); }
#include "control-under-test.inc"
static void fresh(struct tbs5530_dev *d)
{
    unsigned int spent=0;
    memset(d,0,sizeof(*d));now=1000;heartbeat_at=1000;consent_value=1;stop_value=0;
    assert(tbs_op_begin(&d->runtime.policy,now,1,1,0,1,0,heartbeat_at,&spent)==0);
    raw_calls=0;fail_at=0;fake_result=0;
}
/* Model a Sony helper that discards adapter errors, then the real board finish wrapper. */
static int ignored_sony(struct i2c_adapter *a,struct i2c_msg *m)
{ unsigned int i;for(i=0;i<25;i++) (void)tbs5530_i2c_transfer(a,m,1);return 0; }
int main(void)
{
    struct tbs5530_dev d;struct i2c_adapter a={&d};
    unsigned char data[]={0,0},reg=0,in[1]={0};
    struct i2c_msg w={0x64,0,2,data};
    struct i2c_msg read[2]={{0x64,0,1,&reg},{0x64,I2C_M_RD,1,in}};
    unsigned int i;int mode;
    for(mode=0;mode<2;mode++) {
        fresh(&d);fail_at=1;fake_result=mode?-EPROTO:0;
        CHECK(tbs5530_finish(&d,ignored_sony(&a,&w))==(mode?-EPROTO:-EIO));
        CHECK(raw_calls==1 && d.runtime.controls==1 && d.runtime.first_failed_control==1);
        CHECK(d.runtime.first_failed_result==fake_result && d.runtime.suppressed_controls==24);
        CHECK(d.runtime.control_requested==4 && d.runtime.control_actual==0);
    }
    fresh(&d);fail_at=1;fake_result=-EIO;
    CHECK(tbs5530_i2c_transfer(&a,read,2)==-EIO && raw_calls==1);
    fresh(&d);fail_at=2;fake_result=0;
    CHECK(tbs5530_i2c_transfer(&a,read,2)==-EIO && raw_calls==2);
    CHECK(tbs5530_i2c_transfer(&a,read,2)==-EIO && raw_calls==2);
    fresh(&d);CHECK(tbs5530_i2c_transfer(&a,read,2)==2 && raw_calls==2);
    fresh(&d);w.addr=0x69;CHECK(tbs5530_i2c_transfer(&a,&w,1)==-EPERM && raw_calls==0);w.addr=0x64;
    for(i=0;i<3;i++) {
        fresh(&d);if(i==0)stop_value=1;if(i==1)consent_value=0;if(i==2)now=4000;
        CHECK(tbs5530_i2c_transfer(&a,&w,1)<0 && raw_calls==0);
        stop_value=0;consent_value=1;heartbeat_at=now;
        CHECK(tbs5530_i2c_transfer(&a,&w,1)<0 && raw_calls==0);
    }
    fresh(&d);data[0]=10;data[1]=0;
    CHECK(tbs5530_op_rw(&d,0x8a,0,0,data,2,0)==2 && raw_calls==1);
    CHECK(tbs5530_op_rw(&d,0xa0,0,0,data,2,0)<0 && raw_calls==1);
    printf("PASS %u extracted central-control/I2C/ignored-error-wrapper checks; no kernel/USB I/O.\n",tests);
    return 0;
}
