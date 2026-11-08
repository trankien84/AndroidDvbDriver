/* SPDX-License-Identifier: GPL-2.0-only */
#include <assert.h>
#include <stdbool.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include "tbs5530_operational_guard.h"
#define NUM_URBS 8
#define GFP_ATOMIC 0
struct urb { void *context; int status; unsigned int actual_length,transfer_buffer_length; void *transfer_buffer; };
struct work_struct { unsigned int scheduled; };
struct tbs5530_runtime {
    int lock; struct tbs_op_state policy; bool shutdown,drained;
    bool in_flight[8]; struct work_struct drain_work;
    int last_status; unsigned int last_requested,last_actual;
};
struct tbs5530_dev { struct tbs5530_runtime runtime; struct urb *urbs[8]; int demux; };
#define spin_lock_irqsave(lock,flags) do { (void)(lock);(flags)=0; } while(0)
#define spin_unlock_irqrestore(lock,flags) do { (void)(lock);(void)(flags); } while(0)
static unsigned int tests,raw_submits,demux_calls,demux_bytes;
static unsigned long long now=1000,hb=1000;
static int consent=1,stopped;
#define CHECK(x) do { assert(x);tests++; } while(0)
static void schedule_work(struct work_struct *w) { w->scheduled++; }
static int tbs5530_gate_locked(struct tbs5530_dev *d)
{ return tbs_op_gate(&d->runtime.policy,now,consent,stopped,hb); }
static int tbs5530_gate(struct tbs5530_dev *d) { return tbs5530_gate_locked(d); }
static int usb_submit_urb(struct urb *u,int flags) { (void)u;(void)flags;raw_submits++;return 0; }
static void dvb_dmx_swfilter(int *demux,void *data,unsigned int size)
{ (void)demux;(void)data;assert(size>0);demux_calls++;demux_bytes+=size; }
#include "receive-under-test.inc"
static struct urb urbs[8];
static void fresh(struct tbs5530_dev *d,unsigned int credit)
{
    unsigned int i,spent=0;memset(d,0,sizeof(*d));memset(urbs,0,sizeof(urbs));
    now=1000;hb=1000;consent=1;stopped=0;raw_submits=demux_calls=demux_bytes=0;
    d->runtime.policy.empty_success_credit=credit;
    assert(tbs_op_begin(&d->runtime.policy,now,1,1,0,1,0,hb,&spent)==0);
    assert(tbs_op_receive_begin(&d->runtime.policy,now)==0);
    for(i=0;i<8;i++) { d->urbs[i]=&urbs[i];urbs[i].context=d;urbs[i].transfer_buffer_length=65536;assert(tbs5530_submit_locked(d,i,false)==0); }
}
static void complete(unsigned int slot,int status,unsigned int bytes)
{ urbs[slot].status=status;urbs[slot].actual_length=bytes;tbs5530_urb_complete(&urbs[slot]); }
static void cancelled(struct tbs5530_dev *d)
{
    unsigned int i;int fault=d->runtime.policy.fault;
    for(i=0;i<8;i++) if(d->runtime.in_flight[i]) complete(i,-ENOENT,0);
    CHECK(d->runtime.policy.outstanding==0);
    CHECK(d->runtime.policy.reaped==d->runtime.policy.submitted);
    CHECK(d->runtime.policy.fault==fault);
}
int main(void)
{
    struct tbs5530_dev d;struct tbs_op_state invalid={0};unsigned int spent=0,i;
    invalid.empty_success_credit=2;
    CHECK(tbs_op_begin(&invalid,1000,1,1,0,1,0,1000,&spent)==-EINVAL && !spent);
    fresh(&d,0);complete(0,0,0);
    CHECK(d.runtime.policy.fault==-ENODATA && raw_submits==8 && demux_calls==0);
    CHECK(d.runtime.policy.empty_successes==1 && d.runtime.policy.empty_resubmits==0);
    cancelled(&d);
    fresh(&d,1);complete(0,0,82);complete(1,0,0);complete(0,0,188);
    CHECK(!d.runtime.policy.fault && raw_submits==11);
    CHECK(demux_calls==2 && demux_bytes==270);
    CHECK(d.runtime.policy.empty_successes==1 && d.runtime.policy.empty_resubmits==1);
    CHECK(d.runtime.policy.first_rx_seen && d.runtime.policy.first_rx_status==0 && d.runtime.policy.first_rx_length==82);
    complete(1,0,0);
    CHECK(d.runtime.policy.fault==-ENODATA && raw_submits==11 && demux_calls==2);
    cancelled(&d);
    CHECK(d.runtime.policy.first_rx_length==82 && d.runtime.last_status==-ENOENT);
    fresh(&d,1);complete(0,0,0);complete(1,0,0);
    CHECK(d.runtime.policy.fault==-ENODATA && raw_submits==9 && demux_calls==0);
    CHECK(d.runtime.policy.empty_successes==2 && d.runtime.policy.empty_resubmits==1);
    cancelled(&d);
    for(i=0;i<2;i++) {
        fresh(&d,1);if(i)complete(0,0,0);complete(1,-EOVERFLOW,0);
        CHECK(d.runtime.policy.fault==-EOVERFLOW && raw_submits==(i?9U:8U));
        cancelled(&d);
        CHECK(d.runtime.policy.empty_resubmits==(i?1U:0U));
    }
    fresh(&d,1);complete(0,7,0);
    CHECK(d.runtime.policy.fault==-EIO && raw_submits==8);cancelled(&d);
    fresh(&d,1);d.runtime.policy.byte_limit=d.runtime.policy.requested;complete(0,0,0);
    CHECK(d.runtime.policy.fault==-EFBIG && raw_submits==8 && d.runtime.policy.empty_resubmits==0);cancelled(&d);
    for(i=0;i<4;i++) {
        fresh(&d,1);
        if(i==0){now=11000;hb=11000;}
        if(i==1){now=4000;hb=1000;}
        if(i==2)stopped=1;
        if(i==3)consent=0;
        complete(0,0,0);
        CHECK(d.runtime.policy.fault<0 && raw_submits==8 && demux_calls==0 && d.runtime.policy.empty_resubmits==0);
        cancelled(&d);
    }
    printf("PASS %u exact extracted completion/resubmit checks; synthetic82/188 byte lengths are not hardware TS validation.\n",tests);
    return 0;
}
