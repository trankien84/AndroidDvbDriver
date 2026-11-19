// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Offline UI only: no valid firmware, USB operations, storage writes or permissions granted. */
public class Tbs5530FirmwareUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private Tbs5530FirmwareActivity start() {
        return (Tbs5530FirmwareActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), Tbs5530FirmwareActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void awaitReady(Tbs5530FirmwareActivity activity) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            instrumentation.runOnMainSync(() -> ready.set(
                    activity.findViewById(R.id.tbs5530_firmware_select).isEnabled()));
            if (ready.get()) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Firmware UI did not become ready");
    }

    private String status(Tbs5530FirmwareActivity activity) {
        return ((TextView) activity.findViewById(R.id.tbs5530_firmware_status)).getText().toString();
    }

    private void finish(Tbs5530FirmwareActivity activity) {
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void pickerRequiresOpenableDocumentAndCancellationPreservesStatus() throws Exception {
        Tbs5530FirmwareActivity activity = start();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
                assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
                assertEquals("*/*", intent.getType());
                assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
                assertEquals(0, intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        try {
            awaitReady(activity);
            String before = status(activity);
            instrumentation.addMonitor(monitor);
            instrumentation.runOnMainSync(() -> ((Button) activity.findViewById(
                    R.id.tbs5530_firmware_select)).performClick());
            instrumentation.waitForIdleSync();
            awaitReady(activity);
            assertEquals(1, monitor.getHits());
            assertEquals(before, status(activity));
            assertEquals(activity.getString(R.string.tbs5530_firmware_cancelled),
                    ((TextView) activity.findViewById(R.id.tbs5530_firmware_message)).getText().toString());
        } finally {
            instrumentation.removeMonitor(monitor);
            finish(activity);
        }
    }

    @Test public void missingResultAndFileUriAreRejectedWithoutChangingSavedCopyStatus() throws Exception {
        Tbs5530FirmwareActivity activity = start();
        try {
            awaitReady(activity);
            String before = status(activity);
            instrumentation.runOnMainSync(() -> activity.onActivityResult(5530, Activity.RESULT_OK, null));
            assertEquals(activity.getString(R.string.tbs5530_firmware_unreadable),
                    ((TextView) activity.findViewById(R.id.tbs5530_firmware_message)).getText().toString());
            instrumentation.runOnMainSync(() -> activity.onActivityResult(5530, Activity.RESULT_OK,
                    new Intent().setData(Uri.parse("file:///not-a-firmware-file"))));
            assertEquals(before, status(activity));
            assertTrue(activity.findViewById(R.id.tbs5530_firmware_select).isEnabled());
            assertEquals(activity.getString(R.string.tbs5530_firmware_unreadable),
                    ((TextView) activity.findViewById(R.id.tbs5530_firmware_message)).getText().toString());
        } finally { finish(activity); }
    }

    @Test public void unavailableDocumentReportsImportErrorWithoutChangingSavedCopy() throws Exception {
        Tbs5530FirmwareActivity activity = start();
        try {
            awaitReady(activity);
            String before = status(activity);
            instrumentation.runOnMainSync(() -> activity.onActivityResult(5530, Activity.RESULT_OK,
                    new Intent().setData(Uri.parse("content://info.martinmarinov.no_such_firmware_provider/test"))));
            awaitReady(activity);
            assertEquals(before, status(activity));
            assertEquals(activity.getString(R.string.tbs5530_firmware_unreadable),
                    ((TextView) activity.findViewById(R.id.tbs5530_firmware_message)).getText().toString());
        } finally { finish(activity); }
    }
}
