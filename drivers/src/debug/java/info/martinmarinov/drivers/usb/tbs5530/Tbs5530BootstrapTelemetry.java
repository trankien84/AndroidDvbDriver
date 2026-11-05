// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Counts only the named boundary, never equates an adapter request with a native USB call. */
public final class Tbs5530BootstrapTelemetry {
    public interface Audit { void checkpoint(Snapshot snapshot) throws IOException; }
    public interface Clock { long nanos(); }
    public static final class Snapshot {
        public final String runEpoch, timebase;
        public final int plannedOrdinal, guardRequested, guardAccepted, nativeCallStarted,
                nativeCallCompleted, fullLengthCompleted, imageBytesFullLength, payloadBytesFullLength;
        public final List<String> events;
        Snapshot(Tbs5530BootstrapTelemetry t) {
            runEpoch = t.runEpoch; timebase = t.timebase; plannedOrdinal = t.plannedOrdinal;
            guardRequested = t.guardRequested; guardAccepted = t.guardAccepted;
            nativeCallStarted = t.nativeCallStarted; nativeCallCompleted = t.nativeCallCompleted;
            fullLengthCompleted = t.fullLengthCompleted; imageBytesFullLength = t.imageBytesFullLength;
            payloadBytesFullLength = t.payloadBytesFullLength;
            events = Collections.unmodifiableList(new ArrayList<>(t.events));
        }
    }
    private final String runEpoch;
    private final List<String> events = new ArrayList<>();
    private final Audit audit;
    private final Clock clock;
    private final String timebase;
    private int plannedOrdinal, guardRequested, guardAccepted, nativeCallStarted, nativeCallCompleted,
            fullLengthCompleted, imageBytesFullLength, payloadBytesFullLength;
    public Tbs5530BootstrapTelemetry() { this(snapshot -> { }); }
    public Tbs5530BootstrapTelemetry(Audit audit) { this(audit, System::nanoTime, "JAVA_NANO_TIME"); }
    public Tbs5530BootstrapTelemetry(Audit audit, Clock clock, String timebase) { this(audit, clock, timebase, null); }
    public Tbs5530BootstrapTelemetry(Audit audit, Clock clock, String timebase, String runEpoch) {
        if (audit == null || clock == null || timebase == null) throw new IllegalArgumentException("Audit and clock required");
        if (runEpoch != null && !runEpoch.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid run epoch");
        this.audit = audit; this.clock = clock; this.timebase = timebase;
        this.runEpoch = runEpoch == null ? UUID.randomUUID().toString() : runEpoch;
    }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
    public synchronized void planned(int ordinal) { plannedOrdinal = ordinal; event(ordinal, "PLANNED"); }
    public synchronized void guardRequested(int ordinal) { guardRequested++; event(ordinal, "GUARD_REQUESTED"); }
    public synchronized void guardAccepted(int ordinal) { guardAccepted++; event(ordinal, "GUARD_ACCEPTED"); }
    public void nativeIntent(int ordinal) throws IOException {
        synchronized (this) { event(ordinal, "NATIVE_INTENT_NOT_CALL_START"); }
        checkpoint();
    }
    public synchronized void nativeStarted(int ordinal) {
        nativeCallStarted++; event(ordinal, "NATIVE_CALL_START");
    }
    public void nativeReturned(int ordinal, int value, int length, int result) throws IOException {
        synchronized (this) {
            nativeCallCompleted++;
            if (result == length) {
                fullLengthCompleted++; payloadBytesFullLength += length;
                if (ordinal >= 3 && ordinal <= 130 && value >= 0 && value <= 0x1fc0 && length == 64) imageBytesFullLength += length;
            }
            event(ordinal, "NATIVE_CALL_END returned=" + result + " requested=" + length);
        }
        checkpoint();
    }
    public void nativeThrew(int ordinal, Throwable cause) throws IOException {
        synchronized (this) {
            nativeCallCompleted++;
            event(ordinal, "NATIVE_CALL_END exception=" + (cause instanceof IOException ? "IOException" : "RuntimeException"));
        }
        checkpoint();
    }
    public void stopped(int ordinal, Tbs5530BootstrapStop.Kind kind) throws IOException {
        synchronized (this) { event(ordinal, "STOP reason=" + kind); }
        checkpoint();
    }
    public void transitionDetached() throws IOException {
        synchronized (this) { event(132, "FINAL_RELEASE_FULL_LENGTH_DETACH_OBSERVATION_REQUIRED"); }
        checkpoint();
    }
    public void checkpoint() throws IOException {
        try { audit.checkpoint(snapshot()); }
        catch (IOException | RuntimeException error) {
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.AUDIT_FAILED, "Boundary audit failed", error);
        }
    }
    private void event(int ordinal, String status) {
        events.add("ordinal=" + ordinal + " monotonic_ns=" + clock.nanos() + " " + status);
    }
}
