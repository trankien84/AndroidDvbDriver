// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Intent;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530TsSmoke;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/** In-memory namespace only; no app preferences, host files, firmware, ADB or USB. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530OperationalApprovalTest {
    private static final class Fixture implements Tbs5530OperationalApproval.Storage {
        final Map<String,String> files=new HashMap<>(); final Set<String> used=new HashSet<>();
        final Tbs5530TsSmoke.Limits limits=new Tbs5530TsSmoke.Limits(564,1,1000,500);
        final Tbs5530OperationalApproval approval;
        long now=1000; boolean failConsume,failAudit;
        JSONObject permit;
        Fixture() throws Exception { this(null); }
        Fixture(info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Plan plan) throws Exception {
            approval=new Tbs5530OperationalApproval(this,()->now,plan,plan == null ? limits : null,1);
            permit=new JSONObject(files.get("phase31-challenge.json"));
            permit.put("approved_maximum_ms", permit.getLong("maximum_ms"));
            permit.put("approval_id","00000000-0000-4000-8000-000000000001");
            permit.put("explicit_control_approval",true);permit.put("physical_current",true);
            permit.put("expires_elapsed_ms",301000);permit.put("heartbeat_elapsed_ms",now); publish();
        }
        void publish(){files.put("permit",permit.toString());}
        public String read() throws IOException {String value=files.get("permit");if(value==null)throw new IOException("Missing");return value;}
        public void write(String name,String value) throws IOException {if(failAudit)throw new IOException("Audit fault");files.put(name,value);}
        public boolean consumed(String id){return used.contains(id);}
        public void consume(String id)throws IOException{if(failConsume||!used.add(id))throw new IOException("Consume failed");}
    }
    @Test public void openingOperationalScreenDoesNotCreateApprovalOrAccessUsb() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Context context = instrumentation.getTargetContext();
        java.io.File challenge = new java.io.File(context.getFilesDir(), "phase31-challenge.json");
        boolean before = challenge.exists();
        android.app.Activity activity = instrumentation.startActivitySync(new Intent(context, Tbs5530OperationalDiagnosticActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        assertEquals(before, challenge.exists());
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        Object reservation = new Object(); assertTrue(Tbs5530DiagnosticController.reserveOperational(reservation));
        Tbs5530DiagnosticController.releaseOperational(reservation);
    }
    @Test public void challengeIsNotApprovalAndMissingPermitCannotRun() throws Exception {
        Fixture f=new Fixture();f.files.remove("permit");assertFalse(f.approval.eligible());assertTrue(f.used.isEmpty());
        assertThrows(IOException.class,()->f.approval.consume(f.limits,1));
    }
    @Test public void scopeHashEpochAndConsentMustMatchExactly() throws Exception {
        for(String field:new String[]{"challenge","plan_sha256","operation"}){
            Fixture f=new Fixture();f.permit.put(field,"wrong");f.publish();assertFalse(f.approval.eligible());
        }
        Fixture f=new Fixture();f.permit.put("generation",2);f.publish();assertFalse(f.approval.eligible());
        f=new Fixture();f.permit.put("explicit_control_approval",false);f.publish();assertFalse(f.approval.eligible());
        f=new Fixture();f.permit.put("physical_current",false);f.publish();assertFalse(f.approval.eligible());
    }
    @Test public void consumedIdCannotBeReusedOrChanged() throws Exception {
        Fixture f=new Fixture();assertTrue(f.approval.eligible());f.approval.consume(f.limits,1);
        assertEquals(1,f.used.size());assertFalse(f.approval.eligible());f.approval.requireCurrent();
        assertThrows(IOException.class,()->f.approval.consume(f.limits,1));
        f.permit.put("approval_id","00000000-0000-4000-8000-000000000002");f.publish();
        assertThrows(IOException.class,f.approval::requireCurrent);
    }
    @Test public void missingDurableConsumeOrAuditIsFailClosed() throws Exception {
        Fixture f=new Fixture();f.failConsume=true;assertThrows(IOException.class,()->f.approval.consume(f.limits,1));assertTrue(f.used.isEmpty());
        Fixture audit=new Fixture();audit.failAudit=true;assertThrows(IOException.class,()->audit.approval.consume(audit.limits,1));
        assertEquals(1,audit.used.size());assertFalse(audit.approval.eligible());
    }
    @Test public void heartbeatExpiryAndClockRollbackCannotBeClampedOrExtended() throws Exception {
        Fixture f=new Fixture();f.now+=2000;assertFalse(f.approval.eligible());
        f=new Fixture();f.now--;assertFalse(f.approval.eligible());
        f=new Fixture();f.permit.put("expires_elapsed_ms",301001);f.publish();assertFalse(f.approval.eligible());
        f=new Fixture();f.permit.put("heartbeat_elapsed_ms",1001);f.publish();assertFalse(f.approval.eligible());
    }
    @Test public void foregroundLossIsTerminalAndRfConsentIsSeparate() throws Exception {
        Fixture f=new Fixture();assertTrue(f.approval.eligible());assertFalse(f.approval.rfInConfirmed());
        assertThrows(IOException.class,()->f.approval.requireCurrent(1));
        f.approval.rfIn(true);f.approval.requireCurrent(1);
        assertThrows(IOException.class,()->f.approval.requireCurrent(2));
        f.approval.foreground(false);f.approval.foreground(true);assertFalse(f.approval.eligible());assertFalse(f.approval.rfInConfirmed());
    }
    @Test public void autoTranscriptRequiresDedicatedConsentAndCannotReuseManualPermit() throws Exception {
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Plan plan =
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.tune(true);
        Fixture f = new Fixture(plan);
        assertFalse(f.approval.eligible()); assertFalse(f.approval.experimentalAutoPlpApproved());
        f.permit.put("explicit_experimental_auto_plp", true); f.publish();
        assertTrue(f.approval.eligible()); assertTrue(f.approval.experimentalAutoPlpApproved());
        assertThrows(IOException.class, () -> f.approval.consume(
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.tune(false)));
        assertTrue(f.used.isEmpty()); f.approval.consume(plan);
        f.permit.put("explicit_experimental_auto_plp", false); f.publish();
        assertFalse(f.approval.experimentalAutoPlpApproved());
        assertThrows(IOException.class, f.approval::requireCurrent);
    }
    @Test public void longTuneCannotStartNearExpiryEvenWithFreshHeartbeat() throws Exception {
        info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend.Plan plan =
                info.martinmarinov.drivers.usb.tbs5530.Tbs5530Phase31PlanFixture.tune(true);
        Fixture f = new Fixture(plan); f.permit.put("explicit_experimental_auto_plp", true);
        f.permit.put("expires_elapsed_ms", f.now + plan.maximumMillis + 4999); f.publish();
        assertFalse(f.approval.eligible()); assertThrows(IOException.class, () -> f.approval.consume(plan));
        assertTrue(f.used.isEmpty());
        f.permit.put("expires_elapsed_ms", f.now + plan.maximumMillis + 5000); f.publish(); assertTrue(f.approval.eligible());
    }
    @Test public void hostTemporaryNameMustNotCollideWithAtomicFileRecoverySuffix() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("info.martinmarinov.dvbdriver.phase31offline", context.getPackageName());
        java.io.File directory = new java.io.File(context.getCacheDir(), "phase31-host-" + java.util.UUID.randomUUID());
        assertTrue(directory.mkdir());
        java.io.File base = new java.io.File(directory, "permit.json");
        java.io.File conflicting = new java.io.File(directory, "permit.json.new");
        java.io.File safe = new java.io.File(directory, "permit.tmp");
        byte[] old = "old".getBytes(java.nio.charset.StandardCharsets.UTF_8), next = "new".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile.write(base, old);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(conflicting)) { output.write(next); output.getFD().sync(); }
            assertArrayEquals(old, new android.util.AtomicFile(base).readFully());
            assertFalse("AtomicFile reader removes its own .new recovery file", conflicting.exists());
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(safe)) { output.write(next); output.getFD().sync(); }
            assertArrayEquals(old, new android.util.AtomicFile(base).readFully()); assertTrue(safe.exists());
            assertTrue(safe.renameTo(base)); assertArrayEquals(next, new android.util.AtomicFile(base).readFully());
        } finally {
            for (java.io.File file : directory.listFiles()) assertTrue(file.delete());
            assertTrue(directory.delete());
        }
    }
    @Test public void nativeBulkCapturesInvalidFdErrnoBeforeIoctlAndDoesNotModifyPayload() throws Exception {
        Class<?> c = Class.forName("info.martinmarinov.usbxfer.UsbHiSpeedBulk");
        assertTrue(c.getField("IS_PLATFORM_SUPPORTED").getBoolean(null));
        java.lang.reflect.Method read = c.getDeclaredMethod("jni_readBulk82", int.class, byte[].class, int.class, int.class, int[].class);
        read.setAccessible(true); byte[] bytes = new byte[4096]; int[] errno = {-1};
        assertEquals(-1, ((Integer)read.invoke(null, -1, bytes, 4096, 500, errno)).intValue());
        assertEquals(22, errno[0]); assertArrayEquals(new byte[4096], bytes);
        errno[0] = -1; assertEquals(-1, ((Integer)read.invoke(null, -1, bytes, 16385, 500, errno)).intValue());
        assertEquals(22, errno[0]);
    }
    @Test public void nativeSpeedObservationRejectsNegativeFdWithoutUsbIoctl() throws Exception {
        Class<?> bulk = Class.forName("info.martinmarinov.usbxfer.UsbHiSpeedBulk");
        assertTrue(bulk.getField("IS_PLATFORM_SUPPORTED").getBoolean(null));
        java.lang.reflect.Method speed = bulk.getDeclaredMethod("jni_getSpeed", int.class);
        speed.setAccessible(true); assertEquals(-22, ((Integer)speed.invoke(null, -1)).intValue());
        java.lang.reflect.Method observed = bulk.getMethod("negotiatedSpeed", android.hardware.usb.UsbDeviceConnection.class);
        assertEquals(-1, ((Integer)observed.invoke(null, new Object[]{null})).intValue());
    }
    @Test public void nativeEndpointPreparationRejectsInvalidFdWithoutAnyUsbIoctl() throws Exception {
        Class<?> bulkClass = Class.forName("info.martinmarinov.usbxfer.UsbHiSpeedBulk");
        assertTrue(bulkClass.getField("IS_PLATFORM_SUPPORTED").getBoolean(null));
        java.lang.reflect.Method nativeMethod = bulkClass.getDeclaredMethod("jni_clearHalt82", int.class);
        nativeMethod.setAccessible(true);
        assertEquals(-22, ((Integer)nativeMethod.invoke(null, -1)).intValue());
        java.lang.reflect.Method prepare = bulkClass.getMethod("prepareBulkIn82", android.hardware.usb.UsbDeviceConnection.class);
        java.lang.reflect.InvocationTargetException denied = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> prepare.invoke(null, new Object[]{null}));
        assertTrue(denied.getCause() instanceof IOException);
    }
    @Test public void endpointPreparationHasDistinctHashCountBudgetAndMandatoryConsent() throws Exception {
        Fixture f = new Fixture();
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, true);
        Tbs5530OperationalApproval approval = new Tbs5530OperationalApproval(f, () -> f.now, null, limits, 1);
        org.json.JSONObject c = new org.json.JSONObject(f.files.get("phase31-challenge.json"));
        assertEquals(2, c.getInt("usb_out_count")); assertEquals(12000, c.getLong("maximum_ms"));
        assertTrue(c.getString("transcript").contains("TS-SMOKE/v4"));
        assertTrue(c.getString("transcript").contains("USBDEVFS_CLEAR_HALT:82"));
        assertNotEquals(f.approval.planSha256(), approval.planSha256());
        c.put("approval_id", "00000000-0000-4000-8000-000000000003");
        c.put("approved_maximum_ms", 12000); c.put("explicit_control_approval", true); c.put("physical_current", true);
        c.put("expires_elapsed_ms", 301000); c.put("heartbeat_elapsed_ms", f.now); f.permit = c; f.publish();
        assertFalse(approval.eligible()); assertFalse(approval.endpointPreparationApproved());
        c.put("explicit_endpoint_preparation", true); f.publish(); assertTrue(approval.eligible()); assertTrue(approval.endpointPreparationApproved());
        approval.consume(limits, 1); c.put("explicit_endpoint_preparation", false); f.publish();
        assertThrows(IOException.class, approval::requireCurrent);
    }
    @Test public void chunkSizeChangesTsApprovalHashAndCannotReplaceExistingScope() throws Exception {
        Fixture f = new Fixture();
        org.json.JSONObject challenge = new org.json.JSONObject(f.files.get("phase31-challenge.json"));
        assertTrue(challenge.getString("transcript").contains("TS-SMOKE/v4"));
        assertTrue(challenge.getString("transcript").contains("|readApi=USBDEVFS_BULK_ERRNO|"));
        assertTrue(challenge.getString("transcript").contains("|chunkBytes=4096|"));
        assertEquals(challenge.getString("plan_sha256"), f.approval.planSha256());
        Tbs5530TsSmoke.Limits other = new Tbs5530TsSmoke.Limits(564, 1, 1000, 500, 16384);
        Fixture storage = new Fixture();
        Tbs5530OperationalApproval alternative = new Tbs5530OperationalApproval(storage, () -> 1000, null, other, 1);
        assertNotEquals(f.approval.planSha256(), alternative.planSha256());
        assertThrows(IOException.class, () -> f.approval.consume(other, 1));
    }
    @Test public void maximumBudgetAndOwnerIdentityAreMandatoryBeforeConsumption() throws Exception {
        Fixture f = new Fixture(); f.permit.put("owner_identity", "different-owner"); f.publish();
        assertFalse(f.approval.eligible()); assertTrue(f.used.isEmpty());
        f = new Fixture(); f.permit.remove("approved_maximum_ms"); f.publish(); assertFalse(f.approval.eligible());
        f = new Fixture(); f.permit.put("approved_maximum_ms", 3001); f.publish(); assertFalse(f.approval.eligible());
        f = new Fixture(); f.permit.put("protocol", 1); f.publish(); assertFalse(f.approval.eligible());
        f = new Fixture(); f.permit.put("expires_elapsed_ms", f.now + 7999); f.publish(); assertFalse(f.approval.eligible());
        assertTrue(f.used.isEmpty());
        f = new Fixture(); f.permit.put("expires_elapsed_ms", f.now + 8000); f.publish(); assertTrue(f.approval.eligible());
        f.approval.consume(f.limits, 1); f.now += 1000; f.permit.put("heartbeat_elapsed_ms", f.now); f.publish();
        f.approval.requireCurrent();
    }
    @Test public void tsBoundsAreImmutableAndCannotReplaceConsumedScope() throws Exception {
        Fixture f=new Fixture();
        assertThrows(IOException.class,()->f.approval.consume(new Tbs5530TsSmoke.Limits(564,2,1000,500),1));
        assertTrue(f.used.isEmpty());f.approval.consume(f.limits,1);
        f.approval.audit("STREAM_SELECT_INTENT",0,2);
        assertTrue(f.files.keySet().stream().anyMatch(name->name.startsWith("phase31-boundaries-")));
        assertTrue(f.files.keySet().stream().noneMatch(name->name.startsWith("phase29")));
    }
}
