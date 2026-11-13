// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded RAM samples; the caller holds the receive lease until close has joined this worker. */
final class Tbs5530SignalSampler implements AutoCloseable {
    interface Factory { SampleOperation create(Thread thread, AtomicBoolean stopping) throws IOException; }
    interface SampleOperation { Tbs5530SignalMonitor.Sample sample() throws IOException; }
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final CountDownLatch ready = new CountDownLatch(1);
    private final List<Tbs5530SignalMonitor.Sample> samples = new ArrayList<>(256);
    private final Thread thread;
    private volatile boolean lockLost;
    private volatile String failure;

    Tbs5530SignalSampler(Factory factory) { this(factory, false); }

    Tbs5530SignalSampler(Factory factory, boolean b5) {
        thread = new Thread(() -> {
            try {
                SampleOperation operation = factory.create(Thread.currentThread(), stopping);
                ready.countDown();
                while (!stopping.get()) {
                    long started = System.nanoTime();
                    try {
                        Tbs5530SignalMonitor.Sample sample = operation.sample();
                        synchronized (samples) {
                            if (samples.size() == 512) { failure = "MONITOR_SAMPLE_CAPACITY"; break; }
                            samples.add(sample);
                        }
                        if (sample.stopForLockLoss) { lockLost = true; break; }
                    } catch (IOException | RuntimeException error) {
                        if (!b5) throw error;
                        failure = "MONITOR_SAMPLE_ERROR:" + error.getClass().getSimpleName();
                    }
                    long tick = b5 ? java.util.concurrent.ThreadLocalRandom.current().nextLong(
                            80_000_000L, 120_000_001L) : Tbs5530SignalMonitor.TICK_NANOS;
                    long remaining = tick - (System.nanoTime() - started);
                    if (remaining > 0 && !stopping.get()) TimeUnit.NANOSECONDS.sleep(remaining);
                }
            } catch (InterruptedException error) {
                if (!stopping.get()) failure = "MONITOR_INTERRUPTED";
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException error) {
                failure = "MONITOR_WORKER_ERROR:" + error.getClass().getSimpleName();
            } finally { ready.countDown(); }
        }, "tbs5530-signal-monitor");
    }
    void start() throws IOException {
        thread.start();
        try {
            if (!ready.await(2, TimeUnit.SECONDS)) throw new IOException("Monitor startup timed out");
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
    }
    boolean lockLost() { return lockLost; }
    String failure() { return failure; }
    List<Tbs5530SignalMonitor.Sample> samples() {
        synchronized (samples) { return Collections.unmodifiableList(new ArrayList<>(samples)); }
    }
    @Override public void close() throws IOException {
        stopping.set(true);
        boolean interrupted = false;
        for (;;) {
            try { thread.join(); break; }
            catch (InterruptedException error) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
