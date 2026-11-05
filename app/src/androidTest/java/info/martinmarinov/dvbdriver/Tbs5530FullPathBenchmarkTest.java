// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.FullPathRig;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530OneShotBootstrap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530FullPathBenchmarkTest {
    @Test public void isolated132StepProductionPathWithoutUsb() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("info.martinmarinov.dvbdriver.phase29offline", context.getPackageName());
        Bundle args = InstrumentationRegistry.getArguments();
        String path = args.getString("benchmark_directory");
        org.junit.Assume.assumeNotNull(path);
        if (!path.matches("/data/user/0/info\\.martinmarinov\\.dvbdriver\\.phase29offline/files/tbs5530-approval-benchmark/[a-f0-9-]{36}")) throw new AssertionError("Invalid namespace");
        File directory = new File(path);
        File expected = new File(new File(context.getFilesDir(),"tbs5530-approval-benchmark"),directory.getName());
        assertEquals(expected.getCanonicalFile(),directory.getCanonicalFile());
        assertFalse(directory.exists()); assertTrue("Cannot create isolated private namespace",directory.mkdirs());
        boolean baseline = "baseline".equals(args.getString("audit_mode"));
        StringBuilder samples = new StringBuilder();
        long[] requestStorage = new long[2], observationStorage = new long[2];
        Tbs5530ApprovalSession.Storage files = new Tbs5530ApprovalSession.Storage() {
            boolean consumed;
            public String install() { return "benchmark-only"; }
            public boolean consumed() { return consumed; }
            public void consume() throws IOException { if (consumed) throw new IOException("Consumed"); consumed = true; }
            public String read(String name) throws IOException { return readFile(new File(directory,name)); }
            public void write(String name, String content) throws IOException {
                long start = SystemClock.elapsedRealtimeNanos();
                Tbs5530DurableFile.write(new File(directory,name), content.getBytes(StandardCharsets.UTF_8));
                long end = SystemClock.elapsedRealtimeNanos();
                long[] timing = "phase29-write-request.json".equals(name) ? requestStorage
                        : "phase29-write-observation.json".equals(name) ? observationStorage : null;
                if (timing != null) { timing[0] = start; timing[1] = end; }
            }
        };
        String legacyChallenge = UUID.randomUUID().toString();
        files.write("phase29-write-request.json",new JSONObject().put("challenge",legacyChallenge).put("ordinal",24).toString());
        files.write("phase29-session-approval.json",new JSONObject().put("challenge",legacyChallenge)
                .put("install","legacy-test-only").put("upload_epoch",1).put("authorized_ordinal",24).toString());
        Tbs5530ApprovalSession approval = new Tbs5530ApprovalSession(files, new Tbs5530ApprovalSession.Clock() {
            public long now() { return SystemClock.elapsedRealtime(); }
            public void pause(long ms) throws InterruptedException { Thread.sleep(ms); }
        }, "0000000000000000000000000000000000000000000000000000000000000000");
        approval.foreground(true); approval.publishChallenge();
        long totalStart = SystemClock.elapsedRealtime();
        try {
            while (!approval.runEpoch().equals(new JSONObject(files.read("phase29-session-approval.json")).optString("run_epoch"))) {
                if (SystemClock.elapsedRealtime()-totalStart >= 30000) throw new IOException("Initial host approval missing");
                Thread.sleep(10);
            }
            Thread.sleep(3500);
            approval.requireCurrent();
            Tbs5530OneShotBootstrap.Guard guard = new Tbs5530OneShotBootstrap.Guard() {
                JSONObject sample;
                long requested, accepted;
                public String runEpoch() { return approval.runEpoch(); }
                public void requireCurrent() throws IOException {
                    if (SystemClock.elapsedRealtime()-totalStart >= 240000) throw new IOException("TOTAL_BENCHMARK_DEADLINE");
                    approval.requireCurrent();
                }
                public void requireStartBudget() throws IOException { approval.requireStartBudget(); }
                public void requireDispatch(int ordinal) throws IOException {
                    approval.requireDispatch(ordinal);
                    try { sample.put("dispatch_authorized_ns",SystemClock.elapsedRealtimeNanos()); samples.append(sample).append('\n'); }
                    catch (org.json.JSONException e) { throw new IOException(e); }
                }
                public void requireWrite(int ordinal) throws IOException {
                    requireCurrent(); requested = SystemClock.elapsedRealtimeNanos(); approval.requireWrite(ordinal);
                    accepted = SystemClock.elapsedRealtimeNanos();
                    try {
                        sample = new JSONObject(files.read("phase29-write-observation.json"));
                        sample.put("request_storage_started_ns",requestStorage[0]);
                        sample.put("request_persisted_ns",requestStorage[1]);
                        sample.put("observation_storage_started_ns",observationStorage[0]);
                        sample.put("observation_persisted_ns",observationStorage[1]);
                    } catch (org.json.JSONException e) { throw new IOException(e); }
                }
                public void requireNative(int ordinal) throws IOException {
                    long start = SystemClock.elapsedRealtimeNanos(); approval.requireNative(ordinal);
                    long end = SystemClock.elapsedRealtimeNanos();
                    try {
                        sample.put("guard_requested_ns",requested); sample.put("guard_accepted_ns",accepted);
                        sample.put("final_guard_requested_ns",start); sample.put("final_authorized_ns",end);
                    } catch (org.json.JSONException e) { throw new IOException(e); }
                }
            };
            JSONObject runtimeBefore = new JSONObject(android.os.Debug.getRuntimeStats());
            JSONObject result = FullPathRig.run(context,directory,guard,approval::consume,baseline);
            result.put("seeded_legacy_files",true); result.put("ui_wait_ms",3500);
            result.put("runtime_stats_before",runtimeBefore);
            result.put("runtime_stats_after",new JSONObject(android.os.Debug.getRuntimeStats()));
            result.put("total_duration_ms",SystemClock.elapsedRealtime()-totalStart);
            files.write("benchmark-observations.jsonl", samples.toString());
            files.write("benchmark-result.json",result.toString());
        } catch (Throwable error) {
            files.write("benchmark-observations.jsonl",samples.toString());
            files.write("benchmark-result.json","{\"status\":\"FAIL\",\"failure_kind\":\"FULL_PATH_STOP\",\"usb_calls\":0}");
            throw error;
        }
    }
    private static String readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[8193]; int count = 0, n;
            while (count < bytes.length && (n = in.read(bytes,count,bytes.length-count)) > 0) count += n;
            if (count == bytes.length) throw new IOException("Oversized approval");
            return new String(bytes,0,count,StandardCharsets.UTF_8);
        }
    }
}
