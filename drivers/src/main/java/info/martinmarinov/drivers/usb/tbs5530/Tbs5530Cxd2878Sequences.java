// SPDX-License-Identifier: GPL-2.0-or-later
// Selectively adapted from Sony cxd2878.c, Copyright (c) 2021 Davin Zhang / TurboSight.
package info.martinmarinov.drivers.usb.tbs5530;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Kind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Builder;

/**
 * Fixed TBS5530 24 MHz / parallel TS / ASCOT3I E-family subset only.
 * Official cxd2878.c is authoritative. Community cxd2878.c:4765-4900 removes 9C/10
 * and changes stabilization delays (ATSC3 work); those differences are NOT imported for T2.
 * Go tuner_ascot3.go:137-213 omits the E-family ID and its VCO/calibration branch;
 * this port retains official ascot3_init:812-958 instead. Go same-system retune skips
 * PLP writes; the official 23/AF/AD writes below deliberately run on EVERY tune.
 * Auto PLP, Freia, other ASCOT3 variants, LED GPIO, RSSI/CNR/BER, other standards,
 * TS capture, EEPROM, satellite, LNB and RF-board switching are not implemented here.
 */
final class Tbs5530Cxd2878Sequences {
    static final int T = 0x64;
    static final int X = 0x66; // cxd2878_attach:4010-4014: SLV-X = SLV-T + 2 (7-bit).
    static final int U = 0x60;
    static final String OFFICIAL = "tbsdtv/linux_media@2ce787de9f6e81b4294692a45de15596ae2e1322:drivers/media/dvb-frontends/";

    private Tbs5530Cxd2878Sequences() {}

    static void initialize(Builder b) {
        b.source("cxd2878.c:cxd2878_init:3182-3223; cxd2878.h:SONY_DEMOD_XTAL_24000KHz=1");
        b.bank(X, 0);
        b.write(X, 0x02, 0);
        b.delay(4);
        b.bank(X, 0);
        b.write(X, 0x1d, 0);
        b.write(X, 0x14, 1);
        b.delay(2);
        b.write(X, 0x50, 0);
        b.delay(2);
        b.write(X, 0x10, 0);
        b.delay(21);
        b.source("cxd2878.c:cxd2878_attach:4066-4070 (post-reset identity revalidation)");
        b.bank(X, 0);
        int high = b.read(X, 0xfb, 1);
        int low = b.read(X, 0xfd, 1);
        b.check(Kind.CHECK_ID, high, low);
        b.source("cxd2878.c:cxd2878_init:3208-3223");
        b.bank(T, 0x9c);
        b.write(T, 0x10, 0, 0, 0, 0);
        b.bankMasked(X, 0, 0x1a, 1, 255);
        b.delay(2);
        repeater(b, true);

        b.source("cxd2878.c:ascot3_init:812-890 (24MHz, ASCOT3I only)");
        b.check(Kind.CHECK_TUNER, b.read(U, 0x7f, 1), -1);
        b.write(U, 0x99, 0x7a, 1);
        b.write(U, 0x81, 0x18, 0x84, 0xa8, 0x82, 0, 0, 0xc4, 0x40, 0x10,
                0, 0x45, 0x75, 7, 0x1c, 0x3f, 2, 0x10, 0x20, 0x0a, 0);
        b.write(U, 0x9b, 0);
        b.delay(10);
        b.check(Kind.CHECK_ZERO, b.read(U, 0x1a, 1), -1);
        b.source("cxd2878.c:ascot3_init:914-958 (2871A VCO and calibration)");
        b.write(U, 0x17, 0x2a, 0x0e);
        b.delay(1);
        b.derived(U, 0x95, b.read(U, 0x19, 1), 0x0f, 0, 0);
        b.write(U, 0xb0, 0);
        b.write(U, 0x30, 0xe0);
        b.write(U, 0xb1, 0x1e);
        b.write(U, 0xb3, 2);
        b.delay(1);
        b.read(U, 0xb4, 1);
        b.write(U, 0xb3, 0);
        b.delay(1);
        b.write(U, 0xb1, 0);
        b.write(U, 0x30, 0xe1);
        b.write(U, 0xb0, 1);
        b.masked(U, 0x67, 0, 0xfe);
        b.masked(U, 0x74, 2, 3);
        b.write(U, 0x5e, 0x15, 0, 0);
        b.write(U, 0x88, 0);
        b.write(U, 0x87, 0xc0);
        b.write(U, 0x80, 1);
        repeater(b, false);

        b.source("cxd2878.c:cxd2878_init:3236-3254; tbs5530.c:tbs5530_cfg (parallel TS)");
        b.bankMasked(T, 0, 0xc4, 0, 0x80);
        b.bankMasked(T, 2, 0xe4, 0, 1);
        b.bankMasked(T, 0, 0xc6, 1, 0x1f);
        b.bankMasked(T, 0x60, 0x52, 1, 0x1f);
        b.bankMasked(T, 0, 0x95, 1, 3);
        b.bankMasked(T, 0, 0x95, 0x14, 0x3c);
        b.bankMasked(T, 0, 0x96, 0x54, 255);
        b.bankMasked(T, 0, 0x97, 0x54, 255);
    }

    private static void repeater(Builder b, boolean enable) {
        b.source("cxd2878.c:cxd2878_i2c_repeater:436-444");
        b.bank(X, 0);
        b.write(X, 8, enable ? 1 : 0);
        b.delay(20);
    }

    static void tune(Builder b, Tbs5530DvbT2TuneParameters p, boolean active) {
        b.source("cxd2878.c:cxd2878_set_dvbt2:2466-2514 (manual PLP on each tune)");
        b.bank(T, 0x23);
        b.write(T, 0xaf, p.plpId);
        b.write(T, 0xad, 1);
        b.bank(T, 0x2e);
        b.write(T, 0x10, 0);
        b.bank(T, 0x2b);
        b.write(T, 0x9d, 0x2e);
        if (active) {
            streamOutput(b, false);
            bandwidth(b, p.bandwidthHz);
        } else {
            activate(b, p.bandwidthHz);
        }
        repeater(b, true);
        tunerTune(b, p);
        repeater(b, false);
        b.source("cxd2878.c:cxd2878_tuneEnd:1957-1963");
        b.bank(T, 0);
        b.write(T, 0xfe, 1);
        streamOutput(b, true);
        b.source("cxd2878.c:cxd2878_set_frontend:3745-3767");
        b.delay(20);
    }

    private static void streamOutput(Builder b, boolean enable) {
        b.source("cxd2878.c:cxd2878_setstreamoutput:1555-1581 (TS only)");
        b.bank(T, 0);
        b.check(Kind.CHECK_TS, b.read(T, 0xa9, 1), -1);
        b.bank(T, 0);
        b.write(T, 0xc3, enable ? 0 : 1);
    }

    private static void activate(Builder b, int bandwidth) {
        b.source("cxd2878.c:cxd2878_setTSClkModeAndFreq:1634-1722 (board parallel TS)");
        b.bank(T, 0);
        b.check(Kind.CHECK_PARALLEL, b.read(T, 0xc4, 1), -1);
        b.masked(T, 0xd3, 0, 1);
        b.masked(T, 0xde, 0, 1);
        b.masked(T, 0xda, 0, 1);
        b.write(T, 0xd9, 8);
        b.masked(T, 0x32, 0, 1);
        b.masked(T, 0x33, 1, 3);
        b.masked(T, 0x32, 1, 1);
        b.bankMasked(T, 0x10, 0x66, 1, 1);
        b.bankMasked(T, 0x40, 0x66, 1, 1);
        b.source("cxd2878.c:SLtoAT2:2314-2433");
        b.bank(X, 0);
        b.write(X, 0x17, 2);
        b.bank(T, 0);
        b.write(T, 0xa9, 0);
        b.write(T, 0x2c, 1);
        b.write(T, 0x4b, 0x74);
        b.write(T, 0x49, 0);
        b.write(X, 0x18, 0);
        b.bank(T, 0x11);
        b.write(T, 0x6a, 0x50);
        b.bank(T, 0);
        b.write(T, 0xce, 1, 1);
        b.bank(T, 0x13);
        b.write(T, 0x83, 0x10);
        b.write(T, 0x86, 0x34);
        b.write(T, 0x9f, 0xd8);
        b.bank(T, 0x23);
        b.write(T, 0xdb, 1);
        b.bank(T, 0x11);
        b.write(T, 0x33, 0, 3, 0x3b);
        bandwidth(b, bandwidth);
        b.source("cxd2878.c:SLtoAT2:2440-2449; cxd2878_setTSDataPinHiZ:1730-1831 (parallel TS only)");
        b.bank(T, 0);
        b.masked(T, 0x80, 8, 0x1f);
        b.check(Kind.CHECK_TS, b.read(T, 0xa9, 1), -1);
        b.check(Kind.CHECK_PARALLEL, b.read(T, 0xc4, 1), -1);
        b.bank(T, 0);
        b.write(T, 0x81, 0);
    }

    private static void bandwidth(Builder b, int hz) {
        b.source("cxd2878.c:SLtoAT2_BandSetting:2185-2293; cxd2878_attach:4024-4028; cxd2878.h:SONY_DEMOD_MAKE_IFFREQ_CONFIG");
        int[] nominal, coefficients;
        int ifHz, width, filter;
        switch (hz) {
            case 8_000_000:
                nominal = new int[]{0x15, 0, 0, 0, 0};
                coefficients = new int[]{0x2f, 0xba, 0x28, 0x9b, 0x28, 0x9d, 0x28, 0xa1, 0x29, 0xa5, 0x2a, 0xac, 0x29, 0xb5};
                ifHz = 4_800_000; width = 0; filter = 0; break;
            case 7_000_000:
                nominal = new int[]{0x18, 0, 0, 0, 0};
                coefficients = new int[]{0x30, 0xb1, 0x29, 0x9a, 0x28, 0x9c, 0x28, 0xa0, 0x29, 0xa2, 0x2b, 0xa6, 0x2b, 0xad};
                ifHz = 4_200_000; width = 2; filter = 0; break;
            case 6_000_000: case 5_000_000:
                nominal = hz == 6_000_000 ? new int[]{0x1c, 0, 0, 0, 0} : new int[]{0x21, 0x99, 0x99, 0x99, 0x99};
                coefficients = new int[]{0x31, 0xa8, 0x29, 0x9b, 0x27, 0x9c, 0x28, 0x9e, 0x29, 0xa4, 0x29, 0xa2, 0x29, 0xa8};
                ifHz = 3_600_000; width = hz == 6_000_000 ? 4 : 6; filter = 0; break;
            case 1_700_000:
                nominal = new int[]{0x1a, 3, 0xe8, 0x8c, 0xb3};
                coefficients = null;
                ifHz = 3_500_000; width = 3; filter = 3; break;
            default: throw new IllegalArgumentException("Unreviewed bandwidth");
        }
        b.bank(T, 0x20);
        b.write(T, 0x9f, nominal);
        b.bank(T, 0x27);
        b.write(T, 0x7a, filter);
        b.bank(T, 0x10);
        if (coefficients != null) b.write(T, 0xa6, coefficients);
        b.write(T, 0xa5, coefficients == null ? 0 : 1);
        int ifConfig = (int) (((long) ifHz * 16_777_216L + 24_000_000L) / 48_000_000L);
        b.write(T, 0xb6, (ifConfig >>> 16) & 255, (ifConfig >>> 8) & 255, ifConfig & 255);
        b.write(T, 0xd7, width);
    }

    private static void tunerTune(Builder b, Tbs5530DvbT2TuneParameters p) {
        b.source("cxd2878.c:ascot3_tune:596-613,656-804; g_param_table_ascot3i:79-83 (24MHz DVB-T2)");
        b.masked(U, 0x74, 2, 3);
        b.write(U, 0x87, 0xc4, 0x40);
        b.write(U, 0x91, 0x10, 0x20);
        b.write(U, 0x9c, 0, 0);
        b.write(U, 0x5e, 0xee, 2, 0x1e, 0x67, 3, 0xb4, 0x78, 8, 0x30);
        b.masked(U, 0x67, 0, 6);
        int bw, ifOffset, bwOffset;
        switch (p.bandwidthHz) {
            case 1_700_000: bw = 3; ifOffset = -10; bwOffset = -10; break;
            case 5_000_000: case 6_000_000: bw = 0; ifOffset = -8; bwOffset = -3; break;
            case 7_000_000: bw = 1; ifOffset = -6; bwOffset = -5; break;
            case 8_000_000: bw = 2; ifOffset = -4; bwOffset = -6; break;
            default: throw new IllegalArgumentException("Unreviewed bandwidth");
        }
        int khz = (int) (p.frequencyHz / 1000L);
        b.write(U, 0x68, 0, 0x88, 0, 0x0b, 0x22, bw, ifOffset & 0x1f, bwOffset & 0x1f,
                khz & 255, (khz >>> 8) & 255, (khz >>> 16) & 15, 255, 0x11, 0x99, 0, 0x24, 0x87);
        b.delay(50);
        b.write(U, 0x88, 0);
        b.write(U, 0x87, 0xc0);
        b.delay(10);
    }
}
