// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Test APK only: production owner/Port/audit, synthetic bytes and a manager with no USB service. */
public final class FullPathRig {
    private FullPathRig() { }
    private static final class NoUsbContext extends ContextWrapper {
        NoUsbContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public Object getSystemService(String name) {
            if (Context.USB_SERVICE.equals(name)) throw new AssertionError("REAL USB SERVICE FORBIDDEN");
            return super.getSystemService(name);
        }
    }
    private static void append(ByteArrayOutputStream out, int... bytes) { for (int b : bytes) out.write(b); }
    private static byte[] descriptors(boolean operational) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        append(out,18,1,0,2,255,255,255,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1);
        if (operational) {
            append(out,9,2,25,0,1,1,0,128,50,9,4,0,0,1,255,255,255,0,7,5,0x82,2,0,2,0);
        } else {
            append(out,9,2,171,0,1,1,0,128,50);
            for (int alt = 0; alt < 4; alt++) {
                append(out,9,4,0,alt,alt == 0 ? 0 : 6,255,255,255,0);
                if (alt == 0) continue;
                for (int address : new int[]{1,0x81,2,4,0x86,0x88}) {
                    int type = alt == 1 || address == 4 || address == 0x88 ? 2 : alt == 3 && (address == 2 || address == 0x86) ? 1 : 3;
                    int packet = alt != 1 && (address == 1 || address == 0x81) ? 64 : 512;
                    append(out,7,5,address,type,packet & 255,packet >> 8,type == 2 ? 0 : 1);
                }
            }
        }
        return out.toByteArray();
    }
    private interface Work { void run() throws Exception; }
    private static void onWorker(Tbs5530AndroidRuntime runtime, Work work, int seconds) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        runtime.execute(() -> { try { work.run(); } catch (Throwable e) { error.set(e); } finally { done.countDown(); } });
        assertTrue("Worker deadline", done.await(seconds, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError("Worker failed", error.get());
    }
    public static JSONObject run(Context base, File directory, Tbs5530OneShotBootstrap.Guard guard,
                                 Tbs5530OneShotBootstrap.AttemptStore consume, boolean baselineAudit) throws Exception {
        assertEquals("info.martinmarinov.dvbdriver.phase29offline", base.getPackageName());
        Context context = new NoUsbContext(base);
        byte[] synthetic = new byte[8192];
        Tbs5530FirmwarePlan source = Tbs5530FirmwarePlan.create(synthetic, new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(synthetic), Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,8192)),
                Collections.emptyList(),0,8192));
        Tbs5530Fx2ProtocolPlan plan = Tbs5530Fx2ProtocolPlan.describe(source, "TBS_SOURCE_DUAL_RESET");
        Tbs5530UsbBinding.Device device = new Tbs5530UsbBinding.Device("fake-only", 1, 0x734c, 0x5530);
        int[] counters = new int[4];
        Tbs5530AndroidUsbFacade.Manager manager = new Tbs5530AndroidUsbFacade.Manager() {
            public List<Tbs5530UsbBinding.Device> devices() { return Collections.singletonList(device); }
            public boolean hasPermission(Tbs5530UsbBinding.Device d) { assertEquals(device, d); return true; }
            public void requestPermission(Tbs5530UsbBinding.Device d, PendingIntent p) { throw new AssertionError("No permission requests"); }
            public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device d) {
                assertEquals(device, d); counters[0]++;
                return new Tbs5530AndroidUsbFacade.RawConnection() {
                    public byte[] rawDescriptors() { counters[3]++; return descriptors(counters[2] == 132); }
                    public int interfaceCount() { throw new AssertionError("No interfaces"); }
                    public int interfaceId(int i) { throw new AssertionError(); }
                    public int alternateSetting(int i) { throw new AssertionError(); }
                    public boolean claim(int i, boolean f) { throw new AssertionError("No claim"); }
                    public boolean release(int i) { throw new AssertionError("No release"); }
                    public void close() { counters[1]++; }
                    public int controlTransfer(int type, int request, int value, int index, byte[] bytes, int offset, int length, int timeout) {
                        assertEquals(0x40, type); assertEquals(0xa0, request); assertEquals(0, index); assertEquals(2000, timeout);
                        Tbs5530FirmwarePlan.Step step = plan.getSteps().get(counters[2]++);
                        assertEquals(step.getValue(), value); assertArrayEquals(step.getPayload(), bytes);
                        return length;
                    }
                };
            }
        };
        File auditDirectory = new File(base.getFilesDir(), "benchmark-" + directory.getName());
        assertFalse(auditDirectory.exists()); assertTrue(auditDirectory.mkdirs());
        StringBuilder measurements = new StringBuilder();
        Tbs5530BootstrapTelemetry.Audit audit;
        if (baselineAudit) {
            audit = s -> {
                long a = SystemClock.elapsedRealtimeNanos(); byte[] bytes = Tbs5530NativeAudit.encode(s);
                long b = SystemClock.elapsedRealtimeNanos();
                android.util.AtomicFile file = new android.util.AtomicFile(new File(auditDirectory,"phase29-native-audit-" + s.runEpoch + ".json"));
                FileOutputStream output = file.startWrite(); long c = SystemClock.elapsedRealtimeNanos();
                output.write(bytes); long d = SystemClock.elapsedRealtimeNanos(); file.finishWrite(output);
                addMeasurement(measurements, s, bytes.length, b-a, c-b, d-c, 0, SystemClock.elapsedRealtimeNanos()-d, 0);
            };
        } else {
            audit = new Tbs5530NativeAudit(auditDirectory, (s, serialization, t) -> addMeasurement(measurements, s, t.bytes,
                    serialization, t.startNanos, t.writeNanos, t.syncNanos, t.finishNanos, t.verifyNanos));
        }
        Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry(audit, SystemClock::elapsedRealtimeNanos,
                "ANDROID_ELAPSED_REALTIME_NS", guard.runEpoch());
        Tbs5530OneShotBootstrap.Guard portGuard = new Tbs5530OneShotBootstrap.Guard() {
            public void requireCurrent() throws IOException { guard.requireCurrent(); }
            public void requireWrite(int ordinal) throws IOException { requireCurrent(); guard.requireWrite(ordinal); requireCurrent(); }
            public void requireNative(int ordinal) throws IOException { requireCurrent(); guard.requireNative(ordinal); requireCurrent(); }
            public void requireDispatch(int ordinal) throws IOException { guard.requireDispatch(ordinal); }
        };
        Tbs5530AndroidBootstrap.Port port = new Tbs5530AndroidBootstrap.Port(context, manager, plan, portGuard);
        port.telemetry(telemetry);
        Tbs5530Session session = new Tbs5530Session();
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Tbs5530UsbBinding binding = new Tbs5530UsbBinding(session, port, runtime, runtime, errors::add, 30000,10000);
        Tbs5530OneShotBootstrap runner = new Tbs5530OneShotBootstrap(session,binding,port,device,plan,guard,consume,
                ms -> { try { Thread.sleep(ms); } catch (InterruptedException e) { throw new IOException(e); } },binding::bootstrapRawDescriptors);
        runner.telemetry(telemetry);
        runner.rawAudit((phase, bytes) -> Tbs5530DurableFile.write(new File(auditDirectory,"raw-" + phase + ".bin"),bytes));
        long start = SystemClock.elapsedRealtime();
        try {
            runner.queued(); onWorker(runtime, runner::acquire, 10);
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (binding.isPending() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(5);
            assertFalse("Acquisition pending", binding.isPending());
            onWorker(runtime, () -> { runner.execute(); port.finishWrites(); runner.releaseUpload(); }, 240);
            onWorker(runtime, runner::acquireObservation, 10);
            deadline = SystemClock.elapsedRealtime() + 10000;
            while (binding.isPending() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(5);
            assertFalse(binding.isPending());
            onWorker(runtime, () -> { runner.observe(); runner.releaseUpload(); runner.finished(); }, 10);
            assertTrue(errors.toString(), errors.isEmpty());
            assertEquals(Tbs5530OneShotBootstrap.State.OPERATIONAL_CANDIDATE, runner.snapshot().state);
            assertNotEquals(Tbs5530Session.State.READY, session.getState());
            assertEquals(132, telemetry.snapshot().fullLengthCompleted);
            assertEquals(8192, telemetry.snapshot().imageBytesFullLength); assertEquals(8196, telemetry.snapshot().payloadBytesFullLength);
            assertEquals(2,counters[0]); assertEquals(2,counters[1]); assertEquals(132,counters[2]); assertEquals(2,counters[3]);
            assertArrayEquals(descriptors(false),new android.util.AtomicFile(new File(auditDirectory,"raw-before.bin")).readFully());
            assertArrayEquals(descriptors(true),new android.util.AtomicFile(new File(auditDirectory,"raw-after.bin")).readFully());
            JSONObject result = new JSONObject(); result.put("status","PASS"); result.put("roundtrips",132);
            result.put("usb_calls",0); result.put("native_fake_calls",counters[2]); result.put("full_length",132);
            result.put("raw_descriptor_files",2); result.put("descriptor_reads",counters[3]);
            result.put("duration_ms",SystemClock.elapsedRealtime()-start); result.put("audit_mode", baselineAudit ? "BASELINE_ATOMIC" : "VERIFIED_ATOMIC");
            result.put("boundary_events",new JSONArray(telemetry.snapshot().events));
            return result;
        } finally {
            port.finishWrites(); onWorker(runtime, () -> { binding.close(); session.drainCleanup(); }, 10);
            runtime.close(); assertTrue(runtime.awaitTermination(10,TimeUnit.SECONDS));
            Tbs5530DurableFile.write(new File(directory,"benchmark-audit.jsonl"), measurements.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static void addMeasurement(StringBuilder out, Tbs5530BootstrapTelemetry.Snapshot s, int bytes,
                                       long serialization, long start, long write, long sync, long finish, long verify) {
        try {
            JSONObject json = new JSONObject(); json.put("ordinal",s.plannedOrdinal); json.put("bytes",bytes);
            json.put("phase",s.nativeCallStarted == s.nativeCallCompleted && s.nativeCallStarted == s.plannedOrdinal ? "POST_NATIVE" : "PRE_NATIVE");
            json.put("serialization_ns",serialization); json.put("start_ns",start); json.put("write_ns",write);
            json.put("sync_ns",sync); json.put("finish_ns",finish); json.put("verify_ns",verify); out.append(json).append('\n');
        } catch (org.json.JSONException error) { throw new AssertionError(error); }
    }
}
