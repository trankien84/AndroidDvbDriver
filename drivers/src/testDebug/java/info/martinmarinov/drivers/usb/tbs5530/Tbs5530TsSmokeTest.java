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
        int consumes, selects, reads, count = 188 * 3, audits;
        boolean rf = true, consent = true, failAudit;
        long nanos;
        Runnable duringRead;
        Fixture(boolean locked) throws Exception {
            token = session.requestPermission(); session.permissionResult(token, true); session.connected(token, owner);
            session.interfaceClaimed(token); session.ready(token); session.frontendInitialized(token); session.frontendLock(token, locked);
            Tbs5530Bridge bridge = new Tbs5530Bridge((type, request, value, index, bytes, offset, length, timeout) -> {
                assertEquals(0x40, type); assertEquals(0x8a, request); assertEquals(2, length);
                assertArrayEquals(new byte[]{10, 0}, bytes); selects++; return length;
            }, session, token);
            Tbs5530BulkTransport bulk = new Tbs5530BulkTransport(session, token, owner, () -> { }, (bytes, length, timeout) -> {
                assertTrue(timeout > 0 && timeout <= 2000); reads++;
                for (int at = 0, packet = 0; at + 188 <= length && packet < 3; at += 188, packet++) {
                    bytes[at] = 0x47; bytes[at+1] = 0; bytes[at+2] = 0x20; bytes[at+3] = (byte)(0x10 | packet);
                }
                if (duringRead != null) duringRead.run(); nanos += 100_000_000L; return count;
            });
            smoke = new Tbs5530TsSmoke(session, token, bridge, bulk, this, () -> nanos);
        }
        public void consume(Tbs5530TsSmoke.Limits limits, long generation) throws IOException {
            requireCurrent(generation); if (consumes++ != 0) throw new IOException("consumed");
        }
        public void requireCurrent(long generation) throws IOException {
            if (!consent || !rf || generation != token) throw new IOException("RF IN/consent no longer confirmed");
        }
        public void audit(String event, int ordinal, int count) throws IOException {
            audits++; if (failAudit) throw new IOException("audit");
        }
        Tbs5530TsSmoke.Limits limits() { return new Tbs5530TsSmoke.Limits(564, 2, 1000, 500); }
    }
    @Test public void confirmedLockRfAndApprovalAllowExactlyOneStreamSelectThenBoundedBulk() throws Exception {
        Fixture f = new Fixture(true); Tbs5530TsSmoke.Result result = f.smoke.run(f.limits());
        assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, result.status);
        assertEquals(564, result.bytes); assertEquals(3, result.packets.packets); assertEquals(1, f.consumes);
        assertEquals(1, f.selects); assertEquals(1, f.reads); assertEquals(1, f.owner.releases); assertEquals(1, f.owner.closes);
        assertEquals(5640.0, result.bytesPerSecond(), 0.001);
        assertThrows(IOException.class, () -> f.smoke.run(f.limits())); assertEquals(1, f.selects);
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
