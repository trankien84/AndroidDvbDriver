// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Tbs5530SignalAudit {
    static void write(File directory, String runId, List<Tbs5530SignalMonitor.Sample> samples, String failure) throws IOException {
        File file = new File(directory, "phase31-signal-monitor-" + runId + ".jsonl");
        if (!file.createNewFile()) throw new IOException("Signal audit already exists");
        try (FileOutputStream output = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            for (Tbs5530SignalMonitor.Sample sample : samples) {
                Tbs5530SignalMetrics metrics = sample.metrics;
                JSONArray reads = new JSONArray(), errors = new JSONArray();
                for (Tbs5530SignalMonitor.RegisterRead read : sample.rawReads) {
                    StringBuilder hex = new StringBuilder();
                    for (byte value : read.getBytes()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                    reads.put(new JSONObject().put("bank", read.bank).put("register", read.register)
                            .put("hex", hex.toString()).put("start_ns", read.startNanos).put("end_ns", read.endNanos));
                }
                for (Tbs5530SignalMonitor.Error error : sample.errors) errors.put(new JSONObject()
                        .put("operation", error.operation).put("bank", error.bank).put("register", error.register)
                        .put("timestamp_ns", error.timestampNanos).put("exception", error.exceptionType).put("code", error.message));
                JSONObject row = new JSONObject().put("schema", "TBS5530-B4-MONITOR/v1").put("clock", "CLOCK_MONOTONIC")
                        .put("timestamp_ns", sample.startNanos).put("start_ns", sample.startNanos).put("end_ns", sample.endNanos)
                        .put("reads", reads).put("errors", errors)
                        .put("lock", sample.cleanupSucceeded ? metrics.lock.name() : "UNKNOWN")
                        .put("snr_raw", nullable(metrics.snrRaw))
                        .put("snr_db", sample.cleanupSucceeded ? nullable(metrics.officialCnrDb) : JSONObject.NULL)
                        .put("snr_db_curve", sample.cleanupSucceeded ? nullable(metrics.snrDbCurve) : JSONObject.NULL).put("ber", JSONObject.NULL)
                        .put("fer", JSONObject.NULL).put("per", JSONObject.NULL).put("if_agc", JSONObject.NULL)
                        .put("rssi_dbm", JSONObject.NULL).put("unavailable_reason", metrics.unavailableReason)
                        .put("cleanup_succeeded", sample.cleanupSucceeded).put("lock_loss_ns", sample.confirmedLockLossNanos)
                        .put("stop_for_lock_loss", sample.stopForLockLoss);
                writer.write(row.toString()); writer.write('\n');
            }
            writer.write(new JSONObject().put("summary", true).put("samples", samples.size())
                    .put("clock", "CLOCK_MONOTONIC").put("monitor_failure", nullable(failure))
                    .put("tuner_rssi", false).toString());
            writer.write('\n'); writer.flush(); output.getFD().sync();
        } catch (JSONException error) { throw new IOException("Signal audit serialization failed", error); }
    }
    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
    private Tbs5530SignalAudit() { }
}
