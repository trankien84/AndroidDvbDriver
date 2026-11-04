// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class Tbs5530BootstrapApprovalTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Tbs5530UsbBinding.Device device = new Tbs5530UsbBinding.Device("synthetic",1,0x734c,0x5530);
    private JSONObject read(String name) throws Exception {
        try (java.io.FileInputStream in = context.openFileInput(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer,0,n);
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private void write(JSONObject permit) throws Exception {
        try (java.io.FileOutputStream out = context.openFileOutput("phase29-session-approval.json",Context.MODE_PRIVATE)) {
            out.write(permit.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private JSONObject permit(Tbs5530BootstrapApproval approval) throws Exception {
        approval.publishChallenge(); JSONObject json = read("phase29-challenge.json");
        json.put("expires_elapsed_ms",SystemClock.elapsedRealtime()+60000);
        json.put("heartbeat_elapsed_ms",SystemClock.elapsedRealtime());
        json.put("explicit_session_upload_approval",true); json.put("physical_host_and_otg_confirmed",true);
        return json;
    }
    private void denied(Tbs5530BootstrapApproval approval) throws Exception {
        try { approval.requireCurrent(); fail(); } catch(IOException expected) { }
    }
    private void clean() {
        for(String name:new String[]{"phase29-session-approval.json","phase29-challenge.json","phase29-write-request.json"}) {
            File file = new File(context.getFilesDir(),name); if(file.exists()) assertTrue(file.delete());
        }
        context.getSharedPreferences("phase29-attempt",Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void launchPermissionAttachAndIntentDoNotCreateSessionApproval() throws Exception {
        clean(); try {
            Tbs5530BootstrapApproval approval = new Tbs5530BootstrapApproval(context,device);
            approval.foreground(true); approval.publishChallenge(); denied(approval); assertFalse(approval.consumed());
        } finally { clean(); }
    }
    @Test public void expiryHeartbeatForegroundIdentityAndExplicitPermissionsAreRequired() throws Exception {
        clean(); try {
            Tbs5530BootstrapApproval approval = new Tbs5530BootstrapApproval(context,device);
            JSONObject valid = permit(approval); write(valid); denied(approval);
            approval.foreground(true); approval.requireCurrent();
            for(String field:new String[]{"challenge","install","plan","sha256","attachment_hash"}) {
                JSONObject bad = new JSONObject(valid.toString()); bad.put(field,"wrong"); write(bad); denied(approval);
            }
            for(String field:new String[]{"explicit_session_upload_approval","physical_host_and_otg_confirmed"}) {
                JSONObject bad = new JSONObject(valid.toString()); bad.put(field,false); write(bad); denied(approval);
            }
            JSONObject old = new JSONObject(valid.toString()); old.put("heartbeat_elapsed_ms",SystemClock.elapsedRealtime()-10000);
            write(old); denied(approval);
            old = new JSONObject(valid.toString()); old.put("expires_elapsed_ms",0); write(old); denied(approval);
            write(valid); approval.foreground(false); denied(approval);
        } finally { clean(); }
    }
    @Test public void crashRestartReinstallAndDuplicateConsumptionCannotReusePermit() throws Exception {
        clean(); try {
            Tbs5530BootstrapApproval first = new Tbs5530BootstrapApproval(context,device);
            first.foreground(true); write(permit(first)); first.consume(); assertTrue(first.consumed());
            try { first.consume(); fail(); } catch(IOException expected) { }
            Tbs5530BootstrapApproval restart = new Tbs5530BootstrapApproval(context,device);
            restart.foreground(true); denied(restart); assertTrue(restart.consumed());
            context.getSharedPreferences("phase29-attempt",Context.MODE_PRIVATE).edit().clear().commit();
            Tbs5530BootstrapApproval reinstall = new Tbs5530BootstrapApproval(context,device);
            reinstall.foreground(true); denied(reinstall);
        } finally { clean(); }
    }
    @Test public void ordinalAcknowledgmentIsSeparateFromLiveHeartbeat() throws Exception {
        clean(); try {
            Tbs5530BootstrapApproval approval = new Tbs5530BootstrapApproval(context,device);
            approval.foreground(true); JSONObject json = permit(approval); json.put("authorized_ordinal",1); write(json);
            approval.requireWrite(1);
            try { approval.requireWrite(2); fail(); } catch(IOException expected) { }
            try { approval.requireWrite(0); fail(); } catch(IOException expected) { }
        } finally { clean(); }
    }
}
