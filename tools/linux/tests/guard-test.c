/* SPDX-License-Identifier: GPL-2.0-only */
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "tbs5530_diag_guard.h"
static unsigned char image[8192];
static int calls, fail_at = -1, fail_result, cases;
static int fake_write(void *context, unsigned short address,
                      const unsigned char *data, unsigned int length)
{
    int step = calls++;
    (void)context;
    if (step < 2 || step >= 130) {
        assert(length == 1);
        assert(address == ((step == 0 || step == 130) ? 0x7f92 : 0xe600));
        assert(*data == (step < 2 ? 1 : 0));
    } else {
        assert(length == 64 && address == (step - 2) * 64);
        assert(data == image + address);
    }
    return step == fail_at ? fail_result : (int)length;
}
static void rejected(unsigned int vid, unsigned int pid, int enable,
                     int bootstrap, unsigned long size, const unsigned char *hash)
{
    calls = 0;
    assert(tbs_diag_fw_run(vid,pid,enable,bootstrap,image,size,hash,0,fake_write) < 0);
    assert(calls == 0);
    cases++;
}
int main(void)
{
    unsigned char bad_hash[32];
    memcpy(bad_hash,tbs_diag_hash,32); bad_hash[0] ^= 1;
    rejected(0,0x5530,1,1,8192,tbs_diag_hash);
    rejected(0x734c,0,1,1,8192,tbs_diag_hash);
    rejected(0x734c,0x5530,0,1,8192,tbs_diag_hash);
    rejected(0x734c,0x5530,1,0,8192,tbs_diag_hash);
    rejected(0x734c,0x5530,1,1,8191,tbs_diag_hash);
    rejected(0x734c,0x5530,1,1,8193,tbs_diag_hash);
    rejected(0x734c,0x5530,1,1,8192,bad_hash);
    rejected(0x734c,0x5530,1,1,8192,0);
    calls=0;
    assert(tbs_diag_fw_run(0x734c,0x5530,1,1,0,8192,tbs_diag_hash,0,fake_write)==-EINVAL);
    assert(calls==0); cases++;
    assert(tbs_diag_fw_run(0x734c,0x5530,1,1,image,8192,tbs_diag_hash,0,0)==-EINVAL);
    assert(calls==0); cases++;
    calls=0;
    assert(tbs_diag_fw_run(0x734c,0x5530,1,1,image,8192,tbs_diag_hash,0,fake_write)==0);
    assert(calls==132); cases++;
    for (fail_at=0; fail_at<132; fail_at++) {
        calls=0;
        assert(tbs_diag_fw_run(0x734c,0x5530,1,1,image,8192,tbs_diag_hash,0,fake_write)==-EIO);
        assert(calls==fail_at+1); cases++;
    }
    fail_result=-EIO;
    for (fail_at=0; fail_at<132; fail_at++) {
        calls=0;
        assert(tbs_diag_fw_run(0x734c,0x5530,1,1,image,8192,tbs_diag_hash,0,fake_write)==-EIO);
        assert(calls==fail_at+1); cases++;
    }
    assert(tbs_diag_profile(16,16,594000000,8000000,0)==0); cases++;
    assert(tbs_diag_profile(16,16,594000000,8000000,~0U)<0); cases++;
    assert(tbs_diag_profile(5,16,594000000,8000000,0)<0); cases++;
    assert(tbs_diag_profile(16,16,594000001,8000000,0)<0); cases++;
    assert(tbs_diag_profile(16,16,594000000,7000000,0)<0); cases++;
    assert(tbs_diag_profile(16,16,594000000,8000000,1)<0); cases++;
    printf("PASS %d policy cases; fake writes only; SHA digest input is a seam, not firmware acceptance.\n",cases);
    return 0;
}
