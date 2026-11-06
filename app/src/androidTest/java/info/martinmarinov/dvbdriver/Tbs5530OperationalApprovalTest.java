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
        Fixture() throws Exception {
            approval=new Tbs5530OperationalApproval(this,()->now,null,limits,1);
            permit=new JSONObject(files.get("phase30-challenge.json"));
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
        java.io.File challenge = new java.io.File(context.getFilesDir(), "phase30-challenge.json");
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
    @Test public void tsBoundsAreImmutableAndCannotReplaceConsumedScope() throws Exception {
        Fixture f=new Fixture();
        assertThrows(IOException.class,()->f.approval.consume(new Tbs5530TsSmoke.Limits(564,2,1000,500),1));
        assertTrue(f.used.isEmpty());f.approval.consume(f.limits,1);
        f.approval.audit("STREAM_SELECT_INTENT",0,2);
        assertTrue(f.files.keySet().stream().anyMatch(name->name.startsWith("phase30-boundaries-")));
        assertTrue(f.files.keySet().stream().noneMatch(name->name.startsWith("phase29")));
    }
}
