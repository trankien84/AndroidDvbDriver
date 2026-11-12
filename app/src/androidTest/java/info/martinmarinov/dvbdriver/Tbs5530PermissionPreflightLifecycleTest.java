// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Delivers intents while paused: no discovery tick, permission dialog, device open, or session grant. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530PermissionPreflightLifecycleTest {
    @Test public void newIntentPublishesFreshTokenAndDeadlineWithoutUsingStaleTerminalState() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Tbs5530PermissionPreflightActivity activity = (Tbs5530PermissionPreflightActivity) instrumentation.startActivitySync(
                new Intent(context, Tbs5530PermissionPreflightActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                Field foreground = Tbs5530PermissionPreflightActivity.class.getDeclaredField("foreground");
                foreground.setAccessible(true); foreground.setBoolean(activity, false);
                Field controller = Tbs5530PermissionPreflightActivity.class.getDeclaredField("preflight"); controller.setAccessible(true);
                String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
                activity.onNewIntent(new Intent().putExtra("preflight_token", first));
                Tbs5530PermissionPreflight old = (Tbs5530PermissionPreflight) controller.get(activity);
                old.cancel();
                activity.onNewIntent(new Intent().putExtra("preflight_token", second));
                Tbs5530PermissionPreflight fresh = (Tbs5530PermissionPreflight) controller.get(activity);
                assertNotSame(old, fresh); assertTrue(old.terminal()); assertFalse(fresh.terminal());
                assertEquals(second, fresh.token); assertEquals(60000, fresh.deadline - fresh.created);
                assertNull(fresh.permission.attempt());
                JSONObject json = new JSONObject(Tbs5530AutonomousSession.read(new File(context.getFilesDir(), "permission-preflight.json"), 4096));
                assertEquals(second, json.getString("preflight_token")); assertEquals("WAITING", json.getString("state"));
                activity.onNewIntent(new Intent().putExtra("preflight_token", first));
                assertSame(fresh, controller.get(activity)); assertTrue(fresh.terminal());
                assertNotEquals(Tbs5530PermissionPreflight.State.GRANTED, fresh.state());
            } catch (Throwable error) { failure.set(error); }
            finally { activity.finish(); }
        });
        instrumentation.waitForIdleSync();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
