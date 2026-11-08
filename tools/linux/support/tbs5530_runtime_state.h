/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_RUNTIME_STATE_H
#define TBS5530_RUNTIME_STATE_H
#include <linux/spinlock.h>
#include <linux/workqueue.h>
#include "tbs5530_operational_guard.h"
struct tbs5530_runtime {
    spinlock_t lock;
    struct mutex control_lock;
    struct mutex receive_lock;
    struct work_struct drain_work;
    struct delayed_work monitor_work;
    struct tbs_op_state policy;
    bool shutdown, drained, quarantined;
    bool in_flight[TBS_OP_URB_COUNT];
    unsigned int controls, control_requested, control_actual;
    unsigned int first_failed_control, suppressed_controls;
    int first_failed_result;
    int last_control_result, last_status;
    unsigned int last_requested, last_actual;
};
#endif
