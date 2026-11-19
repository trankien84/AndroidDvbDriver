// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;

/**
 * One-shot, continuous Bulk IN 0x82 source (16 requests of 16384 bytes, 4 MiB ring).
 * Pass a NEW, unclaimed connection, never the driver's control connection or a
 * duplicate of it. Ownership transfers at construction; do not use/close it again.
 * Only interface 0 / alternate 0 from the pinned operational descriptors is accepted.
 * open claims that interface, clears halt on 0x82, and transfers its sole fd to
 * native code before run. Unlike the debug core, this source owns that standard
 * endpoint preparation. It sends neither SET_INTERFACE (request 11) nor GET_INTERFACE.
 * Do not add a driver-side interface selection or duplicate clear-halt sequence.
 * Call open only after firmware, frontend tuning and stream selection are ready.
 * One consumer calls readNext; its sink must consume synchronously without keeping
 * the reused array. Slow consumers fail closed on ring overflow, never drop silently.
 * requestStop is nonblocking; close joins cancellation/drain and releases resources.
 * Both may be called concurrently with readNext, including from a sink callback.
 */
public final class NativeUsbfsByteSource implements ByteSource {
    public static final int REQUEST_COUNT = 16;
    public static final int BUFFER_SIZE = 16384;

    /** Bounded queue presets; chunk size stays 16384. Hardware validation is separate. */
    public enum Configuration {
        DEFAULT(16), DEEP_QUEUE(32);
        private final int requests;
        Configuration(int requests) { this.requests = requests; }
        public int getRequestCount() { return requests; }
    }

    interface Backend {
        long create(int fd, int iface, int alt, int requests) throws IOException;
        int run(long handle);
        int[] errorDetails(long handle) throws IOException;
        long[] completions(long handle) throws IOException;
        void stop(long handle);
        int read(long handle, byte[] data) throws IOException;
        boolean destroy(long handle) throws IOException;
    }

    private static final class JniBackend implements Backend {
        JniBackend() throws IOException {
            try { System.loadLibrary("UsbXfer"); }
            catch (LinkageError e) { throw new IOException("Native TS library unavailable", e); }
        }
        @Override public long create(int fd, int iface, int alt, int requests) throws IOException { return nativeCreate(fd, iface, alt, requests); }
        @Override public int run(long handle) { return nativeRun(handle); }
        @Override public int[] errorDetails(long handle) throws IOException { return nativeErrorDetails(handle); }
        @Override public long[] completions(long handle) { return nativeCompletions(handle); }
        @Override public void stop(long handle) { nativeStop(handle); }
        @Override public int read(long handle, byte[] data) throws IOException { return nativeRead(handle, data); }
        @Override public boolean destroy(long handle) throws IOException { return nativeDestroy(handle); }
    }

    private final Object lifecycle = new Object();
    private final Object reader = new Object();
    private final UsbDeviceConnection connection;
    private final AlternateUsbInterface usbInterface;
    private final Configuration configuration;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private final Tbs5530TsPrefixFilter prefix = new Tbs5530TsPrefixFilter(BUFFER_SIZE);
    private Backend backend;
    private long handle;
    private Thread worker;
    private boolean used, stopping, closed, connectionClosed, reading;
    private volatile IOException failure;
    private CompletionSnapshot finalSnapshot;

    /** Immutable userspace REAPURBNDELAY observation times (CLOCK_MONOTONIC),
     * not kernel IRQ/device emission times or Java read sizes/times.
     * A live snapshot is a prefix only. Overflow/invalid clock or a missing final
     * snapshot makes timing/duty UNKNOWN; this API never certifies duty itself.
     * Cancellation/drain completions are excluded; error data completions remain.
     */
    public static final class CompletionSnapshot {
        public static final int CAPACITY = 65536;
        private final long[] values;
        private final long discardedPrefixBytes;
        private final boolean prefixLocked;
        private CompletionSnapshot(long[] values, long discardedPrefixBytes, boolean prefixLocked) throws IOException {
            if (values == null || values.length < 8 || values[0] != CAPACITY
                    || values[1] < 0 || values[1] > CAPACITY || values.length != 8 + values[1] * 3)
                throw new IOException("Invalid native TS completion snapshot");
            this.values = values;
            this.discardedPrefixBytes = discardedPrefixBytes;
            this.prefixLocked = prefixLocked;
        }
        public long getDiscardedPrefixBytes() { return discardedPrefixBytes; }
        public boolean isPrefixLocked() { return prefixLocked; }
        public int getRecordCount() { return (int) values[1]; }
        public int getCapacity() { return CAPACITY; }
        public boolean isOverflow() { return values[2] != 0; }
        public boolean isTruncated() { return isOverflow(); }
        /** Complete timing coverage only, not proof of healthy TS or acceptable duty. */
        public boolean isComplete() {
            return isFinished() && !isOverflow() && !isClockInvalid()
                    && getWindowStartMonotonicNanos() >= 0
                    && getWindowEndMonotonicNanos() > getWindowStartMonotonicNanos();
        }
        public boolean isClockInvalid() { return values[3] != 0; }
        public boolean isFinished() { return values[4] != 0; }
        public int getStopReason() { return (int) values[5]; }
        /** Start of the native run; -1 before start or if clock failed. */
        public long getWindowStartMonotonicNanos() { return values[6]; }
        /** End of receive loop, before cancellation/drain; -1 while running. */
        public long getWindowEndMonotonicNanos() { return values[7]; }
        private int at(int index) {
            if (index < 0 || index >= getRecordCount()) throw new IndexOutOfBoundsException();
            return 8 + index * 3;
        }
        public long getMonotonicNanos(int index) { return values[at(index)]; }
        public int getStatus(int index) { return (int) values[at(index) + 1]; }
        public int getLength(int index) { return (int) values[at(index) + 2]; }
    }

    /** Bounded copy outside the producer loop; callable during run or after close. */
    public CompletionSnapshot snapshotCompletions() throws IOException {
        synchronized (lifecycle) {
            if (handle != 0) return captureSnapshot(backend.completions(handle));
            if (finalSnapshot != null) return finalSnapshot;
            return captureSnapshot(new long[] {CompletionSnapshot.CAPACITY, 0, 0, 0, 0, 0, -1, -1});
        }
    }

    private CompletionSnapshot captureSnapshot(long[] values) throws IOException {
        return new CompletionSnapshot(values, prefix.discardedBytes(), prefix.isLocked());
    }

    public NativeUsbfsByteSource(UsbDeviceConnection ownedConnection, AlternateUsbInterface usbInterface) {
        this(ownedConnection, usbInterface, Configuration.DEFAULT, null);
    }

    public NativeUsbfsByteSource(UsbDeviceConnection ownedConnection, AlternateUsbInterface usbInterface,
                                Configuration configuration) {
        this(ownedConnection, usbInterface, configuration, null);
    }

    NativeUsbfsByteSource(UsbDeviceConnection connection, AlternateUsbInterface usbInterface, Backend backend) {
        this(connection, usbInterface, Configuration.DEFAULT, backend);
    }

    NativeUsbfsByteSource(UsbDeviceConnection connection, AlternateUsbInterface usbInterface,
                         Configuration configuration, Backend backend) {
        if (connection == null || usbInterface == null || configuration == null)
            throw new NullPointerException("Connection/interface/configuration required");
        this.connection = connection;
        this.usbInterface = usbInterface;
        this.configuration = configuration;
        this.backend = backend;
    }

    @Override public void open() throws IOException {
        synchronized (lifecycle) {
            if (used || closed || stopping) throw new IOException("Native TS source is one-shot");
            used = true;
            try {
                int iface = usbInterface.getUsbInterface().getId();
                int alt = usbInterface.getAlternateSettings();
                if (iface != 0 || alt != 0) throw new IOException("Native TS requires interface 0 / alternate 0");
                if (backend == null) backend = new JniBackend();
                handle = backend.create(connection.getFileDescriptor(), iface, alt, configuration.getRequestCount());
                if (handle == 0) throw new IOException("Native TS creation failed");
                closeConnection();
                final long activeHandle = handle;
                worker = new Thread(() -> {
                    try {
                        int reason = backend.run(activeHandle);
                        int[] detail = backend.errorDetails(activeHandle);
                        if (detail == null || detail.length != 3 || detail[0] != reason)
                            throw new IOException("Invalid native TS error details");
                        if (reason != 1 || detail[1] != 0 || detail[2] != 0)
                            failure = new IOException("Native TS failed: reason=" + detail[0]
                                    + ",status=" + detail[1] + ",errno=" + detail[2]);
                    } catch (IOException | RuntimeException | Error e) {
                        failure = new IOException("Native TS worker failed", e);
                    }
                }, "Tbs5530-TS");
                worker.start();
            } catch (IOException | RuntimeException | Error e) {
                closeConnection();
                if (handle != 0) {
                    try { backend.destroy(handle); }
                    catch (IOException cleanup) { e.addSuppressed(cleanup); }
                    handle = 0;
                }
                closed = true;
                throw e;
            }
        }
    }

    private void closeConnection() {
        if (!connectionClosed) {
            connection.close();
            connectionClosed = true;
        }
    }

    @Override public void readNext(ByteSink sink) throws IOException, InterruptedException {
        if (sink == null) throw new NullPointerException("Sink required");
        synchronized (reader) {
            if (reading) throw new IOException("Recursive TS read is unsupported");
            reading = true;
            try {
                int count;
                int filtered = 0;
                Thread active;
                synchronized (lifecycle) {
                    if (failure != null) throw failure;
                    if (closed || stopping || handle == 0) throw new IOException("Native TS source is not running");
                    count = backend.read(handle, buffer);
                    if (count > 0 && count <= BUFFER_SIZE) filtered = prefix.filter(buffer, count);
                    active = worker;
                }
                if (count < 0) {
                    requestStop();
                    // Finish drain before reading the worker's error, without holding
                    // lifecycle: concurrent close must still be able to stop/join.
                    if (active != null) active.join();
                    if (failure != null) throw failure;
                    throw new IOException("Native TS receive failed (" + count + ")");
                }
                if (count > BUFFER_SIZE) throw new IOException("Native TS receive failed (" + count + ")");
                if (count == 0) Thread.sleep(2);
                else if (filtered > 0) sink.consume(prefix.output(), filtered);
            } catch (IOException | InterruptedException | RuntimeException | Error e) {
                requestStop();
                throw e;
            } finally {
                reading = false;
            }
        }
    }

    public void requestStop() {
        synchronized (lifecycle) {
            stopping = true;
            if (handle != 0) backend.stop(handle);
        }
    }

    @Override public void close() throws IOException {
        Thread active;
        synchronized (lifecycle) {
            if (closed) return;
            stopping = true;
            if (handle != 0) backend.stop(handle);
            active = worker;
        }
        // Never hold the lifecycle lock while joining the producer.
        boolean interrupted = false;
        if (active != null) for (;;) {
            try { active.join(); break; }
            catch (InterruptedException e) { interrupted = true; }
        }
        try {
            synchronized (lifecycle) {
                if (closed) return;
                closeConnection();
                if (handle != 0) {
                    boolean drained;
                    prefix.discardPending();
                    try { finalSnapshot = captureSnapshot(backend.completions(handle)); }
                    finally {
                        drained = backend.destroy(handle);
                        handle = 0;
                        closed = true;
                    }
                    if (!drained) {
                        IOException drain = new IOException("USB drain failed; sole streaming fd was closed safely");
                        if (failure == null) throw drain;
                        failure.addSuppressed(drain);
                    }
                }
                closed = true;
                if (failure != null) throw failure;
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static native long nativeCreate(int fd, int iface, int alt, int requests) throws IOException;
    private static native int nativeRun(long handle);
    private static native int[] nativeErrorDetails(long handle) throws IOException;
    private static native long[] nativeCompletions(long handle);
    private static native void nativeStop(long handle);
    private static native int nativeRead(long handle, byte[] data) throws IOException;
    private static native boolean nativeDestroy(long handle) throws IOException;
}
