// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Arrays;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.usbxfer.ByteSink;
import info.martinmarinov.usbxfer.ByteSource;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource;

/** Lazy receive: the framework opens its pipe before tuning, and retunes without replacing that pipe. */
final class Tbs5530StreamGate implements ByteSource {
    interface Factory { ByteSource create() throws IOException; }
    interface Failure { void report(IOException error); }
    interface Attachment { boolean disconnected(); }
    private final Factory factory;
    private final Failure onFailure;
    private final Attachment attachment;
    private ByteSource source;
    private boolean ready, closed;
    private IOException failure;

    Tbs5530StreamGate(Factory factory, boolean ready) { this(factory, ready, error -> { }, () -> false); }
    Tbs5530StreamGate(Factory factory, boolean ready, Failure onFailure, Attachment attachment) {
        this.factory = factory; this.ready = ready; this.onFailure = onFailure; this.attachment = attachment;
    }
    static DvbException disconnected(IOException cause) {
        return new DvbException(DvbException.ErrorCode.DEVICE_DISCONNECTED, "TBS5530 detached during streaming", cause);
    }

    @Override public void open() { }
    synchronized void resume() throws IOException {
        if (closed || failure != null) throw new IOException("TS receive gate is closed", failure);
        ready = true; notifyAll();
    }
    synchronized void pause() throws IOException {
        ready = false;
        if (source != null) {
            try { source.close(); source = null; }
            catch (IOException e) { failure = e; throw e; }
        }
    }
    synchronized boolean active() { return source != null; }
    synchronized boolean drained() { return source == null; }
    synchronized void fail(IOException error) {
        failure = error; ready = false; notifyAll();
        if (source != null) {
            try { source.close(); source = null; }
            catch (IOException | RuntimeException cleanup) { error.addSuppressed(cleanup); }
        }
    }
    @Override public void readNext(ByteSink sink) throws IOException, InterruptedException {
        final byte[][] next = {null};
        synchronized (this) {
            while (!ready && !closed && failure == null) wait();
            if (failure != null) throw failure;
            if (closed) throw new IOException("TS receive stopped");
            try {
                if (source == null) { source = factory.create(); source.open(); }
                // Do not hold this gate while the framework pipe blocks on its consumer.
                source.readNext((bytes, count) -> next[0] = Arrays.copyOf(bytes, count));
            } catch (IOException | RuntimeException e) {
                failure = e instanceof IOException ? (IOException) e : new IOException("Native TS receive failed", e);
                if (e instanceof NativeUsbfsByteSource.ReceiveFailure
                        && ((NativeUsbfsByteSource.ReceiveFailure) e).isDisconnectCandidate() && attachment.disconnected()) {
                    failure = disconnected(failure);
                }
                onFailure.report(failure);
                throw failure;
            }
        }
        if (next[0] != null) sink.consume(next[0], next[0].length);
    }
    @Override public synchronized void close() throws IOException {
        closed = true; ready = false; notifyAll();
        // Keep the source after an unconfirmed drain so later close can finish cleanup.
        if (source != null) { source.close(); source = null; }
    }
}
