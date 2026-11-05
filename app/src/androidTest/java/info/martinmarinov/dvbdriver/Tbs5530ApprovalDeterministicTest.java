// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop.Kind;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class Tbs5530ApprovalDeterministicTest {
    interface Action { void run() throws Exception; }
    static final class FakeClock implements Tbs5530ApprovalSession.Clock {
        long time = 10000;
        Action onPause;
        public long now() { return time; }
        public void pause(long millis) {
            time += millis;
            if (onPause != null) try { onPause.run(); } catch (Exception error) { throw new AssertionError(error); }
        }
    }
    static final class MemoryStorage implements Tbs5530ApprovalSession.Storage {
        final Map<String, String> files = new HashMap<>();
        String identity = UUID.randomUUID().toString();
        boolean used, failConsume, failWrite;
        final java.util.Set<String> attempts = new java.util.HashSet<>();
        public boolean attemptConsumed(String id) { return attempts.contains(id); }
        public void consumeAttempt(String id) throws IOException {
            if (failConsume || !attempts.add(id)) throw new IOException("Synthetic duplicate or persistence failure");
        }
        Action afterRead;
        Action afterWrite;
        public String install() { return identity; }
        public boolean consumed() { return used; }
        public void consume() throws IOException {
            if (failConsume) throw new IOException("synthetic persistence failure");
            used = true;
        }
        public String read(String name) throws IOException {
            String text = files.get(name);
            if (afterRead != null) try { afterRead.run(); } catch (Exception error) { throw new IOException(error); }
            if (text == null) throw new IOException("synthetic missing file");
            return text;
        }
        public void write(String name, String text) throws IOException {
            if (failWrite) throw new IOException("synthetic write failure");
            files.put(name, text);
            if (afterWrite != null) try { afterWrite.run(); } catch (Exception error) { throw new IOException(error); }
        }
        JSONObject json(String name) throws Exception { return new JSONObject(files.get(name)); }
        void permit(JSONObject json) { files.put("phase29-session-approval.json", json.toString()); }
    }
    static final class Fixture {
        final FakeClock clock = new FakeClock();
        final MemoryStorage storage = new MemoryStorage();
        final Tbs5530ApprovalSession approval;
        JSONObject permit;
        Fixture() throws Exception { this(false); }
        Fixture(boolean hardware) throws Exception {
            storage.used = hardware;
            approval = new Tbs5530ApprovalSession(storage, clock, "synthetic-attachment", hardware);
            approval.foreground(true); approval.publishChallenge();
            permit = storage.json("phase29-challenge.json");
            permit.put("expires_elapsed_ms", clock.time + 300000);
            permit.put("heartbeat_elapsed_ms", clock.time);
            permit.put("explicit_session_upload_approval", true);
            permit.put("physical_host_and_otg_confirmed", true);
            permit.put("host_ledger_committed", true);
            permit.put("authorized_ordinal", 0);
            if (hardware) {
                permit.put("new_approval_id", UUID.randomUUID().toString());
                permit.put("explicit_new_attempt_approval", true); permit.put("legacy_consumed_acknowledged", true);
                permit.put("host_ledger_event", "NEW_APPROVAL_CONSUMED");
            }
            storage.permit(permit);
        }
        void ack() throws Exception {
            JSONObject request = storage.json("phase29-write-request.json");
            for (String field : new String[]{"ordinal", "request_nonce", "request_elapsed_ms", "ack_deadline_elapsed_ms"})
                permit.put("ordinal".equals(field) ? "authorized_ordinal" : field, request.get(field));
            permit.put("host_seen_elapsed_ms", clock.time); permit.put("host_ack_elapsed_ms", clock.time);
            permit.put("heartbeat_elapsed_ms", clock.time); storage.permit(permit);
        }
        void hostAfter(long delay, boolean heartbeat) {
            final long start = clock.time;
            clock.onPause = () -> {
                if (heartbeat) { permit.put("heartbeat_elapsed_ms", clock.time); storage.permit(permit); }
                if (clock.time - start >= delay) { ack(); clock.onPause = null; }
            };
            if (delay == 0) storage.afterWrite = () -> {
                if (storage.files.containsKey("phase29-write-request.json")) { ack(); storage.afterWrite = null; }
            };
        }
    }
    static void denied(Kind kind, Action action) throws Exception {
        try { action.run(); fail("Expected " + kind); }
        catch (Tbs5530BootstrapStop error) { assertEquals(kind, error.kind); }
    }
    @Test public void missingPermitDoesNotBecomeApproval() throws Exception {
        Fixture f = new Fixture(); f.storage.files.remove("phase29-session-approval.json");
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent); assertFalse(f.approval.consumed());
    }
    @Test public void bindingsAndExplicitPermissionsAreRequired() throws Exception {
        for (String field : new String[]{"challenge", "install", "run_epoch", "plan", "sha256", "attachment_hash"}) {
            Fixture f = new Fixture(); f.permit.put(field, "wrong"); f.storage.permit(f.permit);
            denied(Kind.HASH_PLAN_MISMATCH, f.approval::requireCurrent);
        }
        for (String field : new String[]{"explicit_session_upload_approval", "physical_host_and_otg_confirmed", "host_ledger_committed"}) {
            Fixture f = new Fixture(); f.permit.put(field, false); f.storage.permit(f.permit);
            denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        }
    }
    @Test public void expiryHeartbeatClockRollbackAndForegroundAreFailClosed() throws Exception {
        Fixture f = new Fixture(); f.permit.put("expires_elapsed_ms", f.clock.time); f.storage.permit(f.permit);
        denied(Kind.PERMIT_EXPIRED, f.approval::requireCurrent);
        f = new Fixture(); f.clock.time += 2000;
        denied(Kind.HEARTBEAT_STALE, f.approval::requireCurrent);
        f = new Fixture(); f.permit.put("heartbeat_elapsed_ms", f.clock.time + 1); f.storage.permit(f.permit);
        denied(Kind.HEARTBEAT_STALE, f.approval::requireCurrent);
        f = new Fixture(); f.clock.time--;
        denied(Kind.PERMIT_EXPIRED, f.approval::requireCurrent);
        f = new Fixture(); f.approval.foreground(false);
        denied(Kind.FOREGROUND_LOST, f.approval::requireCurrent);
    }
    @Test public void backgroundThenResumeCannotRearmTheSameChallenge() throws Exception {
        Fixture f = new Fixture(); f.approval.foreground(false); f.approval.foreground(true);
        denied(Kind.FOREGROUND_LOST, f.approval::requireCurrent);
        assertFalse(f.storage.used);
        Fixture racing = new Fixture(); racing.storage.afterRead = () -> {
            racing.approval.foreground(false); racing.approval.foreground(true);
        };
        denied(Kind.FOREGROUND_LOST, racing.approval::requireCurrent);
    }
    @Test public void all132OrdinalsHaveMonotonicTelemetryWithJitterAndNoSleeping() throws Exception {
        Fixture f = new Fixture();
        for (int ordinal = 1; ordinal <= 132; ordinal++) {
            long delay = ordinal % 17 == 0 ? 1400 : (ordinal * 37) % 170;
            f.hostAfter(delay, true); f.approval.requireWrite(ordinal); f.approval.requireNative(ordinal);
            JSONObject sample = f.storage.json("phase29-write-observation.json");
            assertEquals(ordinal, sample.getInt("ordinal"));
            assertTrue(sample.getLong("host_seen_elapsed_ms") >= sample.getLong("request_elapsed_ms"));
            assertTrue(sample.getLong("host_ack_elapsed_ms") >= sample.getLong("host_seen_elapsed_ms"));
            assertTrue(sample.getLong("ack_observed_elapsed_ms") >= sample.getLong("host_ack_elapsed_ms"));
            assertTrue(sample.getLong("ack_observed_elapsed_ms") < sample.getLong("ack_deadline_elapsed_ms"));
        }
    }
    @Test public void eachOf132OrdinalsRejectsDelaysAtDeadlineThroughThreeSeconds() throws Exception {
        for (int position = 1; position <= 132; position++) {
            Fixture f = new Fixture();
            for (int ordinal = 1; ordinal < position; ordinal++) {
                f.hostAfter(0, true); f.approval.requireWrite(ordinal); f.approval.requireNative(ordinal);
            }
            final int failedOrdinal = position;
            long delay = new long[]{1500, 1510, 2000, 3000}[position % 4];
            f.hostAfter(delay, true);
            denied(Kind.PRE_NATIVE_ACK_TIMEOUT, () -> f.approval.requireWrite(failedOrdinal));
            f.clock.time += 3000; f.ack();
            denied(Kind.PRE_NATIVE_ACK_TIMEOUT, () -> f.approval.requireWrite(failedOrdinal));
        }
    }
    @Test public void staleReplaySkipAndForeignNonceAreRejected() throws Exception {
        Fixture f = new Fixture(); f.hostAfter(0, true); f.approval.requireWrite(1);
        denied(Kind.AUTHORIZATION_REVOKED, () -> f.approval.requireWrite(1));
        Fixture skip = new Fixture(); denied(Kind.AUTHORIZATION_REVOKED, () -> skip.approval.requireWrite(2));
        Fixture preack = new Fixture(); preack.permit.put("authorized_ordinal", 1); preack.storage.permit(preack.permit);
        denied(Kind.AUTHORIZATION_REVOKED, () -> preack.approval.requireWrite(1));
        Fixture wrong = new Fixture(); wrong.clock.onPause = () -> {
            wrong.ack(); wrong.permit.put("request_nonce", "replayed"); wrong.storage.permit(wrong.permit);
        };
        denied(Kind.AUTHORIZATION_REVOKED, () -> wrong.approval.requireWrite(1));
        Fixture future = new Fixture(); future.clock.onPause = () -> {
            future.ack(); future.permit.put("authorized_ordinal", 2); future.storage.permit(future.permit);
        };
        denied(Kind.AUTHORIZATION_REVOKED, () -> future.approval.requireWrite(1));
    }
    @Test public void delayedReadAndObservationCannotAcceptLateAck() throws Exception {
        Fixture f = new Fixture(); f.hostAfter(0, true);
        f.storage.afterRead = () -> { if (f.storage.files.containsKey("phase29-write-request.json")) f.clock.time += 1500; };
        denied(Kind.PRE_NATIVE_ACK_TIMEOUT, () -> f.approval.requireWrite(1));
        Fixture write = new Fixture(); write.clock.onPause = () -> {
            write.ack(); write.clock.onPause = null;
            write.storage.afterWrite = () -> { write.clock.time += 1500; };
        };
        denied(Kind.PRE_NATIVE_ACK_TIMEOUT, () -> write.approval.requireWrite(1));
    }
    @Test public void permitRevocationDuringReadOrObservationIsRevalidated() throws Exception {
        Fixture f = new Fixture(); f.hostAfter(0, true);
        f.storage.afterRead = () -> { if (f.storage.files.containsKey("phase29-write-request.json")) f.approval.foreground(false); };
        denied(Kind.FOREGROUND_LOST, () -> f.approval.requireWrite(1));
        Fixture revoke = new Fixture(); revoke.clock.onPause = () -> {
            revoke.ack(); revoke.clock.onPause = null;
            revoke.storage.afterWrite = () -> {
                revoke.permit.put("explicit_session_upload_approval", false); revoke.storage.permit(revoke.permit);
            };
        };
        denied(Kind.AUTHORIZATION_REVOKED, () -> revoke.approval.requireWrite(1));
    }
    @Test public void crashConsumeLegacyTrueAndUninstallAreNotConfused() throws Exception {
        Fixture f = new Fixture(); f.approval.consume(); assertTrue(f.approval.consumed());
        denied(Kind.ATTEMPT_CONSUMED, f.approval::consume);
        Tbs5530ApprovalSession restart = new Tbs5530ApprovalSession(f.storage, f.clock, "synthetic-attachment");
        restart.foreground(true); assertTrue(restart.consumed());
        denied(Kind.HASH_PLAN_MISMATCH, restart::requireCurrent);
        denied(Kind.ATTEMPT_CONSUMED, restart::consume);
        Fixture legacy = new Fixture(); legacy.storage.used = true;
        denied(Kind.ATTEMPT_CONSUMED, legacy.approval::consume); assertTrue(legacy.storage.used);
        // A fresh store models uninstall honestly: Android preferences do not survive it.
        Tbs5530ApprovalSession uninstalled = new Tbs5530ApprovalSession(new MemoryStorage(), f.clock, "synthetic-attachment");
        assertFalse(uninstalled.consumed());
        denied(Kind.AUTHORIZATION_REVOKED, uninstalled::requireCurrent);
    }
    @Test public void staleReadVisibilityDoesNotAuthorizeUntilFreshSnapshotArrives() throws Exception {
        Fixture f = new Fixture();
        f.storage.afterRead = () -> {
            if (f.storage.files.containsKey("phase29-write-request.json")) {
                f.ack(); f.storage.afterRead = null;
            }
        };
        f.approval.requireWrite(1); f.approval.requireNative(1);
        assertEquals(10010, f.clock.time);
        Fixture malformed = new Fixture(); malformed.storage.files.put("phase29-session-approval.json", "partial{");
        denied(Kind.AUTHORIZATION_REVOKED, malformed.approval::requireCurrent);
    }
    @Test public void nativeBoundaryRechecksDeadlineNonceAndAllowsOnlyOneCall() throws Exception {
        Fixture f = new Fixture(); f.hostAfter(0, true); f.approval.requireWrite(1);
        f.approval.requireNative(1);
        denied(Kind.AUTHORIZATION_REVOKED, () -> f.approval.requireNative(1));
        Fixture late = new Fixture(); late.hostAfter(0, true); late.approval.requireWrite(1); late.clock.time += 1500;
        denied(Kind.PRE_NATIVE_ACK_TIMEOUT, () -> late.approval.requireNative(1));
        Fixture nonce = new Fixture(); nonce.hostAfter(0, true); nonce.approval.requireWrite(1);
        nonce.permit.put("request_nonce", "replaced"); nonce.storage.permit(nonce.permit);
        denied(Kind.AUTHORIZATION_REVOKED, () -> nonce.approval.requireNative(1));
        Fixture revoked = new Fixture(); revoked.hostAfter(0, true); revoked.approval.requireWrite(1);
        revoked.approval.foreground(false);
        denied(Kind.FOREGROUND_LOST, () -> revoked.approval.requireNative(1));
        Fixture skipped = new Fixture(); skipped.hostAfter(0, true); skipped.approval.requireWrite(1);
        denied(Kind.AUTHORIZATION_REVOKED, () -> skipped.approval.requireWrite(2));
    }
    @Test public void newAuditedAttemptConsumesItsOwnStoreAndNeverResetsLegacy() throws Exception {
        Fixture f = new Fixture(true);
        assertTrue(f.approval.legacyConsumed()); assertFalse(f.approval.consumed());
        String id = f.permit.getString("new_approval_id");
        f.approval.consume();
        assertTrue(f.approval.consumed()); assertTrue(f.storage.attemptConsumed(id)); assertTrue(f.storage.used);
        f.hostAfter(0, true); f.approval.requireWrite(1); f.approval.requireNative(1);
        denied(Kind.ATTEMPT_CONSUMED, f.approval::consume); assertTrue(f.storage.used);
    }
    @Test public void newAttemptRequiresAllExplicitFlagsAndLegacyAcknowledgment() throws Exception {
        for (String field : new String[]{"new_approval_id", "explicit_new_attempt_approval", "legacy_consumed_acknowledged", "host_ledger_event"}) {
            Fixture f = new Fixture(true); f.permit.remove(field); f.storage.permit(f.permit);
            assertTrue(f.approval.consumed());
            denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent); assertTrue(f.storage.used);
        }
        Fixture f = new Fixture(true); f.permit.put("legacy_consumed", false); f.storage.permit(f.permit);
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        Fixture early = new Fixture(true);
        denied(Kind.AUDIT_FAILED, () -> early.approval.requireWrite(1));
    }
    @Test public void newAttemptCannotSwapIdReplayOrSurviveRestartBinding() throws Exception {
        Fixture f = new Fixture(true); f.approval.requireCurrent();
        f.permit.put("new_approval_id", UUID.randomUUID().toString()); f.storage.permit(f.permit);
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::consume);
        Fixture used = new Fixture(true); used.storage.attempts.add(used.permit.getString("new_approval_id"));
        assertTrue(used.approval.consumed()); denied(Kind.ATTEMPT_CONSUMED, used.approval::consume);
        Fixture crash = new Fixture(true); crash.approval.consume();
        Tbs5530ApprovalSession restarted = new Tbs5530ApprovalSession(crash.storage, crash.clock, "synthetic-attachment", true);
        restarted.foreground(true); assertTrue(restarted.consumed());
        denied(Kind.HASH_PLAN_MISMATCH, restarted::consume);
        assertTrue(crash.storage.used); assertEquals(1, crash.storage.attempts.size());
    }
    @Test public void missingNewPermitEligibilityDoesNotPoisonLaterExplicitApproval() throws Exception {
        Fixture f = new Fixture(true); String saved = f.storage.files.remove("phase29-session-approval.json");
        assertTrue(f.approval.consumed()); assertTrue(f.approval.legacyConsumed());
        f.storage.files.put("phase29-session-approval.json", saved);
        assertFalse(f.approval.consumed()); f.approval.consume(); assertTrue(f.approval.consumed());
    }
    @Test public void uiPollingMissingInitialPermitCanObserveLaterExternalGrant() throws Exception {
        Fixture f = new Fixture(true);
        String grant = f.storage.files.remove("phase29-session-approval.json");
        for (int poll = 0; poll < 3; poll++) {
            assertTrue(f.approval.consumed());
            denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        }
        assertFalse(f.storage.files.containsKey("phase29-session-approval.json"));
        f.storage.files.put("phase29-session-approval.json", grant);
        f.approval.requireCurrent(); assertFalse(f.approval.consumed());
        f.approval.consume(); assertTrue(f.approval.legacyConsumed());
        f.storage.files.remove("phase29-session-approval.json");
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        f.storage.files.put("phase29-session-approval.json", grant);
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        denied(Kind.AUTHORIZATION_REVOKED, () -> f.approval.requireWrite(1));
    }
    @Test public void malformedInitialPermitCanBeReplacedOnlyBeforeConsume() throws Exception {
        Fixture f = new Fixture(true); String grant = f.storage.files.get("phase29-session-approval.json");
        f.storage.files.put("phase29-session-approval.json", "partial{");
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        f.storage.files.put("phase29-session-approval.json", grant); f.approval.requireCurrent();
        f.approval.consume(); f.storage.files.put("phase29-session-approval.json", "partial{");
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
        f.storage.files.put("phase29-session-approval.json", grant);
        denied(Kind.AUTHORIZATION_REVOKED, f.approval::requireCurrent);
    }
    @Test public void newAttemptPersistenceFailureStopsBeforeAnyRequest() throws Exception {
        Fixture f = new Fixture(true); f.storage.failConsume = true;
        denied(Kind.AUDIT_FAILED, f.approval::consume);
        assertTrue(f.storage.used); assertTrue(f.storage.attempts.isEmpty());
        assertFalse(f.storage.files.containsKey("phase29-write-request.json"));
        denied(Kind.AUDIT_FAILED, () -> f.approval.requireWrite(1));
    }
    @Test public void persistenceAndRequestWriteFailuresAreTypedAndTerminal() throws Exception {
        Fixture f = new Fixture(); f.storage.failConsume = true;
        denied(Kind.AUDIT_FAILED, f.approval::consume);
        denied(Kind.AUDIT_FAILED, f.approval::requireCurrent);
        Fixture write = new Fixture(); write.storage.failWrite = true;
        denied(Kind.JAVA_IO_EXCEPTION, () -> write.approval.requireWrite(1));
    }
}
