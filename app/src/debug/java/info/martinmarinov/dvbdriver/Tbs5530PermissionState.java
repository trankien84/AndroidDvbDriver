// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Permission evidence is attachment-scoped, and never constitutes a session grant. */
final class Tbs5530PermissionState {
    static final int VID = 0x734c, PID = 0x5530;
    static final long RECEIPT_MAX_AGE_MS = 30000;
    enum State { UNVERIFIED, PENDING, CALLBACK_GRANTED, GRANTED, DENIED, INVALIDATED, CANCELLED }
    enum Outcome { AUTO_PERMISSION_HANDLED_BY_OS, AUTO_PERMISSION_UI_HANDLED, USER_ACTION_REQUIRED, PERMISSION_DENIED, UNVERIFIED }
    enum Callback { IGNORED, ACCEPTED, DENIED, INVALIDATED }
    enum Attach { UNSELECTED, UNRELATED, AMBIGUOUS_DUPLICATE, MISMATCH }
    static final class Attempt {
        final String token;
        final Device device;
        final long generation, requestedAt;
        Attempt(Device device, long generation, long now) {
            this.token = UUID.randomUUID().toString(); this.device = device;
            this.generation = generation; this.requestedAt = now;
        }
    }
    private Attempt attempt;
    private long generation;
    private State state = State.UNVERIFIED;
    private Outcome outcome = Outcome.UNVERIFIED;
    private boolean dispatched, uiProof, verifiedGrantAtStop;

    static boolean target(Device device) { return device != null && device.vendorId == VID && device.productId == PID; }
    static Device single(List<Device> devices) throws IOException {
        Device found = null;
        for (Device device : devices) if (target(device)) {
            if (found != null) throw new IOException("Multiple matching USB devices");
            found = device;
        }
        return found;
    }
    synchronized Attempt begin(Device device, boolean osPermission, long now) throws IOException {
        if (!target(device) || now < 0 || attempt != null || state != State.UNVERIFIED)
            throw new IOException("Permission attempt cannot be reused");
        attempt = new Attempt(device, generation, now);
        state = osPermission ? State.GRANTED : State.PENDING;
        outcome = osPermission ? Outcome.AUTO_PERMISSION_HANDLED_BY_OS : Outcome.USER_ACTION_REQUIRED;
        return attempt;
    }
    synchronized void dispatched(Attempt expected) throws IOException {
        if (attempt != expected || state != State.PENDING) throw new IOException("Stale permission dispatch");
        dispatched = true;
    }
    synchronized Callback callback(String token, long expectedGeneration, Device device, Boolean allowed) {
        if (attempt == null || state != State.PENDING || !attempt.token.equals(token)
                || generation != expectedGeneration || attempt.generation != expectedGeneration) return Callback.IGNORED;
        dispatched = false;
        if (!attempt.device.equals(device) || allowed == null) {
            invalidate(); return Callback.INVALIDATED;
        }
        if (!allowed) { state = State.DENIED; outcome = Outcome.PERMISSION_DENIED; return Callback.DENIED; }
        state = State.CALLBACK_GRANTED;
        outcome = Outcome.UNVERIFIED;
        return Callback.ACCEPTED;
    }
    synchronized void confirmFresh(Device fresh, boolean osPermission) throws IOException {
        if (attempt == null || !attempt.device.equals(fresh) || attempt.generation != generation
                || !osPermission || state != State.CALLBACK_GRANTED && state != State.GRANTED) {
            invalidate(); throw new IOException("Fresh attachment and OS permission required");
        }
        state = State.GRANTED;
        if (uiProof && outcome != Outcome.AUTO_PERMISSION_HANDLED_BY_OS) outcome = Outcome.AUTO_PERMISSION_UI_HANDLED;
    }
    synchronized void requireReady(Device fresh) throws IOException {
        if (attempt == null || state != State.GRANTED || generation != attempt.generation || !attempt.device.equals(fresh))
            throw new IOException("Permission pending, cancelled or attachment changed");
    }
    synchronized boolean uiReceipt(String token, long expectedGeneration, Device device, long observed, long clicked, long now) {
        boolean terminalEvidenceOnly = state == State.CANCELLED && verifiedGrantAtStop;
        if (attempt == null || !attempt.token.equals(token)
                || expectedGeneration != attempt.generation || !attempt.device.equals(device)
                || !terminalEvidenceOnly && (expectedGeneration != generation
                || state != State.PENDING && state != State.CALLBACK_GRANTED && state != State.GRANTED)
                || observed < attempt.requestedAt || clicked < observed || now < clicked
                || now - clicked >= RECEIPT_MAX_AGE_MS) return false;
        uiProof = true;
        if ((state == State.GRANTED || terminalEvidenceOnly) && outcome != Outcome.AUTO_PERMISSION_HANDLED_BY_OS)
            outcome = Outcome.AUTO_PERMISSION_UI_HANDLED;
        return true;
    }
    synchronized boolean mayPause(boolean ownerOrWorkExists) {
        return !ownerOrWorkExists && state == State.PENDING && dispatched && attempt != null
                && attempt.generation == generation;
    }
    synchronized Attach attach(Device device) {
        if (!target(device)) return Attach.UNRELATED;
        if (attempt == null) return Attach.UNSELECTED;
        Attach result = attempt.device.equals(device) ? Attach.AMBIGUOUS_DUPLICATE : Attach.MISMATCH;
        // Android supplies no event sequence number. Equality alone cannot rule out path/id reuse.
        invalidate(); return result;
    }
    synchronized void invalidate() { generation++; state = State.INVALIDATED; outcome = Outcome.UNVERIFIED; dispatched = false; uiProof = false; }
    synchronized void firmwareTransition() throws IOException {
        if (state != State.GRANTED) throw new IOException("Unverified firmware permission transition");
        generation++; attempt = null; state = State.UNVERIFIED; outcome = Outcome.UNVERIFIED; dispatched = false; uiProof = false;
    }
    synchronized void cancel() {
        if (state == State.CANCELLED) return;
        verifiedGrantAtStop = state == State.GRANTED;
        generation++; dispatched = false;
        if (state != State.DENIED && state != State.INVALIDATED) {
            if (state != State.GRANTED) outcome = Outcome.UNVERIFIED;
            state = State.CANCELLED;
        }
    }
    synchronized Attempt attempt() { return attempt; }
    synchronized State state() { return state; }
    synchronized Outcome outcome() { return outcome; }
    synchronized long generation() { return generation; }
    synchronized boolean pending() { return state == State.PENDING; }
}
