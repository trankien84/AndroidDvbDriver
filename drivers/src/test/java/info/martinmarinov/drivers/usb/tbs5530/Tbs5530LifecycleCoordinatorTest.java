// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.*;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530LifecycleCoordinator.*;

public class Tbs5530LifecycleCoordinatorTest {
    private interface Action { void run() throws IOException; }

    private static AttachmentIdentity identity(long attachmentId) {
        return new AttachmentIdentity("usb/device/7", 7, 0x734c, 0x5530, attachmentId);
    }

    private static final class Fixture {
        final Tbs5530Session session = new Tbs5530Session();
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        final AttachmentIdentity identity = identity(1);
        final FakeConnection connection = new FakeConnection(this);
        Action onRequest = () -> { };
        Action onOpen = () -> { };
        boolean nullConnection;
        final Tbs5530LifecycleCoordinator coordinator = new Tbs5530LifecycleCoordinator(session,
                request -> {
                    unlocked();
                    calls.add("request");
                    assertEquals(Tbs5530Session.State.WAIT_PERMISSION, session.getState());
                    onRequest.run();
                }, attachment -> {
                    unlocked();
                    calls.add("open");
                    assertEquals(identity, attachment);
                    onOpen.run();
                    return nullConnection ? null : connection;
                });

        void unlocked() {
            assertFalse("External callback holds session monitor", Thread.holdsLock(session));
            assertFalse("External callback holds coordinator monitor", Thread.holdsLock(coordinator));
        }

        Request request() throws IOException { return coordinator.requestPermission(identity); }
        boolean grant(Request request) throws IOException {
            return coordinator.permissionResult(request, request.getIdentity(), true);
        }
        void assertCalls(String... expected) { assertEquals(Arrays.asList(expected), calls); }
    }

    private static final class FakeConnection implements Connection {
        final Fixture fixture;
        Action onClaim = () -> { };
        Action onRelease = () -> { };
        Action onClose = () -> { };
        Action onCancel = () -> { };
        boolean markClaimed = true;
        IOException claimFailure;
        FakeConnection(Fixture fixture) { this.fixture = fixture; }

        @Override public void claim(Claim claim) throws IOException {
            record("claim");
            if (markClaimed) claim.acquired();
            onClaim.run();
            if (claimFailure != null) throw claimFailure;
        }
        @Override public void cancel() throws IOException { record("cancel"); onCancel.run(); }
        @Override public void release() throws IOException { record("release"); onRelease.run(); }
        @Override public void close() throws IOException { record("close"); onClose.run(); }
        private void record(String call) { fixture.unlocked(); fixture.calls.add(call); }
    }

    @Test public void identityIncludesAttachmentPathDeviceIdAndGenerationNotOnlyVidPid() {
        AttachmentIdentity first = identity(1);
        AttachmentIdentity equivalent = identity(1);
        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, identity(2));
        assertNotEquals(first, new AttachmentIdentity("usb/device/8", 7, 0x734c, 0x5530, 1));
        assertNotEquals(first, new AttachmentIdentity("usb/device/7", 8, 0x734c, 0x5530, 1));
        assertNotEquals(first, new AttachmentIdentity("usb/device/7", 7, 0x734d, 0x5530, 1));
        assertNotEquals(first, new AttachmentIdentity("usb/device/7", 7, 0x734c, 0x5531, 1));
        assertNotEquals(first, null);
        assertNotEquals(first, "device");
        assertEquals("usb/device/7", first.getDevicePath());
        assertEquals(7, first.getDeviceId());
        assertEquals(0x734c, first.getVendorId());
        assertEquals(0x5530, first.getProductId());
        assertEquals(1, first.getAttachmentId());
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentIdentity("", 7, 0x734c, 0x5530, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentIdentity("path", 7, 65536, 0x5530, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentIdentity("path", 7, 0x734c, 0x5530, -1));
    }

    @Test public void grantConnectsAndClaimsButRequiresIndependentReadyVerification() throws Exception {
        Fixture f = new Fixture();
        Request request = f.request();
        assertSame(f.identity, request.getIdentity());
        assertEquals(f.session.getGeneration(), request.getToken());
        assertTrue(f.grant(request));
        assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        assertFalse(f.session.isStreaming());
        assertFalse(f.session.isFrontendLocked());
        assertThrows(IllegalStateException.class, () -> f.session.streamingStarted(request.getToken()));
        f.session.ready(request.getToken());
        f.session.streamingStarted(request.getToken());
        f.coordinator.close();
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "cancel", "release", "close");
    }

    @Test public void denialConsumesCallbackWithoutOpeningConnection() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        assertTrue(f.coordinator.permissionResult(r, f.identity, false));
        assertFalse(f.grant(r));
        assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
        f.coordinator.close();
        f.assertCalls("request");
    }

    @Test public void permissionTimeoutMakesLateResultHarmless() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        assertTrue(f.coordinator.permissionTimedOut(r));
        assertFalse(f.coordinator.permissionTimedOut(r));
        assertFalse(f.grant(r));
        assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
        f.assertCalls("request");
    }

    @Test public void cancelAndDetachWhileWaitingInvalidateWithoutAcquisition() throws Exception {
        for (boolean detach : new boolean[]{false, true}) {
            Fixture f = new Fixture(); Request r = f.request();
            assertTrue(detach ? f.coordinator.detached(r, f.identity) : f.coordinator.cancel(r));
            assertFalse(f.grant(r));
            assertEquals(detach ? Tbs5530Session.State.DISCONNECTED : Tbs5530Session.State.CLOSED,
                    f.session.getState());
            assertFalse(f.coordinator.cancel(r));
            f.assertCalls("request");
        }
    }

    @Test public void wrongIdentityDoesNotConsumeCorrectPermissionOrDetachEvent() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        assertFalse(f.coordinator.permissionResult(r, identity(2), true));
        assertFalse(f.coordinator.detached(r, identity(2)));
        assertEquals(Tbs5530Session.State.WAIT_PERMISSION, f.session.getState());
        assertTrue(f.coordinator.permissionResult(r, identity(1), true));
        assertFalse(f.coordinator.permissionTimedOut(r));
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void reconnectRejectsOldGenerationAndForeignRequest() throws Exception {
        Fixture f = new Fixture(); Request old = f.request();
        assertTrue(f.grant(old));
        assertTrue(f.coordinator.detached(old, f.identity));
        Request next = f.request();
        assertTrue(next.getToken() > old.getToken());
        assertFalse(f.grant(old));
        assertFalse(f.coordinator.cancel(old));
        assertFalse(f.coordinator.detached(old, f.identity));
        Fixture foreign = new Fixture(); Request foreignRequest = foreign.request();
        assertFalse(f.grant(foreignRequest));
        assertFalse(f.coordinator.permissionResult(null, f.identity, true));
        assertTrue(f.grant(next));
        assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
        f.coordinator.close(); foreign.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close",
                "request", "open", "claim", "release", "close");
    }

    @Test public void duplicateCallbackIsIgnoredBeforeAnySecondAcquisition() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.onOpen = () -> assertFalse(f.grant(r));
        f.connection.onClaim = () -> assertFalse(f.grant(r));
        assertTrue(f.grant(r));
        assertFalse(f.grant(r));
        assertFalse(f.coordinator.permissionResult(r, f.identity, false));
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void permissionRequestFailureFaultsAndPreservesOriginalCause() throws Exception {
        Fixture f = new Fixture(); IOException original = new IOException("request failed");
        f.onRequest = () -> { throw original; };
        assertSame(original, assertThrows(IOException.class, f::request));
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
        assertSame(original, f.session.getFailure());
        f.coordinator.close(); f.assertCalls("request");
    }

    @Test public void runtimePermissionFailureIsWrappedAndBlocksAutomaticRetry() throws Exception {
        Fixture f = new Fixture(); RuntimeException original = new IllegalStateException("request bug");
        f.onRequest = () -> { throw original; };
        IOException error = assertThrows(IOException.class, f::request);
        assertSame(original, error.getCause());
        assertSame(error, f.session.getFailure());
        assertThrows(IllegalStateException.class, f::request);
        f.coordinator.close(); f.assertCalls("request");
    }

    @Test public void openFailureAndNullConnectionFaultWithoutClaimOrCleanup() throws Exception {
        for (boolean returnsNull : new boolean[]{false, true}) {
            Fixture f = new Fixture(); Request r = f.request();
            IOException original = new IOException("open failed");
            f.nullConnection = returnsNull;
            if (!returnsNull) f.onOpen = () -> { throw original; };
            IOException failure = assertThrows(IOException.class, () -> f.grant(r));
            if (!returnsNull) assertSame(original, failure);
            else assertEquals("Connection port returned no connection", failure.getMessage());
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            assertSame(failure, f.session.getFailure());
            assertFalse(f.grant(r));
            f.coordinator.close(); f.assertCalls("request", "open");
        }
    }

    @Test public void failedClaimWithoutOwnershipOnlyClosesConnection() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.markClaimed = false;
        IOException original = f.connection.claimFailure = new IOException("claim rejected");
        assertSame(original, assertThrows(IOException.class, () -> f.grant(r)));
        assertSame(original, f.session.getFailure());
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "close");
    }

    @Test public void unconfirmedClaimCannotConnectOrBecomeReady() throws Exception {
        Fixture f = new Fixture(); Request r = f.request(); f.connection.markClaimed = false;
        IOException failure = assertThrows(IOException.class, () -> f.grant(r));
        assertEquals("Claim did not confirm ownership", failure.getMessage());
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
        assertThrows(Tbs5530ControlIo.TransferException.class, () -> f.session.ready(r.getToken()));
        f.coordinator.close(); f.assertCalls("request", "open", "claim", "close");
    }

    @Test public void partialClaimFailureReleasesAndClosesExactlyOnce() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        IOException original = f.connection.claimFailure = new IOException("after claim");
        assertSame(original, assertThrows(IOException.class, () -> f.grant(r)));
        assertSame(original, f.session.getFailure());
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
        f.coordinator.close(); f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void acquisitionAndBothCleanupFailuresPreserveSuppressedOrder() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        IOException claim = f.connection.claimFailure = new IOException("claim");
        IOException release = new IOException("release");
        RuntimeException close = new IllegalStateException("close");
        f.connection.onRelease = () -> { throw release; };
        f.connection.onClose = () -> { throw close; };
        IOException failure = assertThrows(IOException.class, () -> f.grant(r));
        assertSame(claim, failure);
        assertSame(claim, f.session.getFailure());
        assertEquals(2, failure.getSuppressed().length);
        assertSame(release, failure.getSuppressed()[0]);
        assertSame(close, failure.getSuppressed()[1].getCause());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void runtimeClaimFailurePreservesCauseAndStillCleansPartialOwnership() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        RuntimeException original = new IllegalStateException("claim bug");
        f.connection.onClaim = () -> { throw original; };
        IOException failure = assertThrows(IOException.class, () -> f.grant(r));
        assertSame(original, failure.getCause());
        assertSame(failure, f.session.getFailure());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void detachInsideOpenRejectsUnclaimedOwnershipAndSkipsClaim() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.onOpen = () -> assertTrue(f.coordinator.detached(r, f.identity));
        assertTrue(f.grant(r));
        assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        f.coordinator.close();
        f.assertCalls("request", "open", "close");
    }

    @Test public void detachInsideClaimDisposesRejectedClaimedOwnershipExactlyOnce() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.onClaim = () -> assertTrue(f.coordinator.detached(r, f.identity));
        assertTrue(f.grant(r));
        assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        assertNull(f.session.getFailure());
        f.coordinator.close(); f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void detachAndPartialClaimFailureCannotDoubleReleaseOrFaultDetachedSession() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.onClaim = () -> assertTrue(f.coordinator.detached(r, f.identity));
        IOException original = f.connection.claimFailure = new IOException("late claim failure");
        assertSame(original, assertThrows(IOException.class, () -> f.grant(r)));
        assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        assertNull(f.session.getFailure());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void rejectedOwnershipCleanupFailureIsVisibleWithoutResurrectingSession() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.onClaim = () -> assertTrue(f.coordinator.detached(r, f.identity));
        IOException release = new IOException("release"); IOException close = new IOException("close");
        f.connection.onRelease = () -> { throw release; };
        f.connection.onClose = () -> { throw close; };
        IOException failure = assertThrows(IOException.class, () -> f.grant(r));
        assertSame(release, failure); assertArrayEquals(new Throwable[]{close}, failure.getSuppressed());
        assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
        assertNull(f.session.getFailure());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    private static void await(CountDownLatch latch) throws IOException {
        try { assertTrue("Timed out waiting for worker", latch.await(3, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    @Test public void blockedOpenAllowsImmediateDetachButBlocksReopenUntilLateClose() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        f.onOpen = () -> { entered.countDown(); await(resume); };
        FutureTask<Boolean> callback = new FutureTask<>(() -> f.grant(r));
        Thread worker = new Thread(callback, "lifecycle-blocked-open"); worker.start();
        try {
            await(entered);
            assertTrue(f.coordinator.detached(r, f.identity));
            assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
            assertFalse(f.grant(r));
            assertThrows(IllegalStateException.class, f::request);
            f.assertCalls("request", "open");
        } finally { resume.countDown(); worker.join(4000); }
        assertFalse(worker.isAlive()); assertTrue(callback.get(1, TimeUnit.SECONDS));
        f.assertCalls("request", "open", "close");
        Request next = f.request(); assertTrue(next.getToken() > r.getToken());
        assertFalse(f.grant(r)); f.coordinator.close();
        f.assertCalls("request", "open", "close", "request");
    }

    @Test public void blockedClaimAllowsCancelAndDefersLocalReleaseUntilClaimReturns() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        f.connection.onClaim = () -> { entered.countDown(); await(resume); };
        FutureTask<Boolean> callback = new FutureTask<>(() -> f.grant(r));
        Thread worker = new Thread(callback, "lifecycle-blocked-claim"); worker.start();
        try {
            await(entered); assertTrue(f.coordinator.cancel(r));
            assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
            assertFalse(f.grant(r)); assertThrows(IllegalStateException.class, f::request);
            f.assertCalls("request", "open", "claim");
        } finally { resume.countDown(); worker.join(4000); }
        assertFalse(worker.isAlive()); assertTrue(callback.get(1, TimeUnit.SECONDS));
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void lateOpenOrClaimCompletionNeverDisposesNewGenerationResources() throws Exception {
        for (boolean blockClaim : new boolean[]{false, true}) {
            Fixture f = new Fixture(); Request old = f.request();
            Fixture replacement = new Fixture();
            CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
            Action block = () -> { entered.countDown(); await(resume); };
            if (blockClaim) f.connection.onClaim = block;
            else f.onOpen = block;
            FutureTask<Boolean> callback = new FutureTask<>(() -> f.grant(old));
            Thread worker = new Thread(callback, "lifecycle-old-generation"); worker.start();
            long next;
            try {
                await(entered);
                assertTrue(f.coordinator.detached(old, f.identity));
                assertThrows(IllegalStateException.class, f::request);
                // Even a caller bypassing the coordinator cannot transfer old ownership to a new generation.
                next = f.session.requestPermission();
                assertTrue(next > old.getToken());
                assertTrue(f.session.permissionResult(next, true));
                assertTrue(f.session.connected(next, replacement.connection));
                f.session.interfaceClaimed(next);
                replacement.assertCalls();
            } finally { resume.countDown(); worker.join(4000); }
            assertFalse(worker.isAlive());
            assertTrue(callback.get(1, TimeUnit.SECONDS));
            assertEquals(next, f.session.getGeneration());
            assertEquals(Tbs5530Session.State.CONNECTED, f.session.getState());
            assertNull(f.session.getFailure());
            replacement.assertCalls();
            assertFalse(f.grant(old));
            assertFalse(f.coordinator.detached(old, f.identity));
            if (blockClaim) f.assertCalls("request", "open", "claim", "release", "close");
            else f.assertCalls("request", "open", "close");
            f.session.close();
            replacement.assertCalls("release", "close");
        }
    }

    @Test public void localCleanupBlocksReentrantReopenButNotSessionInspection() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.onClaim = () -> assertTrue(f.coordinator.cancel(r));
        f.connection.onRelease = () -> {
            assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
            assertThrows(IllegalStateException.class, f::request);
            assertFalse(f.grant(r));
        };
        assertTrue(f.grant(r));
        Request next = f.request(); assertTrue(next.getToken() > r.getToken());
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close", "request");
    }

    @Test public void directSessionInvalidationRejectsCallbackEvenBeforeCoordinatorSeesDetach() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        assertTrue(f.session.detached(r.getToken()));
        assertFalse(f.grant(r));
        assertFalse(f.coordinator.permissionTimedOut(r));
        f.assertCalls("request");
    }

    @Test public void synchronousPermissionResultCanConnectWithoutReenteringAnExternalMonitor() throws Exception {
        Tbs5530Session session = new Tbs5530Session();
        Fixture resources = new Fixture();
        Tbs5530LifecycleCoordinator[] holder = new Tbs5530LifecycleCoordinator[1];
        holder[0] = new Tbs5530LifecycleCoordinator(session, request -> {
            assertFalse(Thread.holdsLock(session));
            assertFalse(Thread.holdsLock(holder[0]));
            assertTrue(holder[0].permissionResult(request, request.getIdentity(), true));
        }, identity -> resources.connection);
        Request request = holder[0].requestPermission(identity(1));
        assertEquals(Tbs5530Session.State.CONNECTED, session.getState());
        assertFalse(holder[0].permissionResult(request, request.getIdentity(), true));
        holder[0].close();
        resources.assertCalls("claim", "release", "close");
    }

    @Test public void reattachmentWithReusedOsIdStillRequiresNewAttachmentIdentity() throws Exception {
        Fixture f = new Fixture(); Request old = f.request();
        assertTrue(f.coordinator.detached(old, f.identity));
        Request next = f.coordinator.requestPermission(identity(2));
        assertEquals(old.getIdentity().getDeviceId(), next.getIdentity().getDeviceId());
        assertNotEquals(old.getIdentity(), next.getIdentity());
        assertFalse(f.coordinator.permissionResult(next, old.getIdentity(), true));
        assertFalse(f.coordinator.detached(next, old.getIdentity()));
        assertEquals(Tbs5530Session.State.WAIT_PERMISSION, f.session.getState());
        assertTrue(f.coordinator.permissionResult(next, next.getIdentity(), false));
        f.coordinator.close(); f.assertCalls("request", "request");
    }

    @Test public void deniedClaimCleanupKeepsReopenBlockedUntilCloseCallbackFinishes() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        f.connection.markClaimed = false;
        IOException claim = f.connection.claimFailure = new IOException("claim failed");
        f.connection.onClose = () -> {
            assertSame(claim, f.session.getFailure());
            assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
            f.coordinator.close();
            assertThrows(IllegalStateException.class, f::request);
        };
        assertSame(claim, assertThrows(IOException.class, () -> f.grant(r)));
        Request next = f.request(); assertTrue(next.getToken() > r.getToken());
        f.coordinator.close(); f.assertCalls("request", "open", "claim", "close", "request");
    }

    @Test public void runtimeOpenFailureIsWrappedWithoutAcquiringOwnership() throws Exception {
        Fixture f = new Fixture(); Request r = f.request();
        RuntimeException original = new IllegalStateException("open bug");
        f.onOpen = () -> { throw original; };
        IOException failure = assertThrows(IOException.class, () -> f.grant(r));
        assertSame(original, failure.getCause());
        assertSame(failure, f.session.getFailure());
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState());
        f.coordinator.close(); f.assertCalls("request", "open");
    }

    @Test public void acceptedOwnershipCleanupFailurePropagatesAndNeverCleansTwice() throws Exception {
        Fixture f = new Fixture(); Request r = f.request(); assertTrue(f.grant(r));
        IOException release = new IOException("release"); IOException close = new IOException("close");
        f.connection.onRelease = () -> { throw release; };
        f.connection.onClose = () -> { throw close; };
        IOException failure = assertThrows(IOException.class, f.coordinator::close);
        assertSame(release, failure); assertArrayEquals(new Throwable[]{close}, failure.getSuppressed());
        assertEquals(Tbs5530Session.State.CLOSED, f.session.getState());
        f.coordinator.close(); f.assertCalls("request", "open", "claim", "release", "close");
    }

    @Test public void nullIdentityIsRejectedBeforeRequestingPermission() {
        Fixture f = new Fixture();
        assertThrows(NullPointerException.class, () -> f.coordinator.requestPermission(null));
        assertEquals(Tbs5530Session.State.DETECTED, f.session.getState());
        f.assertCalls();
    }

    @Test public void acceptedConnectionCleanupRunsOutsideCoordinatorAndSessionMonitors() throws Exception {
        Fixture f = new Fixture(); Request r = f.request(); assertTrue(f.grant(r));
        f.connection.onRelease = () -> {
            assertEquals(Tbs5530Session.State.DISCONNECTED, f.session.getState());
            assertTrue(f.session.isCleanupPending());
            assertThrows(IllegalStateException.class, f::request);
        };
        assertTrue(f.coordinator.detached(r, f.identity));
        f.coordinator.close();
        f.assertCalls("request", "open", "claim", "release", "close");
    }
}
