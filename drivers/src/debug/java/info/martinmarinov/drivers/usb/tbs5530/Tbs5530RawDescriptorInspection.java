// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** One-shot debug capability; its owner must exclude every other USB session until pending=false. */
public final class Tbs5530RawDescriptorInspection implements AutoCloseable {
    public interface Connection {
        byte[] getRawDescriptors() throws IOException;
        void close() throws IOException;
    }
    public interface Port {
        List<Tbs5530UsbBinding.Device> devices() throws IOException;
        boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException;
        Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) throws IOException;
        Connection open(Tbs5530UsbBinding.Device device) throws IOException;
    }
    public static final class Snapshot {
        public final long generation, durationMillis;
        public final boolean pending, invalidated, permission, identityMatched;
        public final int openCalls, openCompleted, readCalls, readCompleted, closeCalls, closeCompleted;
        public final String status, failure, worker;
        public final List<String> calls;
        public final Tbs5530RawDescriptors.Result descriptors;
        private Snapshot(Tbs5530RawDescriptorInspection source) {
            generation = source.generation; durationMillis = source.durationMillis;
            pending = source.pending; invalidated = source.invalid; permission = source.permission;
            identityMatched = source.identityMatched;
            openCalls = source.openCalls; openCompleted = source.openCompleted;
            readCalls = source.readCalls; readCompleted = source.readCompleted;
            closeCalls = source.closeCalls; closeCompleted = source.closeCompleted;
            status = source.status; failure = source.failure; worker = source.thread;
            calls = Collections.unmodifiableList(new ArrayList<>(source.calls)); descriptors = source.result;
        }
    }
    private final Port port;
    private final Executor worker;
    private final Tbs5530UsbBinding.Scheduler scheduler;
    private final Tbs5530UsbBinding.Device device;
    private final long generation;
    private final List<String> calls = new ArrayList<>();
    private boolean started, pending, invalid, permission, identityMatched;
    private int openCalls, openCompleted, readCalls, readCompleted, closeCalls, closeCompleted;
    private long durationMillis;
    private String status = "NOT_RUN", failure = "NONE", thread = "NOT_RUN";
    private Tbs5530RawDescriptors.Result result;

    public Tbs5530RawDescriptorInspection(Port port, Executor worker, Tbs5530UsbBinding.Scheduler scheduler,
                                         Tbs5530UsbBinding.Device device, long generation) {
        this.port = Objects.requireNonNull(port); this.worker = Objects.requireNonNull(worker);
        this.scheduler = Objects.requireNonNull(scheduler); this.device = Objects.requireNonNull(device);
        if (device.vendorId != 0x734c || device.productId != 0x5530 || generation <= 0) {
            throw new IllegalArgumentException("Expected TBS5530 and a positive operation generation");
        }
        this.generation = generation;
    }
    public void start() {
        synchronized (this) {
            if (started || invalid) throw new IllegalStateException("One-shot capability is no longer available");
            started = true; pending = true; status = "PENDING";
        }
        try { worker.execute(this::inspect); }
        catch (RuntimeException rejected) {
            synchronized (this) { pending = false; invalid = true; status = "FAIL"; failure = "WORKER_REJECTED"; }
            throw rejected;
        }
    }
    private void inspect() {
        long begin = System.nanoTime(); Connection connection = null;
        Tbs5530UsbBinding.Registration registration = null;
        Tbs5530UsbBinding.Cancellation deadline = null;
        Tbs5530RawDescriptors.Result local = null;
        String stage = "LISTEN";
        try {
            synchronized (this) {
                thread = "tbs5530-usb-worker".equals(Thread.currentThread().getName()) ? "tbs5530-usb-worker" : "worker-other";
            }
            requireValid();
            deadline = scheduler.schedule(() -> invalidate("DEADLINE"), 10000);
            registration = port.listen(new Tbs5530UsbBinding.Events() {
                @Override public void attached(Tbs5530UsbBinding.Device reported) {
                    if (device.path.equals(reported.path)) invalidate("ATTACHMENT_CHANGED");
                }
                @Override public void detached(Tbs5530UsbBinding.Device reported) {
                    if (device.equals(reported)) invalidate("DETACHED");
                }
            });
            stage = "PRE_OPEN"; validateCurrent();
            synchronized (this) { requireValid(); openCalls++; calls.add("openDevice"); }
            stage = "OPEN"; connection = port.open(device);
            if (connection == null) throw new IOException("OPEN_NULL");
            synchronized (this) { openCompleted++; }
            stage = "POST_OPEN"; validateCurrent();
            synchronized (this) { requireValid(); readCalls++; calls.add("getRawDescriptors"); }
            stage = "RAW_READ"; byte[] data = connection.getRawDescriptors();
            synchronized (this) { readCompleted++; }
            stage = "POST_READ"; validateCurrent();
            stage = "PARSE"; local = Tbs5530RawDescriptors.parse(data);
        } catch (IOException | RuntimeException error) {
            synchronized (this) {
                if ("NONE".equals(failure)) failure = stage + "_" + (error instanceof SecurityException ? "SECURITY" : "FAILED");
                status = "FAIL";
            }
        } finally {
            if (connection != null) {
                synchronized (this) { closeCalls++; calls.add("close"); }
                try { connection.close(); synchronized (this) { closeCompleted++; } }
                catch (IOException | RuntimeException error) { fail("CLOSE_FAILED"); }
            }
            if (registration != null) {
                try { registration.close(); } catch (IOException | RuntimeException error) { fail("LISTENER_CLEANUP_FAILED"); }
            }
            if (deadline != null) deadline.cancel();
            synchronized (this) {
                durationMillis = (System.nanoTime() - begin) / 1000000;
                if (!invalid && "NONE".equals(failure) && local != null) {
                    result = local; status = "READ_AND_CLOSED";
                } else { result = null; status = "FAIL"; }
                pending = false;
            }
        }
    }
    private void validateCurrent() throws IOException {
        requireValid();
        boolean present = port.devices().contains(device);
        boolean granted = present && port.hasPermission(device);
        synchronized (this) { identityMatched = present; permission = granted; }
        requireValid();
        if (!present) { invalidate("ATTACHMENT_CHANGED"); throw new IOException("Attachment changed"); }
        if (!granted) { invalidate("PERMISSION_DENIED"); throw new IOException("Permission denied"); }
    }
    private synchronized void requireValid() throws IOException {
        if (invalid) throw new IOException("Capability invalidated");
    }
    private synchronized void fail(String reason) {
        if ("NONE".equals(failure)) failure = reason;
        status = "FAIL";
    }
    public synchronized void invalidate(String reason) {
        invalid = true; result = null;
        if ("NONE".equals(failure)) failure = reason;
        status = pending ? "INVALIDATED_PENDING_CLEANUP" : "INVALIDATED";
    }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
    @Override public void close() { invalidate("OWNER_CLOSED"); }
}
