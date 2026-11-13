// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530SignalRssiTest {
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    @Test public void exactUserReviewedWindowsSequenceAndGoldenRestore88ZeroThen87C0() throws Exception {
        List<String> operations = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger(), clock = new AtomicInteger();
        Tbs5530ControlIo io = (type, request, value, index, bytes, offset, length, timeout) -> {
            assertEquals(0, value); assertEquals(0, index); assertEquals(2000, timeout);
            operations.add(String.format(java.util.Locale.ROOT, "%02x:%02x:%s", type, request, hex(bytes)));
            if (request == 0x91) bytes[0] = (byte) new int[]{60, 7, 0x88}[reads.getAndIncrement()];
            return length;
        };
        Tbs5530SignalRssi.Result result = Tbs5530SignalRssi.sample(Tbs5530SignalRssi.Phase.OFF,
                io, clock::incrementAndGet, 594000);
        assertEquals(Arrays.asList("40:80:04c087c441", "40:80:04c0590501", "40:90:01c05b", "c0:91:00",
                "40:80:04c0590301", "40:90:01c05b", "c0:91:00", "40:80:03c05904",
                "40:80:03c08800", "40:80:03c087c0", "40:90:01c069", "c0:91:00"), operations);
        assertEquals(-63.52, result.rssiDbm, 0.0);
        assertEquals(60, result.ifAgc); assertEquals(7, result.rfAgc); assertEquals(0x88, result.reg69);
        assertTrue(result.cleanupSucceeded); assertTrue(result.endNanos > result.startNanos);
    }
    @Test public void onOrMissingPhaseIsRejectedBeforeAnyIoOrClock() throws Exception {
        for (Tbs5530SignalRssi.Phase phase : new Tbs5530SignalRssi.Phase[]{null, Tbs5530SignalRssi.Phase.ON}) {
            try {
                Tbs5530SignalRssi.sample(phase, (a,b,c,d,e,f,g,h) -> { fail("ON I/O"); return 0; },
                        () -> { fail("ON clock"); return 0; }, 594000);
                fail("ON accepted");
            } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void adcFailureStillRestoresAllThreeRegistersAndDoesNotFabricateRssi() throws Exception {
        List<String> writes = new ArrayList<>();
        Tbs5530ControlIo io = (a, request, c, d, bytes, f, length, h) -> {
            if (request == 0x80) writes.add(hex(bytes));
            if (request == 0x91) throw new IOException("synthetic ADC failure");
            return length;
        };
        try { Tbs5530SignalRssi.sample(Tbs5530SignalRssi.Phase.OFF, io, System::nanoTime, 594000); fail(); }
        catch (IOException expected) { assertEquals("synthetic ADC failure", expected.getMessage()); }
        assertEquals(Arrays.asList("04c087c441", "04c0590501", "03c05904", "03c08800", "03c087c0"), writes);
    }
    @Test public void cleanupFailureStillAttemptsRemainingCleanupAndFailsClosed() throws Exception {
        List<String> writes = new ArrayList<>();
        Tbs5530ControlIo io = (a, request, c, d, bytes, f, length, h) -> {
            if (request == 0x80) {
                writes.add(hex(bytes));
                if (hex(bytes).equals("03c05904")) return length - 1;
            }
            if (request == 0x91) bytes[0] = 60;
            return length;
        };
        try { Tbs5530SignalRssi.sample(Tbs5530SignalRssi.Phase.OFF, io, System::nanoTime, 594000); fail(); }
        catch (IOException expected) { assertEquals("RSSI_SHORT_CONTROL_TRANSFER", expected.getMessage()); }
        assertEquals(Arrays.asList("03c05904", "03c08800", "03c087c0"), writes.subList(writes.size() - 3, writes.size()));
    }
    @Test public void rssiUsesRfMaximumAndFrequencyBandInsteadOfFixedK36Calibration() {
        assertNotEquals(Tbs5530SignalRssi.ascot3Dbm(60, 7, 0x88, 594000),
                Tbs5530SignalRssi.ascot3Dbm(60, 100, 0x88, 594000), 0.0);
        assertEquals(-63.52, Tbs5530SignalRssi.ascot3Dbm(60, 7, 0x88, 594000), 0.0);
        assertEquals(-63.32, Tbs5530SignalRssi.ascot3Dbm(60, 7, 0x88, 700001), 0.0);
        try { Tbs5530SignalRssi.ascot3Dbm(256, 7, 0x88, 594000); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
