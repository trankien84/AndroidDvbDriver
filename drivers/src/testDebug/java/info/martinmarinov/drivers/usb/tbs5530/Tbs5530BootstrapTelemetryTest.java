// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class Tbs5530BootstrapTelemetryTest {
    @Test public void all132BoundariesSeparateImageAndPayloadAndUseFreshEpoch() throws Exception {
        List<Tbs5530BootstrapTelemetry.Snapshot> audit = new ArrayList<>();
        Tbs5530BootstrapTelemetry t = new Tbs5530BootstrapTelemetry(audit::add);
        for (int n = 1; n <= 132; n++) {
            boolean data = n >= 3 && n <= 130;
            t.planned(n); t.guardRequested(n); t.guardAccepted(n); t.nativeIntent(n);
            t.nativeStarted(n); t.nativeReturned(n, data ? (n - 3) * 64 : 0xe600, data ? 64 : 1, data ? 64 : 1);
        }
        Tbs5530BootstrapTelemetry.Snapshot s = t.snapshot();
        assertEquals(132, s.plannedOrdinal); assertEquals(132, s.guardRequested);
        assertEquals(132, s.guardAccepted); assertEquals(132, s.nativeCallStarted);
        assertEquals(132, s.nativeCallCompleted); assertEquals(132, s.fullLengthCompleted);
        assertEquals(8192, s.imageBytesFullLength); assertEquals(8196, s.payloadBytesFullLength);
        assertEquals(264, audit.size()); assertEquals(0, audit.get(0).nativeCallStarted);
        assertEquals(1, audit.get(1).nativeCallCompleted);
        assertNotEquals(s.runEpoch, new Tbs5530BootstrapTelemetry().snapshot().runEpoch);
        try { s.events.add("mutated"); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void injectedEpochAndClockAlignGuardWithNativeTimeline() {
        String epoch = "11111111-2222-3333-4444-555555555555";
        Tbs5530BootstrapTelemetry t = new Tbs5530BootstrapTelemetry(s -> { }, () -> 987000000L,
                "ANDROID_ELAPSED_REALTIME_NS", epoch);
        t.planned(24); assertEquals(epoch, t.snapshot().runEpoch);
        assertTrue(t.snapshot().events.get(0).contains("monotonic_ns=987000000"));
        assertEquals("ANDROID_ELAPSED_REALTIME_NS", t.snapshot().timebase);
    }
    @Test public void failureResultsDoNotBecomeFullLengthOrUnknownInFlight() throws Exception {
        Tbs5530BootstrapTelemetry t = new Tbs5530BootstrapTelemetry();
        t.nativeStarted(1); assertEquals(0, t.snapshot().nativeCallCompleted);
        t.nativeThrew(1, new IOException("private"));
        t.nativeStarted(2); t.nativeReturned(2, 0xe600, 1, -1);
        t.nativeStarted(3); t.nativeReturned(3, 0, 64, 63);
        assertEquals(3, t.snapshot().nativeCallCompleted);
        assertEquals(0, t.snapshot().fullLengthCompleted); assertEquals(0, t.snapshot().imageBytesFullLength);
        assertFalse(t.snapshot().events.toString().contains("private"));
    }
    @Test public void auditFailureBeforeNativeIsFailClosedAndKeepsCause() throws Exception {
        IOException underlying = new IOException("private path");
        Tbs5530BootstrapTelemetry t = new Tbs5530BootstrapTelemetry(s -> { throw underlying; });
        try { t.nativeIntent(1); fail(); } catch (Tbs5530BootstrapStop expected) {
            assertEquals(Tbs5530BootstrapStop.Kind.AUDIT_FAILED, expected.kind); assertSame(underlying, expected.getCause());
        }
        assertEquals(0, t.snapshot().nativeCallStarted);
        assertFalse(t.snapshot().events.toString().contains("private path"));
    }
}
