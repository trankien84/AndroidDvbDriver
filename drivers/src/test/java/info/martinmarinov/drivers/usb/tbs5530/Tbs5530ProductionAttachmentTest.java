// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Looper;
import android.os.SystemClock;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.usbxfer.ByteSource;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class Tbs5530ProductionAttachmentTest {
    private static final class Fixture implements AutoCloseable {
        final Context context = mock(Context.class);
        final UsbManager manager = mock(UsbManager.class);
        final UsbDevice device = mock(UsbDevice.class);
        final Tbs5530UsbBinding.Registration observer = mock(Tbs5530UsbBinding.Registration.class);
        final Tbs5530UsbBinding.Registration request = mock(Tbs5530UsbBinding.Registration.class);
        final HashMap<String, UsbDevice> devices = new HashMap<>();
        final AtomicLong now = new AtomicLong();
        final MockedStatic<Looper> loopers = mockStatic(Looper.class);
        final MockedStatic<SystemClock> clock = mockStatic(SystemClock.class);
        final MockedConstruction<Tbs5530AndroidUsbFacade> facades;
        Tbs5530UsbBinding.Events events;
        Tbs5530UsbBinding.PermissionCallback callback;
        Tbs5530UsbBinding.Device expected;
        String correlation;
        Runnable requested = () -> { };
        Throwable listenFailure;
        RuntimeException constructionFailure;
        Tbs5530ProductionAttachment attachment;

        Fixture() throws Exception {
            Looper main = mock(Looper.class);
            loopers.when(Looper::getMainLooper).thenReturn(main);
            clock.when(SystemClock::elapsedRealtime).thenAnswer(a -> now.get());
            when(context.getApplicationContext()).thenReturn(context);
            when(context.getSystemService(Context.USB_SERVICE)).thenReturn(manager);
            when(context.getString(anyInt())).thenAnswer(a -> "resource-" + a.getArgument(0));
            when(device.getDeviceName()).thenReturn("fixture");
            when(device.getDeviceId()).thenReturn(7);
            when(device.getVendorId()).thenReturn(0x734c);
            when(device.getProductId()).thenReturn(0x5530);
            devices.put("fixture", device);
            when(manager.getDeviceList()).thenReturn(devices);
            facades = mockConstruction(Tbs5530AndroidUsbFacade.class, (facade, args) -> {
                if (constructionFailure != null) throw constructionFailure;
                when(facade.listen(any())).thenAnswer(a -> {
                    if (listenFailure != null) throw listenFailure;
                    events = a.getArgument(0); return observer;
                });
                when(facade.requestPermission(any(), anyString(), any())).thenAnswer(a -> {
                    expected = a.getArgument(0); correlation = a.getArgument(1); callback = a.getArgument(2);
                    requested.run(); return request;
                });
            });
        }

        Tbs5530ProductionAttachment attach() throws IOException {
            return attachment = new Tbs5530ProductionAttachment(context, device);
        }

        @Override public void close() throws Exception {
            try { if (attachment != null) attachment.close(); }
            finally { facades.close(); clock.close(); loopers.close(); }
        }
    }

    @Test public void systemGrantWithoutCallbackIsPolledAndRegistrationCloses() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.manager.hasPermission(f.device)).thenReturn(false, false, true);
            f.attach().permission(f.device);
            assertNotNull(f.callback); // No callback was delivered.
            verify(f.manager, times(3)).hasPermission(f.device);
            verify(f.request).close();
            assertTrue(Tbs5530ProductionSession.isActive());
        }
        assertFalse(Tbs5530ProductionSession.isActive());
    }

    @Test public void staleCallbackAndUnconfirmedGrantDoNotAuthorizeUsb() throws Exception {
        try (Fixture f = new Fixture()) {
            f.requested = () -> {
                f.callback.result("stale", f.expected, false);
                f.callback.result(f.correlation, new Tbs5530UsbBinding.Device("replaced", 9, 0x734c, 0x5530), false);
                f.callback.result(f.correlation, f.expected, true);
                f.clock.when(SystemClock::elapsedRealtime).thenAnswer(a -> f.now.getAndAdd(60_000));
            };
            Tbs5530ProductionFailure failure = assertThrows(Tbs5530ProductionFailure.class,
                    () -> f.attach().permission(f.device));
            assertEquals(Tbs5530ProductionFailure.Kind.PERMISSION_TIMEOUT, failure.kind);
            verify(f.request).close();
            verify(f.manager, never()).openDevice(any());
        }
    }

    @Test public void stalePermissionCallbacksCannotDenyIndependentSystemGrant() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.manager.hasPermission(f.device)).thenReturn(false, false, true);
            f.requested = () -> {
                f.callback.result("stale", f.expected, false);
                f.callback.result(f.correlation, new Tbs5530UsbBinding.Device("replaced", 9, 0x734c, 0x5530), false);
                f.callback.result(f.correlation, new Tbs5530UsbBinding.Device("fixture", 7, 0x734c, 0x5520), false);
            };
            f.attach().permission(f.device);
            verify(f.request).close();
        }
    }

    @Test public void denialAndInterruptionClosePermissionRegistration() throws Exception {
        try (Fixture f = new Fixture()) {
            f.requested = () -> f.callback.result(f.correlation, f.expected, false);
            assertEquals(Tbs5530ProductionFailure.Kind.PERMISSION,
                    assertThrows(Tbs5530ProductionFailure.class, () -> f.attach().permission(f.device)).kind);
            verify(f.request).close();
        }
        try (Fixture f = new Fixture()) {
            f.requested = () -> Thread.currentThread().interrupt();
            try {
                assertThrows(InterruptedException.class, () -> f.attach().permission(f.device));
                assertTrue(Thread.currentThread().isInterrupted());
                verify(f.request).close();
            } finally { Thread.interrupted(); }
        }
    }

    @Test public void grantNeverOverridesDetachEpochOrLivePresence() throws Exception {
        for (boolean detach : new boolean[]{true, false}) try (Fixture f = new Fixture()) {
            when(f.manager.hasPermission(f.device)).thenAnswer(a -> {
                if (f.callback == null) return false;
                if (detach) f.events.detached(f.expected); else f.devices.clear();
                return true;
            });
            assertThrows(IOException.class, () -> f.attach().permission(f.device));
            verify(f.request).close();
            verify(f.manager, never()).openDevice(any());
        }
    }

    @Test public void competingDiscoveryIsBusyAndFailedPreparationReleasesOwner() throws Exception {
        try (Fixture f = new Fixture()) {
            f.attach();
            Tbs5530ProductionFailure busy = assertThrows(Tbs5530ProductionFailure.class,
                    () -> new Tbs5530ProductionAttachment(f.context, f.device));
            assertEquals(Tbs5530ProductionFailure.Kind.BUSY, busy.kind);
            assertEquals(1, f.facades.constructed().size());
            f.attachment.close();
            when(f.manager.hasPermission(f.device)).thenReturn(true);
            UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
            when(f.manager.openDevice(f.device)).thenReturn(connection);
            when(connection.getRawDescriptors()).thenReturn(new byte[0]);
            assertThrows(DvbException.class, () -> new Tbs5530DvbDeviceCreator().create(f.device, f.context, null));
            verify(connection).close();
            assertFalse(Tbs5530ProductionSession.isActive());
        }
    }

    @Test public void deviceOpenFailureReleasesRealAttachmentOwnership() throws Exception {
        for (Throwable problem : new Throwable[]{new IOException("fixture firmware"),
                new IllegalStateException("fixture initialization"), new LinkageError("fixture native library")}) {
            try (Fixture f = new Fixture(); MockedStatic<DvbDemux> demux = mockStatic(DvbDemux.class);
                 MockedStatic<Tbs5530ProductionFirmware> firmware = mockStatic(Tbs5530ProductionFirmware.class)) {
                demux.when(DvbDemux::DvbDmxSwfilter).thenAnswer(a -> mock(DvbDemux.class));
                when(f.manager.hasPermission(f.device)).thenReturn(true);
                firmware.when(() -> Tbs5530ProductionFirmware.load(f.context)).thenThrow(problem);
                Tbs5530DvbDevice dvb = new Tbs5530DvbDevice(f.context, f.device, null, f.attach());
                try {
                    assertThrows(DvbException.class, dvb::open);
                    verify(f.observer).close();
                    assertFalse(Tbs5530ProductionSession.isActive());
                    verify(f.manager, never()).openDevice(any());
                } finally { dvb.close(); }
            }
        }
    }

    @Test public void controlCleanupRuntimeFailureStillReleasesAttachmentOwnership() throws Exception {
        try (Fixture f = new Fixture(); MockedStatic<DvbDemux> demux = mockStatic(DvbDemux.class)) {
            demux.when(DvbDemux::DvbDmxSwfilter).thenAnswer(a -> mock(DvbDemux.class));
            Tbs5530DvbDevice dvb = new Tbs5530DvbDevice(f.context, f.device, null, f.attach());
            Tbs5530Session session = mock(Tbs5530Session.class);
            doThrow(new IllegalStateException("fixture control cleanup")).when(session).close();
            java.lang.reflect.Field field = Tbs5530DvbDevice.class.getDeclaredField("session");
            field.setAccessible(true); field.set(dvb, session);
            assertThrows(IOException.class, dvb::close);
            verify(session).drainCleanup();
            verify(f.observer).close();
            assertFalse(Tbs5530ProductionSession.isActive());
        }
    }

    @Test public void facadeConstructorAndObserverRegistrationFailuresReleaseTokenBeforeIo() throws Exception {
        try (Fixture f = new Fixture()) {
            f.constructionFailure = new IllegalStateException("fixture facade constructor");
            assertThrows(RuntimeException.class, f::attach);
            assertFalse(Tbs5530ProductionSession.isActive());
            f.constructionFailure = null;
            f.attach();
        }
        for (Throwable failure : new Throwable[]{new IOException("fixture registration"),
                new IllegalStateException("fixture registration runtime"), new LinkageError("fixture registration linkage")}) {
            try (Fixture f = new Fixture()) {
                f.listenFailure = failure;
                assertSame(failure, assertThrows(Throwable.class, f::attach));
                assertFalse(Tbs5530ProductionSession.isActive());
                verify(f.manager, never()).openDevice(any());
                f.listenFailure = null;
                f.attach();
            }
        }
    }

    @Test public void observerCloseIoAndRuntimeFailuresCannotLeakOrReleaseASuccessor() throws Exception {
        for (Exception failure : new Exception[]{new IOException("fixture unregister"),
                new IllegalStateException("fixture unregister runtime")}) try (Fixture f = new Fixture()) {
            f.attach();
            doThrow(failure).when(f.observer).close();
            assertSame(failure, assertThrows(Exception.class, f.attachment::close));
            assertFalse(Tbs5530ProductionSession.isActive());
            try (Tbs5530ProductionSession next = Tbs5530ProductionSession.acquire()) {
                f.attachment.close();
                assertTrue("Stale close must not release a successor", Tbs5530ProductionSession.isActive());
            }
        }
    }

    @Test(timeout = 20000) public void concurrentCreatorsRejectSecondDiscoveryBeforeAnyUsbOpen() throws Exception {
        CountDownLatch waitingPermission = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try (Fixture f = new Fixture()) {
                f.requested = () -> {
                    waitingPermission.countDown();
                    try { assertTrue(release.await(8, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                    f.callback.result(f.correlation, f.expected, false);
                };
                try { new Tbs5530DvbDeviceCreator().create(f.device, f.context, null); }
                catch (Throwable failure) { firstFailure.set(failure); }
                verify(f.request).close();
                verify(f.observer).close();
                verify(f.manager, never()).openDevice(any());
            } catch (Throwable failure) { firstFailure.set(failure); }
        });
        try {
            first.start();
            assertTrue(waitingPermission.await(6, TimeUnit.SECONDS));
            try (Fixture f = new Fixture()) {
                DvbException busy = assertThrows(DvbException.class,
                        () -> new Tbs5530DvbDeviceCreator().create(f.device, f.context, null));
                assertEquals(Tbs5530ProductionFailure.Kind.BUSY, Tbs5530ProductionFailure.classify(busy));
                assertTrue(f.facades.constructed().isEmpty());
                verify(f.manager, never()).openDevice(any());
                assertTrue(Tbs5530ProductionSession.isActive());
            }
        } finally { release.countDown(); first.join(4000); }
        assertFalse(first.isAlive());
        assertTrue(firstFailure.get() instanceof DvbException);
        assertEquals(Tbs5530ProductionFailure.Kind.PERMISSION, Tbs5530ProductionFailure.classify(firstFailure.get()));
        assertFalse(Tbs5530ProductionSession.isActive());
    }

    @Test(timeout = 10000) public void deviceKeepsOwnershipThroughDrainAndRejectsReopenEvenOnCleanupFailure() throws Exception {
        for (Exception drainFailure : new Exception[]{null, new IOException("fixture URBs still outstanding"),
                new IllegalStateException("fixture stop failed before drain")}) try (Fixture f = new Fixture();
                MockedStatic<DvbDemux> demux = mockStatic(DvbDemux.class)) {
            demux.when(DvbDemux::DvbDmxSwfilter).thenAnswer(a -> mock(DvbDemux.class));
            Tbs5530DvbDevice dvb = new Tbs5530DvbDevice(f.context, f.device, null, f.attach());
            ByteSource nativeSource = mock(ByteSource.class);
            CountDownLatch draining = new CountDownLatch(1), release = new CountDownLatch(1);
            doAnswer(a -> {
                draining.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                assertTrue(Tbs5530ProductionSession.isActive());
                if (drainFailure != null) throw drainFailure;
                return null;
            }).when(nativeSource).close();
            Tbs5530StreamGate gate = new Tbs5530StreamGate(() -> nativeSource, true);
            gate.readNext((bytes, count) -> { });
            java.lang.reflect.Field stream = Tbs5530DvbDevice.class.getDeclaredField("stream");
            stream.setAccessible(true); stream.set(dvb, gate);
            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread closer = new Thread(() -> {
                try { dvb.close(); } catch (Throwable failure) { result.set(failure); }
            });
            try {
                closer.start();
                assertTrue(draining.await(2, TimeUnit.SECONDS));
                assertThrows(DvbException.class, dvb::open);
                assertEquals(Tbs5530ProductionFailure.Kind.BUSY, assertThrows(Tbs5530ProductionFailure.class,
                        () -> new Tbs5530ProductionAttachment(f.context, f.device)).kind);
                verify(f.observer, never()).close();
            } finally { release.countDown(); closer.join(3000); }
            assertFalse(closer.isAlive());
            if (drainFailure != null) assertTrue(result.get() instanceof IOException); else assertNull(result.get());
            verify(nativeSource).close();
            if (drainFailure != null) {
                assertTrue(Tbs5530ProductionSession.isActive());
                verify(f.observer, never()).close();
                assertThrows(Tbs5530ProductionFailure.class, Tbs5530ProductionSession::acquire);
                // A later successful close is the proof that outstanding transfers are gone.
                doNothing().when(nativeSource).close();
                assertThrows(IOException.class, dvb::close); // Preserve the original cleanup error.
            }
            verify(f.observer).close();
            assertFalse(Tbs5530ProductionSession.isActive());
        }
    }

    @Test(timeout = 5000) public void processOwnershipRejectsConcurrentOpenUntilRelease() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread contender = new Thread(() -> {
            try (Tbs5530ProductionSession ignored = Tbs5530ProductionSession.acquire()) {
                result.set(new AssertionError("Concurrent production open succeeded"));
            } catch (Throwable failure) { result.set(failure); }
            finally { attempted.countDown(); }
        });
        try (Tbs5530ProductionSession owner = Tbs5530ProductionSession.acquire()) {
            contender.start();
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
            assertTrue(result.get() instanceof Tbs5530ProductionFailure);
            assertEquals(Tbs5530ProductionFailure.Kind.BUSY, ((Tbs5530ProductionFailure) result.get()).kind);
        } finally { contender.join(2000); }
        try (Tbs5530ProductionSession next = Tbs5530ProductionSession.acquire()) {
            assertTrue(Tbs5530ProductionSession.isActive());
        }
        assertFalse(Tbs5530ProductionSession.isActive());
    }
}
