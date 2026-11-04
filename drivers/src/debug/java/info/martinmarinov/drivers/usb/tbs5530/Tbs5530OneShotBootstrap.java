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
    }
    public interface AttemptStore { void consume() throws IOException; }
    interface Delay { void await(int millis) throws IOException; }
    interface Descriptors { byte[] read(long epoch) throws IOException; }
    public static final class Snapshot {
        public final State state;
        public final int attempted, completed, bytes;
        public final long epoch, durationMillis;
        public final boolean pending;
        public final String failure;
        public final List<String> transfers;
        public final Tbs5530RawDescriptors.Result before, after;
        Snapshot(Tbs5530OneShotBootstrap runner) {
            state = runner.state; attempted = runner.attempted; completed = runner.completed;
            bytes = runner.bytes; epoch = runner.epoch; pending = runner.pending;
            durationMillis = runner.started == 0 ? 0 : ((runner.ended == 0 ? System.nanoTime() : runner.ended) - runner.started) / 1000000;
            failure = runner.failure; transfers = Collections.unmodifiableList(new ArrayList<>(runner.transfers));
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
    private String failure = "NONE";
    private Tbs5530RawDescriptors.Result before, after;

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
        if (matches != 1 || !devices.contains(device) || !facade.hasPermission(device)
                || session.isCleanupPending() || session.getCleanupFailure() != null) throw new IOException("Preflight mismatch");
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
            before = Tbs5530RawDescriptors.parse(descriptors.read(epoch));
            requireIdentity(before);
            if (Tbs5530Fx2Fingerprint.assess(before).candidate != Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE) {
                throw new IOException("Exact default FX2 fingerprint required; no reupload");
            }
            Tbs5530ControlIo io = binding.bootstrapControlIo(epoch);
            preflight();
            store.consume();
            session.transaction(token, () -> {
                session.beginFirmwareTransition(token);
                try {
                    for (Tbs5530FirmwarePlan.Step step : plan.getSteps()) {
                        if (step.getKind() == Tbs5530FirmwarePlan.Kind.DELAY) break;
                        preflight(); session.requireUsable(token);
                        byte[] data = step.getPayload();
                        synchronized (this) { attempted++; }
                        long now = System.nanoTime();
                        try {
                            int n = io.controlTransfer(step.getRequestType(), step.getRequest(), step.getValue(),
                                    step.getIndex(), data, 0, data.length, step.getTimeoutMillis());
                            if (n != data.length) throw new IOException("Incomplete write");
                            synchronized (this) { completed++; bytes += n; }
                            record(step, "FULL_LENGTH", now);
                        } catch (IOException | RuntimeException error) {
                            record(step, "FAILED_OR_UNKNOWN", now); throw error;
                        }
                        preflight(); session.requireUsable(token);
                    }
                    session.firmwareUploaded(token);
                } catch (IOException | RuntimeException error) {
                    session.failed(token, error);
                    throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                            "One-shot stopped; no corrective writes", error);
                }
            });
            synchronized (this) { state = State.TRANSITIONING; }
            delay.await(100);
            guard.requireCurrent();
        } catch (IOException | RuntimeException error) { stop(error); throw error; }
    }

    private synchronized void record(Tbs5530FirmwarePlan.Step step, String status, long start) {
        transfers.add("ordinal=" + attempted + " type=64 request=160 index=0 value=" + step.getValue()
                + " length=" + step.getPayload().length + " timeout_ms=" + step.getTimeoutMillis()
                + " status=" + status + " duration_us=" + (System.nanoTime() - start) / 1000);
    }

    /** Dispose the old lease before acquiring a fresh read-only observation epoch. */
    void releaseUpload() throws IOException {
        binding.cancelBootstrap(epoch);
        session.drainCleanup();
        if (session.getCleanupFailure() != null) throw session.getCleanupFailure();
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
            after = Tbs5530RawDescriptors.parse(descriptors.read(epoch));
            requireIdentity(after);
            if (Tbs5530Fx2Fingerprint.assess(after).candidate != Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE) {
                throw new IOException("Operational descriptor not observed");
            }
            synchronized (this) { state = State.OPERATIONAL_CANDIDATE; }
        } catch (IOException | RuntimeException error) { stop(error); throw error; }
    }

    private static void requireIdentity(Tbs5530RawDescriptors.Result raw) throws IOException {
        if (!"COMPLETE_RETURNED_TREE".equals(raw.status) || raw.entries.isEmpty()
                || !Integer.valueOf(0x734c).equals(raw.entries.get(0).fields.get("vid"))
                || !Integer.valueOf(0x5530).equals(raw.entries.get(0).fields.get("pid"))) throw new IOException("Raw identity mismatch");
    }

    void stop(Throwable error) {
        synchronized (this) {
            state = attempted == 0 ? State.FAULTED : State.UNCERTAIN;
            failure = error instanceof Tbs5530ControlIo.TransferException
                    ? ((Tbs5530ControlIo.TransferException) error).getKind().name() : "PREFLIGHT_OR_OBSERVATION_FAILURE";
        }
    }
    void finished() { synchronized (this) { pending = false; ended = System.nanoTime(); } }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
}
