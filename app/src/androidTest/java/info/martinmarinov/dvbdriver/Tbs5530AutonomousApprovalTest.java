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
    static class Storage implements Tbs5530OperationalApproval.Storage {
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
    @Test public void b3CachedPermitStillChecksRealHeartbeatAndLiveOwnerAfterConsume() throws Exception {
        final long[] now = {1000}, beat = {1000}; final boolean[] live = {true};
        final int[] reads = {0};
        Storage store = new Storage() {
            @Override public String read() { reads[0]++; return super.read(); }
            @Override public void requireLiveReceive() throws IOException { if (!live[0]) throw new IOException("Permission revoked"); }
            @Override public long receiveHeartbeat() { return beat[0]; }
        };
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(4194304, 65536, 2000, 500, 16384, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM, false, false, 16);
        Tbs5530OperationalApproval approval = new Tbs5530OperationalApproval(store, () -> now[0], null, limits, 1);
        store.authorize(); store.permit.put("explicit_native_urb_reception", true); approval.rfIn(true);
        approval.consume(limits, 1); int before = reads[0];
        approval.requireCurrent(1); assertEquals(before, reads[0]);
        now[0] = 3000; assertThrows(IOException.class, approval::requireCurrent);
        beat[0] = now[0]; approval.requireCurrent(1);
        live[0] = false; assertThrows(IOException.class, approval::requireCurrent);
        assertEquals(before, reads[0]);
    }
    @Test public void sonyReceiveScopeHasDistinctTranscriptAndCannotReuseOrdinaryNativePermit() throws Exception {
        Storage store=new Storage();
        Tbs5530TsSmoke.Limits sony=new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,false,true);
        Tbs5530TsSmoke.Limits ordinary=new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        Tbs5530OperationalApproval approval=new Tbs5530OperationalApproval(store,() -> 1000,null,sony,1);
        store.authorize();store.permit.put("explicit_native_urb_reception",true);approval.rfIn(true);
        assertTrue(store.permit.getString("transcript").startsWith("TBS5530-TS-SMOKE/v8|"));
        assertTrue(store.permit.getString("transcript").contains("read81=SOURCE_SERIAL_RMW_NOT_PARALLEL_PROOF"));
        assertFalse(approval.eligible());assertFalse(approval.sonyTsOutputReadbackApproved());
        store.permit.put("explicit_sony_ts_output_readback","true");assertFalse(approval.eligible());
        store.permit.put("explicit_sony_ts_output_readback",true);assertTrue(approval.eligible());assertTrue(approval.sonyTsOutputReadbackApproved());
        assertThrows(IOException.class,() -> approval.consume(ordinary,1));approval.consume(sony,1);
        assertThrows(IOException.class,() -> approval.consume(sony,1));
        store.permit.put("explicit_sony_ts_output_readback",false);assertThrows(IOException.class,approval::requireCurrent);
    }
    @Test public void sonyRootScopeIsDefaultOffAndRejectsChangedOrCombinedExperimentalGrant() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession off=new Tbs5530AutonomousSession(context,"SYNC_NATIVE_ERRNO","SM-X926B",36,46);assertFalse(off.sonyTsOutputReadback);
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,false,false,null,false,true));
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,true,true,null,false,true));
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X8192","SM-X926B",36,46,true,false,null,false,true));
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,true,false,"33333333-3333-4333-8333-333333333333",true,true));
        Tbs5530AutonomousSession on=new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,true,false,null,false,true);
        JSONObject grant=new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(),"autonomous-v1-challenge.json"),16384));
        grant.put("profile","EXPERIMENTAL_DIAGNOSTIC").put("bounded_user_authorization",true).put("rf_in_confirmed",true)
                .put("expires_elapsed_ms",on.policy.created()+300000).put("physical_recovery_epoch","44444444-4444-4444-8444-444444444444").put("physical_recovery_confirmed",true);
        String layout=grant.getString("sony_readback_layout");
        grant.put("sony_ts_output_readback",false);on.write("grant.json",grant.toString());assertThrows(IOException.class,on::acceptGrant);
        grant.put("sony_ts_output_readback",true).put("sony_readback_layout",layout+"MUTATED");on.write("grant.json",grant.toString());assertThrows(IOException.class,on::acceptGrant);
        grant.put("sony_readback_layout",layout);on.write("grant.json",grant.toString());
        on.write("heartbeat.json",new JSONObject().put("session",on.id).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()).put("stop",false).toString());
        on.policy.foreground(true);on.acceptGrant();on.require();assertEquals(0,on.controlMirror.count());
        String ledger=Tbs5530AutonomousSession.read(new java.io.File(on.directory,"ledger.jsonl"),4096);assertFalse(ledger.contains("ATTEMPT_CONSUMED"));assertFalse(ledger.contains("FIRMWARE_CONSUMED"));
        grant.put("sony_ts_output_readback",false);on.write("grant.json",grant.toString());assertThrows(IOException.class,on::require);on.policy.stop();on.controlMirror.finish();
    }
    @Test public void sonyStagePermitBindsPlanIdentityConsumesOnceAndFailsClosedOnClockOrOwnerLoss() throws Exception {
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530SonyTsOutputReadback.Plan plan=info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.sonyPlan(info.martinmarinov.drivers.usb.tbs5530.Tbs5530SonyTsOutputReadback.Stage.AFTER_LOCK);
        final class Port implements Tbs5530SonyReadbackApproval.Port {
            final Map<String,String> files=new HashMap<>();long now=1000;int consumes;boolean current=true;
            public void requireCurrent() throws IOException {if(!current)throw new IOException("Synthetic owner loss");}
            public void consume(String attempt,String hash,long maximum) {assertEquals(plan.getSha256(),hash);assertEquals(5000,maximum);consumes++;}
            public void write(String name,String text) {files.put(name,text);}
            public long now(){return now;}
        }
        Port port=new Port();String owner="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Tbs5530SonyReadbackApproval approval=new Tbs5530SonyReadbackApproval(plan,owner,port);assertEquals(1,port.files.size());
        assertThrows(IOException.class,() -> approval.audit("SONY_READBACK_INTENT",1,64,144,3,0));
        assertThrows(IOException.class,() -> approval.consume(info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.sonyPlan(plan.stage)));assertEquals(0,port.consumes);
        approval.consume(plan);assertEquals(1,port.consumes);assertThrows(IOException.class,() -> approval.consume(plan));
        approval.audit("SONY_READBACK_INTENT",1,64,144,3,Integer.MIN_VALUE);approval.audit("SONY_READBACK_COMPLETE",1,64,144,3,3);assertEquals(2,port.files.size());
        assertThrows(IOException.class,() -> approval.audit("SONY_READBACK_INTENT",21,64,144,3,0));
        port.now=11000;assertThrows(IOException.class,approval::requireCurrent);port.now=999;assertThrows(IOException.class,approval::requireCurrent);
        port.now=1000;port.current=false;assertThrows(IOException.class,approval::requireCurrent);assertEquals(1,port.consumes);
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
    @Test public void nativeUrbCannotUseSyncOrJavaQueuePermission() throws Exception {
        Storage store = new Storage();
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        Tbs5530OperationalApproval nativeUrb = new Tbs5530OperationalApproval(store, () -> 1000, null, limits, 1);
        store.authorize(); store.permit.put("explicit_queued_reception", true); nativeUrb.rfIn(true);
        assertFalse(nativeUrb.eligible()); assertFalse(nativeUrb.nativeUrbReceptionApproved());
        assertTrue(store.permit.getString("transcript").startsWith("TBS5530-TS-SMOKE/v6|"));
        assertTrue(store.permit.getString("transcript").contains("errorPayload=METADATA_ONLY"));
        store.permit.put("explicit_native_urb_reception", true);
        assertTrue(nativeUrb.eligible()); assertTrue(nativeUrb.nativeUrbReceptionApproved());
        assertFalse(nativeUrb.queuedReceptionApproved()); nativeUrb.consume(limits, 1);
        assertThrows(IOException.class, () -> nativeUrb.consume(limits, 1));
        store.permit.put("explicit_native_urb_reception", false);
        assertThrows(IOException.class, nativeUrb::requireCurrent);
    }
    @Test public void native8192NeedsDistinctExactPlanAndCannotReuse4096Permit() throws Exception {
        Storage smallStore = new Storage(), largeStore = new Storage();
        Tbs5530TsSmoke.Limits small = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        Tbs5530TsSmoke.Limits large = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 8192, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192);
        Tbs5530OperationalApproval a = new Tbs5530OperationalApproval(smallStore, () -> 1000, null, small, 1);
        Tbs5530OperationalApproval b = new Tbs5530OperationalApproval(largeStore, () -> 1000, null, large, 1);
        smallStore.authorize(); smallStore.permit.put("explicit_native_urb_reception", true); a.rfIn(true);
        largeStore.authorize(); b.rfIn(true);
        assertFalse(b.eligible()); largeStore.permit.put("explicit_native_urb_reception", true); assertTrue(b.eligible());
        JSONObject smallChallenge = new JSONObject(smallStore.files.get("phase31-challenge.json"));
        JSONObject largeChallenge = new JSONObject(largeStore.files.get("phase31-challenge.json"));
        assertNotEquals(smallChallenge.getString("plan_sha256"), largeChallenge.getString("plan_sha256"));
        assertTrue(largeChallenge.getString("transcript").contains("USBDEVFS_URB_8X8192"));
        assertThrows(IOException.class, () -> a.consume(large, 1));
        assertThrows(IOException.class, () -> b.consume(small, 1));
        b.consume(large, 1); assertThrows(IOException.class, () -> b.consume(large, 1));
    }
    @Test public void explicitAlt0NeedsSeparatePlanPermitAndExactBaseline() throws Exception {
        Storage offStore = new Storage(), onStore = new Storage();
        Tbs5530TsSmoke.Limits off = new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        Tbs5530TsSmoke.Limits on = new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true);
        Tbs5530OperationalApproval a = new Tbs5530OperationalApproval(offStore,() -> 1000,null,off,1);
        Tbs5530OperationalApproval b = new Tbs5530OperationalApproval(onStore,() -> 1000,null,on,1);
        offStore.authorize(); onStore.authorize();
        offStore.permit.put("explicit_native_urb_reception",true); onStore.permit.put("explicit_native_urb_reception",true);
        a.rfIn(true); b.rfIn(true);
        assertTrue(a.eligible()); assertFalse(a.explicitSetInterfaceAlt0Approved());
        assertNotEquals(a.planSha256(),b.planSha256());
        assertTrue(onStore.permit.getString("transcript").startsWith("TBS5530-TS-SMOKE/v7|"));
        assertTrue(onStore.permit.getString("transcript").contains("EXPLICIT_SET_INTERFACE_ALT0"));
        onStore.permit.remove("explicit_set_interface_alt0"); assertFalse(b.eligible());
        onStore.permit.put("explicit_set_interface_alt0","true"); assertFalse(b.eligible());
        onStore.permit.put("explicit_set_interface_alt0",true); assertTrue(b.eligible()); assertTrue(b.explicitSetInterfaceAlt0Approved());
        assertThrows(IOException.class,() -> b.consume(off,1)); assertThrows(IOException.class,() -> a.consume(on,1));
        b.consume(on,1); assertThrows(IOException.class,() -> b.consume(on,1));
        onStore.permit.put("explicit_set_interface_alt0",false); assertThrows(IOException.class,b::requireCurrent);
    }
    @Test public void explicitAlt0DefaultOffGrantMismatchAndMutationCannotAuthorizeUsb() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession off = new Tbs5530AutonomousSession(context,"SYNC_NATIVE_ERRNO","SM-X926B",36,46);
        assertFalse(off.explicitSetInterfaceAlt0);
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X8192","SM-X926B",36,46,false,true));
        Tbs5530AutonomousSession on = new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,false,true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(),"autonomous-v1-challenge.json"),16384));
        grant.put("profile","EXPERIMENTAL_DIAGNOSTIC").put("bounded_user_authorization",true).put("rf_in_confirmed",true)
                .put("expires_elapsed_ms",on.policy.created()+300000).put("physical_recovery_epoch","33333333-3333-4333-8333-333333333333")
                .put("physical_recovery_confirmed",true).put("explicit_set_interface_alt0",false);
        on.write("grant.json",grant.toString()); assertThrows(IOException.class,on::acceptGrant);
        grant.put("explicit_set_interface_alt0",true); on.write("grant.json",grant.toString());
        on.write("heartbeat.json",new JSONObject().put("session",on.id).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()).put("stop",false).toString());
        on.policy.foreground(true); on.acceptGrant(); on.require();
        String ledger = Tbs5530AutonomousSession.read(new java.io.File(on.directory,"ledger.jsonl"),4096);
        assertTrue(ledger.contains("EXPLICIT_SET_INTERFACE_ALT0")); assertFalse(ledger.contains("ATTEMPT_CONSUMED")); assertFalse(ledger.contains("FIRMWARE_CONSUMED"));
        grant.put("explicit_set_interface_alt0",false); on.write("grant.json",grant.toString()); assertThrows(IOException.class,on::require);
        on.policy.stop();
    }
    @Test public void controlMirrorIsOffByDefaultAndMustMatchImmutableGrantBeforeAnyUsb() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession off = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "SM-X926B", 36, 46);
        assertFalse(off.controlMirrorEnabled); assertNull(off.controlMirror);
        Tbs5530AutonomousSession on = new Tbs5530AutonomousSession(context, "NATIVE_USBFS_URB_8X8192", "SM-X926B", 36, 46, true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        grant.put("control_mirror", false).put("profile", "EXPERIMENTAL_DIAGNOSTIC")
                .put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", on.policy.created() + 300000);
        on.write("grant.json", grant.toString());
        assertThrows(IOException.class, on::acceptGrant); assertNull(on.controlMirror);
        grant.put("control_mirror", true).put("physical_recovery_epoch", "22222222-2222-4222-8222-222222222222")
                .put("physical_recovery_confirmed", true);
        on.write("grant.json", grant.toString());
        on.write("heartbeat.json", new JSONObject().put("session", on.id).put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
        on.policy.foreground(true); on.acceptGrant(); assertNotNull(on.controlMirror);
        assertEquals(0, on.controlMirror.count()); on.controlMirror.finish(); assertTrue(on.controlMirror.durable());
        String ledger = Tbs5530AutonomousSession.read(new java.io.File(on.directory, "ledger.jsonl"), 1024);
        assertEquals(1, ledger.trim().split("\\n").length);
        assertEquals("SESSION_GRANTED", new JSONObject(ledger).getString("event"));
        assertFalse(ledger.contains("ATTEMPT_CONSUMED")); assertFalse(ledger.contains("FIRMWARE_CONSUMED"));
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
    @Test public void insufficientUploadBudgetDoesNotConsumeAttachmentHistory() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File history = new java.io.File(context.getFilesDir(), "autonomous-v1-attachments.txt");
        String before = history.exists() ? Tbs5530AutonomousSession.read(history, 1024 * 1024) : null;
        Map<String, ?> marker = new HashMap<>(context.getSharedPreferences("autonomous-v1-history", 0).getAll());
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "GM1900", 30, 1);
        session.policy.foreground(true);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new java.io.File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        grant.put("profile", "AUTO_VERIFIED").put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", session.policy.created() + 10000);
        session.write("grant.json", grant.toString());
        session.write("heartbeat.json", new JSONObject().put("session", session.id).put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
        session.acceptGrant();
        session.policy.observe("SYNTHETIC_TEST_FIXTURE", 1, true,
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530OperationalProfile.Kind.FX2_DEFAULT);
        assertThrows(IOException.class, () -> session.consumeFirmwareAttachment("SYNTHETIC_TEST_FIXTURE"));
        assertEquals(before, history.exists() ? Tbs5530AutonomousSession.read(history, 1024 * 1024) : null);
        assertEquals(marker, context.getSharedPreferences("autonomous-v1-history", 0).getAll());
        assertTrue(session.preNativeRefusal); assertEquals("UPLOAD_PREFLIGHT_REFUSED", session.stopReason);
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
