// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** Single-worker receive queue. The port retains every buffer until completion has been drained. */
final class Tbs5530QueuedReceiver implements AutoCloseable {
    static final int DEPTH = 8, CHUNK = 4096;
    interface Port {
        void submit(int slot) throws IOException;
        Completion await(int timeoutMillis) throws IOException;
        void cancel(int slot) throws IOException;
        void copy(int slot, byte[] bytes, int length) throws IOException;
        void release() throws IOException;
    }
    interface Clock { long millis(); }
    static final class Completion {
        final int slot, length;
        Completion(int slot, int length) { this.slot = slot; this.length = length; }
    }
    static final class Snapshot {
        final long submitted, completed, deliveredBytes, cleanupBytes;
        final int outstanding;
        final boolean uncertain;
        Snapshot(Tbs5530QueuedReceiver value) {
            submitted = value.submitted; completed = value.completed; deliveredBytes = value.deliveredBytes;
            cleanupBytes = value.cleanupBytes; outstanding = value.outstanding; uncertain = value.uncertain;
        }
    }
    private final Port port;
    private final Clock clock;
    private final Thread worker = Thread.currentThread();
    private final boolean[] pending = new boolean[DEPTH], ready = new boolean[DEPTH];
    private final int[] lengths = new int[DEPTH];
    private boolean started, stopped, released, uncertain;
    private int outstanding, expected;
    private long submitted, completed, deliveredBytes, cleanupBytes;

    Tbs5530QueuedReceiver(Port port, Clock clock) { this.port = Objects.requireNonNull(port); this.clock = Objects.requireNonNull(clock); }
    private void worker() throws IOException {
        if (Thread.currentThread() != worker) throw new IOException("Queued receive worker changed");
    }
    int read(byte[] bytes, int length, int timeoutMillis) throws IOException {
        worker();
        if (stopped || uncertain) throw new IOException("Queued receive stopped; no retry");
        if (bytes == null || length != CHUNK || bytes.length < length || timeoutMillis < 1 || timeoutMillis > 2000) {
            throw new IllegalArgumentException("Queued receive requires bounded 4096-byte reads");
        }
        try {
            if (!started) {
                started = true;
                for (int slot = 0; slot < DEPTH; slot++) submit(slot);
            }
            long begin = clock.millis();
            while (!ready[expected]) {
                long remaining = timeoutMillis - elapsed(begin);
                if (remaining <= 0) throw new IOException("Queued receive deadline reached; no retry");
                Completion next = port.await((int) remaining);
                if (next == null) throw new IOException("Queued receive returned no completion; native status unknown");
                complete(next);
            }
            int count = lengths[expected];
            port.copy(expected, bytes, count);
            deliveredBytes += count;
            ready[expected] = false;
            if (count > 0) submit(expected);
            else stopped = true;
            expected = (expected + 1) % DEPTH;
            return count;
        } catch (IOException | RuntimeException error) {
            stopped = true;
            throw error;
        }
    }
    private void submit(int slot) throws IOException {
        if (pending[slot] || ready[slot]) throw new IOException("Receive slot still owned");
        // A throwing native submit has unknown ownership; retain the slot for cancellation/drain.
        pending[slot] = true; outstanding++;
        try { port.submit(slot); submitted++; }
        catch (IOException | RuntimeException error) { uncertain = true; throw error; }
    }
    private void complete(Completion next) throws IOException {
        if (next.slot < 0 || next.slot >= DEPTH || !pending[next.slot] || ready[next.slot]
                || next.length < 0 || next.length > CHUNK) {
            uncertain = true;
            throw new IOException("Unknown, duplicate or invalid queued completion");
        }
        pending[next.slot] = false; outstanding--; completed++;
        ready[next.slot] = true; lengths[next.slot] = next.length;
    }
    private long elapsed(long begin) throws IOException {
        long elapsed = clock.millis() - begin;
        if (elapsed < 0) throw new IOException("Queued receive clock moved backwards");
        return elapsed;
    }
    Snapshot snapshot() { return new Snapshot(this); }
    @Override public void close() throws IOException {
        worker(); stopped = true;
        if (released) return;
        IOException failure = null;
        for (int slot = 0; slot < DEPTH; slot++) if (pending[slot]) {
            try { port.cancel(slot); }
            catch (IOException | RuntimeException error) {
                if (failure == null) failure = new IOException("Queued cancellation failed", error);
                else failure.addSuppressed(error);
            }
        }
        long begin = clock.millis();
        try {
            while (outstanding != 0) {
                long remaining = 1000 - elapsed(begin);
                if (remaining <= 0) throw new IOException("Queued drain deadline; connection must remain quarantined");
                Completion next = port.await((int) remaining);
                if (next == null) throw new IOException("Queued drain returned unknown completion");
                complete(next);
            }
            for (int slot = 0; slot < DEPTH; slot++) if (ready[slot]) {
                cleanupBytes += lengths[slot]; ready[slot] = false;
            }
            port.release(); released = true;
        } catch (IOException | RuntimeException error) {
            uncertain = true;
            if (failure == null) failure = new IOException("Queued cleanup uncertain; no release/close of connection", error);
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
