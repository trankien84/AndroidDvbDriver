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
    private final Factory factory;
    private final Failure onFailure;
    private final Tbs5530DetachConfirmation confirmation;
    private final Tbs5530DetachConfirmation.Check stopping;
    private ByteSource source;
    private boolean ready, checkingFailure;
    private volatile boolean closed, checkingDetach;
    private IOException failure;

    Tbs5530StreamGate(Factory factory, boolean ready) {
        this(factory, ready, error -> { }, new Tbs5530DetachConfirmation(() -> true, System::nanoTime, (now, event) -> { }), () -> false);
    }
    Tbs5530StreamGate(Factory factory, boolean ready, Failure onFailure, Tbs5530DetachConfirmation confirmation,
                      Tbs5530DetachConfirmation.Check stopping) {
        this.factory = factory; this.ready = ready; this.onFailure = onFailure;
        this.confirmation = confirmation; this.stopping = stopping;
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
    boolean checkingDetach() { return checkingDetach; }
    synchronized void fail(IOException error) {
        failure = error; ready = false; notifyAll();
        if (source != null) {
            try { source.close(); source = null; }
            catch (IOException | RuntimeException cleanup) { error.addSuppressed(cleanup); }
        }
    }
    @Override public void readNext(ByteSink sink) throws IOException, InterruptedException {
        final byte[][] next = {null};
        IOException error = null;
        synchronized (this) {
            while ((!ready || checkingFailure) && !closed && failure == null) wait();
            if (closed) throw new InterruptedException("TS receive stopped");
            if (failure != null) throw failure;
            try {
                if (source == null) { source = factory.create(); source.open(); }
                // Do not hold this gate while the framework pipe blocks on its consumer.
                source.readNext((bytes, count) -> next[0] = Arrays.copyOf(bytes, count));
            } catch (IOException | RuntimeException e) {
                error = e instanceof IOException ? (IOException) e : new IOException("Native TS receive failed", e);
                checkingFailure = true;
                checkingDetach = error instanceof NativeUsbfsByteSource.ReceiveFailure
                        && ((NativeUsbfsByteSource.ReceiveFailure) error).isDisconnectCandidate();
            }
        }
        if (error != null) {
            try {
                // Android can publish the USB removal after the native receive has already failed.
                if (error instanceof NativeUsbfsByteSource.ReceiveFailure)
                    error = confirmation.nativeFailure((NativeUsbfsByteSource.ReceiveFailure) error, () -> closed || stopping.get());
                synchronized (this) {
                    if (closed || stopping.get()) throw new InterruptedException("TS receive stopped");
                    if (failure == null) {
                        failure = error;
                        onFailure.report(failure);
                    }
                    throw failure;
                }
            } finally {
                synchronized (this) { checkingDetach = false; checkingFailure = false; notifyAll(); }
            }
        }
        if (next[0] != null) sink.consume(next[0], next[0].length);
    }
    @Override public synchronized void close() throws IOException {
        closed = true; ready = false; notifyAll();
        confirmation.wake();
        // Keep the source after an unconfirmed drain so later close can finish cleanup.
        if (source != null) { source.close(); source = null; }
    }
}
