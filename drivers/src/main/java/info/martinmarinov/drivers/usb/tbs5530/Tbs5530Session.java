// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind.SESSION_UNAVAILABLE;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

/** In-memory lifecycle and ownership only; permission and resource events are injected. */
public final class Tbs5530Session implements AutoCloseable {
    public enum State {
        DETECTED, WAIT_PERMISSION, PERMISSION_GRANTED, CONNECTED,
        FIRMWARE_TRANSITION, READY, FAULTED, UNCERTAIN, DISCONNECTED, CLOSED
    }

    public interface Resources {
        /** Stop owned streaming work; must not join the calling thread or send vendor writes. */
        void cancel() throws IOException;
        void release() throws IOException;
        void close() throws IOException;
    }

    private final Object transactionLock = new Object();
    private State state = State.DETECTED;
    private long generation;
    private Resources resources;
    private boolean claimed;
    private boolean cancelNeeded;
    private boolean streaming;
    private boolean frontendLocked;
    private boolean frontendInitialized;
    private boolean firmwareUploaded;
    private boolean cleaning;
    private boolean cleanupRunning;
    private boolean cancellationRequested;
    private int activeTransactions;
    private Throwable failure;
    private IOException cleanupFailure;

    interface Transaction { void run() throws TransferException; }

    void transaction(long token, Transaction operation) throws TransferException {
        TransferException problem = null;
        Cleanup cleanup = null;
        try {
            synchronized (transactionLock) {
                synchronized (this) {
                    requireUsable(token);
                    activeTransactions++;
                }
                try {
                    operation.run();
                } finally {
                    synchronized (this) {
                        activeTransactions--;
                        if (cleaning && activeTransactions == 0) cleanup = takeCleanup();
                    }
                }
            }
        } catch (TransferException e) {
            problem = e;
        } finally {
            // Cleanup may call back or join workers: neither lock may be held here.
            IOException cleanupProblem = finishCleanup(cleanup);
            if (cleanupProblem != null) {
                if (problem != null) problem.addSuppressed(cleanupProblem);
                else problem = new TransferException(SESSION_UNAVAILABLE, "Deferred cleanup failed", cleanupProblem);
            }
        }
        if (problem != null) throw problem;
    }

    public synchronized long requestPermission() {
        if (cleaning || resources != null || !(state == State.DETECTED
                || state == State.CLOSED || state == State.DISCONNECTED)) {
            throw new IllegalStateException("Close the previous session before reopening");
        }
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("Generation exhausted");
        generation++;
        state = State.WAIT_PERMISSION;
        failure = null;
        cleanupFailure = null;
        cancellationRequested = false;
        firmwareUploaded = false;
        frontendInitialized = false;
        return generation;
    }

    public synchronized boolean permissionResult(long token, boolean granted) {
        if (token != generation || state != State.WAIT_PERMISSION) return false;
        state = granted ? State.PERMISSION_GRANTED : State.CLOSED;
        return true;
    }

    public synchronized boolean connected(long token, Resources acquired) {
        if (acquired == null) throw new IllegalArgumentException("Resources are required");
        if (token != generation || state != State.PERMISSION_GRANTED) return false;
        resources = acquired;
        state = State.CONNECTED;
        return true;
    }

    public synchronized void interfaceClaimed(long token) throws TransferException {
        requireUsable(token);
        claimed = true;
    }

    public synchronized void beginFirmwareTransition(long token) throws TransferException {
        requireUsable(token);
        if (state != State.CONNECTED) throw new IllegalStateException("Firmware transition requires connected state");
        firmwareUploaded = false;
        state = State.FIRMWARE_TRANSITION;
    }

    public synchronized void firmwareUploaded(long token) throws TransferException {
        requireUsable(token);
        if (state != State.FIRMWARE_TRANSITION) throw new IllegalStateException("No firmware transition");
        firmwareUploaded = true;
    }

    public synchronized void frontendInitialized(long token) throws TransferException {
        requireUsable(token);
        if (state != State.READY) throw new IllegalStateException("Bridge is not ready");
        frontendInitialized = true;
    }

    /** Caller must establish readiness independently; this does not probe a device. */
    public synchronized void ready(long token) throws TransferException {
        requireUsable(token);
        if (state != State.CONNECTED && state != State.FIRMWARE_TRANSITION) {
            throw new IllegalStateException("Readiness already established");
        }
        state = State.READY;
    }

    public synchronized void streamingStarted(long token) throws TransferException {
        requireUsable(token);
        if (state != State.READY || !claimed) throw new IllegalStateException("Stream requires ready, claimed session");
        streaming = true;
        cancelNeeded = true;
    }

    public synchronized void frontendLock(long token, boolean locked) throws TransferException {
        requireUsable(token);
        if (state != State.READY || !frontendInitialized) throw new IllegalStateException("Frontend is not initialized");
        frontendLocked = locked;
    }

    synchronized void requireUsable(long token) throws TransferException {
        if (cleaning || token != generation || resources == null
                || !(state == State.CONNECTED || state == State.FIRMWARE_TRANSITION || state == State.READY)) {
            throw new TransferException(SESSION_UNAVAILABLE, "Session is " + state + " or generation is stale", failure);
        }
    }

    synchronized void requireOwned(long token, Resources owner) throws TransferException {
        requireConnectionOwned(token, owner);
        if (!claimed) {
            throw new TransferException(SESSION_UNAVAILABLE, "Connection is not the claimed session owner");
        }
    }

    synchronized void requireConnectionOwned(long token, Resources owner) throws TransferException {
        requireUsable(token);
        if (resources != owner) {
            throw new TransferException(SESSION_UNAVAILABLE, "Connection is not the session owner");
        }
    }

    public synchronized boolean failed(long token, Throwable cause) {
        if (token != generation || !(state == State.WAIT_PERMISSION || state == State.PERMISSION_GRANTED
                || state == State.CONNECTED || state == State.FIRMWARE_TRANSITION || state == State.READY)) return false;
        state = state == State.FIRMWARE_TRANSITION ? State.UNCERTAIN : State.FAULTED;
        failure = cause;
        streaming = false;
        frontendLocked = false;
        frontendInitialized = false;
        firmwareUploaded = false;
        return true;
    }

    public boolean detached(long token) throws IOException { return terminate(token, State.DISCONNECTED); }
    public boolean cancel(long token) throws IOException { return terminate(token, State.CLOSED); }

    /** Invalidates now; cleanup waits for the last in-flight transaction without closing its connection early. */
    @Override
    public void close() throws IOException { terminate(null, State.CLOSED); }

    /** Logical invalidation only; safe for a callback that must never run resource cleanup. */
    public synchronized boolean requestCancellation(long token, boolean detached) {
        return invalidate(token, detached ? State.DISCONNECTED : State.CLOSED);
    }

    private synchronized boolean invalidate(Long token, State destination) {
        if (token != null && token != generation) return false;
        if (cleaning) return false;
        if (resources == null && (state == State.CLOSED || state == State.DISCONNECTED)) return false;
        cancellationRequested = true;
        streaming = false;
        frontendLocked = false;
        frontendInitialized = false;
        firmwareUploaded = false;
        state = destination;
        cleaning = true;
        return true;
    }

    /** Run only off the main/broadcast thread. Active transactions drain their own resources. */
    public void drainCleanup() throws IOException {
        Cleanup cleanup;
        synchronized (this) {
            cleanup = cleaning && activeTransactions == 0 ? takeCleanup() : null;
        }
        IOException problem = finishCleanup(cleanup);
        if (problem != null) throw problem;
    }

    private boolean terminate(Long token, State destination) throws IOException {
        boolean accepted = invalidate(token, destination);
        if (accepted) drainCleanup();
        return accepted;
    }

    private static final class Cleanup {
        final Resources owned;
        final boolean cancel, release;
        Cleanup(Resources owned, boolean cancel, boolean release) {
            this.owned = owned; this.cancel = cancel; this.release = release;
        }
    }

    private Cleanup takeCleanup() {
        if (cleanupRunning) return null;
        cleanupRunning = true;
        Cleanup result = new Cleanup(resources, cancelNeeded, claimed);
        resources = null;
        claimed = false;
        cancelNeeded = false;
        return result;
    }

    private IOException finishCleanup(Cleanup cleanup) {
        if (cleanup == null) return null;
        IOException problem = null;
        try {
            if (cleanup.owned != null) {
                if (cleanup.cancel) {
                    try { cleanup.owned.cancel(); } catch (IOException | RuntimeException e) { problem = append(problem, e); }
                }
                if (cleanup.release) {
                    try { cleanup.owned.release(); } catch (IOException | RuntimeException e) { problem = append(problem, e); }
                }
                try { cleanup.owned.close(); } catch (IOException | RuntimeException e) { problem = append(problem, e); }
            }
        } finally {
            synchronized (this) {
                cleanupFailure = problem;
                cleaning = false;
                cleanupRunning = false;
            }
        }
        return problem;
    }

    private static IOException append(IOException first, Exception next) {
        IOException failure = next instanceof IOException ? (IOException) next : new IOException(next);
        if (first == null) return failure;
        if (first != failure) first.addSuppressed(failure);
        return first;
    }

    public synchronized State getState() { return state; }
    public synchronized long getGeneration() { return generation; }
    public synchronized boolean isStreaming() { return streaming; }
    public synchronized boolean isFrontendLocked() { return frontendLocked; }
    public synchronized boolean isInterfaceClaimed() { return claimed; }
    public synchronized boolean isFirmwareUploaded() { return firmwareUploaded; }
    public synchronized boolean isFrontendInitialized() { return frontendInitialized; }
    public synchronized Throwable getFailure() { return failure; }
    public synchronized boolean isCleanupPending() { return cleaning; }
    public synchronized boolean isCancellationRequested() { return cancellationRequested; }
    /** Deferred failures are also suppressed on the transaction error; no exception is thrown on another thread. */
    public synchronized IOException getCleanupFailure() { return cleanupFailure; }
}
