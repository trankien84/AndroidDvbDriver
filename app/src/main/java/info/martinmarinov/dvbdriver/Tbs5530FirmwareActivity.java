// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareStore;

/** Local provisioning only: no USB access and no automatic firmware download. */
public class Tbs5530FirmwareActivity extends Activity {
    private static final int SELECT_FIRMWARE = 5530;
    private static final String SELECTING = "tbs5530_selecting";
    private static final String MESSAGE = "tbs5530_message";
    private static final ExecutorService IO = createExecutor();

    private static ExecutorService createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
    private FirmwareWork work;
    private boolean selecting;
    private Button select;
    private Button delete;
    private TextView status;
    private TextView message;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tbs5530_firmware);
        SystemBarInsets.apply(this);
        select = findViewById(R.id.tbs5530_firmware_select);
        delete = findViewById(R.id.tbs5530_firmware_delete);
        status = findViewById(R.id.tbs5530_firmware_status);
        message = findViewById(R.id.tbs5530_firmware_message);
        ((TextView) findViewById(R.id.tbs5530_firmware_identity)).setText(getString(
                R.string.tbs5530_firmware_identity, Tbs5530FirmwareStore.SIZE,
                Tbs5530FirmwareStore.SHA256));
        work = (FirmwareWork) getLastNonConfigurationInstance();
        boolean fresh = work == null;
        if (fresh) work = new FirmwareWork(new Tbs5530FirmwareStore(getApplicationContext()));
        if (savedInstanceState != null) {
            selecting = savedInstanceState.getBoolean(SELECTING);
            if (fresh) work.message = savedInstanceState.getInt(MESSAGE);
        }
        work.changed = this::render;
        select.setOnClickListener(view -> chooseFirmware());
        delete.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.tbs5530_firmware_delete)
                .setMessage(R.string.tbs5530_firmware_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tbs5530_firmware_delete, (dialog, which) -> work.delete())
                .show());
        findViewById(R.id.tbs5530_firmware_close).setOnClickListener(view -> finish());
        if (fresh) work.refresh();
        render();
    }

    private void chooseFirmware() {
        if (work.busy || selecting) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        selecting = true;
        render();
        try {
            startActivityForResult(intent, SELECT_FIRMWARE);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            selecting = false;
            work.message = R.string.tbs5530_firmware_picker_error;
            render();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SELECT_FIRMWARE) return;
        selecting = false;
        if (resultCode != RESULT_OK) {
            work.message = R.string.tbs5530_firmware_cancelled;
            render();
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            work.message = R.string.tbs5530_firmware_unreadable;
            render();
            return;
        }
        ContentResolver resolver = getApplicationContext().getContentResolver();
        work.importFirmware(() -> resolver.openInputStream(uri));
    }

    private void render() {
        boolean idle = !work.busy && !selecting;
        select.setEnabled(idle);
        delete.setEnabled(idle);
        status.setText(work.busy ? R.string.tbs5530_firmware_busy : work.available
                ? R.string.tbs5530_firmware_available : R.string.tbs5530_firmware_missing);
        message.setText(work.message == 0 ? "" : getString(work.message));
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SELECTING, selecting);
        outState.putInt(MESSAGE, work.message);
        super.onSaveInstanceState(outState);
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return work;
    }

    @Override protected void onDestroy() {
        work.changed = null;
        super.onDestroy();
    }

    // Retain local I/O across rotation without retaining an Activity or its views.
    private static final class FirmwareWork {
        final Tbs5530FirmwareStore store;
        final Handler main = new Handler(Looper.getMainLooper());
        Runnable changed;
        boolean available;
        boolean busy;
        int message;

        FirmwareWork(Tbs5530FirmwareStore store) { this.store = store; }

        void refresh() { submit(() -> 0); }

        void importFirmware(Tbs5530FirmwareImport.Source source) {
            submit(() -> {
                switch (Tbs5530FirmwareImport.run(source, store::importFirmware)) {
                    case SUCCESS: return R.string.tbs5530_firmware_imported;
                    case PERMISSION_DENIED: return R.string.tbs5530_firmware_permission_error;
                    case UNREADABLE: return R.string.tbs5530_firmware_unreadable;
                    default: return R.string.tbs5530_firmware_import_error;
                }
            });
        }

        void delete() {
            submit(() -> {
                try {
                    store.delete();
                    return R.string.tbs5530_firmware_deleted;
                } catch (IOException | RuntimeException error) {
                    return R.string.tbs5530_firmware_delete_error;
                }
            });
        }

        void submit(Operation operation) {
            if (busy) return;
            busy = true;
            notifyChanged();
            IO.execute(() -> {
                int outcome;
                boolean present;
                try {
                    outcome = operation.run();
                    present = store.isAvailable();
                } catch (RuntimeException unavailable) {
                    outcome = R.string.tbs5530_firmware_import_error;
                    present = false;
                }
                final int completedMessage = outcome;
                final boolean completedAvailability = present;
                main.post(() -> {
                    available = completedAvailability;
                    if (completedMessage != 0) message = completedMessage;
                    busy = false;
                    notifyChanged();
                });
            });
        }

        void notifyChanged() { if (changed != null) changed.run(); }
    }

    private interface Operation { int run(); }
}
