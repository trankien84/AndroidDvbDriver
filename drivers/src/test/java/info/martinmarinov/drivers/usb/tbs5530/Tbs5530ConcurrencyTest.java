// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.*;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

public class Tbs5530ConcurrencyTest {
    private static final class Fixture implements Tbs5530Session.Resources, Tbs5530ControlIo {
        final Tbs5530Session session = new Tbs5530Session();
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch entered = new CountDownLatch(1), complete = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Throwable> result = new AtomicReference<>();
        final long token;
        IOException transportFailure;
        boolean failCleanup;
        Fixture() {
            token = session.requestPermission();
            assertTrue(session.permissionResult(token, true));
            assertTrue(session.connected(token, this));
        }
        public int controlTransfer(int type, int request, int value, int index, byte[] data,
                                   int offset, int length, int timeout) throws IOException {
            calls.incrementAndGet(); events.add("enter"); entered.countDown();
            try {
                if (!complete.await(3, TimeUnit.SECONDS)) throw new IOException("Test transfer not released");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            events.add("complete");
            if (transportFailure != null) throw transportFailure;
            return length;
        }
        Thread startRead() {
            Thread thread = new Thread(() -> {
                try { new Tbs5530Bridge(this, session, token).i2cWriteRead(0x64, 0,
                        new byte[]{1}, 0, 1, 0x64, 1, new byte[1], 0, 1); }
                catch (Throwable e) { result.set(e); }
            }, "fake-control-transfer");
            thread.start(); return thread;
        }
        public void cancel() throws IOException { event("cancel"); }
        public void release() throws IOException { event("release"); }
        public void close() throws IOException { event("close"); }
        private void event(String name) throws IOException {
            events.add(name);
            if (failCleanup) throw new IOException(name);
        }
    }

    private void invalidationWhileBlocked(boolean detach) throws Exception {
        Fixture f = new Fixture(); f.session.interfaceClaimed(f.token);
        f.session.ready(f.token); f.session.streamingStarted(f.token);
        Thread transfer = f.startRead();
        CountDownLatch invalidated = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        Thread callback = new Thread(() -> {
            try { if (detach) f.session.detached(f.token); else f.session.cancel(f.token); }
            catch (Throwable e) { callbackFailure.set(e); }
            finally { invalidated.countDown(); }
        }, "fake-lifecycle-event");
        try {
            assertTrue(f.entered.await(1, TimeUnit.SECONDS)); callback.start();
            assertTrue("Lifecycle invalidation must not wait for synchronous I/O", invalidated.await(1, TimeUnit.SECONDS));
            assertEquals(detach ? Tbs5530Session.State.DISCONNECTED : Tbs5530Session.State.CLOSED,
                    f.session.getState());
            assertEquals(Collections.singletonList("enter"), f.events);
            assertTrue(f.session.isCancellationRequested());
            assertTrue(f.session.isCleanupPending());
            assertThrows(IllegalStateException.class, f.session::requestPermission);
            assertThrows(TransferException.class, () -> f.session.ready(f.token));
            f.session.close(); assertEquals(Collections.singletonList("enter"), f.events);
        } finally {
            f.complete.countDown(); transfer.join(3000); if (callback.getState() != Thread.State.NEW) callback.join(3000);
        }
        assertFalse(transfer.isAlive()); assertFalse(callback.isAlive()); assertNull(callbackFailure.get());
        assertTrue(f.result.get() instanceof TransferException);
        assertEquals(1, f.calls.get());
        assertEquals(Arrays.asList("enter", "complete", "cancel", "release", "close"), f.events);
        f.session.close(); assertEquals(5, f.events.size());
    }

    @Test(timeout = 10000) public void detachInvalidatesBeforeBlockedTransferReturns() throws Exception {
        invalidationWhileBlocked(true);
    }
    @Test(timeout = 10000) public void cancellationInvalidatesBeforeBlockedPointerReturns() throws Exception {
        invalidationWhileBlocked(false);
    }

    @Test(timeout = 10000) public void deferredCleanupKeepsAllFailuresAndAllowsOnlyFreshGeneration() throws Exception {
        Fixture f = new Fixture(); f.failCleanup = true;
        f.session.interfaceClaimed(f.token); f.session.ready(f.token); f.session.streamingStarted(f.token);
        Thread transfer = f.startRead();
        try {
            assertTrue(f.entered.await(1, TimeUnit.SECONDS)); f.session.close();
            assertTrue(f.session.isCleanupPending()); assertNull(f.session.getCleanupFailure());
        } finally { f.complete.countDown(); transfer.join(3000); }
        assertFalse(transfer.isAlive()); assertFalse(f.session.isCleanupPending());
        IOException cleanup = f.session.getCleanupFailure(); assertNotNull(cleanup);
        assertEquals("cancel", cleanup.getMessage());
        assertEquals(2, cleanup.getSuppressed().length);
        assertEquals("release", cleanup.getSuppressed()[0].getMessage());
        assertEquals("close", cleanup.getSuppressed()[1].getMessage());
        assertSame(cleanup, f.result.get().getSuppressed()[0]);
        assertEquals(1, f.calls.get());
        f.failCleanup = false;
        long fresh = f.session.requestPermission(); assertTrue(fresh > f.token);
        assertFalse(f.session.failed(f.token, new IOException("late")));
        assertFalse(f.session.detached(f.token));
        assertNull(f.session.getCleanupFailure()); assertFalse(f.session.isCancellationRequested());
        f.session.close(); assertEquals(Arrays.asList("enter", "complete", "cancel", "release", "close"), f.events);
    }

    @Test(timeout = 10000) public void lateTimeoutCannotReplaceDisconnectedStateOrTriggerRetry() throws Exception {
        Fixture f = new Fixture(); f.transportFailure = new java.net.SocketTimeoutException("deadline");
        Thread transfer = f.startRead();
        try {
            assertTrue(f.entered.await(1, TimeUnit.SECONDS)); f.session.detached(f.token);
        } finally { f.complete.countDown(); transfer.join(3000); }
        assertFalse(transfer.isAlive());
        TransferException error = (TransferException) f.result.get();
        assertSame(f.transportFailure, error.getCause());
        assertEquals(Tbs5530ControlIo.FailureKind.TIMEOUT, error.getKind());
        assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        assertNull(f.session.getFailure()); assertEquals(1, f.calls.get());
        assertEquals(Arrays.asList("enter", "complete", "close"), f.events);
    }

    @Test(timeout = 10000) public void queuedTransactionCannotUseClosedConnectionOrNewGeneration() throws Exception {
        Fixture f = new Fixture(); Thread active = f.startRead();
        AtomicReference<Throwable> queuedError = new AtomicReference<>();
        CountDownLatch queuedStarted = new CountDownLatch(1);
        Thread queued = new Thread(() -> {
            queuedStarted.countDown();
            try { new Tbs5530Bridge(f, f.session, f.token).selectTerrestrialStream(); }
            catch (Throwable e) { queuedError.set(e); }
        });
        try {
            assertTrue(f.entered.await(1, TimeUnit.SECONDS)); queued.start();
            assertTrue(queuedStarted.await(1, TimeUnit.SECONDS)); f.session.close();
        } finally {
            f.complete.countDown(); active.join(3000);
            if (queued.getState() != Thread.State.NEW) queued.join(3000);
        }
        assertFalse(active.isAlive()); assertFalse(queued.isAlive());
        assertTrue(queuedError.get() instanceof TransferException); assertEquals(1, f.calls.get());
        long fresh = f.session.requestPermission(); assertTrue(fresh > f.token);
        assertFalse(f.session.detached(f.token)); f.session.close();
        assertEquals(Arrays.asList("enter", "complete", "close"), f.events);
    }

    @Test(timeout = 10000) public void transportCallbackDetachDefersCleanupUntilCallbackReturns() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); List<String> events = new ArrayList<>();
        long token = session.requestPermission(); session.permissionResult(token, true);
        session.connected(token, new Tbs5530Session.Resources() {
            public void cancel() { events.add("cancel"); }
            public void release() { events.add("release"); }
            public void close() { events.add("close"); }
        });
        Tbs5530ControlIo io = (type, request, value, index, data, offset, length, timeout) -> {
            events.add("enter"); session.detached(token);
            assertTrue(session.isCleanupPending()); assertEquals(Collections.singletonList("enter"), events);
            events.add("return"); return length;
        };
        assertThrows(TransferException.class, () -> new Tbs5530Bridge(io, session, token).selectTerrestrialStream());
        assertEquals(Arrays.asList("enter", "return", "close"), events);
        assertFalse(session.isCleanupPending());
    }

    @Test(timeout = 10000) public void timeoutAndIoFailureBetweenReadHalvesFaultWithoutPublishingBytes() throws Exception {
        for (IOException problem : Arrays.asList(new IOException("io"), new java.net.SocketTimeoutException("timeout"))) {
            Fixture f = new Fixture(); AtomicInteger calls = new AtomicInteger(); byte[] output = {9};
            Tbs5530ControlIo io = (type, request, value, index, data, offset, length, timeout) -> {
                if (calls.incrementAndGet() == 1) return length;
                data[offset] = 42; throw problem;
            };
            TransferException error = assertThrows(TransferException.class, () ->
                    new Tbs5530Bridge(io, f.session, f.token).i2cWriteRead(0x64, 0,
                            new byte[]{1}, 0, 1, 0x64, 1, output, 0, 1));
            assertSame(problem, error.getCause()); assertArrayEquals(new byte[]{9}, output);
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            assertThrows(TransferException.class, () -> new Tbs5530Bridge(io, f.session, f.token).selectTerrestrialStream());
            assertEquals(2, calls.get()); f.session.close();
        }
    }

    @Test(timeout = 10000) public void deferredCleanupMayJoinWorkerAfterBothLocksAreReleased() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); long token = session.requestPermission();
        session.permissionResult(token, true); AtomicReference<Throwable> workerError = new AtomicReference<>();
        AtomicInteger ioCalls = new AtomicInteger();
        Tbs5530ControlIo io = (type, request, value, index, data, offset, length, timeout) -> {
            ioCalls.incrementAndGet(); session.close(); return length;
        };
        session.connected(token, new Tbs5530Session.Resources() {
            public void cancel() throws IOException {
                Thread worker = new Thread(() -> {
                    try { new Tbs5530Bridge(io, session, token).selectTerrestrialStream(); }
                    catch (Throwable e) { workerError.set(e); }
                });
                worker.start();
                try { worker.join(2000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                assertFalse("Cleanup held a lock required by worker", worker.isAlive());
            }
            public void release() { }
            public void close() { }
        });
        session.interfaceClaimed(token); session.ready(token); session.streamingStarted(token);
        assertThrows(TransferException.class, () -> new Tbs5530Bridge(io, session, token).selectTerrestrialStream());
        assertTrue(workerError.get() instanceof TransferException); assertEquals(1, ioCalls.get());
        assertFalse(session.isCleanupPending());
    }

    @Test(timeout = 10000) public void concurrentCloseDuringCleanupDoesNotTakeOwnershipTwice() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch releaseEntered = new CountDownLatch(1), finishRelease = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        long token = session.requestPermission(); session.permissionResult(token, true);
        session.connected(token, new Tbs5530Session.Resources() {
            public void cancel() { events.add("cancel"); }
            public void release() throws IOException {
                events.add("release"); releaseEntered.countDown();
                try { if (!finishRelease.await(3, TimeUnit.SECONDS)) throw new IOException("Release not resumed"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            public void close() { events.add("close"); }
        });
        session.interfaceClaimed(token);
        Thread closing = new Thread(() -> { try { session.close(); } catch (Throwable e) { error.set(e); } });
        closing.start();
        try {
            assertTrue(releaseEntered.await(1, TimeUnit.SECONDS)); session.close();
            assertFalse(session.detached(token)); assertTrue(session.isCleanupPending());
            assertThrows(IllegalStateException.class, session::requestPermission);
            assertEquals(Collections.singletonList("release"), events);
        } finally { finishRelease.countDown(); closing.join(3000); }
        assertFalse(closing.isAlive()); assertNull(error.get());
        assertFalse(session.isCleanupPending()); assertEquals(Arrays.asList("release", "close"), events);
        session.close(); assertEquals(2, events.size());
    }

    @Test(timeout = 30000) public void repeatedDetachAndCancelRacesDoNotLeakOrReplay() throws Exception {
        for (int iteration = 0; iteration < 40; iteration++) invalidationWhileBlocked(iteration % 2 == 0);
    }
}
