// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class Tbs5530OneShotBootstrapTest {
    private static final class Rig implements Tbs5530UsbBinding.Facade, Tbs5530LifecycleCoordinator.Connection {
        final Tbs5530Session session = new Tbs5530Session();
        final Tbs5530UsbBinding.Device device = new Tbs5530UsbBinding.Device("synthetic", 1, 0x734c, 0x5530);
        final Tbs5530FirmwarePlan plan;
        final Tbs5530UsbBinding binding;
        final Tbs5530OneShotBootstrap runner;
        final List<Throwable> errors = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        Tbs5530UsbBinding.Events events;
        boolean permission = true, present = true, approved = true, duplicate, consumed, closeFails, operational;
        boolean wrongIdentity, malformed;
        int writes, opens, closes, reads, failAt = -1;
        String failure = "short";
        Runnable during = () -> { };
        Rig() throws Exception {
            plan = Tbs5530BootstrapDryRunTest.plan(Tbs5530BootstrapDryRunTest.image(8192));
            binding = new Tbs5530UsbBinding(session, this, Runnable::run,
                    (r, d) -> () -> { }, errors::add, 30000, 10000);
            runner = new Tbs5530OneShotBootstrap(session, binding, this, device,
                    Tbs5530Fx2ProtocolPlan.describe(plan, "TBS_SOURCE_DUAL_RESET"),
                    () -> { if (!approved) throw new IOException("No current approval/ADB/observer/power"); },
                    () -> { if (consumed) throw new IOException("Consumed"); consumed = true; },
                    ms -> assertEquals(100, ms), binding::bootstrapRawDescriptors);
        }
        public List<Tbs5530UsbBinding.Device> devices() {
            if (!present) return Collections.emptyList();
            return duplicate ? java.util.Arrays.asList(device, new Tbs5530UsbBinding.Device("other", 2, 0x734c, 0x5530))
                    : Collections.singletonList(device);
        }
        public boolean hasPermission(Tbs5530UsbBinding.Device d) { return permission; }
        public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device d, String c,
                Tbs5530UsbBinding.PermissionCallback callback) { throw new AssertionError("No implicit grant"); }
        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events e) { events = e; return () -> { }; }
        public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device d) { throw new AssertionError("No operational open"); }
        public Tbs5530LifecycleCoordinator.Connection openBootstrap(Tbs5530UsbBinding.Device d) { opens++; return this; }
        public void claim(Tbs5530LifecycleCoordinator.Claim c) { throw new AssertionError("No claim"); }
        public void cancel() { throw new AssertionError("No stream cancel"); }
        public void release() { throw new AssertionError("No interface release"); }
        public void close() throws IOException { closes++; if (closeFails) throw new IOException("cleanup"); }
        public byte[] rawDescriptors() {
            reads++;
            byte[] raw = operational ? operational() : Tbs5530Fx2FingerprintTest.bootstrap();
            if (wrongIdentity) raw[8] = 0;
            return malformed ? java.util.Arrays.copyOf(raw, 20) : raw;
        }
        public int controlTransfer(int type, int request, int value, int index, byte[] data,
                                   int offset, int length, int timeout) throws IOException {
            int ordinal = writes++;
            Tbs5530FirmwarePlan.Step expected = plan.getSteps().get(ordinal);
            assertTrue(consumed); assertEquals(0, closes);
            assertEquals(0x40, type); assertEquals(0xa0, request); assertEquals(0, index); assertEquals(0, offset);
            assertEquals(expected.getValue(), value); assertEquals(2000, timeout); assertArrayEquals(expected.getPayload(), data);
            calls.add("write"); during.run();
            if (ordinal == failAt) {
                if (failure.equals("negative")) return -1;
                if (failure.equals("timeout")) throw new SocketTimeoutException();
                if (failure.equals("exception")) throw new IllegalStateException();
                return length - 1;
            }
            return length;
        }
        void load() throws IOException { runner.acquire(); runner.execute(); }
        void failLoad() throws IOException {
            try { load(); fail("Should stop"); } catch (IOException | IllegalStateException expected) { }
            runner.releaseUpload();
        }
    }
    private static byte[] operational() {
        return new byte[]{18,1,0,2,(byte)255,(byte)255,(byte)255,64,0x4c,0x73,0x30,0x55,0,1,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50, 9,4,0,0,2,(byte)255,(byte)255,(byte)255,0,
                7,5,(byte)0x82,2,0,2,0, 7,5,(byte)0x81,2,0,2,0};
    }
    @Test public void exact132Writes8196BytesThenFreshObservationNeverReady() throws Exception {
        Rig r = new Rig(); r.load();
        assertEquals(132, r.writes); assertEquals(8196, r.runner.snapshot().bytes);
        assertEquals(132, r.runner.snapshot().completed);
        assertEquals(Tbs5530OneShotBootstrap.State.TRANSITIONING, r.runner.snapshot().state);
        assertFalse(r.session.isInterfaceClaimed()); assertNotEquals(Tbs5530Session.State.READY, r.session.getState());
        long first = r.runner.snapshot().epoch;
        r.runner.releaseUpload(); assertEquals(1, r.closes);
        r.operational = true; r.runner.acquireObservation(); r.runner.observe(); r.runner.releaseUpload();
        assertTrue(r.runner.snapshot().epoch > first); assertEquals(2, r.opens); assertEquals(2, r.closes);
        assertEquals(Tbs5530OneShotBootstrap.State.OPERATIONAL_CANDIDATE, r.runner.snapshot().state);
        assertEquals(132, r.writes); assertNotEquals(Tbs5530Session.State.READY, r.session.getState());
        try { r.runner.acquire(); fail(); } catch (IOException expected) { }
    }
    @Test public void everyWriteFailureIsUncertainWithoutCorrectiveReleaseOrRetry() throws Exception {
        for (String mode : new String[]{"short", "negative", "timeout", "exception"}) {
            for (int n = 0; n < 132; n++) {
                Rig r = new Rig(); r.failAt = n; r.failure = mode; r.failLoad();
                assertEquals(n + 1, r.writes); assertEquals(n, r.runner.snapshot().completed);
                assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state);
                assertEquals(1, r.closes); assertTrue(r.consumed);
                try { r.runner.execute(); fail(); } catch (IOException expected) { }
                assertEquals(n + 1, r.writes);
            }
        }
    }
    @Test public void missingPreflightBlocksBeforeOpen() throws Exception {
        for (int cause = 0; cause < 4; cause++) {
            Rig r = new Rig();
            if (cause == 0) r.approved = false; else if (cause == 1) r.permission = false;
            else if (cause == 2) r.present = false; else r.duplicate = true;
            r.failLoad(); assertEquals(0, r.opens); assertEquals(0, r.writes); assertFalse(r.consumed);
        }
    }
    @Test public void fingerprintIdentityAndMalformedTreesBlockBeforeConsumption() throws Exception {
        for (int cause = 0; cause < 3; cause++) {
            Rig r = new Rig();
            if (cause == 0) r.operational = true; else if (cause == 1) r.wrongIdentity = true; else r.malformed = true;
            r.failLoad(); assertEquals(0, r.writes); assertFalse(r.consumed); assertEquals(1, r.closes);
        }
    }
    @Test public void adbRevocationDetachPermissionAndStaleEpochStopAfterOneCall() throws Exception {
        for (int cause = 0; cause < 5; cause++) {
            Rig r = new Rig(); final int c = cause;
            r.during = () -> { if (c == 0) r.approved = false; else if (c == 1) r.permission = false;
                else if (c == 2) r.present = false; else if (c == 3) r.events.attached(r.device); else r.binding.cancel(); };
            r.failLoad(); assertEquals(1, r.writes);
            assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state);
        }
    }
    @Test public void detachAfterReleaseCannotAuthorizeAnotherUpload() throws Exception {
        Rig r = new Rig(); r.load(); r.runner.releaseUpload(); r.present = false;
        try { r.runner.acquireObservation(); fail(); } catch (IOException expected) { r.runner.stop(expected); }
        assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state); assertEquals(132, r.writes);
    }
    @Test public void noOperationalDescriptorRemainsUncertain() throws Exception {
        Rig r = new Rig(); r.load(); r.runner.releaseUpload(); r.runner.acquireObservation();
        try { r.runner.observe(); fail(); } catch (IOException expected) { }
        assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state); r.runner.releaseUpload();
    }
    @Test public void closeDuringInFlightDefersNativeCloseUntilReturn() throws Exception {
        Rig r = new Rig();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch leave = new java.util.concurrent.CountDownLatch(1);
        r.during = () -> {
            entered.countDown();
            try { assertTrue(leave.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        };
        r.runner.acquire();
        java.util.concurrent.atomic.AtomicReference<Throwable> result = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> { try { r.runner.execute(); } catch (Throwable error) { result.set(error); } });
        worker.start(); assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        r.binding.cancel(); assertEquals(0, r.closes); assertTrue(r.session.isCleanupPending());
        leave.countDown(); worker.join(5000); assertFalse(worker.isAlive()); assertNotNull(result.get());
        assertEquals(1, r.closes); assertEquals(1, r.writes);
        assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state);
    }
    @Test public void cleanupFailureCannotBecomeSuccessfulCandidate() throws Exception {
        Rig r = new Rig(); r.load(); r.closeFails = true;
        try { r.runner.releaseUpload(); fail(); } catch (IOException expected) { r.runner.stop(expected); }
        assertEquals(Tbs5530OneShotBootstrap.State.UNCERTAIN, r.runner.snapshot().state);
        assertEquals(132, r.writes);
    }
    @Test public void comparatorAlternativeCannotBecomeHardwarePlan() throws Exception {
        Rig r = new Rig();
        try { new Tbs5530OneShotBootstrap(r.session, r.binding, r, r.device,
                Tbs5530Fx2ProtocolPlan.describe(r.plan, "DOCUMENTED_FX2_E600_ONLY"), () -> { }, () -> { },
                ms -> { }, r.binding::bootstrapRawDescriptors); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void boundedFileRejectsMissingTruncatedExtraPaddingHashAndMetadata() throws Exception {
        for (int size : new int[]{0,8191,8192,8193}) {
            try { Tbs5530OneShotBootstrap.readFirmware(new ByteArrayInputStream(new byte[size]),
                    Tbs5530FirmwareValidation.SOURCE, Tbs5530FirmwareValidation.ENTRY,
                    Tbs5530FirmwareValidation.Format.RAW_LINEAR); fail(); } catch (IOException | IllegalArgumentException expected) { }
        }
        for (Tbs5530FirmwareValidation.Format format : Tbs5530FirmwareValidation.Format.values()) {
            try { Tbs5530OneShotBootstrap.readFirmware(new ByteArrayInputStream(new byte[8192]), "unknown", "bad", format);
                fail(); } catch (IllegalArgumentException expected) { }
        }
        assertFalse(Tbs5530FirmwareValidation.hardwareUploadAllowed());
    }
}
