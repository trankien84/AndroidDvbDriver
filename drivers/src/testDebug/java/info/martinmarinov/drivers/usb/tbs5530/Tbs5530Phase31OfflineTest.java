// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** SYNTHETIC_TEST_FIXTURE: no Android manager, preferences, firmware or physical USB. */
public class Tbs5530Phase31OfflineTest {
    private static final class Fixture implements Tbs5530ControlProbe.Authorization, Tbs5530Frontend.Clock {
        final ScriptedFakeControlIo script = new ScriptedFakeControlIo();
        final Tbs5530Session session = new Tbs5530Session();
        final Set<String> consumed = new HashSet<>();
        final Tbs5530Session.Resources resources = new Tbs5530Session.Resources() {
            public void cancel() { } public void release() { } public void close() { closes++; }
        };
        final long generation;
        final Tbs5530ControlProbe probe;
        Tbs5530Frontend.Plan approved;
        int calls, closes, failAt = -1, auditAt = -1, audits;
        boolean experimental = true, rf = true, revoked;
        long now;
        Fixture() throws Exception {
            generation = session.requestPermission(); session.permissionResult(generation, true);
            session.connected(generation, resources); session.interfaceClaimed(generation);
            probe = new Tbs5530ControlProbe(session, generation, (type, request, value, index, bytes, offset, length, timeout) -> {
                calls++;
                int result = script.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                return calls == failAt ? result - 1 : result;
            }, this, this);
        }
        public void requireCurrent() throws IOException { if (revoked) throw new IOException("Revoked fixture"); }
        public void consume(Tbs5530Frontend.Plan plan) throws IOException {
            if (plan != approved || !consumed.add(plan.getSha256())) throw new IOException("Scope/consumed fixture");
        }
        public void audit(String boundary, int ordinal, int type, int request, int length, int result) throws IOException {
            if (++audits == auditAt) throw new IOException("Audit fault fixture");
        }
        public boolean rfInConfirmed() { return rf; }
        public boolean experimentalAutoPlpApproved() { return experimental; }
        public long elapsedRealtimeMillis() { return now; }
        public void delayMillis(long duration) { now += duration; }
        Tbs5530Frontend.Result execute(Tbs5530Frontend.Plan plan) throws Exception { approved = plan; return probe.execute(plan); }
        void initialize() throws Exception {
            Tbs5530FrontendTest.probeFixture(script, 0x197); execute(probe.planProbe());
            Tbs5530FrontendTest.initFixture(script); execute(probe.planInitialize()); probe.readyForTune();
        }
        Tbs5530DvbT2TuneParameters parameters(boolean auto) {
            return new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, auto
                    ? Tbs5530DvbT2TuneParameters.Selection.automatic() : Tbs5530DvbT2TuneParameters.Selection.manual(42));
        }
        Tbs5530Frontend.Plan tunePlan(boolean auto) throws Exception {
            return auto ? probe.planExperimentalAutoTune(parameters(true)) : probe.planTune(parameters(false));
        }
        void tune(boolean auto) throws Exception {
            Tbs5530FrontendTest.tuneFixture(script, parameters(auto), false); execute(tunePlan(auto));
        }
    }
    @Test public void manualAndAutoUseSameOwnerThroughProbeInitPollAndBoundedTs() throws Exception {
        for (boolean auto : new boolean[]{false, true}) {
            Fixture f = new Fixture(); f.initialize(); f.tune(auto);
            Tbs5530FrontendTest.statusFixture(f.script, 5); Tbs5530FrontendTest.statusFixture(f.script, 0x26);
            Tbs5530Frontend.Result status = f.execute(f.probe.planPollStatus(20));
            assertTrue(status.status.locked); assertEquals(2, status.statusSamples.size());
            assertTrue(f.session.isFrontendInitialized()); assertTrue(f.session.isFrontendLocked());
            f.script.write(0x8a, new byte[]{10, 0});
            Tbs5530Bridge bridge = new Tbs5530Bridge(f.script, f.session, f.generation);
            int[] reads = {0};
            Tbs5530BulkTransport bulk = new Tbs5530BulkTransport(f.session, f.generation, f.resources, () -> { }, (bytes, length, timeout) -> {
                assertTrue(length <= 16384); reads[0]++;
                for (int i = 0; i < 564; i += 188) {
                    bytes[i] = 0x47; bytes[i + 2] = 0x20; bytes[i + 3] = (byte)(0x10 | i / 188);
                }
                return 564;
            });
            Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(564, 1, 5000, 500);
            Tbs5530TsSmoke.Permission permission = new Tbs5530TsSmoke.Permission() {
                boolean used;
                public void requireCurrent(long token) throws IOException { f.requireCurrent(); if (token != f.generation) throw new IOException("Epoch"); }
                public void consume(Tbs5530TsSmoke.Limits value, long token) throws IOException {
                    requireCurrent(token); if (value != limits || used) throw new IOException("Foreign TS scope"); used = true;
                }
                public void audit(String event, int ordinal, int count) { }
            };
            Tbs5530TsSmoke.Result result = new Tbs5530TsSmoke(f.session, f.generation, bridge, bulk, permission, () -> 0L).run(limits);
            assertEquals(Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED, result.status);
            assertEquals(3, result.packets.packets); assertEquals(1, reads[0]); assertEquals(1, f.closes);
            assertEquals(4, f.consumed.size()); f.script.assertComplete();
        }
    }
    @Test public void autoNeedsSeparateExperimentalConsentAndRfBeforeConsumeOrIo() throws Exception {
        for (boolean missingExperimental : new boolean[]{true, false}) {
            Fixture f = new Fixture(); f.initialize();
            if (missingExperimental) f.experimental = false; else f.rf = false;
            int before = f.calls; f.approved = f.tunePlan(true);
            assertThrows(IOException.class, () -> f.probe.execute(f.approved));
            assertEquals(before, f.calls); assertEquals(2, f.consumed.size()); f.session.close();
        }
    }
    @Test public void everyAutoNativeBoundaryStopsOnShortWithoutRetryOrCorrection() throws Exception {
        for (int ordinal = 1; ordinal <= 99; ordinal++) {
            Fixture f = new Fixture(); f.initialize(); int before = f.calls;
            f.failAt = before + ordinal; Tbs5530FrontendTest.tuneFixture(f.script, f.parameters(true), false);
            f.approved = f.tunePlan(true);
            assertThrows(IOException.class, () -> f.probe.execute(f.approved));
            assertEquals(before + ordinal, f.calls); assertFalse(f.session.isFrontendLocked());
            assertEquals(Tbs5530Frontend.State.UNCERTAIN, f.probe.state()); f.session.close();
            assertEquals(1, f.closes);
        }
    }
    @Test public void auditFailureAndRevokedOwnerNeverReachNextNativeBoundary() throws Exception {
        Fixture f = new Fixture(); f.initialize(); int before = f.calls;
        f.auditAt = f.audits + 1; f.approved = f.tunePlan(true);
        assertThrows(IOException.class, () -> f.probe.execute(f.approved)); assertEquals(before, f.calls);
        f.session.close();
        Fixture stale = new Fixture(); stale.initialize(); stale.approved = stale.tunePlan(true); stale.probe.cancel();
        assertThrows(IOException.class, () -> stale.probe.execute(stale.approved)); assertEquals(84, stale.calls);
        stale.session.drainCleanup();
    }
}
