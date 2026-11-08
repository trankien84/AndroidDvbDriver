/* SPDX-License-Identifier: GPL-2.0-only */
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include "tbs5530_operational_guard.h"
static unsigned int cases, writes;
#define CHECK(x) do { assert(x); cases++; } while (0)
static struct tbs_op_profile profile(void)
{
    struct tbs_op_profile p={0};
    p.vid=0x734c;p.pid=0x5530;p.bus=2;p.device=2;p.high_speed=1;
    p.configuration=1;p.configurations=1;p.interfaces=1;p.alternate_settings=1;
    p.interface_class=0xff;p.ep0_size=64;p.endpoints=2;
    p.ep81_type=2;p.ep81_size=512;p.ep82_type=2;p.ep82_size=512;p.ep82_interval=1;
    p.digest=tbs_op_descriptor_hash;
    return p;
}
static struct tbs_op_state admitted(void)
{
    struct tbs_op_state s={0};
    unsigned int spent=0;
    assert(tbs_op_begin(&s,1000,1,1,0,1,0,1000,&spent)==0 && spent==1);
    return s;
}
static int control(struct tbs_op_state *s,unsigned long long now,unsigned int request,
                   unsigned char *data,unsigned int len,int read,int result)
{
    int ret=tbs_op_gate(s,now,1,0,now);
    if (ret) return ret;
    ret=tbs_op_control_check(s,request,0,0,data,len,read);
    if (ret) return tbs_op_fail(s,ret);
    writes++;
    return tbs_op_control_result(s,request,data,len,result);
}
int main(void)
{
    struct tbs_op_profile good=profile(),p;
    struct tbs_op_state s;
    unsigned char bad_hash[32],wr[]={2,0xc8,0},setup[]={1,0xc8,0},in[2]={0},stream[]={10,0};
    unsigned int spent=0,i;
    int error;
    CHECK(tbs_op_profile_check(&good,2,2)==0);
#define BAD(field,value) do { p=good;p.field=value;CHECK(tbs_op_profile_check(&p,2,2)<0); } while (0)
    BAD(vid,0);BAD(pid,0);BAD(bus,3);BAD(device,3);BAD(high_speed,0);
    BAD(configuration,2);BAD(configurations,2);BAD(interfaces,2);BAD(alternate_settings,2);
    BAD(interface_number,1);BAD(alternate,1);BAD(interface_class,0);BAD(interface_subclass,1);
    BAD(interface_protocol,1);BAD(ep0_size,8);BAD(endpoints,1);BAD(ep81_type,3);BAD(ep81_size,64);
    BAD(ep81_interval,1);BAD(ep82_type,3);BAD(ep82_size,64);BAD(ep82_interval,0);
    memcpy(bad_hash,tbs_op_descriptor_hash,32);bad_hash[31]^=1;
    p=good;p.digest=bad_hash;CHECK(tbs_op_profile_check(&p,2,2)<0);
    p.digest=NULL;CHECK(tbs_op_profile_check(&p,2,2)<0);
    CHECK(tbs_op_profile_check(&good,0,2)<0);CHECK(tbs_op_profile_check(NULL,2,2)<0);
    memset(&s,0,sizeof(s));CHECK(tbs_op_begin(&s,1000,0,1,0,1,0,1000,&spent)<0 && !spent);
    CHECK(tbs_op_begin(&s,1000,1,0,0,1,0,1000,&spent)<0 && !spent);
    CHECK(tbs_op_begin(&s,1000,1,1,1,1,0,1000,&spent)<0 && !spent);
    CHECK(tbs_op_begin(&s,1000,1,1,0,0,0,1000,&spent)<0 && !spent);
    memset(&s,0,sizeof(s));CHECK(tbs_op_begin(&s,1000,1,1,0,1,0,1000,&spent)==0 && spent);
    memset(&s,0,sizeof(s));CHECK(tbs_op_begin(&s,1001,1,1,0,1,0,1001,&spent)==-EALREADY);
    s=admitted();CHECK(tbs_op_gate(&s,1001,1,1,1001)==-ECANCELED);
    CHECK(tbs_op_gate(&s,1002,1,0,1002)==-ECANCELED);
    s=admitted();CHECK(tbs_op_gate(&s,4000,1,0,1000)==-ETIMEDOUT);
    s=admitted();CHECK(tbs_op_gate(&s,121000,1,0,121000)==-ETIMEDOUT);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);
    CHECK(tbs_op_gate(&s,11001,1,0,11001)==-ETIMEDOUT);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);
    CHECK(tbs_op_receive_begin(&s,1002)==-EALREADY);
    for(error=-5;error<=3;error++) {
        if(error==3) continue;
        s=admitted();writes=0;
        CHECK(control(&s,1001,0x80,wr,3,0,error)<0 && writes==1);
        for(i=0;i<20;i++) CHECK(control(&s,1002,0x80,wr,3,0,3)<0 && writes==1);
    }
    s=admitted();writes=0;CHECK(control(&s,1001,0x90,setup,3,0,3)==0);
    CHECK(control(&s,1002,0x80,wr,3,0,3)<0 && writes==1);
    s=admitted();writes=0;CHECK(control(&s,1001,0x90,setup,3,0,3)==0);
    CHECK(control(&s,1002,0x91,in,1,1,1)==0 && writes==2);
    CHECK(control(&s,1003,0x8a,stream,2,0,2)==0);
    for(i=0;i<256;i++) {
        if(i==0x80 || i==0x90 || i==0x8a) continue;
        s=admitted();writes=0;
        CHECK(control(&s,1001,i,wr,3,0,3)<0 && writes==0);
    }
    stream[1]=1;s=admitted();writes=0;
    CHECK(control(&s,1001,0x8a,stream,2,0,2)<0 && writes==0);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);
    for(i=0;i<8;i++) CHECK(tbs_op_reserve(&s)==0);
    CHECK(tbs_op_reserve(&s)<0 && s.outstanding==8);
    for(i=0;i<8;i++) CHECK(tbs_op_complete(&s,-ENOENT,0)<0);
    CHECK(s.outstanding==0 && s.reaped==8);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);
    for(i=0;i<512;i++) { CHECK(tbs_op_reserve(&s)==0);CHECK(tbs_op_complete(&s,0,65536)==0); }
    CHECK(s.requested==TBS_OP_BYTE_CAP && s.actual==TBS_OP_BYTE_CAP);
    CHECK(tbs_op_reserve(&s)<0 && !s.outstanding);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);CHECK(tbs_op_reserve(&s)==0);
    CHECK(tbs_op_complete(&s,0,0)==-ENODATA);CHECK(tbs_op_reserve(&s)<0);
    s=admitted();CHECK(tbs_op_receive_begin(&s,1001)==0);CHECK(tbs_op_reserve(&s)==0);
    CHECK(tbs_op_complete(&s,-EPROTO,0)==-EPROTO);CHECK(tbs_op_reserve(&s)<0);
    printf("PASS %u operational policy cases; fake I/O only; physical profile data supplied separately.\n",cases);
    return 0;
}
