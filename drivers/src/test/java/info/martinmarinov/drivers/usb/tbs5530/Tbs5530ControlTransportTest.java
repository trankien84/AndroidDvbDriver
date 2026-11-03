// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class Tbs5530ControlTransportTest {
    private static final class Raw implements Tbs5530LifecycleCoordinator.Connection, Tbs5530ControlTransport.Connection {
        int calls, claims, releases, closes;
        int type, request, value, index, offset, length, timeout;
        byte[] before;
        int result = Integer.MIN_VALUE;
        IOException error;
        RuntimeException runtimeError;
        boolean forbiddenThread;
        CountDownLatch entered, finish;
        final List<String> events = new CopyOnWriteArrayList<>();
        public void claim(Tbs5530LifecycleCoordinator.Claim receipt) { claims++; receipt.acquired(); }
        public void requireControlWorker() throws IOException { if (forbiddenThread) throw new IOException("main thread"); }
        public int controlTransfer(int type, int request, int value, int index, byte[] buffer,
                                   int offset, int length, int timeout) throws IOException {
            calls++; this.type = type; this.request = request; this.value = value; this.index = index;
            this.offset = offset; this.length = length; this.timeout = timeout;
            before = buffer == null ? null : buffer.clone(); events.add("enter");
            if (entered != null) {
                entered.countDown();
                try { if (!finish.await(3, TimeUnit.SECONDS)) throw new IOException("Fake transfer not resumed"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            if (buffer != null) Arrays.fill(buffer, offset, offset + length, (byte) 42);
            events.add("complete");
            if (error != null) throw error;
            if (runtimeError != null) throw runtimeError;
            return result == Integer.MIN_VALUE ? length : result;
        }
        public void cancel() { events.add("cancel"); }
        public void release() { releases++; events.add("release"); }
        public void close() { closes++; events.add("close"); }
    }
    private static final class Fixture implements AutoCloseable {
        final Tbs5530Session session = new Tbs5530Session();
        final List<Raw> opened = new ArrayList<>();
        final Tbs5530LifecycleCoordinator coordinator;
        Tbs5530LifecycleCoordinator.Request request;
        Raw raw;
        Fixture() throws IOException {
            coordinator = new Tbs5530LifecycleCoordinator(session, ignored -> { }, identity -> {
                Raw next = new Raw(); opened.add(next); raw = next; return next;
            });
            connect();
        }
        void connect() throws IOException {
            request = coordinator.requestPermission(new Tbs5530LifecycleCoordinator.AttachmentIdentity("/fake", 1, 0x734c, 0x5530, opened.size() + 1));
            coordinator.permissionResult(request, request.getIdentity(), true);
        }
        Tbs5530ControlIo io() throws IOException { return coordinator.controlIo(request); }
        public void close() throws IOException { coordinator.close(); }
    }
    private static void transfer(Tbs5530ControlIo io, byte[] buffer) throws IOException {
        io.controlTransfer(0xc0, 0x91, 0, 0, buffer, 1, 2, 2000);
    }

    @Test public void forwardsEveryParameterAndExactOutputSliceWithoutMutation() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] payload = {9, 1, 2, 3, 8};
            assertEquals(3, f.io().controlTransfer(0x40, 0x80, 0xabcd, 0x1234, payload, 1, 3, 77));
            Raw r = f.raw; assertEquals(0x40, r.type); assertEquals(0x80, r.request); assertEquals(0xabcd, r.value);
            assertEquals(0x1234, r.index); assertEquals(1, r.offset); assertEquals(3, r.length); assertEquals(77, r.timeout);
            assertArrayEquals(new byte[]{9,1,2,3,8}, r.before); assertArrayEquals(r.before, payload);
            assertEquals(1, r.calls); assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        }
    }
    @Test public void inputPublishesOnlyRequestedSliceOnFullSuccess() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] output = {9, 8, 7, 6}; transfer(f.io(), output);
            assertArrayEquals(new byte[]{9,42,42,6}, output); assertEquals(0xc0, f.raw.type);
            assertFalse(f.session.isFrontendInitialized()); assertFalse(f.session.isFirmwareUploaded());
        }
    }
    @Test public void shortNegativeZeroAndOverlongCountsFailClosedWithoutPublishingOrRetry() throws Exception {
        for (int count : new int[]{-1, -110, 0, 1, 3}) try (Fixture f = new Fixture()) {
            f.raw.result = count; byte[] output = {7,7,7,7}; Tbs5530ControlIo io = f.io();
            Tbs5530ControlIo.TransferException error = assertThrows(Tbs5530ControlIo.TransferException.class, () -> transfer(io, output));
            assertEquals(count < 0 || count > 2 ? Tbs5530ControlIo.FailureKind.IO_FAILURE : Tbs5530ControlIo.FailureKind.SHORT_TRANSFER, error.getKind());
            assertArrayEquals(new byte[]{7,7,7,7}, output); assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            for (int retry = 0; retry < 10; retry++) assertThrows(IOException.class, () -> transfer(io, output));
            assertEquals(1, f.raw.calls);
        }
    }
    @Test public void preservesIoTimeoutAndRuntimeCausesWithoutRetry() throws Exception {
        for (boolean timeout : Arrays.asList(false, true)) try (Fixture f = new Fixture()) {
            IOException cause = timeout ? new SocketTimeoutException("deadline") : new IOException("io"); f.raw.error = cause;
            Tbs5530ControlIo io = f.io(); byte[] output = {1,2,3,4};
            Tbs5530ControlIo.TransferException error = assertThrows(Tbs5530ControlIo.TransferException.class, () -> transfer(io, output));
            assertSame(cause, error.getCause()); assertEquals(timeout ? Tbs5530ControlIo.FailureKind.TIMEOUT : Tbs5530ControlIo.FailureKind.IO_FAILURE, error.getKind());
            assertArrayEquals(new byte[]{1,2,3,4}, output); assertEquals(1, f.raw.calls);
        }
        try (Fixture f = new Fixture()) {
            f.raw.runtimeError = new IllegalStateException("driver"); Tbs5530ControlIo io = f.io();
            IOException error = assertThrows(IOException.class, () -> transfer(io, new byte[4]));
            assertSame(f.raw.runtimeError, error.getCause()); assertEquals(1, f.raw.calls);
        }
    }
    @Test public void invalidRangesAndFieldsFailBeforeIo() throws Exception {
        try (Fixture f = new Fixture()) {
            Tbs5530ControlIo io = f.io();
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x140,0x80,0,0,new byte[1],0,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,256,0,0,new byte[1],0,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,-1,0,new byte[1],0,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,65536,new byte[1],0,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,0,null,0,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,0,new byte[1],Integer.MAX_VALUE,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,0,new byte[1],0,Integer.MAX_VALUE,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,0,new byte[1],-1,1,2));
            assertThrows(IllegalArgumentException.class, () -> io.controlTransfer(0x40,0x80,0,0,new byte[1],0,1,0));
            assertEquals(0, f.raw.calls); assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        }
    }
    @Test public void zeroLengthTransferAndNullBufferPreserveApiShape() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0, f.io().controlTransfer(0x40, 0x8a, 0, 0, null, 0, 0, 2));
            assertNull(f.raw.before); assertEquals(0, f.raw.length); assertEquals(1, f.raw.calls);
        }
    }
    @Test public void onlyClaimedManagedConnectionCanIssueCapabilityAndNoSecondOpen() throws Exception {
        Tbs5530Session s = new Tbs5530Session(); Raw raw = new Raw();
        Tbs5530LifecycleCoordinator c = new Tbs5530LifecycleCoordinator(s, ignored -> {}, identity -> raw);
        Tbs5530LifecycleCoordinator.Request request = c.requestPermission(new Tbs5530LifecycleCoordinator.AttachmentIdentity("/x",1,1,2,1));
        assertThrows(IOException.class, () -> c.controlIo(request)); assertEquals(0, raw.calls); c.close();
        try (Fixture f = new Fixture()) {
            Tbs5530ControlIo first = f.io(), second = f.io(); transfer(first,new byte[4]); transfer(second,new byte[4]);
            assertEquals(1,f.opened.size()); assertEquals(1,f.raw.claims); assertEquals(2,f.raw.calls);
            f.close(); f.close(); assertEquals(1,f.raw.releases); assertEquals(1,f.raw.closes);
            assertThrows(IOException.class, () -> transfer(first,new byte[4])); assertEquals(2,f.raw.calls);
        }
    }
    @Test public void transportCannotBypassUnclaimedOrMismatchedSessionOwnership() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); Raw owner = new Raw(), stranger = new Raw();
        long token = session.requestPermission(); session.permissionResult(token,true); session.connected(token,owner);
        Tbs5530ControlIo unclaimed = new Tbs5530ControlTransport(session,token,owner);
        assertThrows(IOException.class,()->transfer(unclaimed,new byte[4])); assertEquals(0,owner.calls);
        session.interfaceClaimed(token);
        Tbs5530ControlIo mismatched = new Tbs5530ControlTransport(session,token,stranger);
        assertThrows(IOException.class,()->transfer(mismatched,new byte[4])); assertEquals(0,stranger.calls);
        transfer(unclaimed,new byte[4]); assertEquals(1,owner.calls); session.close();
        assertThrows(IOException.class,()->transfer(unclaimed,new byte[4])); assertEquals(1,owner.calls);
    }
    @Test public void staleHandleCannotUseOrCloseNewGeneration() throws Exception {
        try (Fixture f = new Fixture()) {
            Tbs5530ControlIo old = f.io(); Raw first=f.raw; f.close(); f.connect(); Raw current=f.raw;
            assertThrows(IOException.class, () -> transfer(old,new byte[4])); assertEquals(0,first.calls); assertEquals(0,current.calls);
            transfer(f.io(),new byte[4]); assertEquals(1,current.calls); assertEquals(0,current.closes);
            assertEquals(1,first.closes); assertEquals(1,first.releases);
        }
    }
    @Test public void faultedAndUncertainSessionsRejectIoBeforeRawCall() throws Exception {
        for(boolean uncertain:Arrays.asList(false,true)) try(Fixture f=new Fixture()) {
            Tbs5530ControlIo io=f.io(); if(uncertain)f.session.beginFirmwareTransition(f.request.getToken());
            f.session.failed(f.request.getToken(),new IOException("failure"));
            assertEquals(uncertain?Tbs5530Session.State.UNCERTAIN:Tbs5530Session.State.FAULTED,f.session.getState());
            assertThrows(IOException.class,()->transfer(io,new byte[4])); assertEquals(0,f.raw.calls);
        }
    }
    @Test public void workerGuardAndInterruptedThreadDoNotSendVendorRequest() throws Exception {
        try(Fixture f=new Fixture()) {
            Tbs5530ControlIo io=f.io(); f.raw.forbiddenThread=true;
            assertThrows(IOException.class,()->transfer(io,new byte[4])); assertEquals(0,f.raw.calls);
            f.raw.forbiddenThread=false; Thread.currentThread().interrupt();
            try { assertThrows(IOException.class,()->transfer(io,new byte[4])); } finally { Thread.interrupted(); }
            assertEquals(0,f.raw.calls);
        }
    }
    private void lateCompletion(int mode) throws Exception {
        try(Fixture f=new Fixture()) {
            Tbs5530ControlIo io=f.io(); Raw r=f.raw; r.entered=new CountDownLatch(1);r.finish=new CountDownLatch(1);
            byte[] output={7,7,7,7}; AtomicReference<Throwable> error=new AtomicReference<>();
            Thread worker=new Thread(()->{try{transfer(io,output);}catch(Throwable e){error.set(e);}});worker.start();
            try{
                assertTrue(r.entered.await(1,TimeUnit.SECONDS));
                if(mode==0)f.session.detached(f.request.getToken());
                else if(mode==1)f.session.requestCancellation(f.request.getToken(),false);
                else f.close();
                assertTrue(f.session.isCleanupPending()); assertEquals(0,r.releases);assertEquals(0,r.closes);
                assertThrows(IllegalStateException.class,f::connect);
            }finally{r.finish.countDown();worker.join(3000);}
            assertFalse(worker.isAlive());assertTrue(error.get() instanceof IOException);
            assertArrayEquals(new byte[]{7,7,7,7},output);assertEquals(1,r.calls);assertEquals(1,r.releases);assertEquals(1,r.closes);
            assertEquals(Arrays.asList("enter","complete","release","close"),r.events);
        }
    }
    @Test(timeout=10000) public void detachDuringControlDefersCloseAndDiscardsLateRead() throws Exception { lateCompletion(0); }
    @Test(timeout=10000) public void logicalTimeoutDuringControlIsNotPhysicalCompletion() throws Exception { lateCompletion(1); }
    @Test(timeout=10000) public void concurrentCloseDuringControlKeepsConnectionUntilReturn() throws Exception { lateCompletion(2); }
}
