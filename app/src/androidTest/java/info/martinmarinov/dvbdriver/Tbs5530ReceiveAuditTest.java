// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Cache-only observation audit faults and measurements. No grant, history, device or TS data. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530ReceiveAuditTest {
    private static File directory() {
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"receive-audit-fixture-"+UUID.randomUUID());
        assertTrue(root.mkdir());return root;
    }
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] read(File f)throws IOException{return new android.util.AtomicFile(f).readFully();}
    private static void cleanup(File root){for(File f:root.listFiles())assertTrue(f.delete());assertTrue(root.delete());}
    @Test public void startedMarkerWithoutCompleteNeverCertifiesCrashOrUndrainedLog()throws Exception{
        File root=directory(),file=new File(root,"audit.jsonl");
        try{
            Tbs5530ReceiveAudit log=new Tbs5530ReceiveAudit(file);log.write(bytes("a\n"));
            assertEquals("BUFFERING_UNCERTIFIED",log.state());assertTrue(new File(file+".started").isFile());
            assertFalse(file.exists());assertFalse(new File(file+".complete").exists());
            assertThrows(IOException.class,()->new Tbs5530ReceiveAudit(file).write(bytes("a\n")));
            assertThrows(IOException.class,()->log.finishAfterDrain(false));assertEquals("FAILED",log.state());
            assertThrows(IOException.class,()->log.finishAfterDrain(true));assertFalse(new File(file+".complete").exists());
        }finally{cleanup(root);}
    }
    @Test public void exactBufferedLogAndHashSealCommitOnlyAfterDrainOnce()throws Exception{
        File root=directory(),file=new File(root,"audit.jsonl");
        try{
            Tbs5530ReceiveAudit log=new Tbs5530ReceiveAudit(file);log.write(bytes("a\n"));log.write(bytes("a\nb\n"));
            log.finishAfterDrain(true);assertEquals("SYNCED_CLOSED",log.state());assertArrayEquals(bytes("a\nb\n"),read(file));
            String seal=new String(read(new File(file+".complete")),StandardCharsets.UTF_8);
            assertTrue(seal.contains(info.martinmarinov.drivers.usb.tbs5530.Tbs5530ReloadLedger.hash(bytes("a\nb\n"))));
            assertThrows(IOException.class,()->log.finishAfterDrain(true));
        }finally{cleanup(root);}
    }
    @Test public void prefixOverflowAndCommitFailureLatchWithoutCompleteOrRollback()throws Exception{
        for(int fault=0;fault<3;fault++){
            File root=directory(),file=new File(root,"audit.jsonl");
            try{
                Tbs5530ReceiveAudit log=new Tbs5530ReceiveAudit(file);log.write(bytes("a\n"));
                if(fault==0)assertThrows(IOException.class,()->log.write(bytes("b\n")));
                else if(fault==1){StringBuilder s=new StringBuilder("a\n");for(int i=0;i<64;i++)s.append("x\n");assertThrows(IOException.class,()->log.write(bytes(s.toString())));}
                else{assertTrue(file.createNewFile());assertThrows(IOException.class,()->log.finishAfterDrain(true));assertEquals(0,file.length());}
                assertEquals("FAILED",log.state());assertThrows(IOException.class,()->log.finishAfterDrain(true));
                assertFalse(new File(file+".complete").exists());assertTrue(new File(file+".started").exists());
            }finally{cleanup(root);}
        }
    }
    @Test public void preexistingSealAndOversizedBytesNeverCertifyOrOverwriteEvidence()throws Exception{
        for(int fault=0;fault<2;fault++){
            File root=directory(),file=new File(root,"audit.jsonl"),seal=new File(file+".complete");
            try{
                Tbs5530ReceiveAudit log=new Tbs5530ReceiveAudit(file);log.write(bytes("a\n"));
                if(fault==0){
                    try(FileOutputStream out=new FileOutputStream(seal)){out.write(bytes("preexisting\n"));out.getFD().sync();}
                    assertThrows(IOException.class,()->log.finishAfterDrain(true));
                    assertArrayEquals(bytes("preexisting\n"),read(seal));assertArrayEquals(bytes("a\n"),read(file));
                }else{
                    byte[] large=new byte[65537];java.util.Arrays.fill(large,(byte)'x');large[0]='a';large[1]='\n';large[large.length-1]='\n';
                    assertThrows(IOException.class,()->log.write(large));assertFalse(file.exists());assertFalse(seal.exists());
                }
                assertEquals("FAILED",log.state());assertThrows(IOException.class,()->log.finishAfterDrain(true));
            }finally{cleanup(root);}
        }
    }
    @Test public void benchmarkAvoidsFilesystemCommitsInsideBoundedReceiveButFinalBytesMatch()throws Exception{
        File root=directory(),old=new File(root,"snapshot.jsonl"),file=new File(root,"buffered.jsonl");
        try{
            Tbs5530ReceiveAudit log=new Tbs5530ReceiveAudit(file);StringBuilder s=new StringBuilder();long oldNs=0,newNs=0;
            for(int i=0;i<34;i++){
                s.append("{\"event\":\"SYNTHETIC_BOUNDARY\",\"ordinal\":").append(i).append("}\n");byte[] next=bytes(s.toString());
                long start=android.os.SystemClock.elapsedRealtimeNanos();Tbs5530DurableFile.write(old,next);oldNs+=android.os.SystemClock.elapsedRealtimeNanos()-start;
                start=android.os.SystemClock.elapsedRealtimeNanos();log.write(next);newNs+=android.os.SystemClock.elapsedRealtimeNanos()-start;
            }
            long finish=android.os.SystemClock.elapsedRealtimeNanos();log.finishAfterDrain(true);long finishNs=android.os.SystemClock.elapsedRealtimeNanos()-finish;
            assertArrayEquals(read(old),read(file));android.os.Bundle result=new android.os.Bundle();
            result.putString("receive_audit_benchmark","records=34 snapshot_ns="+oldNs+" buffered_path_ns="+newNs+" postdrain_ns="+finishNs+" exact_bytes="+file.length()+" NO_USB");
            InstrumentationRegistry.getInstrumentation().sendStatus(0,result);
        }finally{cleanup(root);}
    }
}
