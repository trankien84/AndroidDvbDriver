// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Real Android EP0 adapter, absent from release. Uses the existing binding/session/worker owner. */
public final class Tbs5530AndroidBootstrap implements AutoCloseable {
    private final Tbs5530AndroidRuntime runtime;
    private final Tbs5530Session session = new Tbs5530Session();
    private final Tbs5530UsbBinding binding;
    private final Port port;
    private final Tbs5530OneShotBootstrap runner;
    private final AtomicBoolean revoked = new AtomicBoolean(), started = new AtomicBoolean(), closing = new AtomicBoolean();
    private final Tbs5530OneShotBootstrap.Guard authorization;
    private final long preparedAt = android.os.SystemClock.elapsedRealtime();
    private Tbs5530UsbBinding.Cancellation watchdog;
    private final boolean externalObservation;
    /** Opaque receipt for the original Android API path; never claims kernel syscall observation. */
    public static final class CleanProof {
        public static final String EVIDENCE = "APP_OBSERVED_USB_DEVICE_CONNECTION_CONTROL_TRANSFER";
        private final Tbs5530OneShotBootstrap.Snapshot snapshot;
        private final String physicalEpochKey;
        private CleanProof(Tbs5530OneShotBootstrap.Snapshot snapshot, String physicalEpochKey) {
            this.snapshot = snapshot; this.physicalEpochKey = physicalEpochKey;
        }
        void require(String runId, String epochKey) throws IOException {
            if (epochKey == null || !epochKey.matches("[a-f0-9]{64}") || !epochKey.equals(physicalEpochKey)
                    || snapshot.pending || !snapshot.boundaries.runEpoch.equals(runId)
                    || !Tbs5530AutonomousPolicy.bootstrapTransitionKnown(snapshot.state, snapshot.completed,
                    snapshot.failure, snapshot.cleanupFailure, snapshot.boundaries))
                throw new IOException("Foreign physical epoch or incomplete original bootstrap proof");
        }
    }
    private static CleanProof proveOriginal(Port port, Tbs5530OneShotBootstrap.Snapshot snapshot,
                                            boolean cleanupPending, String physicalEpochKey) throws IOException {
        if (cleanupPending || port.open.get() || !port.writesClosed.get() || port.ordinal != 132
                || port.plan.getSteps().size() != 133 || snapshot.boundaries == null
                || !port.boundaries().runEpoch.equals(snapshot.boundaries.runEpoch))
            throw new IOException("Original bootstrap full132/API boundaries/drain not proven");
        CleanProof proof = new CleanProof(snapshot, physicalEpochKey);
        proof.require(snapshot.boundaries.runEpoch, physicalEpochKey); return proof;
    }
    public CleanProof cleanProof() throws IOException {
        requireAuthorization();
        return proveOriginal(port, runner.snapshot(), cleanupPending(), authorization.physicalEpochKey());
    }

    public void controlMirror(Tbs5530ControlMirror mirror) {
        if (started.get()) throw new IllegalStateException("Control mirror must be configured before bootstrap");
        port.controlMirror = java.util.Objects.requireNonNull(mirror);
        port.mirrorSession = session;
    }

    public Tbs5530AndroidBootstrap(Context context, Tbs5530AndroidRuntime runtime,
                                  Tbs5530UsbBinding.Device device, Tbs5530FirmwarePlan validated,
                                  Tbs5530OneShotBootstrap.Guard authorization,
                                  Tbs5530OneShotBootstrap.AttemptStore store) throws IOException {
        this(context, runtime, device, validated, authorization, store, false);
    }
    public Tbs5530AndroidBootstrap(Context context, Tbs5530AndroidRuntime runtime,
                                  Tbs5530UsbBinding.Device device, Tbs5530FirmwarePlan validated,
                                  Tbs5530OneShotBootstrap.Guard authorization,
                                  Tbs5530OneShotBootstrap.AttemptStore store, boolean externalObservation) throws IOException {
        this.externalObservation = externalObservation;
        this.runtime = runtime; this.authorization = authorization;
        // Public callers cannot substitute a synthetic policy or another plan on the Android path.
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        List<Tbs5530FirmwarePlan.Step> source = validated.getSteps();
        for (int i = 2; i < source.size() - 3; i++) image.write(source.get(i).getPayload());
        Tbs5530FirmwarePlan locked = Tbs5530FirmwareValidation.validate(image.toByteArray(),
                Tbs5530FirmwareValidation.SOURCE, Tbs5530FirmwareValidation.ENTRY,
                Tbs5530FirmwareValidation.Format.RAW_LINEAR);
        Tbs5530Fx2ProtocolPlan plan = Tbs5530Fx2ProtocolPlan.describe(locked,
                Tbs5530Fx2ProtocolPlan.Id.TBS_SOURCE_DUAL_RESET.name());
        Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry(new Tbs5530NativeAudit(context.getFilesDir()),
                android.os.SystemClock::elapsedRealtimeNanos, "ANDROID_ELAPSED_REALTIME_NS", authorization.runEpoch());
        port = new Port(context, plan, new Tbs5530OneShotBootstrap.Guard() {
            public void requireCurrent() throws IOException { requireAuthorization(); }
            public void requireWrite(int ordinal) throws IOException {
                requireAuthorization(); authorization.requireWrite(ordinal); requireAuthorization();
            }
            public void requireNative(int ordinal) throws IOException {
                requireAuthorization(); authorization.requireNative(ordinal); requireAuthorization();
            }
            public void requireDispatch(int ordinal) throws IOException { authorization.requireDispatch(ordinal); }
        });
        port.telemetry = telemetry;
        binding = new Tbs5530UsbBinding(session, port, runtime, runtime, error -> {
            revocation.compareAndSet(null, error);
            revoked.set(true);
        }, 30000, 10000);
        runner = new Tbs5530OneShotBootstrap(session, binding, port, device, plan,
                new Tbs5530OneShotBootstrap.Guard() {
                    public void requireCurrent() throws IOException { requireAuthorization(); port.requireUploadEpoch(); }
                    public void requireStartBudget() throws IOException { requireCurrent(); authorization.requireStartBudget(); }
                }, store, millis -> {
                    try { Thread.sleep(millis); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("Delay interrupted"); }
                }, binding::bootstrapRawDescriptors);
        runner.telemetry(telemetry);
        runner.rawAudit((phase, bytes) -> Tbs5530DurableFile.write(new java.io.File(context.getFilesDir(),
                "phase29-raw-" + telemetry.snapshot().runEpoch + "-" + phase + ".bin"), bytes));
    }

    private final java.util.concurrent.atomic.AtomicReference<Throwable> revocation = new java.util.concurrent.atomic.AtomicReference<>();
    private void requireAuthorization() throws IOException {
        if (revoked.get()) {
            Throwable reason = revocation.get();
            if (reason instanceof Tbs5530BootstrapStop) throw (Tbs5530BootstrapStop) reason;
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.AUTHORIZATION_REVOKED, "Authorization revoked", reason);
        }
        if (android.os.SystemClock.elapsedRealtime() - preparedAt > 300000) {
            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.PERMIT_EXPIRED, "Prepared session expired");
        }
        authorization.requireCurrent();
    }

    public void startFromUserConfirmation() {
        if (!started.compareAndSet(false, true)) return;
        runner.queued();
        runtime.execute(() -> {
            try {
                runner.acquire();
                watch();
                schedule(this::awaitUploadOwner);
            } catch (IOException | RuntimeException error) { fail(error); }
        });
    }
    private void schedule(Runnable action) { runtime.schedule(() -> runtime.execute(action), 25); }
    private void watch() {
        watchdog = runtime.schedule(() -> {
            try { requireAuthorization(); }
            catch (IOException | RuntimeException error) {
                revocation.compareAndSet(null, error); revoked.set(true); binding.cancel();
            }
            if (!revoked.get() && runner.snapshot().pending) watch();
        }, 250);
    }
    private void awaitUploadOwner() {
        try {
            requireAuthorization();
            if (binding.isPending()) { schedule(this::awaitUploadOwner); return; }
            runner.execute();
            port.finishWrites();
            runner.releaseUpload();
            if (externalObservation) { finish(); return; }
            schedule(this::openObservation);
        } catch (IOException | RuntimeException error) { fail(error); }
    }
    private void openObservation() {
        try {
            requireAuthorization();
            if (binding.isPending()) { schedule(this::openObservation); return; }
            runner.acquireObservation();
            schedule(this::awaitObservationOwner);
        } catch (IOException | RuntimeException error) { fail(error); }
    }
    private void awaitObservationOwner() {
        try {
            requireAuthorization();
            if (binding.isPending()) { schedule(this::awaitObservationOwner); return; }
            runner.observe();
            finish();
        } catch (IOException | RuntimeException error) { fail(error); }
    }
    private void fail(Throwable error) {
        Throwable reason = revocation.get();
        if (reason != null && reason != error) runner.revoked(reason);
        runner.stop(error); finish();
    }
    private void finish() {
        port.finishWrites();
        if (watchdog != null) watchdog.cancel();
        try { binding.close(); session.drainCleanup(); }
        catch (IOException | RuntimeException error) { runner.stop(new Tbs5530BootstrapStop(
                Tbs5530BootstrapStop.Kind.CLEANUP_FAILED, "Binding cleanup failed", error)); }
        if (session.getCleanupFailure() != null) runner.stop(new Tbs5530BootstrapStop(
                Tbs5530BootstrapStop.Kind.CLEANUP_FAILED, "Deferred cleanup failed", session.getCleanupFailure()));
        runner.finished();
    }
    public Tbs5530OneShotBootstrap.Snapshot snapshot() { return runner.snapshot(); }
    public long attachmentEvents() { return port.events.get(); }
    public List<String> apiResults() { return port.results(); }
    public boolean cleanupPending() { return binding.isPending() || session.isCleanupPending() || port.open.get(); }
    @Override public void close() { revoke(Tbs5530BootstrapStop.Kind.USER_CANCELLED); }
    public void revoke(Tbs5530BootstrapStop.Kind kind) {
        if (started.get() && !runner.snapshot().pending || !closing.compareAndSet(false, true)) return;
        Tbs5530BootstrapStop reason = new Tbs5530BootstrapStop(kind, "Debug owner revoked");
        revocation.compareAndSet(null, reason);
        revoked.set(true);
        port.finishWrites();
        binding.cancel();
        runtime.execute(() -> fail(reason));
    }

    static final class Port implements Tbs5530UsbBinding.Facade {
        private final Tbs5530AndroidUsbFacade.Manager manager;
        private final Tbs5530AndroidUsbFacade metadata;
        private final Tbs5530Fx2ProtocolPlan plan;
        private final Tbs5530OneShotBootstrap.Guard guard;
        private final AtomicLong events = new AtomicLong();
        private final AtomicBoolean open = new AtomicBoolean(), writesClosed = new AtomicBoolean();
        private final List<String> results = new ArrayList<>();
        private volatile boolean listening;
        private volatile int ordinal;
        private Tbs5530ControlMirror controlMirror;
        private Tbs5530Session mirrorSession;
        private Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry();
        Port(Context context, Tbs5530Fx2ProtocolPlan plan, Tbs5530OneShotBootstrap.Guard guard) {
            this(context, new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext()), plan, guard);
        }
        Port(Context context, Tbs5530AndroidUsbFacade.Manager manager, Tbs5530Fx2ProtocolPlan plan,
             Tbs5530OneShotBootstrap.Guard guard) {
            this.manager = manager; this.metadata = new Tbs5530AndroidUsbFacade(context, manager);
            this.plan = plan; this.guard = guard;
        }
        void requireUploadEpoch() throws IOException {
            if (!listening || !writesClosed.get() && ordinal < 132 && events.get() != 0) {
                throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.USB_DETACHED, "Observer/attachment changed");
            }
        }
        void finishWrites() { writesClosed.set(true); }
        synchronized List<String> results() { return new ArrayList<>(results); }
        void telemetry(Tbs5530BootstrapTelemetry value) { telemetry = value; }
        Tbs5530BootstrapTelemetry.Snapshot boundaries() { return telemetry.snapshot(); }
        public List<Tbs5530UsbBinding.Device> devices() throws IOException { return metadata.devices(); }
        public boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException { return metadata.hasPermission(device); }
        public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device d, String c,
                                                               Tbs5530UsbBinding.PermissionCallback callback) throws IOException {
            throw new IOException("Grant permission explicitly before preparing the attempt");
        }
        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events listener) throws IOException {
            Tbs5530UsbBinding.Registration registration = metadata.listen(new Tbs5530UsbBinding.Events() {
                private boolean target(Tbs5530UsbBinding.Device d) { return d.vendorId == 0x734c && d.productId == 0x5530; }
                public void attached(Tbs5530UsbBinding.Device d) { if (target(d)) events.incrementAndGet(); listener.attached(d); }
                public void detached(Tbs5530UsbBinding.Device d) { if (target(d)) events.incrementAndGet(); listener.detached(d); }
            });
            listening = true;
            return () -> { listening = false; registration.close(); };
        }
        public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device d) throws IOException {
            throw new IOException("No operational/streaming acquisition in bootstrap");
        }
        public Tbs5530LifecycleCoordinator.Connection openBootstrap(Tbs5530UsbBinding.Device expected) throws IOException {
            worker(); guard.requireCurrent(); requireUploadEpoch();
            if (!open.compareAndSet(false, true)) throw new IOException("Another lease is open");
            Tbs5530AndroidUsbFacade.RawConnection raw = null;
            try {
                int count = 0;
                List<Tbs5530UsbBinding.Device> devices = manager.devices();
                for (Tbs5530UsbBinding.Device d : devices) if (d.vendorId == 0x734c && d.productId == 0x5530) count++;
                if (count != 1 || !devices.contains(expected) || !manager.hasPermission(expected)) throw new IOException("Attachment mismatch");
                raw = manager.open(expected);
                if (raw == null) throw new IOException("USB open failed");
                return connection(raw, Thread.currentThread(), expected);
            } catch (IOException | RuntimeException error) {
                if (raw != null) raw.close();
                open.set(false); throw error;
            }
        }
        private void requireDevice(Tbs5530UsbBinding.Device expected) throws IOException {
            List<Tbs5530UsbBinding.Device> devices = manager.devices();
            int count = 0;
            for (Tbs5530UsbBinding.Device device : devices) if (device.vendorId == 0x734c && device.productId == 0x5530) count++;
            if (count != 1 || !devices.contains(expected) || !manager.hasPermission(expected)) throw new Tbs5530BootstrapStop(
                    Tbs5530BootstrapStop.Kind.USB_DETACHED, "Live device or permission changed");
        }
        private Tbs5530LifecycleCoordinator.Connection connection(Tbs5530AndroidUsbFacade.RawConnection raw, Thread owner,
                                                                 Tbs5530UsbBinding.Device expected) {
            return new Tbs5530LifecycleCoordinator.Connection() {
                private boolean closed;
                public void requireControlWorker() throws IOException {
                    worker(); if (Thread.currentThread() != owner || closed) throw new IOException("Wrong worker/closed lease");
                }
                public byte[] rawDescriptors() throws IOException { requireControlWorker(); return raw.rawDescriptors(); }
                public int controlTransfer(int type, int request, int value, int index, byte[] bytes,
                                           int offset, int length, int timeout) throws IOException {
                    requireControlWorker();
                    int current = ordinal + 1;
                    boolean nativeStarted = false, nativeEnded = false;
                    try {
                        guard.requireCurrent(); requireUploadEpoch();
                        if (writesClosed.get() || ordinal >= 132) throw new Tbs5530BootstrapStop(
                                Tbs5530BootstrapStop.Kind.AUTHORIZATION_REVOKED, "No further writes authorized");
                        Tbs5530FirmwarePlan.Step step = plan.getSteps().get(ordinal);
                        if (type != 0x40 || request != 0xa0 || index != 0 || value != step.getValue() || offset != 0
                                || length != step.getPayload().length || timeout != 2000 || !Arrays.equals(bytes, step.getPayload())) {
                            throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.HASH_PLAN_MISMATCH, "Write differs from locked plan");
                        }
                        telemetry.guardRequested(current);
                        guard.requireWrite(current);
                        telemetry.guardAccepted(current);
                        telemetry.nativeIntent(current);
                        requireDevice(expected);
                        guard.requireNative(current); requireDevice(expected); requireUploadEpoch();
                        if (writesClosed.get()) throw new Tbs5530BootstrapStop(
                                Tbs5530BootstrapStop.Kind.AUTHORIZATION_REVOKED, "Write gate closed during acknowledgment");
                        long eventEpoch = events.get();
                        guard.requireDispatch(current);
                        ordinal = current;
                        telemetry.nativeStarted(current); nativeStarted = true;
                        int n = controlMirror == null ? raw.controlTransfer(type, request, value, index, bytes, offset, length, timeout)
                                : controlMirror.transfer("BOOTSTRAP", mirrorSession.getGeneration(), raw::controlTransfer,
                                        type, request, value, index, bytes, offset, length, timeout);
                        nativeEnded = true;
                        synchronized (Port.this) { results.add("ordinal=" + current + " returned=" + n + " requested=" + length); }
                        Tbs5530BootstrapStop resultFailure = n != length ? new Tbs5530BootstrapStop(n < 0
                                ? Tbs5530BootstrapStop.Kind.NATIVE_NEGATIVE : Tbs5530BootstrapStop.Kind.NATIVE_SHORT,
                                "Native result was not full length") : events.get() != eventEpoch
                                ? new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.USB_DETACHED, "Attachment changed in flight") : null;
                        try { telemetry.nativeReturned(current, value, length, n); }
                        catch (IOException audit) {
                            if (resultFailure == null) throw audit;
                            resultFailure.addSuppressed(audit);
                        }
                        if (resultFailure != null) throw resultFailure;
                        return n;
                    } catch (IOException | RuntimeException error) {
                        writesClosed.set(true);
                        if (nativeStarted && !nativeEnded) {
                            try { telemetry.nativeThrew(current, error); }
                            catch (IOException audit) { error.addSuppressed(audit); }
                            synchronized (Port.this) { results.add("ordinal=" + current + " returned=EXCEPTION"); }
                        }
                        if (error instanceof Tbs5530BootstrapStop) throw error;
                        if (!nativeStarted) throw new Tbs5530BootstrapStop(
                                Tbs5530BootstrapStop.Kind.AUTHORIZATION_REVOKED, "Write blocked before native call", error);
                        if (error instanceof IOException) throw new Tbs5530BootstrapStop(
                                Tbs5530BootstrapStop.Kind.JAVA_IO_EXCEPTION, "Native call threw IOException", error);
                        throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.NATIVE_EXCEPTION, "Native call threw RuntimeException", error);
                    }
                }
                public void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException { throw new IOException("EP0 must not claim"); }
                public void cancel() { }
                public void release() throws IOException { throw new IOException("No claimed interface"); }
                public void close() throws IOException {
                    requireControlWorker(); closed = true;
                    try { raw.close(); } finally { open.set(false); }
                }
            };
        }
        private static void worker() throws IOException {
            if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("USB requires owner worker");
        }
    }
}
