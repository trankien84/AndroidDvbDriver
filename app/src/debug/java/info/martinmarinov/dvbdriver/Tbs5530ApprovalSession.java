// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop.Kind;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.UUID;

/** A challenge is not approval. Every write requires a new, deadline-bound host acknowledgment. */
public class Tbs5530ApprovalSession {
    public interface Clock {
        long now();
        void pause(long millis) throws InterruptedException;
    }
    interface Storage {
        String read(String name) throws IOException;
        void write(String name, String content) throws IOException;
        String install() throws IOException;
        boolean consumed();
        void consume() throws IOException;
        default boolean attemptConsumed(String approvalId) { return true; }
        default void consumeAttempt(String approvalId) throws IOException { throw new IOException("New-attempt storage unavailable"); }
    }
    private final Storage storage;
    private final Clock clock;
    private final boolean hardware;
    private final String challenge = UUID.randomUUID().toString();
    private final String runEpoch = UUID.randomUUID().toString();
    private final String install;
    private final String attachmentHash;
    private final long created;
    private volatile boolean foreground;
    private final java.util.concurrent.atomic.AtomicReference<Tbs5530BootstrapStop> stopped =
            new java.util.concurrent.atomic.AtomicReference<>();
    private volatile boolean started;
    private volatile String approvedId;
    private int acknowledged;
    private String acceptedNonce;
    private long acceptedRequest, acceptedDeadline;
    private boolean nativeChecked = true;

    Tbs5530ApprovalSession(Storage storage, Clock clock, String attachmentHash) throws IOException {
        this(storage, clock, attachmentHash, false);
    }
    Tbs5530ApprovalSession(Storage storage, Clock clock, String attachmentHash, boolean hardware) throws IOException {
        this.storage = storage; this.clock = clock; this.attachmentHash = attachmentHash;
        this.hardware = hardware; created = clock.now(); install = storage.install();
    }
    void foreground(boolean current) {
        if (!current && foreground) stop(Kind.FOREGROUND_LOST, "Foreground session lost; cannot resume approval", null);
        foreground = current;
    }
    void publishChallenge() throws IOException {
        try {
            JSONObject json = binding();
            json.put("created_elapsed_ms", created);
            json.put("hardware_enabled", false);
            json.put("approval_mode", hardware ? "NEW_AUDITED_ATTEMPT_REQUIRED" : "BENCHMARK_ONLY");
            json.put("legacy_consumed", storage.consumed());
            json.put("ack_timeout_ms", Tbs5530ApprovalProtocol.ACK_MS);
            json.put("heartbeat_timeout_ms", Tbs5530ApprovalProtocol.HEARTBEAT_MS);
            storage.write("phase29-challenge.json", json.toString());
        } catch (JSONException error) { throw stop(Kind.OBSERVATION_FAILED, "Challenge export failed", error); }
    }
    private JSONObject binding() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("approval_protocol", Tbs5530ApprovalProtocol.VERSION);
        json.put("challenge", challenge); json.put("run_epoch", runEpoch); json.put("install", install);
        json.put("upload_epoch", 1); json.put("attachment_hash", attachmentHash);
        json.put("plan", "TBS_SOURCE_DUAL_RESET"); json.put("sha256", Tbs5530ApprovalProtocol.SHA256);
        if (hardware && approvedId != null) json.put("new_approval_id", approvedId);
        return json;
    }
    String runEpoch() { return runEpoch; }
    private static Tbs5530BootstrapStop invalid(Kind kind, String message, Throwable cause) {
        return new Tbs5530BootstrapStop(kind, message, cause);
    }
    private Tbs5530BootstrapStop stop(Kind kind, String message, Throwable cause) {
        Tbs5530BootstrapStop failure = new Tbs5530BootstrapStop(kind, message, cause);
        stopped.compareAndSet(null, failure);
        return stopped.get();
    }
    void requireCurrent() throws IOException {
        if (started) { currentPermit(); return; }
        if (stopped.get() != null) throw stopped.get();
        // UI polling before consume is observation, not a transaction. A missing host
        // grant denies eligibility without burning a preparation that has never started.
        try {
            JSONObject permit = new JSONObject(storage.read("phase29-session-approval.json"));
            validate(permit, clock.now());
            if (hardware) {
                String candidate = permit.getString("new_approval_id");
                synchronized (this) {
                    if (approvedId == null) approvedId = candidate;
                    else if (!approvedId.equals(candidate)) throw invalid(Kind.AUTHORIZATION_REVOKED, "New approval ID changed", null);
                }
            }
        } catch (Tbs5530BootstrapStop error) { throw error; }
        catch (IOException | JSONException | RuntimeException error) {
            throw invalid(Kind.AUTHORIZATION_REVOKED, "No valid initial session approval", error);
        }
    }
    private JSONObject currentPermit() throws IOException {
        if (stopped.get() != null) throw stopped.get();
        try {
            JSONObject permit = new JSONObject(storage.read("phase29-session-approval.json"));
            validate(permit, clock.now());
            if (hardware) {
                String candidate = permit.getString("new_approval_id");
                synchronized (this) {
                    if (approvedId == null) approvedId = candidate;
                    else if (!approvedId.equals(candidate)) throw stop(Kind.AUTHORIZATION_REVOKED, "New approval ID changed", null);
                }
            }
            return permit;
        } catch (Tbs5530BootstrapStop error) { throw stop(error.kind, error.getMessage(), error); }
        catch (IOException | JSONException | RuntimeException error) {
            throw stop(Kind.AUTHORIZATION_REVOKED, "No valid session approval", error);
        }
    }
    private void validate(JSONObject permit, long now) throws JSONException, IOException {
        if (stopped.get() != null) throw stopped.get();
        if (!foreground) throw invalid(Kind.FOREGROUND_LOST, "Foreground lost", null);
        if (now < created || now - created >= Tbs5530ApprovalProtocol.SESSION_MS)
            throw invalid(Kind.PERMIT_EXPIRED, "Session expired", null);
        if (!challenge.equals(permit.getString("challenge")) || !runEpoch.equals(permit.getString("run_epoch"))
                || !install.equals(permit.getString("install")) || permit.getInt("approval_protocol") != Tbs5530ApprovalProtocol.VERSION
                || !attachmentHash.equals(permit.getString("attachment_hash")) || permit.getLong("upload_epoch") != 1
                || !"TBS_SOURCE_DUAL_RESET".equals(permit.getString("plan"))
                || !Tbs5530ApprovalProtocol.SHA256.equals(permit.getString("sha256")))
            throw invalid(Kind.HASH_PLAN_MISMATCH, "Permit binding changed", null);
        if (!permit.getBoolean("explicit_session_upload_approval") || !permit.getBoolean("physical_host_and_otg_confirmed")
                || !permit.getBoolean("host_ledger_committed"))
            throw invalid(Kind.AUTHORIZATION_REVOKED, "Explicit session approval or ledger missing", null);
        if (hardware) {
            String id = permit.getString("new_approval_id");
            if (!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || (approvedId != null && !approvedId.equals(id))
                    || !permit.getBoolean("explicit_new_attempt_approval")
                    || !permit.getBoolean("legacy_consumed_acknowledged")
                    || permit.getBoolean("legacy_consumed") != storage.consumed()
                    || !"NEW_APPROVAL_CONSUMED".equals(permit.getString("host_ledger_event")))
                throw invalid(Kind.AUTHORIZATION_REVOKED, "New audited attempt approval missing or changed", null);
        }
        long expires = permit.getLong("expires_elapsed_ms"), heartbeat = permit.getLong("heartbeat_elapsed_ms");
        if (expires <= now || expires > created + Tbs5530ApprovalProtocol.SESSION_MS)
            throw invalid(Kind.PERMIT_EXPIRED, "Permit expired", null);
        if (heartbeat < created || heartbeat > now || now - heartbeat >= Tbs5530ApprovalProtocol.HEARTBEAT_MS)
            throw invalid(Kind.HEARTBEAT_STALE, "Host heartbeat stale or from future", null);
    }
    synchronized void requireWrite(int ordinal) throws IOException {
        started = true;
        try {
            currentPermit();
            if (hardware && !storage.attemptConsumed(approvedId))
                throw stop(Kind.AUDIT_FAILED, "New attempt must be durably consumed before any write", null);
            if (!nativeChecked || ordinal != acknowledged + 1 || ordinal < 1 || ordinal > Tbs5530ApprovalProtocol.ORDINALS)
                throw stop(Kind.AUTHORIZATION_REVOKED, "Replayed or skipped write ordinal", null);
            long requested = clock.now(), deadline = requested + Tbs5530ApprovalProtocol.ACK_MS;
            String nonce = UUID.randomUUID().toString();
            JSONObject request = binding(); request.put("ordinal", ordinal); request.put("request_nonce", nonce);
            request.put("request_elapsed_ms", requested); request.put("ack_deadline_elapsed_ms", deadline);
            storage.write("phase29-write-request.json", request.toString());
            while (true) {
                JSONObject permit = currentPermit();
                long observed = clock.now();
                if (observed >= deadline) throw stop(Kind.PRE_NATIVE_ACK_TIMEOUT, "No fresh host step acknowledgment before deadline", null);
                int authorized = permit.optInt("authorized_ordinal", 0);
                if (authorized > ordinal || authorized < acknowledged)
                    throw stop(Kind.AUTHORIZATION_REVOKED, "Out-of-order host acknowledgment", null);
                if (authorized == ordinal) {
                    validateAck(permit, ordinal, nonce, requested, deadline, observed);
                    JSONObject telemetry = new JSONObject();
                    telemetry.put("run_epoch", runEpoch); telemetry.put("ordinal", ordinal);
                    telemetry.put("request_elapsed_ms", requested);
                    telemetry.put("host_seen_elapsed_ms", permit.getLong("host_seen_elapsed_ms"));
                    telemetry.put("host_ack_elapsed_ms", permit.getLong("host_ack_elapsed_ms"));
                    telemetry.put("host_clock", permit.optString("host_clock", "injected_test_clock"));
                    telemetry.put("ack_observed_elapsed_ms", observed);
                    telemetry.put("ack_deadline_elapsed_ms", deadline);
                    storage.write("phase29-write-observation.json", telemetry.toString());
                    JSONObject finalPermit = currentPermit();
                    long finalNow = clock.now();
                    if (finalNow >= deadline) throw stop(Kind.PRE_NATIVE_ACK_TIMEOUT, "Acknowledgment observation exceeded deadline", null);
                    validateAck(finalPermit, ordinal, nonce, requested, deadline, finalNow);
                    acknowledged = ordinal; acceptedNonce = nonce;
                    acceptedRequest = requested; acceptedDeadline = deadline; nativeChecked = false;
                    return;
                }
                try { clock.pause(Math.min(10, deadline - observed)); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw stop(Kind.USER_CANCELLED, "Approval interrupted", error);
                }
            }
        } catch (Tbs5530BootstrapStop error) { throw stop(error.kind, error.getMessage(), error); }
        catch (IOException error) { throw stop(Kind.JAVA_IO_EXCEPTION, "Approval storage failed", error); }
        catch (JSONException | RuntimeException error) { throw stop(Kind.AUTHORIZATION_REVOKED, "Malformed acknowledgment", error); }
    }
    synchronized void requireNative(int ordinal) throws IOException {
        try {
            if (stopped.get() != null) throw stopped.get();
            if (ordinal != acknowledged || nativeChecked || acceptedNonce == null)
                throw stop(Kind.AUTHORIZATION_REVOKED, "No unconsumed acknowledgment for native boundary", null);
            JSONObject permit = currentPermit();
            long now = clock.now();
            if (now >= acceptedDeadline)
                throw stop(Kind.PRE_NATIVE_ACK_TIMEOUT, "Acknowledgment expired before native boundary", null);
            validateAck(permit, ordinal, acceptedNonce, acceptedRequest, acceptedDeadline, now);
            nativeChecked = true;
        } catch (Tbs5530BootstrapStop error) { throw stop(error.kind, error.getMessage(), error); }
        catch (JSONException | RuntimeException error) { throw stop(Kind.AUTHORIZATION_REVOKED, "Invalid native acknowledgment", error); }
    }
    private void validateAck(JSONObject permit, int ordinal, String nonce, long requested, long deadline, long observed) throws JSONException, IOException {
        long seen = permit.getLong("host_seen_elapsed_ms"), ack = permit.getLong("host_ack_elapsed_ms");
        if (permit.getInt("authorized_ordinal") != ordinal || !nonce.equals(permit.getString("request_nonce"))
                || permit.getLong("request_elapsed_ms") != requested || permit.getLong("ack_deadline_elapsed_ms") != deadline
                || seen < requested || ack < seen || ack > observed || ack >= deadline
                || permit.getLong("heartbeat_elapsed_ms") < ack)
            throw stop(Kind.AUTHORIZATION_REVOKED, "Replayed, stale or future acknowledgment", null);
        validate(permit, observed);
    }
    synchronized void requireDispatch(int ordinal) throws IOException {
        if (stopped.get() != null) throw stopped.get();
        if (!foreground) throw stop(Kind.FOREGROUND_LOST, "Foreground lost at dispatch", null);
        if (ordinal != acknowledged || !nativeChecked || acceptedNonce == null)
            throw stop(Kind.AUTHORIZATION_REVOKED, "No authorized native dispatch", null);
        if (clock.now() >= acceptedDeadline)
            throw stop(Kind.PRE_NATIVE_ACK_TIMEOUT, "Acknowledgment expired at USB dispatch", null);
        acceptedNonce = null;
    }
    void requireStartBudget() throws IOException {
        try {
            JSONObject permit = currentPermit();
            long now = clock.now();
            long remaining = Math.min(created + Tbs5530ApprovalProtocol.SESSION_MS,
                    permit.getLong("expires_elapsed_ms")) - now;
            if (remaining < Tbs5530ApprovalProtocol.START_BUDGET_MS)
                throw stop(Kind.PERMIT_EXPIRED, "Insufficient remaining budget before first native write", null);
        } catch (JSONException error) { throw stop(Kind.AUTHORIZATION_REVOKED, "Invalid start budget", error); }
    }
    void consume() throws IOException {
        synchronized (Tbs5530ApprovalSession.class) {
            if (!hardware && storage.consumed()) throw stop(Kind.ATTEMPT_CONSUMED, "Physical attempt already consumed; no retry", null);
            currentPermit();
            started = true;
            if (hardware && storage.attemptConsumed(approvedId))
                throw stop(Kind.ATTEMPT_CONSUMED, "New audited attempt already consumed; no retry", null);
            try {
                if (hardware) storage.consumeAttempt(approvedId);
                else storage.consume();
            } catch (IOException error) { throw stop(Kind.AUDIT_FAILED, "Attempt persistence failed; no retry", error); }
            currentPermit();
        }
    }
    boolean legacyConsumed() { return storage.consumed(); }
    /** True also means no valid new grant: UI eligibility must fail closed without latching an absent permit. */
    boolean consumed() {
        if (!hardware) return storage.consumed();
        if (stopped.get() != null) return true;
        try {
            JSONObject permit = new JSONObject(storage.read("phase29-session-approval.json"));
            validate(permit, clock.now());
            return storage.attemptConsumed(permit.getString("new_approval_id"));
        } catch (IOException | JSONException | RuntimeException error) { return true; }
    }

}
