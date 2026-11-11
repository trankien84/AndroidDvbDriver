// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android launch/grant/stop, with device discovery and every USB boundary forbidden. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530AutonomousLaunchLifecycleTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private Intent request(String token) {
        return new Intent(context, Tbs5530AutonomousDiagnosticActivity.class)
                .putExtra("read_api", "NATIVE_USBFS_URB_8X4096")
                .putExtra("explicit_set_interface_alt0", true)
                .putExtra(Tbs5530AutonomousLaunch.TOKEN, token)
                .putExtra(Tbs5530AutonomousLaunch.CONTROL_ONLY, true);
    }
    private Object field(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner);
    }
    private Tbs5530AutonomousSession session(Tbs5530AutonomousDiagnosticActivity activity) throws Exception {
        return (Tbs5530AutonomousSession) field(activity, "session");
    }
    private void main(Checked run) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { run.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private interface Checked { void run() throws Exception; }
    private Tbs5530AutonomousDiagnosticActivity launch(String token) {
        return (Tbs5530AutonomousDiagnosticActivity) instrumentation.startActivitySync(request(token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private JSONObject challenge() throws Exception {
        return new JSONObject(Tbs5530AutonomousSession.read(new File(context.getFilesDir(), "autonomous-v1-challenge.json"), 16384));
    }
    private void noResources(Tbs5530AutonomousDiagnosticActivity activity) throws Exception {
        for (String name : new String[]{"runtime","manager","facade","handoff","bootstrap","events","permissionRequest","selected","approval"})
            assertNull(name, field(activity, name));
        Tbs5530AutonomousSession value = session(activity);
        assertNull(value.capture); assertNull(value.nativeReceiveSnapshot); assertNull(value.bootstrapSnapshot);
        assertFalse(value.initialRawObserved); assertTrue(value.controlPlaneOnly);
    }
    @Test public void newIntentClosesUngrantSessionAndPublishesCorrelatedFreshChallenge() throws Exception {
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        Tbs5530AutonomousDiagnosticActivity activity = launch(first);
        try {
            instrumentation.waitForIdleSync();
            Tbs5530AutonomousSession old = session(activity); assertEquals(first, challenge().getString("launch_token"));
            main(() -> activity.onNewIntent(request(second)));
            Tbs5530AutonomousSession fresh = session(activity);
            assertNotSame(old, fresh); assertNotEquals(old.id, fresh.id); assertEquals(second, challenge().getString("launch_token"));
            assertFalse(challenge().getBoolean("live_ready")); assertTrue(old.terminal); assertTrue(old.cleanupConfirmed);
            assertEquals("NO_RESOURCES_ACQUIRED", old.cleanupPath); assertFalse(old.grantAvailable());
            assertEquals("", Tbs5530AutonomousSession.read(new File(old.directory,"ledger.jsonl"),16384));
            main(() -> activity.onNewIntent(request(second))); assertSame(fresh, session(activity));
            main(() -> activity.onNewIntent(new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED))); assertSame(fresh, session(activity));
            noResources(activity); assertFalse(fresh.accepted());
        } finally { main(activity::finish); instrumentation.waitForIdleSync(); }
    }
    @Test public void clearTopRoutesFreshLaunchThroughAnExistingPermissionOnlyScreen() throws Exception {
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        Tbs5530AutonomousDiagnosticActivity activity = launch(first);
        Tbs5530PermissionPreflightActivity permissionScreen = null;
        try {
            instrumentation.waitForIdleSync(); Tbs5530AutonomousSession old = session(activity);
            permissionScreen = (Tbs5530PermissionPreflightActivity) instrumentation.startActivitySync(
                    new Intent(context,Tbs5530PermissionPreflightActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            assertTrue(old.cleanupConfirmed); assertEquals("NO_RESOURCES_ACQUIRED",old.cleanupPath);
            context.startActivity(request(second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            long deadline = SystemClock.elapsedRealtime()+5000;
            while (!second.equals(challenge().optString("launch_token")) && SystemClock.elapsedRealtime()<deadline) Thread.sleep(20);
            instrumentation.waitForIdleSync();
            assertEquals(second,challenge().getString("launch_token")); assertNotEquals(old.id,challenge().getString("session"));
            noResources(activity); assertFalse(session(activity).accepted());
        } finally {
            Tbs5530PermissionPreflightActivity screen = permissionScreen;
            if (screen != null) main(screen::finish);
            main(activity::finish); instrumentation.waitForIdleSync();
        }
    }
    @Test public void acceptedControlPlaneCanStopAndRestartWithoutDeviceManagerOrFirmwareHistory() throws Exception {
        String token = UUID.randomUUID().toString(); Tbs5530AutonomousDiagnosticActivity activity = launch(token);
        try {
            instrumentation.waitForIdleSync(); Tbs5530AutonomousSession old = session(activity);
            JSONObject grant = challenge(); long now = SystemClock.elapsedRealtime();
            grant.put("bounded_user_authorization",true).put("rf_in_confirmed",false)
                    .put("profile","EXPERIMENTAL_DIAGNOSTIC").put("expires_elapsed_ms",old.policy.created()+300000);
            old.write("heartbeat.json",new JSONObject().put("session",old.id).put("elapsed_ms",now).put("stop",false).toString());
            old.write("grant.json",grant.toString());
            long deadline = SystemClock.elapsedRealtime()+1500;
            while (!old.accepted() && SystemClock.elapsedRealtime()<deadline) Thread.sleep(10);
            assertTrue(old.accepted()); noResources(activity);
            assertThrows(IOException.class,old::requireDeviceIo);
            assertThrows(IOException.class,() -> old.consumeFirmwareAttachment("fake"));
            assertThrows(IOException.class,() -> old.owner("fake",1,() -> { }));
            assertThrows(IOException.class,() -> old.approval(null,null));
            assertThrows(IOException.class,() -> old.firmwareGuard(() -> { }).requireNative(0));
            main(() -> activity.onNewIntent(request(UUID.randomUUID().toString())));
            assertNotSame(old,session(activity)); assertTrue(old.cleanupConfirmed); assertEquals("NO_RESOURCES_ACQUIRED",old.cleanupPath);
            String ledger = Tbs5530AutonomousSession.read(new File(old.directory,"ledger.jsonl"),16384);
            assertTrue(ledger.contains("SESSION_GRANTED")); assertFalse(ledger.contains("ATTEMPT_CONSUMED"));
            assertNull(old.bootstrapSnapshot); assertNull(old.nativeReceiveSnapshot); noResources(activity);
        } finally { main(activity::finish); instrumentation.waitForIdleSync(); }
    }
    @Test public void cleanupNotProvenRefusesNewLaunchInsteadOfUsingZeroByteTelemetry() throws Exception {
        Tbs5530AutonomousDiagnosticActivity activity = launch(UUID.randomUUID().toString());
        try {
            instrumentation.waitForIdleSync(); Tbs5530AutonomousSession old = session(activity);
            main(() -> ((Tbs5530AutonomousLaunch)field(activity,"launches")).acquiring());
            main(() -> activity.onNewIntent(request(UUID.randomUUID().toString())));
            assertSame(old,session(activity)); assertTrue(old.terminal); assertFalse(old.cleanupConfirmed);
            assertEquals("NOT_CERTIFIED",old.cleanupPath); noResources(activity);
            assertEquals(old.id,challenge().getString("session")); assertFalse(old.grantAvailable());
        } finally { main(activity::finish); instrumentation.waitForIdleSync(); }
    }
    @Test public void controlPlaneGrantCannotBeChangedIntoLiveOrBorrowAnotherLaunchToken() throws Exception {
        Tbs5530AutonomousSession value = new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,
                false,true,UUID.randomUUID().toString(),true);
        JSONObject grant = challenge().put("bounded_user_authorization",true).put("rf_in_confirmed",false)
                .put("profile","EXPERIMENTAL_DIAGNOSTIC").put("expires_elapsed_ms",value.policy.created()+300000);
        value.policy.foreground(true);
        value.write("heartbeat.json",new JSONObject().put("session",value.id).put("elapsed_ms",SystemClock.elapsedRealtime()).put("stop",false).toString());
        grant.put("control_plane_only",false); value.write("grant.json",grant.toString()); assertThrows(IOException.class,value::acceptGrant);
        grant.put("control_plane_only",true).put("launch_token",UUID.randomUUID().toString());
        value.write("grant.json",grant.toString()); assertThrows(IOException.class,value::acceptGrant);
        assertFalse(value.accepted()); assertNull(value.nativeReceiveSnapshot);
        assertThrows(IOException.class,() -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096","SM-X926B",36,46,
                true,true,UUID.randomUUID().toString(),true));
    }
}
