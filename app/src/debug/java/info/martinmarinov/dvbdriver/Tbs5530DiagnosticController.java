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
    boolean canRequestPermission() { return !destroyed && resourcesIdle() && reservationAvailable() && !snapshot.devices.isEmpty(); }
    boolean canInspectRaw() { return rawPort != null && canRequestPermission(); }
    boolean canStart() { return canRequestPermission() && snapshot.acquisitionAllowed.get(selected); }
    boolean isActive() { return active; }
    boolean isRuntimeTerminated() { return runtime == null || runtime.isTerminated(); }

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
        if (rawOperation && rawInspection != null) {
            Tbs5530RawDescriptorInspection.Snapshot raw = rawInspection.snapshot();
            out.append("Raw inspection: ").append(raw.status).append("; generation: ").append(raw.generation)
                    .append("; pending: ").append(raw.pending).append("; lỗi: ").append(raw.failure).append('\n');
            out.append("open/read/close: ").append(raw.openCalls).append('/').append(raw.readCalls).append('/').append(raw.closeCalls)
                    .append("; close hoàn tất: ").append(raw.closeCompleted).append('\n');
            if (raw.descriptors != null) out.append("Raw bytes: ").append(raw.descriptors.length)
                    .append("; parser: ").append(raw.descriptors.status).append("; SHA-256: ").append(raw.descriptors.sha256).append('\n');
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
        }
        return json;
    }

    synchronized org.json.JSONObject structuredReport() throws org.json.JSONException {
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
