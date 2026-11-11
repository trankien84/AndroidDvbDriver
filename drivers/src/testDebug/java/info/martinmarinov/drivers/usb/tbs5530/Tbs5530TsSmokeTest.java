// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class Tbs5530TsSmokeTest {
    private static final class Owner implements Tbs5530Session.Resources {
        int closes, releases;
        boolean failClose;
        public void cancel() { }
        public void release() { releases++; }
        public void close() throws IOException { closes++; if (failClose) throw new IOException("cleanup"); }
    }
    private static final class Fixture implements Tbs5530TsSmoke.Permission {
        final Tbs5530Session session = new Tbs5530Session();
        final Owner owner = new Owner();
        final long token;
        final Tbs5530TsSmoke smoke;
        int consumes, selects, reads, count = 188 * 3, audits, requested, timeoutRequested;
        boolean rf = true, consent = true, failAudit, endpointApproved, failPreparation, nativeApproved, explicitAlt0Approved;
        boolean sonyApproved, failSony;
        int sonyCalls;
        long sonyNanos;
        Tbs5530ExplicitInterfaceSelection interfaceGate;
        int preparations, errno = -1;
        long preparationNanos;
        long nanos;
        Runnable duringRead, beforeBulk;
        byte[] stream = Tbs5530TsParserPsiTest.patPmtFixture();
        Tbs5530TsSmoke.ChunkObserver observer;
        Fixture(boolean locked) throws Exception { this(locked, Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO); }
        Fixture(boolean locked, Tbs5530TsSmoke.ReadApi readApi) throws Exception {
            token = session.requestPermission(); session.permissionResult(token, true); session.connected(token, owner);
            session.interfaceClaimed(token); session.ready(token); session.frontendInitialized(token); session.frontendLock(token, locked);
            Tbs5530Bridge bridge = new Tbs5530Bridge((type, request, value, index, bytes, offset, length, timeout) -> {
                assertEquals(0x40, type); assertEquals(0x8a, request); assertEquals(2, length);
                assertArrayEquals(new byte[]{10, 0}, bytes); selects++; return length;
            }, session, token);
            Tbs5530BulkTransport bulk = new Tbs5530BulkTransport(session, token, owner, () -> { }, (bytes, length, timeout) -> {
                if (interfaceGate != null) interfaceGate.read(() -> 0);
                assertTrue(timeout > 0 && timeout <= 2000); reads++; requested = length; timeoutRequested = timeout;
                System.arraycopy(stream, 0, bytes, 0, Math.min(length, stream.length));
                if (duringRead != null) duringRead.run(); nanos += 100_000_000L; return count;
            }, () -> {
                assertEquals(1, consumes); assertEquals(0, selects); preparations++; nanos += preparationNanos;
                if (failPreparation) throw new IOException("Synthetic clear-halt failed, no retry");
            }, () -> errno);
            smoke = new Tbs5530TsSmoke(session, token, bridge, bulk, this, () -> nanos, readApi,
                    (bytes, length) -> { if (observer != null) observer.accept(bytes, length); });
        }
        public boolean nativeUrbReceptionApproved() { return nativeApproved && consent && rf; }
        public boolean explicitSetInterfaceAlt0Approved() { return explicitAlt0Approved && consent && rf; }
        public boolean sonyTsOutputReadbackApproved() { return sonyApproved && consent && rf; }
        public void beforeFirstBulk() throws IOException {
            assertEquals(1, consumes); assertEquals(1, selects); assertEquals(0, reads);
            assertTrue(session.isStreaming()); sonyCalls++; nanos += sonyNanos;
            if (failSony) throw new IOException("Synthetic readback failure, no Bulk");
        }
        public boolean endpointPreparationApproved() { return endpointApproved && consent && rf; }
        public void consume(Tbs5530TsSmoke.Limits limits, long generation) throws IOException {
            requireCurrent(generation); if (consumes++ != 0) throw new IOException("consumed");
        }
        public void requireCurrent(long generation) throws IOException {
            if (!consent || !rf || generation != token) throw new IOException("RF IN/consent no longer confirmed");
        }
        public void audit(String event, int ordinal, int count) throws IOException {
            audits++; if (failAudit) throw new IOException("audit");
            if ("BULK_READ_INTENT".equals(event) && beforeBulk != null) beforeBulk.run();
        }
        Tbs5530TsSmoke.Limits limits() { return new Tbs5530TsSmoke.Limits(564, 2, 1000, 500); }
    }
    @Test public void sonyReadbackIsOffByDefaultAndRestrictedToUnmodifiedNative4096Baseline() throws Exception {
        Tbs5530TsSmoke.Limits off = new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        assertFalse(off.sonyTsOutputReadback);
        Tbs5530TsSmoke.Limits on = sonyLimits(); assertTrue(on.sonyTsOutputReadback);
        assertEquals(off.maximumMillis()+5000,on.maximumMillis());
        assertThrows(IllegalArgumentException.class,() -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,8192,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192,false,true));
        assertThrows(IllegalArgumentException.class,() -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,true,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,false,true));
        assertThrows(IllegalArgumentException.class,() -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true,true));
        assertThrows(IllegalArgumentException.class,() -> new Tbs5530TsSmoke.Limits(65536,16,5000,501,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,false,true));
    }
    private static Tbs5530TsSmoke.Limits sonyLimits() {
        return new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,false,true);
    }
    @Test public void sonyReadbackNeedsDistinctApprovalBeforeStreamSelection() throws Exception {
        Fixture f = new Fixture(true,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096); f.nativeApproved=true;
        assertThrows(IOException.class,() -> f.smoke.run(sonyLimits()));
        assertEquals(0,f.consumes);assertEquals(0,f.selects);assertEquals(0,f.sonyCalls);assertEquals(0,f.reads);f.session.close();
    }
    @Test public void sonyReadbackRunsAfterStreamSelectionBeforeBulkAndDoesNotUseReceiveWindow() throws Exception {
        Fixture f = new Fixture(true,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096); f.nativeApproved=true;f.sonyApproved=true;f.count=4096;f.sonyNanos=4_000_000_000L;
        f.smoke.run(sonyLimits());assertEquals(1,f.sonyCalls);assertEquals(16,f.reads);assertEquals(1,f.selects);assertEquals(0,f.preparations);assertEquals(500,f.timeoutRequested);
        assertEquals(1,f.owner.releases);assertEquals(1,f.owner.closes);
    }
    @Test public void sonyReadbackFailureStopsBeforeBulkAndDrainsWithoutRetry() throws Exception {
        Fixture f = new Fixture(true,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);f.nativeApproved=true;f.sonyApproved=true;f.failSony=true;
        assertThrows(IOException.class,() -> f.smoke.run(sonyLimits()));assertEquals(1,f.sonyCalls);assertEquals(0,f.reads);assertEquals(1,f.selects);
        assertEquals(1,f.owner.releases);assertEquals(1,f.owner.closes);assertThrows(IOException.class,() -> f.smoke.run(sonyLimits()));assertEquals(1,f.sonyCalls);
    }
    @Test public void explicitAlt0NeedsOwnApprovalBeforeAttemptOrAnyStreamVendorWrite() throws Exception {
        Fixture f = new Fixture(true, Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096); f.nativeApproved = true;
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true);
        assertThrows(IOException.class, () -> f.smoke.run(limits));
        assertEquals(0, f.consumes); assertEquals(0, f.selects); assertEquals(0, f.reads);
        f.session.close(); assertEquals(1, f.owner.closes);
    }
    @Test public void explicitAlt0FalseExceptionDetachDrainOnceAndCannotRestartOrUploadFirmware() throws Exception {
        for (int fault = 0; fault < 3; fault++) {
            Fixture f = new Fixture(true, Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
            f.nativeApproved = true; f.explicitAlt0Approved = true;
            Tbs5530ExplicitInterfaceSelectionTest.Port p = new Tbs5530ExplicitInterfaceSelectionTest.Port();
            if (fault == 0) p.result = false;
            if (fault == 1) p.runtimeFailure = new IllegalStateException("Fake API failure");
            if (fault == 2) p.during = () -> p.present = false;
            f.interfaceGate = p.gate(true);
            Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,
                    Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true);
            assertThrows(IOException.class, () -> f.smoke.run(limits));
            assertEquals(1, f.consumes); assertEquals(1, p.calls); assertEquals(0, f.reads); assertEquals(1, f.selects);
            assertEquals(0, f.preparations); assertEquals(1, f.owner.releases); assertEquals(1, f.owner.closes);
            assertThrows(IOException.class, () -> f.smoke.run(limits)); assertEquals(1, p.calls); assertEquals(1, f.selects);
            f.session.close(); assertEquals(1, f.owner.closes);
        }
    }
    @Test public void explicitAlt0SuccessSelectsOnceAndKeepsOrdinaryBoundedCleanup() throws Exception {
        Fixture f = new Fixture(true, Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        f.nativeApproved = true; f.explicitAlt0Approved = true; f.count = 4096;
        Tbs5530ExplicitInterfaceSelectionTest.Port p = new Tbs5530ExplicitInterfaceSelectionTest.Port(); f.interfaceGate = p.gate(true);
        assertEquals(Tbs5530Session.State.READY, f.session.getState()); assertFalse(f.session.isStreaming());
        f.beforeBulk = () -> {
            assertEquals(Tbs5530Session.State.READY, f.session.getState());
            assertTrue(f.session.isStreaming()); assertTrue(f.session.isInterfaceClaimed());
        };
        f.duringRead = f.beforeBulk;
        f.smoke.run(new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true));
        assertEquals(1, p.calls); assertEquals(16, f.reads); assertEquals(1, f.selects); assertEquals(0, f.preparations);
        assertEquals(1, f.owner.releases); assertEquals(1, f.owner.closes);
    }
    @Test public void confirmedLockRfAndApprovalAllowExactlyOneStreamSelectThenBoundedBulk() throws Exception {
        Fixture f = new Fixture(true); Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, result.status);
        assertEquals(564, result.bytes); assertEquals(3, result.packets.packets); assertEquals(1, f.consumes);
        assertEquals(1, f.selects); assertEquals(1, f.reads); assertEquals(1, f.owner.releases); assertEquals(1, f.owner.closes);
        assertEquals(5640.0, result.bytesPerSecond(), 0.001);
        assertThrows(IOException.class, () -> f.smoke.run(f.limits())); assertEquals(1, f.selects);
    }
    private static byte[] fullBoundedSample() {
        byte[] sample = new byte[65536];
        byte[] psi = Tbs5530TsParserPsiTest.patPmtFixture();
        System.arraycopy(psi, 0, sample, 0, psi.length);
        for (int at = psi.length, packet = 1; at + 4 <= sample.length; at += 188, packet++) {
            sample[at] = 0x47; sample[at + 1] = 1; sample[at + 2] = 1;
            sample[at + 3] = (byte) (0x10 | packet & 15);
        }
        return sample;
    }
    private static Tbs5530TsSmoke.Result nativeSample(byte[] sample, Fixture f) throws Exception {
        f.nativeApproved = true; f.count = 4096;
        f.beforeBulk = () -> f.stream = java.util.Arrays.copyOfRange(sample, f.reads * 4096, (f.reads + 1) * 4096);
        return f.smoke.run(new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096));
    }
    @Test public void earlyPatPmtDoesNotStopBeforeEntireApprovedNativeSample() throws Exception {
        Fixture f = new Fixture(true, Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        f.observer = (bytes, length) -> raw.write(bytes,0,length);
        byte[] sample = fullBoundedSample();
        Tbs5530TsSmoke.Result result = nativeSample(sample,f);
        assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED,result.status);
        assertEquals(65536,result.bytes); assertEquals(16,result.reads);
        assertEquals(348,result.packets.packets); assertTrue(result.packets.hasValidProgramStructure());
        assertEquals(0,result.packets.transportErrorPackets); assertEquals(0,result.packets.continuityErrors);
        assertEquals(0,result.packets.syncLosses); assertEquals(0,result.packets.malformedPackets);
        assertArrayEquals(sample,raw.toByteArray()); assertEquals(1,f.owner.closes);
    }
    @Test public void qualityErrorsLateInNativeSampleAreNotHiddenByEarlyStructure() throws Exception {
        for (boolean transportError : new boolean[]{true,false}) {
            Fixture f = new Fixture(true, Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096);
            byte[] sample = fullBoundedSample();
            if (transportError) sample[300 * 188 + 1] |= 0x80;
            else sample[300 * 188 + 3] ^= 1;
            Tbs5530TsSmoke.Result result = nativeSample(sample,f);
            assertEquals(65536,result.bytes); assertEquals(16,result.reads);
            assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED,result.status);
            assertEquals("ERRORS_OBSERVED",result.qualityObservation());
            if (transportError) assertEquals(1,result.packets.transportErrorPackets);
            else assertTrue(result.packets.continuityErrors > 0);
        }
    }
    @Test public void rawObserverSeesEveryByteAndFailureCannotPublishPass() throws Exception {
        Fixture f = new Fixture(true); java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        f.observer = (bytes, length) -> raw.write(bytes, 0, length);
        Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertArrayEquals(f.stream, raw.toByteArray()); assertEquals(raw.size(), result.bytes);
        Fixture failure = new Fixture(true); failure.observer = (bytes, length) -> { throw new IOException("Raw audit write failed"); };
        assertThrows(IOException.class, () -> failure.smoke.run(failure.limits()));
        assertEquals(1, failure.owner.closes); assertEquals(1, failure.reads);
    }
    @Test public void synchronizedPacketsWithoutPatPmtAreNotTsSmokePass() throws Exception {
        Fixture f = new Fixture(true); f.stream = new byte[564];
        for (int at = 0; at < f.stream.length; at += 188) {
            f.stream[at] = 0x47; f.stream[at + 2] = 0x20; f.stream[at + 3] = (byte)(0x10 | at / 188);
        }
        Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertEquals(Tbs5530TsSmoke.Status.INVALID_TS, result.status);
        assertTrue(result.usbDataReceived()); assertFalse(result.structureConfirmed());
        assertEquals("NOT_ASSESSED", result.qualityObservation());
    }
    @Test public void structureWithTeiIsNotReportedAsCleanOrAcceptableViewingQuality() throws Exception {
        Fixture f = new Fixture(true); f.stream[188 * 2 + 1] |= (byte) 0x80;
        Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, result.status);
        assertTrue(result.structureConfirmed()); assertEquals(1, result.packets.transportErrorPackets);
        assertEquals("ERRORS_OBSERVED", result.qualityObservation());
    }
    @Test public void queuedBackendMismatchCannotConsumeOrSelect() throws Exception {
        Fixture f = new Fixture(true);
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false,
                Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096);
        assertThrows(IOException.class, () -> f.smoke.run(limits));
        assertEquals(0, f.consumes); assertEquals(0, f.selects); assertEquals(0, f.reads); f.session.close();
    }
    @Test public void nativeDiagnosticNeedsItsOwnApprovalBeforeConsumeOrSelect() throws Exception {
        for (Tbs5530TsSmoke.ReadApi api : nativeApis()) {
            int chunk = nativeChunk(api);
            Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, chunk, false, api);
            assertEquals(8000, limits.maximumMillis());
            Fixture denied = new Fixture(true, api);
            assertThrows(IOException.class, () -> denied.smoke.run(limits));
            assertEquals(0, denied.consumes); assertEquals(0, denied.selects); denied.session.close();
            Fixture allowed = new Fixture(true, api); allowed.nativeApproved = true; allowed.count = -1; allowed.errno = 0;
            Tbs5530TsSmoke.Result result = allowed.smoke.run(limits);
            assertEquals(Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE, result.status);
            assertEquals(-1, result.lastReadResult); assertEquals(0, result.lastReadErrno);
            assertEquals(chunk, result.lastReadRequestedBytes);
            assertEquals(0, result.bytes); assertEquals(1, allowed.reads); assertEquals(1, allowed.owner.closes);
        }
    }
    private static Tbs5530TsSmoke.ReadApi[] nativeApis() {
        return new Tbs5530TsSmoke.ReadApi[]{Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192};
    }
    private static int nativeChunk(Tbs5530TsSmoke.ReadApi api) {
        return api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192 ? 8192 : 4096;
    }
    @Test public void nativeDiagnosticRejectsUnboundedVariantsAndClearHalt() {
        for (Tbs5530TsSmoke.ReadApi api : nativeApis()) {
            int chunk = nativeChunk(api), other = chunk == 4096 ? 8192 : 4096;
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,chunk,true,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,other,false,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(7 * chunk,16,5000,500,chunk,false,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,1,5000,500,chunk,false,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65535,16,5000,500,chunk,false,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(2 * 1024 * 1024,16,5000,500,chunk,false,api));
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,257,5000,500,chunk,false,api));
        }
    }
    @Test public void bothNativeSizesObserveTheSameBoundedBytesWithoutEndpointPreparation() throws Exception {
        byte[] sample = fullBoundedSample();
        for (Tbs5530TsSmoke.ReadApi api : nativeApis()) {
            int chunk = nativeChunk(api);
            Fixture f = new Fixture(true,api); f.nativeApproved = true; f.count = chunk;
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            f.observer = (bytes,length) -> raw.write(bytes,0,length);
            f.beforeBulk = () -> f.stream = java.util.Arrays.copyOfRange(sample,f.reads * chunk,(f.reads + 1) * chunk);
            Tbs5530TsSmoke.Result result = f.smoke.run(new Tbs5530TsSmoke.Limits(65536,16,5000,500,chunk,false,api));
            assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED,result.status);
            assertEquals(65536,result.bytes); assertEquals(65536 / chunk,result.reads);
            assertEquals(chunk,result.lastReadRequestedBytes); assertArrayEquals(sample,raw.toByteArray());
            assertEquals(0,f.preparations); assertEquals(1,f.selects); assertEquals(1,f.owner.closes);
        }
    }
    @Test public void nativeShortCompletionsNeverCreateAnUnapprovedPartialSlot() throws Exception {
        for (Tbs5530TsSmoke.ReadApi api : nativeApis()) {
            int chunk = nativeChunk(api);
            Fixture f = new Fixture(true,api); f.nativeApproved = true; f.count = chunk - 1;
            Tbs5530TsSmoke.Result result = f.smoke.run(new Tbs5530TsSmoke.Limits(8 * chunk,16,5000,500,chunk,false,api));
            assertEquals(8,result.reads); assertEquals(8 * (chunk - 1),result.bytes);
            assertEquals(chunk,result.lastReadRequestedBytes); assertEquals(1,f.owner.closes);
        }
    }
    @Test public void changingNativeSizeCannotReuseAnotherBackendApproval() throws Exception {
        Fixture f = new Fixture(true,Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096); f.nativeApproved = true;
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536,16,5000,500,8192,false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192);
        assertThrows(IOException.class,() -> f.smoke.run(limits));
        assertEquals(0,f.consumes); assertEquals(0,f.selects); assertEquals(0,f.reads); f.session.close();
    }
    @Test public void endpointPreparationNeedsSeparateConsentAndRunsOnceBeforeSelect() throws Exception {
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(564, 1, 1000, 500, 4096, true);
        assertEquals(8000, limits.maximumMillis());
        Fixture denied = new Fixture(true);
        assertThrows(IOException.class, () -> denied.smoke.run(limits));
        assertEquals(0, denied.preparations); assertEquals(0, denied.consumes); denied.session.close();
        Fixture allowed = new Fixture(true); allowed.endpointApproved = true;
        Tbs5530TsSmoke.Result result = allowed.smoke.run(limits);
        assertTrue(result.endpointPrepared); assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, result.status);
        assertEquals(1, allowed.preparations); assertEquals(1, allowed.selects); assertEquals(1, allowed.reads);
        assertThrows(IOException.class, () -> allowed.smoke.run(limits)); assertEquals(1, allowed.preparations);
    }
    @Test public void failedOrOverBudgetPreparationCannotSelectStreamOrRetry() throws Exception {
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(564, 1, 1000, 500, 4096, true);
        for (boolean fail : new boolean[]{true, false}) {
            Fixture f = new Fixture(true); f.endpointApproved = true; f.failPreparation = fail;
            f.preparationNanos = fail ? 0 : 8_000_000_000L;
            assertThrows(IOException.class, () -> f.smoke.run(limits)); assertEquals(1, f.preparations);
            assertEquals(0, f.selects); assertEquals(0, f.reads); assertEquals(1, f.owner.closes);
        }
    }
    @Test public void sourceSizedChunkAndNegativeReadTelemetryDoNotInventErrnoOrRetry() throws Exception {
        Fixture f = new Fixture(true); f.count = -1; f.errno = 32;
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500);
        assertEquals(4096, limits.readChunkBytes);
        Tbs5530TsSmoke.Result result = f.smoke.run(limits);
        assertEquals(Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE, result.status);
        assertEquals(-1, result.lastReadResult); assertEquals(32, result.lastReadErrno);
        assertEquals(4096, result.lastReadRequestedBytes);
        assertEquals(500, result.lastReadTimeoutMillis); assertEquals(100_000_000L, result.lastReadWallNanos);
        assertEquals(1, f.reads); assertEquals(4096, f.requested); assertEquals(500, f.timeoutRequested);
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(564, 1, 1000, 500, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(564, 1, 1000, 500, 16385));
        assertEquals(16384, new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 16384).readChunkBytes);
    }
    @Test public void lateNativeReturnCannotPublishTsPassOutsideApprovedDuration() throws Exception {
        Fixture f = new Fixture(true); f.duringRead = () -> f.nanos = 1_000_000_000L;
        assertEquals(Tbs5530TsSmoke.Status.LIMIT_REACHED, f.smoke.run(f.limits()).status);
        assertEquals(1, f.reads);
    }
    @Test public void auditCannotSpendReadBudgetAndStillStartNativeWithOldTimeout() throws Exception {
        Fixture f = new Fixture(true); f.beforeBulk = () -> f.nanos = 1_000_000_000L;
        Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertEquals(0, f.reads); assertEquals(0, result.bytes);
        assertEquals(Tbs5530TsSmoke.Status.LIMIT_REACHED, result.status);
    }
    @Test public void absentRfOrLockOrApprovalSendsNoStreamSelect() throws Exception {
        for (int missing = 0; missing < 3; missing++) {
            Fixture f = new Fixture(missing != 0);
            if (missing == 1) f.rf = false;
            if (missing == 2) f.consent = false;
            assertThrows(IOException.class, () -> f.smoke.run(f.limits()));
            assertEquals(0, f.selects); assertEquals(0, f.reads); f.session.close();
        }
    }
    @Test public void negativeOrEmptyDataStopsWithoutBlindRetryOrFrontendDiagnosis() throws Exception {
        for (int value : new int[]{-1, 0}) {
            Fixture f = new Fixture(true); f.count = value;
            Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
            assertEquals(Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE, result.status); assertEquals(1, f.reads);
            assertEquals(0, result.bytes); assertEquals(1, f.selects); assertEquals(1, f.owner.closes);
        }
    }
    @Test public void rfLossAfterNativeDoesNotPublishTsAndCleanupPreservesPrimaryFailure() throws Exception {
        Fixture f = new Fixture(true); f.owner.failClose = true; f.duringRead = () -> f.rf = false;
        IOException error = assertThrows(IOException.class, () -> f.smoke.run(f.limits()));
        assertTrue(error.getMessage().contains("RF IN")); assertEquals(1, error.getSuppressed().length);
        assertEquals(1, f.reads); assertEquals(1, f.owner.closes);
    }
    @Test public void auditFailureBlocksVendorWriteAndStillCleansUp() throws Exception {
        Fixture f = new Fixture(true); f.failAudit = true;
        assertThrows(IOException.class, () -> f.smoke.run(f.limits())); assertEquals(0, f.selects); assertEquals(1, f.owner.closes);
    }
    @Test public void garbageCannotBecomeSuccessfulTsAndAllLimitsAreFinite() throws Exception {
        Fixture f = new Fixture(true); f.count = 100;
        assertEquals(Tbs5530TsSmoke.Status.INVALID_TS, f.smoke.run(f.limits()).status);
        assertEquals(2, f.reads);
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(564, 1, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(564, 257, 100, 10));
    }
}
