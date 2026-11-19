// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class Tbs5530SignalMetricsTest {
    private Tbs5530SignalMonitor.RegisterRead raw(int bank, int reg, int... data) {
        byte[] b = new byte[data.length]; for (int i = 0; i < data.length; i++) b[i] = (byte) data[i];
        return new Tbs5530SignalMonitor.RegisterRead(bank, reg, b, 0, 1);
    }
    private byte[] ldpcCounter(long errors) {
        return new byte[]{(byte) (0x10 | ((errors >>> 24) & 15)), (byte) (errors >>> 16),
                (byte) (errors >>> 8), (byte) errors};
    }

    @Test public void preLdpcFormulaZeroAndExactShortNormalFrameLimits() {
        assertEquals(0.0, Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(0), 0, 0), 0.0);
        assertEquals(1.0, Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(16200), 0, 0), 0.0);
        assertNull(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(16201), 0, 0));
        assertEquals(1.0, Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(64800), 0, 1), 0.0);
        assertNull(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(64801), 0, 1));
        assertEquals(1.0 / 32400, Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(1), 1, 0), 0.0);
    }

    @Test public void preLdpcFormulaMasksSelectorsAndUsesUnsignedCounterAtMaximumExponent() {
        double expected = 0x0fffffffL / (32768.0 * 64800);
        assertEquals(expected, Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(0x0fffffffL), 255, 255), 0.0);
        assertEquals(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(12345), 15, 1),
                Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(12345), 255, 253));
        assertEquals(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(12345), 15, 0),
                Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(12345), 255, 252));
    }

    @Test public void preLdpcFormulaRejectsMissingValidityAndMalformedInputs() {
        assertNull(Tbs5530SignalMetrics.preLdpcBer(null, 0, 0));
        assertNull(Tbs5530SignalMetrics.preLdpcBer(new byte[3], 0, 0));
        assertNull(Tbs5530SignalMetrics.preLdpcBer(new byte[5], 0, 0));
        assertNull(Tbs5530SignalMetrics.preLdpcBer(new byte[4], 0, 0));
        for (int bad : new int[]{-1, 256}) {
            assertNull(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(0), bad, 0));
            assertNull(Tbs5530SignalMetrics.preLdpcBer(ldpcCounter(0), 0, bad));
        }
    }

    @Test public void officialPerFormulaZeroRoundingAndCounterLimits() {
        assertEquals(0.0, Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 0}, 0), 0.0);
        assertEquals(1.0, Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 1}, 0), 0.0);
        assertNull(Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 2}, 0));
        assertEquals(0.007813, Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 1}, 7), 0.0);
        assertEquals(0.000031, Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 1}, 15), 0.0);
        assertEquals(1.0, Tbs5530SignalMetrics.officialPer(new byte[]{0, (byte) 0x80, 0}, 15), 0.0);
        assertNull(Tbs5530SignalMetrics.officialPer(new byte[]{0, (byte) 0x80, 1}, 15));
        assertNull(Tbs5530SignalMetrics.officialPer(new byte[]{0, (byte) 0xff, (byte) 0xff}, 15));
    }

    @Test public void officialPerFormulaMasksPeriodButDoesNotInventFaValidityBit() {
        assertEquals(Tbs5530SignalMetrics.officialPer(new byte[]{0, 0, 1}, 15),
                Tbs5530SignalMetrics.officialPer(new byte[]{(byte) 0xff, 0, 1}, 255));
        assertNull(Tbs5530SignalMetrics.officialPer(null, 0));
        assertNull(Tbs5530SignalMetrics.officialPer(new byte[2], 0));
        assertNull(Tbs5530SignalMetrics.officialPer(new byte[4], 0));
        for (int bad : new int[]{-1, 256})
            assertNull(Tbs5530SignalMetrics.officialPer(new byte[3], bad));
    }

    @Test public void actualWindowsGoldenBytesDecodeSnrAndSourceConfirmedL1ButBerNeedsPeriod() {
        // Sanitized pairs from SHA70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded.
        Tbs5530SignalMetrics m = Tbs5530SignalMetrics.decode(Arrays.asList(
                raw(0x20, 0x10, 0x26), raw(0x20, 0x28, 0x03, 0x70),
                raw(0x22, 0x12, 1), raw(0x22, 0x5b, 3), raw(0x22, 0x5c, 2), raw(0x22, 0x5e, 1),
                raw(0x24, 0x40, 1, 0, 3, 0xb5)));
        assertEquals(Tbs5530SignalMetrics.Lock.LOCKED, m.lock); assertEquals(Integer.valueOf(6), m.syncState);
        assertEquals(20.75555060468097, m.snrDbCurve, 1e-10);
        assertEquals(Integer.valueOf(20756), m.officialCnrMilliDb);
        assertEquals(m.snrDbCurve, m.officialCnrDb);
        assertNull(m.ber); assertNull(m.fer); assertNull(m.per); assertNull(m.ifAgc);
        assertNull(m.fft); assertNull(m.guardInterval);
        assertEquals("64-QAM", m.modulation); assertEquals("3/4", m.codeRate); assertEquals("64K", m.fec);
        assertNull(m.plpId); assertNull(m.timeInterleaving); assertNull(m.rssiDbm);
    }
    @Test public void snrZeroOrMissingIsUnavailableButTsUnlockDoesNotHideRawSnr() {
        assertNull(Tbs5530SignalMetrics.snrDb(0));
        assertNull(Tbs5530SignalMetrics.decode(Collections.emptyList()).snrDbCurve);
        Tbs5530SignalMetrics m = Tbs5530SignalMetrics.decode(Arrays.asList(raw(0x20, 0x10, 0x06), raw(0x20, 0x28, 3, 0x70)));
        assertEquals(Tbs5530SignalMetrics.Lock.LOCKED, m.lock); assertEquals(Boolean.FALSE, m.tsLock);
        assertEquals(Tbs5530SignalMetrics.snrDb(880), m.snrDbCurve);
        Tbs5530SignalMetrics lost = Tbs5530SignalMetrics.decode(Arrays.asList(raw(0x20, 0x10, 0x10), raw(0x20, 0x28, 3, 0x70)));
        assertEquals(Tbs5530SignalMetrics.Lock.LOST, lost.lock);
        assertEquals(m.snrDbCurve, lost.snrDbCurve);
        assertEquals(Integer.valueOf(880), m.snrRaw);
    }
    @Test public void snrBoundaryClampAndSubtractionAvoidInfinity() {
        assertTrue(Double.isFinite(Tbs5530SignalMetrics.snrDb(1)));
        assertEquals(Tbs5530SignalMetrics.snrDb(10876), Tbs5530SignalMetrics.snrDb(65535));
        assertTrue(Tbs5530SignalMetrics.snrDb(10876) > Tbs5530SignalMetrics.snrDb(10875));
        for (int bad : new int[]{-1, 65536}) {
            try { Tbs5530SignalMetrics.snrDb(bad); fail("invalid word accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    private byte[] bchCounter(int errors) {
        return new byte[]{1, (byte) (errors >>> 16), (byte) (errors >>> 8), (byte) errors};
    }
    @Test public void preBchCounterUsesValidityMaskDenominatorAndRejectsReservedFec() {
        assertEquals(1.0, Tbs5530SignalMetrics.preBchBer(bchCounter(7200), 0, 0, 0), 0.0);
        assertNull(Tbs5530SignalMetrics.preBchBer(bchCounter(7201), 0, 0, 0));
        assertEquals(1.0 / (32768L * 48600),
                Tbs5530SignalMetrics.preBchBer(new byte[]{1, (byte) 0xc0, 0, 1}, 255, 253, 251), 0.0);
        assertNull(Tbs5530SignalMetrics.preBchBer(new byte[4], 0, 0, 0));
        assertNull(Tbs5530SignalMetrics.preBchBer(new byte[3], 0, 0, 0));
        assertNull(Tbs5530SignalMetrics.preBchBer(bchCounter(0), 0, 2, 0));
        assertNull(Tbs5530SignalMetrics.preBchBer(bchCounter(0), -1, 0, 0));
    }
    @Test public void preBchAndPlpRequireValidL1AndDemodButNotTsLock() {
        java.util.List<Tbs5530SignalMonitor.RegisterRead> reads = new java.util.ArrayList<>(Arrays.asList(
                raw(0x20, 0x10, 6), raw(0x20, 0x72, 0), raw(0x22, 0x12, 1),
                raw(0x22, 0x54, 0), raw(0x22, 0x5b, 3), raw(0x22, 0x5c, 2),
                raw(0x22, 0x5e, 1), raw(0x22, 0x62, 3), raw(0x24, 0x40, 1, 0, 3, 0xb5)));
        Tbs5530SignalMetrics m = Tbs5530SignalMetrics.decode(reads);
        assertEquals(949.0 / 48600, m.ber, 0.0); assertEquals(Integer.valueOf(0), m.plpId);
        assertEquals(Integer.valueOf(3), m.timeInterleaving);
        reads.set(2, raw(0x22, 0x12, 0));
        m = Tbs5530SignalMetrics.decode(reads);
        assertNull(m.ber); assertNull(m.modulation); assertNull(m.plpId);
        reads.set(2, raw(0x22, 0x12, 1)); reads.set(0, raw(0x20, 0x10, 0x16));
        assertNull(Tbs5530SignalMetrics.decode(reads).ber);
    }
    @Test public void conflictingDuplicateReadsFailClosedAndBlockReadOffsetsDecode() {
        assertEquals("64-QAM", Tbs5530SignalMetrics.decode(Arrays.asList(raw(0x20, 0x10, 6),
                raw(0x22, 0x12, 1), raw(0x22, 0x5b, 3, 2, 0, 1))).modulation);
        assertEquals(Tbs5530SignalMetrics.Lock.UNKNOWN, Tbs5530SignalMetrics.decode(Arrays.asList(
                raw(0x20, 0x10, 6), raw(0x20, 0x10, 0x16))).lock);
    }
    @Test public void unlockBitAndReservedSyncAreNotSuccessfulLock() {
        assertEquals(Tbs5530SignalMetrics.Lock.LOST,
                Tbs5530SignalMetrics.decode(Collections.singletonList(raw(0x20, 0x10, 0x36))).lock);
        assertEquals(Tbs5530SignalMetrics.Lock.UNKNOWN,
                Tbs5530SignalMetrics.decode(Collections.singletonList(raw(0x20, 0x10, 0x27))).lock);
    }
}
