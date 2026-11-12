// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530FrontendExperiment;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

/** Private synthetic grants only; never opens USB or consumes a physical attachment. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530FrontendExperimentApprovalTest {
    @Test public void manualScopeBindsSelectionGoldenAndExperimentalGrantBeforeAnyIo() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession s = session(context, "MANUAL_PLP0_K36");
        assertEquals(Tbs5530FrontendExperiment.MANUAL_PLP0_K36,s.frontendExperiment);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new File(context.getFilesDir(),"autonomous-v1-challenge.json"),16384));
        String hash = grant.getString("golden_sha256");
        String apkHash = grant.getString("app_apk_sha256");
        assertTrue(apkHash.matches("[a-f0-9]{64}"));
        assertEquals(BuildConfig.SOURCE_GIT_SHA,grant.getString("source_git_sha"));
        grant.put("profile","EXPERIMENTAL_DIAGNOSTIC").put("bounded_user_authorization",true).put("rf_in_confirmed",true)
                .put("expires_elapsed_ms",s.policy.created()+300000).put("physical_recovery_epoch","44444444-4444-4444-8444-444444444444")
                .put("physical_recovery_confirmed",true);
        try {
            grant.put("frontend_experiment","BASELINE_AUTO"); s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("frontend_experiment","MANUAL_PLP0_K36").put("golden_sha256",new String(new char[64]).replace('\0','a'));
            s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("golden_sha256",hash).put("app_apk_sha256",new String(new char[64]).replace('\0','b'));
            s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("app_apk_sha256",apkHash).put("profile","AUTO_VERIFIED");
            s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("profile","EXPERIMENTAL_DIAGNOSTIC"); s.write("grant.json",grant.toString());
            s.write("heartbeat.json",new JSONObject().put("session",s.id).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()).put("stop",false).toString());
            s.policy.foreground(true); s.acceptGrant(); s.require();
            assertThrows(IOException.class,() -> s.policy.requireOwner("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1,false));
            String ledger = Tbs5530AutonomousSession.read(new File(s.directory,"ledger.jsonl"),4096);
            assertFalse(ledger.contains("ATTEMPT_CONSUMED")); assertFalse(ledger.contains("FIRMWARE_CONSUMED"));
            assertEquals(0,s.controlMirror.count());
            grant.put("frontend_experiment","BASELINE_AUTO");s.write("grant.json",grant.toString());assertThrows(IOException.class,s::require);
        } finally { s.policy.stop(); if(s.controlMirror!=null)s.controlMirror.finish(); }
    }
    @Test public void defaultIsAutoAndUnsafeOrUnknownCandidatesAreRejectedBeforeNamespaceCreation() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession off = session(c,null); assertEquals(Tbs5530FrontendExperiment.BASELINE_AUTO,off.frontendExperiment);
        assertThrows(IOException.class,() -> session(c,"TS_DRIVE_55_55"));
        assertThrows(IOException.class,() -> session(c,"MANUAL_PLP1_K36"));
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(c,"NATIVE_USBFS_URB_8X8192","SM-X926B",36,46,
                true,false,null,false,false,"MANUAL_PLP0_K36"));
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(c,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,
                true,false,null,false,true,"MANUAL_PLP0_K36"));
    }
    @Test public void b3UsesSessionJournalWithoutReadingLegacyHistoryAndBindsQueue() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession s = new Tbs5530AutonomousSession(c, "NATIVE_USBFS_STREAM", "SM-X926B", 36, 46,
                true, false, null, false, false, "B2_RELOAD_AFTER_SET_CONFIG", () -> { throw new IOException("Legacy history unavailable"); });
        s.configureStream(32, 16384);
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new File(c.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
        assertTrue(grant.isNull("attachment_history_sha256"));
        assertEquals(32, grant.getInt("b3_queue_depth"));
        grant.put("profile", "EXPERIMENTAL_DIAGNOSTIC").put("bounded_user_authorization", true).put("rf_in_confirmed", true)
                .put("expires_elapsed_ms", s.policy.created() + 300000).put("physical_recovery_epoch", "44444444-4444-4444-8444-444444444444")
                .put("physical_recovery_confirmed", true).put("host_script_sha256", new String(new char[64]).replace('\0', 'a'));
        try {
            grant.put("b3_queue_depth", 16); s.write("grant.json", grant.toString()); assertThrows(IOException.class, s::acceptGrant);
            grant.put("b3_queue_depth", 32); s.write("grant.json", grant.toString());
            s.write("heartbeat.json", new JSONObject().put("session", s.id).put("elapsed_ms", android.os.SystemClock.elapsedRealtime()).put("stop", false).toString());
            s.policy.foreground(true); s.acceptGrant(); s.require();
            assertThrows(IOException.class, () -> s.configureStream(16, 16384));
            assertEquals(0, s.controlMirror.count());
            s.report("SYNTHETIC_NO_IO", 0, 0, -1);
            JSONObject report = new JSONObject(Tbs5530AutonomousSession.read(new File(s.directory, "report.json"), 16384));
            assertTrue(report.getString("history_guard_warning").contains("LEGACY_HISTORY_NOT_AUTHORITY"));
            assertFalse(report.getBoolean("ts_smoke_pass"));
            assertFalse(new File(s.directory, "b3-reload-journal.txt").exists());
        } finally { s.policy.stop(); if (s.controlMirror != null) s.controlMirror.finish(); }
    }
    @Test public void b3RejectsUnchangedApiWithWrongPipelineHostOrReceiveBounds() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(c, "NATIVE_USBFS_STREAM", "SM-X926B", 36, 46,
                true, false, null, false, false, "MANUAL_PLP0_K36"));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(c, "NATIVE_USBFS_STREAM", "GM1900", 30, 46,
                true, false, null, false, false, "B2_RELOAD_AFTER_SET_CONFIG"));
        Tbs5530AutonomousSession s = new Tbs5530AutonomousSession(c, "NATIVE_USBFS_STREAM", "SM-X926B", 36, 46,
                true, false, null, false, false, "B2_RELOAD_AFTER_SET_CONFIG");
        assertThrows(IOException.class, () -> s.configureStream(33, 16384));
        assertThrows(IOException.class, () -> s.configureStream(16, 16385));
        s.policy.stop();
    }
    private static byte[] syntheticHistory() {
        return ("FIRMWARE_CONSUMED " + new String(new char[64]).replace('\0','c') + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test public void historyHashMustBePinnedByGrantAndRollbackStopsSessionBeforeIo() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final byte[][] history = { syntheticHistory() };
        Tbs5530AutonomousSession s = new Tbs5530AutonomousSession(c,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,
                true,false,null,false,false,"MANUAL_PLP0_K36",() -> history[0].clone());
        JSONObject grant = new JSONObject(Tbs5530AutonomousSession.read(new File(c.getFilesDir(),"autonomous-v1-challenge.json"),16384));
        String pinned = grant.getString("attachment_history_sha256");
        grant.put("profile","EXPERIMENTAL_DIAGNOSTIC").put("bounded_user_authorization",true).put("rf_in_confirmed",true)
                .put("expires_elapsed_ms",s.policy.created()+300000).put("physical_recovery_epoch","44444444-4444-4444-8444-444444444444")
                .put("physical_recovery_confirmed",true);
        try {
            grant.remove("attachment_history_sha256"); s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("attachment_history_sha256",new String(new char[64]).replace('\0','a'));
            s.write("grant.json",grant.toString()); assertThrows(IOException.class,s::acceptGrant);
            grant.put("attachment_history_sha256",pinned); s.write("grant.json",grant.toString());
            s.write("heartbeat.json",new JSONObject().put("session",s.id).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()).put("stop",false).toString());
            s.policy.foreground(true); s.acceptGrant(); s.require();
            history[0] = syntheticHistory(); history[0][18] = 'b';
            assertThrows(IOException.class,s::require); assertEquals(0,s.controlMirror.count());
            history[0] = syntheticHistory(); assertThrows(IOException.class,s::require);
        } finally { s.policy.stop(); if(s.controlMirror!=null)s.controlMirror.finish(); }
    }
    private static Tbs5530AutonomousSession session(Context c,String experiment) throws IOException {
        return new Tbs5530AutonomousSession(c,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,true,false,null,false,false,experiment,
                () -> syntheticHistory());
    }
}
