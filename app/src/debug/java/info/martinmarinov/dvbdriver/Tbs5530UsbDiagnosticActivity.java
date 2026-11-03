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
    private Button request, refresh, permissionOnly;
    private boolean reportWriteFailed;
    private Spinner selection;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Object retained = getLastNonConfigurationInstance();
        Context application = getApplicationContext();
        controller = retained instanceof Tbs5530DiagnosticController ? (Tbs5530DiagnosticController) retained
                : new Tbs5530DiagnosticController(() -> new Tbs5530AndroidUsbFacade(application),
                () -> Tbs5530DiagnosticMetadata.read(application));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setFitsSystemWindows(true);
        TextView title = new TextView(this);
        title.setText("USB TBS5530 — chỉ debug\nChỉ nút yêu cầu quyền mới chạy một chu kỳ. Không firmware hoặc truyền điều khiển.");
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
        button(layout, "Đóng", () -> { controller.close(); finish(); });
        button(layout, "Sao chép báo cáo đã ẩn định danh", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Chẩn đoán TBS5530", controller.report()));
        });
        report = new TextView(this);
        report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(report);
        layout.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(layout);
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
        exportReport();
    }

    private void exportReport() {
        try {
            org.json.JSONObject json = controller.structuredReport();
            json.put("model",android.os.Build.MODEL);json.put("manufacturer",android.os.Build.MANUFACTURER);
            json.put("android_version",android.os.Build.VERSION.RELEASE);json.put("api",android.os.Build.VERSION.SDK_INT);
            json.put("usb",Tbs5530DiagnosticMetadata.structured(getApplicationContext()));
            android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(getFilesDir(),"tbs5530-diagnostic.json"));
            java.io.FileOutputStream output = null;
            try {
                output = file.startWrite();
                output.write(json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                file.finishWrite(output);
            } catch (java.io.IOException error) {
                if (output != null) file.failWrite(output);
                throw error;
            }
            reportWriteFailed = false;
        } catch (java.io.IOException | org.json.JSONException | RuntimeException error) {
            if (!reportWriteFailed) android.util.Log.w("Tbs5530Diagnostic", "Private report export failed: " + error.getClass().getSimpleName());
            reportWriteFailed = true;
        }
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
