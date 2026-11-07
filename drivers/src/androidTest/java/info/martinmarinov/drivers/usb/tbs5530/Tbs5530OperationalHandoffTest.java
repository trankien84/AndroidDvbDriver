// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Real Android lifecycle machinery, injected USB only. No production preferences or hardware. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530OperationalHandoffTest {
    private static final Tbs5530UsbBinding.Device DEVICE = new Tbs5530UsbBinding.Device("/fake/phase30", 1, 0x734c, 0x5530);
    private static byte[] descriptor() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,
                7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    private static final class Manager implements Tbs5530AndroidUsbFacade.Manager {
        final List<Tbs5530UsbBinding.Device> devices = new ArrayList<>(Arrays.asList(DEVICE));
        final Raw raw = new Raw();
        volatile boolean permission = true;
        int opens;
        public List<Tbs5530UsbBinding.Device> devices() { return new ArrayList<>(devices); }
        public boolean hasPermission(Tbs5530UsbBinding.Device device) { return permission; }
        public void requestPermission(Tbs5530UsbBinding.Device device, android.app.PendingIntent intent) { fail("No real permission dialog"); }
        public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device device) { opens++; return raw; }
    }
    private static final class Raw implements Tbs5530AndroidUsbFacade.RawConnection {
        byte[] bytes = descriptor();
        int rawReads, claims, releases, closes, controls, bulkReads, preparations;
        int metadataPacket = 512, pointer, address;
        boolean syntheticDemod;
        Runnable afterRaw;
        public byte[] rawDescriptors() { rawReads++; if (afterRaw != null) afterRaw.run(); return bytes.clone(); }
        public int interfaceCount() { return 1; }
        public int interfaceId(int index) { return 0; }
        public int alternateSetting(int index) { return 0; }
        public int endpointCount(int index) { return 2; }
        public int endpointAddress(int index, int ep) { return ep == 0 ? 0x82 : 0x81; }
        public int endpointDirection(int index, int ep) { return 128; }
        public int endpointType(int index, int ep) { return 2; }
        public int endpointMaxPacketSize(int index, int ep) { return metadataPacket; }
        public boolean claim(int index, boolean force) { assertFalse(force); claims++; return true; }
        public boolean release(int index) { releases++; return true; }
        public int controlTransfer(int type, int request, int value, int index, byte[] bytes, int offset, int length, int timeout) {
            controls++;
            if (syntheticDemod) {
                assertNotEquals(0xa0, request);
                if (request == 0x90) { pointer = bytes[2] & 255; address = bytes[1] & 255; }
                else if (request == 0x91) bytes[0] = (byte)(address == 0xcc
                        ? pointer == 0xfb ? 1 : pointer == 0xfd ? 0x97 : 0
                        : address == 0xc0 ? pointer == 0x7f ? 0xe0 : 0 : pointer == 0x10 ? 0x26 : 0);
            }
            return length;
        }
        public int bulkTransfer(int interfaceIndex, int endpointIndex, byte[] bytes, int length, int timeout) {
            assertTrue(syntheticDemod); assertEquals(0x82, endpointIndex); assertTrue(length <= 16384); bulkReads++;
            System.arraycopy(Tbs5530AndroidTsFixture.stream(), 0, bytes, 0, 564);
            return 564;
        }
        public int negotiatedUsbSpeed() { return 3; }
        public int lastBulkErrno() { return bulkReads > 0 ? 0 : -1; }
        public void prepareBulkIn82() { assertTrue(syntheticDemod); preparations++; }
        public void close() { closes++; }
    }
    private static final class Fixture implements AutoCloseable {
        final Manager manager = new Manager();
        final Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        final AtomicBoolean consent = new AtomicBoolean(true);
        final Tbs5530OperationalHandoff handoff;
        Fixture() throws Exception {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            handoff = new Tbs5530OperationalHandoff(context, runtime, manager, () -> {
                if (!consent.get()) throw new IOException("Descriptor-only consent revoked");
            });
        }
        void start() throws Exception {
            handoff.start(DEVICE);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (handoff.pending() && System.nanoTime() < deadline) Thread.sleep(5);
            assertFalse("Bounded fake acquisition", handoff.pending());
        }
        public void close() throws Exception {
            handoff.close(); runtime.close(); assertTrue(runtime.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    @Test public void sameFakeManagerOwnerCoordinatorAndGateReachManualAndAutoTsWithoutPhysicalUsb() throws Exception {
        for (boolean auto : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.manager.raw.syntheticDemod = true; f.start();
                java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
                f.runtime.execute(() -> {
                    try {
                        long[] clock = {0}; java.util.Set<String> consumed = new java.util.HashSet<>();
                        Tbs5530ControlProbe probe = new Tbs5530ControlProbe(f.handoff, new Tbs5530ControlProbe.Authorization() {
                            public void requireCurrent() throws IOException { if (!f.consent.get()) throw new IOException("Revoked fake"); }
                            public void consume(Tbs5530Frontend.Plan plan) throws IOException { if (!consumed.add(plan.getSha256())) throw new IOException("Consumed fake"); }
                            public void audit(String b, int o, int t, int r, int l, int result) { }
                            public boolean rfInConfirmed() { return true; }
                            public boolean experimentalAutoPlpApproved() { return auto; }
                        }, new Tbs5530Frontend.Clock() {
                            public long elapsedRealtimeMillis() { return clock[0]; }
                            public void delayMillis(long duration) { clock[0] += duration; }
                        });
                        probe.execute(probe.planProbe()); probe.execute(probe.planInitialize()); probe.readyForTune();
                        Tbs5530DvbT2TuneParameters p = new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, auto
                                ? Tbs5530DvbT2TuneParameters.Selection.automatic() : Tbs5530DvbT2TuneParameters.Selection.manual(42));
                        probe.execute(auto ? probe.planExperimentalAutoTune(p) : probe.planTune(p));
                        assertTrue(probe.execute(probe.planPollStatus(3)).status.locked);
                        Tbs5530TsSmoke.Permission permission = new Tbs5530TsSmoke.Permission() {
                            boolean used;
                            public void requireCurrent(long generation) throws IOException { if (!f.consent.get() || generation != f.handoff.generation()) throw new IOException("Fake epoch"); }
                            public void consume(Tbs5530TsSmoke.Limits limits, long generation) throws IOException {
                                requireCurrent(generation); if (used) throw new IOException("Consumed fake TS"); used = true;
                            }
                            public void audit(String event, int ordinal, int count) { }
                            public boolean endpointPreparationApproved() { return auto; }
                        };
                        Tbs5530TsSmoke.Result ts = f.handoff.smoke(permission, () -> 0L)
                                .run(new Tbs5530TsSmoke.Limits(564, 1, 5000, 500, 4096, auto));
                        assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, ts.status);
                        assertEquals(0, ts.lastReadErrno);
                        assertEquals(4, consumed.size());
                    } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); }
                });
                assertTrue(done.await(10, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
                assertEquals(1, f.manager.opens); assertEquals(1, f.manager.raw.claims); assertEquals(1, f.manager.raw.bulkReads);
                assertEquals(auto ? 187 : 188, f.manager.raw.controls); assertEquals(auto ? 1 : 0, f.manager.raw.preparations);
            }
        }
    }
    @Test public void freshOwnerNeverReusesIdentityEvenWhenPathAndGenerationAreReused() throws Exception {
        String previous;
        try (Fixture f = new Fixture()) { f.start(); previous = f.handoff.approvalIdentity(); }
        try (Fixture f = new Fixture()) { f.start(); assertNotEquals(previous, f.handoff.approvalIdentity()); }
    }
    @Test public void freshOperationalTreeClaimsOnceButNeverDeclaresFrontendReadyOrUploads() throws Exception {
        Fixture f = new Fixture();
        try {
            f.start(); assertTrue(f.handoff.operationalOwner());
            assertEquals(3, f.handoff.observation().negotiatedUsbSpeed);
            assertEquals(Tbs5530Session.State.CONNECTED, f.handoff.session().getState());
            assertFalse(f.handoff.session().isFrontendInitialized()); assertFalse(f.handoff.session().isFirmwareUploaded());
            assertEquals(1, f.manager.opens); assertEquals(1, f.manager.raw.rawReads); assertEquals(1, f.manager.raw.claims);
            assertEquals(0, f.manager.raw.controls);
        } finally { f.close(); }
        assertEquals(1, f.manager.raw.releases); assertEquals(1, f.manager.raw.closes);
    }
    @Test public void unknownAndMetadataMismatchCloseUnclaimedWithoutAnyControl() throws Exception {
        for (boolean rawMismatch : new boolean[]{true, false}) {
            Fixture f = new Fixture();
            try {
                if (rawMismatch) f.manager.raw.bytes[45] = (byte)0x83;
                else f.manager.raw.metadataPacket = 64;
                f.start(); assertFalse(f.handoff.operationalOwner()); assertNotNull(f.handoff.failure());
                assertEquals(0, f.manager.raw.claims); assertEquals(1, f.manager.raw.closes); assertEquals(0, f.manager.raw.controls);
            } finally { f.close(); }
            assertEquals(0, f.manager.raw.releases); assertEquals(1, f.manager.raw.closes);
        }
    }
    @Test public void duplicateMatchingDevicesNeverOpenAndLostPermissionCannotClaim() throws Exception {
        try (Fixture f = new Fixture()) {
            f.manager.devices.add(new Tbs5530UsbBinding.Device("/fake/second", 2, 0x734c, 0x5530));
            f.start(); assertEquals(0, f.manager.opens); assertNotNull(f.handoff.failure());
        }
        try (Fixture f = new Fixture()) {
            f.manager.raw.afterRaw = () -> f.manager.permission = false;
            f.start(); assertEquals(0, f.manager.raw.claims); assertEquals(1, f.manager.raw.closes);
        }
    }
    @Test public void foregroundOrConsentLossInvalidatesOwnerWithoutForcingNativeClose() throws Exception {
        try (Fixture f = new Fixture()) {
            f.start(); assertTrue(f.handoff.operationalOwner());
            f.consent.set(false); f.handoff.revoke();
            assertFalse(f.handoff.operationalOwner()); assertFalse(f.handoff.session().isFrontendLocked());
            assertThrows(IOException.class, () -> f.handoff.start(DEVICE)); assertEquals(0, f.manager.raw.controls);
        }
    }
}
