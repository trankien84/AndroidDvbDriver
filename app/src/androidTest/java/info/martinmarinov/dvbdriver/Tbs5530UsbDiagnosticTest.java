// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Application;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530LifecycleCoordinator;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;

import static org.junit.Assert.*;

/** No test asks the Android USB manager for permission or opens hardware. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530UsbDiagnosticTest {
    private static final Tbs5530UsbBinding.Device DEVICE = new Tbs5530UsbBinding.Device(
            "/private/device-secret", 987654321, 0x734c, 0x5530);
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private static final class FakeFacade implements Tbs5530UsbBinding.Facade {
        final AtomicInteger listeners = new AtomicInteger(), permissions = new AtomicInteger();
        final AtomicInteger opens = new AtomicInteger(), claims = new AtomicInteger();
        final AtomicInteger releases = new AtomicInteger(), closes = new AtomicInteger();
        final AtomicInteger transfers = new AtomicInteger();
        final CountDownLatch claiming = new CountDownLatch(1), claimGate = new CountDownLatch(1);
        volatile boolean granted, present = true, blockClaim, failOpen, failRelease;
        volatile Tbs5530UsbBinding.PermissionCallback callback;
        volatile String correlation;
        volatile Tbs5530UsbBinding.Events events;

        @Override public List<Tbs5530UsbBinding.Device> devices() {
            return present ? Collections.singletonList(DEVICE) : Collections.emptyList();
        }
        @Override public boolean hasPermission(Tbs5530UsbBinding.Device device) { return granted; }
        @Override public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) {
            this.events = events;
            listeners.incrementAndGet();
            return () -> listeners.decrementAndGet();
        }
        @Override public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device device,
                String correlation, Tbs5530UsbBinding.PermissionCallback callback) {
            this.correlation = correlation;
            this.callback = callback;
            permissions.incrementAndGet();
            return () -> permissions.decrementAndGet();
        }
        void respond(boolean grant) {
            granted = grant;
            callback.result(correlation, DEVICE, grant);
        }
        @Override public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device device) throws IOException {
            opens.incrementAndGet();
            if (failOpen) throw new IOException("/private/device-secret serial-secret 987654321");
            return new Tbs5530LifecycleCoordinator.Connection() {
                @Override public void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException {
                    claims.incrementAndGet();
                    claiming.countDown();
                    if (blockClaim) {
                        try {
                            if (!claimGate.await(5, TimeUnit.SECONDS)) throw new IOException("fake claim blocked");
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IOException(failure);
                        }
                    }
                    receipt.acquired();
                }
                @Override public int controlTransfer(int type, int request, int value, int index,
                        byte[] buffer, int offset, int length, int timeout) {
                    transfers.incrementAndGet();
                    throw new AssertionError("Diagnostic must not transfer data");
                }
                @Override public void cancel() { }
                @Override public void release() throws IOException {
                    releases.incrementAndGet();
                    if (failRelease) throw new IOException("serial-secret /private/device-secret");
                }
                @Override public void close() { closes.incrementAndGet(); }
            };
        }
    }

    private Tbs5530DiagnosticController controller(FakeFacade facade, long timeout) {
        AtomicReference<Tbs5530DiagnosticController> result = new AtomicReference<>();
        main(() -> result.set(new Tbs5530DiagnosticController(() -> facade,
                () -> new Tbs5530DiagnosticMetadata.Snapshot("USB Host: có\nVID:PID 734c:5530",
                        Collections.singletonList("TBS5530 734c:5530"), Collections.singletonList(DEVICE)), timeout, timeout)));
        return result.get();
    }
    private void main(Runnable task) { instrumentation.runOnMainSync(task); }
    private void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        await("Hết thời gian chờ điều kiện giả lập", condition);
    }
    private void await(String failure, java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (System.nanoTime() < deadline) {
            if (condition.call()) return;
            Thread.sleep(20);
        }
        fail(failure);
    }
    private void idle(Tbs5530DiagnosticController controller) throws Exception {
        await(() -> {
            AtomicReference<Boolean> active = new AtomicReference<>();
            main(() -> active.set(controller.isActive()));
            return !active.get();
        });
    }
    private void finish(Tbs5530DiagnosticController controller) throws Exception {
        main(controller::close);
        await(controller::isRuntimeTerminated);
    }
    private static void redacted(String report) {
        assertFalse(report.contains("device-secret"));
        assertFalse(report.contains("serial-secret"));
        assertFalse(report.contains("987654321"));
        assertFalse(report.contains("/private/"));
    }

    @Test public void rawInspectionWithoutTsEndpointNeverClaimsAndExportsBoundedResult() throws Exception {
        FakeFacade fake = new FakeFacade();
        AtomicInteger opens = new AtomicInteger(), reads = new AtomicInteger(), closes = new AtomicInteger();
        AtomicReference<Tbs5530DiagnosticController> ref = new AtomicReference<>();
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptorInspection.Port port =
                new info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptorInspection.Port() {
            @Override public List<Tbs5530UsbBinding.Device> devices() { return Collections.singletonList(DEVICE); }
            @Override public boolean hasPermission(Tbs5530UsbBinding.Device device) { return true; }
            @Override public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) { return () -> { }; }
            @Override public info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptorInspection.Connection open(Tbs5530UsbBinding.Device device) {
                assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper());
                opens.incrementAndGet();
                return new info.martinmarinov.drivers.usb.tbs5530.Tbs5530RawDescriptorInspection.Connection() {
                    @Override public byte[] getRawDescriptors() { reads.incrementAndGet(); return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,0,1,1,2,3,1}; }
                    @Override public void close() { closes.incrementAndGet(); }
                };
            }
        };
        main(() -> ref.set(new Tbs5530DiagnosticController(() -> fake,
                () -> new Tbs5530DiagnosticMetadata.Snapshot("metadata", Collections.singletonList("TBS"),
                        Collections.singletonList(DEVICE), Collections.singletonList(false)), port, 2000, 2000)));
        Tbs5530DiagnosticController owner = ref.get();
        try {
            main(() -> { assertFalse(owner.canStart()); assertTrue(owner.canInspectRaw()); owner.rawSelected();
                assertFalse(owner.canRequestPermission()); owner.startSelected(); owner.rawSelected(); });
            idle(owner);
            org.json.JSONObject json = owner.structuredReport();
            assertEquals("READ_AND_CLOSED", json.getString("state"));
            assertEquals(1, opens.get()); assertEquals(1, reads.get()); assertEquals(1, closes.get());
            assertEquals(0, fake.opens.get()); assertEquals(0, fake.claims.get()); assertEquals(0, fake.transfers.get());
            assertFalse(json.getBoolean("pending")); assertFalse(json.getBoolean("streaming_ready"));
            assertEquals("PARTIAL_OR_INCONSISTENT", json.getJSONObject("raw_descriptors").getString("status"));
            redacted(json.toString());
        } finally { finish(owner); }
    }

    @Test public void twoDebugOwnersCannotOpenParallelConnections() throws Exception {
        FakeFacade fake = new FakeFacade(); fake.granted = true; fake.blockClaim = true;
        FakeFacade second = new FakeFacade(); second.granted = true;
        Tbs5530DiagnosticController first = controller(fake, 2000), other = controller(second, 2000);
        try {
            main(first::startSelected); assertTrue(fake.claiming.await(3, TimeUnit.SECONDS));
            main(() -> { assertFalse(other.canRequestPermission()); other.startSelected(); });
            assertEquals(0, second.opens.get());
            fake.claimGate.countDown(); idle(first);
            main(() -> { assertTrue(other.canStart()); other.startSelected(); });
            idle(other); assertEquals(1, second.opens.get());
        } finally { fake.claimGate.countDown(); finish(first); finish(other); }
    }

    @Test public void rawInspectionBlockedWhileExistingCycleOwnsOrAcquiresConnection() throws Exception {
        FakeFacade fake = new FakeFacade(); fake.granted = true; fake.blockClaim = true;
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected); assertTrue(fake.claiming.await(3, TimeUnit.SECONDS));
            main(() -> { assertFalse(owner.canInspectRaw()); owner.rawSelected(); });
            assertEquals(1, fake.opens.get()); assertEquals(0, fake.closes.get());
            fake.claimGate.countDown(); idle(owner); assertEquals(1, fake.closes.get());
        } finally { fake.claimGate.countDown(); finish(owner); }
    }

    @Test public void permissionOnlyGrantExportsCorrelationWithoutAnyConnection() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::permissionSelected); await(() -> fake.callback != null); fake.respond(true); idle(owner);
            org.json.JSONObject json = owner.structuredReport();
            assertTrue(json.getBoolean("permission_only")); assertEquals(1,json.getInt("operation_id"));
            assertEquals(1,json.getInt("callback_count")); assertTrue(json.getBoolean("correlation_device_matched"));
            assertTrue(json.getLong("timestamp_epoch_ms") > 0); assertEquals(1,json.getLong("generation"));
            assertEquals(0,fake.opens.get()); assertEquals(0,fake.claims.get()); assertEquals(0,fake.transfers.get());
            assertEquals(0,json.getInt("open_calls")); assertFalse(json.getBoolean("cleanup_pending"));
            redacted(json.toString());
        } finally { finish(owner); }
    }

    @Test public void incompatibleProfileBlocksCycleButStillAllowsPermissionOnly() throws Exception {
        FakeFacade fake = new FakeFacade(); AtomicReference<Tbs5530DiagnosticController> ref = new AtomicReference<>();
        main(() -> ref.set(new Tbs5530DiagnosticController(() -> fake,
                () -> new Tbs5530DiagnosticMetadata.Snapshot("BLOCKED: missing 82",Collections.singletonList("TBS"),
                        Collections.singletonList(DEVICE),Collections.singletonList(false)),2000,2000)));
        Tbs5530DiagnosticController owner=ref.get();
        try {
            main(() -> { assertFalse(owner.canStart());assertTrue(owner.canRequestPermission());owner.startSelected(); });
            assertEquals(0,fake.opens.get()); assertNull(fake.callback);
            main(owner::permissionSelected);await(() -> fake.callback!=null);fake.respond(true);idle(owner);
            assertEquals(0,fake.opens.get());assertEquals(0,fake.claims.get());
        } finally { finish(owner); }
    }

    @Test public void metadataAndRefreshNeverCreateBindingOrAskPermission() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 1000);
        try {
            main(() -> { owner.refresh(); owner.refresh(); });
            assertEquals(0, fake.listeners.get());
            assertEquals(0, fake.permissions.get());
            assertEquals(0, fake.opens.get());
            assertTrue(owner.report().contains("CHƯA BẮT ĐẦU"));
            redacted(owner.report());
        } finally { finish(owner); }
    }

    @Test public void grantRunsExactlyOneCycleAndSecondClickWhilePendingIsIgnored() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(() -> { owner.startSelected(); owner.startSelected(); });
            await(() -> fake.callback != null);
            assertEquals(0, fake.opens.get());
            fake.respond(true);
            fake.respond(true);
            idle(owner);
            assertEquals(1, fake.opens.get());
            assertEquals(1, fake.claims.get());
            assertEquals(1, fake.releases.get());
            assertEquals(1, fake.closes.get());
            assertEquals(0, fake.listeners.get());
            assertEquals(0, fake.permissions.get());
            String report = owner.report();
            assertTrue(report, report.contains("Kết quả chu kỳ: PASS"));
            assertTrue(report, report.contains("tbs5530-usb-worker"));
            redacted(report);
            main(owner::startSelected);
            idle(owner);
            assertEquals(2, fake.opens.get());
            assertEquals(2, fake.closes.get());
            assertEquals(0, fake.transfers.get());
        } finally { finish(owner); }
    }

    @Test public void denialOpensNothingAndClosesRegistrations() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected);
            await(() -> fake.callback != null);
            fake.respond(false);
            idle(owner);
            assertEquals(0, fake.opens.get());
            assertEquals(0, fake.claims.get());
            assertEquals(0, fake.listeners.get());
            assertEquals(0, fake.permissions.get());
            assertTrue(owner.report().contains("TỪ CHỐI"));
            assertFalse(owner.report().contains("Kết quả chu kỳ: PASS"));
        } finally { finish(owner); }
    }

    @Test public void timeoutIgnoresLateGrantAndDoesNotRetry() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 200);
        try {
            main(owner::startSelected);
            await(() -> fake.callback != null);
            idle(owner);
            fake.respond(true);
            instrumentation.waitForIdleSync();
            assertEquals(0, fake.opens.get());
            assertEquals(0, fake.listeners.get());
            assertEquals(0, fake.permissions.get());
        } finally { finish(owner); }
    }

    @Test public void finalCloseWhileClaimBlockedQueuesCleanupWithoutBlockingMain() throws Exception {
        FakeFacade fake = new FakeFacade();
        fake.granted = true;
        fake.blockClaim = true;
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected);
            assertTrue(fake.claiming.await(2, TimeUnit.SECONDS));
            main(owner::close);
            assertEquals(0, fake.closes.get());
            assertEquals(0, fake.listeners.get());
            assertFalse(owner.isRuntimeTerminated());
            fake.claimGate.countDown();
            await(owner::isRuntimeTerminated);
            assertEquals(1, fake.releases.get());
            assertEquals(1, fake.closes.get());
        } finally { fake.claimGate.countDown(); finish(owner); }
    }

    @Test public void detachWhileWaitingNeverAcquiresOrRetries() throws Exception {
        FakeFacade fake = new FakeFacade();
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected);
            await(() -> fake.callback != null);
            fake.present = false;
            fake.events.detached(DEVICE);
            idle(owner);
            fake.respond(true);
            assertEquals(0, fake.opens.get());
            assertEquals(0, fake.listeners.get());
            assertEquals(0, fake.permissions.get());
        } finally { finish(owner); }
    }

    @Test public void openFailureNeverReportsExceptionMessageOrPass() throws Exception {
        FakeFacade fake = new FakeFacade();
        fake.granted = true;
        fake.failOpen = true;
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected);
            idle(owner);
            String report = owner.report();
            redacted(report);
            assertTrue(report, report.contains("IOException"));
            assertTrue(report, report.contains("Kết quả chu kỳ: FAIL"));
            assertEquals(0, fake.claims.get());
            assertEquals(0, fake.listeners.get());
        } finally { finish(owner); }
    }

    @Test public void releaseFailureStillClosesAndIsNotPass() throws Exception {
        FakeFacade fake = new FakeFacade();
        fake.granted = true;
        fake.failRelease = true;
        Tbs5530DiagnosticController owner = controller(fake, 2000);
        try {
            main(owner::startSelected);
            idle(owner);
            assertEquals(1, fake.releases.get());
            assertEquals(1, fake.closes.get());
            redacted(owner.report());
            assertTrue(owner.report().contains("Kết quả chu kỳ: FAIL"));
        } finally { finish(owner); }
    }

    @Test public void acquisitionTimeoutKeepsReopenBlockedUntilClaimReturns() throws Exception {
        FakeFacade fake = new FakeFacade();
        fake.granted = true;
        fake.blockClaim = true;
        Tbs5530DiagnosticController owner = controller(fake, 500);
        try {
            main(owner::startSelected);
            assertTrue(fake.claiming.await(2, TimeUnit.SECONDS));
            await(() -> owner.report().contains("Trạng thái: CLOSED"));
            main(() -> {
                assertTrue(owner.isActive());
                assertFalse(owner.canStart());
                owner.startSelected();
            });
            assertEquals(1, fake.opens.get());
            assertEquals(0, fake.closes.get());
            fake.claimGate.countDown();
            idle(owner);
            assertEquals(1, fake.releases.get());
            assertEquals(1, fake.closes.get());
            assertFalse(owner.report().contains("Kết quả chu kỳ: PASS"));
            assertEquals(0, fake.transfers.get());
        } finally { fake.claimGate.countDown(); finish(owner); }
    }

    @Test public void explicitActivityRetainsIdleOwnerAcrossRecreation() throws Exception {
        Context context = instrumentation.getTargetContext();
        Tbs5530UsbDiagnosticActivity initial = (Tbs5530UsbDiagnosticActivity) instrumentation.startActivitySync(
                new Intent(context, Tbs5530UsbDiagnosticActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Tbs5530DiagnosticController retained = initial.diagnosticController();
        CountDownLatch created = new CountDownLatch(1);
        AtomicReference<Tbs5530UsbDiagnosticActivity> replacement = new AtomicReference<>();
        Application application = (Application) context.getApplicationContext();
        Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof Tbs5530UsbDiagnosticActivity && activity != initial) {
                    replacement.set((Tbs5530UsbDiagnosticActivity) activity);
                    created.countDown();
                }
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        application.registerActivityLifecycleCallbacks(callbacks);
        try {
            main(initial::recreate);
            assertTrue(created.await(4, TimeUnit.SECONDS));
            main(() -> {
                assertSame(retained, replacement.get().diagnosticController());
                assertFalse(retained.isActive());
                assertTrue(retained.isRuntimeTerminated());
            });
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks);
            main(() -> { initial.finish(); if (replacement.get() != null) replacement.get().finish(); });
            instrumentation.waitForIdleSync();
        }
    }

    @Test public void debugHostFeatureIsOptional() throws Exception {
        Context context = instrumentation.getTargetContext();
        FeatureInfo[] features = context.getPackageManager().getPackageInfo(context.getPackageName(),
                PackageManager.GET_CONFIGURATIONS).reqFeatures;
        if (features != null) for (FeatureInfo feature : features) {
            if (PackageManager.FEATURE_USB_HOST.equals(feature.name)) assertEquals(0, feature.flags & FeatureInfo.FLAG_REQUIRED);
        }
    }

    @Test public void explicitActivityIgnoresExtrasRefreshesAndCopiesSanitizedReport() throws Exception {
        Context context = instrumentation.getTargetContext();
        Intent intent = new Intent(context, Tbs5530UsbDiagnosticActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("autoStart", true).putExtra("device", "/private/device-secret");
        Tbs5530UsbDiagnosticActivity activity = (Tbs5530UsbDiagnosticActivity) instrumentation.startActivitySync(intent);
        AtomicReference<String> expected = new AtomicReference<>();
        AtomicReference<ClipData> copiedClip = new AtomicReference<>();
        try {
            // startActivitySync does not guarantee window focus; clipboard reads require it.
            await("Activity chưa có focus; kiểm tra hộp thoại hệ thống/ANR trước khi chạy lại", () -> {
                AtomicReference<Boolean> focused = new AtomicReference<>();
                main(() -> focused.set(activity.hasWindowFocus() && !activity.isFinishing()));
                return focused.get();
            });
            main(() -> {
                assertTrue("Diagnostic activity must be foreground before copying", activity.hasWindowFocus());
                Tbs5530DiagnosticController owner = activity.diagnosticController();
                assertFalse(owner.isActive());
                assertTrue(owner.isRuntimeTerminated());
                assertTrue(owner.report().contains("CHƯA BẮT ĐẦU"));
                try {
                    java.io.File exported = new java.io.File(activity.getFilesDir(),"tbs5530-diagnostic.json");
                    assertTrue(exported.isFile());
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (java.io.FileInputStream input = new java.io.FileInputStream(exported)) {
                        byte[] data = new byte[4096]; int count;
                        while ((count = input.read(data)) != -1) bytes.write(data,0,count);
                    }
                    org.json.JSONObject json = new org.json.JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals(android.os.Build.VERSION.SDK_INT,json.getInt("api"));
                    assertEquals(0,json.getInt("operation_id")); assertEquals(0,json.getInt("open_calls"));
                    assertNotNull(json.getJSONObject("usb").getJSONArray("devices"));
                    redacted(json.toString());
                } catch (java.io.IOException | org.json.JSONException failure) { throw new AssertionError(failure); }
                // UI check never clicks a permission/acquisition button, including on physical devices.
                findButton(activity.getWindow().getDecorView(), "Làm mới (chỉ metadata)").performClick();
                expected.set(owner.report());
                findButton(activity.getWindow().getDecorView(), "Sao chép báo cáo đã ẩn định danh").performClick();
            });
            await("Clipboard chưa có báo cáo plain text khi Activity có focus", () -> {
                main(() -> {
                    if (!activity.hasWindowFocus()) return;
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    assertNotNull(clipboard);
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() == 1
                            && expected.get().contentEquals(clip.getItemAt(0).getText() == null ? "" : clip.getItemAt(0).getText())) {
                        copiedClip.set(clip);
                    }
                });
                return copiedClip.get() != null;
            });
            main(() -> {
                ClipData clip = copiedClip.get();
                assertNotNull("Foreground clipboard must contain the copied report", clip);
                assertTrue(clip.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN));
                assertEquals(1, clip.getItemCount());
                assertNotNull(clip.getItemAt(0).getText());
                assertNull(clip.getItemAt(0).getUri());
                assertNull(clip.getItemAt(0).getIntent());
                String copied = clip.getItemAt(0).getText().toString();
                assertEquals(expected.get(), copied);
                redacted(copied);
                assertTrue(activity.diagnosticController().isRuntimeTerminated());
            });
        } finally { main(activity::finish); instrumentation.waitForIdleSync(); }
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button result = findButton(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }
}
