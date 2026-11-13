// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Kind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Operation;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Plan;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.RegisterTransaction;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.State;

public class Tbs5530FrontendTest {
    private static final int X = 0x66, T = 0x64, U = 0x60;
    private static final int[] WIDTHS = {1_700_000, 5_000_000, 6_000_000, 7_000_000, 8_000_000};

    private static final class TestClock implements Tbs5530Frontend.Clock {
        long now;
        boolean stalled;
        boolean interrupted;
        Runnable afterDelay;
        final List<Long> delays = new ArrayList<>();
        @Override public long elapsedRealtimeMillis() { return now; }
        @Override public void delayMillis(long millis) throws InterruptedException {
            if (interrupted) throw new InterruptedException("fixture");
            delays.add(millis);
            if (!stalled) now += millis;
            if (afterDelay != null) afterDelay.run();
        }
    }
    private static final class FaultIo implements Tbs5530ControlIo {
        final ScriptedFakeControlIo script;
        int calls;
        int failAt = Integer.MAX_VALUE;
        int shortBy = 1;
        IOException error;
        Runnable afterCall;
        FaultIo(ScriptedFakeControlIo script) { this.script = script; }
        @Override public int controlTransfer(int type, int request, int value, int index,
                byte[] buffer, int offset, int length, int timeout) throws IOException {
            int result = script.controlTransfer(type, request, value, index, buffer, offset, length, timeout);
            calls++;
            if (afterCall != null) afterCall.run();
            if (calls == failAt) {
                if (error != null) throw error;
                return result - shortBy;
            }
            return result;
        }
    }
    private static final class Rig {
        final ScriptedFakeControlIo script = new ScriptedFakeControlIo();
        final FaultIo io = new FaultIo(script);
        final Tbs5530Session session = Tbs5530BridgeTest.connectedSession();
        final TestClock clock = new TestClock();
        final Tbs5530Frontend frontend = new Tbs5530Frontend(
                new Tbs5530Bridge(io, session, session.getGeneration()), session, session.getGeneration(), clock);
        Rig() throws TransferException {}
        void probe(int id) throws Exception {
            probeFixture(script, id);
            frontend.execute(frontend.planProbe());
        }
        void initialize() throws Exception {
            probe(0x197);
            initFixture(script);
            frontend.execute(frontend.planInitialize());
            frontend.readyForTune();
        }
        void tune(Tbs5530DvbT2TuneParameters p, boolean active) throws Exception {
            tuneFixture(script, p, active);
            frontend.execute(frontend.planTune(p));
        }
    }
    private static Tbs5530DvbT2TuneParameters parameters(int width, int plp) {
        return new Tbs5530DvbT2TuneParameters(666_000_000L, width, plp);
    }

    @Test public void probeIsExactSlvXBankZeroFbFdNotSlvTOrUsbReadiness() throws Exception {
        for (int id : new int[]{0x197, 0x19c, 0x39a}) {
            Rig r = new Rig();
            Plan p = r.frontend.planProbe();
            assertEquals(Operation.PROBE, p.operation);
            assertEquals(3, p.usbOutCount);
            assertEquals(2, p.usbInCount);
            assertEquals(10_000, p.maximumMillis);
            assertEquals(0, r.io.calls);
            assertEquals(State.BRIDGE_VERIFIED, r.frontend.getState());
            probeFixture(r.script, id);
            Tbs5530Frontend.Result result = r.frontend.execute(p);
            assertEquals(id, result.chipId);
            assertEquals(State.DEMOD_IDENTIFIED, result.state);
            assertEquals(Tbs5530Session.State.CONNECTED, r.session.getState());
            assertThrows(IllegalStateException.class, r.frontend::readyForTune);
            assertThrows(IllegalStateException.class, () -> r.frontend.planTune(parameters(8_000_000, 0)));
            r.script.assertComplete();
        }
    }

    @Test public void junkFullLengthIdsFailClosedWithoutAssumingFreiaOrInit() throws Exception {
        for (int id : new int[]{0, 0xff, 0x3ff, 0x091, 0x396, 0x297, 0x196, 0x198}) {
            Rig r = new Rig();
            probeFixture(r.script, id);
            assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planProbe()));
            assertEquals(State.UNCERTAIN, r.frontend.getState());
            assertThrows(TransferException.class, r.frontend::planInitialize);
            assertEquals(5, r.io.calls);
            r.script.assertComplete();
        }
        Rig fullFF = new Rig();
        w(fullFF.script, X, 0, 0); rd(fullFF.script, X, 0xfb, 255); rd(fullFF.script, X, 0xfd, 255);
        assertThrows(TransferException.class, () -> fullFF.frontend.execute(fullFF.frontend.planProbe()));
        fullFF.script.assertComplete();
    }

    @Test public void idMasksOnlySourceSpecifiedHighBits() throws Exception {
        Rig r = new Rig();
        w(r.script, X, 0, 0); rd(r.script, X, 0xfb, 0xfd); rd(r.script, X, 0xfd, 0x97);
        assertEquals(0x197, r.frontend.execute(r.frontend.planProbe()).chipId);
        r.script.assertComplete();
    }

    @Test public void freiaIdsAreRecognizedButNeverReceiveAscotInitWrites() throws Exception {
        for (int id : new int[]{0x19c, 0x39a}) {
            Rig r = new Rig(); r.probe(id);
            assertThrows(UnsupportedOperationException.class, r.frontend::planInitialize);
            assertEquals(5, r.io.calls);
            assertEquals(State.DEMOD_IDENTIFIED, r.frontend.getState());
            r.script.assertComplete();
        }
    }

    @Test public void completeInitGoldenVectorIncludes24MHzResetRepeaterAndAscot3iCalibration() throws Exception {
        Rig r = new Rig(); r.probe(0x197);
        Plan p = r.frontend.planInitialize();
        assertEquals(84, p.delayMillis);
        assertEquals(65, p.usbOutCount); assertEquals(14, p.usbInCount);
        assertEquals(158_084, p.maximumMillis);
        assertFalse(p.countsAreUpperBounds);
        int first = r.io.calls;
        initFixture(r.script);
        Tbs5530Frontend.Result result = r.frontend.execute(p);
        assertEquals(State.FRONTEND_INITIALIZED, result.state);
        assertEquals(84, r.clock.now);
        assertPlanCounts(p, result, r.script, first);
        assertEquals(Tbs5530Session.State.CONNECTED, r.session.getState());
        assertThrows(IllegalStateException.class, r.frontend::planStatus);
        assertThrows(IllegalStateException.class, () -> r.frontend.planTune(parameters(8_000_000, 0)));
        r.frontend.readyForTune();
        assertEquals(State.READY_FOR_TUNE, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void allReviewedBandwidthsUseIndependentSourceGoldenTuneVectors() throws Exception {
        for (int width : WIDTHS) {
            Rig r = new Rig(); r.initialize();
            Tbs5530DvbT2TuneParameters p = parameters(width, 255);
            Plan plan = r.frontend.planTune(p);
            assertEquals(width == 1_700_000 ? 84 : 85, plan.usbOutCount);
            assertEquals(15, plan.usbInCount);
            assertEquals(width == 1_700_000 ? 198_120 : 200_120, plan.maximumMillis);
            int first = r.io.calls;
            long before = r.clock.now;
            tuneFixture(r.script, p, false);
            Tbs5530Frontend.Result result = r.frontend.execute(plan);
            assertEquals(120, r.clock.now - before);
            assertEquals(120, plan.delayMillis);
            assertPlanCounts(plan, result, r.script, first);
            assertEquals(State.READY_FOR_TUNE, result.state);
            assertNull(result.status);
            r.script.assertComplete();
        }
    }

    @Test public void sameSystemRetuneRewritesPlpAndBandwidthAndTunerEveryTime() throws Exception {
        Rig r = new Rig(); r.initialize();
        r.tune(parameters(8_000_000, 0), false);
        int first = r.io.calls;
        r.tune(parameters(6_000_000, 201), true);
        List<ScriptedFakeControlIo.Call> calls = r.script.calls();
        assertArrayEquals(bytes(3, 0xc8, 0, 0x23), calls.get(first).before);
        assertArrayEquals(bytes(3, 0xc8, 0xaf, 201), calls.get(first + 1).before);
        assertArrayEquals(bytes(3, 0xc8, 0xad, 1), calls.get(first + 2).before);
        r.tune(parameters(6_000_000, 0), true);
        r.script.assertComplete();
    }

    @Test public void validationRejectsImplicitBandwidthAutoPlpAndFrequencyRoundingBeforeAnyIo() throws Exception {
        Rig r = new Rig();
        for (long hz : new long[]{Long.MIN_VALUE, -1, 0, 173_999_000, 868_001_000, 666_000_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530DvbT2TuneParameters(hz, 8_000_000, 0));
        }
        for (int width : new int[]{Integer.MIN_VALUE, -1, 0, 1, 7, 1_000_000, 6_500_000, 10_000_000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> parameters(width, 0));
        }
        for (int plp : new int[]{Integer.MIN_VALUE, -2, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> parameters(8_000_000, plp));
        }
        assertThrows(UnsupportedOperationException.class, () -> parameters(8_000_000, Tbs5530DvbT2TuneParameters.AUTO_PLP));
        assertThrows(IllegalArgumentException.class, () -> r.frontend.planTune(null));
        assertThrows(IllegalStateException.class, r.frontend::planInitialize);
        assertEquals(174_000_000L, new Tbs5530DvbT2TuneParameters(174_000_000, 8_000_000, 0).frequencyHz);
        assertEquals(868_000_000L, new Tbs5530DvbT2TuneParameters(868_000_000, 1_700_000, 255).frequencyHz);
        assertEquals(0, r.io.calls);
    }

    @Test public void plansAreImmutableVersionedHashedEpochBoundAndSingleUse() throws Exception {
        Rig r = new Rig();
        Plan p = r.frontend.planProbe();
        String transcript = p.getApprovalTranscript(), hash = p.getSha256();
        assertTrue(transcript.startsWith("TBS5530-CXD2878-PLAN/v1\nPROBE|1|0|3|2|0|10000|false\n"));
        assertEquals(64, hash.length());
        assertEquals(hash, r.frontend.planProbe().getSha256());
        assertThrows(UnsupportedOperationException.class, () -> p.getTransactions().clear());
        p.getTransactions().get(0).getData()[0] = 99;
        assertArrayEquals(bytes(0), p.getTransactions().get(0).getData());
        assertEquals(transcript, p.getApprovalTranscript());
        Rig other = new Rig();
        assertThrows(IllegalArgumentException.class, () -> other.frontend.execute(p));
        assertEquals(0, other.io.calls);
        probeFixture(r.script, 0x197);
        r.frontend.execute(p);
        assertThrows(IllegalArgumentException.class, () -> r.frontend.execute(p));
        assertEquals(5, r.io.calls);
        initFixture(r.script);
        r.frontend.execute(r.frontend.planInitialize()); r.frontend.readyForTune();
        assertNotEquals(r.frontend.planTune(parameters(8_000_000, 0)).getSha256(),
                r.frontend.planTune(parameters(8_000_000, 255)).getSha256());
        assertNotEquals(r.frontend.planTune(parameters(8_000_000, 0)).getSha256(),
                r.frontend.planTune(parameters(7_000_000, 0)).getSha256());
        r.script.assertComplete();
    }

    @Test public void derivedWritesExposeOnlyExactReviewedReadFormulae() throws Exception {
        Rig r = new Rig(); r.probe(0x197);
        int derived = 0;
        for (RegisterTransaction step : r.frontend.planInitialize().getTransactions()) {
            assertNotNull(step.provenance);
            assertTrue(step.provenance.contains("2ce787de9f6e81b4294692a45de15596ae2e1322"));
            if (step.isReadDerivedWrite()) {
                derived++;
                assertNull(step.getData());
                for (int value = 0; value < 256; value++) {
                    byte[] expected = bytes(((value & step.sourceMask) >>> step.shift) | step.value);
                    assertArrayEquals(expected, step.resolveWriteData(value));
                    byte[] payload = step.usbWritePayload(value);
                    assertEquals(3, payload[0]);
                    assertEquals(step.address << 1, payload[1] & 255);
                    assertEquals(step.register, payload[2] & 255);
                    assertEquals(expected[0], payload[3]);
                }
                assertThrows(IllegalArgumentException.class, () -> step.resolveWriteData(256));
            }
        }
        assertTrue(derived > 0);
    }

    @Test public void shortTransferAtEveryProbeInitAndTuneUsbCallStopsWithoutRetryOrCleanupWrite() throws Exception {
        for (int operation = 0; operation < 3; operation++) {
            Rig sample = new Rig();
            Plan template;
            if (operation == 0) template = sample.frontend.planProbe();
            else if (operation == 1) { sample.probe(0x197); template = sample.frontend.planInitialize(); }
            else { sample.initialize(); template = sample.frontend.planTune(parameters(8_000_000, 0)); }
            int count = template.usbOutCount + template.usbInCount;
            for (int at = 1; at <= count; at++) {
                Rig r = new Rig(); Plan p;
                if (operation == 0) { probeFixture(r.script, 0x197); p = r.frontend.planProbe(); }
                else if (operation == 1) { r.probe(0x197); initFixture(r.script); p = r.frontend.planInitialize(); }
                else { r.initialize(); tuneFixture(r.script, parameters(8_000_000, 0), false); p = r.frontend.planTune(parameters(8_000_000, 0)); }
                int before = r.io.calls;
                r.io.failAt = before + at;
                TransferException failure = assertThrows(TransferException.class, () -> r.frontend.execute(p));
                assertEquals(FailureKind.SHORT_TRANSFER, failure.getKind());
                assertEquals(before + at, r.io.calls);
                assertEquals(State.UNCERTAIN, r.frontend.getState());
                assertThrows(TransferException.class, r.frontend::planProbe);
                assertThrows(IllegalArgumentException.class, () -> r.frontend.execute(p));
                assertEquals(before + at, r.io.calls);
            }
        }
    }

    @Test public void timeoutAndIoFailuresKeepCausesAndDoNotAttemptRepeaterRecovery() throws Exception {
        for (IOException cause : new IOException[]{new SocketTimeoutException("fixture timeout"), new IOException("fixture IO")}) {
            Rig r = new Rig(); r.probe(0x197); initFixture(r.script);
            r.io.failAt = r.io.calls + 2;
            r.io.error = cause;
            TransferException error = assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planInitialize()));
            assertEquals(cause instanceof SocketTimeoutException ? FailureKind.TIMEOUT : FailureKind.IO_FAILURE, error.getKind());
            assertSame(cause, error.getCause());
            assertEquals(7, r.io.calls);
            assertEquals(State.UNCERTAIN, r.frontend.getState());
        }
    }

    @Test public void unsupportedAscotVariantAndBootStatusFailBeforeFalseInitialization() throws Exception {
        for (int revision : new int[]{0, 0xc0, 0xd0, 0xf0}) {
            Rig r = new Rig(); r.probe(0x197);
            initPrefixFixture(r.script, 0x197, revision);
            assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planInitialize()));
            assertEquals(State.UNCERTAIN, r.frontend.getState());
            r.script.assertComplete();
        }
        Rig r = new Rig(); r.probe(0x197);
        initPrefixFixture(r.script, 0x197, 0xe0);
        tunerBootWrites(r.script);
        rd(r.script, U, 0x1a, 1);
        assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planInitialize()));
        assertEquals(State.UNCERTAIN, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void postResetIdentityChangeStopsBeforeTunerAccess() throws Exception {
        Rig r = new Rig(); r.probe(0x197);
        resetFixture(r.script, 0x19c);
        assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planInitialize()));
        assertEquals(State.UNCERTAIN, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void cancellationBeforeIoAndDuringDelayIsBoundedAndNeverRestarts() throws Exception {
        Rig before = new Rig(); Plan p = before.frontend.planProbe(); before.frontend.cancel();
        assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class, () -> before.frontend.execute(p)).getKind());
        assertEquals(0, before.io.calls);
        Rig during = new Rig(); during.probe(0x197); initFixture(during.script);
        during.clock.afterDelay = during.frontend::cancel;
        assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class,
                () -> during.frontend.execute(during.frontend.planInitialize())).getKind());
        assertEquals(7, during.io.calls);
        assertEquals(4, during.clock.now);
        assertEquals(State.UNCERTAIN, during.frontend.getState());
        assertThrows(TransferException.class, during.frontend::planInitialize);
    }

    @Test public void threadInterruptionAndStalledDelayFailClosed() throws Exception {
        Rig r = new Rig(); r.probe(0x197); initFixture(r.script); r.clock.interrupted = true;
        try {
            assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class,
                    () -> r.frontend.execute(r.frontend.planInitialize())).getKind());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        Rig stalled = new Rig(); stalled.probe(0x197); initFixture(stalled.script); stalled.clock.stalled = true;
        assertThrows(TransferException.class, () -> stalled.frontend.execute(stalled.frontend.planInitialize()));
        assertEquals(State.UNCERTAIN, stalled.frontend.getState());
        assertEquals(7, stalled.io.calls);
    }

    @Test public void detachBetweenPointerAndInNeverPublishesIdOrRunsAnotherTransfer() throws Exception {
        Rig r = new Rig(); probeFixture(r.script, 0x197);
        r.io.afterCall = () -> { if (r.io.calls == 2) r.session.requestCancellation(r.session.getGeneration(), true); };
        assertEquals(FailureKind.SESSION_UNAVAILABLE, assertThrows(TransferException.class,
                () -> r.frontend.execute(r.frontend.planProbe())).getKind());
        assertEquals(2, r.io.calls);
        assertEquals(-1, r.frontend.getChipId());
        assertEquals(State.UNCERTAIN, r.frontend.getState());
    }

    @Test public void oldGenerationAndExternalBankChangesNeverReuseBankCache() throws Exception {
        Rig r = new Rig(); Plan old = r.frontend.planProbe();
        r.session.close(); long newGeneration = r.session.requestPermission();
        assertTrue(r.session.permissionResult(newGeneration, true));
        assertTrue(r.session.connected(newGeneration, new Tbs5530Session.Resources() {
            public void cancel() {} public void release() {} public void close() {}
        }));
        assertThrows(TransferException.class, () -> r.frontend.execute(old));
        assertEquals(0, r.io.calls);
        Tbs5530Frontend fresh = new Tbs5530Frontend(new Tbs5530Bridge(r.io, r.session, newGeneration), r.session, newGeneration, r.clock);
        probeFixture(r.script, 0x197);
        fresh.execute(fresh.planProbe());
        assertEquals(State.DEMOD_IDENTIFIED, fresh.getState());
        r.script.assertComplete();

        Rig ready = new Rig(); ready.initialize(); ready.tune(parameters(8_000_000, 0), false);
        w(ready.script, T, 0, 0x13);
        new Tbs5530Bridge(ready.io, ready.session, ready.session.getGeneration()).i2cWrite(T, 0, bytes(0, 0x13), 0, 2);
        statusFixture(ready.script, 0x26);
        assertTrue(ready.frontend.execute(ready.frontend.planStatus()).status.locked);
        ready.script.assertComplete();
    }

    @Test public void explicitPlpModesNeverEncodeAutoAsFfOrSilentlyEnableMonitor() throws Exception {
        Rig r = new Rig(); r.initialize();
        for (int id : new int[]{0, 1, 42, 254, 255}) {
            Tbs5530DvbT2TuneParameters p = new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000,
                    Tbs5530DvbT2TuneParameters.Selection.manual(id));
            assertEquals(id, p.plpId); assertEquals(Integer.valueOf(id), p.selection.id);
            assertEquals(Tbs5530DvbT2TuneParameters.Mode.MANUAL, p.selection.mode);
        }
        for (int id : new int[]{-1, 256}) assertThrows(IllegalArgumentException.class,
                () -> Tbs5530DvbT2TuneParameters.Selection.manual(id));
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, null));
        Tbs5530DvbT2TuneParameters auto = autoParameters();
        assertNull(auto.selection.id);
        assertThrows(UnsupportedOperationException.class, () -> r.frontend.planTune(auto));
        assertThrows(UnsupportedOperationException.class, () -> r.frontend.planTune(new Tbs5530DvbT2TuneParameters(
                594_000_000L, 8_000_000, Tbs5530DvbT2TuneParameters.Selection.monitorResolved(42))));
        for (int width : new int[]{7_610_000, 7_710_000, 7_770_000}) assertThrows(IllegalArgumentException.class,
                () -> new Tbs5530DvbT2TuneParameters(594_000_000L, width, 42));
        assertThrows(UnsupportedOperationException.class, () -> r.frontend.planTune(new Tbs5530DvbT2TuneParameters(
                602_000_000L, 8_000_000, Tbs5530DvbT2TuneParameters.Selection.automatic()), true));
        assertEquals(84, r.io.calls);
    }

    private static Tbs5530DvbT2TuneParameters autoParameters() {
        return new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, Tbs5530DvbT2TuneParameters.Selection.automatic());
    }

    @Test public void experimentalAutoGoldenDiffIsOnlyAfOmissionAndAdZero() throws Exception {
        Rig r = new Rig(); r.initialize();
        Plan manual = r.frontend.planTune(new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, 42));
        Plan auto = r.frontend.planTune(autoParameters(), true);
        assertEquals(84, auto.usbOutCount); assertEquals(15, auto.usbInCount);
        assertEquals(198_120, auto.maximumMillis); assertEquals(120, auto.delayMillis);
        assertNotEquals(manual.getSha256(), auto.getSha256());
        assertTrue(auto.getApprovalTranscript().contains("mode=AUTO|id=NOT_MEASURED"));
        assertTrue(auto.getApprovalTranscript().contains("CROSS-CHIP-ANALOGY:torvalds/linux@adc218"));
        List<RegisterTransaction> a = auto.getTransactions(), m = manual.getTransactions();
        assertEquals(m.size() - 1, a.size());
        assertArrayEquals(bytes(3, 0xc8, 0, 0x23), a.get(0).usbWritePayload(0));
        assertArrayEquals(bytes(3, 0xc8, 0xad, 0), a.get(1).usbWritePayload(0));
        for (RegisterTransaction step : a) assertFalse(step.address == T && step.bank == 0x23 && step.register == 0xaf);
        for (int i = 2; i < a.size(); i++) {
            RegisterTransaction left = a.get(i), right = m.get(i + 1);
            assertEquals(right.kind, left.kind); assertEquals(right.address, left.address);
            assertEquals(right.bank, left.bank); assertEquals(right.register, left.register);
            assertEquals(right.readLength, left.readLength); assertEquals(right.delayMillis, left.delayMillis);
            assertArrayEquals(right.getData(), left.getData());
            assertEquals(right.sourceStep < 0 ? -1 : right.sourceStep - 1, left.sourceStep);
        }
        tuneFixture(r.script, autoParameters(), false); r.frontend.execute(auto); r.script.assertComplete();
    }

    @Test public void k25AutoRemainsExplicitAndUsesReviewedFrequencyOnly() throws Exception {
        Rig r = new Rig(); r.initialize();
        Tbs5530DvbT2TuneParameters k25 = new Tbs5530DvbT2TuneParameters(506_000_000L, 8_000_000,
                Tbs5530DvbT2TuneParameters.Selection.automatic());
        assertThrows(UnsupportedOperationException.class, () -> r.frontend.planTune(k25));
        assertThrows(UnsupportedOperationException.class, () -> r.frontend.planTune(
                new Tbs5530DvbT2TuneParameters(506_000_000L, 7_000_000,
                        Tbs5530DvbT2TuneParameters.Selection.automatic()), true));
        tuneFixture(r.script, k25, false);
        r.frontend.execute(r.frontend.planTune(k25, true)); r.script.assertComplete();
    }

    @Test public void autoToManualRetuneWritesTheNewIdAndAdOneEveryTime() throws Exception {
        Rig r = new Rig(); r.initialize();
        tuneFixture(r.script, autoParameters(), false); r.frontend.execute(r.frontend.planTune(autoParameters(), true));
        r.tune(new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, 255), true);
        r.tune(new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, 42), true);
        r.script.assertComplete();
    }

    @Test public void qcvnSyntheticTopologiesAreNotOnAirPresetsOrCommonPlpDiscovery() throws Exception {
        final String provenance = "SYNTHETIC_TEST_FIXTURE";
        int[][] data = {{42}, {1, 2}, {17, 254}, {2}, {42, 17}};
        Integer[] common = {null, 0, null, 0, null};
        for (int scenario = 0; scenario < data.length; scenario++) {
            assertEquals("SYNTHETIC_TEST_FIXTURE", provenance);
            Rig r = new Rig(); r.initialize(); boolean active = false;
            for (int id : data[scenario]) {
                assertNotEquals(common[scenario], Integer.valueOf(id));
                Tbs5530DvbT2TuneParameters p = new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, id);
                assertTrue(p.selection.transcript().contains("NOT_RF_MEASURED"));
                r.tune(p, active); active = true;
            }
            r.script.assertComplete();
        }
    }

    @Test public void statusProgressionHasImmutableEpochAndMonotonicSamples() throws Exception {
        Rig r = new Rig(); r.initialize(); r.tune(parameters(8_000_000, 42), false);
        statusFixture(r.script, 1); statusFixture(r.script, 5); statusFixture(r.script, 0x26);
        Tbs5530Frontend.Result result = r.frontend.execute(r.frontend.planPollStatus(20));
        assertEquals(3, result.statusSamples.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(r.session.getGeneration(), result.statusSamples.get(i).generation);
            assertEquals(204 + 1000L * i, result.statusSamples.get(i).elapsedRealtimeMillis);
        }
        assertEquals(5, result.statusSamples.get(1).status.syncState);
        assertTrue(result.statusSamples.get(2).status.locked);
        assertThrows(UnsupportedOperationException.class, () -> result.statusSamples.clear());
        r.script.assertComplete();
    }

    @Test public void reservedSyncSevenCannotAuthorizeTsEvenWithTsBitSet() throws Exception {
        Rig r = new Rig(); r.initialize(); r.tune(parameters(8_000_000, 42), false);
        statusFixture(r.script, 0x27);
        assertFalse(r.frontend.execute(r.frontend.planStatus()).status.locked);
        assertEquals(State.READY_FOR_TUNE, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void statusUsesSourceBitSemanticsAndNeverInventsMetrics() throws Exception {
        Rig r = new Rig(); r.initialize(); r.tune(parameters(8_000_000, 0), false);
        for (int raw = 0; raw < 256; raw++) {
            statusFixture(r.script, raw);
            Tbs5530Frontend.Result result = r.frontend.execute(r.frontend.planStatus());
            boolean expected = (raw & 7) == 6 && (raw & 0x10) == 0;
            assertEquals(expected, result.status.locked);
            assertEquals(expected, result.status.demodLocked);
            assertEquals((raw & 0x20) != 0, result.status.tsLock);
            assertEquals(expected ? State.DVBT2_LOCKED : State.READY_FOR_TUNE, result.state);
            assertEquals(Tbs5530Frontend.Metric.NOT_AVAILABLE, result.status.signalStrength);
            assertEquals(Tbs5530Frontend.Metric.NOT_AVAILABLE, result.status.cnr);
            assertEquals(Tbs5530Frontend.Metric.NOT_AVAILABLE, result.status.bitErrorRate);
            assertEquals(Tbs5530Frontend.Metric.NOT_AVAILABLE, result.status.packetErrorRate);
        }
        r.script.assertComplete();
    }

    @Test public void pollingHasFiniteExactUpperBoundSourceIntervalAndEarlyStops() throws Exception {
        for (int terminal : new int[]{0, 0x06, 0x26, 0x10}) {
            Rig r = new Rig(); r.initialize(); r.tune(parameters(8_000_000, 0), false);
            int samples = terminal == 0 ? 20 : 2;
            for (int i = 0; i < samples; i++) statusFixture(r.script, i == samples - 1 ? terminal : 0);
            Plan p = r.frontend.planPollStatus(20);
            assertTrue(p.countsAreUpperBounds);
            assertEquals(40, p.usbOutCount); assertEquals(20, p.usbInCount);
            assertEquals(19_000, p.delayMillis);
            assertEquals(139_000, p.maximumMillis);
            long before = r.clock.now;
            Tbs5530Frontend.Result result = r.frontend.execute(p);
            assertEquals((samples - 1) * 1000L, r.clock.now - before);
            assertEquals(samples * 2, result.usbOutCompleted);
            assertEquals(samples, result.usbInCompleted);
            assertEquals(terminal == 0x06 || terminal == 0x26, result.status.locked);
            assertThrows(IllegalArgumentException.class, () -> r.frontend.planPollStatus(0));
            assertThrows(IllegalArgumentException.class, () -> r.frontend.planPollStatus(21));
            r.script.assertComplete();
        }
    }

    @Test public void cancellationDuringReadFinishesAtMostItsAtomicPairAndNeverPublishesId() throws Exception {
        Rig r = new Rig(); probeFixture(r.script, 0x197);
        r.io.afterCall = () -> { if (r.io.calls == 2) r.frontend.cancel(); };
        assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class,
                () -> r.frontend.execute(r.frontend.planProbe())).getKind());
        assertEquals(3, r.io.calls);
        assertEquals(-1, r.frontend.getChipId());
        assertEquals(State.UNCERTAIN, r.frontend.getState());
    }

    @Test public void cancellationAtLastIoDoesNotPublishSuccessfulInitializationOrLock() throws Exception {
        Rig r = new Rig(); r.probe(0x197); initFixture(r.script);
        Plan p = r.frontend.planInitialize();
        int last = r.io.calls + p.usbOutCount + p.usbInCount;
        r.io.afterCall = () -> { if (r.io.calls == last) r.frontend.cancel(); };
        assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class,
                () -> r.frontend.execute(p)).getKind());
        assertEquals(State.UNCERTAIN, r.frontend.getState());
        assertThrows(TransferException.class, r.frontend::readyForTune);
        r.script.assertComplete();
    }

    @Test public void cancellationDuringLongPollDelayChecksEveryTenMillis() throws Exception {
        Rig r = new Rig(); r.initialize(); r.tune(parameters(8_000_000, 0), false);
        statusFixture(r.script, 0);
        long before = r.clock.now;
        r.clock.afterDelay = r.frontend::cancel;
        assertEquals(FailureKind.CANCELLED, assertThrows(TransferException.class,
                () -> r.frontend.execute(r.frontend.planPollStatus(20))).getKind());
        assertEquals(10, r.clock.now - before);
        assertEquals(State.UNCERTAIN, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void regressedOrOverrunClockFailsWithoutContinuingNativeCalls() throws Exception {
        for (boolean backwards : new boolean[]{true, false}) {
            Rig r = new Rig(); probeFixture(r.script, 0x197);
            r.clock.now = 100;
            r.io.afterCall = () -> { if (r.io.calls == 1) r.clock.now = backwards ? 99 : 10_101; };
            assertEquals(FailureKind.TIMEOUT, assertThrows(TransferException.class,
                    () -> r.frontend.execute(r.frontend.planProbe())).getKind());
            assertEquals(1, r.io.calls);
            assertEquals(State.UNCERTAIN, r.frontend.getState());
        }
    }

    @Test public void unportedSerialModeFailsClosedBeforeAnyDemodOrTunerActivation() throws Exception {
        Rig r = new Rig(); r.initialize();
        w(r.script, T, 0, 0x23); w(r.script, T, 0xaf, 0); w(r.script, T, 0xad, 1);
        w(r.script, T, 0, 0x2e); w(r.script, T, 0x10, 0); w(r.script, T, 0, 0x2b); w(r.script, T, 0x9d, 0x2e);
        w(r.script, T, 0, 0); rd(r.script, T, 0xc4, 0x80);
        assertThrows(TransferException.class, () -> r.frontend.execute(r.frontend.planTune(parameters(8_000_000, 0))));
        assertEquals(State.UNCERTAIN, r.frontend.getState());
        r.script.assertComplete();
    }

    @Test public void bankSelectAndBothIdReadsShareTheSessionLockAgainstAnotherBridge() throws Exception {
        ScriptedFakeControlIo script = new ScriptedFakeControlIo(); probeFixture(script, 0x197); w(script, X, 0, 9);
        Tbs5530Session session = Tbs5530BridgeTest.connectedSession();
        CountDownLatch first = new CountDownLatch(1), release = new CountDownLatch(1), competing = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Tbs5530ControlIo io = (type, request, value, index, buffer, offset, length, timeout) -> {
            int count = script.controlTransfer(type, request, value, index, buffer, offset, length, timeout);
            if (script.calls().size() == 1) {
                first.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("fixture deadline"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            return count;
        };
        Tbs5530Frontend frontend = new Tbs5530Frontend(new Tbs5530Bridge(io, session, 1), session, 1, new TestClock());
        Plan p = frontend.planProbe();
        Thread owner = new Thread(() -> { try { frontend.execute(p); } catch (Throwable e) { failure.set(e); } });
        Thread other = new Thread(() -> {
            competing.countDown();
            try { new Tbs5530Bridge(io, session, 1).i2cWrite(X, 0, bytes(0, 9), 0, 2); }
            catch (Throwable e) { failure.set(e); }
        });
        owner.start();
        try {
            assertTrue(first.await(3, TimeUnit.SECONDS)); other.start(); assertTrue(competing.await(3, TimeUnit.SECONDS));
            assertEquals(1, script.calls().size());
        } finally { release.countDown(); owner.join(3000); other.join(3000); }
        assertFalse(owner.isAlive()); assertFalse(other.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        script.assertComplete();
    }

    private static void assertPlanCounts(Plan p, Tbs5530Frontend.Result result, ScriptedFakeControlIo script, int first) {
        int out = 0, in = 0;
        List<ScriptedFakeControlIo.Call> calls = script.calls();
        for (int i = first; i < calls.size(); i++) {
            ScriptedFakeControlIo.Call c = calls.get(i);
            if (c.type == 0x40) out++; else if (c.type == 0xc0) in++; else fail("Unreviewed USB direction");
            assertTrue(c.request == 0x80 || c.request == 0x90 || c.request == 0x91);
        }
        assertEquals(p.usbOutCount, out); assertEquals(p.usbInCount, in);
        assertEquals(out, result.usbOutCompleted); assertEquals(in, result.usbInCompleted);
    }

    // Golden fixtures are independent of Plan/Builder: official cxd2878.c at the pinned SHA.
    static void probeFixture(ScriptedFakeControlIo f, int id) {
        w(f, X, 0, 0); rd(f, X, 0xfb, id >>> 8); rd(f, X, 0xfd, id & 255);
    }
    private static void resetFixture(ScriptedFakeControlIo f, int id) {
        w(f, X, 0, 0); w(f, X, 2, 0); w(f, X, 0, 0); w(f, X, 0x1d, 0); w(f, X, 0x14, 1);
        w(f, X, 0x50, 0); w(f, X, 0x10, 0);
        probeFixture(f, id);
    }
    private static void initPrefixFixture(ScriptedFakeControlIo f, int id, int tunerId) {
        resetFixture(f, id);
        w(f, T, 0, 0x9c); w(f, T, 0x10, 0, 0, 0, 0);
        w(f, X, 0, 0); w(f, X, 0x1a, 1); w(f, X, 0, 0); w(f, X, 8, 1);
        rd(f, U, 0x7f, tunerId);
    }
    private static void tunerBootWrites(ScriptedFakeControlIo f) {
        w(f, U, 0x99, 0x7a, 1);
        w(f, U, 0x81, 0x18, 0x84, 0xa8, 0x82, 0, 0, 0xc4, 0x40, 0x10, 0, 0x45, 0x75, 7, 0x1c, 0x3f, 2, 0x10, 0x20, 0x0a, 0);
        w(f, U, 0x9b, 0);
    }
    static void initFixture(ScriptedFakeControlIo f) {
        initPrefixFixture(f, 0x197, 0xe1); tunerBootWrites(f); rd(f, U, 0x1a, 0);
        w(f, U, 0x17, 0x2a, 0x0e); rd(f, U, 0x19, 0xab); w(f, U, 0x95, 0x0b);
        w(f, U, 0xb0, 0); w(f, U, 0x30, 0xe0); w(f, U, 0xb1, 0x1e); w(f, U, 0xb3, 2);
        rd(f, U, 0xb4, 0x73); w(f, U, 0xb3, 0); w(f, U, 0xb1, 0); w(f, U, 0x30, 0xe1); w(f, U, 0xb0, 1);
        rmw(f, U, 0x67, 0, 0); rmw(f, U, 0x74, 0, 2);
        w(f, U, 0x5e, 0x15, 0, 0); w(f, U, 0x88, 0); w(f, U, 0x87, 0xc0); w(f, U, 0x80, 1);
        w(f, X, 0, 0); w(f, X, 8, 0);
        w(f, T, 0, 0); rmw(f, T, 0xc4, 0, 0);
        w(f, T, 0, 2); rmw(f, T, 0xe4, 0, 0);
        w(f, T, 0, 0); rmw(f, T, 0xc6, 0, 1);
        w(f, T, 0, 0x60); rmw(f, T, 0x52, 0, 1);
        w(f, T, 0, 0); rmw(f, T, 0x95, 0, 1);
        w(f, T, 0, 0); rmw(f, T, 0x95, 1, 0x15);
        w(f, T, 0, 0); w(f, T, 0x96, 0x54); w(f, T, 0, 0); w(f, T, 0x97, 0x54);
    }
    static void tuneFixture(ScriptedFakeControlIo f, Tbs5530DvbT2TuneParameters p, boolean active) {
        w(f, T, 0, 0x23);
        if (p.selection.mode == Tbs5530DvbT2TuneParameters.Mode.AUTO) w(f, T, 0xad, 0);
        else { w(f, T, 0xaf, p.selection.id); w(f, T, 0xad, 1); }
        w(f, T, 0, 0x2e); w(f, T, 0x10, 0); w(f, T, 0, 0x2b); w(f, T, 0x9d, 0x2e);
        if (active) { streamFixture(f, false); }
        else {
            w(f, T, 0, 0); rd(f, T, 0xc4, 0);
            rmw(f, T, 0xd3, 0, 0); rmw(f, T, 0xde, 0, 0); rmw(f, T, 0xda, 0, 0); w(f, T, 0xd9, 8);
            rmw(f, T, 0x32, 0, 0); rmw(f, T, 0x33, 0, 1); rmw(f, T, 0x32, 0, 1);
            w(f, T, 0, 0x10); rmw(f, T, 0x66, 0, 1); w(f, T, 0, 0x40); rmw(f, T, 0x66, 0, 1);
            w(f, X, 0, 0); w(f, X, 0x17, 2);
            w(f, T, 0, 0); w(f, T, 0xa9, 0); w(f, T, 0x2c, 1); w(f, T, 0x4b, 0x74); w(f, T, 0x49, 0); w(f, X, 0x18, 0);
            w(f, T, 0, 0x11); w(f, T, 0x6a, 0x50); w(f, T, 0, 0); w(f, T, 0xce, 1, 1);
            w(f, T, 0, 0x13); w(f, T, 0x83, 0x10); w(f, T, 0x86, 0x34); w(f, T, 0x9f, 0xd8);
            w(f, T, 0, 0x23); w(f, T, 0xdb, 1); w(f, T, 0, 0x11); w(f, T, 0x33, 0, 3, 0x3b);
        }
        bandFixture(f, p.bandwidthHz);
        if (!active) {
            w(f, T, 0, 0); rmw(f, T, 0x80, 0, 8); rd(f, T, 0xa9, 0); rd(f, T, 0xc4, 0); w(f, T, 0, 0); w(f, T, 0x81, 0);
        }
        w(f, X, 0, 0); w(f, X, 8, 1);
        rmw(f, U, 0x74, 2, 2); w(f, U, 0x87, 0xc4, 0x40); w(f, U, 0x91, 0x10, 0x20); w(f, U, 0x9c, 0, 0);
        w(f, U, 0x5e, 0xee, 2, 0x1e, 0x67, 3, 0xb4, 0x78, 8, 0x30); rmw(f, U, 0x67, 0, 0);
        int bw, offset, widthOffset;
        switch (p.bandwidthHz) {
            case 1_700_000: bw = 3; offset = 0x16; widthOffset = 0x16; break;
            case 5_000_000: case 6_000_000: bw = 0; offset = 0x18; widthOffset = 0x1d; break;
            case 7_000_000: bw = 1; offset = 0x1a; widthOffset = 0x1b; break;
            case 8_000_000: bw = 2; offset = 0x1c; widthOffset = 0x1a; break;
            default: throw new AssertionError();
        }
        assertTrue(p.frequencyHz == 506_000_000L || p.frequencyHz == 594_000_000L || p.frequencyHz == 666_000_000L);
        int[] channel = p.frequencyHz == 506_000_000L ? new int[]{0x90, 0xb8, 0x07}
                : p.frequencyHz == 594_000_000L ? new int[]{0x50, 0x10, 0x09} : new int[]{0x90, 0x29, 0x0a};
        w(f, U, 0x68, 0, 0x88, 0, 0x0b, 0x22, bw, offset, widthOffset, channel[0], channel[1], channel[2], 255, 0x11, 0x99, 0, 0x24, 0x87);
        w(f, U, 0x88, 0); w(f, U, 0x87, 0xc0); w(f, X, 0, 0); w(f, X, 8, 0);
        w(f, T, 0, 0); w(f, T, 0xfe, 1); streamFixture(f, true);
    }
    private static void bandFixture(ScriptedFakeControlIo f, int width) {
        w(f, T, 0, 0x20);
        switch (width) {
            case 8_000_000: w(f, T, 0x9f, 0x15, 0, 0, 0, 0); break;
            case 7_000_000: w(f, T, 0x9f, 0x18, 0, 0, 0, 0); break;
            case 6_000_000: w(f, T, 0x9f, 0x1c, 0, 0, 0, 0); break;
            case 5_000_000: w(f, T, 0x9f, 0x21, 0x99, 0x99, 0x99, 0x99); break;
            case 1_700_000: w(f, T, 0x9f, 0x1a, 3, 0xe8, 0x8c, 0xb3); break;
            default: throw new AssertionError();
        }
        w(f, T, 0, 0x27); w(f, T, 0x7a, width == 1_700_000 ? 3 : 0); w(f, T, 0, 0x10);
        switch (width) {
            case 8_000_000: w(f, T, 0xa6, 0x2f, 0xba, 0x28, 0x9b, 0x28, 0x9d, 0x28, 0xa1, 0x29, 0xa5, 0x2a, 0xac, 0x29, 0xb5); break;
            case 7_000_000: w(f, T, 0xa6, 0x30, 0xb1, 0x29, 0x9a, 0x28, 0x9c, 0x28, 0xa0, 0x29, 0xa2, 0x2b, 0xa6, 0x2b, 0xad); break;
            case 5_000_000: case 6_000_000: w(f, T, 0xa6, 0x31, 0xa8, 0x29, 0x9b, 0x27, 0x9c, 0x28, 0x9e, 0x29, 0xa4, 0x29, 0xa2, 0x29, 0xa8); break;
            default: break;
        }
        w(f, T, 0xa5, width == 1_700_000 ? 0 : 1);
        switch (width) {
            case 8_000_000: w(f, T, 0xb6, 0x19, 0x99, 0x9a); w(f, T, 0xd7, 0); break;
            case 7_000_000: w(f, T, 0xb6, 0x16, 0x66, 0x66); w(f, T, 0xd7, 2); break;
            case 6_000_000: w(f, T, 0xb6, 0x13, 0x33, 0x33); w(f, T, 0xd7, 4); break;
            case 5_000_000: w(f, T, 0xb6, 0x13, 0x33, 0x33); w(f, T, 0xd7, 6); break;
            case 1_700_000: w(f, T, 0xb6, 0x12, 0xaa, 0xab); w(f, T, 0xd7, 3); break;
            default: throw new AssertionError();
        }
    }
    private static void streamFixture(ScriptedFakeControlIo f, boolean enable) {
        w(f, T, 0, 0); rd(f, T, 0xa9, 0); w(f, T, 0, 0); w(f, T, 0xc3, enable ? 0 : 1);
    }
    static void statusFixture(ScriptedFakeControlIo f, int raw) { w(f, T, 0, 0x20); rd(f, T, 0x10, raw); }
    private static void rmw(ScriptedFakeControlIo f, int address, int register, int before, int after) {
        rd(f, address, register, before); w(f, address, register, after);
    }
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length]; for (int i = 0; i < out.length; i++) out[i] = (byte) values[i]; return out;
    }
    private static void w(ScriptedFakeControlIo f, int address, int register, int... data) {
        byte[] payload = new byte[data.length + 3];
        payload[0] = (byte) (data.length + 2); payload[1] = (byte) (address << 1); payload[2] = (byte) register;
        System.arraycopy(bytes(data), 0, payload, 3, data.length); f.write(0x80, payload);
    }
    private static void rd(ScriptedFakeControlIo f, int address, int register, int... answer) {
        f.write(0x90, bytes(answer.length, address << 1, register)); f.read(bytes(answer));
    }
}
