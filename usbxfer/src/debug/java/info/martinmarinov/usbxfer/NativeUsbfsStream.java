// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Debug-only continuous receive. Never closes the caller's claimed fd.
 * Consent must read the real permission/heartbeat source and return within 100 ms;
 * neither this watchdog nor Android scheduling is a hard real-time guarantee.
 */
public final class NativeUsbfsStream implements AutoCloseable {
    public interface Consent { void requireCurrent() throws IOException; }
    interface Port {
        void run() throws IOException;
        void stop();
        int copy(byte[] bytes) throws IOException;
        long[] snapshot();
        long[] metrics();
        long[] events(int offset) throws IOException;
        boolean destroy();
    }
    public static final class Result {
        public final byte[] data;
        public final long submitted, completed, elapsedNanos, starvationCount;
        public final long maxResubmitNanos, medianResubmitNanos;
        public final int minOutstanding, firstStatus, firstErrno, outstanding;
        public final boolean quarantined;
        public final String stopReason;
        private Result(byte[] data, long[] m, String reason) {
            this.data = data; submitted = m[0]; completed = m[1]; elapsedNanos = m[2];
            starvationCount = m[3]; maxResubmitNanos = m[4]; medianResubmitNanos = m[5];
            minOutstanding = (int) m[6]; firstStatus = (int) m[7]; firstErrno = (int) m[8];
            outstanding = (int) m[9]; quarantined = m[10] != 0; stopReason = reason;
        }
        public double bytesPerSecond() { return elapsedNanos <= 0 ? 0 : data.length * 1_000_000_000.0 / elapsedNanos; }
    }
    private static final String[] REASONS = {"RUNNING", "STOP", "DEADLINE", "BYTE_LIMIT", "COMPLETION_STATUS",
            "INVALID_LENGTH", "SYSCALL", "IDENTITY", "EVENT_CAPACITY", "CLOCK"};
    private final Object lifecycle = new Object();
    private final Port port;
    private final Consent consent;
    private final Writer audit;
    private final String runId;
    private final byte[] buffer;
    private boolean used, running, closed;
    private Thread receiver;
    private volatile boolean quarantined;
    private volatile long[] finalSnapshot;

    public NativeUsbfsStream(int ownedFd, int queueDepth, int chunkBytes, int maxBytes, int durationMillis,
                             String runId, Consent consent, Writer audit) throws IOException {
        validate(ownedFd, queueDepth, chunkBytes, maxBytes, durationMillis, runId, consent, audit);
        this.runId = runId; this.consent = consent; this.audit = audit;
        buffer = new byte[maxBytes];
        port = new Bindings(ownedFd, queueDepth, chunkBytes, maxBytes, durationMillis);
    }
    NativeUsbfsStream(Port port, int maxBytes, String runId, Consent consent, Writer audit) {
        validate(0, 16, 16384, maxBytes, 2000, runId, consent, audit);
        this.port = Objects.requireNonNull(port); this.runId = runId; this.consent = consent; this.audit = audit;
        buffer = new byte[maxBytes];
    }
    private static void validate(int fd, int depth, int chunk, int bytes, int duration,
                                 String id, Consent consent, Writer audit) {
        if (fd < 0 || depth < 8 || depth > 32 || chunk < 4096 || chunk > 65536 || chunk % 512 != 0
                || bytes < 1 || bytes > 16 * 1024 * 1024 || duration < 1 || duration > 10000
                || id == null || !id.matches("[A-Za-z0-9_-]{1,100}") || consent == null || audit == null)
            throw new IllegalArgumentException("Invalid native stream preflight bounds");
    }
    /** One-shot, synchronous on the caller's runtime worker. No Java calls occur in the native hot loop. */
    public Result receive() throws IOException {
        synchronized (lifecycle) {
            if (used || closed) throw new IOException("Native stream is one-shot or closed");
            used = true; running = true; receiver = Thread.currentThread();
        }
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch checked = new CountDownLatch(1);
        Thread worker = Thread.currentThread();
        Thread watchdog = new Thread(() -> {
            try {
                while (!done.get()) {
                    consent.requireCurrent();
                    if (worker.isInterrupted()) throw new IOException("Receive worker interrupted");
                    checked.countDown();
                    Thread.sleep(100);
                }
            } catch (Throwable error) {
                if (!done.get()) { failure.compareAndSet(null, error); requestStop(); }
            } finally { checked.countDown(); }
        }, "tbs5530-stream-consent");
        boolean watchdogStarted = false, interrupted = false;
        try {
            consent.requireCurrent();
            if (worker.isInterrupted()) throw new IOException("Receive interrupted before submit");
            watchdog.start(); watchdogStarted = true;
            try { checked.await(); }
            catch (InterruptedException error) {
                interrupted = true; failure.compareAndSet(null, error); requestStop();
            }
            try { port.run(); }
            finally {
                done.set(true); watchdog.interrupt();
                interrupted |= join(watchdog);
            }
            long[] state = requireWidth(port.snapshot(), 14);
            finalSnapshot = state.clone(); quarantined = state[6] != 0 || state[3] != 0;
            long[] metrics = requireWidth(port.metrics(), 14);
            int count = port.copy(buffer);
            if (count < 0 || count > buffer.length) throw new IOException("Invalid native stream byte count");
            int code = (int) metrics[11];
            String reason = failure.get() != null ? "WATCHDOG" : code >= 0 && code < REASONS.length ? REASONS[code] : "UNKNOWN";
            Result result = new Result(Arrays.copyOf(buffer, count), metrics, reason);
            writeAudit(result, metrics);
            return result;
        } finally {
            if (watchdogStarted && watchdog.isAlive()) {
                done.set(true); requestStop(); watchdog.interrupt(); interrupted |= join(watchdog);
            }
            synchronized (lifecycle) {
                running = false; receiver = null; lifecycle.notifyAll();
            }
            if (interrupted) worker.interrupt();
        }
    }
    private static boolean join(Thread thread) {
        boolean interrupted = false;
        for (;;) {
            try { thread.join(); return interrupted; }
            catch (InterruptedException error) { interrupted = true; }
        }
    }
    private static long[] requireWidth(long[] values, int width) throws IOException {
        if (values == null || values.length != width) throw new IOException("Malformed native stream result");
        return values;
    }
    public void requestStop() {
        synchronized (lifecycle) { if (!closed) port.stop(); }
    }
    public boolean isQuarantined() {
        synchronized (lifecycle) {
            if (!closed) { long[] s = port.snapshot(); quarantined |= s[6] != 0; }
            return quarantined;
        }
    }
    /** Live fields are individually atomic observations, not a hardware in-flight queue measurement. */
    public long[] snapshot() {
        synchronized (lifecycle) {
            return (closed ? finalSnapshot : port.snapshot()).clone();
        }
    }
    private void writeAudit(Result result, long[] metrics) throws IOException {
        int offset = 0;
        for (;;) {
            long[] batch = port.events(offset);
            if (batch == null || batch.length % 14 != 0) throw new IOException("Malformed native stream events");
            if (batch.length == 0) break;
            for (int i = 0; i < batch.length; i += 14) {
                audit.write("{\"run_id\":\"" + runId + "\",\"read_api\":\"NATIVE_USBFS_STREAM\",\"event\":" + (++offset)
                        + ",\"kind\":" + batch[i] + ",\"slot\":" + batch[i + 1] + ",\"syscall_return\":" + batch[i + 2]
                        + ",\"syscall_errno\":" + batch[i + 3] + ",\"urb_status\":" + (batch[i + 4] == Integer.MIN_VALUE ? "null" : batch[i + 4])
                        + ",\"actual_length\":" + batch[i + 5] + ",\"requested_length\":" + batch[i + 6]
                        + ",\"allocated_capacity\":" + batch[i + 7] + ",\"endpoint\":" + batch[i + 8] + ",\"flags\":" + batch[i + 9]
                        + ",\"sequence\":" + batch[i + 10] + ",\"clock\":\"CLOCK_MONOTONIC\",\"before_ns\":" + batch[i + 11]
                        + ",\"after_ns\":" + batch[i + 12] + ",\"software_outstanding\":" + batch[i + 13] + "}\n");
            }
        }
        audit.write("{\"run_id\":\"" + runId + "\",\"summary\":true,\"stop_reason\":\"" + result.stopReason
                + "\",\"delivered_bytes\":" + result.data.length + ",\"submitted\":" + result.submitted + ",\"completed\":" + result.completed
                + ",\"outstanding\":" + result.outstanding + ",\"quarantined\":" + result.quarantined
                + ",\"zero_software_outstanding_count\":" + metrics[12] + ",\"queue_horizon_risk_count\":" + metrics[13]
                + ",\"queue_horizon_assumed_bytes_per_second\":3437500,\"starvation_scope\":\"software_zero_or_assumed_queue_horizon_risk_not_hardware_proof\"}\n");
        audit.flush();
    }
    @Override public void close() throws IOException {
        boolean interrupted = false;
        synchronized (lifecycle) {
            if (closed) return;
            port.stop();
            if (running && receiver == Thread.currentThread()) throw new IOException("Cannot close a running stream from its own audit callback");
            while (running) {
                try { lifecycle.wait(); }
                catch (InterruptedException error) { interrupted = true; }
            }
            if (closed) {
                if (interrupted) Thread.currentThread().interrupt();
                return;
            }
            finalSnapshot = port.snapshot();
            quarantined |= finalSnapshot[6] != 0 || finalSnapshot[3] != 0;
            if (!quarantined && !port.destroy()) quarantined = true;
            closed = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (quarantined) throw new IOException("Native stream QUARANTINED; keep owned fd and kernel-referenced memory");
    }
    private static final class Bindings implements Port {
        static { System.loadLibrary("Tbs5530UsbfsDiagnostic"); }
        private final long handle;
        Bindings(int fd, int depth, int chunk, int bytes, int duration) throws IOException {
            handle = create(fd, depth, chunk, bytes, duration);
            if (handle == 0) throw new IOException("Native stream preallocation failed before submit");
        }
        public void run() throws IOException { NativeUsbfsStream.run(handle); }
        public void stop() { NativeUsbfsStream.stop(handle); }
        public int copy(byte[] bytes) throws IOException { return NativeUsbfsStream.copy(handle, bytes); }
        public long[] snapshot() { return state(handle); }
        public long[] metrics() { return NativeUsbfsStream.metrics(handle); }
        public long[] events(int offset) throws IOException { return eventBatch(handle, offset); }
        public boolean destroy() { return NativeUsbfsStream.destroy(handle); }
    }
    private static native long create(int fd, int depth, int chunk, int bytes, int duration);
    private static native void run(long handle) throws IOException;
    private static native void stop(long handle);
    private static native int copy(long handle, byte[] bytes) throws IOException;
    private static native long[] state(long handle);
    private static native long[] metrics(long handle);
    private static native long[] eventBatch(long handle, int offset) throws IOException;
    private static native boolean destroy(long handle);
}
