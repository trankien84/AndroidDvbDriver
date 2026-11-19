// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class Tbs5530SignalMonitorTest {
    private static final class Clock implements Tbs5530SignalMonitor.Clock {
        long now;
        public long nanoTime() { return now; }
        void tick() { now += Tbs5530SignalMonitor.TICK_NANOS; }
    }
    private static class Io implements Tbs5530ControlIo {
        int bank = 0x20, register, length, status = 0x26;
        boolean frozen, failStatus, failFreeze, failUnfreeze, shortRead;
        final List<String> reads = new ArrayList<>();
        public int controlTransfer(int type, int request, int value, int index, byte[] b,
                                   int offset, int n, int timeout) throws IOException {
            assertEquals(0, value); assertEquals(0, index); assertEquals(0, offset);
            assertEquals(2000, timeout);
            if (request == 0x80) {
                assertEquals(0x40, type); assertEquals(4, n);
                assertEquals(3, b[0]); assertEquals(0xc8, b[1] & 255);
                int reg = b[2] & 255, v = b[3] & 255;
                assertTrue(reg == 0 && (v == 0x20 || v == 0x22 || v == 0x24) || reg == 1 && v <= 1);
                if (reg == 0) bank = v;
                else {
                    if (v == 1 && failFreeze || v == 0 && failUnfreeze) throw new IOException("private serial must not escape");
                    frozen = v == 1;
                }
            } else if (request == 0x90) {
                assertTrue(frozen); assertEquals(0x40, type); assertEquals(3, n);
                assertEquals(0xc8, b[1] & 255);
                length = b[0] & 255; register = b[2] & 255;
            } else if (request == 0x91) {
                assertEquals(0xc0, type); assertEquals(length, n);
                reads.add(String.format("%02x:%02x:%d", bank, register, n));
                if (bank == 0x20 && register == 0x10) {
                    if (failStatus) throw new IOException("status failed");
                    b[0] = (byte) status;
                } else if (bank == 0x20 && register == 0x28) {
                    // Real capture SHA70fced47... first SNR pair: 03 70, bank20, not a random golden.
                    b[0] = 3; b[1] = 0x70;
                } else if (bank == 0x22 && register == 0x5b) b[0] = 3;
                else if (bank == 0x22 && register == 0x5c) b[0] = 2;
                else if (bank == 0x22 && register == 0x5e) b[0] = 1;
                else if (bank == 0x24 && register == 0x40) { b[0] = 1; b[2] = 3; b[3] = (byte) 0xb5; }
                if (shortRead) return n - 1;
            } else fail("Unallowlisted request " + request);
            return n;
        }
    }

    @Test public void goldenReadsUseActualBank22AndOnlyDemodBankFreezeWrites() {
        Io io = new Io(); Clock clock = new Clock();
        Tbs5530SignalMonitor.Sample s = new Tbs5530SignalMonitor(io, clock).sample();
        assertTrue(s.errors.isEmpty()); assertEquals(8, s.rawReads.size());
        assertTrue(io.reads.contains("22:12:1")); assertTrue(io.reads.contains("22:5b:1"));
        assertTrue(io.reads.contains("24:40:4")); assertFalse(io.reads.contains("20:5b:1"));
        assertFalse(io.frozen); assertEquals(0x20, io.bank);
        assertEquals(Tbs5530SignalMetrics.Lock.LOCKED, s.metrics.lock);
        assertEquals(Integer.valueOf(880), s.metrics.snrRaw);
        assertEquals(20.75555060468097, s.metrics.snrDbCurve, 0.0000001);
        assertNull(s.metrics.ber); assertNull(s.metrics.per); assertNull(s.metrics.modulation);
        assertFalse(s.stopForLockLoss);
    }

    @Test public void samplesAreImmutableSnapshotsAndDoNotExposeExceptionText() {
        Io io = new Io(); io.failStatus = true;
        Tbs5530SignalMonitor.Sample s = new Tbs5530SignalMonitor(io, new Clock()).sample();
        assertEquals(Tbs5530SignalMetrics.Lock.UNKNOWN, s.metrics.lock);
        assertEquals(1, s.errors.size()); assertEquals("MONITOR_IO_ERROR", s.errors.get(0).message);
        assertEquals("java.io.IOException", s.errors.get(0).exceptionType);
        byte[] b = s.rawReads.get(0).getBytes(); b[0] = 99;
        assertNotEquals(99, s.rawReads.get(0).getBytes()[0]);
        try { s.rawReads.clear(); fail("mutable reads"); } catch (UnsupportedOperationException expected) { }
        try { s.errors.clear(); fail("mutable errors"); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void tickDoesNotCatchUpOrStopBeforeStrictlyMoreThanTwoSeconds() {
        Io io = new Io(); io.status = 0x05; Clock clock = new Clock();
        Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, clock);
        assertFalse(m.sampleIfDue().stopForLockLoss); assertNull(m.sampleIfDue());
        for (int n = 1; n <= 8; n++) { clock.tick(); assertFalse(m.sampleIfDue().stopForLockLoss); }
        clock.tick(); Tbs5530SignalMonitor.Sample s = m.sampleIfDue();
        assertTrue(s.stopForLockLoss); assertEquals(2_250_000_000L, s.confirmedLockLossNanos);
        io.status = 0x26; clock.tick(); assertFalse(m.sample().stopForLockLoss);
        io.status = 0x05; clock.now += 3_000_000_000L;
        assertFalse(m.sampleIfDue().stopForLockLoss); assertNull(m.sampleIfDue());
    }

    @Test public void unknownReadOrReservedStatusBreaksContinuousLossAndNextTickRecovers() {
        Io io = new Io(); io.status = 0x05; Clock c = new Clock(); Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, c);
        m.sample(); for (int i = 0; i < 7; i++) { c.tick(); m.sample(); }
        io.failStatus = true; c.tick(); assertFalse(m.sample().stopForLockLoss);
        io.failStatus = false; c.tick(); assertEquals(0, m.sample().confirmedLockLossNanos);
        io.status = 7; c.tick(); assertEquals(Tbs5530SignalMetrics.Lock.UNKNOWN, m.sample().metrics.lock);
        io.status = 0x05; c.tick(); assertEquals(0, m.sample().confirmedLockLossNanos);
    }

    @Test public void uncertainCleanupAndLongSamplingGapNeverConfirmLoss() {
        Io io = new Io(); io.status = 0x05; Clock c = new Clock(); Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, c);
        m.sample(); c.now += 2_500_000_000L; assertFalse(m.sample().stopForLockLoss);
        io.failUnfreeze = true; c.tick(); Tbs5530SignalMonitor.Sample s = m.sample();
        assertFalse(s.cleanupSucceeded); assertFalse(s.stopForLockLoss);
        assertEquals("UNFREEZE", s.errors.get(0).operation);
        io.failUnfreeze = false; c.tick(); assertEquals(0, m.sample().confirmedLockLossNanos);
    }

    @Test public void shortReadAndFreezeFailureStillCleanupAndContinueNextSample() {
        Io io = new Io(); io.shortRead = true; Clock c = new Clock(); Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, c);
        Tbs5530SignalMonitor.Sample s = m.sample(); assertEquals(8, s.errors.size());
        assertTrue(s.rawReads.isEmpty()); assertEquals("SHORT_CONTROL_TRANSFER", s.errors.get(0).message);
        assertFalse(io.frozen); io.shortRead = false; io.failFreeze = true;
        c.tick(); assertEquals("FREEZE_SETUP", m.sample().errors.get(0).operation); assertFalse(io.frozen);
        io.failFreeze = false; c.tick(); assertEquals(Tbs5530SignalMetrics.Lock.LOCKED, m.sample().metrics.lock);
    }

    @Test public void concurrentCallsCannotInterleaveFreezeBankOrReadWithinOneMonitor() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), max = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
        Io io = new Io() {
            @Override public int controlTransfer(int t, int r, int v, int i, byte[] b, int o, int n, int timeout) throws IOException {
                int concurrent = active.incrementAndGet(); max.set(Math.max(max.get(), concurrent));
                try {
                    if (entered.getCount() != 0) {
                        entered.countDown();
                        try { if (!release.await(2, TimeUnit.SECONDS)) throw new IOException("test latch timeout"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                    }
                    return super.controlTransfer(t, r, v, i, b, o, n, timeout);
                } finally { active.decrementAndGet(); }
            }
        };
        Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, new Clock());
        Runnable run = () -> { try { assertTrue(m.sample().errors.isEmpty()); } catch (Throwable e) { failure.set(e); } };
        Thread a = new Thread(run), b = new Thread(run);
        try {
            a.start(); assertTrue(entered.await(2, TimeUnit.SECONDS)); b.start(); release.countDown();
            a.join(3000); b.join(3000); assertFalse(a.isAlive()); assertFalse(b.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertEquals(1, max.get()); assertEquals(16, io.reads.size());
        } finally { release.countDown(); a.join(3000); b.join(3000); }
    }

    @Test public void slowRemainingReadsOrClockRollbackCannotCompleteLossThreshold() {
        Clock c = new Clock();
        Io io = new Io() {
            boolean slow;
            @Override public int controlTransfer(int t, int r, int v, int i, byte[] b, int o, int n, int timeout) throws IOException {
                int result = super.controlTransfer(t, r, v, i, b, o, n, timeout);
                if (r == 0x91 && bank == 0x24 && register == 0x40 && slow) c.now += 1_000_000_000L;
                slow = c.now >= 2_000_000_000L;
                return result;
            }
        };
        io.status = 0x05; Tbs5530SignalMonitor m = new Tbs5530SignalMonitor(io, c);
        m.sample(); for (int i = 0; i < 7; i++) { c.tick(); m.sample(); }
        c.tick(); assertFalse(m.sample().stopForLockLoss);
        c.tick(); assertEquals(0, m.sample().confirmedLockLossNanos);
        c.now = -1_000_000_000L; assertFalse(m.sample().stopForLockLoss);
    }

    @Test public void b5AddsPlpAndInterleavingWithoutChangingLegacyInventory() {
        Io io = new Io();
        Tbs5530SignalMonitor.Sample sample = new Tbs5530SignalMonitor(io, new Clock(), false, true).sample();
        assertEquals(10, sample.rawReads.size());
        assertTrue(io.reads.contains("22:54:1")); assertTrue(io.reads.contains("22:62:1"));
    }
    @Test public void tsBitFlappingDoesNotStopDemodLockedMonitor() {
        Io io = new Io(); Clock c = new Clock(); Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor(io, c);
        for (int i = 0; i < 30; i++) {
            io.status = i % 2 == 0 ? 0x26 : 0x06;
            assertFalse(monitor.sample().stopForLockLoss); c.tick();
        }
    }

    @Test public void tunerRssiIsRefusedBeforeAnyIo() {
        Io io = new Io();
        try { new Tbs5530SignalMonitor(io, new Clock(), true); fail("unsafe RSSI accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().startsWith("TUNER_RSSI_UNSUPPORTED")); }
        assertTrue(io.reads.isEmpty());
    }
}
