// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Registration;
import java.io.IOException;
import java.util.List;

/** Permission-only preparation. The port deliberately has no open, claim, session or transfer API. */
final class Tbs5530PermissionPreflight {
    static final long MAX_MILLIS = 60000;
    enum State { WAITING, USER_ACTION_REQUIRED, GRANTED, DENIED, UNVERIFIED, CANCELLED, TIMED_OUT }
    interface Clock { long now(); }
    interface Port {
        List<Device> devices() throws IOException;
        boolean hasPermission(Device device) throws IOException;
        Registration request(Device device, String token, Tbs5530UsbBinding.PermissionCallback callback) throws IOException;
        Registration listen(Tbs5530UsbBinding.Events events) throws IOException;
    }
    interface Evidence { void write(Tbs5530PermissionPreflight value, long now) throws IOException; }
    final String token;
    final long created, deadline;
    final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private final Clock clock;
    private final Port port;
    private final Evidence evidence;
    private Registration listener, request;
    private long last;
    private boolean started, terminal;
    private State state = State.WAITING;
    private String reason = "WAIT_DEVICE";

    Tbs5530PermissionPreflight(String token, Clock clock, Port port, Evidence evidence) throws IOException {
        if (token == null || !token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw new IOException("Fresh host preflight token required");
        this.token = token; this.clock = clock; this.port = port; this.evidence = evidence;
        created = last = clock.now();
        if (created < 0 || created > Long.MAX_VALUE - MAX_MILLIS) throw new IOException("Invalid preflight clock");
        deadline = created + MAX_MILLIS;
    }
    synchronized void start() throws IOException {
        if (started || terminal) throw new IOException("Preflight cannot restart");
        started = true;
        listener = port.listen(new Tbs5530UsbBinding.Events() {
            public void attached(Device device) {
                synchronized (Tbs5530PermissionPreflight.this) {
                    if (terminal || !Tbs5530PermissionState.target(device) || permission.attempt() == null) return;
                    Tbs5530PermissionState.Attach event = permission.attach(device);
                    failQuietly(event == Tbs5530PermissionState.Attach.AMBIGUOUS_DUPLICATE
                            ? "ATTACH_DUPLICATE_OR_ABA_UNVERIFIED" : "ATTACH_IDENTITY_MISMATCH");
                }
            }
            public void detached(Device device) {
                synchronized (Tbs5530PermissionPreflight.this) {
                    if (!terminal && permission.attempt() != null && permission.attempt().device.equals(device)) failQuietly("USB_DETACHED");
                }
            }
        });
        evidence.write(this, last);
    }
    synchronized void tick(boolean foreground) throws IOException {
        if (terminal || !started) return;
        if (!withinDeadline()) return;
        Device fresh;
        try { fresh = Tbs5530PermissionState.single(port.devices()); }
        catch (IOException | RuntimeException error) { fail("ENUMERATION_UNVERIFIED"); throw error; }
        Tbs5530PermissionState.Attempt attempt = permission.attempt();
        if (attempt != null && !attempt.device.equals(fresh)) { fail("ATTACHMENT_CHANGED"); return; }
        if (fresh == null) { evidence.write(this, last); return; }
        if (attempt == null) {
            if (!foreground) return;
            attempt = permission.begin(fresh, port.hasPermission(fresh), last);
            if (permission.pending()) {
                state = State.USER_ACTION_REQUIRED; reason = "WAIT_ANDROID_PERMISSION";
                evidence.write(this, last); permission.dispatched(attempt);
                long generation = attempt.generation;
                Registration registration = port.request(fresh, attempt.token,
                        (correlation, device, granted) -> callback(correlation, generation, device, granted));
                if (terminal || !permission.pending()) registration.close(); else request = registration;
                return;
            }
        }
        if (permission.pending() || !foreground) { evidence.write(this, last); return; }
        // The callback cannot grant access: enumerate and ask Android again before publishing success.
        fresh = Tbs5530PermissionState.single(port.devices());
        try { permission.confirmFresh(fresh, fresh != null && port.hasPermission(fresh)); }
        catch (IOException | RuntimeException error) { fail("FRESH_PERMISSION_UNVERIFIED"); throw error; }
        finish(State.GRANTED, "OS_PERMISSION_VERIFIED");
    }
    private synchronized void callback(String token, long generation, Device device, Boolean granted) {
        if (terminal) return;
        try {
            if (!withinDeadline()) return;
            Tbs5530PermissionState.Callback result = permission.callback(token, generation, device, granted);
            if (result == Tbs5530PermissionState.Callback.IGNORED) return;
            closeRequest();
            if (result == Tbs5530PermissionState.Callback.DENIED) finish(State.DENIED, "PERMISSION_DENIED");
            else if (result == Tbs5530PermissionState.Callback.INVALIDATED) fail("PERMISSION_CALLBACK_MISMATCH");
            else evidence.write(this, last);
        } catch (IOException | RuntimeException error) { failQuietly("PERMISSION_CALLBACK_UNVERIFIED"); }
    }
    private boolean withinDeadline() throws IOException {
        long now = clock.now();
        if (now < last) { fail("CLOCK_ROLLBACK"); return false; }
        last = now;
        if (now >= deadline) { permission.cancel(); finish(State.TIMED_OUT, "PREFLIGHT_DEADLINE"); return false; }
        return true;
    }
    synchronized boolean mayPause() { return !terminal && permission.mayPause(false); }
    synchronized void cancel() throws IOException {
        if (!terminal) { permission.cancel(); finish(State.CANCELLED, "PREFLIGHT_CANCELLED"); }
    }
    synchronized void fail(String reason) throws IOException {
        if (!terminal) { permission.invalidate(); finish(State.UNVERIFIED, reason); }
    }
    private void failQuietly(String reason) {
        try { fail(reason); } catch (IOException ignored) { /* No success is possible if private evidence cannot be written. */ }
    }
    private void closeRequest() throws IOException {
        Registration previous = request; request = null;
        if (previous != null) previous.close();
    }
    private void finish(State next, String reason) throws IOException {
        terminal = true; state = next; this.reason = reason;
        IOException failure = null;
        try { closeRequest(); } catch (IOException error) { failure = error; }
        Registration previous = listener; listener = null;
        try { if (previous != null) previous.close(); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) { state = State.UNVERIFIED; this.reason = "PERMISSION_CLEANUP_UNVERIFIED"; permission.invalidate(); }
        evidence.write(this, last);
        if (failure != null) throw failure;
    }
    synchronized State state() { return state; }
    synchronized String reason() { return reason; }
    synchronized boolean terminal() { return terminal; }
}
