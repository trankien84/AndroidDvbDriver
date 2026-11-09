// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class Tbs5530ControlMirrorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final AtomicLong clock = new AtomicLong(100);
    private Tbs5530ControlMirror mirror(File folder) throws Exception {
        return new Tbs5530ControlMirror(folder, "run-1", "epoch-1", "test-api30-sha1", clock::incrementAndGet);
    }
    private String text(File folder) throws Exception {
        return new String(Files.readAllBytes(new File(folder, "control-ep0.jsonl").toPath()), StandardCharsets.UTF_8);
    }
    @Test public void outputSliceAndTimingAreNotChangedAndNoDiskWritesDuringDispatch() throws Exception {
        File folder = temporary.newFolder(); Tbs5530ControlMirror trace = mirror(folder);
        byte[] bytes = {9,4,0,8}; AtomicInteger calls = new AtomicInteger();
        assertEquals(2, trace.transfer("OPERATIONAL", 7, (t,r,v,i,b,o,l,ms) -> {
            assertSame(bytes, b); assertEquals(1, o); assertEquals(2,l); assertEquals(500,ms);
            assertEquals(0x40,t); assertEquals(0x8a,r); assertEquals(3,v); assertEquals(5,i);
            assertFalse(new File(folder,"control-ep0.jsonl").exists()); calls.incrementAndGet(); b[1]=6; return 2;
        },0x40,0x8a,3,5,bytes,1,2,500));
        assertEquals(1,calls.get()); assertEquals(102,clock.get()); trace.finish(); trace.finish();
        String json=text(folder); assertTrue(json.contains("\"out_hex\":\"0400\""));
        assertTrue(json.contains("\"before_ns\":101,\"after_ns\":102"));
        assertTrue(json.contains("\"generation\":7")); assertTrue(json.contains("\"errno\":null"));
        assertTrue(json.contains("ANDROID_APP_OBSERVED")); assertTrue(trace.durable());
    }
    @Test public void inputOnlyRecordsReturnedSliceNotUnusedOrPreviousBytes() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder); byte[] bytes={9,9,9,9};
        assertEquals(1,trace.transfer("OPERATIONAL",2,(t,r,v,i,b,o,l,ms)->{b[o]=0x55;return 1;},0xc0,0x91,0,0,bytes,1,2,900));
        trace.finish(); String json=text(folder); assertTrue(json.contains("\"in_hex\":\"55\""));
        assertTrue(json.contains("\"out_hex\":\"\"")); assertTrue(json.contains("\"success\":false"));
        assertArrayEquals(new byte[]{9,0x55,9,9},bytes);
    }
    @Test public void zeroNegativeAndOversizedReturnsArePreservedWithoutInventedErrnoOrPayload() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder);
        for(int result:new int[]{0,-1,3}) assertEquals(result,trace.transfer("OPERATIONAL",1,(t,r,v,i,b,o,l,ms)->result,0xc0,0x91,0,0,new byte[2],0,2,1));
        assertEquals(0,trace.transfer("OPERATIONAL",1,(t,r,v,i,b,o,l,ms)->0,0x40,1,0,0,null,0,0,1));
        trace.finish(); String json=text(folder); assertFalse(json.contains("0000"));
        assertEquals(4,json.split("\n").length); assertTrue(json.contains("\"returned_length\":-1"));
        assertTrue(json.contains("\"returned_length\":0,\"errno\":null,\"exception\":\"NONE\",\"success\":true"));
    }
    @Test public void exceptionsKeepIdentityAndNeverCopyErrorMessagesOrTokens() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder); IOException expected=new IOException("private-token");
        assertSame(expected,assertThrows(IOException.class,()->trace.transfer("OPERATIONAL",1,(t,r,v,i,b,o,l,ms)->{throw expected;},0x40,0x80,0,0,new byte[]{3},0,1,1)));
        trace.finish(); String json=text(folder); assertTrue(json.contains("\"returned_length\":null"));
        assertTrue(json.contains("\"exception\":\"IOException\"")); assertFalse(json.contains("private-token"));
    }
    @Test public void firmwareBytesAreOmittedAndNonFirmwarePayloadIsBounded() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder); byte[] bytes=new byte[100]; java.util.Arrays.fill(bytes,(byte)0xaa);
        trace.transfer("BOOTSTRAP",1,(t,r,v,i,b,o,l,ms)->l,0x40,0xa0,0x100,0,bytes,0,100,2000);
        trace.transfer("OPERATIONAL",2,(t,r,v,i,b,o,l,ms)->l,0x40,0x80,0,0,bytes,0,100,2000);
        trace.finish(); String[] lines=text(folder).split("\n");
        assertFalse(lines[0].contains("aaaa")); assertTrue(lines[0].contains("\"firmware_payload_omitted\":true"));
        assertTrue(lines[1].contains("\"out_hex\":\""+new String(new char[128]).replace('\0','a')+"\""));
    }
    @Test public void invalidParametersFailBeforeDelegateAndCompletedTraceCannotBeReused() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder); AtomicInteger calls=new AtomicInteger();
        Tbs5530ControlIo io=(t,r,v,i,b,o,l,ms)->{calls.incrementAndGet();return l;};
        assertThrows(IOException.class,()->trace.transfer("bad\"phase",1,io,0x40,0x80,0,0,new byte[1],0,1,1));
        assertThrows(IOException.class,()->trace.transfer("OPERATIONAL",1,io,0x40,0x80,0,0,new byte[1],1,1,1));
        assertEquals(0,calls.get()); trace.finish();
        assertThrows(IOException.class,()->trace.transfer("OPERATIONAL",1,io,0x40,0x80,0,0,null,0,0,1));
        assertThrows(IOException.class,()->mirror(folder)); assertEquals(0,calls.get());
    }
    @Test public void capacityIsBoundedAndNoExtraControlIsIssued() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder); AtomicInteger calls=new AtomicInteger();
        Tbs5530ControlIo io=(t,r,v,i,b,o,l,ms)->{calls.incrementAndGet();return l;};
        for(int i=0;i<4096;i++) trace.transfer("OPERATIONAL",1,io,0x40,0x80,0,0,null,0,0,1);
        assertThrows(IOException.class,()->trace.transfer("OPERATIONAL",1,io,0x40,0x80,0,0,null,0,0,1));
        assertEquals(4096,calls.get()); assertEquals(4096,trace.count()); trace.finish();
    }
    @Test public void flushCannotRunDuringActiveCallOrOverwriteEvidence() throws Exception {
        File folder=temporary.newFolder(); Tbs5530ControlMirror trace=mirror(folder);
        trace.transfer("OPERATIONAL",1,(t,r,v,i,b,o,l,ms)->{assertThrows(IOException.class,trace::finish);return l;},0x40,0x80,0,0,null,0,0,1);
        File existing=new File(folder,"control-ep0.jsonl"); Files.write(existing.toPath(),new byte[]{7});
        assertThrows(IOException.class,trace::finish); assertArrayEquals(new byte[]{7},Files.readAllBytes(existing.toPath()));
    }
}
