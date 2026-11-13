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
        write(directory, runId, samples, failure, null);
    }
    static void write(File directory, String runId, List<Tbs5530SignalMonitor.Sample> samples,
                      String failure, String phase) throws IOException {
        validatePhase(phase);
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
                JSONObject row = new JSONObject().put("schema", phase == null ? "TBS5530-B4-MONITOR/v1" : "TBS5530-B5-MONITOR/v1")
                        .put("phase", nullable(phase)).put("clock", "CLOCK_MONOTONIC")
                        .put("timestamp_ns", sample.startNanos).put("start_ns", sample.startNanos).put("end_ns", sample.endNanos)
                        .put("reads", reads).put("errors", errors)
                        .put("lock", sample.cleanupSucceeded ? metrics.lock.name() : "UNKNOWN")
                        .put("demod_lock", sample.cleanupSucceeded ? metrics.lock.name() : "UNKNOWN")
                        .put("sync_state", nullable(metrics.syncState)).put("ts_lock", nullable(metrics.tsLock))
                        .put("unlock_detected", nullable(metrics.unlockDetected))
                        .put("snr_raw", nullable(metrics.snrRaw))
                        .put("snr_db", sample.cleanupSucceeded && sample.errors.isEmpty() ? nullable(metrics.snrDbCurve) : JSONObject.NULL)
                        .put("snr_db_curve", sample.cleanupSucceeded && sample.errors.isEmpty() ? nullable(metrics.snrDbCurve) : JSONObject.NULL)
                        .put("snr_db_observed", nullable(metrics.snrDbCurve))
                        .put("snr_formula", "10log10(min(raw,10876)/(12600-min(raw,10876)))+32")
                        .put("ber", sample.cleanupSucceeded ? nullable(metrics.ber) : JSONObject.NULL).put("ber_kind", "PRE_BCH")
                        .put("modulation", nullable(metrics.modulation)).put("code_rate", nullable(metrics.codeRate))
                        .put("fec", nullable(metrics.fec)).put("plp_id", nullable(metrics.plpId))
                        .put("time_interleaving", nullable(metrics.timeInterleaving))
                        .put("fft", nullable(metrics.fft)).put("guard_interval", nullable(metrics.guardInterval))
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
    static void writeRssi(File directory, String runId, String phase,
                          List<Tbs5530SignalRssi.Result> samples) throws IOException {
        validatePhase(phase);
        if (!"OFF1".equals(phase) && !"OFF2".equals(phase))
            throw new IllegalArgumentException("RSSI_REQUIRES_OFF_PHASE");
        File file = new File(directory, "phase31-signal-rssi-" + runId + "-" + phase + ".jsonl");
        if (!file.createNewFile()) throw new IOException("RSSI audit already exists");
        try (FileOutputStream output = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            for (Tbs5530SignalRssi.Result sample : samples) {
                JSONObject row = new JSONObject().put("schema", "TBS5530-B5-RSSI/v1")
                        .put("phase", phase).put("clock", "CLOCK_MONOTONIC")
                        .put("timestamp_ns", sample.startNanos).put("start_ns", sample.startNanos).put("end_ns", sample.endNanos)
                        .put("if_agc", sample.ifAgc).put("rf_agc", sample.rfAgc).put("reg69", sample.reg69)
                        .put("rssi_dbm", sample.rssiDbm).put("cleanup_succeeded", sample.cleanupSucceeded)
                        .put("sequence", "ASCOT3_WINDOWS_88_00_87_C0")
                        .put("windows_sequence_matches", true).put("windows_cleanup_88", "00")
                        .put("cleanup_87", "c0");
                writer.write(row.toString()); writer.write('\n');
            }
            writer.flush(); output.getFD().sync();
        } catch (JSONException error) { throw new IOException("RSSI audit serialization failed", error); }
    }
    private static void validatePhase(String phase) {
        if (phase != null && !phase.equals("OFF1") && !phase.equals("ON1")
                && !phase.equals("OFF2") && !phase.equals("ON2"))
            throw new IllegalArgumentException("Invalid B5 phase");
    }
    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
    private Tbs5530SignalAudit() { }
}
