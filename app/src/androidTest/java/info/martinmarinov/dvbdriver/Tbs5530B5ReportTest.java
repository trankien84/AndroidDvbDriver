// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530TsSmoke;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Configuration and fake-clock reports only; no grant, USB connection, firmware or receive call. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530B5ReportTest {
    @Test public void phaseClockLoadsNativeMonotonicSourceWithoutOpeningUsb() throws Exception {
        long before = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos();
        Thread.sleep(2);
        long after = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos();
        assertTrue(before > 0); assertTrue(after > before);
    }
    @Test public void onePlusIsB5OnlyAndAutomaticColdLaunchCannotBorrowFallbackApproval() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context, "NATIVE_USBFS_STREAM", "GM1900", 30, 1,
                true, false, null, false, false, "B2_RELOAD_AFTER_SET_CONFIG"));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context, "NATIVE_USBFS_STREAM", "GM1900", 30, 1,
                true, false, null, false, false, "B5_K25_AUTO"));
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "NATIVE_USBFS_STREAM", "GM1900", 30, 1,
                true, false, null, false, false, "B5_K25_MANUAL_PLP0");
        session.configureStream(16, 16384); session.configureB5();
        assertThrows(IOException.class, () -> session.configureB4(30000, 268435456, false));
        JSONObject challenge = new JSONObject(Tbs5530AutonomousSession.read(new File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        assertTrue(challenge.getBoolean("b5_enabled")); assertTrue(challenge.getBoolean("b5_allow_k25_auto"));
        assertEquals(100, challenge.getInt("b5_monitor_period_ms")); assertEquals(20, challenge.getInt("b5_monitor_jitter_ms"));
        assertEquals(Tbs5530B5Runner.LAYOUT, challenge.getString("b5_layout"));
        assertFalse(new File(session.directory, "grant.json").exists()); session.policy.stop();
    }
    @Test public void phaseReportKeepsNativeClockStaleAnnotationAndUnknownQualityWithoutHardware() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "NATIVE_USBFS_STREAM", "GM1900", 30, 1,
                true, false, null, false, false, "B5_K36_MANUAL_PLP0");
        session.configureStream(16, 16384); session.configureB5(); session.b5Runner = new Tbs5530B5Runner();
        session.b5Runner.run(new Tbs5530B5Runner.Port() {
            long now;
            public long nanos() { return now; }
            public void require() { }
            public void pause(int millis) { now += millis * 1_000_000L; }
            public void selectStream() { }
            public void rssi(Tbs5530B5Runner.Phase phase) { }
            public void drain() { }
            public Tbs5530B5Runner.Outcome receive(Tbs5530B5Runner.Phase phase) {
                now += phase.durationMillis * 1_000_000L;
                return new Tbs5530B5Runner.Outcome(0, true, false, "DEADLINE");
            }
        });
        session.report("FAKE_ONLY", 0, 0, -1);
        JSONObject report = new JSONObject(Tbs5530AutonomousSession.read(new File(session.directory, "report.json"), 65536));
        assertTrue(report.getBoolean("b5_enabled")); assertFalse(report.getBoolean("ts_smoke_pass"));
        assertTrue(report.isNull("native_stream")); assertEquals("PENDING_PAIRED_OFFLINE_ANALYSIS", report.getString("b5_quality"));
        JSONArray phases = report.getJSONArray("b5_phases"); assertEquals(4, phases.length());
        for (int i = 0; i < 4; i++) {
            JSONObject phase = phases.getJSONObject(i);
            assertEquals("CLOCK_MONOTONIC", phase.getString("clock")); assertTrue(phase.getBoolean("drained"));
            assertEquals(i == 1 ? 2048 : 0, phase.getInt("stale_prefix_bytes"));
            assertEquals(i == 1 ? "STALE_PREFIX" : "NONE", phase.getString("prefix_classification"));
            assertEquals("NOT_STARTED", phase.getString("raw_capture"));
        }
        session.policy.stop();
    }
    @Test public void b5ProgramApprovalBindsFourPhasesAndCannotConsumeLegacyOrSecondOnLimits() throws Exception {
        Tbs5530AutonomousApprovalTest.Storage storage = new Tbs5530AutonomousApprovalTest.Storage();
        Tbs5530TsSmoke.Limits limits = Tbs5530TsSmoke.Limits.b5("ON1");
        Tbs5530OperationalApproval approval = new Tbs5530OperationalApproval(storage, () -> 1000, null, limits, 1);
        storage.authorize(); storage.permit.put("explicit_native_urb_reception", true); approval.rfIn(true);
        assertEquals(65000, storage.permit.getLong("maximum_ms"));
        assertTrue(storage.permit.getString("transcript").contains("B5_FOUR_PHASES|layout=" + Tbs5530B5Runner.LAYOUT));
        assertThrows(IOException.class, () -> approval.consume(Tbs5530TsSmoke.Limits.b5("ON2"), 1));
        assertThrows(IOException.class, () -> approval.consume(Tbs5530TsSmoke.Limits.b4(30000, 16, false), 1));
        approval.consume(limits, 1);
    }
}
