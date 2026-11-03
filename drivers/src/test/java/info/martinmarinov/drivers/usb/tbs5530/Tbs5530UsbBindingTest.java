// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class Tbs5530UsbBindingTest {
    private static final Tbs5530UsbBinding.Device A = new Tbs5530UsbBinding.Device("/usb/a", 1, 0x734c, 0x5530);
    private static final Tbs5530UsbBinding.Device B = new Tbs5530UsbBinding.Device("/usb/b", 2, 0x734c, 0x5530);
    private static final class QueueWorker implements Executor {
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        public void execute(Runnable job) { queue.add(job); }
        void drain() { Runnable job; while ((job = queue.poll()) != null) job.run(); }
    }
    private static final class Clock implements Tbs5530UsbBinding.Scheduler {
        final List<Timer> tasks = new CopyOnWriteArrayList<>();
        static final class Timer implements Tbs5530UsbBinding.Cancellation {
            final Runnable task; final long delay; boolean cancelled;
            Timer(Runnable task, long delay) { this.task = task; this.delay = delay; }
            public void cancel() { cancelled = true; }
        }
        public Tbs5530UsbBinding.Cancellation schedule(Runnable task, long delay) {
            Timer timer = new Timer(task, delay); tasks.add(timer); return timer;
        }
        void expire(long delay) { for (Timer timer : new ArrayList<>(tasks)) if (timer.delay == delay && !timer.cancelled) { timer.cancel(); timer.task.run(); } }
    }
    private static final class Connection implements Tbs5530LifecycleCoordinator.Connection {
        final List<String> calls = new CopyOnWriteArrayList<>();
        CountDownLatch claimEntered, finishClaim;
        boolean claimFails, releaseFails, closeFails;
        Runnable claimed;
        public void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException {
            calls.add("claim"); await(claimEntered, finishClaim); receipt.acquired();
            if (claimed != null) claimed.run();
            if (claimFails) throw new IOException("claim failure");
        }
        public void cancel() { calls.add("cancel"); }
        public void release() throws IOException { calls.add("release"); if (releaseFails) throw new IOException("release failure"); }
        public void close() throws IOException { calls.add("close"); if (closeFails) throw new IOException("close failure"); }
    }
    private static void await(CountDownLatch entered, CountDownLatch resume) throws IOException {
        if (entered == null) return; entered.countDown();
        try { if (!resume.await(3, TimeUnit.SECONDS)) throw new IOException("Fake operation not resumed"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }
    private static final class Usb implements Tbs5530UsbBinding.Facade {
        final List<Tbs5530UsbBinding.Device> devices = new CopyOnWriteArrayList<>(Arrays.asList(A, B));
        final List<Connection> connections = new CopyOnWriteArrayList<>();
        Tbs5530UsbBinding.PermissionCallback callback;
        Tbs5530UsbBinding.Events events;
        String correlation;
        boolean permission;
        int opens, requests, receiverCloses, eventCloses;
        CountDownLatch openEntered, finishOpen;
        Connection next;
        IOException openError;
        Runnable opened;
        public List<Tbs5530UsbBinding.Device> devices() { return new ArrayList<>(devices); }
        public boolean hasPermission(Tbs5530UsbBinding.Device device) { return permission; }
        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) { this.events = events; return () -> eventCloses++; }
        public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device device, String correlation,
                                                               Tbs5530UsbBinding.PermissionCallback callback) {
            requests++; this.correlation = correlation; this.callback = callback;
            return () -> receiverCloses++;
        }
        public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device device) throws IOException {
            opens++; await(openEntered, finishOpen);
            if (openError != null) throw openError;
            Connection connection = next == null ? new Connection() : next;
            next = null; connections.add(connection); if (opened != null) opened.run(); return connection;
        }
        void grant() { permission = true; callback.result(correlation, A, true); }
    }
    private static final class Fixture implements AutoCloseable {
        final Tbs5530Session session = new Tbs5530Session();
        final Usb usb = new Usb(); final QueueWorker worker = new QueueWorker(); final Clock clock = new Clock();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final Tbs5530UsbBinding binding;
        Fixture() throws IOException { binding = new Tbs5530UsbBinding(session, usb, worker, clock, errors::add, 10, 20); }
        void request() throws IOException { binding.start(A); worker.drain(); }
        public void close() throws IOException { binding.close(); worker.drain(); }
    }

    @Test public void constructorDoesNotRequestOrOpenAndGrantRunsOnlyOnWorker() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0, f.usb.opens); assertEquals(0, f.usb.requests);
            f.binding.start(A); assertEquals(0, f.usb.requests); f.worker.drain();
            assertEquals(1, f.usb.requests); f.usb.grant(); assertEquals(0, f.usb.opens);
            f.worker.drain(); assertEquals(1, f.usb.opens); assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
            assertTrue(f.session.isInterfaceClaimed()); assertFalse(f.session.isStreaming()); assertFalse(f.session.isFrontendInitialized());
            assertEquals(1, f.usb.receiverCloses); assertTrue(f.errors.isEmpty());
        }
    }
    @Test public void alreadyGrantedStillAcquiresOffCallerThreadWithoutDialog() throws Exception {
        try (Fixture f = new Fixture()) {
            f.usb.permission = true; f.binding.start(A); assertEquals(0, f.usb.opens);
            f.worker.drain(); assertEquals(0, f.usb.requests); assertEquals(1, f.usb.opens);
        }
    }
    @Test public void denialAndPermissionTimeoutNeverOpen() throws Exception {
        for (boolean timeout : Arrays.asList(false, true)) try (Fixture f = new Fixture()) {
            f.request();
            if (timeout) f.clock.expire(10); else f.usb.callback.result(f.usb.correlation, A, false);
            f.worker.drain(); assertEquals(0, f.usb.opens); assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
            assertEquals(1, f.usb.receiverCloses);
            f.usb.grant(); f.worker.drain(); assertEquals(0, f.usb.opens);
        }
    }
    @Test public void malformedWrongCorrelationAndWrongDeviceAreRejectedWithoutConsumingRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.permission = true;
            f.usb.callback.result(null, A, true); f.usb.callback.result(f.usb.correlation, null, true);
            f.usb.callback.result(f.usb.correlation, A, null); f.usb.callback.result(f.usb.correlation, B, true);
            f.worker.drain(); assertEquals(0, f.usb.opens);
            f.usb.grant(); f.usb.grant(); f.worker.drain(); assertEquals(1, f.usb.opens);
        }
    }
    @Test public void broadcastGrantMustStillHaveLivePermissionAndDevice() throws Exception {
        for (boolean missing : Arrays.asList(false, true)) try (Fixture f = new Fixture()) {
            f.request(); if (missing) f.usb.devices.remove(A);
            f.usb.callback.result(f.usb.correlation, A, true); f.worker.drain(); assertEquals(0, f.usb.opens);
        }
    }
    @Test public void detachBeforeGrantIgnoresLateCallback() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.devices.remove(A); f.usb.events.detached(A); f.clock.expire(0);
            f.usb.grant(); f.worker.drain(); assertEquals(0, f.usb.opens);
            assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        }
    }
    @Test public void reusedPathGetsFreshEpochCorrelationAndCannotConsumeOldCallback() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); String old = f.usb.correlation; Tbs5530UsbBinding.PermissionCallback callback = f.usb.callback;
            long token = f.session.getGeneration(); f.binding.cancel(); f.worker.drain();
            f.request(); assertTrue(f.session.getGeneration() > token); assertNotEquals(old, f.usb.correlation);
            f.usb.permission = true; callback.result(old, A, true); f.worker.drain(); assertEquals(0, f.usb.opens);
            f.usb.grant(); f.worker.drain(); assertEquals(1, f.usb.opens);
        }
    }
    @Test public void lateDetachWithLiveReusedDeviceDoesNotCloseCurrentConnection() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.grant(); f.worker.drain();
            f.usb.events.detached(A); f.clock.expire(0); f.worker.drain();
            assertEquals(Collections.singletonList("claim"), f.usb.connections.get(0).calls);
            assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        }
    }
    @Test public void newAttachAtSelectedPathInvalidatesInsteadOfAssumingSameSession() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.grant(); f.worker.drain();
            f.usb.events.attached(A); f.worker.drain();
            assertEquals(Arrays.asList("claim", "release", "close"), f.usb.connections.get(0).calls);
            assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        }
    }
    @Test public void otherDeviceWithSameVidPidDoesNotDetachSelectedDevice() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.grant(); f.worker.drain(); f.usb.events.attached(B); f.usb.events.detached(B);
            f.clock.expire(0); f.worker.drain(); assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        }
    }
    @Test public void stalePermissionDeadlineCannotInvalidateAcquisitionOrReadyConnection() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); Clock.Timer old = f.clock.tasks.get(0); f.usb.grant(); f.worker.drain();
            old.task.run(); f.clock.expire(20); f.worker.drain();
            assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState()); assertTrue(f.errors.isEmpty());
        }
    }
    @Test public void partialClaimFailureKeepsCleanupFailuresSuppressedWithoutRetry() throws Exception {
        try (Fixture f = new Fixture()) {
            Connection connection = new Connection(); connection.claimFails = true; connection.releaseFails = true; connection.closeFails = true;
            f.usb.next = connection; f.request(); f.usb.grant(); f.worker.drain();
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState()); assertEquals(1, f.errors.size());
            assertEquals(2, f.errors.get(0).getSuppressed().length);
            assertEquals(Arrays.asList("claim", "release", "close"), connection.calls);
            f.usb.grant(); f.worker.drain(); assertEquals(1, f.usb.opens);
        }
    }
    @Test public void permissionRevokedDuringClaimCannotBecomeSessionOwned() throws Exception {
        try (Fixture f = new Fixture()) {
            Connection connection = new Connection(); connection.claimed = () -> f.usb.permission = false;
            f.usb.next = connection; f.request(); f.usb.grant(); f.worker.drain();
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState()); assertFalse(f.session.isInterfaceClaimed());
            assertEquals(Arrays.asList("claim", "release", "close"), connection.calls); assertEquals(1, f.errors.size());
        }
    }
    @Test public void identityChangesDuringOpenClosesOnlyReturnedResourceAndKeepsCleanupCause() throws Exception {
        try (Fixture f = new Fixture()) {
            Connection connection = new Connection(); connection.closeFails = true; f.usb.next = connection;
            f.usb.opened = () -> f.usb.devices.remove(A); f.request(); f.usb.grant(); f.worker.drain();
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            assertEquals(Collections.singletonList("close"), connection.calls);
            assertEquals(1, f.errors.size()); assertEquals(1, f.errors.get(0).getSuppressed().length);
            assertEquals("close failure", f.errors.get(0).getSuppressed()[0].getMessage());
            f.usb.grant(); f.worker.drain(); assertEquals(1, f.usb.opens);
        }
    }
    @Test public void openFailureFaultsSessionAndNeverClaims() throws Exception {
        try (Fixture f = new Fixture()) {
            f.usb.openError = new IOException("open"); f.request(); f.usb.grant(); f.worker.drain();
            assertSame(f.usb.openError, f.errors.get(0)); assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            assertEquals(1, f.usb.opens); assertTrue(f.usb.connections.isEmpty());
        }
    }
    @Test public void permissionSchedulerShutdownRejectsBeforeAnyIo() throws Exception {
        Usb usb = new Usb(); Tbs5530UsbBinding binding = new Tbs5530UsbBinding(new Tbs5530Session(), usb,
                new QueueWorker(), (task, delay) -> { throw new RejectedExecutionException("clock stopped"); },
                error -> fail(error.toString()), 10, 20);
        assertThrows(RejectedExecutionException.class, () -> binding.start(A));
        assertFalse(binding.isPending()); assertEquals(0, usb.requests); assertEquals(0, usb.opens); binding.close();
    }
    @Test public void changedOsIdentityBeforeQueuedGrantCannotOpen() throws Exception {
        try (Fixture f = new Fixture()) {
            f.request(); f.usb.grant(); f.usb.devices.remove(A);
            f.usb.devices.add(new Tbs5530UsbBinding.Device(A.path, 99, A.vendorId, A.productId));
            f.worker.drain(); assertEquals(0, f.usb.opens); assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        }
    }
    @Test public void cleanupIsQueuedRatherThanRunByLogicalCancellationCaller() throws Exception {
        Fixture f = new Fixture(); f.request(); f.usb.grant(); f.worker.drain();
        Connection connection = f.usb.connections.get(0); f.binding.cancel();
        assertEquals(Tbs5530Session.State.CLOSED, f.session.getState()); assertTrue(f.session.isCleanupPending());
        assertEquals(Collections.singletonList("claim"), connection.calls);
        f.worker.drain(); assertEquals(Arrays.asList("claim", "release", "close"), connection.calls);
        assertFalse(f.session.isCleanupPending()); f.close();
    }
    @Test public void repeatedCloseCancelsReceiverOnceAndPreventsReopen() throws Exception {
        Fixture f = new Fixture(); f.request(); f.binding.close(); f.binding.close(); f.worker.drain();
        assertEquals(1, f.usb.eventCloses); assertEquals(1, f.usb.receiverCloses);
        assertThrows(IllegalStateException.class, () -> f.binding.start(A)); assertFalse(f.binding.isPending());
    }

    private void blockedAcquisition(boolean claim, Boolean timeout) throws Exception {
        Fixture f = new Fixture(); CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        Connection connection = new Connection(); f.usb.next = connection;
        if (claim) { connection.claimEntered = entered; connection.finishClaim = resume; }
        else { f.usb.openEntered = entered; f.usb.finishOpen = resume; }
        f.request(); f.usb.grant();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { f.worker.drain(); } catch (Throwable e) { error.set(e); } }); worker.start();
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            if (timeout == null) f.binding.cancel();
            else if (timeout) f.clock.expire(20);
            else { f.usb.devices.remove(A); f.usb.events.detached(A); f.clock.expire(0); }
            assertEquals(timeout == null || timeout ? Tbs5530Session.State.CLOSED : Tbs5530Session.State.DISCONNECTED, f.session.getState());
            assertTrue(f.binding.isPending()); assertThrows(IllegalStateException.class, () -> f.binding.start(A));
            assertFalse(connection.calls.contains("close"));
        } finally { resume.countDown(); worker.join(3000); f.close(); }
        assertFalse(worker.isAlive()); assertNull(error.get());
        assertEquals(claim ? Arrays.asList("claim", "release", "close") : Collections.singletonList("close"), connection.calls);
        assertEquals(1, f.usb.opens);
    }
    @Test(timeout = 10000) public void cancelDuringBlockedOpenDefersPhysicalClose() throws Exception { blockedAcquisition(false, null); }
    @Test(timeout = 10000) public void cancelDuringBlockedClaimDefersPhysicalClose() throws Exception { blockedAcquisition(true, null); }
    @Test public void shutdownBeforeSubmissionInvalidatesWithoutCallerThreadIo() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); Usb usb = new Usb(); List<Throwable> errors = new ArrayList<>();
        Tbs5530UsbBinding binding = new Tbs5530UsbBinding(session, usb,
                job -> { throw new RejectedExecutionException("shutdown"); }, new Clock(), errors::add, 10, 20);
        binding.start(A); assertFalse(binding.isPending()); assertEquals(0, usb.opens); assertEquals(0, usb.requests);
        assertEquals(1, errors.size()); assertTrue(errors.get(0) instanceof RejectedExecutionException);
        binding.close(); assertEquals(1, usb.eventCloses);
    }
    @Test(timeout = 10000) public void detachDuringBlockedOpenDefersPhysicalClose() throws Exception { blockedAcquisition(false, false); }
    @Test(timeout = 10000) public void detachDuringBlockedClaimDefersPhysicalRelease() throws Exception { blockedAcquisition(true, false); }
    @Test(timeout = 10000) public void deadlineDuringBlockedOpenIsLogicalNotPhysicalCancellation() throws Exception { blockedAcquisition(false, true); }
    @Test(timeout = 10000) public void deadlineDuringBlockedClaimIsLogicalNotPhysicalCancellation() throws Exception { blockedAcquisition(true, true); }
}
