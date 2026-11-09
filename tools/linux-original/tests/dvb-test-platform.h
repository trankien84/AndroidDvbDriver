// SPDX-License-Identifier: GPL-2.0-only
#ifndef ORIGINAL_DVB_TEST_PLATFORM_H
#define ORIGINAL_DVB_TEST_PLATFORM_H
/* Windows fake-only model; not a Linux ABI header or a device backend. */
#define O_CLOEXEC 0x100000
#define O_NOFOLLOW 0x200000
#define O_NONBLOCK 0x400000
#define POLLIN 0x001
#define POLLERR 0x008
#define POLLHUP 0x010
#define POLLNVAL 0x020
#define FE_GET_INFO 1UL
#define FE_SET_PROPERTY 2UL
#define FE_READ_STATUS 3UL
#define DMX_SET_PES_FILTER 4UL
#define DMX_STOP 5UL
#define DTV_CLEAR 2U
#define DTV_DELIVERY_SYSTEM 17U
#define DTV_FREQUENCY 3U
#define DTV_BANDWIDTH_HZ 5U
#define DTV_STREAM_ID 42U
#define DTV_TUNE 1U
#define SYS_DVBT2 16U
#define FE_HAS_LOCK 0x10U
#define DMX_IN_FRONTEND 0
#define DMX_OUT_TS_TAP 2
#define DMX_PES_OTHER 20
#define DMX_IMMEDIATE_START 4U
typedef unsigned int fe_status_t;
struct dvb_frontend_info { char name[128]; };
struct dtv_property { unsigned int cmd; union { unsigned int data; } u; int result; };
struct dtv_properties { unsigned int num; struct dtv_property *props; };
struct dmx_pes_filter_params { unsigned short pid; int input, output, pes_type; unsigned int flags; };
#endif
