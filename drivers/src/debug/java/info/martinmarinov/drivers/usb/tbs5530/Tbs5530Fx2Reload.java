// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** One exact operational RAM reload, never a retry or a public operational A0 transport. */
public final class Tbs5530Fx2Reload {
    public static final String OPTION = "B2_RELOAD_AFTER_SET_CONFIG";
    public static final String DESCRIPTOR_SHA256 = "22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5";
    public static final int WAIT_MS = 1500;
    public enum State { PREPARED, CONSUMED, TRANSFERRING, WAITING, COMPLETE, BLOCKED, UNCERTAIN, RELOAD_CAUSED_REENUMERATION }
    public interface Guard { void require() throws IOException; }
    public interface Clock { long nanos(); void pause(int millis) throws IOException; }
    interface Port {
        int write(Tbs5530FirmwarePlan.Step step) throws IOException;
        Boundary boundary() throws IOException;
        void cleanup();
    }
    public static final class Boundary {
        public final int ordinal, value, length, result, errno;
        public final long beforeNanos, afterNanos;
        public Boundary(int ordinal, int value, int length, int result, int errno, long before, long after) {
            this.ordinal = ordinal; this.value = value; this.length = length; this.result = result; this.errno = errno;
            beforeNanos = before; afterNanos = after;
        }
    }
    public static final class Snapshot {
        public final State state;
        public final int attempted, completed;
        public final long finalReleaseNanos, waitEndedNanos, detachedNanos;
        public final boolean consumed;
        Snapshot(Tbs5530Fx2Reload r) {
            state = r.state; attempted = r.attempted; completed = r.completed; consumed = r.consumed;
            finalReleaseNanos = r.finalRelease; waitEndedNanos = r.waitEnded; detachedNanos = r.detachedAt;
        }
    }
    private final List<Tbs5530FirmwarePlan.Step> steps;
    private final Guard guard;
    private final Clock clock;
    private final Port port;
    private final Tbs5530ReloadLedger ledger;
    private final String epochKey;
    private volatile State state = State.PREPARED;
    private volatile int attempted, completed;
    private volatile boolean consumed, detached, transferActive;
    private volatile long finalRelease = -1, waitEnded = -1, detachedAt = -1;
    private boolean once, cleaned;

    Tbs5530Fx2Reload(Tbs5530FirmwarePlan validated, Guard guard, Clock clock, Port port,
                     Tbs5530ReloadLedger ledger, String epochKey) throws IOException {
        this.steps = Tbs5530Fx2ProtocolPlan.describe(validated, "TBS_SOURCE_DUAL_RESET").getSteps();
        if (steps.size() != 133 || steps.get(132).getDelayMillis() != 100) throw new IOException("Exact 132/100ms source plan required");
        this.guard = java.util.Objects.requireNonNull(guard); this.clock = java.util.Objects.requireNonNull(clock);
        this.port = java.util.Objects.requireNonNull(port); this.ledger = java.util.Objects.requireNonNull(ledger); this.epochKey = epochKey;
    }
    public static byte[] lockedImage(Tbs5530FirmwarePlan validated) throws IOException {
        if (validated == null) throw new IOException("Missing firmware plan");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Tbs5530FirmwarePlan.Step step : validated.getSteps())
            if (step.getKind() == Tbs5530FirmwarePlan.Kind.DATA && step.getValue() < 0x2000) bytes.write(step.getPayload());
        byte[] image = bytes.toByteArray();
        try { Tbs5530FirmwareValidation.validate(image, Tbs5530FirmwareValidation.SOURCE, Tbs5530FirmwareValidation.ENTRY,
                Tbs5530FirmwareValidation.Format.RAW_LINEAR); }
        catch (IllegalArgumentException error) { throw new IOException("Reload image size/hash mismatch before I/O", error); }
        return image;
    }
    public static void requireProfile(Tbs5530OperationalProfile profile) throws IOException {
        if (profile == null || profile.kind != Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE
                || !DESCRIPTOR_SHA256.equals(profile.descriptorHash) || profile.negotiatedUsbSpeed != 3
                || profile.streamPacketSize != 512 || profile.controlPacketSize != 512)
            throw new IOException("B2 requires pinned 50-byte Samsung operational descriptors and GET_SPEED=3");
    }
    public static String transcript() {
        return OPTION + "|firmware=" + Tbs5530FirmwareValidation.SHA256 + "|bytes=8192|TBS_SOURCE_DUAL_RESET|132"
                + "|after=OS_PERMISSION,CLAIM|before=80,90,91,8A|planDelayMs=100|additionalWaitAfterFinalReleaseMs=1500"
                + "|descriptor=" + DESCRIPTOR_SHA256 + "|speed=3|host=SM-X926B:36|frontend=MANUAL_PLP0_K36"
                + "|bootstrap=ORIGINAL_USB_DEVICE_CONNECTION_APP_BOUNDARIES132_DRAIN_SAME_EPOCH"
                + "|drive=54,54|stream=8A:0A00:UNCHANGED|urb=8x4096|retry=0|maxReloadPerPhysicalEpoch=1";
    }
    public static String b5Transcript(Tbs5530FrontendExperiment experiment, String model, int api) {
        if (experiment == null || !experiment.isB5() || experiment.automatic()
                || !("GM1900".equals(model) && api == 30 || "SM-X926B".equals(model) && api == 36))
            throw new IllegalArgumentException("Exact B5 reload host and manual-first frontend required");
        return "B5_RELOAD_AFTER_SET_CONFIG|firmware=" + Tbs5530FirmwareValidation.SHA256
                + "|bytes=8192|TBS_SOURCE_DUAL_RESET|132|after=OS_PERMISSION,CLAIM|before=80,90,91,8A"
                + "|planDelayMs=100|additionalWaitAfterFinalReleaseMs=1500|descriptor=" + DESCRIPTOR_SHA256
                + "|speed=3|host=" + model + ":" + api + "|frontend=" + experiment.name()
                + "|bootstrap=ORIGINAL_USB_DEVICE_CONNECTION_APP_BOUNDARIES132_DRAIN_SAME_EPOCH"
                + "|drive=54,54|stream=8A:0A00:UNCHANGED|urb=16x16384|retry=0|maxReloadPerPhysicalEpoch=1";
    }
    public static String b5PlanSha256(Tbs5530FrontendExperiment experiment, String model, int api) {
        return Tbs5530AutonomousPlans.hash(b5Transcript(experiment, model, api));
    }
    public static String planSha256() { return Tbs5530AutonomousPlans.hash(transcript()); }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
    /** Broadcast latches even if enumeration later reuses the same Android path/id. */
    public synchronized void reenumerated() {
        detachedAt = clock.nanos(); detached = true;
        if (state == State.COMPLETE) state = State.RELOAD_CAUSED_REENUMERATION;
    }
    public void requireComplete() throws IOException {
        if (detached || state != State.COMPLETE) throw new IOException("Reload completion revoked by attachment event");
    }
    public boolean window() { return consumed && state != State.COMPLETE && state != State.BLOCKED; }
    private void current() throws IOException {
        if (detached) throw new IOException("RELOAD_CAUSED_REENUMERATION");
        guard.require(); ledger.require();
    }
    void execute() throws IOException {
        synchronized (this) { if (once) throw new IOException("Reload cannot be retried"); once = true; }
        try {
            current(); ledger.consume(epochKey); consumed = true; state = State.CONSUMED; current();
            for (int i = 0; i < 132; i++) {
                current(); state = State.TRANSFERRING;
                Tbs5530FirmwarePlan.Step step = steps.get(i);
                attempted = i + 1; transferActive = true;
                int result = port.write(step);
                Boundary b = port.boundary();
                if (b == null || b.ordinal != attempted || b.value != step.getValue() || b.length != step.getPayload().length
                        || b.result != result || b.errno != 0 || b.beforeNanos < 0 || b.afterNanos < b.beforeNanos
                        || result != step.getPayload().length)
                    throw new IOException("Reload native boundary incomplete or non-full result");
                current(); transferActive = false; completed++;
                if (completed == 132) finalRelease = b.afterNanos;
            }
            state = State.WAITING;
            // Preserve the source delay independently; it is not a substitute for a Windows B7/EEPROM operation.
            clock.pause(steps.get(132).getDelayMillis()); current();
            long waitStarted = clock.nanos();
            while (true) {
                current(); long elapsed = clock.nanos() - waitStarted;
                if (elapsed < 0 || elapsed > 5000000000L) throw new IOException("Reload wait clock/budget invalid");
                if (elapsed >= WAIT_MS * 1000000L) break;
                clock.pause((int)Math.min(25, (WAIT_MS * 1000000L - elapsed + 999999L) / 1000000L));
            }
            current();
            synchronized (this) {
                if (detached) throw new IOException("RELOAD_CAUSED_REENUMERATION");
                waitEnded = clock.nanos(); state = State.COMPLETE;
            }
        } catch (IOException | RuntimeException error) {
            state = transferActive ? State.UNCERTAIN : detached && consumed ? State.RELOAD_CAUSED_REENUMERATION
                    : attempted > 0 ? State.UNCERTAIN : State.BLOCKED;
            if (!cleaned) { cleaned = true; port.cleanup(); }
            throw error;
        }
    }
}
