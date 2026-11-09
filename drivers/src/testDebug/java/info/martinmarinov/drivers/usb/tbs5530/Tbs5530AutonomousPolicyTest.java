// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Pure in-memory policy tests. No Android service, real firmware or USB device. */
public class Tbs5530AutonomousPolicyTest {
    static final String OWNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String PLAN = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    static final String ATTEMPT = "00000000-0000-4000-8000-000000000001";
    private static final class Rig implements Tbs5530AutonomousPolicy.Ledger {
        long now = 1000; boolean corrupt, failAppend;
        final List<String> audit = new ArrayList<>();
        final Tbs5530AutonomousPolicy policy;
        Rig() throws IOException { policy = new Tbs5530AutonomousPolicy(() -> now, this); policy.foreground(true); }
        public void check() throws IOException { if (corrupt) throw new IOException("Corrupt ledger"); }
        public void append(String event, String attempt, String plan) throws IOException {
            check(); if (failAppend) throw new IOException("Durability failed"); audit.add(event);
        }
        void grant() throws IOException { policy.grant(Tbs5530AutonomousPolicy.Profile.AUTO_VERIFIED, 301000, now); }
        void operational() throws IOException {
            grant(); policy.observe("ATTACHMENT", 1, true, Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE);
            policy.bindOwner(OWNER, 1);
        }
        void fx2() throws IOException { grant(); policy.observe("ATTACHMENT", 1, true, Tbs5530OperationalProfile.Kind.FX2_DEFAULT); }
        void beat(long delta) throws IOException { now += delta; policy.heartbeat(now); }
    }
    @Test public void challengeAndIntentsAreNotAuthorization() throws Exception {
        Rig r = new Rig(); assertThrows(IOException.class, r.policy::requireSession);
        assertTrue(r.audit.isEmpty()); assertEquals(Tbs5530AutonomousPolicy.State.WAIT_DEVICE, r.policy.state());
    }
    @Test public void standingGrantIsDistinctFromPerProgramSingleUseAttempt() throws Exception {
        Rig r = new Rig(); r.operational(); r.policy.consume(ATTEMPT, PLAN, 10000);
        assertThrows(IOException.class, () -> r.policy.consume(ATTEMPT, PLAN, 10000));
        r.policy.consume("00000000-0000-4000-8000-000000000002", PLAN, 10000);
        assertEquals(3, r.audit.size()); r.policy.requireSession();
        assertThrows(IOException.class, r::grant);
    }
    @Test public void hostOfflineAtExactTwoSecondsIsTerminalWithoutRetry() throws Exception {
        Rig r = new Rig(); r.operational(); r.now += 2000;
        assertThrows(IOException.class, r.policy::requireSession);
        assertThrows(IOException.class, () -> r.policy.heartbeat(r.now));
    }
    @Test public void freshReceivedHeartbeatAfterBoundedNativeGapReplacesOnlyStaleCache() throws Exception {
        Rig r = new Rig(); r.operational();
        r.now += 2500;
        r.policy.heartbeat(r.now - 100);
        r.policy.requireSession(); r.policy.requireOwner(OWNER, 1, true);
        r.now += 1900;
        assertThrows(IOException.class, () -> r.policy.heartbeat(r.now - 2000));
        assertThrows(IOException.class, () -> r.policy.heartbeat(r.now));
    }
    @Test public void freshHeartbeatCannotResurrectStoppedExpiredBackgroundOrCorruptSession() throws Exception {
        for (int fault = 0; fault < 5; fault++) {
            Rig r = new Rig(); r.operational(); r.now += 2500;
            switch (fault) {
                case 0: r.policy.stop(); break;
                case 1: r.policy.foreground(false); r.policy.foreground(true); break;
                case 2: r.corrupt = true; break;
                case 3: r.now = 301000; break;
                default: r.policy.failUncertain();
            }
            assertThrows(IOException.class, () -> r.policy.heartbeat(r.now));
            r.corrupt = false;
            assertThrows(IOException.class, r.policy::requireSession);
        }
    }
    @Test public void fakeClockHeartbeatAndTotalSessionCannotExtend() throws Exception {
        Rig rollback = new Rig(); rollback.operational(); rollback.now--;
        assertThrows(IOException.class, rollback.policy::requireSession);
        Rig future = new Rig(); future.operational(); assertThrows(IOException.class, () -> future.policy.heartbeat(future.now + 1));
        Rig ttl = new Rig(); ttl.grant();
        for (int i = 0; i < 299; i++) ttl.beat(1000);
        ttl.now += 1000; assertThrows(IOException.class, ttl.policy::requireSession);
        Rig longGrant = new Rig(); assertThrows(IOException.class, () -> longGrant.policy.grant(Tbs5530AutonomousPolicy.Profile.AUTO_VERIFIED, 301001, 1000));
    }
    @Test public void permissionGenerationAndOwnerChangesStopBeforeNative() throws Exception {
        for (int fault = 0; fault < 3; fault++) {
            Rig r = new Rig(); r.operational(); final int mode = fault;
            assertThrows(IOException.class, () -> r.policy.requireOwner(mode == 0 ? PLAN : OWNER, mode == 1 ? 2 : 1, mode != 2));
            assertThrows(IOException.class, r.policy::requireSession);
        }
    }
    @Test public void duplicateObservationCannotCreateSecondOwnerOrUpload() throws Exception {
        Rig r = new Rig(); r.operational();
        assertThrows(IOException.class, () -> r.policy.bindOwner(OWNER, 1));
        assertThrows(IOException.class, () -> r.policy.beginUpload(ATTEMPT));
        assertThrows(IOException.class, () -> r.policy.observe("DIFFERENT", 2, true, Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE));
    }
    @Test public void stopBackgroundDetachAndCorruptLedgerAreFailClosed() throws Exception {
        for (int i = 0; i < 4; i++) {
            Rig r = new Rig(); r.operational();
            switch (i) { case 0: r.policy.stop(); break; case 1: r.policy.foreground(false); r.policy.foreground(true); break;
                case 2: r.policy.detach(); break; default: r.corrupt = true; }
            assertThrows(IOException.class, () -> r.policy.consume(ATTEMPT, PLAN, 1000));
        }
    }
    @Test public void durableConsumeFailureSendsNoProgram() throws Exception {
        Rig r = new Rig(); r.operational(); r.failAppend = true;
        assertThrows(IOException.class, () -> r.policy.consume(ATTEMPT, PLAN, 10000)); assertFalse(r.policy.consumed(ATTEMPT));
    }
    @Test public void uploadBudgetPreflightHasNoConsumptionAndStillRechecksAtStart() throws Exception {
        Rig r = new Rig(); r.fx2();
        r.policy.requireUploadStartBudget(); assertEquals(1, r.audit.size());
        r.beat(15001);
        assertThrows(IOException.class, r.policy::requireUploadStartBudget);
        assertThrows(IOException.class, () -> r.policy.beginUpload(ATTEMPT));
        assertEquals(1, r.audit.size());
        assertEquals(Tbs5530AutonomousPolicy.State.FX2_DEFAULT, r.policy.state());
    }
    @Test public void fake132NativeBoundariesThenFreshOwnerNoStaleHandle() throws Exception {
        Rig r = new Rig(); r.fx2(); r.policy.beginUpload(ATTEMPT);
        for (int n = 1; n <= 132; n++) { r.policy.uploadNative(n); r.policy.uploadDispatch(n); }
        r.policy.uploadComplete(132, false);
        assertEquals(Tbs5530AutonomousPolicy.State.WAIT_REENUMERATION, r.policy.state());
        assertThrows(IOException.class, () -> r.policy.requireOwner(OWNER, 1, true));
        // A separate fixture demonstrates the successful fresh observation without reusing the stale check's terminal state.
        Rig fresh = new Rig(); fresh.fx2(); fresh.policy.beginUpload(ATTEMPT);
        for (int n = 1; n <= 132; n++) { fresh.policy.uploadNative(n); fresh.policy.uploadDispatch(n); }
        fresh.policy.uploadComplete(132, false);
        fresh.policy.observe("NEW_OPERATIONAL", 2, true, Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE);
        fresh.policy.bindOwner(OWNER, 2); fresh.policy.requireOwner(OWNER, 2, true);
        assertThrows(IOException.class, () -> fresh.policy.beginUpload(ATTEMPT));
    }
    @Test public void stopAtEveryFirmwareBoundaryNeverPermitsNextWrite() throws Exception {
        for (int stop = 1; stop <= 132; stop++) {
            Rig r = new Rig(); r.fx2(); r.policy.beginUpload(ATTEMPT);
            for (int n = 1; n < stop; n++) { r.policy.uploadNative(n); r.policy.uploadDispatch(n); }
            r.policy.stop(); final int n = stop;
            assertThrows(IOException.class, () -> r.policy.uploadNative(n));
            assertThrows(IOException.class, () -> r.policy.uploadDispatch(n));
        }
    }
    @Test public void partialUnknownAndPendingNativeNeverAllowRetry() throws Exception {
        for (int completed : new int[]{0, 1, 131, 132}) {
            Rig r = new Rig(); r.fx2(); r.policy.beginUpload(ATTEMPT);
            assertThrows(IOException.class, () -> r.policy.uploadComplete(completed, true));
            assertEquals(Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN, r.policy.state());
            assertThrows(IOException.class, () -> r.policy.beginUpload(ATTEMPT));
        }
    }
    @Test public void wrongOrdinalAndPermissionDialogDuringOwnerFailBeforeIo() throws Exception {
        Rig r = new Rig(); r.fx2(); r.policy.beginUpload(ATTEMPT);
        assertThrows(IOException.class, () -> r.policy.uploadDispatch(1));
        assertThrows(IOException.class, () -> r.policy.uploadNative(2));
        Rig owner = new Rig(); owner.operational(); assertThrows(IOException.class, owner.policy::permissionWaiting);
    }
    @Test public void goldenFrontendFullPathIsPinnedAndTsBoundsExact() throws Exception {
        assertEquals(4, Tbs5530AutonomousPlans.dryRun().size()); new Tbs5530AutonomousPlans();
        Tbs5530AutonomousPlans.requireTs(new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false));
        assertThrows(IOException.class, () -> Tbs5530AutonomousPlans.requireTs(new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, true)));
        assertThrows(IOException.class, () -> Tbs5530AutonomousPlans.requireTs(new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 8192)));
        Tbs5530AutonomousPlans.requireTs(new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 8192, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192));
        assertEquals(8192, Tbs5530AutonomousPlans.chunkBytes(Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192));
        assertTrue(Tbs5530AutonomousPlans.nativeUrb(Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192));
        assertFalse(Tbs5530AutonomousPlans.nativeUrb(Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096));
    }
    @Test public void attachmentConsumptionSurvivesNewSessionObjectAndNeverRearms() throws Exception {
        final String[] text = { "" };
        Tbs5530AutonomousAttachmentLedger.Storage storage = new Tbs5530AutonomousAttachmentLedger.Storage() {
            public String read() { return text[0]; }
            public void write(String value) { text[0] = value; }
        };
        new Tbs5530AutonomousAttachmentLedger(storage).consume(OWNER);
        String before = text[0];
        assertThrows(IOException.class, () -> new Tbs5530AutonomousAttachmentLedger(storage).consume(OWNER));
        assertEquals(before, text[0]);
        new Tbs5530AutonomousAttachmentLedger(storage).consume(PLAN);
        assertTrue(text[0].startsWith(before));
    }
    @Test public void corruptTruncatedOrDuplicateAttachmentHistoryBlocksBeforeWrite() throws Exception {
        for (String corrupt : new String[]{"partial", "FIRMWARE_CONSUMED " + OWNER,
                "FIRMWARE_CONSUMED " + OWNER + "\nFIRMWARE_CONSUMED " + OWNER + "\n", "UNKNOWN " + OWNER + "\n"}) {
            Tbs5530AutonomousAttachmentLedger.Storage storage = new Tbs5530AutonomousAttachmentLedger.Storage() {
                public String read() { return corrupt; }
                public void write(String value) { fail("Corrupt history must not be rewritten"); }
            };
            assertThrows(IOException.class, () -> new Tbs5530AutonomousAttachmentLedger(storage).consume(PLAN));
        }
    }
    private Tbs5530BootstrapTelemetry.Snapshot completedBootstrapBoundaries(int count, boolean shortFinal) throws Exception {
        Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry();
        for (int ordinal = 1; ordinal <= count; ordinal++) {
            int length = ordinal >= 3 && ordinal <= 130 ? 64 : 1;
            int value = ordinal >= 3 && ordinal <= 130 ? (ordinal - 3) * 64 : 0xe600;
            telemetry.guardRequested(ordinal); telemetry.guardAccepted(ordinal); telemetry.nativeStarted(ordinal);
            telemetry.nativeReturned(ordinal, value, length, ordinal == 132 && shortFinal ? 0 : length);
        }
        return telemetry.snapshot();
    }
    @Test public void freshHandoffRequiresKnown132CompletionNotOldUncertainTerminal() throws Exception {
        Tbs5530BootstrapTelemetry.Snapshot complete = completedBootstrapBoundaries(132, false);
        assertTrue(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.TRANSITIONING,
                132, "NONE", "NONE", complete));
        assertTrue(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.OPERATIONAL_CANDIDATE,
                132, "NONE", "NONE", complete));
        assertFalse(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.UNCERTAIN,
                132, "JAVA_IO_EXCEPTION", "NONE", complete));
        assertFalse(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.TRANSITIONING,
                132, "NONE", "CLEANUP_FAILED", complete));
        assertFalse(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.TRANSITIONING,
                132, "AUDIT_FAILED", "NONE", complete));
        assertFalse(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.TRANSITIONING,
                131, "NONE", "NONE", completedBootstrapBoundaries(131, false)));
        assertFalse(Tbs5530AutonomousPolicy.bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State.TRANSITIONING,
                132, "NONE", "NONE", completedBootstrapBoundaries(132, true)));
    }
    @Test public void completedSynchronousErrnoIsNotAnUnknownNativeReturn() {
        assertTrue(Tbs5530AutonomousPolicy.nativeReturnKnown(true, 1, -1, 75));
        assertTrue(Tbs5530AutonomousPolicy.completedReceiveError(true, 1, -1, 75));
        assertFalse(Tbs5530AutonomousPolicy.completedReceiveError(false, 1, -1, 75));
        assertFalse(Tbs5530AutonomousPolicy.completedReceiveError(true, 1, -1, -1));
        assertFalse(Tbs5530AutonomousPolicy.completedReceiveError(true, 0, -1, 75));
        assertFalse(Tbs5530AutonomousPolicy.completedReceiveError(true, 1, Integer.MIN_VALUE, 75));
        assertFalse(Tbs5530AutonomousPolicy.completedReceiveError(true, 1, 4096, 0));
    }
    @Test public void knownReceiveErrorCanReportButUnknownCleanupStillFailsClosed() throws Exception {
        Rig r = new Rig(); r.operational();
        r.policy.transition(Tbs5530AutonomousPolicy.State.FRONTEND_INIT);
        r.policy.transition(Tbs5530AutonomousPolicy.State.LOCK_CONFIRMED);
        r.policy.transition(Tbs5530AutonomousPolicy.State.TS_CAPTURE);
        assertTrue(Tbs5530AutonomousPolicy.completedReceiveError(true, 1, -1, 75));
        r.policy.transition(Tbs5530AutonomousPolicy.State.REPORT);
        r.policy.stop(); assertEquals(Tbs5530AutonomousPolicy.State.REPORT, r.policy.state());
        assertThrows(IOException.class, r.policy::requireSession);
        r.policy.failUncertain(); assertEquals(Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN, r.policy.state());
        assertThrows(IOException.class, () -> r.policy.heartbeat(r.now));
    }
    @Test public void finiteFrontendLockCaptureReportPath() throws Exception {
        Rig r = new Rig(); r.operational();
        assertThrows(IOException.class, () -> r.policy.transition(Tbs5530AutonomousPolicy.State.TS_CAPTURE));
        r.policy.transition(Tbs5530AutonomousPolicy.State.FRONTEND_INIT);
        r.policy.transition(Tbs5530AutonomousPolicy.State.LOCK_CONFIRMED);
        r.policy.transition(Tbs5530AutonomousPolicy.State.TS_CAPTURE);
        r.policy.transition(Tbs5530AutonomousPolicy.State.REPORT);
        assertEquals(Tbs5530AutonomousPolicy.State.REPORT, r.policy.state());
    }
}
