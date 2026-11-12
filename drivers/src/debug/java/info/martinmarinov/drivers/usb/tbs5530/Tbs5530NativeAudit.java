// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Tbs5530NativeAudit implements Tbs5530BootstrapTelemetry.Audit {
    interface Measurement {
        void recorded(Tbs5530BootstrapTelemetry.Snapshot snapshot, long serializationNanos, Tbs5530DurableFile.Timing timing);
    }
    private final File directory;
    private final Measurement measurement;
    Tbs5530NativeAudit(File directory) { this(directory, null); }
    Tbs5530NativeAudit(File directory, Measurement measurement) { this.directory = directory; this.measurement = measurement; }
    static byte[] encode(Tbs5530BootstrapTelemetry.Snapshot s) throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("run_epoch", s.runEpoch); json.put("monotonic_timebase", s.timebase);
            json.put("evidence_layer", "ANDROID_APP_OBSERVED");
            json.put("boundary", "UsbDeviceConnection.controlTransfer");
            json.put("legacy_native_counter_names_are_syscall_proof", false);
            json.put("planned_ordinal", s.plannedOrdinal);
            json.put("guard_requested", s.guardRequested); json.put("guard_accepted", s.guardAccepted);
            json.put("native_call_started", s.nativeCallStarted); json.put("native_call_completed", s.nativeCallCompleted);
            json.put("full_length_completed", s.fullLengthCompleted); json.put("image_bytes_full_length", s.imageBytesFullLength);
            json.put("payload_bytes_full_length", s.payloadBytesFullLength);
            json.put("events", new JSONArray(s.events));
            return json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException error) { throw new IOException("Native audit serialization failed", error); }
    }
    public void checkpoint(Tbs5530BootstrapTelemetry.Snapshot s) throws IOException {
        long start = android.os.SystemClock.elapsedRealtimeNanos();
        byte[] bytes = encode(s);
        long serialized = android.os.SystemClock.elapsedRealtimeNanos() - start;
        Tbs5530DurableFile.Timing timing = Tbs5530DurableFile.write(new File(directory,
                "phase29-native-audit-" + s.runEpoch + ".json"), bytes);
        if (measurement != null) measurement.recorded(s, serialized, timing);
    }
}
