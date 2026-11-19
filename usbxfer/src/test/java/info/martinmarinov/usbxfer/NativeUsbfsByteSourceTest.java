// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativeUsbfsByteSourceTest {
    private static final class Fake implements NativeUsbfsByteSource.Backend {
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(1);
        final AtomicInteger destroyed = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final AtomicInteger connectionCloses = new AtomicInteger();
        volatile int count = 188, reason = 1, status, errno;
        volatile boolean drained = true, createFails, runFinished, snapshotFails;
        final AtomicInteger detailReads = new AtomicInteger();
        int expectedRequests = 16;
        long[] trace = {65536, 1, 0, 0, 0, 0, 123450000L, 123460000L, 123456789L, 0, 188};
        byte[] firstBuffer;
        @Override public long create(int fd, int iface, int alt, int requests) throws IOException {
            assertEquals(42, fd); assertEquals(0, iface); assertEquals(0, alt); assertEquals(expectedRequests, requests);
            if (createFails) throw new IOException("setup failed");
            return 123;
        }
        @Override public int run(long handle) {
            assertEquals(1, connectionCloses.get());
            running.countDown();
            try { stop.await(); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            runFinished = true;
            return reason;
        }
        @Override public int[] errorDetails(long handle) {
            assertTrue(runFinished); assertEquals(0, destroyed.get()); detailReads.incrementAndGet();
            return new int[] {reason, status, errno};
        }
        @Override public long[] completions(long handle) throws IOException {
            assertEquals(0, destroyed.get());
            if (snapshotFails) throw new IOException("snapshot failed");
            long[] result = trace.clone(); result[4] = runFinished ? 1 : 0; result[5] = reason;
            if (!runFinished) result[7] = -1;
            return result;
        }
        @Override public void stop(long handle) { assertEquals(123, handle); stops.incrementAndGet(); stop.countDown(); }
        @Override public int read(long handle, byte[] data) {
            boolean first = firstBuffer == null;
            if (first) firstBuffer = data;
            else assertSame(firstBuffer, data);
            assertEquals(NativeUsbfsByteSource.BUFFER_SIZE, data.length);
            if (first && count == 188) {
                for (int i = 0; i < 5; i++) data[2049 + i * 188] = 0x47;
                return 2049 + 5 * 188;
            }
            data[0] = 0x47;
            return count;
        }
        @Override public boolean destroy(long handle) { assertEquals(123, handle); destroyed.incrementAndGet(); return drained; }
    }
    private NativeUsbfsByteSource source(Fake fake) {
        return source(fake, NativeUsbfsByteSource.Configuration.DEFAULT);
    }
    private NativeUsbfsByteSource source(Fake fake, NativeUsbfsByteSource.Configuration configuration) {
        UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
        when(connection.getFileDescriptor()).thenReturn(42);
        doAnswer(invocation -> { fake.connectionCloses.incrementAndGet(); return null; }).when(connection).close();
        UsbInterface iface = mock(UsbInterface.class);
        when(iface.getId()).thenReturn(0);
        return new NativeUsbfsByteSource(connection, new AlternateUsbInterface(iface, 0), configuration, fake);
    }
    private static void assertIo(Throwing action) throws Exception {
        try { action.run(); fail("Expected IOException"); }
        catch (IOException expected) { }
    }
    private interface Throwing { void run() throws Exception; }

    @Test(timeout = 5000) public void transfersConnectionAndReusesConsumerBuffer() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake);
        source.open(); assertTrue(fake.running.await(1, TimeUnit.SECONDS));
        AtomicInteger calls = new AtomicInteger();
        ByteSink sink = (data, length) -> {
            assertEquals(calls.getAndIncrement() == 0 ? 5 * 188 : 188, length);
            assertEquals(0x47, data[0]);
        };
        source.readNext(sink); source.readNext(sink);
        assertEquals(2, calls.get());
        source.close(); source.close();
        assertEquals(1, fake.destroyed.get()); assertEquals(1, fake.connectionCloses.get());
        assertTrue(source.snapshotCompletions().isPrefixLocked());
        assertEquals(2049, source.snapshotCompletions().getDiscardedPrefixBytes());
        assertTrue(source.snapshotCompletions().isComplete());
        assertFalse(source.snapshotCompletions().isTruncated());
        assertIo(source::open); assertIo(() -> source.readNext(sink));
    }
    @Test(timeout = 5000) public void acceptsOnlyBoundedDeepQueuePreset() throws Exception {
        Fake fake = new Fake(); fake.expectedRequests = 32;
        NativeUsbfsByteSource source = source(fake, NativeUsbfsByteSource.Configuration.DEEP_QUEUE);
        source.open(); source.close(); assertEquals(1, fake.destroyed.get());
    }
    @Test public void rejectsNonPinnedInterfaceAndAlternateBeforeNativeCreate() throws Exception {
        int[][] invalid = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {256, 0}, {0, 256}};
        for (int[] selection : invalid) {
            UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
            UsbInterface iface = mock(UsbInterface.class);
            when(iface.getId()).thenReturn(selection[0]);
            NativeUsbfsByteSource.Backend backend = mock(NativeUsbfsByteSource.Backend.class);
            NativeUsbfsByteSource source = new NativeUsbfsByteSource(connection,
                    new AlternateUsbInterface(iface, selection[1]), backend);
            assertIo(source::open); source.close();
            verifyNoInteractions(backend); verify(connection).close();
        }
    }
    @Test public void closesConnectionWithoutOpeningNative() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake);
        source.close(); source.close(); assertEquals(1, fake.connectionCloses.get());
        assertEquals(0, fake.destroyed.get()); assertIo(source::open);
    }
    @Test public void closesConnectionOnCreateFailure() throws Exception {
        Fake fake = new Fake(); fake.createFails = true;
        NativeUsbfsByteSource source = source(fake);
        assertIo(source::open); source.close();
        assertEquals(1, fake.connectionCloses.get()); assertEquals(0, fake.destroyed.get());
    }
    @Test public void stopBeforeOpenPreventsStart() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake);
        source.requestStop(); assertIo(source::open); source.close(); assertEquals(1, fake.connectionCloses.get());
    }
    @Test(timeout = 5000) public void concurrentStopAndTwoClosesDoNotDeadlockOrDoubleFree() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake);
        source.open(); assertTrue(fake.running.await(1, TimeUnit.SECONDS));
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable close = () -> { try { source.requestStop(); source.close(); } catch (Throwable e) { error.set(e); } };
        Thread a = new Thread(close); Thread b = new Thread(close);
        a.start(); b.start(); a.join(2000); b.join(2000);
        assertFalse(a.isAlive()); assertFalse(b.isAlive()); assertNull(error.get()); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void sinkCanCloseSourceWithoutDeadlock() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        source.readNext((data, length) -> source.close()); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void slowSinkDoesNotBlockCloseOrNativeStop() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        CountDownLatch inSink = new CountDownLatch(1), releaseSink = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try { source.readNext((data, length) -> {
                inSink.countDown();
                try { releaseSink.await(); } catch (InterruptedException e) { throw new IOException(e); }
            }); } catch (Throwable e) { failure.set(e); }
        });
        reader.start(); assertTrue(inSink.await(1, TimeUnit.SECONDS));
        try { source.close(); assertEquals(1, fake.destroyed.get()); }
        finally { releaseSink.countDown(); reader.join(2000); }
        assertNull(failure.get()); assertFalse(reader.isAlive());
    }
    @Test(timeout = 5000) public void sinkFailureRequestsStop() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        assertIo(() -> source.readNext((data, length) -> { throw new IOException("sink failed"); }));
        assertTrue(fake.stops.get() > 0); source.close(); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void nativeReadFailureNeverCallsSink() throws Exception {
        Fake fake = new Fake(); fake.count = -5;
        NativeUsbfsByteSource source = source(fake); source.open();
        assertIo(() -> source.readNext((data, length) -> fail("Corrupt data escaped")));
        source.close(); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void emptyReadInterruptionStopsProducer() throws Exception {
        Fake fake = new Fake(); fake.count = 0;
        NativeUsbfsByteSource source = source(fake); source.open();
        Thread.currentThread().interrupt();
        try {
            source.readNext((data, length) -> fail("Empty sink called"));
            fail("Expected interruption");
        } catch (InterruptedException expected) { assertTrue(fake.stops.get() > 0); }
        finally { Thread.interrupted(); source.close(); }
    }
    @Test(timeout = 5000) public void uncheckedSinkFailureStopsProducer() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        try {
            source.readNext((data, length) -> { throw new IllegalStateException("sink"); });
            fail("Expected unchecked failure");
        } catch (IllegalStateException expected) { assertTrue(fake.stops.get() > 0); }
        finally { source.close(); }
    }
    @Test(timeout = 5000) public void failedDrainStillDestroysExactlyOnceAndReportsFailure() throws Exception {
        Fake fake = new Fake(); fake.drained = false;
        NativeUsbfsByteSource source = source(fake); source.open();
        assertIo(source::close); source.close(); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void interruptedCloseStillJoinsAndPreservesInterrupt() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        Thread.currentThread().interrupt();
        try { source.close(); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
        assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void preservesUrbStatusAndErrnoInReadAndCloseFailures() throws Exception {
        int[][] cases = {{2, -75, 0}, {2, -71, 0}, {2, -19, 0}, {4, 0, 19}, {1, -75, 0}, {1, 0, 19}};
        for (int[] c : cases) {
            Fake fake = new Fake(); fake.reason = c[0]; fake.status = c[1]; fake.errno = c[2]; fake.count = -5;
            NativeUsbfsByteSource source = source(fake); source.open();
            String expected = "Native TS failed: reason=" + c[0] + ",status=" + c[1] + ",errno=" + c[2];
            try { source.readNext((data, length) -> fail("Failed URB reached sink")); fail("Expected failure"); }
            catch (IOException e) { assertEquals(expected, e.getMessage()); }
            try { source.close(); fail("Expected close failure"); }
            catch (IOException e) { assertEquals(expected, e.getMessage()); }
            assertEquals(1, fake.detailReads.get()); assertEquals(1, fake.destroyed.get());
        }
    }
    @Test(timeout = 5000) public void drainFailureDoesNotMaskUrbStatus() throws Exception {
        Fake fake = new Fake(); fake.reason = 2; fake.status = -75; fake.drained = false;
        NativeUsbfsByteSource source = source(fake); source.open();
        try { source.close(); fail("Expected failure"); }
        catch (IOException e) {
            assertEquals("Native TS failed: reason=2,status=-75,errno=0", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
        }
    }
    @Test(timeout = 5000) public void boundedSnapshotsRemainAvailableAfterCloseWithoutNativeAccess() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake);
        assertFalse(source.snapshotCompletions().isFinished());
        source.open(); assertTrue(fake.running.await(1, TimeUnit.SECONDS));
        NativeUsbfsByteSource.CompletionSnapshot live = source.snapshotCompletions();
        assertEquals(1, live.getRecordCount()); assertEquals(65536, live.getCapacity());
        assertEquals(123456789L, live.getMonotonicNanos(0)); assertEquals(188, live.getLength(0));
        assertEquals(0, live.getStatus(0)); assertFalse(live.isFinished());
        assertEquals(123450000L, live.getWindowStartMonotonicNanos());
        assertEquals(-1, live.getWindowEndMonotonicNanos());
        fake.trace[2] = 1; fake.trace[3] = 1;
        source.close();
        NativeUsbfsByteSource.CompletionSnapshot ended = source.snapshotCompletions();
        assertTrue(ended.isFinished()); assertTrue(ended.isOverflow()); assertTrue(ended.isClockInvalid());
        assertTrue(ended.isTruncated()); assertFalse(ended.isComplete());
        assertEquals(123460000L, ended.getWindowEndMonotonicNanos());
        assertFalse(live.isOverflow()); assertSame(ended, source.snapshotCompletions());
        assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void snapshotFailureStillReleasesNativeHandle() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open(); fake.snapshotFails = true;
        assertIo(source::close); source.close(); assertEquals(1, fake.destroyed.get());
    }
    @Test(timeout = 5000) public void recursiveReadIsRejectedAndStopsSource() throws Exception {
        Fake fake = new Fake(); NativeUsbfsByteSource source = source(fake); source.open();
        assertIo(() -> source.readNext((data, length) -> {
            try { source.readNext((ignored, size) -> fail("Recursive sink called")); }
            catch (InterruptedException e) { throw new IOException(e); }
        }));
        source.close();
    }
}
