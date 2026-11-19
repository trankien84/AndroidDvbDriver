// SPDX-License-Identifier: GPL-2.0-or-later
#include "native_fx2_reload_core.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>

struct model { int checks, writes, refuse_at, short_at, error_at; };
static int require(void *opaque) {
    struct model *f = opaque;
    return ++f->checks == f->refuse_at ? -1 : 0;
}
static int write_step(void *opaque, const struct tbs_reload_step *step, int *error) {
    struct model *f = opaque; ++f->writes;
    *error = f->writes == f->error_at ? EPIPE : 0;
    return *error ? -1 : step->length - (f->writes == f->short_at);
}
static int run(struct model *f, struct tbs_reload_result *result) {
    unsigned char image[8192] = {0};
    struct tbs_reload_ops ops = {f, require, write_step};
    return tbs_reload_run(image, sizeof(image), ops, result);
}
int main(void) {
    struct tbs_reload_result result;
    struct model success = {0};
    assert(run(&success, &result) == 0);
    assert(success.checks == 132 && success.writes == 132 && result.completed == 132);
    for (int at = 1; at <= 132; ++at) {
        struct model refused = {.refuse_at = at};
        assert(run(&refused, &result) < 0);
        assert(refused.checks == at && refused.writes == at - 1 && result.completed == at - 1);
        struct model short_write = {.short_at = at};
        assert(run(&short_write, &result) < 0);
        assert(short_write.checks == at && short_write.writes == at && result.completed == at - 1);
        assert(result.ordinal == at && result.error == 0);
        struct model error = {.error_at = at};
        assert(run(&error, &result) < 0);
        assert(error.checks == at && error.writes == at && result.completed == at - 1);
        assert(result.ordinal == at && result.error == EPIPE);
    }
    puts("PASS FX2 exact132 + every-step guard/short/error: 397 scenarios, no retries");
    return 0;
}
