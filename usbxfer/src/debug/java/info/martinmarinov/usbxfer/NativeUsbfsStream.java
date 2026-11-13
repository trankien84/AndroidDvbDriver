// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        default void writerAttach() throws IOException { throw new IOException("File writer unsupported"); }
        default int writerRead(byte[] bytes) throws IOException { throw new IOException("File writer unsupported"); }
        default void writerFinish() { }
        default void writerDetach() { }
        default long[] writerMetrics() throws IOException { return new long[]{0, 0}; }
        default long[] mappings(int offset) throws IOException { return new long[0]; }
    }
    public static final class Result {
        public final byte[] data;
        public final File dataFile, file;
        /** Valid received bytes up to the cap, including whole completions dropped by the writer ring. */
        public final int byteCount;
        public final long writtenBytes, dropBytes, writerWrittenBytes, writerDropBytes;
        public final String writerFailure;
        public final long submitted, completed, elapsedNanos, starvationCount;
        public final long maxResubmitNanos, medianResubmitNanos;
        public final int minOutstanding, firstStatus, firstErrno, outstanding;
        public final boolean quarantined;
        public final String stopReason;
        private Result(byte[] data, long[] metrics, String reason) {
            this(data, null, data.length, data.length, 0, null, metrics, reason);
        }
        private Result(byte[] data, File file, int byteCount, long written, long dropped, String writerFailure,
                       long[] m, String reason) {
            this.data = data; this.file = this.dataFile = file; this.byteCount = byteCount;
            writtenBytes = writerWrittenBytes = written; dropBytes = writerDropBytes = dropped;
            this.writerFailure = writerFailure;
            submitted = m[0]; completed = m[1]; elapsedNanos = m[2];
            starvationCount = m[3]; maxResubmitNanos = m[4]; medianResubmitNanos = m[5];
            minOutstanding = (int) m[6]; firstStatus = (int) m[7]; firstErrno = (int) m[8];
            outstanding = (int) m[9]; quarantined = m[10] != 0; stopReason = reason;
        }
        public double bytesPerSecond() { return elapsedNanos <= 0 ? 0 : byteCount * 1_000_000_000.0 / elapsedNanos; }
        /** Open only after receive returns; file-backed captures must be parsed in bounded chunks. */
        public InputStream openData() throws IOException {
            return dataFile == null ? new ByteArrayInputStream(data) : new FileInputStream(dataFile);
        }
    }
    private static final String[] REASONS = {"RUNNING", "STOP", "DEADLINE", "BYTE_LIMIT", "COMPLETION_STATUS",
            "INVALID_LENGTH", "SYSCALL", "IDENTITY", "EVENT_CAPACITY", "CLOCK"};
    private final Object lifecycle = new Object();
    private final Port port;
    private final Consent consent;
    private final Writer audit;
    private final String runId;
    private final byte[] buffer;
    private final File dataFile;
    private final NativeUsbfsStreamWriter fileWriter;
    private final long writerDrainMillis;
    private boolean used, running, closed;
    private Thread receiver;
    private volatile boolean quarantined;
    private volatile long[] finalSnapshot;

    public NativeUsbfsStream(int ownedFd, int queueDepth, int chunkBytes, int maxBytes, int durationMillis,
                             String runId, Consent consent, Writer audit) throws IOException {
        this(ownedFd, queueDepth, chunkBytes, maxBytes, durationMillis, runId, consent, audit, null);
    }
    /** Opt-in file-backed capture; never allocates the byte cap as a Java or native data array. */
    public NativeUsbfsStream(int ownedFd, int queueDepth, int chunkBytes, int maxBytes, int durationMillis,
                             String runId, Consent consent, Writer audit, File dataFile) throws IOException {
        validate(ownedFd, queueDepth, chunkBytes, maxBytes, durationMillis, runId, consent, audit, dataFile != null);
        this.runId = runId; this.consent = consent; this.audit = audit; this.dataFile = dataFile;
        buffer = dataFile == null ? new byte[maxBytes] : new byte[0];
        port = new Bindings(ownedFd, queueDepth, chunkBytes, maxBytes, durationMillis, dataFile != null);
        NativeUsbfsStreamWriter writer = null;
        try {
            if (dataFile != null) {
                if (!dataFile.createNewFile()) throw new IOException("Native capture already exists");
                FileOutputStream output = new FileOutputStream(dataFile);
                try { writer = new NativeUsbfsStreamWriter(port, NativeUsbfsStreamWriter.fileSink(output)); }
                catch (RuntimeException | Error error) { output.close(); throw error; }
            }
        } catch (IOException | RuntimeException | Error error) {
            if (!port.destroy()) error.addSuppressed(new IOException("Native preflight cleanup refused"));
            throw error;
        }
        fileWriter = writer; writerDrainMillis = 10000;
    }
    NativeUsbfsStream(Port port, int maxBytes, String runId, Consent consent, Writer audit) {
        validate(0, 16, 16384, maxBytes, 2000, runId, consent, audit, false);
        this.port = Objects.requireNonNull(port); this.runId = runId; this.consent = consent; this.audit = audit;
        buffer = new byte[maxBytes]; dataFile = null; fileWriter = null; writerDrainMillis = 10000;
    }
    NativeUsbfsStream(Port port, File file, NativeUsbfsStreamWriter.Sink sink,
                      String runId, Consent consent, Writer audit) {
        this(port, file, sink, runId, consent, audit, 10000);
    }
    NativeUsbfsStream(Port port, File file, NativeUsbfsStreamWriter.Sink sink,
                      String runId, Consent consent, Writer audit, long writerDrainMillis) {
        validate(0, 16, 16384, 256 * 1024 * 1024, 60000, runId, consent, audit, true);
        if (writerDrainMillis < 1 || writerDrainMillis > 10000) throw new IllegalArgumentException("Writer drain bound");
        this.writerDrainMillis = writerDrainMillis;
        this.port = Objects.requireNonNull(port); this.runId = runId; this.consent = consent; this.audit = audit;
        dataFile = Objects.requireNonNull(file); buffer = new byte[0];
        fileWriter = new NativeUsbfsStreamWriter(port, sink);
    }
    private static void validate(int fd, int depth, int chunk, int bytes, int duration,
                                 String id, Consent consent, Writer audit, boolean file) {
        if (fd < 0 || depth < 8 || depth > 32 || chunk < 4096 || chunk > 65536 || chunk % 512 != 0
                || (file && ((depth != 16 && depth != 32) || chunk != 16384))
                || bytes < 1 || bytes > (file ? 256 : 16) * 1024 * 1024
                || duration < 1 || duration > (file ? 60000 : 10000)
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
            if (fileWriter != null) fileWriter.start();
            try { port.run(); }
            finally {
                done.set(true); watchdog.interrupt();
                interrupted |= join(watchdog);
                if (fileWriter != null) {
                    port.writerFinish();
                    interrupted |= fileWriter.await(writerDrainMillis);
                    if (fileWriter.alive()) {
                        quarantined = true;
                        throw new IOException("Native writer QUARANTINED; drain timeout, retain owned fd and memory");
                    }
                }
            }
            long[] state = requireWidth(port.snapshot(), 14);
            finalSnapshot = state.clone(); quarantined |= state[6] != 0 || state[3] != 0;
            long[] metrics = requireWidth(port.metrics(), 14);
            int count;
            long written, dropped = 0;
            String writerFailure = null;
            if (fileWriter == null) {
                count = port.copy(buffer); written = count;
                if (count < 0 || count > buffer.length) throw new IOException("Invalid native stream byte count");
            } else {
                if (state[4] < 0 || state[4] > 256 * 1024 * 1024) throw new IOException("Invalid native file byte count");
                count = (int) state[4];
                long[] wm = requireWidth(port.writerMetrics(), 2);
                written = fileWriter.writtenBytes(); dropped = wm[1]; writerFailure = fileWriter.failure();
                if (wm[0] < 0 || dropped < 0 || wm[0] + dropped != count
                        || written != wm[0] || dataFile.length() != written) {
                    if (writerFailure == null) writerFailure = "WRITER_BYTE_ACCOUNTING";
                }
            }
            int code = (int) metrics[11];
            String reason = failure.get() != null ? "WATCHDOG" : code >= 0 && code < REASONS.length ? REASONS[code] : "UNKNOWN";
            Result result = new Result(fileWriter == null ? Arrays.copyOf(buffer, count) : buffer,
                    dataFile, count, written, dropped, writerFailure, metrics, reason);
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
        if (fileWriter != null) {
            int mapped = 0;
            for (;;) {
                long[] batch = port.mappings(mapped);
                if (batch == null || batch.length % 6 != 0) throw new IOException("Malformed native stream mappings");
                if (batch.length == 0) break;
                for (int i = 0; i < batch.length; i += 6) {
                    ++mapped;
                    audit.write("{\"run_id\":\"" + runId + "\",\"mapping\":true,\"sequence\":" + batch[i]
                            + ",\"source_offset\":" + batch[i + 1] + ",\"raw_offset\":" + (batch[i + 2] < 0 ? "null" : batch[i + 2])
                            + ",\"length\":" + batch[i + 3] + ",\"dropped\":" + (batch[i + 4] != 0)
                            + ",\"reap_ns\":" + batch[i + 5] + ",\"clock\":\"CLOCK_MONOTONIC\"}\n");
                }
            }
        }
        audit.write("{\"run_id\":\"" + runId + "\",\"summary\":true,\"stop_reason\":\"" + result.stopReason
                + "\",\"delivered_bytes\":" + result.byteCount + ",\"submitted\":" + result.submitted + ",\"completed\":" + result.completed
                + ",\"writer_written_bytes\":" + result.writtenBytes + ",\"writer_drop_bytes\":" + result.dropBytes
                + ",\"writer_failure\":" + (result.writerFailure == null ? "null" : "\"" + result.writerFailure + "\"")
                + ",\"raw_mapping_valid\":" + (result.writerFailure == null && result.dropBytes == 0)
                + ",\"raw_offset_scope\":\"queued_bytes_require_writer_success\""
                + ",\"outstanding\":" + result.outstanding + ",\"quarantined\":" + result.quarantined
                + ",\"zero_software_outstanding_count\":" + metrics[12]
                + ",\"queue_horizon_risk_count\":null,\"queue_horizon_assumed_bytes_per_second\":null"
                + ",\"starvation_scope\":\"software_zero_only_not_hardware_proof\"}\n");
        audit.flush();
    }
    @Override public void close() throws IOException {
        boolean interrupted = false;
        IOException closeFailure = null;
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
            if (fileWriter != null) {
                if (fileWriter.alive()) quarantined = true;
                else {
                    try { fileWriter.closeUnstarted(); }
                    catch (IOException error) { closeFailure = error; }
                }
            }
            if (!quarantined && !port.destroy()) quarantined = true;
            closed = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (quarantined) throw new IOException("Native stream QUARANTINED; keep owned fd and kernel-referenced memory");
        if (closeFailure != null) throw closeFailure;
    }
    private static final class Bindings implements Port {
        static { System.loadLibrary("Tbs5530UsbfsDiagnostic"); }
        private final long handle;
        Bindings(int fd, int depth, int chunk, int bytes, int duration, boolean file) throws IOException {
            handle = file ? createFile(fd, depth, chunk, bytes, duration) : create(fd, depth, chunk, bytes, duration);
            if (handle == 0) throw new IOException("Native stream preallocation failed before submit");
        }
        public void run() throws IOException { NativeUsbfsStream.run(handle); }
        public void stop() { NativeUsbfsStream.stop(handle); }
        public int copy(byte[] bytes) throws IOException { return NativeUsbfsStream.copy(handle, bytes); }
        public long[] snapshot() { return state(handle); }
        public long[] metrics() { return NativeUsbfsStream.metrics(handle); }
        public long[] events(int offset) throws IOException { return eventBatch(handle, offset); }
        public boolean destroy() { return NativeUsbfsStream.destroy(handle); }
        public void writerAttach() throws IOException { NativeUsbfsStream.writerAttach(handle); }
        public int writerRead(byte[] bytes) throws IOException { return NativeUsbfsStream.writerRead(handle, bytes); }
        public void writerFinish() { NativeUsbfsStream.writerFinish(handle); }
        public void writerDetach() { NativeUsbfsStream.writerDetach(handle); }
        public long[] writerMetrics() throws IOException { return NativeUsbfsStream.writerMetrics(handle); }
        public long[] mappings(int offset) throws IOException { return mappingBatch(handle, offset); }
    }
    private static native long create(int fd, int depth, int chunk, int bytes, int duration);
    private static native long createFile(int fd, int depth, int chunk, int bytes, int duration);
    private static native void writerAttach(long handle) throws IOException;
    private static native int writerRead(long handle, byte[] bytes) throws IOException;
    private static native void writerFinish(long handle);
    private static native void writerDetach(long handle);
    private static native long[] writerMetrics(long handle) throws IOException;
    private static native long[] mappingBatch(long handle, int offset) throws IOException;
    private static native void run(long handle) throws IOException;
    private static native void stop(long handle);
    private static native int copy(long handle, byte[] bytes) throws IOException;
    private static native long[] state(long handle);
    private static native long[] metrics(long handle);
    private static native long[] eventBatch(long handle, int offset) throws IOException;
    private static native boolean destroy(long handle);
}
