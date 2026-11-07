// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import info.martinmarinov.drivers.usb.tbs5530.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** New namespace only. A host session grant issues distinct consumed per-program permits. */
final class Tbs5530AutonomousSession {
    interface Boundary { void require() throws IOException; }
    static final String PREFIX = "autonomous-v1";
    final String id = UUID.randomUUID().toString();
    final File directory;
    final Tbs5530AutonomousPolicy policy;
    final Tbs5530AutonomousPlans golden;
    final Tbs5530TsSmoke.ReadApi readApi;
    private final JSONObject challenge = new JSONObject();
    private final File root;
    private final Context contextForLedger;
    private String ledger = "", grantText, owner;
    private long generation, expires;
    private boolean accepted;
    private String recoveryEpoch = "";
    private boolean recoveryConfirmed;
    volatile boolean preNativeRefusal;
    volatile boolean cleanupConfirmed;
    volatile Tbs5530TsSmoke.Result tsResult;
    volatile Tbs5530OneShotBootstrap.Snapshot bootstrapSnapshot;
    volatile Tbs5530OperationalProfile operationalObservation;
    volatile int initialNegotiatedUsbSpeed = -1;
    volatile boolean initialRawObserved;
    volatile Tbs5530AutonomousCapture capture;
    volatile String stopReason = "NONE", failureStage = "NONE", errorClass = "NONE", causeClass = "NONE";
    private volatile String lastStage = "WAIT_DEVICE";
    private Boundary boundary = () -> { };

    Tbs5530AutonomousSession(Context context, String requestedApi) throws IOException {
        this(context, requestedApi, Build.MODEL, Build.VERSION.SDK_INT,
                Settings.Global.getInt(context.getContentResolver(), "boot_count", -1));
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot) throws IOException {
        readApi = requestedApi == null ? Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO : Tbs5530TsSmoke.ReadApi.valueOf(requestedApi);
        contextForLedger = context.getApplicationContext();
        root = context.getFilesDir(); directory = new File(root, PREFIX + "-" + id);
        if (!directory.mkdir()) throw new IOException("Cannot create fresh session namespace");
        golden = new Tbs5530AutonomousPlans();
        write("ledger.jsonl", "");
        policy = new Tbs5530AutonomousPolicy(SystemClock::elapsedRealtime, new Tbs5530AutonomousPolicy.Ledger() {
            public void check() throws IOException {
                if (!ledger.equals(read(new File(directory, "ledger.jsonl"), 1024 * 1024))) throw new IOException("Session ledger corrupted");
            }
            public void append(String event, String attempt, String plan) throws IOException {
                check();
                try {
                    JSONObject value = new JSONObject().put("event", event).put("attempt", attempt).put("plan", plan)
                            .put("elapsed_ms", SystemClock.elapsedRealtime());
                    String next = ledger + value + "\n"; write("ledger.jsonl", next); ledger = next;
                } catch (org.json.JSONException error) { throw new IOException(error); }
            }
        });
        try {
            if (boot < 0 || !"GM1900".equals(model) || api != 30) throw new IOException("Unverified Android model/API/boot");
            challenge.put("policy", Tbs5530AutonomousPolicy.VERSION).put("session", id).put("model", model)
                    .put("api", api).put("boot_count", boot).put("vid", 0x734c).put("pid", 0x5530)
                    .put("firmware_sha256", Tbs5530FirmwareValidation.SHA256).put("firmware_protocol", "TBS_SOURCE_DUAL_RESET")
                    .put("created_elapsed_ms", policy.created()).put("max_session_ms", 300000).put("heartbeat_ms", 2000)
                    .put("golden_sha256", Tbs5530AutonomousPlans.hash(Tbs5530AutonomousPlans.dryRun().toString()))
                    .put("live_ready", true).put("retry", 0).put("max_upload_per_attachment", 1)
                    .put("ts_variant", readApi.name());
            Tbs5530DurableFile.write(new File(root, PREFIX + "-challenge.json"), challenge.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
    static String read(File file, int limit) throws IOException {
        try (FileInputStream input = new android.util.AtomicFile(file).openRead()) {
            byte[] data = new byte[limit + 1]; int at = 0, n;
            while (at < data.length && (n = input.read(data, at, data.length - at)) > 0) at += n;
            if (at > limit) throw new IOException("Oversized session metadata");
            return new String(data, 0, at, StandardCharsets.UTF_8);
        }
    }
    void write(String name, String text) throws IOException {
        if (!name.matches("[A-Za-z0-9_.-]+")) throw new IOException("Invalid private filename");
        Tbs5530DurableFile.write(new File(directory, name), text.getBytes(StandardCharsets.UTF_8));
    }
    boolean grantAvailable() { return new File(directory, "grant.json").isFile(); }
    void acceptGrant() throws IOException {
        if (accepted) throw new IOException("Session grant already accepted");
        try {
            String text = read(new File(directory, "grant.json"), 16384); JSONObject grant = new JSONObject(text);
            for (String key : new String[]{"policy", "session", "model", "api", "boot_count", "vid", "pid", "firmware_sha256",
                    "firmware_protocol", "created_elapsed_ms", "golden_sha256", "ts_variant"}) {
                if (!challenge.get(key).toString().equals(grant.get(key).toString())) throw new IOException("Grant scope mismatch: " + key);
            }
            if (!grant.getBoolean("bounded_user_authorization") || !grant.getBoolean("rf_in_confirmed")
                    || grant.getInt("max_upload_per_attachment") != 1 || grant.getInt("retry") != 0)
                throw new IOException("Missing bounded session authorization");
            recoveryEpoch = grant.optString("physical_recovery_epoch", "");
            recoveryConfirmed = grant.optBoolean("physical_recovery_confirmed", false);
            Tbs5530AutonomousAttachmentLedger.requireRecoveryEpoch(recoveryEpoch, recoveryConfirmed);
            expires = grant.getLong("expires_elapsed_ms");
            Tbs5530AutonomousPolicy.Profile profile = Tbs5530AutonomousPolicy.Profile.valueOf(grant.getString("profile"));
            if (readApi == Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096 && profile != Tbs5530AutonomousPolicy.Profile.EXPERIMENTAL_DIAGNOSTIC)
                throw new IOException("Queued receive requires experimental session profile");
            policy.grant(profile, expires, heartbeat());
            grantText = text; accepted = true;
        } catch (org.json.JSONException | IllegalArgumentException error) { throw new IOException("Malformed session grant", error); }
    }
    private long heartbeat() throws IOException {
        try {
            JSONObject beat = new JSONObject(read(new File(directory, "heartbeat.json"), 2048));
            if (!id.equals(beat.getString("session")) || beat.optBoolean("stop", true)) throw new IOException("Host stopped session");
            return beat.getLong("elapsed_ms");
        } catch (org.json.JSONException error) { throw new IOException("Malformed heartbeat", error); }
    }
    void require() throws IOException {
        if (policy.state() != Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN) lastStage = policy.state().name();
        if (!accepted || !grantText.equals(read(new File(directory, "grant.json"), 16384))) throw new IOException("Grant changed or unavailable");
        policy.heartbeat(heartbeat()); policy.requireSession();
    }
    void owner(String identity, long token, Boundary guard) throws IOException {
        require(); policy.bindOwner(identity, token); owner = identity; generation = token; boundary = guard;
    }
    Tbs5530OperationalApproval approval(Tbs5530Frontend.Plan plan, Tbs5530TsSmoke.Limits limits) throws IOException {
        require(); boundary.require();
        if (plan != null) golden.require(plan); else {
            Tbs5530AutonomousPlans.requireTs(limits);
            if (limits.readApi != readApi || limits.readApi == Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096 && !policy.experimental())
                throw new IOException("Foreign or non-experimental receive variant");
        }
        if (plan != null && plan.generation != generation) throw new IOException("Foreign plan generation");
        final long maximum = plan == null ? limits.maximumMillis() : plan.maximumMillis;
        Tbs5530OperationalApproval.Storage storage = new Tbs5530OperationalApproval.Storage() {
            private JSONObject program;
            private final String attempt = UUID.randomUUID().toString();
            public String read() throws IOException {
                require(); boundary.require(); policy.requireOwner(owner, generation, true);
                if (program == null) throw new IOException("No exact program challenge");
                try {
                    JSONObject permit = new JSONObject(program.toString());
                    return permit.put("approval_id", attempt).put("explicit_control_approval", true).put("physical_current", true)
                            .put("explicit_experimental_auto_plp", true).put("explicit_endpoint_preparation", false)
                            .put("explicit_queued_reception", policy.experimental() && readApi == Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096)
                            .put("approved_maximum_ms", maximum).put("expires_elapsed_ms", expires)
                            .put("heartbeat_elapsed_ms", heartbeat()).toString();
                } catch (org.json.JSONException error) { throw new IOException(error); }
            }
            public void write(String name, String text) throws IOException {
                if ("phase31-challenge.json".equals(name)) {
                    try { program = new JSONObject(text); } catch (org.json.JSONException error) { throw new IOException(error); }
                }
                Tbs5530AutonomousSession.this.write(name.replace("phase31", "program"), text);
            }
            public boolean consumed(String value) { return policy.consumed(value); }
            public void consume(String value) throws IOException {
                if (!attempt.equals(value)) throw new IOException("Foreign attempt ID");
                try { policy.consume(value, program.getString("plan_sha256"), maximum); }
                catch (org.json.JSONException error) { throw new IOException(error); }
            }
        };
        Tbs5530OperationalApproval value = new Tbs5530OperationalApproval(storage, SystemClock::elapsedRealtime,
                plan, limits, generation, owner, policy.created());
        value.rfIn(true); return value;
    }
    void consumeFirmwareAttachment(String attachment) throws IOException {
        synchronized (Tbs5530AutonomousSession.class) {
            require();
            File history = new File(root, PREFIX + "-attachments.txt");
            android.content.SharedPreferences marker = contextForLedger.getSharedPreferences(PREFIX + "-history", Context.MODE_PRIVATE);
            if (!history.exists()) {
                if (marker.getBoolean("initialized", false)) throw new IOException("Attachment history missing; no rearm");
                Tbs5530DurableFile.write(history, new byte[0]);
            }
            if (!marker.edit().putBoolean("initialized", true).commit()) throw new IOException("History initialization persistence failed");
            final String key;
            try { key = Tbs5530AutonomousPlans.hash(challenge.get("boot_count").toString() + ":" + attachment); }
            catch (org.json.JSONException error) { throw new IOException(error); }
            try {
                new Tbs5530AutonomousAttachmentLedger(new Tbs5530AutonomousAttachmentLedger.Storage() {
                    public String read() throws IOException { return Tbs5530AutonomousSession.read(history, 1024 * 1024); }
                    public void write(String value) throws IOException { Tbs5530DurableFile.write(history, value.getBytes(StandardCharsets.UTF_8)); }
                }).consume(key, recoveryEpoch, recoveryConfirmed);
            } catch (Tbs5530AutonomousAttachmentLedger.AlreadyConsumed error) {
                preNativeRefusal = true; failure(error); throw error;
            }
        }
    }
    Tbs5530OneShotBootstrap.Guard firmwareGuard(Boundary physical) {
        return new Tbs5530OneShotBootstrap.Guard() {
            // General transition/observation checks must not require the released upload attachment.
            public void requireCurrent() throws IOException { require(); }
            public void requireStartBudget() throws IOException { require(); physical.require(); }
            public void requireWrite(int ordinal) throws IOException { require(); physical.require(); }
            public void requireNative(int ordinal) throws IOException { require(); physical.require(); policy.uploadNative(ordinal); }
            public void requireDispatch(int ordinal) throws IOException { require(); physical.require(); policy.uploadDispatch(ordinal); }
            public String runEpoch() { return id; }
        };
    }
    synchronized void failure(Throwable error) {
        if (!"NONE".equals(stopReason)) return;
        failureStage = policy.state() == Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN ? lastStage : policy.state().name();
        errorClass = safeClass(error);
        causeClass = safeClass(error == null ? null : error.getCause());
        String message = error == null ? "" : error.getMessage();
        if (error instanceof Tbs5530AutonomousAttachmentLedger.AlreadyConsumed) stopReason = "ATTACHMENT_ALREADY_CONSUMED";
        else if ("Control approval expired or host heartbeat stale".equals(message)) stopReason = "PROGRAM_HEARTBEAT_OR_BUDGET";
        else if ("Control approval scope/attachment/clock mismatch".equals(message)) stopReason = "PROGRAM_SCOPE_MISMATCH";
        else if ("Session stopped, expired, background or host offline".equals(message)) stopReason = "SESSION_LIFETIME_OR_HOST";
        else if ("Host stopped session".equals(message)) stopReason = "HOST_STOP_REQUESTED";
        else if ("Session ledger corrupted".equals(message)) stopReason = "SESSION_LEDGER_CORRUPT";
        else if ("Invalid heartbeat".equals(message)) stopReason = "HOST_HEARTBEAT_INVALID";
        else if ("Grant changed or unavailable".equals(message)) stopReason = "SESSION_GRANT_CHANGED";
        else if ("Permission or owner generation changed".equals(message)) stopReason = "OWNER_OR_PERMISSION_CHANGED";
        else if ("Plan is not allowlisted".equals(message)) stopReason = "PLAN_NOT_ALLOWLISTED";
        else if ("No exact program challenge".equals(message)) stopReason = "PROGRAM_CHALLENGE_MISSING";
        else if ("Invalid control permit".equals(message)) stopReason = "PROGRAM_PERMIT_INVALID";
        else if ("Control program already consumed".equals(message)) stopReason = "PROGRAM_ATTEMPT_CONSUMED";
        else if ("Foreground, OS permission or attachment changed".equals(message)) stopReason = "PHYSICAL_GUARD_CHANGED";
        else if ("No confirmed lock".equals(message)) stopReason = "RF_LOCK_NOT_CONFIRMED";
        else if ("Firmware uncertain; no retry".equals(message)) stopReason = "FIRMWARE_UNCERTAIN";
        else if (message != null && message.startsWith("Raw capture")) stopReason = "RAW_CAPTURE_IO_OR_BOUNDS";
        else stopReason = "GUARD_OR_IO_FAILURE";
    }
    private static String safeBootstrapCode(String code) {
        if ("NONE".equals(code) || "IOException".equals(code) || "RuntimeException".equals(code)) return code;
        try { return Tbs5530BootstrapStop.Kind.valueOf(code).name(); }
        catch (IllegalArgumentException | NullPointerException error) { return "REDACTED_UNKNOWN_CAUSE"; }
    }
    private static JSONObject bootstrapMetrics(Tbs5530OneShotBootstrap.Snapshot snapshot) throws org.json.JSONException {
        org.json.JSONArray chain = new org.json.JSONArray();
        for (String cause : snapshot.failureChain) chain.put(safeBootstrapCode(cause));
        Tbs5530BootstrapTelemetry.Snapshot b = snapshot.boundaries;
        return new JSONObject().put("state", snapshot.state.name()).put("attempted", snapshot.attempted)
                .put("completed", snapshot.completed).put("bytes", snapshot.bytes).put("duration_ms", snapshot.durationMillis)
                .put("stop_cause", safeBootstrapCode(snapshot.failure)).put("cleanup_failure", safeBootstrapCode(snapshot.cleanupFailure))
                .put("failure_chain", chain).put("native_started", b.nativeCallStarted).put("native_completed", b.nativeCallCompleted)
                .put("full_length_completed", b.fullLengthCompleted).put("guard_requested", b.guardRequested)
                .put("guard_accepted", b.guardAccepted).put("image_bytes", b.imageBytesFullLength).put("payload_bytes", b.payloadBytesFullLength);
    }
    private static JSONObject tsMetrics(Tbs5530TsSmoke.Result ts) throws org.json.JSONException {
        Tbs5530TsParser.Snapshot p = ts.packets;
        return new JSONObject().put("packets", p.packets).put("transport_error_packets", p.transportErrorPackets)
                .put("continuity_errors", p.continuityErrors).put("malformed_packets", p.malformedPackets)
                .put("sync_losses", p.syncLosses).put("discarded_bytes", p.discardedBytes).put("buffered_bytes", p.bufferedBytes)
                .put("valid_pat_sections", p.validPatSections).put("valid_pmt_sections", p.validPmtSections)
                .put("has_valid_program_structure", p.hasValidProgramStructure()).put("elapsed_ns", ts.elapsedNanos)
                .put("read_count", ts.reads).put("last_read_result", ts.lastReadResult == Integer.MIN_VALUE ? JSONObject.NULL : ts.lastReadResult)
                .put("last_requested_bytes", ts.lastReadRequestedBytes).put("last_timeout_ms", ts.lastReadTimeoutMillis)
                .put("last_managed_wall_ns", ts.lastReadWallNanos).put("last_errno", ts.lastReadErrno < 0 ? JSONObject.NULL : ts.lastReadErrno);
    }
    private static String safeClass(Throwable error) {
        if (error == null) return "NONE";
        if (error instanceof Tbs5530BootstrapStop) return "Tbs5530BootstrapStop:" + ((Tbs5530BootstrapStop) error).kind.name();
        if (error instanceof Tbs5530ControlIo.TransferException)
            return "TransferException:" + ((Tbs5530ControlIo.TransferException) error).getKind().name();
        if (error instanceof java.net.SocketTimeoutException) return "SocketTimeoutException";
        if (error instanceof java.io.InterruptedIOException) return "InterruptedIOException";
        if (error instanceof IOException) return "IOException";
        if (error instanceof IllegalArgumentException) return "IllegalArgumentException";
        if (error instanceof IllegalStateException) return "IllegalStateException";
        if (error instanceof RuntimeException) return "RuntimeException";
        return "Throwable";
    }
    void report(String result, long bytes, long packets, int errno) throws IOException {
        try {
            JSONObject json = new JSONObject().put("policy", Tbs5530AutonomousPolicy.VERSION).put("state", policy.state().name())
                    .put("result", result).put("ts_bytes", bytes).put("ts_packets", packets).put("last_errno", errno < 0 ? JSONObject.NULL : errno)
                    .put("cleanup", cleanupConfirmed ? "DRAIN_CONFIRMED" : "DRAIN_NOT_CERTIFIED")
                    .put("base_git_sha", BuildConfig.SOURCE_GIT_SHA)
                    .put("ts_variant", readApi.name()).put("retry", 0).put("redacted", true)
                    .put("stop_reason", stopReason).put("failure_stage", failureStage)
                    .put("physical_recovery_epoch_bound", recoveryConfirmed && !recoveryEpoch.isEmpty())
                    .put("pre_native_refusal", preNativeRefusal)
                    .put("native_return_status", preNativeRefusal ? "NOT_CALLED_PRE_NATIVE_REFUSAL" : "SEE_TYPED_RESULT_AND_BOUNDARIES")
                    .put("error_class", errorClass).put("cause_class", causeClass)
                    .put("raw_received_bytes", capture == null ? 0 : capture.bytes())
                    .put("raw_capture", capture == null ? "NOT_STARTED" : capture.durable() ? "SYNCED_CLOSED" : "NOT_CERTIFIED");
            json.put("initial_negotiated_usb_speed", initialNegotiatedUsbSpeed < 0 ? JSONObject.NULL : initialNegotiatedUsbSpeed);
            json.put("initial_negotiated_usb_speed_source", initialNegotiatedUsbSpeed < 0 ? "UNAVAILABLE" : "USBDEVFS_GET_SPEED_LINUX_ENUM");
            Tbs5530OperationalProfile usb = operationalObservation;
            json.put("operational_usb_observation", usb == null ? JSONObject.NULL : new JSONObject()
                    .put("profile", usb.kind.name()).put("stream_packet_size", usb.streamPacketSize)
                    .put("control_packet_size", usb.controlPacketSize)
                    .put("negotiated_usb_speed", usb.negotiatedUsbSpeed < 0 ? JSONObject.NULL : usb.negotiatedUsbSpeed)
                    .put("negotiated_usb_speed_source", usb.negotiatedUsbSpeed < 0 ? "UNAVAILABLE" : "USBDEVFS_GET_SPEED_LINUX_ENUM"));
            Tbs5530OneShotBootstrap.Snapshot firmware = bootstrapSnapshot;
            json.put("firmware", firmware == null ? JSONObject.NULL : bootstrapMetrics(firmware));
            Tbs5530TsSmoke.Result ts = tsResult;
            boolean synchronous = readApi == Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO;
            boolean nativeKnown = ts != null && Tbs5530AutonomousPolicy.nativeReturnKnown(synchronous,
                    ts.reads, ts.lastReadResult, ts.lastReadErrno);
            boolean completedError = ts != null && ts.status == Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE
                    && Tbs5530AutonomousPolicy.completedReceiveError(synchronous, ts.reads, ts.lastReadResult, ts.lastReadErrno);
            json.put("native_return_known", nativeKnown);
            json.put("receive_error", !completedError ? "NOT_CLASSIFIED_AS_COMPLETED_ERROR"
                    : policy.state() == Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN ? "COMPLETED_USB_ERROR_WITH_UNCERTAIN_SESSION_OR_CLEANUP"
                    : cleanupConfirmed ? "KNOWN_COMPLETED_USB_ERROR" : "COMPLETED_USB_ERROR_AWAITING_DRAIN");
            json.put("usb_data_received", ts == null ? bytes > 0 : ts.usbDataReceived());
            json.put("structure_confirmed", ts != null && ts.structureConfirmed());
            json.put("quality_observation", ts == null ? "NOT_ASSESSED" : ts.qualityObservation());
            json.put("ts_metrics", ts == null ? JSONObject.NULL : tsMetrics(ts));
            write("report.json", json.toString());
            Tbs5530DurableFile.write(new File(root, PREFIX + "-report.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
}
