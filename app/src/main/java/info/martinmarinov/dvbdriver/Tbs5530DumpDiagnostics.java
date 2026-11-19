// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource;

/** Post-drain sidecar only: no file or JSON operations on the USB producer. */
final class Tbs5530DumpDiagnostics {
    static void write(File ts, long start, long end, long frequency, long bandwidth,
                      NativeUsbfsByteSource.CompletionSnapshot snapshot, Tbs5530RecordingTimeline timeline) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("schema", "TBS5530-DUMP-DIAGNOSTICS/v1").put("clock", "CLOCK_MONOTONIC")
                    .put("ts_file", ts.getName()).put("ts_size", ts.length())
                    .put("recording_start_ns", start).put("recording_end_ns", end)
                    .put("frequency_hz", frequency).put("bandwidth_hz", bandwidth).put("drive", 0x55)
                    .put("source", "RELEASE_OR_DEBUG_PRODUCTION_NATIVE");
            JSONArray rows = new JSONArray();
            boolean complete = snapshot != null && snapshot.isComplete()
                    && start >= snapshot.getWindowStartMonotonicNanos()
                    && end <= snapshot.getWindowEndMonotonicNanos();
            json.put("completions_complete", complete);
            if (snapshot != null) {
                json.put("prefix_locked", snapshot.isPrefixLocked())
                        .put("discarded_prefix_bytes", snapshot.getDiscardedPrefixBytes())
                        .put("native_window_start_ns", snapshot.getWindowStartMonotonicNanos())
                        .put("native_window_end_ns", snapshot.getWindowEndMonotonicNanos())
                        .put("native_finished", snapshot.isFinished()).put("overflow", snapshot.isOverflow())
                        .put("clock_invalid", snapshot.isClockInvalid()).put("stop_reason", snapshot.getStopReason());
                for (int i = 0; i < snapshot.getRecordCount(); i++) {
                    long time = snapshot.getMonotonicNanos(i);
                    if (time >= start && time <= end) rows.put(new JSONObject()
                            .put("timestamp_ns", time).put("clock", "CLOCK_MONOTONIC")
                            .put("actual_length", snapshot.getLength(i)).put("status", snapshot.getStatus(i))
                            .put("stage", "data"));
                }
            }
            JSONArray offsets = new JSONArray();
            for (int i = 0; i < timeline.size(); i++) offsets.put(new JSONObject()
                    .put("timestamp_ns", timeline.time(i)).put("byte_offset", timeline.offset(i)));
            json.put("consumer_offsets", offsets).put("consumer_offsets_complete", timeline.complete())
                    .put("timestamp_kind", "userspace_after_reap_not_kernel_completion")
                    .put("completions", rows)
                    .put("alignment", "Recording times are consumer observations; queued bytes can precede the recording window.")
                    .put("metrics", JSONObject.NULL);
        } catch (JSONException e) { throw new IOException("Cannot encode TBS5530 recording diagnostics", e); }
        AtomicFile target = new AtomicFile(new File(ts.getPath() + ".usb.json"));
        FileOutputStream output = null;
        try {
            output = target.startWrite();
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            target.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            if (output != null) target.failWrite(output);
            throw e;
        }
    }
}
