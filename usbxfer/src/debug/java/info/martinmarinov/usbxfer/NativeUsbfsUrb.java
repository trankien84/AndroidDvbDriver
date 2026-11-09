// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/** Debug-only usbfs diagnostic. The caller owns the claimed fd; this class never closes it. */
public final class NativeUsbfsUrb implements AutoCloseable {
    public interface Consent { void requireCurrent() throws IOException; }
    interface Port {
        int fill(); int poll(); int take(byte[] bytes); void stop(); void quarantine();
        long[] events(); long[] snapshot(); boolean destroy();
    }
    interface Clock { long nanos(); void pause() throws InterruptedException; }
    private static final int WAIT = -10, EVENT_WIDTH = 14;
    private final Port port;
    private final Consent consent;
    private final Clock clock;
    private final Writer audit;
    private final String runId;
    private final int chunkBytes;
    private final long worker = Thread.currentThread().getId();
    private final long durationNanos;
    private long started = -1;
    private volatile long[] finalSnapshot, publishedSnapshot;
    private int polls, eventSequence;
    private boolean stopped, closed, quarantined, freed;

    public NativeUsbfsUrb(int ownedFd, int maxSubmissions, int durationMillis, String runId,
                          Consent consent, Writer audit) throws IOException {
        this(ownedFd, maxSubmissions, durationMillis, 4096, runId, consent, audit);
    }
    /** 8192 changes only the diagnostic slot size; it is not an EOVERFLOW fix. */
    public NativeUsbfsUrb(int ownedFd, int maxSubmissions, int durationMillis, int chunkBytes, String runId,
                          Consent consent, Writer audit) throws IOException {
        this(bindings(ownedFd, maxSubmissions, durationMillis, chunkBytes, runId, consent, audit), durationMillis, chunkBytes, runId, consent, audit, new Clock() {
            public long nanos() { return System.nanoTime(); }
            public void pause() throws InterruptedException { Thread.sleep(10); }
        });
    }
    private static Port bindings(int fd, int budget, int duration, int chunkBytes, String runId, Consent consent, Writer audit) throws IOException {
        requireChunkBytes(chunkBytes);
        if (fd < 0 || budget < 8 || budget > 256 || budget > 1024 * 1024 / chunkBytes
                || duration < 1 || duration > 10000
                || runId == null || !runId.matches("[A-Za-z0-9_-]{1,100}")
                || consent == null || audit == null) throw new IllegalArgumentException("Invalid native diagnostic arguments");
        return new Bindings(fd, budget, chunkBytes);
    }
    private static void requireChunkBytes(int chunkBytes) {
        if (chunkBytes != 4096 && chunkBytes != 8192) throw new IllegalArgumentException("Native slots must be 4096 or 8192 bytes");
    }
    NativeUsbfsUrb(Port port, int durationMillis, String runId, Consent consent, Writer audit, Clock clock) {
        this(port, durationMillis, 4096, runId, consent, audit, clock);
    }
    NativeUsbfsUrb(Port port, int durationMillis, int chunkBytes, String runId, Consent consent, Writer audit, Clock clock) {
        requireChunkBytes(chunkBytes);
        if (durationMillis < 1 || durationMillis > 10000 || runId == null || !runId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid native diagnostic bounds or run ID");
        }
        this.port = Objects.requireNonNull(port); this.consent = Objects.requireNonNull(consent);
        this.audit = Objects.requireNonNull(audit); this.clock = Objects.requireNonNull(clock);
        this.runId = runId; this.chunkBytes = chunkBytes; durationNanos = durationMillis * 1_000_000L;
        publishedSnapshot = port.snapshot();
    }
    private void worker() throws IOException {
        if (Thread.currentThread().getId() != worker) throw new IOException("Native receive worker changed");
    }
    private long elapsed(long from) throws IOException {
        long value = clock.nanos() - from;
        if (value < 0) throw new IOException("Native receive monotonic clock moved backwards");
        return value;
    }
    private void current(long readStarted, long readBudget) throws IOException {
        consent.requireCurrent();
        if (elapsed(started) >= durationNanos || elapsed(readStarted) >= readBudget) {
            throw new IOException("Native receive deadline; no retry");
        }
    }
    public int read(byte[] destination, int length, int timeoutMillis) throws IOException {
        worker();
        if (closed || stopped) throw new IOException("Native receive stopped; no retry");
        if (destination == null || length != chunkBytes || destination.length < length || timeoutMillis < 1 || timeoutMillis > 2000) {
            throw new IllegalArgumentException("Native receive requires the exact approved slot size");
        }
        long begin = clock.nanos();
        if (started == -1) started = begin;
        long budget = timeoutMillis * 1_000_000L;
        try {
            for (int i = 0; i < 8; i++) {
                current(begin, budget);
                int filled = port.fill(); flushEvents();
                if (filled < 0) { stopped = true; return -1; }
                if (filled == 0) break;
            }
            while (true) {
                current(begin, budget);
                int count = port.take(destination); flushEvents();
                if (count != WAIT) {
                    if (count <= 0) stopped = true;
                    current(begin, budget);
                    return count < 0 ? -1 : count;
                }
                if (++polls > 1200) throw new IOException("Native receive poll budget reached");
                int result = port.poll(); flushEvents();
                if (result < 0 && result != WAIT) { stopped = true; return -1; }
                if (result == WAIT) clock.pause();
            }
        } catch (InterruptedException error) {
            stopped = true; Thread.currentThread().interrupt(); throw new IOException("Native receive interrupted", error);
        } catch (IOException | RuntimeException | Error error) {
            stopped = true; throw error;
        } finally { publishedSnapshot = port.snapshot(); }
    }
    /** Syscall errno only: 0 after successful reap, even for negative urb.status. Never -urb.status. */
    public int errno() throws IOException { worker(); return (int) snapshot()[7]; }
    public long[] snapshot() {
        long[] result = finalSnapshot;
        if (result == null) result = Thread.currentThread().getId() == worker ? port.snapshot() : publishedSnapshot;
        return result.clone();
    }
    public boolean isQuarantined() { return quarantined; }
    private void flushEvents() throws IOException {
        long[] values;
        while ((values = port.events()).length != 0) {
            if (values.length % EVENT_WIDTH != 0) throw new IOException("Malformed native audit boundary");
            for (int i = 0; i < values.length; i += EVENT_WIDTH) {
                if (++eventSequence > 4096) throw new IOException("Native audit bound exceeded");
                String[] operations = {"UNKNOWN", "SUBMIT", "REAP", "DISCARD", "GUARD", "COPY"};
                int kind = (int) values[i];
                String operation = kind > 0 && kind < operations.length ? operations[kind] : "UNKNOWN";
                audit.write("{\"run_id\":\"" + runId + "\",\"read_api\":\"NATIVE_USBFS_URB_8X" + chunkBytes + "\",\"worker\":" + worker
                        + ",\"event\":" + eventSequence + ",\"operation\":\"" + operation + "\",\"slot\":" + values[i + 1]
                        + ",\"syscall_return\":" + values[i + 2] + ",\"syscall_errno\":" + values[i + 3]
                        + ",\"urb_status\":" + (values[i + 4] == Integer.MIN_VALUE ? "null" : values[i + 4])
                        + ",\"actual_length\":" + values[i + 5] + ",\"requested_length\":" + values[i + 6]
                        + ",\"allocated_capacity\":" + values[i + 7] + ",\"endpoint\":" + values[i + 8]
                        + ",\"flags\":" + values[i + 9] + ",\"sequence\":" + values[i + 10]
                        + ",\"clock\":\"CLOCK_MONOTONIC\",\"before_ns\":" + values[i + 11]
                        + ",\"after_ns\":" + values[i + 12] + ",\"outstanding\":" + values[i + 13] + "}\n");
            }
            audit.flush();
        }
    }
    @Override public void close() throws IOException {
        worker(); stopped = true;
        if (closed) { if (quarantined) throw new IOException("Native resources QUARANTINED; physical recovery required"); return; }
        IOException failure = null;
        boolean interrupted = Thread.interrupted();
        try {
            port.stop();
            long begin = clock.nanos();
            for (int i = 0; i < 128; i++) {
                try { flushEvents(); } catch (IOException error) { if (failure == null) failure = error; }
                long[] state = port.snapshot();
                if (state[3] == 0 || state[6] != 0) break;
                if (elapsed(begin) >= 1_000_000_000L) break;
                int result = port.poll();
                if (result < 0 && result != WAIT) break;
                if (result == WAIT) {
                    try { clock.pause(); } catch (InterruptedException error) { interrupted = true; }
                }
            }
            try { flushEvents(); } catch (IOException error) { if (failure == null) failure = error; }
            finalSnapshot = port.snapshot();
            if (finalSnapshot[3] != 0 || finalSnapshot[6] != 0) {
                port.quarantine(); quarantined = true; finalSnapshot = port.snapshot();
            } else {
                freed = port.destroy();
                quarantined = !freed;
            }
            audit.write("{\"run_id\":\"" + runId + "\",\"summary\":true,\"attempted\":" + finalSnapshot[0]
                    + ",\"submitted\":" + finalSnapshot[1] + ",\"completed\":" + finalSnapshot[2]
                    + ",\"outstanding\":" + finalSnapshot[3] + ",\"delivered_bytes\":" + finalSnapshot[4]
                    + ",\"cleanup_bytes_not_delivered\":" + finalSnapshot[5]
                    + ",\"quarantined\":" + quarantined + "}\n");
            audit.flush();
        } catch (IOException | RuntimeException | Error error) {
            if (!freed) { port.quarantine(); quarantined = true; }
            throw error;
        } finally {
            closed = true;
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (quarantined) throw new IOException("Native resources QUARANTINED; no release/close of owned fd", failure);
        if (failure != null) throw failure;
    }
    private static final class Bindings implements Port {
        static { System.loadLibrary("Tbs5530UsbfsDiagnostic"); }
        private final long handle;
        Bindings(int fd, int budget, int chunkBytes) throws IOException {
            handle = create(fd, budget, chunkBytes);
            if (handle == 0) throw new IOException("Native diagnostic allocation failed before submit");
        }
        public int fill() { return NativeUsbfsUrb.fill(handle); }
        public int poll() { return NativeUsbfsUrb.poll(handle); }
        public int take(byte[] bytes) { return NativeUsbfsUrb.take(handle, bytes); }
        public void stop() { NativeUsbfsUrb.stop(handle); }
        public void quarantine() { NativeUsbfsUrb.quarantine(handle); }
        public long[] events() { return NativeUsbfsUrb.events(handle); }
        public long[] snapshot() { return NativeUsbfsUrb.snapshot(handle); }
        public boolean destroy() { return NativeUsbfsUrb.destroy(handle); }
    }
    private static native long create(int fd, int budget, int chunkBytes);
    private static native int fill(long handle);
    private static native int poll(long handle);
    private static native int take(long handle, byte[] bytes);
    private static native void stop(long handle);
    private static native void quarantine(long handle);
    private static native long[] events(long handle);
    private static native long[] snapshot(long handle);
    private static native boolean destroy(long handle);
}
