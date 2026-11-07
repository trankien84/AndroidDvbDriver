// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Debug execution only. Authorization is live, external to firmware validation, and never inferred. */
public final class Tbs5530OneShotBootstrap {
    public enum State { PREPARED, ACQUIRING, LOADING, TRANSITIONING, OBSERVING,
        OPERATIONAL_CANDIDATE, FAULTED, UNCERTAIN }
    public interface Guard {
        void requireCurrent() throws IOException;
        default void requireWrite(int ordinal) throws IOException { requireCurrent(); }
        default void requireNative(int ordinal) throws IOException { requireCurrent(); }
        default void requireStartBudget() throws IOException { requireCurrent(); }
        default void requireDispatch(int ordinal) throws IOException { }
        default String runEpoch() { return null; }
    }
    public interface AttemptStore { void consume() throws IOException; }
    interface Delay { void await(int millis) throws IOException; }
    interface Descriptors { byte[] read(long epoch) throws IOException; }
    interface RawAudit { void save(String phase, byte[] bytes) throws IOException; }
    public static final class Snapshot {
        public final State state;
        public final int attempted, completed, bytes;
        public final long epoch, durationMillis;
        public final boolean pending;
        public final String failure, cleanupFailure;
        public final Tbs5530BootstrapTelemetry.Snapshot boundaries;
        public final List<String> transfers, failureChain;
        public final Tbs5530RawDescriptors.Result before, after;
        Snapshot(Tbs5530OneShotBootstrap runner) {
            state = runner.state; attempted = runner.attempted; completed = runner.completed;
            bytes = runner.bytes; epoch = runner.epoch; pending = runner.pending;
            durationMillis = runner.started == 0 ? 0 : ((runner.ended == 0 ? System.nanoTime() : runner.ended) - runner.started) / 1000000;
            failure = runner.failure; cleanupFailure = runner.cleanupFailure;
            boundaries = runner.telemetry.snapshot();
            failureChain = Tbs5530BootstrapStop.sanitizedChain(runner.stopCause);
            transfers = Collections.unmodifiableList(new ArrayList<>(runner.transfers));
            before = runner.before; after = runner.after;
        }
    }
    private final Tbs5530Session session;
    private final Tbs5530UsbBinding binding;
    private final Tbs5530UsbBinding.Facade facade;
    private final Tbs5530UsbBinding.Device device;
    private final Tbs5530Fx2ProtocolPlan plan;
    private final Guard guard;
    private final AttemptStore store;
    private final Delay delay;
    private final Descriptors descriptors;
    private final List<String> transfers = new ArrayList<>();
    private State state = State.PREPARED;
    private boolean startedOnce, pending;
    private int attempted, completed, bytes;
    private long epoch, started, ended;
    private String failure = "NONE", cleanupFailure = "NONE";
    private Throwable stopCause;
    private Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry();
    private Tbs5530RawDescriptors.Result before, after;
    private RawAudit rawAudit = (phase, bytes) -> { };

    void rawAudit(RawAudit audit) { rawAudit = audit; }
    private Tbs5530RawDescriptors.Result readAndAudit(String phase) throws IOException {
        byte[] bytes = descriptors.read(epoch);
        Tbs5530RawDescriptors.Result result = Tbs5530RawDescriptors.parse(bytes);
        synchronized (this) { if ("before".equals(phase)) before = result; else after = result; }
        if (bytes != null && bytes.length <= Tbs5530RawDescriptors.MAX_BYTES) {
            try { rawAudit.save(phase, bytes); }
            catch (IOException | RuntimeException error) {
                throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.AUDIT_FAILED,"Raw descriptor audit failed",error);
            }
        }
        return result;
    }

    void telemetry(Tbs5530BootstrapTelemetry telemetry) { this.telemetry = telemetry; }
    Tbs5530BootstrapTelemetry telemetry() { return telemetry; }
    synchronized Throwable stopCause() { return stopCause; }

    Tbs5530OneShotBootstrap(Tbs5530Session session, Tbs5530UsbBinding binding,
                           Tbs5530UsbBinding.Facade facade, Tbs5530UsbBinding.Device device,
                           Tbs5530Fx2ProtocolPlan plan, Guard guard, AttemptStore store,
                           Delay delay, Descriptors descriptors) {
        if (session == null || binding == null || facade == null || device == null || plan == null
                || guard == null || store == null || delay == null || descriptors == null
                || plan.getId() != Tbs5530Fx2ProtocolPlan.Id.TBS_SOURCE_DUAL_RESET
                || plan.getSteps().size() != 133) throw new IllegalArgumentException("Exact dual-reset inputs required");
        this.session = session; this.binding = binding; this.facade = facade; this.device = device;
        this.plan = plan; this.guard = guard; this.store = store; this.delay = delay; this.descriptors = descriptors;
    }

    public static Tbs5530FirmwarePlan readFirmware(InputStream input, String source, String entry,
                                                 Tbs5530FirmwareValidation.Format format) throws IOException {
        if (input == null) throw new IOException("Missing selected file");
        byte[] image = new byte[Tbs5530FirmwareValidation.SIZE];
        int at = 0;
        while (at < image.length) {
            int n = input.read(image, at, image.length - at);
            if (n <= 0) throw new IOException("Incomplete selected image");
            at += n;
        }
        if (input.read() != -1) throw new IOException("Trailing image bytes");
        return Tbs5530FirmwareValidation.validate(image, source, entry, format);
    }

    synchronized void queued() { pending = true; }

    void acquire() throws IOException {
        synchronized (this) {
            if (startedOnce) throw new IOException("Attempt cannot be resumed");
            startedOnce = true; pending = true; started = System.nanoTime(); state = State.ACQUIRING;
        }
        try {
            preflight();
            binding.startBootstrap(device);
            epoch = binding.bootstrapEpoch();
        } catch (IOException | RuntimeException error) { stop(error); throw error; }
    }

    private void preflight() throws IOException {
        guard.requireCurrent();
        List<Tbs5530UsbBinding.Device> devices = facade.devices();
        int matches = 0;
        for (Tbs5530UsbBinding.Device d : devices) if (d.vendorId == 0x734c && d.productId == 0x5530) matches++;
        if (matches != 1 || !devices.contains(device) || !facade.hasPermission(device)) {
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.USB_DETACHED, "Attachment or permission changed");
        }
        if (session.isCleanupPending() || session.getCleanupFailure() != null) {
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.CLEANUP_FAILED, "Owner cleanup not complete", session.getCleanupFailure());
        }
    }

    /** Called by the existing owner's worker, after binding acquisition jobs have drained. */
    void execute() throws IOException {
        synchronized (this) {
            if (state != State.ACQUIRING || binding.isPending()) throw new IOException("Owner not acquired");
            state = State.LOADING;
        }
        final long token = session.getGeneration();
        try {
            preflight();
            before = readAndAudit("before");
            requireIdentity(before);
            if (Tbs5530Fx2Fingerprint.assess(before).candidate != Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE) {
                throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.HASH_PLAN_MISMATCH,
                        "Exact default FX2 fingerprint required; no reupload");
            }
            Tbs5530ControlIo io = binding.bootstrapControlIo(epoch);
            preflight();
            guard.requireStartBudget();
            store.consume();
            try {
                session.transaction(token, () -> {
                    session.beginFirmwareTransition(token);
                    try {
                        for (Tbs5530FirmwarePlan.Step step : plan.getSteps()) {
                            if (step.getKind() == Tbs5530FirmwarePlan.Kind.DELAY) break;
                            preflight(); session.requireUsable(token);
                            byte[] data = step.getPayload();
                            synchronized (this) { attempted++; }
                            telemetry.planned(attempted);
                            long now = System.nanoTime();
                            try {
                                int n = io.controlTransfer(step.getRequestType(), step.getRequest(), step.getValue(),
                                        step.getIndex(), data, 0, data.length, step.getTimeoutMillis());
                                if (n != data.length) throw new IOException("Incomplete write");
                                synchronized (this) { completed++; bytes += n; }
                                record(step, "FULL_LENGTH", now);
                            } catch (IOException | RuntimeException error) {
                                Tbs5530BootstrapTelemetry.Snapshot boundary = telemetry.snapshot();
                                boolean finalDetached = expectedFinalReleaseDetach(error);
                                if (finalDetached) synchronized (this) {
                                    completed = boundary.fullLengthCompleted; bytes = boundary.payloadBytesFullLength;
                                }
                                String status = finalDetached ? "FULL_LENGTH_FINAL_RELEASE_ATTACHMENT_CHANGED"
                                        : boundary.nativeCallStarted > boundary.nativeCallCompleted
                                        ? Tbs5530BootstrapStop.Kind.UNKNOWN_IN_FLIGHT.name()
                                        : Tbs5530BootstrapStop.kindOf(error).name();
                                record(step, status, now); throw error;
                            }
                            preflight(); session.requireUsable(token);
                        }
                        session.firmwareUploaded(token);
                    } catch (IOException | RuntimeException error) {
                        session.failed(token, error);
                        throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                                "One-shot stopped; inspect typed cause", error);
                    }
                });
            } catch (IOException | RuntimeException error) {
                if (!expectedFinalReleaseDetach(error)) throw error;
                telemetry.transitionDetached();
            }
            synchronized (this) { state = State.TRANSITIONING; }
            delay.await(100);
            guard.requireCurrent();
        } catch (IOException | RuntimeException error) { stop(error); throw error; }
    }

    private boolean expectedFinalReleaseDetach(Throwable error) {
        Tbs5530BootstrapTelemetry.Snapshot s = telemetry.snapshot();
        if (s.fullLengthCompleted != 132 || s.nativeCallStarted != 132 || s.nativeCallCompleted != 132) return false;
        if (Tbs5530BootstrapStop.sanitizedChain(error).contains(Tbs5530BootstrapStop.Kind.AUDIT_FAILED.name())) return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof Tbs5530UsbBinding.AttachmentChangedException) return true;
            if (cause instanceof Tbs5530BootstrapStop) {
                return ((Tbs5530BootstrapStop) cause).kind == Tbs5530BootstrapStop.Kind.USB_DETACHED;
            }
        }
        return session.getState() == Tbs5530Session.State.DISCONNECTED;
    }
    private synchronized void record(Tbs5530FirmwarePlan.Step step, String status, long start) {
        transfers.add("ordinal=" + attempted + " type=64 request=160 index=0 value=" + step.getValue()
                + " length=" + step.getPayload().length + " timeout_ms=" + step.getTimeoutMillis()
                + " status=" + status + " duration_us=" + (System.nanoTime() - start) / 1000);
    }

    /** Dispose the old lease before acquiring a fresh read-only observation epoch. */
    void releaseUpload() throws IOException {
        try {
            binding.cancelBootstrap(epoch);
            session.drainCleanup();
            if (session.getCleanupFailure() != null) throw session.getCleanupFailure();
        } catch (IOException | RuntimeException error) {
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.CLEANUP_FAILED, "Upload lease cleanup failed", error);
        }
    }

    void acquireObservation() throws IOException {
        guard.requireCurrent();
        if (binding.isPending() || session.isCleanupPending()) throw new IOException("Old lease is pending");
        List<Tbs5530UsbBinding.Device> devices = facade.devices();
        Tbs5530UsbBinding.Device selected = null;
        for (Tbs5530UsbBinding.Device d : devices) if (d.vendorId == 0x734c && d.productId == 0x5530) {
            if (selected != null) throw new IOException("Ambiguous observation attachment");
            selected = d;
        }
        if (selected == null || !facade.hasPermission(selected)) throw new IOException("Observation permission/attachment unavailable");
        synchronized (this) { state = State.OBSERVING; }
        binding.startBootstrap(selected);
        epoch = binding.bootstrapEpoch();
    }

    void observe() throws IOException {
        try {
            guard.requireCurrent();
            after = readAndAudit("after");
            requireIdentity(after);
            if (Tbs5530Fx2Fingerprint.assess(after).candidate != Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE) {
                throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.OBSERVATION_FAILED, "Operational descriptor not observed");
            }
            synchronized (this) { state = State.OPERATIONAL_CANDIDATE; }
        } catch (IOException | RuntimeException error) { stop(error); throw error; }
    }

    private static void requireIdentity(Tbs5530RawDescriptors.Result raw) throws IOException {
        if (!"COMPLETE_RETURNED_TREE".equals(raw.status) || raw.entries.isEmpty()
                || !Integer.valueOf(0x734c).equals(raw.entries.get(0).fields.get("vid"))
                || !Integer.valueOf(0x5530).equals(raw.entries.get(0).fields.get("pid"))) throw new Tbs5530BootstrapStop(
                        Tbs5530BootstrapStop.Kind.HASH_PLAN_MISMATCH, "Raw identity mismatch");
    }

    synchronized void revoked(Throwable reason) {
        if (stopCause == null) { stop(reason); return; }
        boolean untyped = true;
        for (Throwable cause = stopCause; cause != null; cause = cause.getCause()) {
            if (cause instanceof Tbs5530BootstrapStop) { untyped = false; break; }
        }
        if (untyped && reason instanceof Tbs5530BootstrapStop) {
            failure = Tbs5530BootstrapStop.kindOf(reason).name();
            try { telemetry.stopped(attempted, Tbs5530BootstrapStop.kindOf(reason)); }
            catch (IOException audit) { stopCause.addSuppressed(audit); }
        }
        if (stopCause != reason) stopCause.addSuppressed(reason);
    }
    void stop(Throwable error) {
        synchronized (this) {
            Tbs5530BootstrapTelemetry.Snapshot boundary = telemetry.snapshot();
            state = boundary.nativeCallStarted == 0 ? State.FAULTED : State.UNCERTAIN;
            Tbs5530BootstrapStop.Kind kind = boundary.nativeCallStarted > boundary.nativeCallCompleted
                    ? Tbs5530BootstrapStop.Kind.UNKNOWN_IN_FLIGHT : Tbs5530BootstrapStop.kindOf(error);
            if (kind == Tbs5530BootstrapStop.Kind.CLEANUP_FAILED) cleanupFailure = kind.name();
            if (stopCause == null) { stopCause = error; failure = kind.name(); }
            else if (stopCause != error) stopCause.addSuppressed(error);
            try { telemetry.stopped(attempted, kind); }
            catch (IOException audit) { if (audit != stopCause) stopCause.addSuppressed(audit); }
        }
    }
    void finished() { synchronized (this) { pending = false; ended = System.nanoTime(); } }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
}
