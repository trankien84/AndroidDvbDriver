// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.*;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

public class Tbs5530BridgeTest {
    static Tbs5530Session connectedSession() {
        Tbs5530Session s = new Tbs5530Session();
        long generation = s.requestPermission();
        assertTrue(s.permissionResult(generation, true));
        assertTrue(s.connected(generation, new Tbs5530Session.Resources() {
            public void cancel() {} public void release() {} public void close() {}
        }));
        return s;
    }
    static Tbs5530Bridge bridge(Tbs5530ControlIo io, Tbs5530Session s) {
        return new Tbs5530Bridge(io, s, s.getGeneration());
    }

    @Test public void writesExactMinimumAndMaximumPayloadWithOffsets() throws Exception {
        ScriptedFakeControlIo io = new ScriptedFakeControlIo();
        Tbs5530Bridge b = bridge(io, connectedSession());
        io.write(0x80, new byte[]{2, (byte) 0xc8, (byte) 0xff});
        b.i2cWrite(0x64, 0, new byte[]{99, (byte) 0xff, 98}, 1, 1);
        byte[] data = new byte[48]; Arrays.fill(data, (byte) 0x82);
        byte[] payload = new byte[50]; payload[0] = 49; payload[1] = (byte) 0xfe;
        System.arraycopy(data, 0, payload, 2, 48);
        io.write(0x80, payload);
        b.i2cWrite(127, 0, data, 0, 48);
        data[0] = 0;
        assertEquals((byte) 0x82, io.calls().get(1).before[2]);
        io.assertComplete();
    }

    @Test public void readsExactPairAndOnlyCopiesRequestedOutputRegion() throws Exception {
        for (int length : new int[]{1, 50}) {
            ScriptedFakeControlIo io = new ScriptedFakeControlIo();
            Tbs5530Bridge b = bridge(io, connectedSession());
            byte[] answer = new byte[length]; Arrays.fill(answer, (byte) 0xab);
            io.write(0x90, new byte[]{(byte) length, (byte) 0xc8, (byte) 0xfd}); io.read(answer);
            byte[] result = new byte[length + 2]; Arrays.fill(result, (byte) 7);
            b.i2cWriteRead(100, 0, new byte[]{9, (byte) 0xfd}, 1, 1, 100, 1, result, 1, length);
            assertEquals(7, result[0]); assertEquals(7, result[result.length - 1]);
            assertArrayEquals(answer, Arrays.copyOfRange(result, 1, length + 1));
            result[1] = 0; assertEquals((byte) 0xab, io.calls().get(1).after[0]);
            io.assertComplete();
        }
    }

    @Test public void invalidWritesNeverTouchTransportOrFaultHealthySession() {
        ScriptedFakeControlIo io = new ScriptedFakeControlIo(); Tbs5530Session s = connectedSession();
        Tbs5530Bridge b = bridge(io, s);
        int[][] cases = {{-1,0,0,1},{128,0,0,1},{0,1,0,1},{0,16,0,1},{0,0,-1,1},
                {0,0,0,0},{0,0,0,49},{0,0,Integer.MAX_VALUE,1},{0,0,1,Integer.MAX_VALUE}};
        for (int[] c : cases) assertThrows(IllegalArgumentException.class,
                () -> b.i2cWrite(c[0],c[1],new byte[49],c[2],c[3]));
        assertThrows(IllegalArgumentException.class, () -> b.i2cWrite(0,0,null,0,1));
        assertTrue(io.calls().isEmpty()); assertEquals(Tbs5530Session.State.CONNECTED,s.getState());
    }

    @Test public void invalidReadsRejectFlagsAddressesPointerAndBufferBeforeIo() {
        ScriptedFakeControlIo io = new ScriptedFakeControlIo(); Tbs5530Bridge b = bridge(io, connectedSession());
        int[][] cases = {{100,0,1,101,1,1},{100,1,1,100,1,1},{100,0,2,100,1,1},
                {100,0,1,100,0,1},{128,0,1,128,1,1},{100,0,1,100,17,1},
                {100,0,1,100,1,0},{100,0,1,100,1,51}};
        for (int[] c : cases) assertThrows(IllegalArgumentException.class, () ->
                b.i2cWriteRead(c[0],c[1],new byte[2],0,c[2],c[3],c[4],new byte[51],0,c[5]));
        assertThrows(IllegalArgumentException.class, () -> b.i2cWriteRead(0,0,null,0,1,0,1,new byte[1],0,1));
        assertThrows(IllegalArgumentException.class, () -> b.i2cWriteRead(0,0,new byte[1],1,1,0,1,new byte[1],0,1));
        assertThrows(IllegalArgumentException.class, () -> b.i2cWriteRead(0,0,new byte[1],0,1,0,1,new byte[1],1,1));
        assertTrue(io.calls().isEmpty());
    }

    @Test public void controlDirectionRequestAndScalarParametersValidatedBeforeIo() {
        ScriptedFakeControlIo io = new ScriptedFakeControlIo(); Tbs5530Bridge b = bridge(io, connectedSession());
        int[][] invalid = {{0xc0,0x80,0,0,1},{0x40,0x91,0,0,1},{0x40,0xa0,0,0,1},
                {0x40,0x80,1,0,1},{0x40,0x80,0,1,1},{0x40,0x80,0,0,0}};
        for (int[] p : invalid) assertThrows(IllegalArgumentException.class, () ->
                b.transfer(p[0],p[1],p[2],p[3],new byte[3],0,3,p[4]));
        assertTrue(io.calls().isEmpty());
    }

    @Test public void terrestrialRoutingUsesVendorEp0NotBulk81() throws Exception {
        ScriptedFakeControlIo io = new ScriptedFakeControlIo(); io.write(0x8a,new byte[]{10,0});
        bridge(io,connectedSession()).selectTerrestrialStream(); io.assertComplete();
    }

    @Test public void shortZeroNegativeAndOverlongTransfersFaultAndNeverRetry() throws Exception {
        for (int count : new int[]{-1,0,2,4}) {
            ScriptedFakeControlIo io = new ScriptedFakeControlIo(); Tbs5530Session s = connectedSession();
            Tbs5530Bridge b = bridge(io,s);
            io.expect(0x40,0x80,0,0,new byte[]{2,0,7},0,3,2000,null,count,null);
            TransferException ex = assertThrows(TransferException.class, () -> b.i2cWrite(0,0,new byte[]{7},0,1));
            assertEquals(count < 0 || count > 3 ? FailureKind.IO_FAILURE : FailureKind.SHORT_TRANSFER,ex.getKind());
            for(int retry=0;retry<10;retry++) assertThrows(TransferException.class, () -> b.selectTerrestrialStream());
            assertEquals(1,io.calls().size()); assertEquals(Tbs5530Session.State.FAULTED,s.getState()); io.assertComplete();
        }
    }

    @Test public void timeoutIoAndCancellationKeepCauseAndStopRemainingPair() {
        IOException[] failures = {new SocketTimeoutException("deadline"), new IOException("io"),
                new InterruptedIOException("cancel"), new TransferException(FailureKind.CANCELLED,"cancel")};
        FailureKind[] kinds = {FailureKind.TIMEOUT,FailureKind.IO_FAILURE,FailureKind.CANCELLED,FailureKind.CANCELLED};
        for(int i=0;i<failures.length;i++) {
            ScriptedFakeControlIo io = new ScriptedFakeControlIo(); Tbs5530Session s=connectedSession();
            Tbs5530Bridge b=bridge(io,s); IOException cause=failures[i];
            io.expect(0x40,0x90,0,0,new byte[]{1,(byte)0xc8,0},0,3,2000,null,0,cause);
            TransferException ex=assertThrows(TransferException.class, () -> b.i2cWriteRead(100,0,new byte[1],0,1,100,1,new byte[1],0,1));
            assertEquals(kinds[i],ex.getKind()); assertSame(cause,ex.getCause()); assertEquals(1,io.calls().size());
        }
    }

    @Test public void partialReadDoesNotExposeUntrustedBytes() {
        ScriptedFakeControlIo io=new ScriptedFakeControlIo();Tbs5530Session s=connectedSession();
        io.write(0x90,new byte[]{2,0,3});
        io.expect(0xc0,0x91,0,0,new byte[2],0,2,2000,new byte[]{9,9},1,null);
        byte[] out={7,7};
        assertThrows(TransferException.class, () -> bridge(io,s).i2cWriteRead(0,0,new byte[]{3},0,1,0,1,out,0,2));
        assertArrayEquals(new byte[]{7,7},out); assertEquals(Tbs5530Session.State.FAULTED,s.getState());
    }

    @Test public void atomicPairCannotBeInterleavedEvenByAnotherBridge() throws Exception {
        Tbs5530Session s=connectedSession(); ScriptedFakeControlIo script=new ScriptedFakeControlIo();
        script.write(0x90,new byte[]{1,0,1});script.read(new byte[]{42});script.write(0x80,new byte[]{2,0,2});
        CountDownLatch first=new CountDownLatch(1), competing=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Tbs5530ControlIo io=(type,req,value,index,buf,offset,length,timeout)->{
            int n=script.controlTransfer(type,req,value,index,buf,offset,length,timeout);
            if(req==0x90){first.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("latch timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
            return n;
        };
        Tbs5530Bridge a=bridge(io,s),b=bridge(io,s);byte[] answer=new byte[1];
        Thread read=new Thread(()->{try{a.i2cWriteRead(0,0,new byte[]{1},0,1,0,1,answer,0,1);}catch(Throwable e){failure.set(e);}});
        Thread write=new Thread(()->{competing.countDown();try{b.i2cWrite(0,0,new byte[]{2},0,1);}catch(Throwable e){failure.set(e);}});
        try {read.start();assertTrue(first.await(3,TimeUnit.SECONDS));write.start();assertTrue(competing.await(3,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(write.getState()!=Thread.State.BLOCKED && write.isAlive() && System.nanoTime()<deadline) Thread.yield();
            assertEquals("Competing write must block on the session transaction",Thread.State.BLOCKED,write.getState());
            release.countDown();read.join(3000);write.join(3000);assertFalse(read.isAlive());assertFalse(write.isAlive());
            assertNull(failure.get());assertArrayEquals(new byte[]{42},answer);script.assertComplete();
        } finally {release.countDown();read.interrupt();write.interrupt();read.join(3000);write.join(3000);}
    }

    @Test public void endpointPermutationIgnoresOtherInputsAndRejectsAmbiguity() {
        Tbs5530Bridge.Endpoint ts=new Tbs5530Bridge.Endpoint(0,0,0x82,2);
        java.util.List<Tbs5530Bridge.Endpoint> descriptors=Arrays.asList(new Tbs5530Bridge.Endpoint(0,0,0x81,2),ts,
                new Tbs5530Bridge.Endpoint(1,0,0x82,2),new Tbs5530Bridge.Endpoint(0,1,0x82,2));
        for(int i=0;i<descriptors.size();i++){Collections.rotate(descriptors,1);assertSame(ts,Tbs5530Bridge.selectBulkEndpoint(descriptors,0));}
        assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.selectBulkEndpoint(Arrays.asList(ts,ts),0));
        for(int type:new int[]{0,1,3})assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.selectBulkEndpoint(
                Collections.singletonList(new Tbs5530Bridge.Endpoint(0,0,0x82,type)),0));
        assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.selectBulkEndpoint(Collections.emptyList(),0));
        assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.selectBulkEndpoint(null,0));
    }

    @Test public void malformedControlPayloadRejectedWithoutIo() {
        ScriptedFakeControlIo io=new ScriptedFakeControlIo(); Tbs5530Bridge b=bridge(io,connectedSession());
        for(byte[] payload:new byte[][]{{1,0,3},{2,1,3}}) assertThrows(IllegalArgumentException.class,
                ()->b.transfer(0x40,0x80,0,0,payload,0,3,2000));
        for(byte[] payload:new byte[][]{{0,0,3},{51,0,3},{1,1,3}}) assertThrows(IllegalArgumentException.class,
                ()->b.transfer(0x40,0x90,0,0,payload,0,3,2000));
        assertThrows(IllegalArgumentException.class,()->b.transfer(0x40,0x8a,0,0,new byte[]{10,1},0,2,2000));
        assertTrue(io.calls().isEmpty());
    }

    @Test public void interruptedThreadAndTransportCancellationDoNotRetry() throws Exception {
        ScriptedFakeControlIo io=new ScriptedFakeControlIo();Tbs5530Session s=connectedSession();Tbs5530Bridge b=bridge(io,s);
        Thread.currentThread().interrupt();
        try {assertEquals(FailureKind.CANCELLED,assertThrows(TransferException.class,b::selectTerrestrialStream).getKind());}
        finally {Thread.interrupted();}
        assertTrue(io.calls().isEmpty());assertEquals(Tbs5530Session.State.FAULTED,s.getState());
        for(RuntimeException cause:new RuntimeException[]{new java.util.concurrent.CancellationException("cancel"),new IllegalStateException("transport bug")}) {
            Tbs5530Session other=connectedSession();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
            Tbs5530ControlIo broken=(t,r,v,i,buf,o,n,ms)->{calls.incrementAndGet();throw cause;};
            Tbs5530Bridge bridge=bridge(broken,other);TransferException failure=assertThrows(TransferException.class,bridge::selectTerrestrialStream);
            assertSame(cause,failure.getCause());assertEquals(cause instanceof java.util.concurrent.CancellationException?FailureKind.CANCELLED:FailureKind.IO_FAILURE,failure.getKind());
            assertThrows(TransferException.class,bridge::selectTerrestrialStream);assertEquals(1,calls.get());
        }
    }

    @Test public void timeoutOnReadHalfKeepsOutputAndDoesNotRepeatPointer() {
        ScriptedFakeControlIo io=new ScriptedFakeControlIo();Tbs5530Session s=connectedSession();Tbs5530Bridge b=bridge(io,s);
        io.write(0x90,new byte[]{1,0,3});IOException cause=new SocketTimeoutException("read timed out");
        io.expect(0xc0,0x91,0,0,new byte[1],0,1,2000,null,0,cause);byte[] result={9};
        TransferException failure=assertThrows(TransferException.class,()->b.i2cWriteRead(0,0,new byte[]{3},0,1,0,1,result,0,1));
        assertEquals(FailureKind.TIMEOUT,failure.getKind());assertSame(cause,failure.getCause());assertArrayEquals(new byte[]{9},result);
        assertThrows(TransferException.class,b::selectTerrestrialStream);assertEquals(2,io.calls().size());io.assertComplete();
    }

    @Test public void fakeDetectsUnexpectedCallsAndSnapshotsMutableBuffers() throws Exception {
        ScriptedFakeControlIo io=new ScriptedFakeControlIo();byte[] expected={10,0};io.write(0x8a,expected);expected[0]=99;
        byte[] actual={7,10,0,7};
        // Use a separate exact offset expectation; snapshots must not retain caller arrays.
        ScriptedFakeControlIo offsetFake=new ScriptedFakeControlIo();offsetFake.expect(0x40,0x8a,0,0,new byte[]{10,0},1,2,2000,null,2,null);
        assertEquals(2,offsetFake.controlTransfer(0x40,0x8a,0,0,actual,1,2,2000));actual[1]=99;
        assertArrayEquals(new byte[]{10,0},offsetFake.calls().get(0).before);offsetFake.assertComplete();
        bridge(io,connectedSession()).selectTerrestrialStream();io.assertComplete();
        assertThrows(AssertionError.class,()->io.controlTransfer(0x40,0x8a,0,0,new byte[]{10,0},0,2,2000));
    }

    @Test public void detachCallbackDuringPointerTransferPreventsReadHalf() {
        Tbs5530Session s=connectedSession();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        Tbs5530ControlIo io=(type,request,value,index,buf,off,len,timeout)->{calls.incrementAndGet();s.detached(s.getGeneration());return len;};
        byte[] output={7};Tbs5530Bridge b=bridge(io,s);
        assertThrows(TransferException.class,()->b.i2cWriteRead(0,0,new byte[]{3},0,1,0,1,output,0,1));
        assertEquals(1,calls.get());assertArrayEquals(new byte[]{7},output);
        assertEquals(Tbs5530Session.State.DISCONNECTED,s.getState());
    }

    @Test public void chipAndManualPlpPolicyIsExplicitNotAutoOrFallback() {
        assertEquals(Tbs5530Bridge.TunerCore.ASCOT3,Tbs5530Bridge.tunerCoreForChip(0x197));
        for(int id:new int[]{0x19c,0x39a})assertEquals(Tbs5530Bridge.TunerCore.FREIA,Tbs5530Bridge.tunerCoreForChip(id));
        for(int id:new int[]{0,0x396,0x297,0xffff})assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.tunerCoreForChip(id));
        assertEquals(0,Tbs5530Bridge.DEFAULT_MANUAL_PLP);
        assertEquals(0,Tbs5530Bridge.manualPlp(Tbs5530Bridge.DEFAULT_MANUAL_PLP));assertEquals(255,Tbs5530Bridge.manualPlp(255));
        assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.manualPlp(-1));
        assertThrows(IllegalArgumentException.class,()->Tbs5530Bridge.manualPlp(256));
    }
}
