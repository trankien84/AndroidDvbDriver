// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Storage-only baseline/new comparison, with no manager or USB connection dependency. */
public class Tbs5530AuditCostTest {
    @Test public void measureBounded132StepAuditWithoutUsb() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("info.martinmarinov.dvbdriver.phase29offline",context.getPackageName());
        File directory = new File(context.getFilesDir(),"audit-cost-" + UUID.randomUUID()); assertTrue(directory.mkdirs());
        for (boolean baseline : new boolean[]{true,false}) {
            long[] values = new long[264]; int[] at = {0}; long[] bytes = {0};
            Tbs5530NativeAudit verified = new Tbs5530NativeAudit(directory,(s,serialization,timing) -> bytes[0] += timing.bytes);
            Tbs5530BootstrapTelemetry.Audit audit = baseline ? s -> {
                long a = SystemClock.elapsedRealtimeNanos(); byte[] data = Tbs5530NativeAudit.encode(s);
                android.util.AtomicFile file = new android.util.AtomicFile(new File(directory,"baseline.json"));
                FileOutputStream output = file.startWrite(); output.write(data); file.finishWrite(output);
                values[at[0]++] = SystemClock.elapsedRealtimeNanos()-a; bytes[0] += data.length;
            } : s -> {
                long a = SystemClock.elapsedRealtimeNanos(); verified.checkpoint(s);
                values[at[0]++] = SystemClock.elapsedRealtimeNanos()-a;
            };
            Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry(audit,SystemClock::elapsedRealtimeNanos,"ANDROID_ELAPSED_REALTIME_NS");
            long start = SystemClock.elapsedRealtimeNanos();
            for (int ordinal = 1; ordinal <= 132; ordinal++) {
                telemetry.planned(ordinal); telemetry.guardRequested(ordinal); telemetry.guardAccepted(ordinal);
                telemetry.nativeIntent(ordinal); telemetry.nativeStarted(ordinal);
                telemetry.nativeReturned(ordinal,ordinal <= 2 || ordinal >= 131 ? 0x7f92 : (ordinal-3)*64,
                        ordinal <= 2 || ordinal >= 131 ? 1 : 64,ordinal <= 2 || ordinal >= 131 ? 1 : 64);
            }
            assertEquals(264,at[0]);
            long total = SystemClock.elapsedRealtimeNanos()-start;
            java.util.Arrays.sort(values);
            JSONObject result = new JSONObject(); result.put("mode",baseline ? "BASELINE_ATOMIC" : "VERIFIED_ATOMIC");
            result.put("checkpoints",at[0]); result.put("serialized_bytes",bytes[0]); result.put("total_ns",total);
            result.put("p50_ns",values[131]); result.put("p95_ns",values[250]); result.put("p99_ns",values[261]); result.put("max_ns",values[263]);
            android.os.Bundle status = new android.os.Bundle(); status.putString("audit_cost",result.toString());
            InstrumentationRegistry.getInstrumentation().sendStatus(2,status);
        }
        for (File file : directory.listFiles()) assertTrue(file.delete()); assertTrue(directory.delete());
    }
}
