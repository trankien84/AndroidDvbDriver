// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Finite, fail-closed debug authorization. No Android or hardware access. */
public final class Tbs5530AutonomousPolicy {
    public static final String VERSION = "TBS5530-AUTONOMOUS/v1";
    public static final long MAX_SESSION_MS = 300000, HEARTBEAT_MS = 2000;
    public enum Profile { AUTO_VERIFIED, EXPERIMENTAL_DIAGNOSTIC }
    public enum State { WAIT_DEVICE, NEED_ANDROID_USB_PERMISSION, FX2_DEFAULT, FIRMWARE_BOOTSTRAP,
        WAIT_REENUMERATION, OPERATIONAL, FRONTEND_INIT, LOCK_CONFIRMED, TS_CAPTURE, REPORT, ERROR_UNCERTAIN }
    public interface Clock { long now(); }
    public interface Ledger {
        /** Throws on malformed, missing or inconsistent existing state. */
        void check() throws IOException;
        void append(String event, String attempt, String plan) throws IOException;
    }
    private final Clock clock;
    private final Ledger ledger;
    private final long created;
    private long lastNow, heartbeat, expires, generation = -1;
    private String attachment, owner;
    private Profile profile;
    private boolean granted, foreground, stopped, permission, uncertain, uploading;
    private int dispatchOrdinal, nativeOrdinal;
    private State state = State.WAIT_DEVICE;
    private final Set<String> attempts = new HashSet<>(), uploads = new HashSet<>();

    public Tbs5530AutonomousPolicy(Clock clock, Ledger ledger) throws IOException {
        this.clock = clock; this.ledger = ledger;
        created = lastNow = clock.now();
        if (created < 0 || created > Long.MAX_VALUE - MAX_SESSION_MS) throw new IOException("Invalid session clock");
        ledger.check();
    }
    public static boolean bootstrapTransitionKnown(Tbs5530OneShotBootstrap.State state, int completed,
                                                   String failure, String cleanupFailure,
                                                   Tbs5530BootstrapTelemetry.Snapshot boundaries) {
        return (state == Tbs5530OneShotBootstrap.State.TRANSITIONING
                || state == Tbs5530OneShotBootstrap.State.OPERATIONAL_CANDIDATE)
                && "NONE".equals(failure) && "NONE".equals(cleanupFailure) && completed == 132 && boundaries != null
                && boundaries.guardRequested == 132 && boundaries.guardAccepted == 132
                && boundaries.nativeCallStarted == 132 && boundaries.nativeCallCompleted == 132
                && boundaries.fullLengthCompleted == 132 && boundaries.imageBytesFullLength == 8192
                && boundaries.payloadBytesFullLength == 8196;
    }
    public static boolean nativeReturnKnown(boolean synchronous, int reads, int result, int errno) {
        return synchronous && reads > 0 && result != Integer.MIN_VALUE && errno >= 0;
    }
    public static boolean completedReceiveError(boolean synchronous, int reads, int result, int errno) {
        return nativeReturnKnown(synchronous, reads, result, errno) && result == -1 && errno > 0;
    }
    public synchronized State state() { return state; }
    public long created() { return created; }
    public synchronized void foreground(boolean value) {
        if (!value && granted) stopped = true;
        foreground = value;
    }
    public synchronized void grant(Profile value, long deadline, long beat) throws IOException {
        long now = time();
        if (granted || stopped || !foreground || value == null || deadline <= now || deadline > created + MAX_SESSION_MS
                || beat < created || beat > now || now - beat >= HEARTBEAT_MS) throw new IOException("Invalid single session grant");
        ledger.check(); ledger.append("SESSION_GRANTED", "SESSION", value.name());
        granted = true; profile = value; expires = deadline; heartbeat = beat;
    }
    public synchronized void heartbeat(long value) throws IOException {
        long now = time();
        requireLiveState(now);
        if (value < created || value < heartbeat || value > now || now - value >= HEARTBEAT_MS) {
            stopped = true; throw new IOException("Invalid heartbeat");
        }
        heartbeat = value;
    }
    private long time() throws IOException {
        long now = clock.now();
        if (now < lastNow || now < created) { stopped = true; throw new IOException("Clock rollback"); }
        lastNow = now; return now;
    }
    public synchronized void requireSession() throws IOException {
        long now = time();
        requireLiveState(now);
        if (heartbeat > now || now - heartbeat >= HEARTBEAT_MS) {
            stopped = true; throw new IOException("Session stopped, expired, background or host offline");
        }
    }
    private void requireLiveState(long now) throws IOException {
        if (!granted || stopped || uncertain || !foreground || now >= expires || now - created >= MAX_SESSION_MS) {
            stopped = true; throw new IOException("Session stopped, expired, background or host offline");
        }
        try { ledger.check(); }
        catch (IOException error) { failUncertain(); throw error; }
    }
    public synchronized void permissionWaiting() throws IOException {
        requireSession();
        if (owner != null || uploading) throw new IOException("Permission UI while I/O owner active");
        state = State.NEED_ANDROID_USB_PERMISSION;
    }
    public synchronized void observe(String attached, long token, boolean allowed,
                                      Tbs5530OperationalProfile.Kind kind) throws IOException {
        requireSession();
        if (attached == null || attached.isEmpty() || token < 0 || kind == Tbs5530OperationalProfile.Kind.UNKNOWN)
            throw new IOException("Unknown attachment/profile");
        if (attachment != null && (!attachment.equals(attached) || generation != token)) {
            stop(); throw new IOException("Unexpected attachment generation change");
        }
        attachment = attached; generation = token; permission = allowed;
        if (!allowed) { state = State.NEED_ANDROID_USB_PERMISSION; throw new IOException("OS USB permission required"); }
        state = kind == Tbs5530OperationalProfile.Kind.FX2_DEFAULT ? State.FX2_DEFAULT : State.OPERATIONAL;
    }
    public synchronized void bindOwner(String identity, long token) throws IOException {
        requireSession();
        if (state != State.OPERATIONAL || !permission || owner != null || identity == null || !identity.matches("[a-f0-9]{64}"))
            throw new IOException("Fresh exclusive operational owner required");
        owner = identity; generation = token;
    }
    public synchronized void requireOwner(String identity, long token, boolean osPermission) throws IOException {
        requireSession();
        if (!osPermission || !permission || owner == null || !owner.equals(identity) || token != generation) {
            stopped = true; throw new IOException("Permission or owner generation changed");
        }
    }
    public synchronized void consume(String attempt, String plan, long maximumMillis) throws IOException {
        requireSession();
        if (owner == null || !permission || attempt == null || !attempt.matches("[a-f0-9-]{36}")
                || plan == null || !plan.matches("[a-f0-9]{64}") || maximumMillis < 0
                || expires - time() < maximumMillis + 5000 || attempts.contains(attempt))
            throw new IOException("Invalid, duplicate or insufficient-budget attempt");
        ledger.append("ATTEMPT_CONSUMED", attempt, plan); attempts.add(attempt);
    }
    public synchronized void requireUploadStartBudget() throws IOException {
        requireSession();
        if (state != State.FX2_DEFAULT || !permission || owner != null || uploads.contains(attachment)
                || expires - time() < 285000) throw new IOException("Upload forbidden or insufficient start budget");
    }
    public synchronized void beginUpload(String attempt) throws IOException {
        requireUploadStartBudget();
        ledger.append("FIRMWARE_CONSUMED", attempt, Tbs5530FirmwareValidation.SHA256);
        uploads.add(attachment); uploading = true; dispatchOrdinal = nativeOrdinal = 0; state = State.FIRMWARE_BOOTSTRAP;
    }
    public synchronized void uploadDispatch(int ordinal) throws IOException {
        requireSession();
        if (!uploading || ordinal != dispatchOrdinal + 1 || ordinal > 132 || nativeOrdinal != ordinal)
            throw new IOException("Firmware dispatch ordinal mismatch");
        dispatchOrdinal = ordinal;
    }
    public synchronized void uploadNative(int ordinal) throws IOException {
        requireSession();
        if (!uploading || ordinal != nativeOrdinal + 1 || dispatchOrdinal != nativeOrdinal || ordinal > 132)
            throw new IOException("Firmware native ordinal mismatch");
        nativeOrdinal = ordinal;
    }
    /** A successful return is insufficient unless all native boundaries and cleanup are known. */
    public synchronized void uploadComplete(int completed, boolean cleanupPending) throws IOException {
        requireSession();
        if (!uploading || completed != 132 || dispatchOrdinal != 132 || nativeOrdinal != 132 || cleanupPending) {
            failUncertain(); throw new IOException("Firmware result or cleanup uncertain");
        }
        ledger.append("FIRMWARE_COMPLETE", "132", Tbs5530FirmwareValidation.SHA256);
        uploading = false; owner = null; permission = false; attachment = null; generation = -1;
        state = State.WAIT_REENUMERATION;
    }
    public synchronized boolean consumed(String attempt) { return attempts.contains(attempt); }
    public synchronized boolean experimental() { return profile == Profile.EXPERIMENTAL_DIAGNOSTIC; }
    public synchronized void transition(State next) throws IOException {
        requireSession();
        boolean valid = state == State.OPERATIONAL && next == State.FRONTEND_INIT
                || state == State.FRONTEND_INIT && next == State.LOCK_CONFIRMED
                || state == State.LOCK_CONFIRMED && next == State.TS_CAPTURE
                || next == State.REPORT;
        if (!valid) throw new IOException("Invalid autonomous state transition");
        ledger.append("STATE", "SESSION", next.name()); state = next;
    }
    public synchronized void stop() { stopped = true; permission = false; }
    public synchronized void detach() { if (uploading || owner != null) failUncertain(); else stop(); }
    public synchronized void failUncertain() { uncertain = true; stopped = true; permission = false; state = State.ERROR_UNCERTAIN; }
}
