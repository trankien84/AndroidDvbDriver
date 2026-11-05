// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidRuntime;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530LifecycleCoordinator;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530Session;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptorInspection;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptors;

/** Retained debug owner. Only startSelected() requests permission; no connection escapes. */
final class Tbs5530DiagnosticController implements AutoCloseable {
    interface FacadeFactory { Tbs5530UsbBinding.Facade create(); }
    interface Metadata { Tbs5530DiagnosticMetadata.Snapshot read(); }

    private static Object diagnosticReservation;
    private final Object reservation = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FacadeFactory factory;
    private final Metadata metadata;
    private final Tbs5530RawDescriptorInspection.Port rawPort;
    private Tbs5530RawDescriptorInspection rawInspection;
    private boolean rawOperation;
    private info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwarePlan firmware;
    private info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidBootstrap bootstrap;
    private Tbs5530BootstrapApproval approval;
    private Tbs5530UsbBinding.Device firmwareDevice;
    private boolean preparing, bootstrapOperation, foreground;
    private Tbs5530UsbBinding.Registration preparationObserver;
    private java.util.concurrent.atomic.AtomicBoolean preparationInvalidated = new java.util.concurrent.atomic.AtomicBoolean();
    private String firmwareStatus = "NOT_SELECTED";
    private final long permissionTimeout, acquisitionTimeout;
    private final List<String> steps = new ArrayList<>();
    private final int[] attempts = new int[4], completed = new int[4];
    private final Runnable poll = this::poll;
    private Tbs5530DiagnosticMetadata.Snapshot snapshot;
    private Tbs5530AndroidRuntime runtime;
    private Tbs5530UsbBinding binding;
    private Tbs5530Session session;
    private Runnable observer;
    private boolean active, bindingClosed, destroyed, connectedObserved;
    private int selected, clicks, failures;
    private String permission = "CHƯA YÊU CẦU";
    private boolean permissionOnly;
    private int callbacks;
    private boolean correlationMatched;
    private boolean permissionRequested;
    private String stateBeforeCleanup = "NOT_RUN";

    Tbs5530DiagnosticController(FacadeFactory factory, Metadata metadata) {
        this(factory, metadata, 30000, 10000);
    }

    Tbs5530DiagnosticController(FacadeFactory factory, Metadata metadata,
                               long permissionTimeout, long acquisitionTimeout) {
        this(factory, metadata, null, permissionTimeout, acquisitionTimeout);
    }

    Tbs5530DiagnosticController(FacadeFactory factory, Metadata metadata,
                               Tbs5530RawDescriptorInspection.Port rawPort,
                               long permissionTimeout, long acquisitionTimeout) {
        this.factory = factory;
        this.metadata = metadata;
        this.rawPort = rawPort;
        this.permissionTimeout = permissionTimeout;
        this.acquisitionTimeout = acquisitionTimeout;
        refresh();
    }

    void observe(Runnable observer) { main(); this.observer = observer; }

    void refresh() {
        main();
        if (destroyed || active) return;
        snapshot = metadata.read();
        selected = 0;
        changed();
    }

    List<String> labels() { return new ArrayList<>(snapshot.labels); }
    int selectedIndex() { return selected; }
    void select(int index) { main(); if (!active && index >= 0 && index < snapshot.devices.size()) selected = index; }
    private boolean resourcesIdle() {
        if (preparing || bootstrap != null && bootstrap.cleanupPending()) return false;
        if (active || binding != null && binding.isPending() || session != null && session.isCleanupPending()) return false;
        if (rawInspection != null) {
            Tbs5530RawDescriptorInspection.Snapshot raw = rawInspection.snapshot();
            if (raw.pending || raw.openCompleted != raw.closeCompleted) return false;
        }
        return true;
    }
    private static synchronized boolean reservationAvailable() { return diagnosticReservation == null; }
    private boolean reserve() {
        synchronized (Tbs5530DiagnosticController.class) {
            if (diagnosticReservation != null) return false;
            diagnosticReservation = reservation;
            return true;
        }
    }
    private void releaseReservation() {
        synchronized (Tbs5530DiagnosticController.class) {
            if (diagnosticReservation == reservation) diagnosticReservation = null;
        }
    }
    boolean canRequestPermission() { return !destroyed && !bootstrapOperation && resourcesIdle() && reservationAvailable() && !snapshot.devices.isEmpty(); }
    boolean canInspectRaw() { return rawPort != null && canRequestPermission(); }
    boolean canStart() { return firmware == null && canRequestPermission() && snapshot.acquisitionAllowed.get(selected); }
    boolean isActive() { return active; }
    boolean isRuntimeTerminated() { return runtime == null || runtime.isTerminated(); }

    void foreground(boolean value) {
        main(); foreground = value;
        if (approval != null) approval.foreground(value);
        if (!value && bootstrap != null && bootstrap.snapshot().pending) bootstrap.revoke(
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop.Kind.FOREGROUND_LOST);
    }

    boolean canPrepareFirmware() { return canRequestPermission() && snapshot.devices.size() == 1 && bootstrap == null; }
    boolean canBootstrap() {
        if (!foreground || preparationInvalidated.get() || firmware == null || approval == null || approval.consumed() || !canPrepareFirmware()
                || !firmwareDevice.equals(snapshot.devices.get(selected))) return false;
        try { approval.requireCurrent(); return true; } catch (IOException error) { return false; }
    }
    void prepareFirmware(android.content.Context context, android.net.Uri uri, String source, String entry,
                         boolean sourceAndPowerConfirmed) {
        main();
        if (!sourceAndPowerConfirmed || !canPrepareFirmware() || !reserve()) return;
        applicationForBootstrap = context.getApplicationContext();
        firmware = null; approval = null; preparing = true; active = true;
        firmwareStatus = "VALIDATING_LOCAL_SELECTION";
        firmwareDevice = snapshot.devices.get(selected);
        java.util.concurrent.atomic.AtomicBoolean invalidated = new java.util.concurrent.atomic.AtomicBoolean();
        preparationInvalidated = invalidated;
        try {
            if (preparationObserver != null) preparationObserver.close();
            preparationObserver = factory.create().listen(new Tbs5530UsbBinding.Events() {
                private void changed(Tbs5530UsbBinding.Device device) {
                    if (device.vendorId == 0x734c && device.productId == 0x5530) invalidated.set(true);
                }
                public void attached(Tbs5530UsbBinding.Device device) { changed(device); }
                public void detached(Tbs5530UsbBinding.Device device) { changed(device); }
            });
        } catch (IOException | RuntimeException error) {
            preparing = false; active = false; invalidated.set(true); releaseReservation(); changed(); return;
        }
        if (runtime == null) runtime = new Tbs5530AndroidRuntime();
        runtime.execute(() -> {
            try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwarePlan validated =
                        info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.readFirmware(input, source, entry,
                                info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareValidation.Format.RAW_LINEAR);
                Tbs5530BootstrapApproval prepared = new Tbs5530BootstrapApproval(context, firmwareDevice);
                prepared.publishChallenge();
                handler.post(() -> {
                    if (!destroyed) {
                        firmware = validated; approval = prepared; approval.foreground(foreground);
                        firmwareStatus = "HASH_SIZE_VALIDATED_SOURCE_USER_ATTESTED_SESSION_APPROVAL_REQUIRED";
                    }
                    preparing = false; active = false; releaseReservation(); changed();
                });
            } catch (IOException | RuntimeException error) {
                handler.post(() -> { preparing = false; active = false; firmwareStatus = "VALIDATION_FAILED";
                    releaseReservation(); changed(); });
            }
        });
        changed();
    }
    void bootstrapSelected() {
        main();
        if (!canBootstrap() || !reserve()) return;
        bootstrapOperation = true; rawOperation = false; active = true; clicks++;
        try {
            bootstrap = new info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidBootstrap(
                    applicationForBootstrap, runtime, firmwareDevice, firmware,
                    new info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.Guard() {
                        public void requireCurrent() throws IOException { approval.requireCurrent(); }
                        public void requireWrite(int ordinal) throws IOException { approval.requireWrite(ordinal); }
                        public void requireNative(int ordinal) throws IOException { approval.requireNative(ordinal); }
                        public String runEpoch() { return approval.runEpoch(); }
                    }, approval::consume);
            if (preparationInvalidated.get()) throw new IOException("Prepared attachment changed");
            if (preparationObserver != null) { preparationObserver.close(); preparationObserver = null; }
            bootstrap.startFromUserConfirmation();
        } catch (IOException | RuntimeException error) {
            error(error); active = false;
            if (bootstrap != null) bootstrap.close();
            releaseReservation();
        }
        handler.post(poll); changed();
    }
    private android.content.Context applicationForBootstrap;

    void startSelected() { start(false); }
    void permissionSelected() { start(true); }

    void rawSelected() {
        main();
        if (!canInspectRaw()) return;
        Tbs5530UsbBinding.Device device = snapshot.devices.get(selected);
        if (device.vendorId != 0x734c || device.productId != 0x5530 || !reserve()) return;
        clicks++;
        rawOperation = true;
        active = true;
        if (runtime == null) runtime = new Tbs5530AndroidRuntime();
        rawInspection = new Tbs5530RawDescriptorInspection(rawPort, runtime, runtime, device, clicks);
        try { rawInspection.start(); }
        catch (RuntimeException failure) { error(failure); active = false; releaseReservation(); }
        handler.post(poll);
        changed();
    }

    private void start(boolean onlyPermission) {
        main();
        if (onlyPermission ? !canRequestPermission() : !canStart()) return;
        Tbs5530UsbBinding.Device device = snapshot.devices.get(selected);
        if (device.vendorId != 0x734c || device.productId != 0x5530 || !reserve()) return;
        synchronized (this) {
            steps.clear();
            for (int i = 0; i < attempts.length; i++) { attempts[i] = 0; completed[i] = 0; }
            failures = 0;
            permissionOnly = onlyPermission;
            callbacks = 0;
            correlationMatched = false;
            permissionRequested = false;
            stateBeforeCleanup = "NOT_RUN";
            permission = "ĐANG KIỂM TRA";
        }
        rawOperation = false;
        clicks++;
        connectedObserved = false;
        active = true;
        bindingClosed = false;
        binding = null;
        session = new Tbs5530Session();
        if (runtime == null) runtime = new Tbs5530AndroidRuntime();
        try {
            binding = new Tbs5530UsbBinding(session, decorate(factory.create()), runtime, runtime,
                    this::error, permissionTimeout, acquisitionTimeout);
            if (onlyPermission) binding.requestPermissionOnly(device); else binding.start(device);
        } catch (IOException | RuntimeException failure) {
            error(failure);
            closeBinding();
        }
        handler.post(poll);
        changed();
    }

    private void poll() {
        main();
        if (destroyed || !active) return;
        if (bootstrapOperation && bootstrap != null) {
            active = bootstrap.snapshot().pending || bootstrap.cleanupPending();
            if (!active) releaseReservation();
            changed();
            if (active) handler.postDelayed(poll, 50);
            return;
        }
        if (rawOperation) {
            active = rawInspection.snapshot().pending;
            if (resourcesIdle()) releaseReservation();
            changed();
            if (active) handler.postDelayed(poll, 50);
            return;
        }
        if (!bindingClosed && binding != null && !binding.isPending()) {
            // CONNECTED means ownership only. Close immediately, without probing readiness.
            connectedObserved = session.getState() == Tbs5530Session.State.CONNECTED;
            synchronized (this) {
                stateBeforeCleanup = session.getState().name();
                if (session.getState() == Tbs5530Session.State.CLOSED && "ĐANG CHỜ".equals(permission)) permission = "TIMEOUT";
                steps.add("Phiên trước khi đóng: " + session.getState());
            }
            closeBinding();
        }
        if (bindingClosed && (binding == null || !binding.isPending())) active = false;
        if (resourcesIdle()) releaseReservation();
        changed();
        if (active) handler.postDelayed(poll, 50);
    }

    private void closeBinding() {
        if (bindingClosed) return;
        bindingClosed = true;
        if (binding != null) {
            try { binding.close(); }
            catch (IOException | RuntimeException failure) { error(failure); }
        }
    }

    private Tbs5530UsbBinding.Facade decorate(Tbs5530UsbBinding.Facade delegate) {
        return new Tbs5530UsbBinding.Facade() {
            @Override public List<Tbs5530UsbBinding.Device> devices() throws IOException { return delegate.devices(); }
            @Override public boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException {
                boolean granted = delegate.hasPermission(device);
                synchronized (Tbs5530DiagnosticController.this) {
                    permission = granted ? "ĐÃ CẤP" : "CHƯA CẤP";
                }
                return granted;
            }
            @Override public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) throws IOException {
                return delegate.listen(events);
            }
            @Override public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device device,
                    String correlation, Tbs5530UsbBinding.PermissionCallback callback) throws IOException {
                synchronized (Tbs5530DiagnosticController.this) { permission = "ĐANG CHỜ"; permissionRequested = true; }
                return delegate.requestPermission(device, correlation, (token, reported, granted) -> {
                    synchronized (Tbs5530DiagnosticController.this) {
                        callbacks++;
                        correlationMatched = correlation.equals(token) && device.equals(reported);
                        permission = granted == null ? "KHÔNG XÁC ĐỊNH" : granted ? "ĐÃ CẤP" : "TỪ CHỐI";
                    }
                    callback.result(token, reported, granted);
                });
            }
            @Override public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device device) throws IOException {
                long started = begin(0);
                Tbs5530LifecycleCoordinator.Connection connection;
                try {
                    connection = delegate.open(device);
                    end(0, started, connection != null);
                } catch (IOException | RuntimeException failure) {
                    end(0, started, false);
                    throw failure;
                }
                if (connection == null) return null;
                return new Tbs5530LifecycleCoordinator.Connection() {
                    @Override public void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException {
                        long start = begin(1);
                        boolean ok = false;
                        try { connection.claim(receipt); ok = true; }
                        finally { end(1, start, ok); }
                    }
                    @Override public void cancel() throws IOException { connection.cancel(); }
                    @Override public void release() throws IOException {
                        long start = begin(2);
                        boolean ok = false;
                        try { connection.release(); ok = true; }
                        finally { end(2, start, ok); }
                    }
                    @Override public void close() throws IOException {
                        long start = begin(3);
                        boolean ok = false;
                        try { connection.close(); ok = true; }
                        finally { end(3, start, ok); }
                    }
                };
            }
        };
    }

    private static String step(int index) { return new String[]{"open (facade)", "claim(false)", "release", "close"}[index]; }
    private static String thread() {
        String name = Thread.currentThread().getName();
        return "main".equals(name) || "tbs5530-usb-worker".equals(name) || "tbs5530-usb-deadline".equals(name)
                ? name : "worker-khác";
    }
    private synchronized long begin(int index) {
        attempts[index]++;
        steps.add(step(index) + " | " + thread() + " | PENDING");
        return System.nanoTime();
    }
    private synchronized void end(int index, long started, boolean ok) {
        if (ok) completed[index]++; else failures++;
        steps.add(step(index) + " | " + thread() + " | " + (System.nanoTime() - started) / 1000000
                + " ms | " + (ok ? "PASS" : "FAIL"));
    }
    private synchronized void error(Throwable failure) {
        failures++;
        // Never include messages, causes, device identity or caller-controlled thread names.
        String kind = failure instanceof SecurityException ? "SecurityException"
                : failure instanceof IOException ? "IOException"
                : failure instanceof IllegalStateException ? "IllegalStateException" : "RuntimeException";
        steps.add("Lỗi | " + thread() + " | " + kind);
    }

    synchronized String report() {
        StringBuilder out = new StringBuilder("Chẩn đoán USB TBS5530 — chỉ bản debug\n");
        out.append("API Android: ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        out.append(snapshot.report).append('\n');
        out.append("Firmware: ").append(firmwareStatus).append('\n');
        if (bootstrapOperation && bootstrap != null) {
            info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.Snapshot s = bootstrap.snapshot();
            out.append("Bootstrap: ").append(s.state).append("; epoch=").append(s.epoch)
                    .append("; plannedOrdinal=").append(s.boundaries.plannedOrdinal)
                    .append("; guard requested/accepted=").append(s.boundaries.guardRequested).append('/').append(s.boundaries.guardAccepted)
                    .append("; native started/completed=").append(s.boundaries.nativeCallStarted).append('/').append(s.boundaries.nativeCallCompleted)
                    .append("; full length=").append(s.boundaries.fullLengthCompleted)
                    .append("; image bytes=").append(s.boundaries.imageBytesFullLength)
                    .append("; failure=").append(s.failure).append('\n');
            out.append("Trạng thái firmware: ").append(firmwareState(s)).append('\n');
            out.append("Không READY, không frontend hoặc TS; không retry.\n");
            return out.toString();
        }
        if (rawOperation && rawInspection != null) {
            Tbs5530RawDescriptorInspection.Snapshot raw = rawInspection.snapshot();
            out.append("Raw inspection: ").append(raw.status).append("; generation: ").append(raw.generation)
                    .append("; pending: ").append(raw.pending).append("; lỗi: ").append(raw.failure).append('\n');
            out.append("open/read/close: ").append(raw.openCalls).append('/').append(raw.readCalls).append('/').append(raw.closeCalls)
                    .append("; close hoàn tất: ").append(raw.closeCompleted).append('\n');
            if (raw.descriptors != null) out.append("Raw bytes: ").append(raw.descriptors.length)
                    .append("; parser: ").append(raw.descriptors.status).append("; SHA-256: ").append(raw.descriptors.sha256).append('\n');
            if (raw.descriptors != null) {
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.Candidate candidate =
                        info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.assess(raw.descriptors).candidate;
                out.append("Trạng thái từ raw: ").append(candidate
                        == info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE
                        ? "FX2_DEFAULT" : candidate.name()).append('\n');
            }
            out.append("Không claim, không transfer; không suy alternate đang active hoặc streaming readiness.\n");
            return out.toString();
        }
        out.append("Phiên thao tác: ").append(clicks).append("; generation: ")
                .append(session == null ? 0 : session.getGeneration()).append('\n');
        out.append("Trạng thái: ").append(session == null ? "CHƯA BẮT ĐẦU" : session.getState())
                .append("; quyền: ").append(permission).append('\n');
        out.append("Đang chờ: ").append(active ? "có" : "không")
                .append("; dọn tài nguyên: ").append(session != null && session.isCleanupPending() ? "PENDING" : "không").append('\n');
        boolean pass = connectedObserved && !active && failures == 0
                && session.getState() == Tbs5530Session.State.CLOSED && !session.isCleanupPending();
        for (int i = 0; i < attempts.length; i++) {
            pass &= attempts[i] == 1 && completed[i] == 1;
            out.append(step(i)).append(": gọi=").append(attempts[i]).append(", trả về thành công=").append(completed[i]).append('\n');
        }
        out.append("Kết quả chu kỳ: ").append(active ? "PENDING" : pass ? "PASS" : failures > 0 ? "FAIL" : "NOT RUN / không đủ bước").append('\n');
        out.append("Không firmware, không truyền điều khiển, không kiểm DVB/TS.\n");
        out.append("open đo cả kiểm tra descriptor của facade; claim(false) do facade Android thực thi.\n");
        for (String entry : steps) out.append(entry).append('\n');
        return out.toString();
    }

    private org.json.JSONObject rawReport() throws org.json.JSONException {
        Tbs5530RawDescriptorInspection.Snapshot raw = rawInspection.snapshot();
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("timestamp_epoch_ms", System.currentTimeMillis()); json.put("operation_id", clicks);
        json.put("operation", "RAW_DESCRIPTOR_INSPECTION"); json.put("generation", raw.generation);
        json.put("state", raw.status); json.put("pending", raw.pending);
        json.put("cleanup_pending", raw.pending || raw.openCompleted != raw.closeCompleted);
        json.put("invalidated", raw.invalidated); json.put("permission", raw.permission);
        json.put("attachment_identity_matched", raw.identityMatched);
        json.put("open_calls", raw.openCalls); json.put("open_completed", raw.openCompleted);
        json.put("get_raw_descriptors_calls", raw.readCalls); json.put("get_raw_descriptors_completed", raw.readCompleted);
        json.put("close_calls", raw.closeCalls); json.put("close_completed", raw.closeCompleted);
        json.put("claim_calls", 0); json.put("release_calls", 0); json.put("transfer_calls", 0);
        json.put("failure_reason", raw.failure); json.put("worker", raw.worker); json.put("duration_ms", raw.durationMillis);
        json.put("usb_api_calls", new org.json.JSONArray(raw.calls)); json.put("text_report", report());
        json.put("streaming_ready", false); json.put("active_alternate", "NOT_OBSERVED");
        if (raw.descriptors != null) {
            Tbs5530RawDescriptors.Result parsed = raw.descriptors;
            org.json.JSONObject descriptors = new org.json.JSONObject();
            descriptors.put("length", parsed.length); descriptors.put("sha256", parsed.sha256);
            descriptors.put("status", parsed.status); descriptors.put("issues", new org.json.JSONArray(parsed.issues));
            org.json.JSONArray entries = new org.json.JSONArray();
            for (Tbs5530RawDescriptors.Entry entry : parsed.entries) entries.put(new org.json.JSONObject(entry.fields));
            descriptors.put("entries", entries); json.put("raw_descriptors", descriptors);
            info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.Result fingerprint =
                    info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.assess(parsed);
            org.json.JSONObject assessed = new org.json.JSONObject();
            assessed.put("match", fingerprint.match.name()); assessed.put("candidate", fingerprint.candidate.name());
            assessed.put("differences", new org.json.JSONArray(fingerprint.differences));
            assessed.put("hardware_upload_allowed", false); assessed.put("firmware_running", "NOT_PROVEN");
            json.put("fx2_fingerprint", assessed);
            json.put("firmware_state", fingerprint.candidate
                    == info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE
                    ? "FX2_DEFAULT" : fingerprint.candidate.name());
        }
        return json;
    }

    private static String firmwareState(info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.Snapshot s) {
        if (s.state == info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.State.OPERATIONAL_CANDIDATE) return "OPERATIONAL_CANDIDATE";
        if (s.boundaries.fullLengthCompleted == 132) return "UPLOAD_COMPLETE_OPERATIONAL_UNVERIFIED";
        if (s.boundaries.nativeCallStarted > 0) return "FIRMWARE_UPLOAD_INCOMPLETE";
        return s.before != null && info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.assess(s.before).candidate
                == info.martinmarinov.drivers.usb.tbs5530.Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE
                ? "FX2_DEFAULT" : "NOT_OBSERVED";
    }
    private org.json.JSONObject bootstrapReport() throws org.json.JSONException {
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap.Snapshot s = bootstrap.snapshot();
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("operation", "DEBUG_ONE_SHOT_FX2"); json.put("state", s.state.name());
        json.put("timestamp_epoch_ms", System.currentTimeMillis()); json.put("duration_ms", s.durationMillis);
        json.put("epoch", s.epoch); json.put("pending", s.pending); json.put("cleanup_pending", bootstrap.cleanupPending());
        json.put("attempted", s.attempted); json.put("completed", s.completed); json.put("transferred_bytes", s.bytes);
        json.put("legacy_counter_semantics", "ADAPTER_REQUESTED_AND_OWNER_VALIDATED_NOT_NATIVE");
        json.put("run_epoch", s.boundaries.runEpoch); json.put("monotonic_timebase", s.boundaries.timebase);
        json.put("planned_ordinal", s.boundaries.plannedOrdinal);
        json.put("guard_requested", s.boundaries.guardRequested); json.put("guard_accepted", s.boundaries.guardAccepted);
        json.put("native_call_started", s.boundaries.nativeCallStarted); json.put("native_call_completed", s.boundaries.nativeCallCompleted);
        json.put("full_length_completed", s.boundaries.fullLengthCompleted);
        json.put("image_bytes_full_length", s.boundaries.imageBytesFullLength);
        json.put("payload_bytes_full_length", s.boundaries.payloadBytesFullLength);
        json.put("boundary_events", new org.json.JSONArray(s.boundaries.events));
        json.put("cleanup_failure", s.cleanupFailure); json.put("firmware_state", firmwareState(s));
        json.put("failure_chain", new org.json.JSONArray(s.failureChain));
        json.put("plan", "TBS_SOURCE_DUAL_RESET");
        json.put("firmware_size", info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareValidation.SIZE);
        json.put("firmware_sha256", info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareValidation.SHA256);
        json.put("source_provenance", "USER_ATTESTED_ARCHIVE_METADATA_HASH_VERIFIED");
        json.put("failure", s.failure); json.put("attachment_events", bootstrap.attachmentEvents());
        json.put("requests", new org.json.JSONArray(s.transfers));
        json.put("android_api_results", new org.json.JSONArray(bootstrap.apiResults()));
        json.put("before", descriptorReport(s.before)); json.put("after", descriptorReport(s.after));
        json.put("ready", false); json.put("frontend_tested", false); json.put("active_configuration", "NOT_OBSERVED");
        json.put("active_alternate", "NOT_OBSERVED"); json.put("text_report", report());
        return json;
    }
    private org.json.JSONObject descriptorReport(Tbs5530RawDescriptors.Result raw) throws org.json.JSONException {
        org.json.JSONObject json = new org.json.JSONObject();
        if (raw == null) { json.put("status", "NOT_OBSERVED"); return json; }
        json.put("length", raw.length); json.put("sha256", raw.sha256); json.put("status", raw.status);
        json.put("issues", new org.json.JSONArray(raw.issues));
        org.json.JSONArray entries = new org.json.JSONArray();
        for (Tbs5530RawDescriptors.Entry entry : raw.entries) entries.put(new org.json.JSONObject(entry.fields));
        json.put("entries", entries); return json;
    }
    void observerFailed() {
        main();
        if (approval != null) approval.foreground(false);
        if (bootstrap != null && bootstrap.snapshot().pending) bootstrap.close();
    }

    synchronized org.json.JSONObject structuredReport() throws org.json.JSONException {
        if (bootstrapOperation && bootstrap != null) return bootstrapReport();
        if (rawOperation && rawInspection != null) return rawReport();
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("timestamp_epoch_ms",System.currentTimeMillis());json.put("operation_id",clicks);
        json.put("generation",session==null?0:session.getGeneration());
        json.put("state",session==null?"DETECTED":session.getState().name());
        json.put("permission",permission);json.put("permission_only",permissionOnly);
        json.put("permission_requested",permissionRequested);json.put("pre_granted",!permissionRequested && "ĐÃ CẤP".equals(permission));
        json.put("state_before_cleanup",stateBeforeCleanup);
        json.put("acquisition_profile_allowed",!snapshot.devices.isEmpty() && snapshot.acquisitionAllowed.get(selected));
        json.put("acquisition_result",attempts[0]>0?completed[0]>0?"OPENED":"FAIL":"NOT_RUN");
        json.put("failure_reason",failures>0?"EXCEPTION_RECORDED":"TIMEOUT".equals(permission)?"PERMISSION_DEADLINE":
                !snapshot.devices.isEmpty() && !snapshot.acquisitionAllowed.get(selected)?"MISSING_SAFE_TS_ENDPOINT_PROFILE":"NONE");
        json.put("callback_count",callbacks);json.put("correlation_device_matched",correlationMatched);
        json.put("pending",active);json.put("cleanup_pending",session!=null&&session.isCleanupPending());
        json.put("open_calls",attempts[0]);json.put("claim_calls",attempts[1]);json.put("release_calls",attempts[2]);json.put("close_calls",attempts[3]);
        json.put("open_completed",completed[0]);json.put("claim_completed",completed[1]);json.put("release_completed",completed[2]);json.put("close_completed",completed[3]);
        json.put("failures",failures);json.put("steps",new org.json.JSONArray(steps));
        org.json.JSONArray events = new org.json.JSONArray();
        for (String entry : steps) {
            String[] fields=entry.split(" \\| ");
            org.json.JSONObject event=new org.json.JSONObject();
            event.put("operation",fields[0]);
            if(fields.length>1)event.put("thread",fields[1]);
            if(fields.length==4) {
                event.put("duration_ms",Long.parseLong(fields[2].replace(" ms","")));
                event.put("result",fields[3]);
            } else if(fields.length==3)event.put("result",fields[2]);
            events.put(event);
        }
        json.put("events",events);
        json.put("test_status",active?"PENDING":failures>0?"FAIL":permissionOnly&&"ĐÃ CẤP".equals(permission)?"PERMISSION_CONFIRMED":
                !snapshot.devices.isEmpty()&&!snapshot.acquisitionAllowed.get(selected)?"BLOCKED_PROFILE":"NOT_RUN");
        json.put("text_report",report());
        return json;
    }

    private void changed() { if (observer != null) observer.run(); }
    private static void main() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
    }

    @Override public void close() {
        main();
        if (destroyed) return;
        destroyed = true;
        observer = null;
        handler.removeCallbacks(poll);
        if (approval != null) approval.foreground(false);
        preparationInvalidated.set(true);
        if (preparationObserver != null) {
            try { preparationObserver.close(); } catch (IOException error) { error(error); }
            preparationObserver = null;
        }
        if (bootstrap != null) bootstrap.close();
        if (rawInspection != null && rawInspection.snapshot().pending) rawInspection.close();
        try { closeBinding(); }
        finally {
            // Binding invalidates first and queues cleanup; shutdown drains, never joins or interrupts.
            if (runtime != null) {
                runtime.execute(() -> {
                    active = false;
                    if (resourcesIdle()) releaseReservation();
                });
                runtime.close();
            } else releaseReservation();
        }
    }
}
