// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import info.martinmarinov.drivers.usb.tbs5530.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Debug-only bounded session. Launch intents publish a challenge, never consent. */
public final class Tbs5530AutonomousDiagnosticActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object reservation = new Object();
    private final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private final Tbs5530AttachPermissionWindow attachPermissionWindow = new Tbs5530AttachPermissionWindow();
    private boolean attachPromptPaused;
    private Tbs5530AutonomousSession session;
    private Tbs5530AndroidRuntime runtime;
    private Tbs5530AndroidUsbFacade facade;
    private Tbs5530AndroidUsbFacade.Manager manager;
    private Tbs5530OperationalHandoff handoff;
    private Tbs5530AndroidBootstrap bootstrap;
    private Tbs5530UsbBinding.Registration events, permissionRequest;
    private Tbs5530UsbBinding.Device selected;
    private volatile Tbs5530OperationalApproval approval;
    private volatile boolean foreground, stopped, busy, waitingGrant = true, reserved;
    private boolean uploaded;
    private TextView status;
    private volatile String result = "WAIT_HOST_SESSION_GRANT";
    private long bytes, packets;
    private int errno = -1;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (status != null) status.setText((session == null ? "NOT_STARTED" : session.policy.state().name()) + "\n" + result);
            if (stopped) return;
            try {
                if (waitingGrant) {
                    if (foreground && session.grantAvailable()) { session.acceptGrant(); waitingGrant = false; startDiscovery(); }
                } else {
                    session.require();
                    if (attachPromptPaused && !foreground && !attachPermissionAllowed()) {
                        stop("ATTACH_PERMISSION_UI_TIMEOUT"); return;
                    }
                    refreshPermissionReceipt();
                    if (permission.pending()) {
                        if (selected == null || !selected.equals(single())) {
                            permission.invalidate(); throw new IOException("Permission attachment changed while waiting");
                        }
                        publishPermission();
                    }
                    if (!busy && foreground && !permission.pending()) discover();
                }
            } catch (IOException | RuntimeException error) { fail(error); }
            if (!stopped) ui.postDelayed(this, 200);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 24, 24, 24);
        status = new TextView(this); status.setText("Phiên debug tối đa 5 phút; chờ host cấp một quyền phiên có giới hạn."); layout.addView(status);
        Button cancel = new Button(this); cancel.setText("CANCEL — dừng phiên"); cancel.setOnClickListener(v -> stop("CANCELLED")); layout.addView(cancel);
        setContentView(layout);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(getIntent().getAction())) {
            // A cold attach launch can deliver OS permission, but must never mint an autonomous session.
            stopped = true; result = "ATTACH_ONLY_NO_SESSION"; finish(); return;
        }
        try { session = new Tbs5530AutonomousSession(this, getIntent().getStringExtra("read_api")); }
        catch (IOException | RuntimeException error) {
            if (session != null) session.failure(error);
            stopped = true; result = "DRY_RUN_OR_IDENTITY_BLOCKED"; status.setText(result);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (attachPromptPaused && !stopped && !attachPermissionAllowed()) stop("ATTACH_PERMISSION_UI_TIMEOUT");
        foreground = true; attachPromptPaused = false;
        if (session != null) session.policy.foreground(true);
        ui.removeCallbacks(tick); if (!stopped) ui.post(tick);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Never replace the launch intent or read_api: attachment delivery is not a new session.
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()) && !waitingGrant && !stopped) {
            try {
                UsbDevice actual = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Tbs5530UsbBinding.Device fresh = single();
                if (actual == null || !target(fresh) || actual.getVendorId() != fresh.vendorId
                        || actual.getProductId() != fresh.productId || actual.getDeviceId() != fresh.id
                        || !actual.getDeviceName().equals(fresh.path)) {
                    permission.invalidate(); publishPermission(); fail("ATTACH_INTENT_IDENTITY_UNVERIFIED");
                } else if (!attachPermissionAllowed() && bootstrap == null && permission.attempt() != null) {
                    permission.invalidate(); publishPermission(); fail("ATTACH_INTENT_GENERATION_UNVERIFIED");
                }
            } catch (IOException | RuntimeException error) { fail(error); }
        }
    }
    private boolean attachPermissionAllowed() {
        if (session == null || stopped || waitingGrant || handoff != null || approval != null) return false;
        Tbs5530OneShotBootstrap.Snapshot snapshot = bootstrap == null ? session.bootstrapSnapshot : bootstrap.snapshot();
        boolean drained = snapshot != null && !snapshot.pending && (bootstrap == null ? !busy : !bootstrap.cleanupPending());
        boolean known = drained && Tbs5530AutonomousPolicy.bootstrapTransitionKnown(snapshot.state,
                snapshot.completed, snapshot.failure, snapshot.cleanupFailure, snapshot.boundaries);
        Tbs5530AutonomousPolicy.State state = session.policy.state();
        boolean waiting = state == Tbs5530AutonomousPolicy.State.FIRMWARE_BOOTSTRAP
                || state == Tbs5530AutonomousPolicy.State.WAIT_REENUMERATION;
        long now = SystemClock.elapsedRealtime();
        attachPermissionWindow.observe(now, known && waiting, drained, handoff != null || approval != null);
        return attachPermissionWindow.allows(now, known && waiting, handoff != null || approval != null);
    }
    @Override protected void onPause() {
        foreground = false;
        // System attach confirmation can precede requestPermission, but only after proven bootstrap drain.
        attachPromptPaused = attachPermissionAllowed();
        if (!stopped && !attachPromptPaused && !permission.mayPause(busy || bootstrap != null || handoff != null || approval != null)) {
            if (session != null) session.policy.foreground(false);
            stop("FOREGROUND_LOST");
        }
        super.onPause();
    }
    @Override protected void onStop() {
        // A backgrounded activity is not a visible system permission dialog; OEM uncertainty stops safely.
        if (!stopped && !foreground) stop("PERMISSION_UI_OR_FOREGROUND_HIDDEN");
        super.onStop();
    }
    @Override protected void onDestroy() { if (!stopped) stop("DESTROYED"); super.onDestroy(); }
    private void startDiscovery() throws IOException {
        if (!Tbs5530DiagnosticController.reserveOperational(reservation)) throw new IOException("Another debug owner exists");
        reserved = true; runtime = new Tbs5530AndroidRuntime();
        manager = Tbs5530AutonomousAndroidAccess.manager(getApplicationContext());
        facade = new Tbs5530AndroidUsbFacade(getApplicationContext(), manager);
        events = facade.listen(new Tbs5530UsbBinding.Events() {
            public void attached(Tbs5530UsbBinding.Device device) {
                if (stopped || !target(device) || selected == null) return;
                Tbs5530OneShotBootstrap.Snapshot current = bootstrap == null ? null : bootstrap.snapshot();
                boolean knownTransition = current != null && !bootstrap.cleanupPending()
                        && Tbs5530AutonomousPolicy.bootstrapTransitionKnown(current.state,
                        current.completed, current.failure, current.cleanupFailure, current.boundaries);
                if (current == null) {
                    Tbs5530PermissionState.Attach event = permission.attach(device);
                    fail(event == Tbs5530PermissionState.Attach.AMBIGUOUS_DUPLICATE
                            ? "ATTACH_DUPLICATE_OR_ABA_UNVERIFIED" : "ATTACH_IDENTITY_MISMATCH");
                } else if (!current.pending && !knownTransition) fail("UNEXPECTED_ATTACH_EVENT");
            }
            public void detached(Tbs5530UsbBinding.Device device) {
                if (!stopped && selected != null && selected.equals(device) && bootstrap == null) {
                    permission.invalidate(); fail("USB_DETACHED");
                }
            }
        });
        discover();
    }
    private static boolean target(Tbs5530UsbBinding.Device d) { return Tbs5530PermissionState.target(d); }
    private Tbs5530UsbBinding.Device single() throws IOException {
        return Tbs5530PermissionState.single(manager.devices());
    }
    private void publishPermission() throws IOException {
        synchronized (permission) {
            session.write("permission.json", Tbs5530PermissionEvidence.challenge(session.id, permission, SystemClock.elapsedRealtime()).toString());
        }
    }
    private void refreshPermissionReceipt() throws IOException {
        File receipt = new File(session.directory, "permission-ui.json");
        if (receipt.isFile() && Tbs5530PermissionEvidence.receipt(session.id, permission,
                Tbs5530AutonomousSession.read(receipt, 4096), SystemClock.elapsedRealtime())) publishPermission();
    }
    private void closePermissionRequest() throws IOException {
        Tbs5530UsbBinding.Registration previous = permissionRequest; permissionRequest = null;
        if (previous != null) previous.close();
    }
    private void requestPermission(Tbs5530UsbBinding.Device device) throws IOException {
        closePermissionRequest();
        session.policy.permissionWaiting();
        if (uploaded) attachPermissionWindow.close();
        Tbs5530PermissionState.Attempt attempt = permission.attempt();
        publishPermission(); result = "USER_ACTION_REQUIRED";
        permission.dispatched(attempt);
        permissionRequest = facade.requestPermission(device, attempt.token, (correlation, actual, allowed) -> ui.post(() -> {
            if (stopped) return;
            Tbs5530PermissionState.Callback decision = permission.callback(correlation, attempt.generation, actual, allowed);
            if (decision == Tbs5530PermissionState.Callback.IGNORED) return;
            try {
                closePermissionRequest(); session.require(); refreshPermissionReceipt(); publishPermission();
                if (decision == Tbs5530PermissionState.Callback.DENIED) { fail("PERMISSION_DENIED"); return; }
                if (decision == Tbs5530PermissionState.Callback.INVALIDATED) { fail("PERMISSION_CALLBACK_MISMATCH"); return; }
                result = permission.outcome().name();
                // Only onResume/tick may enumerate again and validate actual OS access before opening.
            } catch (IOException | RuntimeException error) { fail(error); }
        }));
    }
    private void discover() throws IOException {
        session.require(); if (!foreground || busy || permission.pending()) return;
        Tbs5530UsbBinding.Device device = single();
        if (device == null) {
            if (selected != null) { permission.invalidate(); throw new IOException("Attachment disappeared"); }
            result = "WAIT_DEVICE"; return;
        }
        if (selected != null && !selected.equals(device)) { permission.invalidate(); throw new IOException("Unexpected generation change"); }
        selected = device;
        boolean osPermission = manager.hasPermission(device);
        if (permission.attempt() == null) {
            permission.begin(device, osPermission, SystemClock.elapsedRealtime()); publishPermission();
            if (!osPermission) { requestPermission(device); return; }
        }
        permission.confirmFresh(device, osPermission);
        if (uploaded) attachPermissionWindow.close();
        publishPermission(); result = permission.outcome().name();
        busy = true; runtime.execute(() -> {
            try {
                requirePhysical();
                Tbs5530AndroidUsbFacade.RawConnection raw = manager.open(device);
                if (raw == null) throw new IOException("Cannot open fresh descriptor observation");
                Tbs5530OperationalProfile observation;
                try {
                    requirePhysical();
                    byte[] descriptors = raw.rawDescriptors();
                    if (descriptors == null || descriptors.length == 0 || descriptors.length > 65536)
                        throw new IOException("Missing or oversized raw descriptor evidence");
                    if (!session.initialRawObserved) {
                        Tbs5530DurableFile.write(new File(session.directory, "initial-descriptors.bin"), descriptors);
                        session.initialNegotiatedUsbSpeed = raw.negotiatedUsbSpeed(); session.initialRawObserved = true;
                    }
                    observation = Tbs5530OperationalProfile.observe(descriptors); requirePhysical();
                }
                finally { raw.close(); }
                session.policy.observe(Tbs5530AutonomousPlans.hash(device.path + ":" + device.id), permission.generation(),
                        manager.hasPermission(device), observation.kind);
                if (observation.kind == Tbs5530OperationalProfile.Kind.FX2_DEFAULT) bootstrap(device);
                else { observation.requireOperational(); operational(device); }
            } catch (IOException | RuntimeException error) { fail(error); }
        });
    }
    private void requirePhysical() throws IOException {
        session.require();
        permission.requireReady(selected);
        if (!foreground || stopped || selected == null || !selected.equals(single()) || !manager.hasPermission(selected))
            throw new IOException("Foreground, OS permission or attachment changed");
    }
    private void bootstrap(Tbs5530UsbBinding.Device device) throws IOException {
        if (uploaded) throw new IOException("No firmware retry on this session");
        File file = new File(session.directory, "firmware.bin");
        Tbs5530FirmwarePlan plan;
        try (FileInputStream input = new FileInputStream(file)) {
            plan = Tbs5530OneShotBootstrap.readFirmware(input, Tbs5530FirmwareValidation.SOURCE,
                    Tbs5530FirmwareValidation.ENTRY, Tbs5530FirmwareValidation.Format.RAW_LINEAR);
        }
        requirePhysical();
        bootstrap = new Tbs5530AndroidBootstrap(getApplicationContext(), runtime, device, plan, session.firmwareGuard(this::requirePhysical), () -> {
            requirePhysical();
            session.consumeFirmwareAttachment(Tbs5530AutonomousPlans.hash(device.path + ":" + device.id));
            session.policy.beginUpload(session.id); uploaded = true;
        }, true);
        result = "FIRMWARE_BOOTSTRAP"; bootstrap.startFromUserConfirmation();
        ui.postDelayed(this::awaitBootstrap, 50);
    }
    private void awaitBootstrap() {
        if (stopped) return;
        Tbs5530OneShotBootstrap.Snapshot snapshot = bootstrap.snapshot();
        session.bootstrapSnapshot = snapshot;
        if (snapshot.pending || bootstrap.cleanupPending()) { ui.postDelayed(this::awaitBootstrap, 50); return; }
        attachPermissionAllowed();
        runtime.execute(() -> {
            try {
                if (session.preNativeRefusal && ("ATTACHMENT_ALREADY_CONSUMED".equals(session.stopReason)
                        || "UPLOAD_PREFLIGHT_REFUSED".equals(session.stopReason))
                        && snapshot.state == Tbs5530OneShotBootstrap.State.FAULTED && snapshot.attempted == 0
                        && snapshot.boundaries.nativeCallStarted == 0 && snapshot.boundaries.nativeCallCompleted == 0
                        && "NONE".equals(snapshot.cleanupFailure)) {
                    session.policy.transition(Tbs5530AutonomousPolicy.State.REPORT);
                    stop(session.stopReason); return;
                }
                if (!Tbs5530AutonomousPolicy.bootstrapTransitionKnown(snapshot.state, snapshot.completed,
                        snapshot.failure, snapshot.cleanupFailure, snapshot.boundaries))
                    throw new IOException("Firmware uncertain; no retry");
                session.policy.uploadComplete(snapshot.completed, bootstrap.cleanupPending());
                bootstrap.close();
                ui.post(() -> {
                    if (stopped) return;
                    try {
                        closePermissionRequest(); permission.firmwareTransition(); publishPermission();
                        bootstrap = null; selected = null; busy = false;
                        result = "WAIT_REENUMERATION";
                    } catch (IOException | RuntimeException error) { fail(error); }
                });
            } catch (IOException | RuntimeException error) { fail(error); }
        });
    }
    private void operational(Tbs5530UsbBinding.Device device) throws IOException {
        requirePhysical();
        handoff = new Tbs5530OperationalHandoff(getApplicationContext(), runtime, this::requirePhysical, session.readApi, session.id);
        handoff.start(device); result = "ACQUIRING_FRESH_OPERATIONAL_OWNER"; ui.postDelayed(this::awaitOwner, 25);
    }
    private void awaitOwner() {
        if (stopped) return;
        if (handoff.pending()) { ui.postDelayed(this::awaitOwner, 25); return; }
        session.operationalObservation = handoff.observation();
        if (!handoff.operationalOwner()) {
            if (handoff.failure() != null) fail(handoff.failure()); else fail("OPERATIONAL_OWNER_UNAVAILABLE");
            return;
        }
        runtime.execute(() -> {
            try {
                requirePhysical(); session.owner(handoff.approvalIdentity(), handoff.generation(), this::requirePhysical);
                Tbs5530ControlProbe probe = new Tbs5530ControlProbe(handoff, new Tbs5530ControlProbe.Authorization() {
                    public void requireCurrent() throws IOException { requirePhysical(); current().requireCurrent(); }
                    public void consume(Tbs5530Frontend.Plan p) throws IOException { current().consume(p); }
                    public void audit(String b, int o, int t, int r, int l, int n) throws IOException { current().audit(b, o, t, r, l, n); }
                    public boolean rfInConfirmed() { return approval != null && approval.rfInConfirmed(); }
                    public boolean experimentalAutoPlpApproved() { return approval != null && approval.experimentalAutoPlpApproved(); }
                }, new Tbs5530Frontend.Clock() {
                    public long elapsedRealtimeMillis() { return SystemClock.elapsedRealtime(); }
                    public void delayMillis(long millis) throws InterruptedException { Thread.sleep(millis); }
                });
                execute(probe, probe.planProbe());
                session.policy.transition(Tbs5530AutonomousPolicy.State.FRONTEND_INIT);
                execute(probe, probe.planInitialize()); probe.readyForTune();
                execute(probe, probe.planExperimentalAutoTune(new Tbs5530DvbT2TuneParameters(594000000L, 8000000,
                        Tbs5530DvbT2TuneParameters.Selection.automatic())));
                Tbs5530Frontend.Result lock = execute(probe, probe.planPollStatus(3));
                if (lock.status == null || !lock.status.locked) throw new IOException("No confirmed lock");
                session.policy.transition(Tbs5530AutonomousPolicy.State.LOCK_CONFIRMED);
                Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false, session.readApi);
                approval = session.approval(null, limits); session.policy.transition(Tbs5530AutonomousPolicy.State.TS_CAPTURE);
                Tbs5530TsSmoke.Result ts;
                try (Tbs5530AutonomousCapture raw = new Tbs5530AutonomousCapture(session.directory, limits.maxBytes)) {
                    session.capture = raw;
                    ts = handoff.smoke(current(), SystemClock::elapsedRealtimeNanos, raw).run(limits);
                    preserveNativeReceiveSnapshot();
                    session.tsResult = ts;
                    bytes = ts.bytes; packets = ts.packets.packets; errno = ts.lastReadErrno;
                } finally {
                    preserveNativeReceiveSnapshot();
                    if (session.capture != null) bytes = session.capture.bytes();
                }
                if (ts.status == Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE) {
                    boolean completedError = session.readApi == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096
                            ? completedNativeReceiveError()
                            : Tbs5530AutonomousPolicy.completedReceiveError(session.readApi == Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO,
                                    ts.reads, ts.lastReadResult, ts.lastReadErrno);
                    if (completedError) {
                        session.failureStage = Tbs5530AutonomousPolicy.State.TS_CAPTURE.name();
                        session.stopReason = "USB_RECEIVE_ERROR_COMPLETED";
                        session.policy.transition(Tbs5530AutonomousPolicy.State.REPORT);
                        stop("USB_RECEIVE_FAILURE");
                    } else fail("USB_RECEIVE_FAILURE_UNCERTAIN");
                    return;
                }
                session.policy.transition(Tbs5530AutonomousPolicy.State.REPORT); stop(ts.status.name());
            } catch (IOException | RuntimeException error) {
                preserveNativeReceiveSnapshot();
                // A negative completion does not explain a thrown guard, audit or cleanup failure.
                fail(error);
            }
        });
    }
    private void preserveNativeReceiveSnapshot() {
        if (session != null && handoff != null) session.nativeReceiveSnapshot = handoff.nativeReceiveSnapshot();
    }
    private boolean completedNativeReceiveError() throws IOException {
        Tbs5530OperationalHandoff.NativeReceiveSnapshot snapshot = session.nativeReceiveSnapshot;
        if (snapshot == null || !snapshot.completionKnown() || snapshot.urbStatus >= 0
                || snapshot.quarantined || snapshot.outstanding != 0 || handoff.cleanupFailure() != null) return false;
        requirePhysical(); current().requireCurrent();
        return true;
    }
    private Tbs5530Frontend.Result execute(Tbs5530ControlProbe probe, Tbs5530Frontend.Plan plan) throws IOException {
        approval = session.approval(plan, null); return probe.execute(plan);
    }
    private Tbs5530OperationalApproval current() throws IOException {
        if (approval == null) throw new IOException("No current per-program approval"); return approval;
    }
    private void fail(String reason) {
        if (session != null && "NONE".equals(session.stopReason)) {
            session.failureStage = session.policy.state().name(); session.stopReason = reason;
        }
        if (session != null) session.policy.failUncertain(); stop("ERROR_UNCERTAIN_NO_RETRY");
    }
    private void fail(Throwable error) {
        if (session != null) session.failure(error);
        fail("GUARD_OR_IO_FAILURE");
    }
    private synchronized void stop(String reason) {
        if (stopped) return;
        stopped = true; result = reason; ui.removeCallbacks(tick);
        attachPermissionWindow.close(); permission.cancel();
        try { closePermissionRequest(); if (session != null) publishPermission(); }
        catch (IOException error) { if (session != null) session.failure(error); result = "PERMISSION_AUDIT_OR_CLEANUP_UNCERTAIN"; }
        if (session != null) {
            if ("NONE".equals(session.stopReason) && session.policy.state() != Tbs5530AutonomousPolicy.State.REPORT) {
                session.failureStage = session.policy.state().name(); session.stopReason = reason;
            }
            if (session.policy.state() != Tbs5530AutonomousPolicy.State.REPORT && (bootstrap != null || handoff != null)) session.policy.failUncertain();
            session.policy.stop();
        }
        if (approval != null) approval.foreground(false);
        if (bootstrap != null) bootstrap.close();
        if (handoff != null) handoff.revoke();
        if (runtime == null) {
            if (session != null) try { session.report(result, bytes, packets, errno); } catch (IOException error) { result = "AUDIT_UNAVAILABLE"; }
            return;
        }
        runtime.execute(() -> {
            try {
                if (bootstrap != null) session.bootstrapSnapshot = bootstrap.snapshot();
                if (handoff != null) {
                    try { handoff.close(); }
                    finally { preserveNativeReceiveSnapshot(); }
                    if (handoff.cleanupFailure() != null) throw handoff.cleanupFailure();
                }
                closePermissionRequest(); if (events != null) events.close();
                if (session != null) { refreshPermissionReceipt(); session.report(result, bytes, packets, errno); }
            } catch (IOException | RuntimeException error) {
                if (session != null) { session.failure(error); session.policy.failUncertain(); try { session.report("CLEANUP_OR_AUDIT_UNCERTAIN", bytes, packets, errno); } catch (IOException ignored) { } }
            } finally {
                runtime.close();
                // Runtime shutdown drains native calls/cleanup. Never force-close or free a pending lease.
                new Thread(() -> {
                    try {
                        boolean terminated = runtime.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
                        if (terminated) preserveNativeReceiveSnapshot();
                        boolean drained = terminated
                                && (handoff == null || !handoff.pending() && handoff.cleanupFailure() == null)
                                && (bootstrap == null || !bootstrap.cleanupPending() && "NONE".equals(bootstrap.snapshot().cleanupFailure))
                                && (session == null || session.nativeReceiveSnapshot == null
                                || !session.nativeReceiveSnapshot.quarantined && session.nativeReceiveSnapshot.outstanding == 0);
                        if (session != null) {
                            if (bootstrap != null) session.bootstrapSnapshot = bootstrap.snapshot();
                            session.cleanupConfirmed = drained;
                            if (!drained) session.policy.failUncertain();
                            refreshPermissionReceipt();
                            session.report(drained ? result : "CLEANUP_UNCERTAIN_NO_FORCE_CLOSE", bytes, packets, errno);
                        }
                        if (drained && reserved) { Tbs5530DiagnosticController.releaseOperational(reservation); reserved = false; }
                    } catch (IOException error) { result = "AUDIT_UNAVAILABLE"; }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }, "tbs5530-autonomous-drain").start();
            }
        });
    }
}
