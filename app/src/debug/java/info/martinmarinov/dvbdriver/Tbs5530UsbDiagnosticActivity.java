// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidUsbFacade;

/** Explicit debug entry point; incoming intents/extras never select or acquire USB devices. */
public final class Tbs5530UsbDiagnosticActivity extends Activity {
    private Tbs5530DiagnosticController controller;
    private TextView report;
    private Button request, refresh, permissionOnly, raw;
    private boolean reportWriteFailed;
    private String lastBootstrapExport;
    private Spinner selection;
    private Button selectFirmware, upload;
    private android.widget.EditText source, entry;
    private android.widget.CheckBox sourceAndPower;
    private final android.os.Handler heartbeatUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable permitPoll = new Runnable() {
        public void run() { if (upload != null) upload.setEnabled(controller.canBootstrap()); heartbeatUi.postDelayed(this, 500); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Object retained = getLastNonConfigurationInstance();
        Context application = getApplicationContext();
        controller = retained instanceof Tbs5530DiagnosticController ? (Tbs5530DiagnosticController) retained
                : new Tbs5530DiagnosticController(() -> new Tbs5530AndroidUsbFacade(application),
                () -> Tbs5530DiagnosticMetadata.read(application),
                new info.martinmarinov.drivers.usb.tbs5530.Tbs5530AndroidRawDescriptorPort(application), 30000, 10000);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setFitsSystemWindows(true);
        TextView title = new TextView(this);
        title.setText("USB TBS5530 — chỉ debug\nMetadata không mở USB. Đọc raw cần quyền đã có, không claim hoặc transfer; không chứng minh streaming.");
        layout.addView(title);
        selection = new Spinner(this);
        selection.setContentDescription("Chọn TBS5530");
        selection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, controller.labels()));
        selection.setSelection(controller.selectedIndex());
        selection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { controller.select(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        layout.addView(selection);
        refresh = button(layout, "Làm mới (chỉ metadata)", () -> {
            controller.refresh();
            selection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, controller.labels()));
        });
        permissionOnly = button(layout, "Chỉ yêu cầu quyền (không mở USB)", controller::permissionSelected);
        request = button(layout, "Yêu cầu quyền + chạy một chu kỳ", controller::startSelected);
        raw = button(layout, "Đọc raw descriptors một lần (không claim)", controller::rawSelected);
        source = new android.widget.EditText(this);
        source.setHint("URL archive nguồn do người dùng xác nhận");
        source.setSingleLine(true);
        source.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(source);
        entry = new android.widget.EditText(this);
        entry.setHint("Entry trong archive (./dvb-usb-id5530.fw)");
        entry.setSingleLine(true);
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        layout.addView(entry);
        sourceAndPower = new android.widget.CheckBox(this);
        sourceAndPower.setText("Tôi xác nhận nguồn/entry/raw-linear; chỉ nguồn OTG, không nguồn phụ/backfeed, không ứng dụng khác giữ tuner.");
        layout.addView(sourceAndPower);
        selectFirmware = button(layout, "Chọn firmware local (không nạp)", () -> {
            if (!sourceAndPower.isChecked() || !controller.canPrepareFirmware()) return;
            android.content.Intent picker = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(picker, 29);
        });
        upload = button(layout, "Xác nhận một lượt dual-reset (cần phê duyệt phiên)", () -> {
            if (!controller.canBootstrap()) return;
            new android.app.AlertDialog.Builder(this).setTitle("Một lần nạp FX2 RAM")
                    .setMessage("132 writes A0, gồm 7F92 và E600. Có thể CPU halt hoặc mất USB. Không retry/recovery tự động; không chứng minh DVB. Chỉ tiếp tục nếu đã duyệt riêng trong phiên.")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Nạp đúng một lần", (dialog, which) -> controller.bootstrapSelected()).show();
        });
        button(layout, "Phase 3.0 — operational/I²C (không tự ghi)", () -> {
            if (controller.isActive()) return;
            controller.close();
            startActivity(new android.content.Intent(this, Tbs5530OperationalDiagnosticActivity.class));
            finish();
        });
        button(layout, "Đóng", () -> { controller.close(); finish(); });
        button(layout, "Sao chép báo cáo đã ẩn định danh", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Chẩn đoán TBS5530", controller.report()));
        });
        report = new TextView(this);
        report.setTextIsSelectable(true);
        layout.addView(report);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout);
        setContentView(scroll);
        controller.observe(this::render);
        render();
    }

    private Button button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }

    private void render() {
        report.setText(controller.report());
        request.setEnabled(controller.canStart());
        refresh.setEnabled(!controller.isActive());
        selection.setEnabled(!controller.isActive());
        permissionOnly.setEnabled(controller.canRequestPermission());
        raw.setEnabled(controller.canInspectRaw());
        selectFirmware.setEnabled(controller.canPrepareFirmware());
        upload.setEnabled(controller.canBootstrap());
        exportReport();
    }

    static String bootstrapRevision(org.json.JSONObject json) {
        if (!"DEBUG_ONE_SHOT_FX2".equals(json.optString("operation"))) return null;
        StringBuilder key = new StringBuilder();
        for (String field : new String[]{"run_epoch", "state", "planned_ordinal", "guard_requested", "guard_accepted",
                "native_call_started", "native_call_completed", "full_length_completed", "image_bytes_full_length",
                "pending", "cleanup_pending", "failure", "cleanup_failure"}) key.append('|').append(json.opt(field));
        return key.toString();
    }
    private void exportReport() {
        try {
            org.json.JSONObject json = controller.structuredReport();
            String bootstrapRevision = bootstrapRevision(json);
            if (!reportWriteFailed && bootstrapRevision != null && bootstrapRevision.equals(lastBootstrapExport)) return;
            json.put("git_sha", BuildConfig.SOURCE_GIT_SHA);
            json.put("source_state", "BUILD_WORKTREE_NOT_ATTESTED_BY_SHA");
            json.put("model",android.os.Build.MODEL);json.put("manufacturer",android.os.Build.MANUFACTURER);
            json.put("android_version",android.os.Build.VERSION.RELEASE);json.put("api",android.os.Build.VERSION.SDK_INT);
            json.put("usb",Tbs5530DiagnosticMetadata.structured(getApplicationContext()));
            info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile.write(
                    new java.io.File(getFilesDir(),"tbs5530-diagnostic.json"),
                    json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            reportWriteFailed = false;
            lastBootstrapExport = bootstrapRevision;
        } catch (java.io.IOException | org.json.JSONException | RuntimeException error) {
            if (!reportWriteFailed) android.util.Log.w("Tbs5530Diagnostic", "Private report export failed: " + error.getClass().getSimpleName());
            reportWriteFailed = true;
            controller.observerFailed();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 29 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            controller.prepareFirmware(getApplicationContext(), data.getData(), source.getText().toString(),
                    entry.getText().toString(), sourceAndPower.isChecked());
        }
    }
    @Override protected void onResume() {
        super.onResume(); controller.foreground(true); heartbeatUi.post(permitPoll);
    }
    @Override protected void onPause() {
        controller.foreground(false); heartbeatUi.removeCallbacks(permitPoll); super.onPause();
    }

    Tbs5530DiagnosticController diagnosticController() { return controller; }

    @Override public Object onRetainNonConfigurationInstance() { return controller; }

    @Override protected void onDestroy() {
        if (controller != null) {
            controller.observe(null);
            if (!isChangingConfigurations()) controller.close();
        }
        super.onDestroy();
    }
}
