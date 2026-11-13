// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.FileOutputStream;
import java.io.IOException;

/** Single consumer; no lock or file operation is shared with the native USB producer. */
final class NativeUsbfsStreamWriter implements Runnable {
    interface Sink {
        void write(byte[] bytes, int count) throws IOException;
        void sync() throws IOException;
        void close() throws IOException;
    }
    static Sink fileSink(FileOutputStream output) {
        return new Sink() {
            public void write(byte[] bytes, int count) throws IOException { output.write(bytes, 0, count); }
            public void sync() throws IOException { output.flush(); output.getFD().sync(); }
            public void close() throws IOException { output.close(); }
        };
    }
    private final NativeUsbfsStream.Port port;
    private final Sink sink;
    private final byte[] chunk = new byte[16384];
    private final Thread thread = new Thread(this, "tbs5530-stream-writer");
    private volatile String failure;
    private volatile long writtenBytes;
    private boolean started;

    NativeUsbfsStreamWriter(NativeUsbfsStream.Port port, Sink sink) { this.port = port; this.sink = sink; }
    void start() throws IOException {
        try {
            port.writerAttach();
            thread.start();
            started = true;
        } catch (Throwable error) {
            try { port.writerDetach(); } catch (Throwable detach) { error.addSuppressed(detach); }
            try { sink.close(); } catch (IOException close) { error.addSuppressed(close); }
            if (error instanceof IOException) throw (IOException) error;
            if (error instanceof Error) throw (Error) error;
            throw new IOException("Writer start failed", error);
        }
    }
    @Override public void run() {
        try {
            for (;;) {
                int count = port.writerRead(chunk);
                if (count == -1) break;
                if (count < -1 || count > chunk.length) throw new IOException("Invalid native writer chunk");
                if (count == 0) { Thread.sleep(1); continue; }
                sink.write(chunk, count);
                writtenBytes += count;
            }
            sink.sync();
        } catch (Throwable error) {
            fail(error);
            port.stop();
        } finally {
            try { sink.close(); } catch (Throwable error) { fail(error); }
            try { port.writerDetach(); } catch (Throwable error) { fail(error); }
        }
    }
    private void fail(Throwable error) {
        // Store the type, not a potentially sensitive filesystem path from an exception message.
        if (failure == null) {
            String type = error.getClass().getSimpleName();
            failure = type.isEmpty() ? "Throwable" : type;
        }
    }
    boolean await(long timeoutMillis) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (started && thread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { thread.join(Math.max(1, remaining / 1_000_000L)); }
            catch (InterruptedException error) { interrupted = true; }
        }
        return interrupted;
    }
    boolean alive() { return started && thread.isAlive(); }
    long writtenBytes() { return writtenBytes; }
    String failure() { return failure; }
    void closeUnstarted() throws IOException { if (!started) sink.close(); }
}
