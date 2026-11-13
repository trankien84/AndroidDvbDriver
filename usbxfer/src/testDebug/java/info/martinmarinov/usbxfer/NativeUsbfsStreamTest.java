// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import org.junit.Test;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class NativeUsbfsStreamTest {
    private static final class Port implements NativeUsbfsStream.Port {
        final CountDownLatch entered = new CountDownLatch(1), stopped = new CountDownLatch(1);
        boolean block, done, quarantine, failRun, auditEvent;
        CountDownLatch finishGate, closeStops;
        final AtomicInteger afterFree = new AtomicInteger();
        int runs, stops, frees, copies;
        private void requireLive() {
            if (frees != 0) { afterFree.incrementAndGet(); fail("Native port accessed after destroy"); }
        }
        long[] result = {16,16,200000000,0,5000,4000,15,0,0,0,0,3,0,0};
        public void run() throws IOException {
            requireLive(); ++runs; entered.countDown();
            if (block) {
                boolean interrupted = false;
                for (;;) {
                    try { assertTrue(stopped.await(2, TimeUnit.SECONDS)); break; }
                    catch (InterruptedException error) {
                        interrupted = true; Thread.currentThread().interrupt();
                        while (stopped.getCount() != 0) Thread.yield();
                        break;
                    }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (finishGate != null) {
                try { assertTrue(finishGate.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            done = true;
            if (failRun) throw new IOException("JNI boundary failure");
        }
        public synchronized void stop() {
            requireLive(); ++stops; stopped.countDown();
            if (closeStops != null) closeStops.countDown();
        }
        public int copy(byte[] bytes) { requireLive(); assertTrue(done); copies++; bytes[0] = 0x47; return 188; }
        public long[] snapshot() {
            requireLive();
            return new long[]{16,16,16,quarantine ? 1 : 0,done ? 188 : 0,0,quarantine ? 1 : 0,0,1,0,188,0,1,0};
        }
        public long[] metrics() { requireLive(); assertTrue(done); result[9] = quarantine ? 1 : 0; result[10] = quarantine ? 1 : 0; return result; }
        public long[] events(int offset) {
            requireLive(); assertTrue(done);
            return auditEvent && offset == 0 ? new long[]{2,0,0,0,0,188,16384,16384,0x82,0,1,10,20,15} : new long[0];
        }
        public boolean destroy() { requireLive(); assertFalse(quarantine); ++frees; return true; }
    }
    private static NativeUsbfsStream stream(Port port, NativeUsbfsStream.Consent consent, Writer audit) {
        return new NativeUsbfsStream(port, 4096, "b3-test", consent, audit);
    }
    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(3,TimeUnit.SECONDS)); }
    @Test public void receiveReturnsRawMetricsAndAuditOnlyAfterNativeDrain() throws Exception {
        Port port = new Port(); port.auditEvent = true;
        StringWriter audit = new StringWriter();
        NativeUsbfsStream receiver = stream(port, () -> assertEquals("",audit.toString()), audit);
        NativeUsbfsStream.Result r = receiver.receive();
        assertEquals(188,r.data.length); assertEquals(0x47,r.data[0]);
        assertEquals(188,r.byteCount); assertEquals(188,r.writtenBytes); assertEquals(0,r.dropBytes);
        assertNull(r.dataFile); assertNull(r.writerFailure);
        try (java.io.InputStream input = r.openData()) { assertEquals(0x47,input.read()); }
        assertEquals(16,r.submitted); assertEquals(16,r.completed); assertEquals(15,r.minOutstanding);
        assertEquals(5000,r.maxResubmitNanos); assertEquals(4000,r.medianResubmitNanos);
        assertEquals(940.0,r.bytesPerSecond(),0.01); assertEquals("BYTE_LIMIT",r.stopReason);
        assertEquals(0,r.firstStatus); assertEquals(0,r.firstErrno);
        assertTrue(audit.toString().contains("\"sequence\":1"));
        assertTrue(audit.toString().contains("software_zero_only_not_hardware_proof"));
        assertTrue(audit.toString().contains("\"queue_horizon_risk_count\":null"));
        assertTrue(audit.toString().contains("\"queue_horizon_assumed_bytes_per_second\":null"));
        assertEquals(14,receiver.snapshot().length); receiver.close(); receiver.close();
        assertEquals(1,port.frees); assertFalse(receiver.isQuarantined());
    }
    @Test public void starvationAuditDoesNotInferAQueueHorizonFromAssumedBitrate() throws Exception {
        Port port = new Port(); port.result[3] = port.result[12] = 2;
        StringWriter audit = new StringWriter();
        NativeUsbfsStream receiver = stream(port, () -> { },audit);
        try {
            assertEquals(2,receiver.receive().starvationCount);
            assertTrue(audit.toString().contains("\"zero_software_outstanding_count\":2"));
            assertTrue(audit.toString().contains("\"queue_horizon_assumed_bytes_per_second\":null"));
            assertFalse(audit.toString().contains("3437500"));
        } finally { receiver.close(); }
    }
    @Test public void completionErrorPreservesDataAndDoesNotThrow() throws Exception {
        Port port = new Port(); port.result[7] = -75; port.result[11] = 4;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        NativeUsbfsStream.Result r = receiver.receive();
        assertEquals("COMPLETION_STATUS",r.stopReason); assertEquals(-75,r.firstStatus);
        assertEquals(0,r.firstErrno); assertEquals(188,r.data.length); receiver.close();
    }
    @Test public void syscallErrnoIsSeparateFromDataStatus() throws Exception {
        Port port = new Port(); port.result[8] = 19; port.result[11] = 6;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        NativeUsbfsStream.Result r = receiver.receive();
        assertEquals("SYSCALL",r.stopReason); assertEquals(19,r.firstErrno);
        assertEquals(0,r.firstStatus); receiver.close();
    }
    @Test public void consentFailureBeforeStartThrowsWithoutAnyReceive() throws Exception {
        Port port = new Port();
        NativeUsbfsStream receiver = stream(port, () -> { throw new IOException("revoked before start"); },new StringWriter());
        assertThrows(IOException.class,receiver::receive); assertEquals(0,port.runs);
        receiver.close(); assertEquals(1,port.frees);
    }
    @Test public void watchdogChecksBeforeStartAndReturnsFailureMetrics() throws Exception {
        Port port = new Port(); AtomicInteger checks = new AtomicInteger();
        NativeUsbfsStream receiver = stream(port, () -> {
            if (checks.incrementAndGet() == 2) throw new IOException("permission source revoked");
        },new StringWriter());
        NativeUsbfsStream.Result r = receiver.receive();
        assertEquals("WATCHDOG",r.stopReason); assertEquals(2,checks.get());
        assertTrue(port.stops > 0); assertEquals(188,r.data.length); receiver.close();
    }
    @Test public void watchdogReadsActualPermissionEvery100msAndJoinsBeforeAudit() throws Exception {
        Port port = new Port(); port.block = true;
        AtomicBoolean allowed = new AtomicBoolean(true), audited = new AtomicBoolean();
        AtomicInteger checks = new AtomicInteger(); AtomicLong lastCheck = new AtomicLong();
        AtomicLong revokeAt = new AtomicLong();
        Writer audit = new StringWriter() {
            @Override public void write(String text) { audited.set(true); super.write(text); }
        };
        NativeUsbfsStream receiver = stream(port, () -> {
            assertFalse(audited.get()); checks.incrementAndGet(); lastCheck.set(System.nanoTime());
            if (!allowed.get()) throw new IOException("live permission denied");
        },audit);
        AtomicReference<NativeUsbfsStream.Result> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { result.set(receiver.receive()); } catch (Throwable error) { failure.set(error); }
        });
        worker.start(); await(port.entered); revokeAt.set(System.nanoTime()); allowed.set(false);
        worker.join(2000); assertFalse(worker.isAlive()); assertNull(failure.get());
        assertEquals("WATCHDOG",result.get().stopReason); assertTrue(checks.get() >= 3);
        assertTrue("scheduler-tolerant bound", lastCheck.get() - revokeAt.get() < 500000000L);
        int joinedCount = checks.get(); Thread.sleep(120); assertEquals(joinedCount,checks.get());
        receiver.close();
    }
    @Test public void uncheckedWatchdogFailureAlsoStopsNative() throws Exception {
        Port port = new Port(); AtomicInteger checks = new AtomicInteger();
        NativeUsbfsStream receiver = stream(port, () -> {
            if (checks.incrementAndGet() == 2) throw new IllegalStateException("heartbeat source fault");
        },new StringWriter());
        assertEquals("WATCHDOG",receiver.receive().stopReason); receiver.close();
    }
    @Test public void requestStopCanComeFromAnotherThread() throws Exception {
        Port port = new Port(); port.block = true; port.result[11] = 1;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { receiver.receive(); } catch (Throwable e) { error.set(e); } });
        worker.start(); await(port.entered); receiver.requestStop(); worker.join(2000);
        assertFalse(worker.isAlive()); assertNull(error.get()); receiver.close();
    }
    @Test public void closeStopsAndWaitsForNativeAndWatchdogBeforeFreeing() throws Exception {
        Port port = new Port(); port.block = true;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { receiver.receive(); } catch (Throwable e) { error.set(e); } });
        worker.start(); await(port.entered); receiver.close(); worker.join(2000);
        assertFalse(worker.isAlive()); assertNull(error.get()); assertEquals(1,port.frees);
        receiver.requestStop(); receiver.close(); assertEquals(1,port.frees);
    }
    @Test public void twoConcurrentClosersWaitingForReceiveDestroyOnceWithoutAfterFreeAccess() throws Exception {
        Port port = new Port(); port.block = true;
        port.finishGate = new CountDownLatch(1); port.closeStops = new CountDownLatch(2);
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { receiver.receive(); } catch (Throwable error) { failure.compareAndSet(null,error); }
        });
        Runnable close = () -> {
            try { receiver.close(); } catch (Throwable error) { failure.compareAndSet(null,error); }
        };
        Thread first = new Thread(close), second = new Thread(close);
        worker.start(); await(port.entered);
        try {
            first.start(); second.start(); await(port.closeStops);
            assertEquals(0,port.frees);
        } finally {
            port.finishGate.countDown();
            worker.join(3000); first.join(3000); second.join(3000);
        }
        assertFalse(worker.isAlive()); assertFalse(first.isAlive()); assertFalse(second.isAlive());
        assertNull(failure.get()); assertEquals(1,port.frees); assertEquals(0,port.afterFree.get());
        assertEquals(14,receiver.snapshot().length); receiver.requestStop(); receiver.close();
        assertEquals(1,port.frees); assertEquals(0,port.afterFree.get());
    }
    @Test public void quarantineRetainsAllocationsAndCloseIsIdempotent() throws Exception {
        Port port = new Port(); port.quarantine = true;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        NativeUsbfsStream.Result r = receiver.receive(); assertTrue(r.quarantined); assertEquals(1,r.outstanding);
        assertTrue(receiver.isQuarantined()); assertThrows(IOException.class,receiver::close);
        receiver.close(); receiver.requestStop(); assertEquals(0,port.frees);
        assertEquals(1,receiver.snapshot()[3]);
    }
    @Test public void auditFailureDoesNotLeakDrainedNativeState() throws Exception {
        Port port = new Port(); Writer broken = new Writer() {
            public void write(char[] c,int at,int count) throws IOException { assertTrue(port.done); throw new IOException("disk full"); }
            public void flush() { }
            public void close() { }
        };
        NativeUsbfsStream receiver = stream(port, () -> { },broken);
        assertThrows(IOException.class,receiver::receive); receiver.close(); assertEquals(1,port.frees);
    }
    @Test public void nativeBoundaryExceptionStillJoinsWatchdogAndAllowsCleanup() throws Exception {
        Port port = new Port(); port.failRun = true; AtomicInteger checks = new AtomicInteger();
        NativeUsbfsStream receiver = stream(port,checks::incrementAndGet,new StringWriter());
        assertThrows(IOException.class,receiver::receive); int after = checks.get();
        Thread.sleep(120); assertEquals(after,checks.get()); receiver.close(); assertEquals(1,port.frees);
    }
    @Test public void interruptBeforeStartDoesNotSubmitAndPreservesFlag() throws Exception {
        Port port = new Port(); NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class,receiver::receive); assertTrue(Thread.currentThread().isInterrupted()); assertEquals(0,port.runs); }
        finally { Thread.interrupted(); receiver.close(); }
    }
    @Test public void runningWorkerInterruptRequestsAtomicStopAndPreservesFlag() throws Exception {
        Port port = new Port(); port.block = true;
        NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        AtomicReference<Throwable> error = new AtomicReference<>(); AtomicBoolean preserved = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try { assertEquals("WATCHDOG",receiver.receive().stopReason); preserved.set(Thread.currentThread().isInterrupted()); }
            catch (Throwable e) { error.set(e); }
        });
        worker.start(); await(port.entered); worker.interrupt(); worker.join(2000);
        assertFalse(worker.isAlive()); assertNull(error.get()); assertTrue(preserved.get()); receiver.close();
    }
    @Test public void receiveIsOneShotAndSnapshotCannotBeMutatedByCaller() throws Exception {
        Port port = new Port(); NativeUsbfsStream receiver = stream(port, () -> { },new StringWriter());
        receiver.receive(); assertThrows(IOException.class,receiver::receive);
        long[] state = receiver.snapshot(); state[0] = -1; assertEquals(16,receiver.snapshot()[0]);
        receiver.close(); assertThrows(IOException.class,receiver::receive);
    }
    @Test public void legacyResultReflectionConstructorKeepsRamCaptureDefaults() throws Exception {
        java.lang.reflect.Constructor<NativeUsbfsStream.Result> constructor = NativeUsbfsStream.Result.class
                .getDeclaredConstructor(byte[].class, long[].class, String.class);
        constructor.setAccessible(true);
        byte[] data = {0x47, 0x20};
        NativeUsbfsStream.Result result = constructor.newInstance(data, new Port().result, "BYTE_LIMIT");
        assertSame(data,result.data); assertNull(result.dataFile); assertNull(result.file);
        assertEquals(2,result.byteCount); assertEquals(2,result.writtenBytes); assertEquals(2,result.writerWrittenBytes);
        assertEquals(0,result.dropBytes); assertEquals(0,result.writerDropBytes); assertNull(result.writerFailure);
        assertEquals(16,result.submitted); assertEquals("BYTE_LIMIT",result.stopReason);
        try (java.io.InputStream input = result.openData()) {
            assertEquals(0x47,input.read()); assertEquals(0x20,input.read()); assertEquals(-1,input.read());
        }
    }
    @Test public void invalidArgumentsFailBeforeLoadingNativeLibrary() {
        int[][] invalid = {{-1,16,16384,4096,2000},{1,7,16384,4096,2000},{1,33,16384,4096,2000},
            {1,16,4095,4096,2000},{1,16,65537,4096,2000},{1,16,4097,4096,2000},
            {1,16,16384,0,2000},{1,16,16384,16777217,2000},{1,16,16384,4096,0},{1,16,16384,4096,10001}};
        for (int[] a : invalid) assertThrows(IllegalArgumentException.class, () ->
            new NativeUsbfsStream(a[0],a[1],a[2],a[3],a[4],"bounds",() -> { },new StringWriter()));
        assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsStream(1,16,16384,4096,2000,"bad\"id",() -> { },new StringWriter()));
        assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsStream(1,16,16384,4096,2000,"bounds",null,new StringWriter()));
    }
}
