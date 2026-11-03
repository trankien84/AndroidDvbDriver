// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Registration;

import static org.junit.Assert.*;

/** All USB operations use FakeManager; only Context receivers and PendingIntent delivery are real. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530AndroidUsbFacadeTest {
    private static final Device DEVICE = new Device("/dev/bus/usb/001/007", 7, 0x734c, 0x5530);

    private static final class RecordingContext extends ContextWrapper {
        final Map<BroadcastReceiver, BroadcastReceiver> receivers = new IdentityHashMap<>();
        final AtomicInteger registrations = new AtomicInteger();
        final AtomicInteger unregistrations = new AtomicInteger();
        volatile Intent received;
        volatile BroadcastReceiver lastReceiver;
        volatile IntentFilter lastFilter;
        volatile int flags;
        boolean failRegistration;

        RecordingContext() {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext());
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            return registerTracked(receiver, filter, 0, false);
        }
        @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int receiverFlags) {
            return registerTracked(receiver, filter, receiverFlags, true);
        }
        private Intent registerTracked(BroadcastReceiver receiver, IntentFilter filter, int receiverFlags, boolean modern) {
            if (failRegistration) throw new SecurityException("Injected receiver registration failure");
            BroadcastReceiver forwarding = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    received = intent;
                    receiver.onReceive(context, intent);
                }
            };
            Intent result = modern ? super.registerReceiver(forwarding, filter, receiverFlags)
                    : super.registerReceiver(forwarding, filter);
            synchronized (receivers) { receivers.put(receiver, forwarding); }
            lastReceiver = receiver;
            lastFilter = filter;
            flags = receiverFlags;
            registrations.incrementAndGet();
            return result;
        }
        @Override public void unregisterReceiver(BroadcastReceiver receiver) {
            BroadcastReceiver forwarding;
            synchronized (receivers) { forwarding = receivers.remove(receiver); }
            if (forwarding == null) throw new IllegalArgumentException("Unknown receiver");
            super.unregisterReceiver(forwarding);
            unregistrations.incrementAndGet();
        }
        int active() { return registrations.get() - unregistrations.get(); }
        Intent permissionTarget(String correlation) {
            String action = lastFilter.getAction(0);
            return Tbs5530AndroidUsbFacade.permissionIntent(getPackageName(), correlation,
                    action.substring(action.lastIndexOf('.') + 1));
        }
    }

    private static final class FakeRaw implements Tbs5530AndroidUsbFacade.RawConnection {
        int[] ids = {2, 0, 0};
        int[] alternates = {0, 0, 1};
        boolean claimResult = true, releaseResult = true;
        int claimedIndex = -1, releasedIndex = -1;
        boolean forced;
        final AtomicInteger claims = new AtomicInteger(), releases = new AtomicInteger(), closes = new AtomicInteger();
        final CountDownLatch enteredClaim = new CountDownLatch(1);
        CountDownLatch allowClaim;
        Runnable afterClaim;
        Thread claimThread;
        int[] addresses = {0x81, 0x82};
        int direction = 0x80, type = 2, packetSize = 512;
        boolean unsupportedDescriptors;
        final List<Integer> descriptorInterfaces = new ArrayList<>();
        final AtomicInteger controls = new AtomicInteger();
        final CountDownLatch enteredControl = new CountDownLatch(1);
        CountDownLatch allowControl;
        int requestType, request, value, controlIndex, offset, length, timeout;
        int result = Integer.MIN_VALUE;
        byte[] controlBuffer, payload;
        @Override public int interfaceCount() { return ids.length; }
        @Override public int interfaceId(int index) { return ids[index]; }
        @Override public int alternateSetting(int index) { return alternates[index]; }
        @Override public int endpointCount(int interfaceIndex) throws IOException {
            descriptorInterfaces.add(interfaceIndex);
            if (unsupportedDescriptors) return Tbs5530AndroidUsbFacade.RawConnection.super.endpointCount(interfaceIndex);
            return addresses.length;
        }
        @Override public int endpointAddress(int interfaceIndex, int endpointIndex) { return addresses[endpointIndex]; }
        @Override public int endpointDirection(int interfaceIndex, int endpointIndex) { return direction; }
        @Override public int endpointType(int interfaceIndex, int endpointIndex) { return type; }
        @Override public int endpointMaxPacketSize(int interfaceIndex, int endpointIndex) { return packetSize; }
        @Override public int controlTransfer(int requestType, int request, int value, int index,
                                             byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
            controls.incrementAndGet();
            this.requestType = requestType; this.request = request; this.value = value; this.controlIndex = index;
            this.controlBuffer = buffer; this.offset = offset; this.length = length; this.timeout = timeoutMillis;
            payload = buffer == null ? null : Arrays.copyOfRange(buffer, offset, offset + length);
            enteredControl.countDown();
            awaitGate(allowControl);
            if ((requestType & 0x80) != 0 && length > 0) Arrays.fill(buffer, offset, offset + length, (byte) 0x5a);
            return result == Integer.MIN_VALUE ? length : result;
        }
        @Override public boolean claim(int index, boolean force) throws IOException {
            claimThread = Thread.currentThread();
            claims.incrementAndGet();
            claimedIndex = index;
            forced = force;
            enteredClaim.countDown();
            awaitGate(allowClaim);
            if (afterClaim != null) afterClaim.run();
            return claimResult;
        }
        @Override public boolean release(int index) {
            releases.incrementAndGet();
            releasedIndex = index;
            return releaseResult;
        }
        @Override public void close() { closes.incrementAndGet(); }
    }

    private static final class FakeManager implements Tbs5530AndroidUsbFacade.Manager {
        volatile List<Device> available = Collections.singletonList(DEVICE);
        volatile boolean permitted = true;
        final FakeRaw raw = new FakeRaw();
        final AtomicInteger enumerations = new AtomicInteger(), permissions = new AtomicInteger();
        final AtomicInteger requests = new AtomicInteger(), opens = new AtomicInteger();
        volatile PendingIntent pending;
        boolean requestFails;
        Runnable afterOpen;
        CountDownLatch allowOpen;
        final CountDownLatch enteredOpen = new CountDownLatch(1);
        Thread openThread;
        @Override public List<Device> devices() { enumerations.incrementAndGet(); return available; }
        @Override public boolean hasPermission(Device device) { permissions.incrementAndGet(); return permitted; }
        @Override public void requestPermission(Device device, PendingIntent result) throws IOException {
            assertEquals(DEVICE, device);
            pending = result;
            requests.incrementAndGet();
            if (requestFails) throw new IOException("Injected permission failure");
        }
        @Override public Tbs5530AndroidUsbFacade.RawConnection open(Device device) throws IOException {
            openThread = Thread.currentThread();
            opens.incrementAndGet();
            enteredOpen.countDown();
            awaitGate(allowOpen);
            if (afterOpen != null) afterOpen.run();
            return raw;
        }
    }

    private static void awaitGate(CountDownLatch gate) throws IOException {
        if (gate == null) return;
        try {
            if (!gate.await(10, TimeUnit.SECONDS)) throw new IOException("Fake gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    @Test public void constructionDoesNotTouchUsbOrRegisterReceiver() {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        new Tbs5530AndroidUsbFacade(context, manager);
        assertEquals(0, context.active());
        assertEquals(0, manager.enumerations.get());
        assertEquals(0, manager.permissions.get());
        assertEquals(0, manager.requests.get());
        assertEquals(0, manager.opens.get());
    }

    @Test public void permissionIntentCompatibilityBranches21Through37() {
        for (int sdk : new int[] {21, 23, 30, 31, 33, 34, 37}) {
            int flags = Tbs5530AndroidUsbFacade.permissionFlags(sdk);
            assertTrue((flags & PendingIntent.FLAG_ONE_SHOT) != 0);
            assertEquals(0, flags & PendingIntent.FLAG_IMMUTABLE);
            assertEquals(sdk >= 31, (flags & PendingIntent.FLAG_MUTABLE) != 0);
            assertEquals(0, flags & PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
        }
        Intent first = Tbs5530AndroidUsbFacade.permissionIntent("example.test", "request/a", "first");
        Intent second = Tbs5530AndroidUsbFacade.permissionIntent("example.test", "request/a", "second");
        assertEquals("example.test", first.getPackage());
        assertNull(first.getComponent());
        assertNotEquals(first.getAction(), second.getAction());
        assertNotEquals(first.getData(), second.getData());
        assertFalse(first.filterEquals(second));
    }

    @Test public void realPendingIntentFillsExtrasAndIsOneShotButMissingDeviceRejects() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(context, manager);
        AtomicReference<Boolean> granted = new AtomicReference<>(Boolean.TRUE);
        AtomicReference<Device> reported = new AtomicReference<>(DEVICE);
        AtomicReference<String> correlation = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        Registration registration = facade.requestPermission(DEVICE, "correlation", (token, device, result) -> {
            correlation.set(token); reported.set(device); granted.set(result); callback.countDown();
        });
        try {
            assertEquals(1, context.active());
            if (Build.VERSION.SDK_INT >= 33) assertEquals(Context.RECEIVER_NOT_EXPORTED, context.flags);
            if (Build.VERSION.SDK_INT >= 31) assertFalse(manager.pending.isImmutable());
            manager.pending.send(context, 0, new Intent().putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)
                    .putExtra("fill-in-proof", "delivered"));
            assertTrue(callback.await(3, TimeUnit.SECONDS));
            assertEquals("correlation", correlation.get());
            assertNull(reported.get());
            assertNull(granted.get());
            assertEquals("delivered", context.received.getStringExtra("fill-in-proof"));
            assertTrue(context.received.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
            assertEquals(context.getPackageName(), context.received.getPackage());
            try { manager.pending.send(); fail("One-shot token replayed"); }
            catch (PendingIntent.CanceledException expected) { }
        } finally { registration.close(); registration.close(); }
        assertEquals(0, context.active());
        assertEquals(1, context.unregistrations.get());
        assertEquals(0, manager.opens.get());
    }

    @Test public void wrongOrAbsentExtrasRejectAndWrongActionOrUriIsIgnored() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(context, manager);
        AtomicInteger callbacks = new AtomicInteger();
        Registration registration = facade.requestPermission(DEVICE, "parse", (token, device, result) -> {
            assertNull(result);
            callbacks.incrementAndGet();
        });
        try {
            Intent valid = context.permissionTarget("parse");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                context.lastReceiver.onReceive(context, new Intent(valid).setAction("wrong"));
                context.lastReceiver.onReceive(context, new Intent(valid).setData(android.net.Uri.parse("invalid:data")));
            });
            assertEquals(0, callbacks.get());
            List<Intent> malformed = Arrays.asList(new Intent(valid),
                    new Intent(valid).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, "true"),
                    new Intent(valid).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, 1),
                    new Intent(valid).putExtra(UsbManager.EXTRA_DEVICE, "not a USB device")
                            .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true),
                    new Intent(valid).putExtra(UsbManager.EXTRA_DEVICE, new Intent("wrong parcelable"))
                            .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true));
            for (Intent intent : malformed) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> context.lastReceiver.onReceive(context, intent));
            }
            assertEquals(malformed.size(), callbacks.get());
            assertEquals(0, manager.opens.get());
            assertEquals(0, manager.enumerations.get());
            assertEquals(0, manager.permissions.get());
        } finally { registration.close(); }
        assertEquals(0, context.active());
    }

    @Test public void grantedExtraRequiresActualBooleanAndDeviceIdentity() {
        Intent intent = new Intent();
        assertNull(Tbs5530AndroidUsbFacade.readGranted(intent, DEVICE));
        intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, "true");
        assertNull(Tbs5530AndroidUsbFacade.readGranted(intent, DEVICE));
        intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, 1);
        assertNull(Tbs5530AndroidUsbFacade.readGranted(intent, DEVICE));
        intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
        assertEquals(Boolean.TRUE, Tbs5530AndroidUsbFacade.readGranted(intent, DEVICE));
        assertNull(Tbs5530AndroidUsbFacade.readGranted(intent, null));
        intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        assertEquals(Boolean.FALSE, Tbs5530AndroidUsbFacade.readGranted(intent, DEVICE));
    }

    @Test public void separateRequestsOwnDistinctPendingIntentsAndReceivers() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(context, manager);
        AtomicInteger firstCalls = new AtomicInteger();
        CountDownLatch secondCalls = new CountDownLatch(1);
        Registration first = facade.requestPermission(DEVICE, "same-correlation", (token, device, result) -> firstCalls.incrementAndGet());
        PendingIntent firstPending = manager.pending;
        BroadcastReceiver firstReceiver = context.lastReceiver;
        Intent firstIntent = context.permissionTarget("same-correlation");
        Registration second = facade.requestPermission(DEVICE, "same-correlation", (token, device, result) -> secondCalls.countDown());
        try {
            assertEquals(2, context.active());
            assertNotEquals(firstPending, manager.pending);
            first.close();
            assertEquals(1, context.active());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> firstReceiver.onReceive(context, firstIntent));
            assertEquals(0, firstCalls.get());
            manager.pending.send(context, 0, new Intent().putExtra("fill-in", true));
            assertTrue(secondCalls.await(3, TimeUnit.SECONDS));
        } finally { first.close(); second.close(); }
        assertEquals(0, context.active());
    }

    @Test public void permissionCloseCancelsTokenAndRequestFailureCleansReceiver() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(context, manager);
        Registration registration = facade.requestPermission(DEVICE, "cancel", (token, device, result) -> fail("Closed callback"));
        registration.close();
        registration.close();
        assertEquals(0, context.active());
        try { manager.pending.send(); fail("Canceled pending intent survived"); }
        catch (PendingIntent.CanceledException expected) { }
        manager.requestFails = true;
        try { facade.requestPermission(DEVICE, "failure", (token, device, result) -> { }); fail("Expected failure"); }
        catch (IOException expected) { }
        assertEquals(0, context.active());
        assertEquals(2, context.unregistrations.get());
        try { manager.pending.send(); fail("Failed request leaked token"); }
        catch (PendingIntent.CanceledException expected) { }
        context.failRegistration = true;
        try { facade.requestPermission(DEVICE, "register-failure", (token, device, result) -> { }); fail("Expected registration failure"); }
        catch (IOException expected) { }
        assertEquals(0, context.active());
        assertEquals(2, manager.requests.get());
        assertEquals(2, context.unregistrations.get());
    }

    @Test public void eventListenerUsesRealRegistrationRejectsMalformedAndClosesOnce() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        AtomicInteger events = new AtomicInteger();
        Registration registration = new Tbs5530AndroidUsbFacade(context, manager).listen(new Tbs5530UsbBinding.Events() {
            @Override public void attached(Device device) { events.incrementAndGet(); }
            @Override public void detached(Device device) { events.incrementAndGet(); }
        });
        try {
            assertEquals(1, context.active());
            if (Build.VERSION.SDK_INT >= 33) assertEquals(Context.RECEIVER_NOT_EXPORTED, context.flags);
            assertTrue(context.lastFilter.hasAction(UsbManager.ACTION_USB_DEVICE_ATTACHED));
            assertTrue(context.lastFilter.hasAction(UsbManager.ACTION_USB_DEVICE_DETACHED));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                // Protected system actions cannot be sent by the test UID; exercise parsing directly.
                context.lastReceiver.onReceive(context, new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED));
                context.lastReceiver.onReceive(context, new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
                        .putExtra(UsbManager.EXTRA_DEVICE, "invalid"));
            });
            assertEquals(0, events.get());
            assertEquals(0, manager.enumerations.get());
        } finally { registration.close(); registration.close(); }
        assertEquals(0, context.active());
        assertEquals(1, context.unregistrations.get());
    }

    private static Tbs5530LifecycleCoordinator coordinator(Tbs5530Session session, Tbs5530AndroidUsbFacade facade) {
        return new Tbs5530LifecycleCoordinator(session, request -> { }, identity -> facade.open(DEVICE));
    }

    private static Tbs5530LifecycleCoordinator.Request acquire(Tbs5530LifecycleCoordinator coordinator) throws IOException {
        Tbs5530LifecycleCoordinator.Request request = coordinator.requestPermission(
                new Tbs5530LifecycleCoordinator.AttachmentIdentity(DEVICE.path, DEVICE.id, DEVICE.vendorId, DEVICE.productId, 1));
        coordinator.permissionResult(request, request.getIdentity(), true);
        return request;
    }

    @Test public void interfaceSelectedByIdAndAlternateNotArrayPosition() throws Exception {
        for (int alternate : new int[] {0, 1}) {
            FakeManager manager = new FakeManager();
            Tbs5530Session session = new Tbs5530Session();
            Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(new RecordingContext(), manager, alternate);
            try {
                acquire(coordinator(session, facade));
                assertEquals(alternate == 0 ? 1 : 2, manager.raw.claimedIndex);
                assertEquals(Collections.singletonList(manager.raw.claimedIndex), manager.raw.descriptorInterfaces);
                assertFalse(manager.raw.forced);
                assertEquals(Tbs5530Session.State.CONNECTED, session.getState());
            } finally { session.close(); }
            assertEquals(manager.raw.claimedIndex, manager.raw.releasedIndex);
            assertEquals(1, manager.raw.releases.get());
            assertEquals(1, manager.raw.closes.get());
        }
    }

    @Test public void failedClaimDoesNotReleaseUnownedInterface() throws Exception {
        FakeManager manager = new FakeManager();
        manager.raw.claimResult = false;
        Tbs5530Session session = new Tbs5530Session();
        try { acquire(coordinator(session, new Tbs5530AndroidUsbFacade(new RecordingContext(), manager))); fail("Claim accepted"); }
        catch (IOException expected) { }
        finally { session.close(); }
        assertEquals(1, manager.raw.claims.get());
        assertEquals(0, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void permissionRevokedAfterSuccessfulClaimStillReleasesOwnership() throws Exception {
        FakeManager manager = new FakeManager();
        manager.raw.afterClaim = () -> manager.permitted = false;
        Tbs5530Session session = new Tbs5530Session();
        try { acquire(coordinator(session, new Tbs5530AndroidUsbFacade(new RecordingContext(), manager))); fail("Revoked claim accepted"); }
        catch (IOException expected) { }
        finally { session.close(); }
        assertEquals(1, manager.raw.claims.get());
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void falseReleaseReportsFailureButStillClosesExactlyOnce() throws Exception {
        FakeManager manager = new FakeManager();
        manager.raw.releaseResult = false;
        Tbs5530Session session = new Tbs5530Session();
        acquire(coordinator(session, new Tbs5530AndroidUsbFacade(new RecordingContext(), manager)));
        try { session.close(); fail("Release failure hidden"); }
        catch (IOException expected) { }
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void changedIdentityOrPermissionAfterOpenClosesUnclaimedConnection() throws Exception {
        for (boolean revokePermission : new boolean[] {false, true}) {
            FakeManager manager = new FakeManager();
            manager.afterOpen = () -> {
                if (revokePermission) manager.permitted = false;
                else manager.available = Collections.singletonList(new Device(DEVICE.path, DEVICE.id + 1, DEVICE.vendorId, DEVICE.productId));
            };
            try { new Tbs5530AndroidUsbFacade(new RecordingContext(), manager).open(DEVICE); fail("Stale attachment accepted"); }
            catch (IOException expected) { }
            assertEquals(0, manager.raw.claims.get());
            assertEquals(0, manager.raw.releases.get());
            assertEquals(1, manager.raw.closes.get());
        }
    }

    @Test public void missingOrAmbiguousInterfaceClosesWithoutClaim() throws Exception {
        for (int[] ids : new int[][] {{1, 2}, {0, 0}}) {
            FakeManager manager = new FakeManager();
            manager.raw.ids = ids;
            manager.raw.alternates = new int[] {0, 0};
            try { new Tbs5530AndroidUsbFacade(new RecordingContext(), manager).open(DEVICE); fail("Invalid interface accepted"); }
            catch (IOException expected) { }
            assertEquals(0, manager.raw.claims.get());
            assertEquals(1, manager.raw.closes.get());
        }
    }

    @Test public void mainThreadCannotOpenOrAwaitRuntimeShutdown() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(new RecordingContext(), manager);
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        try {
            AtomicInteger rejected = new AtomicInteger();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try { facade.open(DEVICE); fail("Main thread opened USB"); }
                catch (IOException expected) { rejected.incrementAndGet(); }
                try { runtime.awaitTermination(1, TimeUnit.MILLISECONDS); fail("Main thread joined"); }
                catch (IllegalStateException expected) { rejected.incrementAndGet(); }
                catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            });
            assertEquals(2, rejected.get());
            assertEquals(0, manager.opens.get());
        } finally { runtime.close(); }
        assertTrue(runtime.awaitTermination(3, TimeUnit.SECONDS));
    }

    private static Tbs5530LifecycleCoordinator.Connection claimedConnection(Tbs5530Session session,
                                                                           FakeManager manager) throws IOException {
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(new RecordingContext(), manager);
        AtomicReference<Tbs5530LifecycleCoordinator.Connection> owned = new AtomicReference<>();
        Tbs5530LifecycleCoordinator coordinator = new Tbs5530LifecycleCoordinator(session, request -> { }, identity -> {
            Tbs5530LifecycleCoordinator.Connection connection = facade.open(DEVICE);
            owned.set(connection);
            return connection;
        });
        acquire(coordinator);
        return owned.get();
    }

    @Test public void invalidOrMissingEndpointMetadataFailsClosedBeforeClaim() throws Exception {
        for (int fault = 0; fault < 6; fault++) {
            FakeManager manager = new FakeManager();
            switch (fault) {
                case 0: manager.raw.addresses = new int[] {0x81}; break;
                case 1: manager.raw.addresses = new int[] {0x82, 0x82}; break;
                case 2: manager.raw.direction = 0; break;
                case 3: manager.raw.type = 3; break;
                case 4: manager.raw.packetSize = 32; break;
                default: manager.raw.unsupportedDescriptors = true;
            }
            try { new Tbs5530AndroidUsbFacade(new RecordingContext(), manager).open(DEVICE); fail("Invalid endpoint accepted"); }
            catch (IOException expected) { }
            assertEquals(0, manager.raw.claims.get());
            assertEquals(0, manager.raw.controls.get());
            assertEquals(1, manager.raw.closes.get());
        }
    }

    @Test public void claimedConnectionExposesImmutableValidatedEndpointSnapshot() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        try {
            Tbs5530Descriptors.Endpoint endpoint = connection.streamEndpoint();
            assertEquals(0x82, endpoint.address);
            assertEquals(0x80, endpoint.direction);
            assertEquals(2, endpoint.transferType);
            assertEquals(512, endpoint.maxPacketSize);
            manager.raw.packetSize = 0;
            manager.raw.addresses[1] = 3;
            assertSame(endpoint, connection.streamEndpoint());
            assertEquals(512, endpoint.maxPacketSize);
        } finally { session.close(); }
        try { connection.streamEndpoint(); fail("Closed connection exposed active metadata"); }
        catch (IOException expected) { }
    }

    @Test public void rawControlUsesOriginalConnectionAndExactFullParametersForInAndOut() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        try {
            byte[] buffer = {9, 10, 11, 12, 13, 14};
            assertEquals(3, connection.controlTransfer(0x40, 0x90, 0x1234, 0xabcd, buffer, 2, 3, 731));
            assertSame(buffer, manager.raw.controlBuffer);
            assertArrayEquals(new byte[] {11, 12, 13}, manager.raw.payload);
            assertEquals(0x40, manager.raw.requestType);
            assertEquals(0x90, manager.raw.request);
            assertEquals(0x1234, manager.raw.value);
            assertEquals(0xabcd, manager.raw.controlIndex);
            assertEquals(2, manager.raw.offset);
            assertEquals(3, manager.raw.length);
            assertEquals(731, manager.raw.timeout);
            assertEquals(2, connection.controlTransfer(0xc0, 0x91, 65535, 0, buffer, 1, 2, 1));
            assertArrayEquals(new byte[] {9, 0x5a, 0x5a, 12, 13, 14}, buffer);
            assertEquals(0xc0, manager.raw.requestType);
            assertEquals(0x91, manager.raw.request);
            assertEquals(65535, manager.raw.value);
            assertEquals(0, manager.raw.controlIndex);
            assertEquals(1, manager.raw.offset);
            assertEquals(2, manager.raw.length);
            assertEquals(1, manager.raw.timeout);
            assertEquals(1, manager.opens.get());
            assertEquals(1, manager.raw.claims.get());
            assertEquals(0, manager.raw.releases.get());
            assertEquals(0, manager.raw.closes.get());
        } finally { session.close(); }
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void rawShortAndNegativeResultsAreReturnedOnceWithoutRetry() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        try {
            for (int result : new int[] {0, 1, -1, -110}) {
                manager.raw.result = result;
                assertEquals(result, connection.controlTransfer(0xc0, 0x91, 0, 0, new byte[4], 1, 2, 500));
            }
            assertEquals(4, manager.raw.controls.get());
            assertEquals(1, manager.opens.get());
            assertEquals(0, manager.raw.closes.get());
        } finally { session.close(); }
    }

    @Test public void rawControlRejectsUnclaimedReleasedClosedAndMainThreadCalls() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(new RecordingContext(), manager);
        Tbs5530LifecycleCoordinator.Connection unclaimed = facade.open(DEVICE);
        try {
            try { unclaimed.controlTransfer(0x40, 0x90, 0, 0, new byte[0], 0, 0, 500); fail("Unclaimed transfer"); }
            catch (IOException expected) { }
            try { unclaimed.streamEndpoint(); fail("Unclaimed metadata"); }
            catch (IOException expected) { }
        } finally { unclaimed.close(); }
        manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        try {
            AtomicInteger rejected = new AtomicInteger();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try { connection.controlTransfer(0x40, 0x90, 0, 0, new byte[0], 0, 0, 500); fail("Main-thread control"); }
                catch (IOException expected) { rejected.incrementAndGet(); }
            });
            assertEquals(1, rejected.get());
            connection.release();
            try { connection.controlTransfer(0x40, 0x90, 0, 0, new byte[0], 0, 0, 500); fail("Released transfer"); }
            catch (IOException expected) { }
            connection.close();
            try { connection.controlTransfer(0x40, 0x90, 0, 0, new byte[0], 0, 0, 500); fail("Closed transfer"); }
            catch (IOException expected) { }
            assertEquals(0, manager.raw.controls.get());
        } finally { session.close(); }
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void rawControlRejectsInvalidFieldsBuffersAndUnboundedTimeoutBeforeIo() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        int[][] invalid = {
                {-1, 1, 0, 0, 0, 1, 1}, {256, 1, 0, 0, 0, 1, 1},
                {0x40, -1, 0, 0, 0, 1, 1}, {0x40, 256, 0, 0, 0, 1, 1},
                {0x40, 1, -1, 0, 0, 1, 1}, {0x40, 1, 65536, 0, 0, 1, 1},
                {0x40, 1, 0, -1, 0, 1, 1}, {0x40, 1, 0, 65536, 0, 1, 1},
                {0x40, 1, 0, 0, -1, 1, 1}, {0x40, 1, 0, 0, 0, -1, 1},
                {0x40, 1, 0, 0, 0, 65536, 1}, {0x40, 1, 0, 0, 5, 0, 1},
                {0x40, 1, 0, 0, 3, 2, 1}, {0x40, 1, 0, 0, Integer.MAX_VALUE, 1, 1},
                {0x40, 1, 0, 0, 0, 1, 0}, {0x40, 1, 0, 0, 0, 1, -1}
        };
        try {
            for (int[] p : invalid) {
                try { connection.controlTransfer(p[0], p[1], p[2], p[3], new byte[4], p[4], p[5], p[6]); fail("Invalid call accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            try { connection.controlTransfer(0x40, 1, 0, 0, null, 0, 1, 1); fail("Null payload buffer accepted"); }
            catch (IllegalArgumentException expected) { }
            try { connection.controlTransfer(0x40, 1, 0, 0, null, 1, 0, 1); fail("Null buffer offset accepted"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(0, manager.raw.controls.get());
            assertEquals(0, connection.controlTransfer(0x40, 1, 0, 0, null, 0, 0, 1));
            assertNull(manager.raw.controlBuffer);
            assertEquals(1, manager.raw.controls.get());
        } finally { session.close(); }
    }

    @Test public void managedControlFactoryReachesSameClaimedRawAndRejectsMainOrStaleCalls() throws Exception {
        FakeManager manager = new FakeManager();
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator coordinator = coordinator(session,
                new Tbs5530AndroidUsbFacade(new RecordingContext(), manager));
        Tbs5530LifecycleCoordinator.Request request = acquire(coordinator);
        Tbs5530ControlIo control = coordinator.controlIo(request);
        try {
            assertEquals(0x82, coordinator.streamEndpoint(request).address);
            byte[] out = {1, 2, 3, 4, 5};
            assertEquals(2, control.controlTransfer(0x40, 0x90, 0x5678, 0x4321, out, 2, 2, 903));
            assertNotSame(out, manager.raw.controlBuffer);
            assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, out);
            assertArrayEquals(new byte[] {3, 4}, manager.raw.payload);
            assertEquals(0x40, manager.raw.requestType);
            assertEquals(0x90, manager.raw.request);
            assertEquals(0x5678, manager.raw.value);
            assertEquals(0x4321, manager.raw.controlIndex);
            assertEquals(2, manager.raw.offset);
            assertEquals(2, manager.raw.length);
            assertEquals(903, manager.raw.timeout);
            byte[] in = {1, 2, 3, 4, 5};
            assertEquals(2, control.controlTransfer(0xc0, 0x91, 0x1357, 0x2468, in, 1, 2, 700));
            assertNotSame(in, manager.raw.controlBuffer);
            assertEquals(0xc0, manager.raw.requestType);
            assertEquals(0x91, manager.raw.request);
            assertEquals(0x1357, manager.raw.value);
            assertEquals(0x2468, manager.raw.controlIndex);
            assertEquals(1, manager.raw.offset);
            assertEquals(2, manager.raw.length);
            assertEquals(700, manager.raw.timeout);
            assertArrayEquals(new byte[] {1, 0x5a, 0x5a, 4, 5}, in);
            assertEquals(2, manager.raw.controls.get());
            assertEquals(1, manager.opens.get());
            assertEquals(1, manager.raw.claims.get());
            assertEquals(0, manager.raw.releases.get());
            assertEquals(0, manager.raw.closes.get());
            assertEquals(Tbs5530Session.State.CONNECTED, session.getState());
            AtomicInteger rejected = new AtomicInteger();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try { control.controlTransfer(0xc0, 0x91, 0, 0, new byte[1], 0, 1, 1); fail("Managed main-thread transfer"); }
                catch (IOException expected) { rejected.incrementAndGet(); }
            });
            assertEquals(1, rejected.get());
            assertEquals(2, manager.raw.controls.get());
        } finally { session.close(); }
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
        try { control.controlTransfer(0xc0, 0x91, 0, 0, new byte[1], 0, 1, 1); fail("Stale managed capability"); }
        catch (IOException expected) { }
        assertEquals(2, manager.raw.controls.get());
    }

    @Test public void managedShortNegativeAndOverlongInResultsNeverPublishPayloadOrRetry() throws Exception {
        for (int result : new int[] {0, 1, -1, -110, 3}) {
            FakeManager manager = new FakeManager();
            manager.raw.result = result;
            Tbs5530Session session = new Tbs5530Session();
            Tbs5530LifecycleCoordinator coordinator = coordinator(session,
                    new Tbs5530AndroidUsbFacade(new RecordingContext(), manager));
            Tbs5530ControlIo control = coordinator.controlIo(acquire(coordinator));
            try {
                byte[] unchanged = {1, 2, 3, 4};
                try { control.controlTransfer(0xc0, 0x91, 0, 0, unchanged, 1, 2, 700); fail("Failed transfer accepted"); }
                catch (Tbs5530ControlIo.TransferException expected) {
                    assertEquals(result >= 0 && result < 2 ? Tbs5530ControlIo.FailureKind.SHORT_TRANSFER
                            : Tbs5530ControlIo.FailureKind.IO_FAILURE, expected.getKind());
                }
                assertArrayEquals(new byte[] {1, 2, 3, 4}, unchanged);
                assertEquals(Tbs5530Session.State.FAULTED, session.getState());
                assertEquals(1, manager.raw.controls.get());
                assertEquals(1, manager.opens.get());
                try { control.controlTransfer(0xc0, 0x91, 0, 0, unchanged, 1, 2, 700); fail("Failed session retried"); }
                catch (IOException expected) { }
                assertEquals(1, manager.raw.controls.get());
            } finally { session.close(); }
            assertEquals(1, manager.raw.releases.get());
            assertEquals(1, manager.raw.closes.get());
        }
    }

    @Test public void directControlSerializesCloseAndMainRejectionDoesNotWaitForBlockedRaw() throws Exception {
        FakeManager manager = new FakeManager();
        manager.raw.allowControl = new CountDownLatch(1);
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530LifecycleCoordinator.Connection connection = claimedConnection(session, manager);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch closed = new CountDownLatch(1), closing = new CountDownLatch(1);
        Thread transfer = new Thread(() -> {
            try { connection.controlTransfer(0xc0, 0x91, 0, 0, new byte[1], 0, 1, 500); }
            catch (Throwable problem) { failure.set(problem); }
        });
        Thread closer = new Thread(() -> {
            closing.countDown();
            try { session.close(); }
            catch (Throwable problem) { failure.set(problem); }
            finally { closed.countDown(); }
        });
        try {
            transfer.start();
            assertTrue(manager.raw.enteredControl.await(3, TimeUnit.SECONDS));
            closer.start();
            assertTrue(closing.await(3, TimeUnit.SECONDS));
            assertFalse("Close raced active raw control", closed.await(100, TimeUnit.MILLISECONDS));
            assertEquals(0, manager.raw.closes.get());
            AtomicInteger rejected = new AtomicInteger();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try { connection.controlTransfer(0xc0, 0x91, 0, 0, new byte[1], 0, 1, 500); fail("Blocked main control"); }
                catch (IOException expected) { rejected.incrementAndGet(); }
                try { connection.streamEndpoint(); fail("Main metadata read blocked behind control"); }
                catch (IOException expected) { rejected.incrementAndGet(); }
            });
            assertEquals(2, rejected.get());
            assertEquals(1, manager.raw.controls.get());
        } finally {
            manager.raw.allowControl.countDown();
            transfer.join(3000);
            closer.join(3000);
            session.close();
        }
        assertFalse(transfer.isAlive());
        assertFalse(closer.isAlive());
        assertNull(failure.get());
        assertEquals(1, manager.raw.releases.get());
        assertEquals(1, manager.raw.closes.get());
    }

    @Test public void runtimeShutdownDrainsWorkerAndRejectsNewWorkWithoutWaitingForLongDeadline() throws Exception {
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        CountDownLatch ran = new CountDownLatch(2);
        AtomicInteger rejectedSelfJoins = new AtomicInteger();
        Runnable checkSelfJoin = () -> {
            try { runtime.awaitTermination(1, TimeUnit.SECONDS); }
            catch (IllegalStateException expected) { rejectedSelfJoins.incrementAndGet(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { ran.countDown(); }
        };
        try {
            runtime.execute(checkSelfJoin);
            runtime.schedule(checkSelfJoin, 0);
            assertTrue(ran.await(2, TimeUnit.SECONDS));
            assertEquals(2, rejectedSelfJoins.get());
            runtime.schedule(() -> fail("Long deadline survived shutdown"), TimeUnit.HOURS.toMillis(1));
        } finally { runtime.close(); runtime.close(); }
        assertTrue(ran.await(2, TimeUnit.SECONDS));
        assertTrue(runtime.awaitTermination(3, TimeUnit.SECONDS));
        assertTrue(runtime.isTerminated());
        try { runtime.execute(() -> { }); fail("Closed worker accepted task"); }
        catch (java.util.concurrent.RejectedExecutionException expected) { }
        try { runtime.schedule(() -> { }, 0); fail("Closed scheduler accepted deadline"); }
        catch (java.util.concurrent.RejectedExecutionException expected) { }
    }

    @Test public void acquisitionDeadlineInvalidatesWithoutClosingBlockedOpen() throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        CountDownLatch unblock = new CountDownLatch(1);
        manager.allowOpen = unblock;
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        Tbs5530Session session = new Tbs5530Session();
        CountDownLatch deadline = new CountDownLatch(1);
        Tbs5530UsbBinding.Scheduler observedScheduler = (task, delay) -> runtime.schedule(() -> {
            task.run();
            deadline.countDown();
        }, delay);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        Tbs5530UsbBinding binding = new Tbs5530UsbBinding(session,
                new Tbs5530AndroidUsbFacade(context, manager), runtime, observedScheduler, failures::add, 5000, 1000);
        try {
            binding.start(DEVICE);
            assertTrue(manager.enteredOpen.await(3, TimeUnit.SECONDS));
            assertTrue("Acquisition deadline blocked behind open", deadline.await(3, TimeUnit.SECONDS));
            assertEquals(Tbs5530Session.State.CLOSED, session.getState());
            assertTrue(binding.isPending());
            assertEquals(0, manager.raw.closes.get());
            CountDownLatch main = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(main::countDown);
            assertTrue(main.await(2, TimeUnit.SECONDS));
        } finally {
            binding.close();
            unblock.countDown();
            runtime.close();
            assertTrue(runtime.awaitTermination(4, TimeUnit.SECONDS));
        }
        assertEquals(0, context.active());
        assertEquals(1, manager.raw.closes.get());
        assertEquals(0, manager.raw.releases.get());
        assertFalse(binding.isPending());
        for (Throwable failure : failures) assertTrue("Unexpected binding failure: " + failure, failure instanceof IOException);
    }

    @Test public void bindingBlockedOpenKeepsMainAndDeadlineResponsive() throws Exception { blockedAcquisition(false); }
    @Test public void bindingBlockedClaimKeepsMainAndDeadlineResponsive() throws Exception { blockedAcquisition(true); }

    private static void blockedAcquisition(boolean blockClaim) throws Exception {
        RecordingContext context = new RecordingContext();
        FakeManager manager = new FakeManager();
        CountDownLatch unblock = new CountDownLatch(1);
        if (blockClaim) manager.raw.allowClaim = unblock;
        else manager.allowOpen = unblock;
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        Tbs5530Session session = new Tbs5530Session();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        Tbs5530UsbBinding binding = new Tbs5530UsbBinding(session,
                new Tbs5530AndroidUsbFacade(context, manager), runtime, runtime, failures::add, 5000, 5000);
        try {
            assertEquals(0, manager.enumerations.get());
            assertEquals(0, manager.opens.get());
            assertEquals(1, context.active());
            binding.start(DEVICE);
            assertTrue((blockClaim ? manager.raw.enteredClaim : manager.enteredOpen).await(3, TimeUnit.SECONDS));
            assertTrue(binding.isPending());
            assertNotSame(Looper.getMainLooper().getThread(), manager.openThread);
            if (blockClaim) assertNotSame(Looper.getMainLooper().getThread(), manager.raw.claimThread);
            CountDownLatch deadline = new CountDownLatch(1);
            runtime.schedule(deadline::countDown, 0);
            assertTrue("Deadline queued behind blocked USB worker", deadline.await(2, TimeUnit.SECONDS));
            CountDownLatch main = new CountDownLatch(1);
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            new Handler(Looper.getMainLooper()).post(() -> {
                try { binding.isPending(); binding.close(); }
                catch (Throwable failure) { closeFailure.set(failure); }
                finally { main.countDown(); }
            });
            assertTrue("Main thread blocked by USB acquisition/cleanup", main.await(2, TimeUnit.SECONDS));
            assertNull(closeFailure.get());
            assertEquals("Blocked acquisition closed prematurely", 0, manager.raw.closes.get());
        } finally {
            unblock.countDown();
            binding.close();
            runtime.close();
            assertTrue("Runtime did not drain after fake USB unblocked", runtime.awaitTermination(4, TimeUnit.SECONDS));
        }
        assertTrue(runtime.isTerminated());
        assertEquals(0, context.active());
        assertEquals(1, manager.raw.closes.get());
        assertEquals(blockClaim ? 1 : 0, manager.raw.releases.get());
        assertEquals(0, manager.requests.get());
        assertFalse(binding.isPending());
        // Cancellation after open may be reported as an expected acquisition IOException.
        for (Throwable failure : failures) assertTrue("Unexpected binding failure: " + failure, failure instanceof IOException);
    }
}
