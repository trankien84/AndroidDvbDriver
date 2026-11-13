// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.List;

/** Pure CXD6802 decoding. Null means UNKNOWN/unavailable, never a fabricated zero. */
public final class Tbs5530SignalMetrics {
    private static final int[] LOG2_FRACTIONS = {0,4,9,13,17,21,25,29,32,36,39,43,46,49,52,55,
            58,61,64,67,70,73,75,78,81,83,86,88,91,93,95,98};
    public enum Lock { LOCKED, LOST, UNKNOWN }
    public final Lock lock;
    public final Integer syncState;
    public final Boolean tsLock, unlockDetected;
    public final Integer snrRaw, officialCnrMilliDb;
    public final Double snrDbCurve, officialCnrDb;
    public final Double ber = null, fer = null, per = null, rssiDbm = null;
    public final Integer ifAgc = null;
    public final String fft = null, guardInterval = null, modulation = null, codeRate = null;
    public final Integer plpId = null, timeInterleaving = null;
    public final String unavailableReason = "UNKNOWN: no proven same-chip mapping/denominator for Windows bank22 signaling and bank24:40";

    private Tbs5530SignalMetrics(Integer status, Integer snr) {
        syncState = status == null ? null : status & 7;
        tsLock = status == null ? null : (status & 0x20) != 0;
        unlockDetected = status == null ? null : (status & 0x10) != 0;
        // Official cxd2878_read_status at 2ce787de9f6e, lines 3313-3323.
        // Sync state 7 is reserved: do not turn an invalid measurement into a lock-loss stop.
        lock = status == null || syncState == 7 ? Lock.UNKNOWN
                : syncState >= 6 && tsLock && !unlockDetected ? Lock.LOCKED : Lock.LOST;
        snrRaw = snr;
        snrDbCurve = lock == Lock.LOCKED && snr != null ? snrDb(snr) : null;
        officialCnrMilliDb = snrDbCurve == null ? null : officialCnrMilliDb(snr);
        officialCnrDb = officialCnrMilliDb == null ? null : officialCnrMilliDb / 1000.0;
    }

    /** Exact integer-log curve and -1500 mdB offset from official cxd2878.c:134-186,3480-3485,3591. */
    public static Integer officialCnrMilliDb(int raw) {
        if (raw < 0 || raw > 65535) throw new IllegalArgumentException("SNR word outside uint16");
        if (raw == 0) return null;
        int r = Math.min(raw, 10876);
        return 100 * (sonyLog10(r) - sonyLog10(12600 - r)) + 32000 - 1500;
    }

    // Sony/TurboSight GPL-2.0-or-later integer logarithm, constants from cxd2878_priv.h:12-14.
    private static int sonyLog10(int value) {
        int count = 31 - Integer.numberOfLeadingZeros(value);
        int log2 = count * 100;
        if (count > 0) log2 += LOG2_FRACTIONS[(count <= 5 ? value << (5 - count) : value >> (count - 5)) & 31];
        return (100 * log2 + 166) / 332;
    }

    /** Real-log counterpart of the official integer-log curve, not bit-exact vendor output. */
    public static Double snrDb(int raw) {
        if (raw < 0 || raw > 65535) throw new IllegalArgumentException("SNR word outside uint16");
        if (raw == 0) return null;
        int r = Math.min(raw, 10876);
        return 10.0 * Math.log10((double) r / (12600 - r)) + 32.0;
    }

    /**
     * Offline pre-LDPC BER from Go coax@6aaa1899991c875ba0482fa9098adc6c83b01fd8,
     * sony/errors.go:DVBT2ErrorCount:130-163. No register access or live-field assignment.
     * Null means missing bytes, an invalid counter/selector, or errors beyond measured bits.
     */
    public static Double preLdpcBer(byte[] counter20_39, int period20_6f, int fec22_5e) {
        if (counter20_39 == null || counter20_39.length != 4
                || !registerByte(period20_6f) || !registerByte(fec22_5e)
                || (counter20_39[0] & 0x10) == 0) return null;
        long errors = ((long) (counter20_39[0] & 15) << 24)
                | ((long) (counter20_39[1] & 255) << 16)
                | ((long) (counter20_39[2] & 255) << 8) | (counter20_39[3] & 255);
        long frameBits = (fec22_5e & 3) == 0 ? 16200L : 64800L;
        long totalBits = (1L << (period20_6f & 15)) * frameBits;
        return errors > totalBits ? null : (double) errors / totalBits;
    }

    /**
     * Offline official PER arithmetic, rounded to 1e-6 then returned as a ratio, not percent.
     * TBS@2ce787de9f6e81b4294692a45de15596ae2e1322 cxd2878.c:3622-3639.
     * That source does not validate FA[0]; this helper deliberately does not invent its meaning.
     * A numeric result is NOT a counter-validity certificate. Live decode stays unavailable.
     */
    public static Double officialPer(byte[] counter24_fa, int period24_dc) {
        if (counter24_fa == null || counter24_fa.length != 3 || !registerByte(period24_dc)) return null;
        long errors = ((counter24_fa[1] & 255) << 8) | (counter24_fa[2] & 255);
        long period = 1L << (period24_dc & 15);
        if (errors > period) return null;
        long millionths = (errors * 1_000_000L + period / 2) / period;
        return millionths / 1_000_000.0;
    }

    private static boolean registerByte(int value) { return value >= 0 && value <= 255; }

    public static Tbs5530SignalMetrics decode(List<Tbs5530SignalMonitor.RegisterRead> reads) {
        Integer status = null, snr = null;
        for (Tbs5530SignalMonitor.RegisterRead read : reads) {
            byte[] b = read.getBytes();
            if (read.bank == 0x20 && read.register == 0x10 && b.length == 1) status = b[0] & 255;
            if (read.bank == 0x20 && read.register == 0x28 && b.length == 2)
                snr = ((b[0] & 255) << 8) | (b[1] & 255);
        }
        return new Tbs5530SignalMetrics(status, snr);
    }
}
