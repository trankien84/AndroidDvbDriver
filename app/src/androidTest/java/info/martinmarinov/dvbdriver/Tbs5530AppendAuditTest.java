// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.system.Os;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated cache files only. No USB, grants, production history, or capture data. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530AppendAuditTest {
    private static File directory() {
        File root = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "append-audit-test-" + UUID.randomUUID());
        assertTrue(root.mkdir()); return root;
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static byte[] read(File file) throws IOException { return new android.util.AtomicFile(file).readFully(); }
    private static void cleanup(File root) { for (File f : root.listFiles()) assertTrue(f.delete()); assertTrue(root.delete()); }
    @Test public void eachAppendCommitsExactBytesAndCannotOverwriteOrReopen() throws Exception {
        File root=directory(), file=new File(root,"audit.jsonl");
        try {
            Tbs5530AppendAudit log=new Tbs5530AppendAudit(file);log.write(bytes("a\n"));assertArrayEquals(bytes("a\n"),read(file));
            log.write(bytes("a\nb\n"));assertArrayEquals(bytes("a\nb\n"),read(file));
            assertThrows(IOException.class,()->new Tbs5530AppendAudit(file).write(bytes("replacement\n")));
            assertArrayEquals(bytes("a\nb\n"),read(file));
            assertThrows(IOException.class,()->log.write(bytes("wrong prefix\n")));
            assertThrows(IOException.class,()->log.write(bytes("a\nb\nc\n")));
            assertArrayEquals(bytes("a\nb\n"),read(file));
        } finally { cleanup(root); }
    }
    @Test public void changedPrefixInodeOrSymlinkLatchesFailureWithoutRollback() throws Exception {
        for(int fault=0;fault<3;fault++) {
            File root=directory(), file=new File(root,"audit.jsonl"), target=new File(root,"target.jsonl");
            try {
                Tbs5530AppendAudit log=new Tbs5530AppendAudit(file);log.write(bytes("a\n"));
                if(fault==0){try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes("x\n"));out.getFD().sync();}}
                else { assertTrue(file.renameTo(target)); if(fault==1){try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes("a\n"));out.getFD().sync();}}
                    else Os.symlink(target.getPath(),file.getPath()); }
                assertThrows(IOException.class,()->log.write(bytes("a\nb\n")));
                assertThrows(IOException.class,()->log.write(bytes("a\nb\n")));
                if(fault>0)assertArrayEquals(bytes("a\n"),read(target));
            } finally { cleanup(root); }
        }
    }
    @Test public void benchmarkDurableSnapshotVersusAppendKeepsIdenticalAudit() throws Exception {
        File root=directory(), old=new File(root,"snapshot.jsonl"), next=new File(root,"append.jsonl");
        try {
            Tbs5530AppendAudit log=new Tbs5530AppendAudit(next);StringBuilder text=new StringBuilder();long snapshotNs=0,appendNs=0;
            for(int i=0;i<34;i++) {
                text.append("{\"event\":\"SYNTHETIC_BOUNDARY\",\"ordinal\":").append(i).append("}\n");byte[] expected=bytes(text.toString());
                long begin=android.os.SystemClock.elapsedRealtimeNanos();Tbs5530DurableFile.write(old,expected);snapshotNs+=android.os.SystemClock.elapsedRealtimeNanos()-begin;
                begin=android.os.SystemClock.elapsedRealtimeNanos();log.write(expected);appendNs+=android.os.SystemClock.elapsedRealtimeNanos()-begin;
                assertArrayEquals(expected,read(next));
            }
            assertArrayEquals(read(old),read(next));
            String measurement="APPEND_AUDIT_BENCHMARK records=34 snapshot_ns="+snapshotNs+" append_ns="+appendNs+" exact_bytes="+next.length()+" NO_USB";
            android.os.Bundle result=new android.os.Bundle();result.putString("audit_benchmark",measurement);
            InstrumentationRegistry.getInstrumentation().sendStatus(0,result);
        } finally { cleanup(root); }
    }
}
