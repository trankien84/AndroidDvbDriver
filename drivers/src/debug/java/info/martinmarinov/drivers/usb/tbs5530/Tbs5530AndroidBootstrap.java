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

    public Tbs5530AndroidBootstrap(Context context, Tbs5530AndroidRuntime runtime,
                                  Tbs5530UsbBinding.Device device, Tbs5530FirmwarePlan validated,
                                  Tbs5530OneShotBootstrap.Guard authorization,
                                  Tbs5530OneShotBootstrap.AttemptStore store) throws IOException {
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
        port = new Port(context, plan, new Tbs5530OneShotBootstrap.Guard() {
            public void requireCurrent() throws IOException { requireAuthorization(); }
            public void requireWrite(int ordinal) throws IOException {
                requireAuthorization(); authorization.requireWrite(ordinal); requireAuthorization();
            }
        });
        binding = new Tbs5530UsbBinding(session, port, runtime, runtime, error -> {
            revoked.set(true);
        }, 30000, 10000);
        runner = new Tbs5530OneShotBootstrap(session, binding, port, device, plan,
                () -> { requireAuthorization(); port.requireUploadEpoch(); }, store, millis -> {
                    try { Thread.sleep(millis); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("Delay interrupted"); }
                }, binding::bootstrapRawDescriptors);
    }

    private void requireAuthorization() throws IOException {
        if (revoked.get() || android.os.SystemClock.elapsedRealtime() - preparedAt > 300000) {
            throw new IOException("Authorization revoked or expired");
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
            catch (IOException | RuntimeException error) { revoked.set(true); binding.cancel(); }
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
    private void fail(Throwable error) { runner.stop(error); finish(); }
    private void finish() {
        port.finishWrites();
        if (watchdog != null) watchdog.cancel();
        try { binding.close(); session.drainCleanup(); }
        catch (IOException | RuntimeException error) { runner.stop(error); }
        if (session.getCleanupFailure() != null) runner.stop(session.getCleanupFailure());
        runner.finished();
    }
    public Tbs5530OneShotBootstrap.Snapshot snapshot() { return runner.snapshot(); }
    public long attachmentEvents() { return port.events.get(); }
    public List<String> apiResults() { return port.results(); }
    public boolean cleanupPending() { return binding.isPending() || session.isCleanupPending() || port.open.get(); }
    @Override public void close() {
        if (started.get() && !runner.snapshot().pending || !closing.compareAndSet(false, true)) return;
        revoked.set(true);
        binding.cancel();
        runtime.execute(() -> fail(new IOException("UI owner closed")));
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
        Port(Context context, Tbs5530Fx2ProtocolPlan plan, Tbs5530OneShotBootstrap.Guard guard) {
            this(context, new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext()), plan, guard);
        }
        Port(Context context, Tbs5530AndroidUsbFacade.Manager manager, Tbs5530Fx2ProtocolPlan plan,
             Tbs5530OneShotBootstrap.Guard guard) {
            this.manager = manager; this.metadata = new Tbs5530AndroidUsbFacade(context, manager);
            this.plan = plan; this.guard = guard;
        }
        void requireUploadEpoch() throws IOException {
            if (!listening || !writesClosed.get() && ordinal < 132 && events.get() != 0) throw new IOException("Observer/attachment changed");
        }
        void finishWrites() { writesClosed.set(true); }
        synchronized List<String> results() { return new ArrayList<>(results); }
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
                return connection(raw, Thread.currentThread());
            } catch (IOException | RuntimeException error) {
                if (raw != null) raw.close();
                open.set(false); throw error;
            }
        }
        private Tbs5530LifecycleCoordinator.Connection connection(Tbs5530AndroidUsbFacade.RawConnection raw, Thread owner) {
            return new Tbs5530LifecycleCoordinator.Connection() {
                private boolean closed;
                public void requireControlWorker() throws IOException {
                    worker(); if (Thread.currentThread() != owner || closed) throw new IOException("Wrong worker/closed lease");
                }
                public byte[] rawDescriptors() throws IOException { requireControlWorker(); return raw.rawDescriptors(); }
                public int controlTransfer(int type, int request, int value, int index, byte[] bytes,
                                           int offset, int length, int timeout) throws IOException {
                    requireControlWorker(); guard.requireCurrent(); requireUploadEpoch();
                    if (writesClosed.get() || ordinal >= 132) throw new IOException("No further writes authorized");
                    Tbs5530FirmwarePlan.Step step = plan.getSteps().get(ordinal);
                    if (type != 0x40 || request != 0xa0 || index != 0 || value != step.getValue() || offset != 0
                            || length != step.getPayload().length || timeout != 2000 || !Arrays.equals(bytes, step.getPayload())) {
                        throw new IOException("Write differs from locked plan");
                    }
                    guard.requireWrite(ordinal + 1);
                    requireUploadEpoch();
                    int current = ++ordinal;
                    try {
                        long eventEpoch = events.get();
                        int n = raw.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                        synchronized (Port.this) { results.add("ordinal=" + current + " returned=" + n + " requested=" + length); }
                        if (n != length || events.get() != eventEpoch) {
                            writesClosed.set(true);
                            if (events.get() != eventEpoch) throw new IOException("Attachment changed in flight");
                        }
                        return n;
                    } catch (RuntimeException error) {
                        writesClosed.set(true);
                        synchronized (Port.this) { results.add("ordinal=" + current + " returned=EXCEPTION"); }
                        throw error;
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
