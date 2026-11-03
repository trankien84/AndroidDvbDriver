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

/** Retained debug owner. Only startSelected() requests permission; no connection escapes. */
final class Tbs5530DiagnosticController implements AutoCloseable {
    interface FacadeFactory { Tbs5530UsbBinding.Facade create(); }
    interface Metadata { Tbs5530DiagnosticMetadata.Snapshot read(); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FacadeFactory factory;
    private final Metadata metadata;
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

    Tbs5530DiagnosticController(FacadeFactory factory, Metadata metadata) {
        this(factory, metadata, 30000, 10000);
    }

    Tbs5530DiagnosticController(FacadeFactory factory, Metadata metadata,
                               long permissionTimeout, long acquisitionTimeout) {
        this.factory = factory;
        this.metadata = metadata;
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
    boolean canStart() { return !destroyed && !active && !snapshot.devices.isEmpty(); }
    boolean isActive() { return active; }
    boolean isRuntimeTerminated() { return runtime == null || runtime.isTerminated(); }

    void startSelected() {
        main();
        if (!canStart()) return;
        Tbs5530UsbBinding.Device device = snapshot.devices.get(selected);
        if (device.vendorId != 0x734c || device.productId != 0x5530) return;
        synchronized (this) {
            steps.clear();
            for (int i = 0; i < attempts.length; i++) { attempts[i] = 0; completed[i] = 0; }
            failures = 0;
            permission = "ĐANG KIỂM TRA";
        }
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
            binding.start(device);
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
        if (!bindingClosed && binding != null && !binding.isPending()) {
            // CONNECTED means ownership only. Close immediately, without probing readiness.
            connectedObserved = session.getState() == Tbs5530Session.State.CONNECTED;
            synchronized (this) { steps.add("Phiên trước khi đóng: " + session.getState()); }
            closeBinding();
        }
        if (bindingClosed && (binding == null || !binding.isPending())) active = false;
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
                synchronized (Tbs5530DiagnosticController.this) { permission = "ĐANG CHỜ"; }
                return delegate.requestPermission(device, correlation, (token, reported, granted) -> {
                    synchronized (Tbs5530DiagnosticController.this) {
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
        try { closeBinding(); }
        finally {
            // Binding invalidates first and queues cleanup; shutdown drains, never joins or interrupts.
            if (runtime != null) runtime.close();
        }
    }
}
