// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidUsbFacade;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AutonomousAndroidAccess;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Debug-only USB permission preparation, with no session grant, owner or device I/O. */
public final class Tbs5530PermissionPreflightActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object reservation = new Object();
    private Tbs5530PermissionPreflight preflight;
    private TextView status;
    private boolean foreground, reserved;
    private final java.util.Set<String> usedTokens = new java.util.HashSet<>();
    private final Runnable tick = new Runnable() {
        public void run() {
            if (preflight == null || preflight.terminal()) { releaseReservation(); return; }
            try { preflight.tick(foreground); }
            catch (IOException | RuntimeException error) { fail(); }
            if (!preflight.terminal()) ui.postDelayed(this, 200); else releaseReservation();
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this); status.setText("Chuẩn bị quyền USB tối đa 60 giây; chưa bắt đầu phiên firmware."); layout.addView(status);
        Button cancel = new Button(this); cancel.setText("CANCEL — hủy chuẩn bị quyền USB");
        cancel.setOnClickListener(v -> cancel()); layout.addView(cancel); setContentView(layout);
        beginPreflight(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        beginPreflight(intent);
    }
    private void beginPreflight(Intent intent) {
        ui.removeCallbacks(tick);
        try {
            String token = intent == null ? null : intent.getStringExtra("preflight_token");
            if (token == null || !token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || usedTokens.contains(token) || usedTokens.size() >= 16) throw new IOException("Fresh preflight token required");
            if (preflight != null) preflight.cancel();
            releaseReservation(); preflight = null;
            usedTokens.add(token);
            if (!Tbs5530DiagnosticController.reserveOperational(reservation)) throw new IOException("Another debug workflow is active");
            reserved = true;
            Tbs5530AndroidUsbFacade.Manager manager = Tbs5530AutonomousAndroidAccess.manager(getApplicationContext());
            Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(getApplicationContext(), manager);
            preflight = new Tbs5530PermissionPreflight(token, SystemClock::elapsedRealtime,
                    new Tbs5530PermissionPreflight.Port() {
                        public List<Tbs5530UsbBinding.Device> devices() throws IOException { return manager.devices(); }
                        public boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException { return manager.hasPermission(device); }
                        public Tbs5530UsbBinding.Registration request(Tbs5530UsbBinding.Device device, String token, Tbs5530UsbBinding.PermissionCallback callback) throws IOException {
                            return facade.requestPermission(device, token, (id, actual, allowed) -> ui.post(() -> callback.result(id, actual, allowed)));
                        }
                        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) throws IOException { return facade.listen(events); }
                    }, this::publish);
            preflight.start();
            if (foreground) ui.post(tick);
        } catch (IOException | RuntimeException error) { fail(); }
    }
    private void publish(Tbs5530PermissionPreflight value, long now) throws IOException {
        try {
            JSONObject json = new JSONObject().put("version", 1).put("mode", "PERMISSION_ONLY")
                    .put("preflight_token", value.token).put("created_elapsed_ms", value.created)
                    .put("deadline_elapsed_ms", value.deadline).put("elapsed_ms", now)
                    .put("state", value.state().name()).put("reason", value.reason())
                    .put("outcome", value.permission.outcome().name()).put("vid", Tbs5530PermissionState.VID).put("pid", Tbs5530PermissionState.PID);
            Tbs5530PermissionState.Attempt attempt = value.permission.attempt();
            if (attempt != null) json.put("request_token", attempt.token).put("attachment_generation", attempt.generation)
                    .put("device_identity_sha256", Tbs5530PermissionEvidence.identity(attempt)).put("requested_elapsed_ms", attempt.requestedAt);
            Tbs5530DurableFile.write(new File(getFilesDir(), "permission-preflight.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            status.setText(value.state().name() + "\n" + value.reason());
            if (value.terminal()) { ui.removeCallbacks(tick); releaseReservation(); }
        } catch (JSONException error) { throw new IOException("Cannot encode private preflight status", error); }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; ui.removeCallbacks(tick); ui.post(tick); }
    @Override protected void onPause() {
        foreground = false;
        if (preflight != null && !preflight.terminal() && !preflight.mayPause()) cancel();
        super.onPause();
    }
    @Override protected void onStop() { if (!foreground) cancel(); super.onStop(); }
    @Override protected void onDestroy() { cancel(); super.onDestroy(); }
    private void cancel() {
        try { if (preflight != null) preflight.cancel(); } catch (IOException error) { status.setText("UNVERIFIED"); }
        ui.removeCallbacks(tick); releaseReservation();
    }
    private void fail() {
        try { if (preflight != null) preflight.fail("PREFLIGHT_UNVERIFIED"); } catch (IOException ignored) { }
        status.setText("UNVERIFIED"); ui.removeCallbacks(tick); releaseReservation();
    }
    private void releaseReservation() {
        if (reserved) { Tbs5530DiagnosticController.releaseOperational(reservation); reserved = false; }
    }
}
