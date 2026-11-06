// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import info.martinmarinov.drivers.usb.tbs5530.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Explicit debug-only operational owner; intents never grant control approval or select a device. */
public final class Tbs5530OperationalDiagnosticActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object reservation = new Object();
    private volatile boolean foreground, destroyed, busy;
    private boolean reserved;
    private Tbs5530AndroidRuntime runtime;
    private Tbs5530OperationalHandoff handoff;
    private Tbs5530ControlProbe probe;
    private volatile Tbs5530OperationalApproval approval;
    private volatile Tbs5530Frontend.Plan plan;
    private volatile Tbs5530TsSmoke.Limits smokeLimits;
    private TextView report;
    private EditText frequency, bandwidth, plp;
    private CheckBox rf;
    private Button connect, prepareProbe, initialize, tune, status, execute;
    private volatile String state = "NOT_STARTED", failure = "NONE";
    private volatile int chipId = -1, out, in;
    private final String reportId = java.util.UUID.randomUUID().toString();
    private volatile long tsBytes, tsPackets, tsContinuityErrors, tsElapsedNanos;
    private volatile String lastPlanHash = "NONE";
    private void persistResult() throws IOException {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("state", state); json.put("failure", failure); json.put("chip_id", chipId);
            json.put("checked_control_out_completed", out); json.put("checked_control_in_completed", in);
            json.put("counter_semantics", "MANAGED_FULL_LENGTH_NOT_NATIVE_BOUNDARY_COUNT");
            json.put("plan_sha256", lastPlanHash); json.put("firmware_writes", 0);
            json.put("ts_bytes", tsBytes); json.put("ts_packets", tsPackets);
            json.put("ts_continuity_errors", tsContinuityErrors); json.put("ts_elapsed_ns", tsElapsedNanos);
            json.put("rf_in", approval != null && approval.rfInConfirmed() ? "USER_CONFIRMED" : "CONNECTION_UNCONFIRMED");
            if (handoff != null && handoff.observation() != null) {
                json.put("descriptor_sha256", handoff.observation().descriptorHash);
                json.put("descriptor_profile", handoff.observation().kind.name());
            }
            Tbs5530DurableFile.write(new File(getFilesDir(), "phase30-report-" + reportId + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException("Operational report failed", error); }
    }
    private final Runnable tick = new Runnable() {
        public void run() { if (!destroyed) { render(); ui.postDelayed(this, 250); } }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24); layout.setFitsSystemWindows(true);
        text(layout, "TBS5530 Phase 3.0 — chỉ debug. Kết nối đọc raw mới và claim(false), không nạp firmware. Mỗi chương trình I²C cần host permit và xác nhận riêng; read probe vẫn có USB OUT.");
        connect = button(layout, "Mở operational owner (raw mới + claim false)", this::connect);
        prepareProbe = button(layout, "Chuẩn bị chip-ID probe (chưa gửi USB OUT)", () -> prepare(0));
        initialize = button(layout, "Chuẩn bị frontend init (cần approval riêng)", () -> prepare(1));
        frequency = input(layout, "Frequency Hz — nhập rõ, không tự scan");
        bandwidth = input(layout, "Bandwidth Hz — không mặc định 8 MHz");
        plp = input(layout, "Manual PLP 0..255 — không mặc định 0");
        rf = new CheckBox(this); rf.setText(R.string.tbs5530_rf_in_confirmation);
        rf.setOnCheckedChangeListener((button, value) -> { if (approval != null) approval.rfIn(value); }); layout.addView(rf);
        tune = button(layout, "Chuẩn bị tune DVB-T2 (chưa ghi)", () -> prepare(2));
        status = button(layout, "Chuẩn bị 3 lần đọc lock có giới hạn", () -> prepare(3));
        button(layout, "Chuẩn bị TS smoke 64 KiB / 16 reads / 5 giây (cần lock và approval riêng)", () -> prepare(4));
        execute = button(layout, "Chạy đúng một chương trình đã được duyệt", () -> {
            if (!eligible()) return;
            new android.app.AlertDialog.Builder(this).setTitle("Control transaction riêng")
                    .setMessage("Đúng transcript/hash và số OUT/IN dưới đây; có thay đổi bank/register. Không retry hoặc corrective reset. Chỉ xác nhận sau approval riêng trong phiên.")
                    .setNegativeButton("Hủy", null).setPositiveButton("Chạy đúng một lần", (dialog, which) -> runPlan()).show();
        });
        button(layout, "Đóng và hủy quyền phiên", this::finish);
        report = text(layout, "NOT_STARTED"); report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
    }
    private TextView text(LinearLayout layout, String value) { TextView view = new TextView(this); view.setText(value); layout.addView(view); return view; }
    private EditText input(LinearLayout layout, String hint) {
        EditText view = new EditText(this); view.setHint(hint); view.setSingleLine(true);
        view.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); layout.addView(view); return view;
    }
    private Button button(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(view -> action.run()); layout.addView(button); return button;
    }
    private void requireCurrent() throws IOException {
        if (!foreground || destroyed) throw new IOException("Foreground/owner lost");
    }
    private void connect() {
        if (busy || handoff != null || !foreground || !Tbs5530DiagnosticController.reserveOperational(reservation)) return;
        reserved = true; busy = true; runtime = new Tbs5530AndroidRuntime();
        runtime.execute(() -> {
            try {
                requireCurrent(); Tbs5530AndroidUsbFacade facade = new Tbs5530AndroidUsbFacade(getApplicationContext());
                List<Tbs5530UsbBinding.Device> devices = facade.devices(); Tbs5530UsbBinding.Device selected = null;
                for (Tbs5530UsbBinding.Device device : devices) if (device.vendorId == 0x734c && device.productId == 0x5530) {
                    if (selected != null) throw new IOException("Ambiguous TBS5530 devices"); selected = device;
                }
                if (selected == null) throw new IOException("No TBS5530");
                handoff = new Tbs5530OperationalHandoff(getApplicationContext(), runtime, this::requireCurrent);
                handoff.start(selected); state = "ACQUIRING";
                ui.postDelayed(this::awaitOwner, 25);
            } catch (IOException | RuntimeException error) { fail(error); busy = false; }
        });
    }
    private void awaitOwner() {
        if (destroyed) return;
        if (handoff.pending()) { ui.postDelayed(this::awaitOwner, 25); return; }
        if (!handoff.operationalOwner()) { state = "BLOCKED"; failure = "OPERATIONAL_PROFILE_OR_PERMISSION"; busy = false; return; }
        runtime.execute(() -> {
            try {
                probe = new Tbs5530ControlProbe(handoff, new Tbs5530ControlProbe.Authorization() {
                    public void requireCurrent() throws IOException { Tbs5530OperationalDiagnosticActivity.this.requireCurrent(); currentApproval().requireCurrent(); }
                    public void consume(Tbs5530Frontend.Plan value) throws IOException { currentApproval().consume(value); }
                    public void audit(String boundary,int ordinal,int type,int request,int length,int result) throws IOException {
                        currentApproval().audit(boundary,ordinal,type,request,length,result);
                    }
                    public boolean rfInConfirmed() { return approval != null && approval.rfInConfirmed(); }
                }, new Tbs5530Frontend.Clock() {
                    public long elapsedRealtimeMillis() { return SystemClock.elapsedRealtime(); }
                    public void delayMillis(long millis) throws InterruptedException { Thread.sleep(millis); }
                });
                state = "OPERATIONAL_SESSION_VERIFIED";
            } catch (IOException | RuntimeException error) { fail(error); }
            finally { busy = false; }
        });
    }
    private Tbs5530OperationalApproval currentApproval() throws IOException {
        Tbs5530OperationalApproval value = approval; if (value == null) throw new IOException("No control approval"); return value;
    }
    private void prepare(int operation) {
        if (busy || probe == null || !foreground) return;
        final Tbs5530DvbT2TuneParameters parameters;
        try { parameters = operation == 2 ? new Tbs5530DvbT2TuneParameters(Long.parseLong(frequency.getText().toString()),
                Integer.parseInt(bandwidth.getText().toString()), Integer.parseInt(plp.getText().toString())) : null; }
        catch (IllegalArgumentException error) { failure = "INVALID_TUNE"; return; }
        if (approval != null) approval.foreground(false); approval = null; plan = null; smokeLimits = null; busy = true;
        final boolean rfConfirmed = rf.isChecked();
        runtime.execute(() -> {
            try {
                requireCurrent();
                if (operation == 4) {
                    if (probe.state() != Tbs5530Frontend.State.DVBT2_LOCKED) throw new IOException("Runtime lock required");
                    Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500);
                    Tbs5530OperationalApproval candidate = new Tbs5530OperationalApproval(getApplicationContext(), handoff.generation(), limits);
                    candidate.rfIn(rfConfirmed); approval = candidate; smokeLimits = limits; failure = "NONE";
                    state = "AWAITING_TS_SMOKE_APPROVAL"; return;
                }
                Tbs5530Frontend.Plan next = operation == 0 ? probe.planProbe() : operation == 1 ? probe.planInitialize()
                        : operation == 2 ? probe.planTune(parameters) : probe.planPollStatus(3);
                Tbs5530OperationalApproval candidate = new Tbs5530OperationalApproval(getApplicationContext(), next);
                candidate.rfIn(rfConfirmed); approval = candidate; plan = next; lastPlanHash = next.getSha256(); failure = "NONE";
                state = "AWAITING_" + next.operation + "_APPROVAL";
                persistResult();
            } catch (IOException | RuntimeException error) { fail(error); }
            finally { busy = false; }
        });
    }
    private boolean eligible() { return foreground && !busy && (plan != null || smokeLimits != null) && approval != null && approval.eligible(); }
    private void runPlan() {
        if (!eligible()) return;
        Tbs5530Frontend.Plan approved = plan;
        Tbs5530TsSmoke.Limits approvedSmoke = smokeLimits; busy = true;
        runtime.execute(() -> {
            try {
                if (approvedSmoke != null) {
                    Tbs5530TsSmoke.Result result = handoff.smoke(currentApproval(), SystemClock::elapsedRealtimeNanos).run(approvedSmoke);
                    state = result.status == Tbs5530TsSmoke.Status.STRUCTURE_CONFIRMED ? "TS_SMOKE_PASS" : result.status.name();
                    out++; tsBytes = result.bytes; tsPackets = result.packets.packets;
                    tsContinuityErrors = result.packets.continuityErrors; tsElapsedNanos = result.elapsedNanos;
                    return;
                }
                Tbs5530Frontend.Result result = probe.execute(approved); chipId = result.chipId;
                out += result.usbOutCompleted; in += result.usbInCompleted; state = result.state.name();
                if (result.operation == Tbs5530Frontend.Operation.INITIALIZE) {
                    probe.readyForTune(); state = "AWAITING_RF_IN_CONNECTION";
                }
                if (result.status != null && !result.status.locked) state = "TUNE_ATTEMPTED_NO_LOCK";
            } catch (IOException | RuntimeException error) { fail(error); }
            finally {
                try { persistResult(); }
                catch (IOException reportFailure) {
                    failure = "REPORT_FAILURE"; state = "UNCERTAIN";
                    if (approval != null) approval.foreground(false);
                    if (probe != null) probe.cancel(); if (handoff != null) handoff.revoke();
                }
                plan = null; smokeLimits = null; busy = false;
            }
        });
    }
    private void fail(Throwable error) {
        failure = error instanceof UnsupportedOperationException ? "UNSUPPORTED" : error instanceof IOException ? "IO_OR_APPROVAL_FAILURE" : "INVALID_STATE";
        state = "BLOCKED_WITH_EVIDENCE";
    }
    private void render() {
        if (report == null) return;
        connect.setEnabled(!busy && handoff == null); prepareProbe.setEnabled(!busy && probe != null);
        initialize.setEnabled(!busy && probe != null); tune.setEnabled(!busy && probe != null); status.setEnabled(!busy && probe != null);
        execute.setEnabled(eligible());
        String summary = "State: " + state + "\nFailure: " + failure + "\nChip-ID: " + (chipId < 0 ? "NOT_OBSERVED" : Integer.toHexString(chipId))
                + "\nCompleted control OUT/IN: " + out + "/" + in + "\nRF IN: " + (rf.isChecked() ? "USER_CONFIRMED" : "ANTENNA_CONNECTION_UNCONFIRMED");
        Tbs5530Frontend.Plan prepared = plan;
        if (prepared != null) summary += "\nPlan SHA-256: " + prepared.getSha256() + "\n" + prepared.getApprovalTranscript();
        report.setText(summary);
    }
    @Override public void onResume() { super.onResume(); foreground = true; ui.post(tick); }
    @Override public void onPause() {
        foreground = false; if (approval != null) approval.foreground(false);
        if (probe != null) probe.cancel(); if (handoff != null) handoff.revoke();
        ui.removeCallbacks(tick); super.onPause();
    }
    @Override public void onDestroy() {
        destroyed = true; ui.removeCallbacks(tick);
        if (handoff != null) try { handoff.close(); } catch (IOException error) { failure = "CLEANUP_FAILURE"; }
        if (runtime != null) {
            runtime.execute(() -> {
                if (reserved && (handoff == null || !handoff.pending() && handoff.cleanupFailure() == null)) {
                    reserved = false; Tbs5530DiagnosticController.releaseOperational(reservation);
                }
            }); runtime.close();
        } else if (reserved) Tbs5530DiagnosticController.releaseOperational(reservation);
        super.onDestroy();
    }
}
