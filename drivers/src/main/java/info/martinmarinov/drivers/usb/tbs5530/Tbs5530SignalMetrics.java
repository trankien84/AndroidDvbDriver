// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.List;

/** Pure CXD6802 decoding. Null means UNKNOWN/unavailable, never a fabricated zero. */
public final class Tbs5530SignalMetrics {
    private static final int[][] BCH_BITS = {
            {7200, 9720, 10800, 11880, 12600, 13320, 5400, 6480},
            {32400, 38880, 43200, 48600, 51840, 54000, 21600, 25920}
    };
    private static final String[] CODE_RATES = {"1/2", "3/5", "2/3", "3/4", "4/5", "5/6", "1/3", "2/5"};
    private static final String[] MODULATIONS = {"QPSK", "16-QAM", "64-QAM", "256-QAM"};
    public enum Lock { LOCKED, LOST, UNKNOWN }
    public final Lock lock;
    public final Integer syncState;
    public final Boolean tsLock, unlockDetected;
    public final Integer snrRaw, officialCnrMilliDb;
    public final Double snrDbCurve, officialCnrDb;
    public final Double ber, fer = null, per = null, rssiDbm = null;
    public final Integer ifAgc = null;
    public final String fft = null, guardInterval = null, modulation, codeRate, fec;
    public final Integer plpId, timeInterleaving;
    public final String unavailableReason;

    private Tbs5530SignalMetrics(List<Tbs5530SignalMonitor.RegisterRead> reads) {
        Integer status = octet(reads, 0x20, 0x10);
        syncState = status == null ? null : status & 7;
        tsLock = status == null ? null : (status & 0x20) != 0;
        unlockDetected = status == null ? null : (status & 0x10) != 0;
        // TS lock is independent telemetry, not a demodulator-lock prerequisite.
        lock = status == null || syncState == 7 ? Lock.UNKNOWN
                : syncState == 6 && !unlockDetected ? Lock.LOCKED : Lock.LOST;
        byte[] snr = bytes(reads, 0x20, 0x28, 2);
        snrRaw = snr == null ? null : ((snr[0] & 255) << 8) | (snr[1] & 255);
        snrDbCurve = snrRaw == null ? null : snrDb(snrRaw);
        officialCnrMilliDb = snrRaw == null ? null : officialCnrMilliDb(snrRaw);
        officialCnrDb = snrDbCurve;

        Integer l1 = octet(reads, 0x22, 0x12), cr = octet(reads, 0x22, 0x5b);
        Integer mod = octet(reads, 0x22, 0x5c), fecType = octet(reads, 0x22, 0x5e);
        boolean validL1 = lock == Lock.LOCKED && l1 != null && (l1 & 1) != 0;
        codeRate = validL1 && cr != null ? CODE_RATES[cr & 7] : null;
        modulation = validL1 && mod != null && (mod & 7) < MODULATIONS.length ? MODULATIONS[mod & 7] : null;
        fec = validL1 && fecType != null && (fecType & 3) < 2
                ? ((fecType & 3) == 0 ? "16K" : "64K") : null;
        plpId = validL1 ? octet(reads, 0x22, 0x54) : null;
        timeInterleaving = validL1 ? octet(reads, 0x22, 0x62) : null;
        Integer period = octet(reads, 0x20, 0x72);
        ber = validL1 && cr != null && fecType != null && period != null
                ? preBchBer(bytes(reads, 0x24, 0x40, 4), period, fecType, cr) : null;
        unavailableReason = "UNKNOWN fields require valid same-sample reads; BER is pre-BCH, not pre-LDPC; "
                + "FFT/GI/FER/PER unavailable; RSSI requires a separate OFF-only tuner sample";
    }

    /** Compatibility name: B5 reports the unshifted real-log curve, rounded to milli-dB. */
    public static Integer officialCnrMilliDb(int raw) {
        Double db = snrDb(raw);
        return db == null ? null : (int) Math.round(db * 1000.0);
    }

    /** Mainline cxd2880 dvbt2_calc_snr: clamp 10876, denominator 12600-r, +32 dB. */
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

    /** Pre-BCH ratio; reserved FEC types, missing validity and impossible counts stay UNKNOWN. */
    public static Double preBchBer(byte[] counter24_40, int period20_72, int fec22_5e, int cr22_5b) {
        if (counter24_40 == null || counter24_40.length != 4 || (counter24_40[0] & 1) == 0
                || !registerByte(period20_72) || !registerByte(fec22_5e) || !registerByte(cr22_5b)
                || (fec22_5e & 3) > 1) return null;
        long errors = ((long) (counter24_40[1] & 63) << 16)
                | ((long) (counter24_40[2] & 255) << 8) | (counter24_40[3] & 255);
        long bits = (1L << (period20_72 & 15)) * BCH_BITS[fec22_5e & 3][cr22_5b & 7];
        return errors > bits ? null : (double) errors / bits;
    }

    private static Integer octet(List<Tbs5530SignalMonitor.RegisterRead> reads, int bank, int register) {
        byte[] b = bytes(reads, bank, register, 1);
        return b == null ? null : b[0] & 255;
    }

    private static byte[] bytes(List<Tbs5530SignalMonitor.RegisterRead> reads, int bank, int register, int length) {
        byte[] found = null;
        for (Tbs5530SignalMonitor.RegisterRead read : reads) {
            byte[] b = read.getBytes();
            int offset = register - read.register;
            if (read.bank != bank || offset < 0 || offset + length > b.length) continue;
            byte[] candidate = java.util.Arrays.copyOfRange(b, offset, offset + length);
            if (found != null && !java.util.Arrays.equals(found, candidate)) return null;
            found = candidate;
        }
        return found;
    }

    public static Tbs5530SignalMetrics decode(List<Tbs5530SignalMonitor.RegisterRead> reads) {
        return new Tbs5530SignalMetrics(java.util.Objects.requireNonNull(reads));
    }
}
