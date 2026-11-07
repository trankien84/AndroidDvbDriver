// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Per-owner USB lifecycle binding; contains no Android framework or transfer implementation. */
public final class Tbs5530UsbBinding implements AutoCloseable {
    public static final class Device {
        public final String path;
        public final int id, vendorId, productId;
        public Device(String path, int id, int vendorId, int productId) {
            if (path == null || path.isEmpty() || id < 0 || vendorId < 0 || vendorId > 65535
                    || productId < 0 || productId > 65535) throw new IllegalArgumentException("Invalid USB device");
            this.path = path; this.id = id; this.vendorId = vendorId; this.productId = productId;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Device)) return false;
            Device d = (Device) other;
            return id == d.id && vendorId == d.vendorId && productId == d.productId && path.equals(d.path);
        }
        @Override public int hashCode() { return Objects.hash(path, id, vendorId, productId); }
    }
    public interface Registration extends AutoCloseable { @Override void close() throws IOException; }
    public interface PermissionCallback { void result(String correlation, Device device, Boolean granted); }
    public interface Events {
        void attached(Device device);
        void detached(Device device);
    }
    public interface Facade {
        List<Device> devices() throws IOException;
        boolean hasPermission(Device device) throws IOException;
        Registration requestPermission(Device device, String correlation, PermissionCallback callback) throws IOException;
        Registration listen(Events events) throws IOException;
        Tbs5530LifecycleCoordinator.Connection open(Device device) throws IOException;
        default Tbs5530LifecycleCoordinator.Connection openBootstrap(Device device) throws IOException {
            throw new IOException("Bootstrap EP0 connection is disabled");
        }
    }
    public interface Cancellation { void cancel(); }
    public interface Scheduler { Cancellation schedule(Runnable task, long delayMillis); }
    public interface Errors { void report(Throwable failure); }

    private final Tbs5530Session session;
    private final Facade facade;
    private final Executor worker;
    private final Scheduler scheduler;
    private final Errors errors;
    private final long permissionTimeout, acquisitionTimeout;
    private final Tbs5530LifecycleCoordinator coordinator;
    private final Registration events;
    private long epoch;
    private Attempt current;
    private boolean closed;
    private int jobs;

    private static final class Attempt {
        final Device device;
        final long epoch;
        final String correlation = UUID.randomUUID().toString();
        Tbs5530LifecycleCoordinator.Request request;
        Registration permission;
        Cancellation deadline;
        long deadlineId;
        boolean invalid, callbackConsumed, acquiring, permissionOnly, bootstrap;
        Attempt(Device device, long epoch) { this.device = device; this.epoch = epoch; }
    }

    public Tbs5530UsbBinding(Tbs5530Session session, Facade facade, Executor worker, Scheduler scheduler,
                            Errors errors, long permissionTimeoutMillis, long acquisitionTimeoutMillis) throws IOException {
        if (permissionTimeoutMillis <= 0 || acquisitionTimeoutMillis <= 0) throw new IllegalArgumentException("Positive deadlines required");
        this.session = Objects.requireNonNull(session); this.facade = Objects.requireNonNull(facade);
        this.worker = Objects.requireNonNull(worker); this.scheduler = Objects.requireNonNull(scheduler);
        this.errors = Objects.requireNonNull(errors);
        permissionTimeout = permissionTimeoutMillis; acquisitionTimeout = acquisitionTimeoutMillis;
        coordinator = new Tbs5530LifecycleCoordinator(session, this::requestPermission, this::open);
        events = facade.listen(new Events() {
            public void attached(Device device) { attachmentChanged(device); }
            public void detached(Device device) { detachEvent(device); }
        });
    }

    public void start(Device device) throws IOException { start(device, false); }

    /** Explicit metadata/permission preflight: even a valid grant must not acquire a connection. */
    public void requestPermissionOnly(Device device) throws IOException { start(device, true); }

    private void start(Device device, boolean permissionOnly) throws IOException {
        start(device, permissionOnly, false);
    }

    void startBootstrap(Device device) throws IOException { start(device, false, true); }

    private void start(Device device, boolean permissionOnly, boolean bootstrap) throws IOException {
        Objects.requireNonNull(device);
        if (bootstrap && (device.vendorId != 0x734c || device.productId != 0x5530)) {
            throw new IllegalArgumentException("Bootstrap requires TBS5530 identity");
        }
        Attempt attempt;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Binding closed");
            if (jobs != 0 || coordinator.isAcquiring() || session.isCleanupPending()
                    || current != null && !current.invalid) throw new IllegalStateException("Previous attachment is pending");
            if (epoch == Long.MAX_VALUE) throw new IllegalStateException("Attachment epochs exhausted");
            if (bootstrap && session.getCleanupFailure() != null) throw new IllegalStateException("Previous cleanup failed");
            attempt = new Attempt(device, ++epoch); attempt.permissionOnly = permissionOnly;
            attempt.bootstrap = bootstrap; current = attempt;
        }
        try {
            arm(attempt, permissionTimeout);
            submit(() -> begin(attempt));
        } catch (RuntimeException error) {
            synchronized (this) { attempt.invalid = true; }
            disarm(attempt);
            throw error;
        }
    }

    private void begin(Attempt attempt) throws IOException {
        if (!active(attempt)) return;
        if (!present(attempt.device)) { invalidate(attempt, true); return; }
        Tbs5530LifecycleCoordinator.AttachmentIdentity identity = new Tbs5530LifecycleCoordinator.AttachmentIdentity(
                attempt.device.path, attempt.device.id, attempt.device.vendorId, attempt.device.productId, attempt.epoch);
        coordinator.requestPermission(identity);
    }

    private void requestPermission(Tbs5530LifecycleCoordinator.Request request) throws IOException {
        Attempt attempt;
        synchronized (this) {
            attempt = current;
            if (attempt == null || attempt.invalid || closed || request.getIdentity().getAttachmentId() != attempt.epoch) {
                coordinator.invalidate(request, false); return;
            }
            attempt.request = request;
        }
        if (facade.hasPermission(attempt.device)) {
            callback(attempt.correlation, attempt.device, true); return;
        }
        Registration registration = facade.requestPermission(attempt.device, attempt.correlation, this::callback);
        synchronized (this) {
            if (active(attempt) && !attempt.callbackConsumed) { attempt.permission = registration; return; }
        }
        registration.close();
    }

    private void callback(String correlation, Device device, Boolean granted) {
        Attempt attempt;
        synchronized (this) {
            attempt = current;
            if (!active(attempt) || attempt.callbackConsumed || granted == null || device == null
                    || !attempt.correlation.equals(correlation) || !attempt.device.equals(device)) return;
            attempt.callbackConsumed = true;
        }
        submit(() -> accept(attempt, granted));
    }

    private void accept(Attempt attempt, boolean granted) throws IOException {
        clearPermission(attempt);
        if (!active(attempt)) return;
        if (!present(attempt.device)) { invalidate(attempt, true); return; }
        boolean permitted = granted && facade.hasPermission(attempt.device);
        if (permitted) {
            synchronized (this) { if (!active(attempt)) return; attempt.acquiring = true; }
        }
        try {
            if (permitted && !attempt.permissionOnly) arm(attempt, acquisitionTimeout);
            coordinator.permissionResult(attempt.request, attempt.request.getIdentity(), permitted,
                    !attempt.permissionOnly, !attempt.bootstrap);
        } finally {
            synchronized (this) { attempt.acquiring = false; }
            disarm(attempt);
        }
        if (!permitted) synchronized (this) { attempt.invalid = true; }
    }

    private Tbs5530LifecycleCoordinator.Connection open(Tbs5530LifecycleCoordinator.AttachmentIdentity identity) throws IOException {
        Attempt attempt;
        synchronized (this) { attempt = current; }
        if (!active(attempt) || identity.getAttachmentId() != attempt.epoch
                || !present(attempt.device) || !facade.hasPermission(attempt.device)) throw new IOException("Attachment or permission changed");
        Tbs5530LifecycleCoordinator.Connection connection = attempt.bootstrap
                ? facade.openBootstrap(attempt.device) : facade.open(attempt.device);
        if (connection == null) throw new IOException("openDevice returned null");
        try {
            if (!active(attempt) || !present(attempt.device) || !facade.hasPermission(attempt.device)) {
                throw new IOException("Attachment changed during open");
            }
        } catch (IOException | RuntimeException error) {
            try { connection.close(); } catch (IOException | RuntimeException cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
        return new Tbs5530LifecycleCoordinator.Connection() {
            public void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException {
                ensureCurrent(attempt);
                connection.claim(receipt);
                ensureCurrent(attempt);
            }
            public byte[] rawDescriptors() throws IOException {
                ensureCurrent(attempt);
                byte[] result = connection.rawDescriptors();
                ensureCurrent(attempt);
                return result;
            }
            public void requireControlWorker() throws IOException { connection.requireControlWorker(); }
            public int controlTransfer(int type, int request, int value, int index, byte[] buffer,
                                       int offset, int length, int timeout) throws IOException {
                ensureCurrent(attempt);
                int count = connection.controlTransfer(type, request, value, index, buffer, offset, length, timeout);
                ensureCurrent(attempt);
                return count;
            }
            public int bulkTransfer(byte[] buffer, int length, int timeout) throws IOException {
                ensureCurrent(attempt);
                int count = connection.bulkTransfer(buffer, length, timeout);
                ensureCurrent(attempt);
                return count;
            }
            public int lastBulkErrno() throws IOException { ensureCurrent(attempt); return connection.lastBulkErrno(); }
            public void prepareBulkIn82() throws IOException {
                ensureCurrent(attempt); connection.prepareBulkIn82(); ensureCurrent(attempt);
            }
            public Tbs5530Descriptors.Endpoint streamEndpoint() throws IOException { return connection.streamEndpoint(); }
            public void cancel() throws IOException { connection.cancel(); }
            public void release() throws IOException { connection.release(); }
            public void close() throws IOException { connection.close(); }
        };
    }

    static final class AttachmentChangedException extends IOException {
        AttachmentChangedException() { super("Attachment or OS permission changed during acquisition"); }
    }
    private void ensureCurrent(Attempt attempt) throws IOException {
        if (!active(attempt)) throw new IOException("Acquisition cancelled or owner invalidated");
        if (!present(attempt.device) || !facade.hasPermission(attempt.device)) throw new AttachmentChangedException();
    }

    private boolean present(Device expected) throws IOException {
        return facade.devices().contains(expected);
    }

    private synchronized boolean active(Attempt attempt) {
        return !closed && attempt != null && current == attempt && !attempt.invalid;
    }

    private void arm(Attempt attempt, long timeout) {
        disarm(attempt);
        final long deadlineId;
        synchronized (this) { deadlineId = attempt.deadlineId; }
        Cancellation task = scheduler.schedule(() -> invalidate(attempt, false, deadlineId), timeout);
        synchronized (this) {
            if (active(attempt)) { attempt.deadline = task; return; }
        }
        task.cancel();
    }

    private void disarm(Attempt attempt) {
        Cancellation task;
        synchronized (this) { task = attempt.deadline; attempt.deadline = null; attempt.deadlineId++; }
        if (task != null) task.cancel();
    }

    private void clearPermission(Attempt attempt) throws IOException {
        Registration registration;
        synchronized (this) { registration = attempt.permission; attempt.permission = null; }
        if (registration != null) registration.close();
    }

    private void invalidate(Attempt attempt, boolean detached) { invalidate(attempt, detached, -1); }

    private void invalidate(Attempt attempt, boolean detached, long deadlineId) {
        Tbs5530LifecycleCoordinator.Request request;
        synchronized (this) {
            if (attempt == null || current != attempt || attempt.invalid
                    || deadlineId >= 0 && attempt.deadlineId != deadlineId) return;
            attempt.invalid = true; request = attempt.request;
        }
        disarm(attempt);
        if (request != null) coordinator.invalidate(request, detached);
        submit(() -> {
            try { clearPermission(attempt); } finally { session.drainCleanup(); }
        });
    }

    private void attachmentChanged(Device device) {
        Attempt attempt;
        synchronized (this) { attempt = current; }
        if (attempt != null && attempt.device.path.equals(device.path)) invalidate(attempt, true);
    }

    private void detachEvent(Device device) {
        // Only the current attempt captured at delivery may be affected. Reused OS identities remain ambiguous.
        Attempt attempt;
        synchronized (this) { attempt = current; }
        if (attempt == null || !attempt.device.equals(device)) return;
        // Enumeration can already contain a new attachment with the very same path/id.
        // A delivered detach therefore invalidates now; physical cleanup still drains on the worker.
        invalidate(attempt, true);
    }

    private interface Job { void run() throws IOException; }
    private void submit(Job job) {
        synchronized (this) { jobs++; }
        try {
            worker.execute(() -> {
                try { job.run(); }
                catch (IOException | RuntimeException error) {
                    Attempt attempt;
                    synchronized (this) { attempt = current; }
                    if (attempt != null) {
                        disarm(attempt);
                        if (attempt.request != null) session.failed(attempt.request.getToken(), error);
                        try { clearPermission(attempt); }
                        catch (IOException | RuntimeException cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
                    }
                    errors.report(error);
                } finally { synchronized (this) { jobs--; } }
            });
        } catch (RejectedExecutionException error) {
            Attempt attempt;
            synchronized (this) { jobs--; attempt = current; if (attempt != null) attempt.invalid = true; }
            if (attempt != null) {
                disarm(attempt);
                if (attempt.request != null) coordinator.invalidate(attempt.request, false);
            }
            // The executor owner must close the binding before shutdown; never fall back to main-thread cleanup.
            errors.report(error);
        }
    }

    public synchronized boolean isPending() {
        return jobs != 0 || coordinator.isAcquiring() || session.isCleanupPending()
                || current != null && !current.invalid && session.getState() == Tbs5530Session.State.WAIT_PERMISSION;
    }

    public Tbs5530ControlIo controlIo() throws IOException {
        synchronized (this) {
            if (!active(current) || current.request == null || current.acquiring) {
                throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.SESSION_UNAVAILABLE,
                        "No claimed connection for this attachment");
            }
            return coordinator.controlIo(current.request);
        }
    }

    public Tbs5530BulkTransport bulkIo() throws IOException {
        synchronized (this) {
            if (!active(current) || current.request == null || current.acquiring || current.bootstrap) {
                throw new IOException("No current operational bulk owner");
            }
            return coordinator.bulkIo(current.request);
        }
    }

    synchronized long bootstrapEpoch() throws IOException {
        if (current == null || !current.bootstrap) throw new IOException("No bootstrap attempt");
        return current.epoch;
    }

    byte[] bootstrapRawDescriptors(long expectedEpoch) throws IOException {
        Tbs5530LifecycleCoordinator.Request request;
        synchronized (this) {
            if (!active(current) || current.epoch != expectedEpoch || !current.bootstrap
                    || current.request == null || current.acquiring) throw new IOException("No bootstrap attachment");
            request = current.request;
        }
        return coordinator.bootstrapRawDescriptors(request);
    }

    Tbs5530ControlIo bootstrapControlIo() throws IOException { return bootstrapControlIo(bootstrapEpoch()); }

    Tbs5530ControlIo bootstrapControlIo(long expectedEpoch) throws IOException {
        synchronized (this) {
            if (!active(current) || current.epoch != expectedEpoch || !current.bootstrap || current.request == null || current.acquiring) {
                throw new IOException("No bootstrap attachment");
            }
            return coordinator.bootstrapControlIo(current.request);
        }
    }

    public Tbs5530Descriptors.Endpoint streamEndpoint() throws IOException {
        Tbs5530LifecycleCoordinator.Request request;
        synchronized (this) {
            if (!active(current) || current.request == null || current.acquiring) throw new IOException("No claimed attachment");
            request = current.request;
        }
        return coordinator.streamEndpoint(request);
    }

    void cancelBootstrap(long expectedEpoch) {
        Attempt attempt;
        synchronized (this) {
            attempt = current;
            if (attempt == null || !attempt.bootstrap || attempt.epoch != expectedEpoch) return;
        }
        invalidate(attempt, false);
    }

    public void cancel() {
        Attempt attempt;
        synchronized (this) { attempt = current; }
        invalidate(attempt, false);
    }

    @Override public void close() throws IOException {
        Attempt attempt;
        synchronized (this) { if (closed) return; closed = true; attempt = current; }
        invalidate(attempt, false);
        events.close();
    }
}
