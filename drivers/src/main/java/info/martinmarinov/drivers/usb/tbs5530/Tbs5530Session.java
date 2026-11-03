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
        void cancel() throws IOException;
        void release() throws IOException;
        void close() throws IOException;
    }

    private State state = State.DETECTED;
    private long generation;
    private Resources resources;
    private boolean claimed;
    private boolean cancelNeeded;
    private boolean streaming;
    private boolean frontendLocked;
    private boolean cleaning;
    private Throwable failure;

    public synchronized long requestPermission() {
        if (cleaning || resources != null || !(state == State.DETECTED
                || state == State.CLOSED || state == State.DISCONNECTED)) {
            throw new IllegalStateException("Close the previous session before reopening");
        }
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("Generation exhausted");
        generation++;
        state = State.WAIT_PERMISSION;
        failure = null;
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
        state = State.FIRMWARE_TRANSITION;
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
        if (state != State.READY) throw new IllegalStateException("Frontend is not ready");
        frontendLocked = locked;
    }

    synchronized void requireUsable(long token) throws TransferException {
        if (cleaning || token != generation || resources == null
                || !(state == State.CONNECTED || state == State.FIRMWARE_TRANSITION || state == State.READY)) {
            throw new TransferException(SESSION_UNAVAILABLE, "Session is " + state + " or generation is stale", failure);
        }
    }

    public synchronized boolean failed(long token, Throwable cause) {
        if (token != generation || !(state == State.CONNECTED
                || state == State.FIRMWARE_TRANSITION || state == State.READY)) return false;
        state = state == State.FIRMWARE_TRANSITION ? State.UNCERTAIN : State.FAULTED;
        failure = cause;
        streaming = false;
        frontendLocked = false;
        return true;
    }

    public boolean detached(long token) throws IOException { return terminate(token, State.DISCONNECTED); }
    public boolean cancel(long token) throws IOException { return terminate(token, State.CLOSED); }

    @Override
    public void close() throws IOException { terminate(null, State.CLOSED); }

    private boolean terminate(Long token, State destination) throws IOException {
        Resources owned;
        boolean cancel;
        boolean release;
        synchronized (this) {
            if (token != null && token != generation) return false;
            if (cleaning) return false;
            if (resources == null && (state == State.CLOSED || state == State.DISCONNECTED)) return false;
            owned = resources;
            cancel = cancelNeeded;
            release = claimed;
            resources = null;
            claimed = false;
            cancelNeeded = false;
            streaming = false;
            frontendLocked = false;
            state = destination;
            cleaning = true;
        }
        // Never hold the transfer monitor while cancellation may join a worker.
        IOException problem = null;
        try {
            if (owned != null) {
                if (cancel) {
                    try { owned.cancel(); } catch (IOException e) { problem = e; }
                    catch (RuntimeException e) { problem = new IOException("Cancel failed", e); }
                }
                if (release) {
                    try { owned.release(); } catch (IOException | RuntimeException e) { problem = append(problem, e); }
                }
                try { owned.close(); } catch (IOException | RuntimeException e) { problem = append(problem, e); }
            }
        } finally {
            synchronized (this) { cleaning = false; }
        }
        if (problem != null) throw problem;
        return true;
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
    public synchronized Throwable getFailure() { return failure; }
}
