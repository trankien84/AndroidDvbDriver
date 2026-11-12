// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef TBS5530_NATIVE_FX2_PLAN_H
#define TBS5530_NATIVE_FX2_PLAN_H

struct tbs_fx2_step { int type, request, value, index, length, timeout; const unsigned char *payload; };
static int tbs_fx2_step(int ordinal, const unsigned char *image, int image_length, unsigned char *reset,
                        struct tbs_fx2_step *step) {
    if (ordinal < 1 || ordinal > 132 || !image || image_length != 8192 || !reset || !step) return -1;
    *step = (struct tbs_fx2_step){0x40, 0xa0, 0, 0, 64, 2000, image};
    if (ordinal <= 2 || ordinal >= 131) {
        step->value = ordinal == 1 || ordinal == 131 ? 0x7f92 : 0xe600;
        step->length = 1; *reset = ordinal <= 2 ? 1 : 0; step->payload = reset;
    } else { step->value = (ordinal - 3) * 64; step->payload = image + step->value; }
    return 0;
}
#endif
