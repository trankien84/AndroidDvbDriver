// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Instrumentation;
import android.content.Intent;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** SYNTHETIC_TEST_FIXTURE: never clicks connect or execute; injected manager only. */
public class Tbs5530Phase31UiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Tbs5530OperationalDiagnosticActivity start() {
        assertEquals("info.martinmarinov.dvbdriver.phase31offline", instrumentation.getTargetContext().getPackageName());
        return (Tbs5530OperationalDiagnosticActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), Tbs5530OperationalDiagnosticActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static void set(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    @Test public void renderDoesNotReplaceInputOrLoseCursorDuringImeCommit() throws Exception {
        Tbs5530OperationalDiagnosticActivity activity = start();
        try {
            EditText input = (EditText)field(activity, "plp");
            instrumentation.runOnMainSync(() -> {
                input.requestFocus(); input.setText("42"); input.setSelection(1);
                android.view.inputmethod.InputConnection connection = input.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
                assertNotNull(connection); assertTrue(connection.commitText("5", 1));
            });
            Thread.sleep(650);
            instrumentation.runOnMainSync(() -> { assertEquals("452", input.getText().toString()); assertTrue(input.hasFocus()); assertEquals(2, input.getSelectionStart()); });
            assertNull(field(activity, "approval")); assertNull(field(activity, "handoff"));
        } finally { instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync(); }
    }
    @Test public void tsPreparationPublishesItsOwnHashInsteadOfPreviousControlHash() throws Exception {
        Tbs5530OperationalDiagnosticActivity activity = start();
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        Tbs5530OperationalHandoff owner = Tbs5530Phase31PlanFixture.owner(instrumentation.getTargetContext(), runtime);
        try {
            set(activity, "runtime", runtime); set(activity, "handoff", owner);
            set(activity, "probe", Tbs5530Phase31PlanFixture.lockedProbe()); set(activity, "lastPlanHash", "previous-control-hash");
            java.lang.reflect.Method prepare = activity.getClass().getDeclaredMethod("prepare", int.class); prepare.setAccessible(true);
            instrumentation.runOnMainSync(() -> { try { prepare.invoke(activity, 4); } catch (Exception error) { throw new AssertionError(error); } });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((Boolean)field(activity, "busy") && System.nanoTime() < deadline) Thread.sleep(5);
            org.json.JSONObject challenge = new org.json.JSONObject(new String(new android.util.AtomicFile(
                    new java.io.File(instrumentation.getTargetContext().getFilesDir(), "phase31-challenge.json")).readFully(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("TS_SMOKE", challenge.getString("operation"));
            assertEquals(challenge.getString("plan_sha256"), field(activity, "lastPlanHash"));
        } finally {
            instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
            assertTrue(runtime.awaitTermination(5, TimeUnit.SECONDS)); owner.close(); runtime.close();
        }
    }
    @Test public void pauseDrainsFakeOwnerAndResumeCannotKeepInitializedStateOrRfConsent() throws Exception {
        Tbs5530OperationalDiagnosticActivity activity = start();
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        Tbs5530OperationalHandoff owner = Tbs5530Phase31PlanFixture.owner(instrumentation.getTargetContext(), runtime);
        try {
            set(activity, "runtime", runtime); set(activity, "handoff", owner); set(activity, "state", "FRONTEND_INITIALIZED");
            set(activity, "plan", Tbs5530Phase31PlanFixture.tune(true));
            CheckBox rf = (CheckBox)field(activity, "rf");
            instrumentation.runOnMainSync(() -> { rf.setChecked(true); instrumentation.callActivityOnPause(activity); instrumentation.callActivityOnResume(activity); });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((Boolean)field(activity, "closing") && System.nanoTime() < deadline) Thread.sleep(5);
            instrumentation.waitForIdleSync();
            assertFalse((Boolean)field(activity, "closing")); assertFalse(owner.operationalOwner());
            assertNull(field(activity, "handoff")); assertNull(field(activity, "probe")); assertNull(field(activity, "plan"));
            assertEquals("OWNER_CLOSED_RECONNECT_REQUIRED", field(activity, "state")); assertFalse(rf.isChecked());
            assertTrue(runtime.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync(); owner.close(); runtime.close();
        }
    }
}
