// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * One acquisition/cleanup worker and one independent logical-deadline thread per owner.
 * Close the binding before this runtime. Shutdown drains queued cleanup without joining
 * the caller or pretending to interrupt a blocked UsbManager open/claim.
 */
public final class Tbs5530AndroidRuntime implements Executor, Tbs5530UsbBinding.Scheduler, AutoCloseable {
    private final ThreadLocal<Boolean> ownedThread = new ThreadLocal<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(named("tbs5530-usb-worker"));
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, named("tbs5530-usb-deadline"));

    public Tbs5530AndroidRuntime() {
        deadlines.setRemoveOnCancelPolicy(true);
        deadlines.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    private ThreadFactory named(String name) {
        return task -> {
            Thread thread = new Thread(() -> {
                ownedThread.set(Boolean.TRUE);
                try { task.run(); } finally { ownedThread.remove(); }
            }, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override public void execute(Runnable task) { worker.execute(task); }

    @Override public Tbs5530UsbBinding.Cancellation schedule(Runnable task, long delayMillis) {
        if (delayMillis < 0) throw new IllegalArgumentException("Negative deadline");
        java.util.concurrent.ScheduledFuture<?> future = deadlines.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override public void close() {
        deadlines.shutdown();
        worker.shutdown();
    }

    public boolean isTerminated() { return worker.isTerminated() && deadlines.isTerminated(); }

    /** Optional bounded drain for background owners/tests only; never call on Android's main thread. */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper() || Boolean.TRUE.equals(ownedThread.get())) {
            throw new IllegalStateException("Cannot join main or this runtime's own threads");
        }
        if (timeout < 0) throw new IllegalArgumentException("Negative timeout");
        long budget = unit.toNanos(timeout);
        long started = System.nanoTime();
        if (!worker.awaitTermination(budget, TimeUnit.NANOSECONDS)) return false;
        long remaining = Math.max(0, budget - (System.nanoTime() - started));
        return deadlines.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }
}
