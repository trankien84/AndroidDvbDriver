// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Arrays;
import info.martinmarinov.usbxfer.ByteSink;
import info.martinmarinov.usbxfer.ByteSource;

/** Lazy receive: the framework opens its pipe before tuning, and retunes without replacing that pipe. */
final class Tbs5530StreamGate implements ByteSource {
    interface Factory { ByteSource create() throws IOException; }
    interface Failure { void report(IOException error); }
    private final Factory factory;
    private final Failure onFailure;
    private ByteSource source;
    private boolean ready, closed;
    private IOException failure;

    Tbs5530StreamGate(Factory factory, boolean ready) { this(factory, ready, error -> { }); }
    Tbs5530StreamGate(Factory factory, boolean ready, Failure onFailure) {
        this.factory = factory; this.ready = ready; this.onFailure = onFailure;
    }
    @Override public void open() { }
    synchronized void resume() throws IOException {
        if (closed || failure != null) throw new IOException("TS receive gate is closed", failure);
        ready = true; notifyAll();
    }
    synchronized void pause() throws IOException {
        ready = false;
        if (source != null) {
            ByteSource previous = source; source = null;
            try { previous.close(); }
            catch (IOException e) { failure = e; throw e; }
        }
    }
    synchronized boolean active() { return source != null; }
    synchronized void fail(IOException error) {
        failure = error; ready = false; notifyAll();
        if (source != null) {
            ByteSource previous = source; source = null;
            try { previous.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
        }
    }
    @Override public void readNext(ByteSink sink) throws IOException, InterruptedException {
        final byte[][] next = {null};
        synchronized (this) {
            while (!ready && !closed && failure == null) wait();
            if (closed || failure != null) throw new IOException("TS receive stopped", failure);
            try {
                if (source == null) { source = factory.create(); source.open(); }
                // Do not hold this gate while the framework pipe blocks on its consumer.
                source.readNext((bytes, count) -> next[0] = Arrays.copyOf(bytes, count));
            } catch (IOException | RuntimeException e) {
                failure = e instanceof IOException ? (IOException) e : new IOException("Native TS receive failed", e);
                onFailure.report(failure);
                throw failure;
            }
        }
        if (next[0] != null) sink.consume(next[0], next[0].length);
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; ready = false; notifyAll();
        if (source != null) { ByteSource previous = source; source = null; previous.close(); }
    }
}
