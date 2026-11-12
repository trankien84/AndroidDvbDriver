// SPDX-License-Identifier: GPL-2.0-or-later
#include "../debug/cpp/native_fx2_plan.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    unsigned char image[8192], reset;
    for (int i = 0; i < 8192; i++) image[i] = (unsigned char)(i * 17 + 3);
    struct tbs_fx2_step step;
    int total = 0;
    for (int ordinal = 1; ordinal <= 132; ordinal++) {
        assert(tbs_fx2_step(ordinal, image, sizeof(image), &reset, &step) == 0);
        assert(step.type == 0x40 && step.request == 0xa0 && step.index == 0 && step.timeout == 2000);
        if (ordinal <= 2 || ordinal >= 131) {
            assert(step.value == (ordinal == 1 || ordinal == 131 ? 0x7f92 : 0xe600));
            assert(step.length == 1 && step.payload[0] == (ordinal <= 2 ? 1 : 0));
        } else {
            assert(step.value == (ordinal - 3) * 64 && step.length == 64);
            assert(memcmp(step.payload, image + (ordinal - 3) * 64, 64) == 0);
        }
        total += step.length;
    }
    assert(total == 8196);
    assert(tbs_fx2_step(0, image, sizeof(image), &reset, &step) == -1);
    assert(tbs_fx2_step(133, image, sizeof(image), &reset, &step) == -1);
    assert(tbs_fx2_step(1, image, 8191, &reset, &step) == -1);
    puts("PASS: native FX2 shared setup/payload plan 132/132; 8196 bytes; no USB syscall");
    return 0;
}
