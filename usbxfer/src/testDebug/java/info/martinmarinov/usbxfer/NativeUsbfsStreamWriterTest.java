// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class NativeUsbfsStreamWriterTest {
    private static final class Port implements NativeUsbfsStream.Port {
        final CountDownLatch entered = new CountDownLatch(1), stopped = new CountDownLatch(1), detached = new CountDownLatch(1);
        final AtomicBoolean attached = new AtomicBoolean(), producerDone = new AtomicBoolean();
        boolean block, failRun, quarantine;
        int chunks, totalChunks = 3, frees, copies, runs;
        long drops;
        public void run() throws IOException {
            ++runs; assertTrue(attached.get()); entered.countDown();
            if (block) try { assertTrue(stopped.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new IOException(error); }
            if (failRun) throw new IOException("fake JNI failure");
        }
        public void stop() { stopped.countDown(); }
        public int copy(byte[] bytes) { ++copies; throw new AssertionError("File receive cannot copy full data"); }
        public long[] snapshot() { return new long[]{16,16,16,quarantine ? 1 : 0,totalChunks * 188 + drops,0,quarantine ? 1 : 0,0,0,0,188,0,1,0}; }
        public long[] metrics() { return new long[]{16,16,60000000000L,0,4000,3000,15,0,0,quarantine ? 1 : 0,quarantine ? 1 : 0,2,0,0}; }
        public long[] events(int offset) { assertFalse(attached.get()); return new long[0]; }
        public boolean destroy() { assertFalse(attached.get()); assertFalse(quarantine); ++frees; return true; }
        public void writerAttach() { assertTrue(attached.compareAndSet(false, true)); }
        public int writerRead(byte[] bytes) {
            assertTrue(attached.get());
            if (chunks == totalChunks) return producerDone.get() ? -1 : 0;
            for (int i = 0; i < 188; ++i) bytes[i] = (byte) (chunks + 1);
            ++chunks; return 188;
        }
        public void writerFinish() { producerDone.set(true); }
        public void writerDetach() { attached.set(false); detached.countDown(); }
        public long[] writerMetrics() { assertFalse(attached.get()); return new long[]{totalChunks * 188, drops}; }
        public long[] mappings(int offset) { assertFalse(attached.get()); return offset == 0 ? new long[]{1,0,0,188,0,100} : new long[0]; }
    }
    private static final class Sink implements NativeUsbfsStreamWriter.Sink {
        final File file;
        final FileOutputStream output;
        CountDownLatch writeEntered, releaseWrite;
        boolean failWrite, failSync, anonymousFailure, synced, closed;
        Sink(String name) throws IOException {
            File directory = new File(System.getProperty("b4.native.test.directory", new File("build", "b4-native-writer-tests").getPath()));
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create host test directory");
            file = new File(directory, name + ".ts");
            if (!file.createNewFile()) throw new IOException("Host test artifact already exists");
            output = new FileOutputStream(file);
        }
        public void write(byte[] bytes, int count) throws IOException {
            if (writeEntered != null) {
                writeEntered.countDown();
                try { assertTrue(releaseWrite.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new IOException(error); }
            }
            if (anonymousFailure) throw new IOException("fake anonymous failure") { };
            if (failWrite) throw new IOException("fake full disk");
            output.write(bytes, 0, count);
        }
        public void sync() throws IOException {
            if (failSync) throw new IOException("fake sync failure");
            output.flush(); output.getFD().sync(); synced = true;
        }
        public void close() throws IOException { closed = true; output.close(); }
        void remove() { assertTrue(file.delete()); }
    }
    private static NativeUsbfsStream receiver(Port port, Sink sink, StringWriter audit) {
        return new NativeUsbfsStream(port, sink.file, sink, "b4-test", () -> { }, audit);
    }
    @Test public void fileResultIsDrainedSyncedAndOpenedWithoutFullCaptureArray() throws Exception {
        Port port = new Port(); Sink sink = new Sink("writer-success"); StringWriter audit = new StringWriter();
        NativeUsbfsStream stream = receiver(port,sink,audit);
        try {
            NativeUsbfsStream.Result result = stream.receive();
            assertTrue(sink.synced); assertTrue(sink.closed); assertFalse(port.attached.get());
            assertEquals(0,result.data.length); assertEquals(564,result.byteCount); assertEquals(564,result.writtenBytes);
            assertEquals(0,result.dropBytes); assertNull(result.writerFailure); assertEquals(sink.file,result.dataFile);
            assertEquals(result.dataFile,result.file); assertEquals(9.4,result.bytesPerSecond(),0.001);
            try (InputStream input = result.openData()) { assertEquals(1,input.read()); assertEquals(563,input.available()); }
            assertTrue(audit.toString().contains("\"mapping\":true"));
            assertTrue(audit.toString().contains("\"writer_failure\":null"));
            assertTrue(audit.toString().contains("\"raw_mapping_valid\":true"));
            assertEquals(0,port.copies);
        } finally { stream.close(); sink.remove(); }
        assertEquals(1,port.frees);
    }
    @Test public void dropsRemainSeparateFromWrittenBytesAndNeverQualifyMapping() throws Exception {
        Port port = new Port(); port.drops = 16384; Sink sink = new Sink("writer-drops"); StringWriter audit = new StringWriter();
        NativeUsbfsStream stream = receiver(port,sink,audit);
        try {
            NativeUsbfsStream.Result result = stream.receive();
            assertEquals(16948,result.byteCount); assertEquals(564,result.writtenBytes); assertEquals(16384,result.dropBytes);
            assertNull(result.writerFailure); assertTrue(audit.toString().contains("\"raw_mapping_valid\":false"));
            assertTrue(audit.toString().contains("\"writer_drop_bytes\":16384"));
        } finally { stream.close(); sink.remove(); }
    }
    @Test public void writeAndSyncFailuresStopAndDrainWithoutFalseSuccessOrNativeLeak() throws Exception {
        for (boolean sync : new boolean[]{false,true}) {
            Port port = new Port(); Sink sink = new Sink(sync ? "writer-sync-failure" : "writer-write-failure");
            sink.failSync = sync; sink.failWrite = !sync;
            StringWriter audit = new StringWriter(); NativeUsbfsStream stream = receiver(port,sink,audit);
            try {
                NativeUsbfsStream.Result result = stream.receive();
                assertNotNull(result.writerFailure); assertTrue(sink.closed); assertFalse(sink.synced);
                assertEquals(sync ? 564 : 0,result.writtenBytes); assertEquals(0,result.dropBytes);
                assertEquals(0,port.stopped.getCount()); assertFalse(port.attached.get());
                assertTrue(audit.toString().contains("\"raw_mapping_valid\":false"));
            } finally { stream.close(); sink.remove(); }
            assertEquals(1,port.frees);
        }
    }
    @Test public void anonymousWriterExceptionCannotBecomeAnEmptySuccessfulFailureField() throws Exception {
        Port port = new Port(); Sink sink = new Sink("writer-anonymous-error"); sink.anonymousFailure = true;
        NativeUsbfsStream stream = receiver(port,sink,new StringWriter());
        try { assertEquals("Throwable",stream.receive().writerFailure); }
        finally { stream.close(); sink.remove(); }
    }
    @Test public void blockedWriterDoesNotBlockNativeRunAndCloseWaitsForWriterOwnership() throws Exception {
        Port port = new Port(); Sink sink = new Sink("writer-blocked");
        sink.writeEntered = new CountDownLatch(1); sink.releaseWrite = new CountDownLatch(1);
        NativeUsbfsStream stream = receiver(port,sink,new StringWriter());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread receive = new Thread(() -> { try { stream.receive(); } catch (Throwable error) { failure.set(error); } });
        Thread close = new Thread(() -> { try { stream.close(); } catch (Throwable error) { failure.set(error); } });
        receive.start(); assertTrue(sink.writeEntered.await(3,TimeUnit.SECONDS));
        assertTrue(port.entered.await(3,TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!port.producerDone.get() && System.nanoTime() < deadline) Thread.yield();
        assertTrue(port.producerDone.get());
        close.start(); Thread.sleep(30); assertTrue(close.isAlive()); assertEquals(0,port.frees);
        sink.releaseWrite.countDown(); receive.join(3000); close.join(3000);
        assertFalse(receive.isAlive()); assertFalse(close.isAlive()); assertNull(failure.get());
        assertFalse(port.attached.get()); assertEquals(1,port.frees); sink.remove();
    }
    @Test public void nativeBoundaryFailureStillFinishesAndJoinsWriterBeforeFree() throws Exception {
        Port port = new Port(); port.failRun = true; Sink sink = new Sink("writer-native-failure");
        NativeUsbfsStream stream = receiver(port,sink,new StringWriter());
        try { assertThrows(IOException.class,stream::receive); assertFalse(port.attached.get()); assertTrue(sink.closed); }
        finally { stream.close(); sink.remove(); }
        assertEquals(1,port.frees);
    }
    @Test public void nativeQuarantineRetainsMemoryEvenAfterFileWriterHasClosed() throws Exception {
        Port port = new Port(); port.quarantine = true; Sink sink = new Sink("writer-quarantine");
        NativeUsbfsStream stream = receiver(port,sink,new StringWriter());
        try {
            assertTrue(stream.receive().quarantined); assertTrue(sink.synced); assertTrue(sink.closed);
            assertThrows(IOException.class,stream::close); assertEquals(0,port.frees);
        } finally { sink.remove(); }
    }
    @Test public void writerDrainTimeoutQuarantinesWithoutAuditOrFreeEvenAfterWriterEventuallyExits() throws Exception {
        Port port = new Port(); Sink sink = new Sink("writer-timeout");
        sink.writeEntered = new CountDownLatch(1); sink.releaseWrite = new CountDownLatch(1);
        StringWriter audit = new StringWriter();
        NativeUsbfsStream stream = new NativeUsbfsStream(port,sink.file,sink,"b4-test",() -> { },audit,100);
        try {
            assertThrows(IOException.class,stream::receive); assertEquals(0,sink.writeEntered.getCount());
            assertTrue(port.attached.get()); assertTrue(stream.isQuarantined()); assertEquals("",audit.toString());
            assertThrows(IOException.class,stream::close); assertEquals(0,port.frees);
        } finally {
            sink.releaseWrite.countDown(); assertTrue(port.detached.await(3,TimeUnit.SECONDS));
            // Sticky quarantine cannot be silently recovered by a late file write or close.
            assertTrue(stream.isQuarantined()); assertEquals(0,port.frees); sink.remove();
        }
    }
    @Test public void consentRefusalClosesUnstartedFileBeforeFreeWithoutNativeReceive() throws Exception {
        Port port = new Port(); Sink sink = new Sink("writer-prestart-refused");
        NativeUsbfsStream stream = new NativeUsbfsStream(port,sink.file,sink,"b4-test",() -> { throw new IOException("revoked"); },new StringWriter());
        try { assertThrows(IOException.class,stream::receive); assertEquals(0,port.runs); }
        finally { stream.close(); sink.remove(); }
        assertEquals(1,port.frees); assertTrue(sink.closed);
    }
    @Test public void invalidFileBoundsFailBeforeNativeLibraryLoading() {
        int[][] invalid = {{8,16384,1,60000},{16,4096,1,60000},{16,16384,268435457,60000},{16,16384,1,60001}};
        for (int[] bounds : invalid) assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsStream(
                1,bounds[0],bounds[1],bounds[2],bounds[3],"b4-test",() -> { },new StringWriter(),new File("unused.ts")));
    }
}
