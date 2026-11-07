// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import static org.junit.Assert.*;
import static info.martinmarinov.dvbdriver.Tbs5530PermissionState.*;

/** Fake identities and in-memory receipts only: no USB service, hardware grant, or live session. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530PermissionIntegrationTest {
    private final Device device = new Device("SYNTHETIC_ONLY", 1, VID, PID);
    private final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private JSONObject receipt() throws Exception {
        return Tbs5530PermissionEvidence.challenge("synthetic-session", permission, 1000)
                .put("action", "GRANT").put("observed_elapsed_ms", 1001).put("clicked_elapsed_ms", 1002);
    }
    private boolean accept(JSONObject value) throws IOException {
        return Tbs5530PermissionEvidence.receipt("synthetic-session", permission, value.toString(), 1003);
    }
    @Test public void installedDebugManifestResolvesOnlyItsExactMetadata() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = context.getPackageManager();
        ActivityInfo activity = pm.getActivityInfo(new ComponentName(context, Tbs5530AutonomousDiagnosticActivity.class), PackageManager.GET_META_DATA);
        assertTrue(activity.exported); assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activity.launchMode);
        boolean resolved = false;
        for (ResolveInfo match : pm.queryIntentActivities(new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).setPackage(context.getPackageName()), 0))
            resolved |= Tbs5530AutonomousDiagnosticActivity.class.getName().equals(match.activityInfo.name);
        assertTrue(resolved);
        int resource = activity.metaData.getInt(UsbManager.ACTION_USB_DEVICE_ATTACHED); assertNotEquals(0, resource);
        int devices = 0;
        try (XmlResourceParser parser = context.getResources().getXml(resource)) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) if (parser.getEventType() == XmlPullParser.START_TAG && "usb-device".equals(parser.getName())) {
                devices++; assertEquals(2, parser.getAttributeCount());
                assertEquals(VID, parser.getAttributeIntValue(null, "vendor-id", -1));
                assertEquals(PID, parser.getAttributeIntValue(null, "product-id", -1));
            }
        }
        assertEquals(1, devices);
    }
    @Test public void alreadyGrantedPathHasNoRequestAndNoUiHandledClaim() throws Exception {
        permission.begin(device, true, 1000); permission.confirmFresh(device, true);
        JSONObject evidence = Tbs5530PermissionEvidence.challenge("synthetic-session", permission, 1001);
        assertEquals("GRANTED", evidence.getString("state"));
        assertEquals("AUTO_PERMISSION_HANDLED_BY_OS", evidence.getString("outcome"));
        assertFalse(permission.pending()); assertFalse(evidence.toString().contains(device.path));
    }
    @Test public void exactReceiptAndFreshPermissionAreBothNeededForUiHandled() throws Exception {
        Attempt attempt = permission.begin(device, false, 1000); assertTrue(accept(receipt()));
        assertThrows(IOException.class, () -> permission.requireReady(device));
        permission.callback(attempt.token, attempt.generation, device, true);
        assertEquals(Outcome.UNVERIFIED, permission.outcome());
        permission.confirmFresh(device, true); assertEquals(Outcome.AUTO_PERMISSION_UI_HANDLED, permission.outcome());
    }
    @Test public void validReceiptCannotTurnDenialIntoPermission() throws Exception {
        Attempt attempt = permission.begin(device, false, 1000); JSONObject proof = receipt(); assertTrue(accept(proof));
        permission.callback(attempt.token, attempt.generation, device, false);
        assertFalse(accept(proof)); assertEquals(Outcome.PERMISSION_DENIED, permission.outcome());
        assertThrows(IOException.class, () -> permission.requireReady(device));
    }
    @Test public void receiptRequiresSessionTokenGenerationIdentityAndExactDevice() throws Exception {
        permission.begin(device, false, 1000);
        String[] keys = {"session", "request_token", "attachment_generation", "device_identity_sha256", "vid", "pid", "version", "action"};
        Object[] wrong = {"another-session", "another-token", 99, "wrong-hash", 1, 1, 2, "SET_DEFAULT"};
        for (int i = 0; i < keys.length; i++) assertFalse(keys[i], accept(receipt().put(keys[i], wrong[i])));
        assertFalse(Tbs5530PermissionEvidence.receipt("synthetic-session", permission, "{broken", 1003));
        assertEquals(Outcome.USER_ACTION_REQUIRED, permission.outcome());
    }
    @Test public void receiptRequiresFreshMonotonicClickNotFileTimestamp() throws Exception {
        permission.begin(device, false, 1000);
        assertFalse(accept(receipt().put("observed_elapsed_ms", 999)));
        assertFalse(accept(receipt().put("clicked_elapsed_ms", 1004)));
        assertFalse(Tbs5530PermissionEvidence.receipt("synthetic-session", permission, receipt().toString(), 31002));
    }
    @Test public void firmwareTransitionRejectsOldReceiptAndCallbackOnReusedPath() throws Exception {
        Attempt previous = permission.begin(device, false, 1000); JSONObject stale = receipt();
        permission.callback(previous.token, previous.generation, device, true); permission.confirmFresh(device, true);
        permission.firmwareTransition(); permission.begin(device, false, 2000);
        assertFalse(accept(stale)); assertEquals(Callback.IGNORED, permission.callback(previous.token, previous.generation, device, true));
        assertTrue(permission.pending());
    }
    @Test public void detachAbaDuplicateAndCancellationRemainClosed() throws Exception {
        Attempt attempt = permission.begin(device, false, 1000); JSONObject stale = receipt();
        assertEquals(Attach.AMBIGUOUS_DUPLICATE, permission.attach(new Device(device.path, device.id, VID, PID)));
        permission.cancel(); assertFalse(accept(stale));
        assertEquals(Callback.IGNORED, permission.callback(attempt.token, attempt.generation, device, true));
        assertThrows(IOException.class, () -> permission.confirmFresh(device, true));
    }
    @Test public void multipleDevicesAndLegacyPreferencesAreProtected() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Map<String, ?> old = new HashMap<>(context.getSharedPreferences("phase31-control-attempts", 0).getAll());
        assertThrows(IOException.class, () -> single(Arrays.asList(device, new Device("SYNTHETIC_OTHER", 2, VID, PID))));
        permission.begin(device, false, 1000); assertTrue(accept(receipt())); permission.cancel();
        assertEquals(old, context.getSharedPreferences("phase31-control-attempts", 0).getAll());
    }
    @Test public void usbAttachIntentAndReceiptCannotGrantAutonomousSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AutonomousSession session = new Tbs5530AutonomousSession(context, "SYNC_NATIVE_ERRNO", "GM1900", 30, 1);
        Intent intent = new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                .putExtra("bounded_user_authorization", true).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
        assertTrue(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
        permission.begin(device, false, 1000); assertTrue(accept(receipt()));
        assertFalse(session.grantAvailable()); assertThrows(IOException.class, session::require);
        assertEquals("", Tbs5530AutonomousSession.read(new java.io.File(session.directory, "ledger.jsonl"), 1024));
        session.policy.stop();
    }
}
