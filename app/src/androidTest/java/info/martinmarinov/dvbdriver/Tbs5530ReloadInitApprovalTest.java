// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ReloadLedger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic grants and cache-only exclusive files. Never initializes no_backup or touches a real history. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530ReloadInitApprovalTest {
    private static final class Rig implements Tbs5530ReloadInitPermit.Port {
        long now=1000; boolean foreground=true, consumed, failConsume;
        String record; int tombstones, creates;
        JSONObject challenge,grant,beat;
        Tbs5530ReloadInitPermit permit;
        Rig() throws Exception {
            challenge=new JSONObject().put("protocol",Tbs5530ReloadInitPlan.PROTOCOL).put("session","fake-session").put("launch_token","fake-launch")
                    .put("model","SM-X926B").put("api",36).put("boot_count",1).put("apk_sha256",repeat('a',64)).put("source_git_sha",repeat('b',40))
                    .put("plan_sha256",Tbs5530ReloadInitPlan.sha256()).put("history_sha256",Tbs5530ReloadInitPlan.HISTORY).put("history_bytes",996).put("history_records",12)
                    .put("store_sha256",Tbs5530ReloadInitPlan.STORE).put("anchor_sha256",Tbs5530ReloadInitPlan.ANCHOR).put("marker_sha256",Tbs5530ReloadInitPlan.MARKER)
                    .put("store_bytes",138).put("anchor_bytes",94).put("marker_bytes",99).put("created_ms",1000);
            grant=new JSONObject(challenge.toString()).put("first_initialization_authorized",true).put("no_tuner_confirmed",true)
                    .put("hardware_authorized",false).put("retry",0).put("host_script_sha256",repeat('c',64)).put("expires_ms",121000);
            beat=new JSONObject().put("session","fake-session").put("elapsed_ms",1000).put("stop",false);
            permit=new Tbs5530ReloadInitPermit(challenge,this);
        }
        public long now(){return now;} public boolean foreground(){return foreground;}
        public String grant(){return grant.toString();} public String heartbeat(){return beat.toString();}
        public boolean consumed(){return consumed;}
        public void consume(String value) throws IOException {
            if(consumed||failConsume)throw new IOException("Synthetic fsync failure");record=value;consumed=true;tombstones++;
        }
        public void requireConsumed(String expected) throws IOException {if(!consumed||!expected.equals(record))throw new IOException("Tombstone missing/changed");}
        void createFirst() throws IOException {
            permit.requireExact(Tbs5530ReloadInitPlan.STORE,Tbs5530ReloadInitPlan.ANCHOR,Tbs5530ReloadInitPlan.MARKER,Tbs5530ReloadInitPlan.HISTORY);
            permit.consume();assertTrue(consumed);permit.requireCurrent();creates++;
        }
    }
    @Test public void exactNoTunerGrantConsumesBeforeCreateAndCannotRearm() throws Exception {
        Rig r=new Rig();r.permit.accept();r.createFirst();assertEquals(1,r.tombstones);assertEquals(1,r.creates);
        assertThrows(IOException.class,r::createFirst);assertEquals(1,r.creates);
        Rig failed=new Rig();failed.permit.accept();failed.failConsume=true;assertThrows(IOException.class,failed::createFirst);assertEquals(0,failed.creates);
    }
    @Test public void everyPinHardwareAuthorizationOrTemplateChangeRefusesBeforeWrites() throws Exception {
        for(String key:Tbs5530ReloadInitPermit.KEYS){Rig r=new Rig();r.grant.put(key,"changed");assertThrows(IOException.class,r.permit::accept);assertEquals(0,r.tombstones);}
        for(String key:new String[]{"first_initialization_authorized","no_tuner_confirmed","hardware_authorized"}){
            Rig r=new Rig();r.grant.put(key,!r.grant.getBoolean(key));assertThrows(IOException.class,r.permit::accept);assertEquals(0,r.creates);
        }
        Rig retry=new Rig();retry.grant.put("retry",1);assertThrows(IOException.class,retry.permit::accept);
        Rig marker=new Rig();marker.permit.accept();assertThrows(IOException.class,()->marker.permit.requireExact(Tbs5530ReloadInitPlan.STORE,
                Tbs5530ReloadInitPlan.ANCHOR,repeat('d',64),Tbs5530ReloadInitPlan.HISTORY));assertEquals(0,marker.tombstones);
    }
    @Test public void heartbeatClockForegroundAndMissingTombstoneRemainTerminal() throws Exception {
        for(int mode=0;mode<6;mode++){
            Rig r=new Rig();r.permit.accept();
            switch(mode){case 0:r.now=3000;break;case 1:r.now=999;break;case 2:r.foreground=false;break;case 3:r.beat.put("stop",true);break;
                case 4:r.grant.put("expires_ms",122000);break;default:r.permit.consume();r.record="damaged";}
            assertThrows(IOException.class,r.permit::requireCurrent);r.now=1000;r.foreground=true;r.beat.put("stop",false);
            assertThrows(IOException.class,r.permit::requireCurrent);
        }
    }
    @Test public void exclusiveTombstoneAndCreatePreservePartialFilesInsteadOfResetting() throws Exception {
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"reload-init-fixture-"+UUID.randomUUID());assertTrue(root.mkdir());
        try{
            Tbs5530ReloadInitJournal j=new Tbs5530ReloadInitJournal(root);assertFalse(j.consumed());j.consume("{\"synthetic\":true}");
            assertTrue(new Tbs5530ReloadInitJournal(root).consumed());assertThrows(IOException.class,()->j.consume("{}"));j.requireRecord("{\"synthetic\":true}");
            File tombstone=new File(root,Tbs5530ReloadInitPlan.TOMBSTONE);try(FileOutputStream out=new FileOutputStream(tombstone)){out.getFD().sync();}
            assertTrue(j.consumed());assertThrows(IOException.class,()->j.requireRecord("{\"synthetic\":true}"));assertThrows(IOException.class,()->j.consume("{}"));
            Method create=Tbs5530ReloadStore.class.getDeclaredMethod("createExclusive",File.class,byte[].class);create.setAccessible(true);
            File file=new File(root,"synthetic-header.txt");byte[] bytes=Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER);create.invoke(null,file,bytes);
            assertArrayEquals(bytes,Tbs5530ReloadStore.readExisting(file));
            InvocationTargetException error=assertThrows(InvocationTargetException.class,()->create.invoke(null,file,new byte[]{1}));assertTrue(error.getCause() instanceof IOException);
            assertArrayEquals(bytes,Tbs5530ReloadStore.readExisting(file));
        }finally{File[] files=root.listFiles();if(files!=null)for(File f:files)assertTrue(f.delete());assertTrue(root.delete());}
    }
    @Test public void emulatorCallerRefusesBeforeStoreOrTombstoneAndHasNoHardwareGrant() throws Exception {
        org.junit.Assume.assumeTrue("Emulator-only initialization refusal",android.os.Build.MODEL.startsWith("sdk_"));
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context c=inst.getTargetContext();String launch=UUID.randomUUID().toString();
        android.content.Intent request=new android.content.Intent(c,Tbs5530ReloadInitActivity.class).putExtra("launch_token",launch).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        Tbs5530ReloadInitActivity activity=(Tbs5530ReloadInitActivity)inst.startActivitySync(request);
        try{inst.waitForIdleSync();JSONObject report=new JSONObject(Tbs5530AutonomousSession.read(new File(c.getFilesDir(),"reload-init-v1-report.json"),8192));
            assertEquals(launch,report.getString("launch_token"));assertEquals("REFUSED_BEFORE_CREATE",report.getString("result"));assertFalse(report.getBoolean("consumed"));
            assertFalse(report.getBoolean("hardware_authorized"));assertEquals(0,report.getInt("usb_io_count"));
        }finally{inst.runOnMainSync(activity::finish);inst.waitForIdleSync();}
    }
    private static String repeat(char c,int count){char[] chars=new char[count];java.util.Arrays.fill(chars,c);return new String(chars);}
}
