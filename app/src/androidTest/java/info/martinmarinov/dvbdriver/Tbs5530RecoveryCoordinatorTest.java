// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530HistoryRecovery;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic grants and private UUID cache journal fixtures only, no actual firmware history or USB. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530RecoveryCoordinatorTest {
    static final String PLAN=repeat('a',64);
    private static final class Rig implements Tbs5530RecoveryPermit.Port {
        long now=1000;boolean foreground=true,consumed,failConsume;int writes;
        JSONObject challenge,grant,beat;
        Tbs5530RecoveryPermit permit;
        Rig()throws Exception{
            challenge=new JSONObject().put("protocol","TBS5530-HISTORY-APPLY/v1").put("session","fixture-session")
                .put("launch_token","fixture-launch").put("model","SM-X926B").put("api",36).put("boot_count",47)
                .put("apk_sha256",repeat('b',64)).put("source_git_sha",repeat('c',40)).put("plan_sha256",PLAN)
                .put("before_sha256",repeat('d',64)).put("after_sha256",repeat('e',64))
                .put("before_bytes",581).put("after_bytes",747).put("created_ms",1000);
            grant=new JSONObject(challenge.toString()).put("append_only_authorized",true).put("no_tuner_confirmed",true)
                .put("hardware_authorized",false).put("full_history_proven",false).put("host_script_sha256",repeat('f',64))
                .put("expires_ms",121000);
            beat=new JSONObject().put("session","fixture-session").put("elapsed_ms",1000).put("stop",false);
            permit=new Tbs5530RecoveryPermit(challenge,this);
        }
        public long now(){return now;}public boolean foreground(){return foreground;}
        public String grant(){return grant.toString();}public String heartbeat(){return beat.toString();}
        public boolean consumed(){return consumed;}
        public void consume(String record)throws IOException{if(consumed||failConsume)throw new IOException("Synthetic consume failure");writes++;consumed=true;}
    }
    @Test public void exactScopeConsumesOnceAndChangedOrMissingConsumeCannotRearm()throws Exception{
        Rig r=new Rig();r.permit.accept();r.permit.consume(PLAN);assertEquals(1,r.writes);
        assertThrows(IOException.class,()->r.permit.consume(PLAN));assertEquals(1,r.writes);
        Rig missing=new Rig();missing.permit.accept();missing.permit.consume(PLAN);missing.consumed=false;
        assertThrows(IOException.class,missing.permit::requireCurrent);missing.consumed=true;
        assertThrows(IOException.class,missing.permit::requireCurrent);
    }
    @Test public void everyImmutableFieldAndHardwareOrFalseHistoryProofRejectBeforeWrites()throws Exception{
        for(String field:new String[]{"protocol","session","launch_token","model","api","boot_count","apk_sha256","source_git_sha",
                "plan_sha256","before_sha256","after_sha256","before_bytes","after_bytes","created_ms"}){
            Rig r=new Rig();r.grant.put(field,"MUTATED");assertThrows(IOException.class,r.permit::accept);assertEquals(0,r.writes);
        }
        for(String field:new String[]{"append_only_authorized","no_tuner_confirmed","hardware_authorized","full_history_proven"}){
            Rig r=new Rig();r.grant.put(field,!r.grant.getBoolean(field));assertThrows(IOException.class,r.permit::accept);assertEquals(0,r.writes);
        }
    }
    @Test public void witnessedModeBindsProtocolPlanAndAfterSizeWithoutGrantingHardware()throws Exception{
        Rig r=new Rig();String plan=Tbs5530HistoryRecovery.witnessPlanSha256();
        for(JSONObject object:new JSONObject[]{r.challenge,r.grant})object.put("protocol","TBS5530-HISTORY-WITNESS-APPLY/v2")
                .put("plan_sha256",plan).put("after_sha256",Tbs5530HistoryRecovery.WITNESS_AFTER).put("after_bytes",996);
        r.permit=new Tbs5530RecoveryPermit(r.challenge,r);r.permit.accept();r.permit.consume(plan);
        assertEquals(1,r.writes);assertFalse(r.grant.getBoolean("hardware_authorized"));assertFalse(r.grant.getBoolean("full_history_proven"));
        Rig mismatch=new Rig();mismatch.challenge.put("protocol","TBS5530-HISTORY-WITNESS-APPLY/v2").put("after_bytes",996);
        mismatch.permit=new Tbs5530RecoveryPermit(mismatch.challenge,mismatch);
        assertThrows(IOException.class,mismatch.permit::accept);assertEquals(0,mismatch.writes);
    }
    @Test public void heartbeatStopStaleClockForegroundAndGrantMutationAreTerminal()throws Exception{
        for(int fault=0;fault<6;fault++){
            Rig r=new Rig();r.permit.accept();
            switch(fault){case 0:r.now=3000;break;case 1:r.now=999;break;case 2:r.foreground=false;break;
                case 3:r.beat.put("stop",true);break;case 4:r.grant.put("expires_ms",122000);break;default:r.beat.put("session","other");}
            assertThrows(IOException.class,r.permit::requireCurrent);r.now=1000;r.foreground=true;r.beat.put("stop",false).put("session","fixture-session");
            assertThrows(IOException.class,r.permit::requireCurrent);assertEquals(0,r.writes);
        }
    }
    @Test public void exclusiveJournalSurvivesReopenAndEmptyTombstoneBlocksRetry()throws Exception{
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"recovery-journal-fixture-"+UUID.randomUUID());
        assertTrue(root.mkdir());File directory=new File(root,"tbs5530-history-recovery-v1");
        try{
            Tbs5530RecoveryJournal j=new Tbs5530RecoveryJournal(root);assertFalse(j.consumed(PLAN));
            j.consume(PLAN,"{\"synthetic\":true}");assertTrue(new Tbs5530RecoveryJournal(root).consumed(PLAN));
            assertThrows(IOException.class,()->j.consume(PLAN,"{}"));
            String emptyPlan=repeat('e',64);File empty=new File(directory,emptyPlan+".consumed.json");assertTrue(empty.createNewFile());
            assertTrue(new Tbs5530RecoveryJournal(root).consumed(emptyPlan));assertThrows(IOException.class,()->j.consume(emptyPlan,"{}"));
        }finally{File[] files=directory.listFiles();if(files!=null)for(File f:files)assertTrue(f.delete());assertTrue(directory.delete());assertTrue(root.delete());}
    }
    @Test public void durableConsumeFailureStopsCoreBeforeAppend()throws Exception{
        Rig r=new Rig();r.permit.accept();r.failConsume=true;
        byte[] before=record('1'),after=join(before,record('2'),record('3'));int[] appends={0};
        Tbs5530HistoryRecovery.Plan plan=Tbs5530HistoryRecovery.prepare(before,after,Tbs5530HistoryRecovery.hash(before),Tbs5530HistoryRecovery.hash(after));
        // A fresh synthetic permit is bound to this exact fixture plan, never a production plan.
        r=new Rig();r.challenge.put("plan_sha256",plan.sha256);r.grant.put("plan_sha256",plan.sha256);r.permit=new Tbs5530RecoveryPermit(r.challenge,r);r.permit.accept();r.failConsume=true;
        final Rig exact=r;
        Tbs5530HistoryRecovery core=new Tbs5530HistoryRecovery(plan,new Tbs5530HistoryRecovery.Port(){
            public void requireCurrent()throws IOException{exact.permit.requireCurrent();}public byte[] readCurrent(){return before.clone();}
            public void consume(String hash)throws IOException{exact.permit.consume(hash);}
            public void appendAndSync(String expected,byte[] suffix){appends[0]++;}
        });
        assertThrows(IOException.class,core::apply);assertEquals(0,appends[0]);assertEquals(0,exact.writes);
    }
    private static String repeat(char c,int count){char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
    @Test public void terminalRefusalCanPublishFreshLaunchButDuplicateTokenCannotReplaceIt()throws Exception{
        org.junit.Assume.assumeTrue("Emulator-only no-device lifecycle fixture",android.os.Build.MODEL.startsWith("sdk_"));
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();android.content.Context c=inst.getTargetContext();
        String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        android.content.Intent request=new android.content.Intent(c,Tbs5530HistoryRecoveryActivity.class).putExtra("launch_token",first)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        Tbs5530HistoryRecoveryActivity activity=(Tbs5530HistoryRecoveryActivity)inst.startActivitySync(request);
        try{
            inst.waitForIdleSync();File report=new File(c.getFilesDir(),"history-recovery-v1-report.json");
            JSONObject initial=new JSONObject(Tbs5530AutonomousSession.read(report,8192));assertEquals(first,initial.getString("launch_token"));
            assertEquals("REFUSED_BEFORE_APPEND",initial.getString("result"));assertFalse(initial.getBoolean("consumed"));
            inst.runOnMainSync(()->activity.onNewIntent(new android.content.Intent(c,Tbs5530HistoryRecoveryActivity.class).putExtra("launch_token",second)));
            String text=Tbs5530AutonomousSession.read(report,8192);JSONObject fresh=new JSONObject(text);assertEquals(second,fresh.getString("launch_token"));
            assertFalse(fresh.getBoolean("consumed"));assertEquals(0,fresh.getInt("usb_io_count"));
            inst.runOnMainSync(()->activity.onNewIntent(new android.content.Intent(c,Tbs5530HistoryRecoveryActivity.class).putExtra("launch_token",second)));
            assertEquals(text,Tbs5530AutonomousSession.read(report,8192));
        }finally{inst.runOnMainSync(activity::finish);inst.waitForIdleSync();}
    }
    @Test public void witnessedLaunchOnUnapprovedHostRefusesBeforeAnyConsume()throws Exception{
        org.junit.Assume.assumeTrue("Emulator-only witnessed launch refusal",android.os.Build.MODEL.startsWith("sdk_"));
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();android.content.Context c=inst.getTargetContext();
        String token=UUID.randomUUID().toString();
        android.content.Intent request=new android.content.Intent(c,Tbs5530HistoryRecoveryActivity.class).putExtra("launch_token",token)
                .putExtra("recovery_mode","EXACT_HOST_WITNESS_V2").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        Tbs5530HistoryRecoveryActivity activity=(Tbs5530HistoryRecoveryActivity)inst.startActivitySync(request);
        try{
            inst.waitForIdleSync();JSONObject report=new JSONObject(Tbs5530AutonomousSession.read(new File(c.getFilesDir(),"history-recovery-v1-report.json"),8192));
            assertEquals(token,report.getString("launch_token"));assertEquals("TBS5530-HISTORY-WITNESS-APPLY/v2",report.getString("protocol"));
            assertEquals(Tbs5530HistoryRecovery.witnessPlanSha256(),report.getString("plan_sha256"));assertFalse(report.getBoolean("consumed"));
            assertEquals("REFUSED_BEFORE_APPEND",report.getString("result"));assertEquals(0,report.getInt("usb_io_count"));
        }finally{inst.runOnMainSync(activity::finish);inst.waitForIdleSync();}
    }
    private static byte[] record(char c){char[] chars=new char[64];Arrays.fill(chars,c);return ("FIRMWARE_CONSUMED "+new String(chars)+"\n").getBytes(StandardCharsets.UTF_8);}
    private static byte[] join(byte[]...values){int n=0;for(byte[]b:values)n+=b.length;byte[]out=new byte[n];int at=0;for(byte[]b:values){System.arraycopy(b,0,out,at,b.length);at+=b.length;}return out;}
}
