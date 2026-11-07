// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530TsSmoke;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/** Isolated/in-memory permissions only. Never supplies a host session grant or a USB device. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530AutonomousApprovalTest {
    static final class Storage implements Tbs5530OperationalApproval.Storage {
        final Map<String, String> files = new HashMap<>(); final Set<String> used = new HashSet<>();
        JSONObject permit;
        public String read() { return permit.toString(); }
        public void write(String name, String text) { files.put(name, text); }
        public boolean consumed(String id) { return used.contains(id); }
        public void consume(String id) throws IOException { if (!used.add(id)) throw new IOException("Duplicate"); }
        void authorize() throws Exception {
            permit = new JSONObject(files.get("phase31-challenge.json"));
            permit.put("approval_id", "00000000-0000-4000-8000-000000000001").put("explicit_control_approval", true)
                    .put("physical_current", true).put("approved_maximum_ms", permit.getLong("maximum_ms"))
                    .put("expires_elapsed_ms", 301000).put("heartbeat_elapsed_ms", 1000);
        }
    }
    @Test public void queueNeedsItsOwnExplicitScopeAndDifferentTranscript() throws Exception {
        Storage store = new Storage();
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false,
                Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096);
        Tbs5530OperationalApproval queue = new Tbs5530OperationalApproval(store, () -> 1000, null, limits, 1);
        store.authorize(); assertFalse(queue.eligible()); assertFalse(queue.queuedReceptionApproved());
        assertTrue(store.permit.getString("transcript").startsWith("TBS5530-TS-SMOKE/v5|"));
        store.permit.put("explicit_queued_reception", true); queue.rfIn(true);
        assertTrue(queue.eligible()); assertTrue(queue.queuedReceptionApproved()); queue.consume(limits, 1);
        assertThrows(IOException.class, () -> queue.consume(limits, 1)); assertEquals(1, store.used.size());
        store.permit.put("explicit_queued_reception", false); assertThrows(IOException.class, queue::requireCurrent);
    }
    @Test public void synchronousLegacyTranscriptAndConstructorsRemainUnchanged() throws Exception {
        Storage store = new Storage(); Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false);
        Tbs5530OperationalApproval sync = new Tbs5530OperationalApproval(store, () -> 1000, null, limits, 1);
        store.authorize(); assertTrue(sync.eligible()); assertFalse(sync.queuedReceptionApproved());
        assertTrue(store.permit.getString("transcript").startsWith("TBS5530-TS-SMOKE/v4|"));
        assertTrue(store.permit.getString("transcript").contains("readApi=USBDEVFS_BULK_ERRNO"));
        assertFalse(store.permit.has("queued_reception"));
    }
    @Test public void sessionStorageIssuesExactSingleUsePlanWithoutTouchingManualStores() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Map<String, ?> legacy = new HashMap<>(context.getSharedPreferences("phase31-control-attempts", 0).getAll());
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "GM1900", 30, 1);
        session.policy.foreground(true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        grant.put("profile", "AUTO_VERIFIED").put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", session.policy.created() + 300000);
        session.write("grant.json", grant.toString());
        session.write("heartbeat.json", new JSONObject().put("session", session.id).put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
        session.acceptGrant();
        String owner = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        session.policy.observe("SYNTHETIC_TEST_FIXTURE", 1, true,
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE);
        session.owner(owner, 1, () -> { });
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Plan plan = info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.tune(true);
        Tbs5530OperationalApproval approval = session.approval(plan, null);
        approval.requireCurrent(); assertTrue(approval.eligible()); approval.consume(plan); approval.requireCurrent();
        assertThrows(IOException.class, () -> approval.consume(plan));
        assertEquals(legacy, context.getSharedPreferences("phase31-control-attempts", 0).getAll());
        session.write("ledger.jsonl", "CORRUPT");
        IOException failure = assertThrows(IOException.class, approval::requireCurrent);
        session.failure(failure); session.report("ERROR_UNCERTAIN_NO_RETRY", 0, 0, -1);
        JSONObject report = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(session.directory, "report.json"), 16384));
        assertEquals("SESSION_LEDGER_CORRUPT", report.getString("stop_reason"));
        assertEquals("OPERATIONAL", report.getString("failure_stage"));
        assertEquals("IOException", report.getString("error_class"));
        assertTrue(report.isNull("last_errno"));
        assertFalse(report.toString().contains(owner));
        session.policy.stop();
    }
    @Test public void standingHeartbeatCanPredateProgramButNotSessionAndStillExpiresAtTwoSeconds() throws Exception {
        long[] now = {1100};
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false);
        Storage store = new Storage();
        Tbs5530OperationalApproval approval = new Tbs5530OperationalApproval(store, () -> now[0], null, limits, 1,
                "SYNTHETIC_TEST_FIXTURE", 900);
        store.authorize(); store.permit.put("expires_elapsed_ms", 300900);
        assertEquals(1000, store.permit.getLong("heartbeat_elapsed_ms"));
        assertEquals(1100, store.permit.getLong("created_elapsed_ms"));
        approval.requireCurrent(); assertTrue(approval.eligible());
        now[0] = 2999; approval.requireCurrent();
        now[0] = 3000; assertThrows(IOException.class, approval::requireCurrent);
        assertEquals(1000, store.permit.getLong("heartbeat_elapsed_ms"));
    }
    @Test public void heartbeatOriginFixDoesNotRelaxLegacyFuturePreSessionOrBudgetChecks() throws Exception {
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false);
        Storage manual = new Storage();
        Tbs5530OperationalApproval legacy = new Tbs5530OperationalApproval(manual, () -> 1100, null, limits, 1);
        manual.authorize(); assertThrows(IOException.class, legacy::requireCurrent);
        Storage scoped = new Storage();
        Tbs5530OperationalApproval session = new Tbs5530OperationalApproval(scoped, () -> 1100, null, limits, 1,
                "SYNTHETIC_TEST_FIXTURE", 900);
        scoped.authorize();
        scoped.permit.put("expires_elapsed_ms", 300901); assertThrows(IOException.class, session::requireCurrent);
        scoped.permit.put("expires_elapsed_ms", 300900).put("heartbeat_elapsed_ms", 899); assertThrows(IOException.class, session::requireCurrent);
        scoped.permit.put("heartbeat_elapsed_ms", 1101); assertThrows(IOException.class, session::requireCurrent);
        scoped.permit.put("heartbeat_elapsed_ms", 1000).put("expires_elapsed_ms", 1100 + limits.maximumMillis() + 4999);
        assertThrows(IOException.class, session::requireCurrent);
        assertThrows(IOException.class, () -> new Tbs5530OperationalApproval(new Storage(), () -> 1100, null, limits, 1,
                "SYNTHETIC_TEST_FIXTURE", 1101));
        assertTrue(scoped.used.isEmpty());
    }
    @Test public void rawCapturePreservesExactChunksSyncsAndCannotOverwriteOrExceedBound() throws Exception {
        java.io.File directory = new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "raw-capture-test-" + java.util.UUID.randomUUID());
        assertTrue(directory.mkdir());
        byte[] first = new byte[]{0x47, 0, 1, 2, 99};
        byte[] second = new byte[]{3, 4, 5};
        Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(directory, 65536);
        capture.accept(first, 4); capture.accept(second, 3); capture.close();
        assertEquals(7, capture.bytes()); assertTrue(capture.durable());
        byte[] actual = new byte[7];
        try (java.io.FileInputStream input = new java.io.FileInputStream(new java.io.File(directory, "received.ts"))) {
            assertEquals(7, input.read(actual)); assertEquals(-1, input.read());
        }
        assertArrayEquals(new byte[]{0x47, 0, 1, 2, 3, 4, 5}, actual);
        assertThrows(IOException.class, () -> capture.accept(second, 3));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousCapture(directory, 65536));
        assertTrue(new java.io.File(directory, "received.ts").delete()); assertTrue(directory.delete());
    }
    @Test public void rawCaptureBoundsFailurePreservesCompletedBytesForAudit() throws Exception {
        java.io.File directory = new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "raw-bounds-test-" + java.util.UUID.randomUUID());
        assertTrue(directory.mkdir());
        Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(directory, 65536);
        byte[] chunk = new byte[65536]; capture.accept(chunk, chunk.length);
        assertThrows(IOException.class, () -> capture.accept(new byte[]{1}, 1));
        capture.close(); assertTrue(capture.durable()); assertEquals(65536, capture.bytes());
        assertEquals(65536, new java.io.File(directory, "received.ts").length());
        assertTrue(new java.io.File(directory, "received.ts").delete()); assertTrue(directory.delete());
    }
    @Test public void bootstrapTransitionDropsOldPhysicalGuardButCannotDispatchOrReuseOwner() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "GM1900", 30, 1);
        session.policy.foreground(true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        grant.put("profile", "AUTO_VERIFIED").put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", session.policy.created() + 300000);
        session.write("grant.json", grant.toString());
        session.write("heartbeat.json", new JSONObject().put("session", session.id).put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
        session.acceptGrant();
        session.policy.observe("SYNTHETIC_UPLOAD_ATTACHMENT", 1, true,
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530OperationalProfile.Kind.FX2_DEFAULT);
        session.policy.beginUpload(session.id);
        boolean[] physical = {true};
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.Guard guard = session.firmwareGuard(() -> {
            if (!physical[0]) throw new IOException("Synthetic upload lease no longer valid");
        });
        for (int ordinal = 1; ordinal <= 132; ordinal++) {
            session.write("heartbeat.json", new JSONObject().put("session", session.id)
                    .put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
            guard.requireWrite(ordinal); guard.requireNative(ordinal); guard.requireDispatch(ordinal);
        }
        physical[0] = false;
        guard.requireCurrent();
        assertThrows(IOException.class, () -> guard.requireWrite(133));
        assertThrows(IOException.class, () -> guard.requireNative(133));
        assertThrows(IOException.class, () -> guard.requireDispatch(133));
        assertThrows(IOException.class, guard::requireStartBudget);
        session.policy.uploadComplete(132, false); session.policy.permissionWaiting();
        assertEquals(info.martinmarinov.drivers.usb.tbs5530.Tbs5530AutonomousPolicy.State.NEED_ANDROID_USB_PERMISSION, session.policy.state());
        session.policy.observe("SYNTHETIC_NEW_ATTACHMENT", 2, true,
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE);
        String freshOwner = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        session.owner(freshOwner, 2, () -> { });
        session.policy.requireOwner(freshOwner, 2, true);
        assertThrows(IOException.class, () -> guard.requireNative(133));
        session.policy.stop();
    }
    @Test public void malformedRecoveryEpochCannotGrantSessionOrModifyUploadHistory() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File history = new java.io.File(context.getFilesDir(), "autonomous-v1-attachments.txt");
        String before = history.exists() ? Tbs5530AutonomousSession.read(history, 1024 * 1024) : null;
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "GM1900", 30, 1);
        session.policy.foreground(true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        grant.put("profile", "AUTO_VERIFIED").put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", session.policy.created() + 300000)
                .put("physical_recovery_epoch", "malformed").put("physical_recovery_confirmed", true);
        session.write("grant.json", grant.toString());
        assertThrows(IOException.class, session::acceptGrant);
        assertEquals("", Tbs5530AutonomousSession.read(new java.io.File(session.directory, "ledger.jsonl"), 16384));
        assertEquals(before, history.exists() ? Tbs5530AutonomousSession.read(history, 1024 * 1024) : null);
        session.policy.stop();
    }
    @Test public void launchIntentCannotGrantSessionOrReadLegacyConsumedStores() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Context context = instrumentation.getTargetContext();
        android.content.SharedPreferences old = context.getSharedPreferences("phase31-control-attempts", 0);
        Map<String, ?> before = new HashMap<>(old.getAll());
        Intent intent = new Intent(context, Tbs5530AutonomousDiagnosticActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("bounded_user_authorization", true).putExtra("explicit_control_approval", true)
                .putExtra("profile", "EXPERIMENTAL_DIAGNOSTIC");
        android.app.Activity activity = instrumentation.startActivitySync(intent); instrumentation.waitForIdleSync();
        assertEquals(before, old.getAll());
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
    }
}
