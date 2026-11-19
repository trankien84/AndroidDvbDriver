// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef TBS_NATIVE_FX2_RELOAD_CORE_H
#define TBS_NATIVE_FX2_RELOAD_CORE_H
#include "native_fx2_reload_plan.h"
struct tbs_reload_result { int completed, ordinal, result, error; };
struct tbs_reload_ops {
    void *opaque;
    int (*require)(void *opaque);
    int (*write)(void *opaque, const struct tbs_reload_step *step, int *error);
};
/* Fixed plan, one guard check per transfer; no retry/recovery after any refusal. */
static int tbs_reload_run(const unsigned char *image, int length, struct tbs_reload_ops ops,
                           struct tbs_reload_result *result) {
    if (!image || length != 8192 || !ops.require || !ops.write || !result) return -1;
    *result = (struct tbs_reload_result){0, 0, 0, 0};
    for (int ordinal = 1; ordinal <= 132; ++ordinal) {
        if (ops.require(ops.opaque) != 0) return -1;
        unsigned char reset;
        struct tbs_reload_step step;
        if (tbs_reload_step(ordinal, image, length, &reset, &step) != 0) return -1;
        result->ordinal = ordinal;
        result->result = ops.write(ops.opaque, &step, &result->error);
        if (result->result != step.length || result->error) return -1;
        result->completed = ordinal;
    }
    return 0;
}
#endif
