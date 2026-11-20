// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource;

final class Tbs5530DetachConfirmation {
    interface Check { boolean get(); }
    interface Clock { long now(); }
    interface Trace { void log(long nanos, String event); }

    private static final long WINDOW = TimeUnit.SECONDS.toNanos(3);
    private static final long POLL = TimeUnit.MILLISECONDS.toNanos(50);
    private final Check present;
    private final Clock clock;
    private final Trace trace;
    private boolean broadcast, listGone;

    Tbs5530DetachConfirmation(Check present, Clock clock, Trace trace) {
        this.present = present;
        this.clock = clock;
        this.trace = trace;
    }

    synchronized void detached() {
        if (!broadcast) {
            broadcast = true;
            trace.log(clock.now(), "detach_broadcast");
        }
        notifyAll();
    }

    synchronized void wake() { notifyAll(); }

    boolean disconnected() {
        synchronized (this) {
            if (broadcast || listGone) return true;
        }
        boolean missing = !present.get();
        synchronized (this) {
            if (missing && !listGone) {
                listGone = true;
                trace.log(clock.now(), "device_list_gone");
                notifyAll();
            }
            return broadcast || listGone;
        }
    }

    IOException nativeFailure(NativeUsbfsByteSource.ReceiveFailure error, Check stopped) throws InterruptedException {
        long start = clock.now();
        trace.log(start, "native_fail status=" + error.getStatus() + " errno=" + error.getErrno());
        return error.isDisconnectCandidate() ? await(error, start, stopped) : usbError(error, start);
    }

    DvbException controlFailure(DvbException error, Check stopped) throws InterruptedException {
        if (error.getErrorCode() == DvbException.ErrorCode.DEVICE_DISCONNECTED) return error;
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            if (isControlCandidate(cause)) {
                return (DvbException) await(error, clock.now(), stopped);
            }
        }
        return error;
    }

    static boolean isControlCandidate(Throwable error) {
        return error instanceof ControlTransferFailure || (error instanceof Tbs5530ProductionFailure
                && ((Tbs5530ProductionFailure) error).kind == Tbs5530ProductionFailure.Kind.REMOVED);
    }

    private IOException await(IOException original, long start, Check stopped) throws InterruptedException {
        try {
            while (true) {
                if (stopped.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                boolean confirmed = disconnected();
                synchronized (this) {
                    if (stopped.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (confirmed || broadcast || listGone) {
                        decision("DISCONNECTED", start);
                        return Tbs5530StreamGate.disconnected(original);
                    }
                    long remaining = WINDOW - (clock.now() - start);
                    if (remaining <= 0) return usbError(original, start);
                    TimeUnit.NANOSECONDS.timedWait(this, Math.min(POLL, remaining));
                }
            }
        } catch (InterruptedException stoppedWaiting) {
            decision("STOPPED", start);
            throw stoppedWaiting;
        }
    }

    private IOException usbError(IOException error, long start) {
        decision("USB_ERROR", start);
        return error;
    }

    private void decision(String result, long start) {
        long now = clock.now();
        trace.log(now, "decision=" + result + " waited_ms=" + TimeUnit.NANOSECONDS.toMillis(now - start));
    }

    static final class ControlTransferFailure extends IOException {
        ControlTransferFailure(int status) { super("USB control transfer failed: status=" + status); }
    }
}
