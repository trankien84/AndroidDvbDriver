// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop.Kind;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

public class Tbs5530BootstrapApprovalTest extends Tbs5530ApprovalDeterministicTest {
    @Test public void isolatedAndroidStorageCannotTouchTargetFilesOrAttemptPreferences() throws Exception {
        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Instrumentation runs as the target UID; its APK Context need not own writable data.
        // Check exact isolated IDs BEFORE requesting any files/preferences directory.
        String target = testContext.getPackageName();
        assertTrue(target.equals("info.martinmarinov.dvbdriver.phase29offline") || target.equals("info.martinmarinov.dvbdriver.phase31offline"));
        assertEquals(target + ".test", instrumentationContext.getPackageName());
        String namespace = "approval-test-" + UUID.randomUUID();
        File directory = new File(testContext.getFilesDir(), namespace);
        assertTrue(directory.mkdir());
        assertTrue(directory.getCanonicalPath().startsWith(testContext.getFilesDir().getCanonicalPath() + File.separator));
        String prefs = namespace + "-attempt";
        try {
            Tbs5530BootstrapApproval.AndroidStorage store = new Tbs5530BootstrapApproval.AndroidStorage(testContext, directory, prefs);
            Tbs5530BootstrapApproval approval = new Tbs5530BootstrapApproval(store, new FakeClock(), "synthetic");
            approval.publishChallenge();
            assertTrue(new File(directory, "phase29-challenge.json").isFile());
            assertFalse(new File(testContext.getFilesDir(), "phase29-challenge.json").exists());
            assertFalse(testContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean("consumed", false));
            assertTrue(testContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putBoolean("consumed", true).commit());
            assertTrue(approval.consumed());
            denied(Kind.ATTEMPT_CONSUMED, approval::consume);
            store.write("phase29-session-approval.json", "{\"version\":1}");
            store.write("phase29-session-approval.json", "{\"version\":2}");
            assertEquals(2, new JSONObject(store.read("phase29-session-approval.json")).getInt("version"));
        } finally {
            for (File file : directory.listFiles()) assertTrue(file.delete());
            assertTrue(directory.delete());
            assertTrue(testContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit());
        }
    }
}
