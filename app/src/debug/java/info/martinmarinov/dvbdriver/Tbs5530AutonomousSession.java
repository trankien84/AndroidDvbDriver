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
    final Tbs5530FrontendExperiment frontendExperiment;
    Tbs5530ReloadLedger reloadLedger;
    final boolean nativeStream;
    private String historyWarning = "NONE";
    private volatile boolean receiving;
    private long bootstrapIoDeadline = Long.MAX_VALUE, reloadIoDeadline = Long.MAX_VALUE;
    private void requireBootstrapIoBudget() throws IOException {
        if (nativeStream && SystemClock.elapsedRealtime() >= bootstrapIoDeadline) throw new IOException("B3 bootstrap I/O deadline; no retry");
    }
    void requireReload() throws IOException {
        require();
        if (nativeStream && SystemClock.elapsedRealtime() >= reloadIoDeadline) throw new IOException("B3 reload I/O deadline; no retry");
    }
    int streamQueueDepth = 16, streamUrbBytes = 16384;
    boolean b4Capture, tunerRssi;
    int streamDurationMillis = 2000, streamMaxBytes = 4 * 1024 * 1024;
    void configureB4(int durationMillis, int maxBytes, boolean tunerRssi) throws IOException {
        if (tunerRssi) throw new IOException("TUNER_RSSI_UNSUPPORTED_REQUIRES_REVIEWED_EXACT_SEQUENCE");
        if (!nativeStream || accepted || (streamQueueDepth != 16 && streamQueueDepth != 32)
                || streamUrbBytes != 16384 || durationMillis < 1000 || durationMillis > 60000
                || maxBytes != 256 * 1024 * 1024)
            throw new IOException("Invalid B4 capture scope");
        b4Capture = true; this.tunerRssi = tunerRssi;
        streamDurationMillis = durationMillis; streamMaxBytes = maxBytes;
        try {
            challenge.put("b4_enabled", true).put("b4_duration_ms", durationMillis)
                    .put("b4_max_bytes", maxBytes).put("b4_tuner_rssi", tunerRssi)
                    .put("b3_duration_ms", durationMillis).put("b3_max_bytes", maxBytes);
            Tbs5530DurableFile.write(new File(root, PREFIX + "-challenge.json"), challenge.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
    void configureStream(int depth, int bytes) throws IOException {
        if (!nativeStream || accepted || depth < 8 || depth > 32 || bytes < 4096 || bytes > 65536 || bytes % 512 != 0)
            throw new IOException("Invalid B3 receive configuration");
        streamQueueDepth = depth; streamUrbBytes = bytes;
        try {
            challenge.put("b3_queue_depth", depth).put("b3_urb_bytes", bytes).put("b3_max_bytes", 4 * 1024 * 1024).put("b3_duration_ms", 2000);
            Tbs5530DurableFile.write(new File(root, PREFIX + "-challenge.json"), challenge.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
    volatile Tbs5530AndroidBootstrap.CleanProof bootstrapProof;
    volatile Tbs5530Fx2Reload.Snapshot reloadSnapshot;
    boolean reloadEnabled() { return frontendExperiment == Tbs5530FrontendExperiment.B2_RELOAD_AFTER_SET_CONFIG; }
    private final Tbs5530HistoryFence.Store historyStore;
    private final Tbs5530HistoryFence historyFence;
    private Tbs5530ReceiveAudit receiveAudit;
    final boolean controlMirrorEnabled, explicitSetInterfaceAlt0, controlPlaneOnly, sonyTsOutputReadback;
    final String launchToken;
    final java.util.Map<Tbs5530SonyTsOutputReadback.Stage,Tbs5530SonyTsOutputReadback.Snapshot> sonySnapshots = new java.util.EnumMap<>(Tbs5530SonyTsOutputReadback.Stage.class);
    private boolean sonyAuthorizationIssued;
    private int sonyStages;
    volatile boolean terminal;
    volatile String cleanupPath = "NOT_CERTIFIED";
    volatile Tbs5530ControlMirror controlMirror;
    private final JSONObject challenge = new JSONObject();
    private final File root;
    private final Context contextForLedger;
    private final String hostProfile, historyPrefix;
    private String ledger = "", grantText, owner;
    private long generation, expires;
    private boolean accepted;
    private String recoveryEpoch = "";
    private boolean recoveryConfirmed;
    volatile boolean preNativeRefusal;
    volatile boolean cleanupConfirmed;
    volatile Tbs5530TsSmoke.Result tsResult;
    volatile int signalSampleCount;
    volatile String signalMonitorFailure;
    volatile Tbs5530OperationalHandoff.NativeReceiveSnapshot nativeReceiveSnapshot;
    volatile Tbs5530OneShotBootstrap.Snapshot bootstrapSnapshot;
    volatile Tbs5530OperationalProfile operationalObservation;
    volatile int initialNegotiatedUsbSpeed = -1;
    volatile boolean initialRawObserved;
    volatile Tbs5530AutonomousCapture capture;
    volatile String stopReason = "NONE", failureStage = "NONE", errorClass = "NONE", causeClass = "NONE";
    private volatile String lastStage = "WAIT_DEVICE";
    private Boundary boundary = () -> { };

    Tbs5530AutonomousSession(Context context, String requestedApi) throws IOException {
        this(context, requestedApi, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, boolean mirror) throws IOException {
        this(context, requestedApi, mirror, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, boolean mirror, boolean explicitAlt0) throws IOException {
        this(context, requestedApi, mirror, explicitAlt0, null, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, boolean mirror, boolean explicitAlt0,
                            String launchToken, boolean controlOnly) throws IOException {
        this(context,requestedApi,mirror,explicitAlt0,launchToken,controlOnly,false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, boolean mirror, boolean explicitAlt0,
                            String launchToken, boolean controlOnly, boolean sonyReadback) throws IOException {
        this(context, requestedApi, mirror, explicitAlt0, launchToken, controlOnly, sonyReadback, null);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, boolean mirror, boolean explicitAlt0,
                            String launchToken, boolean controlOnly, boolean sonyReadback, String experiment) throws IOException {
        this(context, requestedApi, Build.MODEL, Build.VERSION.SDK_INT,
                Settings.Global.getInt(context.getContentResolver(), "boot_count", -1), mirror, explicitAlt0, launchToken, controlOnly, sonyReadback, experiment);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot) throws IOException {
        this(context, requestedApi, model, api, boot, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror) throws IOException {
        this(context, requestedApi, model, api, boot, mirror, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror, boolean explicitAlt0) throws IOException {
        this(context, requestedApi, model, api, boot, mirror, explicitAlt0, null, false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror,
                            boolean explicitAlt0, String launch, boolean controlOnly) throws IOException {
        this(context,requestedApi,model,api,boot,mirror,explicitAlt0,launch,controlOnly,false);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror,
                            boolean explicitAlt0, String launch, boolean controlOnly, boolean sonyReadback) throws IOException {
        this(context, requestedApi, model, api, boot, mirror, explicitAlt0, launch, controlOnly, sonyReadback, null);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror,
                            boolean explicitAlt0, String launch, boolean controlOnly, boolean sonyReadback, String experiment) throws IOException {
        this(context, requestedApi, model, api, boot, mirror, explicitAlt0, launch, controlOnly, sonyReadback, experiment, null);
    }
    Tbs5530AutonomousSession(Context context, String requestedApi, String model, int api, int boot, boolean mirror,
                            boolean explicitAlt0, String launch, boolean controlOnly, boolean sonyReadback, String experiment,
                            Tbs5530HistoryFence.Store injectedHistory) throws IOException {
        nativeStream = "NATIVE_USBFS_STREAM".equals(requestedApi);
        frontendExperiment = Tbs5530FrontendExperiment.parse(experiment);
        if (nativeStream && frontendExperiment != Tbs5530FrontendExperiment.B2_RELOAD_AFTER_SET_CONFIG)
            throw new IOException("B3 requires the unchanged B2 firmware and frontend pipeline");
        frontendExperiment.requireOptions(requestedApi, mirror, explicitAlt0, controlOnly, sonyReadback);
        sonyTsOutputReadback=sonyReadback;
        if (sonyReadback && (controlOnly || explicitAlt0 || !mirror || !"NATIVE_USBFS_URB_8X4096".equals(requestedApi)))
            throw new IOException("Sony readback requires native4096/mirror and forbids other endpoint experiments");
        if (launch != null && !Tbs5530AutonomousLaunch.valid(launch)) throw new IOException("Invalid launch correlation");
        if (controlOnly && (launch == null || mirror)) throw new IOException("Control-plane validation requires a launch correlation and no USB mirror");
        launchToken = launch; controlPlaneOnly = controlOnly;
        controlMirrorEnabled = mirror; explicitSetInterfaceAlt0 = explicitAlt0;
        readApi = requestedApi == null ? Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO : Tbs5530TsSmoke.ReadApi.valueOf(requestedApi);
        if (explicitAlt0 && readApi != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096)
            throw new IOException("EXPLICIT_SET_INTERFACE_ALT0 cannot be combined with another receive variant");
        hostProfile = Tbs5530VerifiedHost.profile(model, api);
        historyPrefix = Tbs5530VerifiedHost.historyPrefix(hostProfile);
        contextForLedger = context.getApplicationContext();
        if (reloadEnabled() && (!"SM-X926B".equals(model) || api != 36)) throw new IOException("B2 first host is Samsung SM-X926B/API36 only");
        reloadLedger = reloadEnabled() && !nativeStream ? new Tbs5530ReloadLedger(new Tbs5530ReloadStore(contextForLedger)) : null;
        historyStore = nativeStream || frontendExperiment == Tbs5530FrontendExperiment.BASELINE_AUTO ? null
                : injectedHistory != null ? injectedHistory : () -> Tbs5530HistoryRecoveryStore.readSnapshot(contextForLedger, historyPrefix);
        historyFence = historyStore == null ? null : new Tbs5530HistoryFence(historyStore.read());
        root = context.getFilesDir(); directory = new File(root, PREFIX + "-" + id);
        if (!directory.mkdir()) throw new IOException("Cannot create fresh session namespace");
        if (nativeStream) {
            historyWarning = "LEGACY_HISTORY_NOT_AUTHORITY_B3_SESSION_BUDGET_AND_COLD_EPOCH_REQUIRED";
            reloadLedger = new Tbs5530ReloadLedger(new Tbs5530ReloadLedger.Storage() {
                private byte[] journal = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER);
                private byte[] anchor = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER);
                public byte[] read() { return journal.clone(); }
                public byte[] anchor() { return anchor.clone(); }
                public void appendAndSync(byte[] expected, byte[] oldAnchor, byte[] suffix, byte[] anchorSuffix) throws IOException {
                    if (!java.util.Arrays.equals(journal, expected) || !java.util.Arrays.equals(anchor, oldAnchor))
                        throw new IOException("B3 session reload journal changed");
                    byte[] next = java.util.Arrays.copyOf(journal, journal.length + suffix.length);
                    System.arraycopy(suffix, 0, next, journal.length, suffix.length);
                    byte[] nextAnchor = java.util.Arrays.copyOf(anchor, anchor.length + anchorSuffix.length);
                    System.arraycopy(anchorSuffix, 0, nextAnchor, anchor.length, anchorSuffix.length);
                    Tbs5530DurableFile.write(new File(directory, "b3-reload-journal.txt"), next);
                    Tbs5530DurableFile.write(new File(directory, "b3-reload-anchor.txt"), nextAnchor);
                    journal = next; anchor = nextAnchor;
                }
            });
        }
        golden = new Tbs5530AutonomousPlans(frontendExperiment);
        write("ledger.jsonl", "");
        policy = new Tbs5530AutonomousPolicy(SystemClock::elapsedRealtime, new Tbs5530AutonomousPolicy.Ledger() {
            public void check() throws IOException {
                if (nativeStream && policy != null && policy.state() == Tbs5530AutonomousPolicy.State.TS_CAPTURE) return;
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
        }, nativeStream);
        try {
            if (boot < 0) throw new IOException("Unverified Android boot");
            challenge.put("policy", Tbs5530AutonomousPolicy.VERSION).put("session", id).put("host_profile", hostProfile).put("model", model)
                    .put("api", api).put("boot_count", boot).put("vid", 0x734c).put("pid", 0x5530)
                    .put("source_git_sha", BuildConfig.SOURCE_GIT_SHA).put("app_apk_sha256", installedApkHash(context))
                    .put("firmware_sha256", Tbs5530FirmwareValidation.SHA256).put("firmware_protocol", "TBS_SOURCE_DUAL_RESET")
                    .put("created_elapsed_ms", policy.created()).put("max_session_ms", 300000).put("heartbeat_ms", 2000)
                    .put("golden_sha256", golden.fingerprint()).put("frontend_experiment", frontendExperiment.name())
                    .put("live_ready", !controlPlaneOnly).put("retry", 0).put("max_upload_per_attachment", 1)
                    .put("ts_variant", readApi.name()).put("control_mirror", controlMirrorEnabled)
                    .put("explicit_set_interface_alt0", explicitSetInterfaceAlt0);
            if (launchToken != null) challenge.put("launch_token", launchToken).put("control_plane_only", controlPlaneOnly);
            challenge.put("attachment_history_sha256", historyFence == null ? JSONObject.NULL : historyFence.sha256());
            if (reloadEnabled()) challenge.put("fx2_reload_option", Tbs5530Fx2Reload.OPTION)
                    .put("fx2_reload_plan_sha256", Tbs5530Fx2Reload.planSha256())
                    .put("fx2_reload_store_sha256", reloadLedger.sha256()).put("fx2_reload_anchor_sha256", reloadLedger.anchorSha256())
                    .put("bootstrap_proof", "SAME_SESSION_ORIGINAL132_APP_BOUNDARIES_DRAIN_REQUIRED").put("max_reload_per_physical_epoch", 1);
            challenge.put("sony_ts_output_readback",sonyTsOutputReadback);
            if (sonyTsOutputReadback) challenge.put("sony_readback_layout",Tbs5530SonyTsOutputReadback.layout());
            Tbs5530DurableFile.write(new File(root, PREFIX + "-challenge.json"), challenge.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
    static String installedApkHash(Context context) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(context.getApplicationInfo().sourceDir)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder text = new StringBuilder(64);
            for (byte b : digest.digest()) text.append("0123456789abcdef".charAt((b & 255) >>> 4))
                    .append("0123456789abcdef".charAt(b & 15));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
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
            for (String key : new String[]{"policy", "session", "host_profile", "model", "api", "boot_count", "vid", "pid", "firmware_sha256",
                    "firmware_protocol", "created_elapsed_ms", "golden_sha256", "ts_variant", "frontend_experiment", "source_git_sha", "app_apk_sha256"}) {
                if (!challenge.get(key).toString().equals(grant.get(key).toString())) throw new IOException("Grant scope mismatch: " + key);
            }
            if (nativeStream) {
                for (String key : new String[]{"b3_queue_depth", "b3_urb_bytes", "b3_max_bytes", "b3_duration_ms"})
                    if (!challenge.get(key).toString().equals(grant.get(key).toString())) throw new IOException("B3 receive scope mismatch: " + key);
                if (b4Capture) {
                    for (String key : new String[]{"b4_enabled", "b4_duration_ms", "b4_max_bytes", "b4_tuner_rssi"})
                        if (!challenge.get(key).equals(grant.get(key))) throw new IOException("B4 receive scope mismatch: " + key);
                } else if (grant.optBoolean("b4_enabled", false)) throw new IOException("Unexpected B4 grant");
            }
            if (historyFence != null) {
                if (!historyFence.sha256().equals(grant.opt("attachment_history_sha256")))
                    throw new IOException("Host-pinned attachment history scope mismatch");
                synchronized (Tbs5530AutonomousSession.class) { historyFence.require(historyStore.read()); }
            }
            if (reloadEnabled()) {
                for (String key : new String[]{"fx2_reload_option", "fx2_reload_plan_sha256", "fx2_reload_store_sha256", "fx2_reload_anchor_sha256", "bootstrap_proof", "max_reload_per_physical_epoch"})
                    if (!challenge.get(key).toString().equals(grant.get(key).toString())) throw new IOException("Reload grant mismatch: " + key);
                if (!grant.optString("host_script_sha256").matches("[a-f0-9]{64}")) throw new IOException("Host helper hash pin required");
                reloadLedger.require();
            }
            if (grant.optBoolean("control_mirror", false) != controlMirrorEnabled) throw new IOException("Control mirror scope mismatch");
            if (!Boolean.valueOf(explicitSetInterfaceAlt0).equals(grant.opt("explicit_set_interface_alt0")))
                throw new IOException("EXPLICIT_SET_INTERFACE_ALT0 grant scope mismatch");
            if (!Boolean.valueOf(sonyTsOutputReadback).equals(grant.opt("sony_ts_output_readback"))
                    || sonyTsOutputReadback && !Tbs5530SonyTsOutputReadback.layout().equals(grant.opt("sony_readback_layout")))
                throw new IOException("SONY_TS_OUTPUT_READBACK grant scope mismatch");
            if (launchToken != null && (!launchToken.equals(grant.opt("launch_token"))
                    || !Boolean.valueOf(controlPlaneOnly).equals(grant.opt("control_plane_only"))))
                throw new IOException("Launch correlation or control-plane scope mismatch");
            if (!grant.getBoolean("bounded_user_authorization") || (!controlPlaneOnly && !grant.getBoolean("rf_in_confirmed"))
                    || grant.getInt("max_upload_per_attachment") != 1 || grant.getInt("retry") != 0)
                throw new IOException("Missing bounded session authorization");
            recoveryEpoch = grant.optString("physical_recovery_epoch", "");
            recoveryConfirmed = grant.optBoolean("physical_recovery_confirmed", false);
            if (controlPlaneOnly && (!recoveryEpoch.isEmpty() || recoveryConfirmed || grant.optBoolean("rf_in_confirmed", false)))
                throw new IOException("Control-plane validation cannot authorize a physical attachment");
            Tbs5530AutonomousAttachmentLedger.requireRecoveryEpoch(recoveryEpoch, recoveryConfirmed);
            if (reloadEnabled() && (!recoveryConfirmed || recoveryEpoch.isEmpty())) throw new IOException("B2 requires fixed approved physical epoch");
            expires = grant.getLong("expires_elapsed_ms");
            Tbs5530AutonomousPolicy.Profile profile = Tbs5530AutonomousPolicy.Profile.valueOf(grant.getString("profile"));
            if ((frontendExperiment != Tbs5530FrontendExperiment.BASELINE_AUTO || sonyTsOutputReadback
                    || readApi != Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO) && profile != Tbs5530AutonomousPolicy.Profile.EXPERIMENTAL_DIAGNOSTIC)
                throw new IOException("Diagnostic receive requires experimental session profile");
            policy.grant(profile, expires, heartbeat(), explicitSetInterfaceAlt0);
            if (controlMirrorEnabled) controlMirror = new Tbs5530ControlMirror(directory, id, recoveryEpoch,
                    hostProfile + ":" + BuildConfig.SOURCE_GIT_SHA, SystemClock::elapsedRealtimeNanos);
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
        if (!accepted || !(receiving && policy.state() == Tbs5530AutonomousPolicy.State.TS_CAPTURE)
                && !grantText.equals(read(new File(directory, "grant.json"), 16384))) throw new IOException("Grant changed or unavailable");
        policy.heartbeat(heartbeat()); policy.requireSession();
        if (reloadLedger != null) reloadLedger.require();
        if (historyFence != null) synchronized (Tbs5530AutonomousSession.class) { historyFence.require(historyStore.read()); }
    }
    void requireDeviceIo() throws IOException {
        if (controlPlaneOnly) throw new IOException("Control-plane validation forbids device I/O");
    }
    boolean accepted() { return accepted; }
    void controlPlaneReady() throws IOException {
        require();
        if (!controlPlaneOnly) throw new IOException("Not a control-plane validation session");
        try {
            write("control-plane.json", new JSONObject().put("session", id).put("launch_token", launchToken)
                    .put("ready", true).put("device_io_forbidden", true).toString());
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
    void owner(String identity, long token, Boundary guard) throws IOException {
        requireDeviceIo(); require(); policy.bindOwner(identity, token); owner = identity; generation = token; boundary = guard;
    }
    Tbs5530OperationalApproval approval(Tbs5530Frontend.Plan plan, Tbs5530TsSmoke.Limits limits) throws IOException {
        requireDeviceIo(); require(); boundary.require();
        if (plan != null) golden.require(plan); else {
            Tbs5530AutonomousPlans.requireTs(limits);
            if (limits.readApi != readApi || limits.explicitSetInterfaceAlt0 != explicitSetInterfaceAlt0
                    || limits.sonyTsOutputReadback != sonyTsOutputReadback
                    || limits.readApi != Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO && !policy.experimental())
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
                            .put("explicit_native_urb_reception", policy.experimental() && Tbs5530AutonomousPlans.nativeUrb(readApi))
                            .put("explicit_set_interface_alt0", policy.experimental() && explicitSetInterfaceAlt0)
                            .put("explicit_sony_ts_output_readback", policy.experimental() && sonyTsOutputReadback)
                            .put("approved_maximum_ms", maximum).put("expires_elapsed_ms", expires)
                            .put("heartbeat_elapsed_ms", heartbeat()).toString();
                } catch (org.json.JSONException error) { throw new IOException(error); }
            }
            public void write(String name, String text) throws IOException {
                if ("phase31-challenge.json".equals(name)) {
                    try { program = new JSONObject(text); } catch (org.json.JSONException error) { throw new IOException(error); }
                }
                String target = name.replace("phase31", "program");
                if (plan == null && reloadEnabled() && Tbs5530AutonomousPlans.nativeUrb(readApi)
                        && name.equals("phase31-boundaries-" + attempt + ".jsonl")) {
                    if (receiveAudit == null) receiveAudit = new Tbs5530ReceiveAudit(new File(directory, target));
                    receiveAudit.write(text.getBytes(StandardCharsets.UTF_8));
                } else Tbs5530AutonomousSession.this.write(target, text);
            }
            public void requireLiveReceive() throws IOException {
                require(); boundary.require(); policy.requireOwner(owner, generation, true);
            }
            public long receiveHeartbeat() throws IOException { return heartbeat(); }
            public boolean consumed(String value) { return policy.consumed(value); }
            public void consume(String value) throws IOException {
                if (!attempt.equals(value)) throw new IOException("Foreign attempt ID");
                try {
                    policy.consume(value, program.getString("plan_sha256"), maximum);
                    if (nativeStream && plan == null) receiving = true;
                }
                catch (org.json.JSONException error) { throw new IOException(error); }
            }
        };
        Tbs5530OperationalApproval value = new Tbs5530OperationalApproval(storage, SystemClock::elapsedRealtime,
                plan, limits, generation, owner, policy.created());
        value.rfIn(true); return value;
    }
    Tbs5530SonyTsOutputReadback.Authorization sonyAuthorization() throws IOException {
        requireDeviceIo(); require(); boundary.require();
        if (!sonyTsOutputReadback || !policy.experimental() || owner == null || sonyAuthorizationIssued)
            throw new IOException("One explicit Sony readback authorization required");
        sonyAuthorizationIssued=true;
        return new Tbs5530SonyTsOutputReadback.Authorization() {
            private Tbs5530SonyReadbackApproval active;
            public void requireCurrent() throws IOException {
                Tbs5530AutonomousSession.this.require(); boundary.require(); policy.requireOwner(owner,generation,true);
                if (!sonyTsOutputReadback || !policy.experimental()) throw new IOException("Sony readback authorization revoked");
            }
            public void consume(Tbs5530SonyTsOutputReadback.Plan plan) throws IOException {
                requireCurrent();
                Tbs5530SonyTsOutputReadback.Stage expected=sonyStages==0?Tbs5530SonyTsOutputReadback.Stage.AFTER_LOCK:Tbs5530SonyTsOutputReadback.Stage.BEFORE_FIRST_URB;
                if (sonyStages>=2 || plan.stage!=expected || plan.generation!=generation
                        || !Tbs5530SonyTsOutputReadback.transcript(expected,generation).equals(plan.getApprovalTranscript()))
                    throw new IOException("Foreign, reordered or exhausted Sony snapshot plan");
                sonyStages++;
                active=new Tbs5530SonyReadbackApproval(plan,owner,new Tbs5530SonyReadbackApproval.Port() {
                    public void requireCurrent() throws IOException { Tbs5530AutonomousSession.this.require(); boundary.require(); policy.requireOwner(owner,generation,true); }
                    public void consume(String attempt,String hash,long maximum) throws IOException { policy.consume(attempt,hash,maximum); }
                    public void write(String name,String text) throws IOException { Tbs5530AutonomousSession.this.write(name,text); }
                    public long now() { return SystemClock.elapsedRealtime(); }
                });
                active.consume(plan);
            }
            public void audit(String b,int ordinal,int type,int request,int length,int result) throws IOException {
                requireCurrent(); if(active==null)throw new IOException("No consumed Sony snapshot permit");
                active.audit(b,ordinal,type,request,length,result);
            }
        };
    }
    void consumeFirmwareAttachment(String attachment) throws IOException {
        requireDeviceIo();
        synchronized (Tbs5530AutonomousSession.class) {
            require();
            try { policy.requireUploadStartBudget(); }
            catch (IOException error) {
                preNativeRefusal = true; stopReason = "UPLOAD_PREFLIGHT_REFUSED";
                failureStage = policy.state().name(); throw error;
            }
            if (nativeStream) {
                write("b3-bootstrap-consumed.txt", "B3_SESSION_BOOTSTRAP_CONSUMED\n" + handoffEpochKey() + "\n");
                bootstrapIoDeadline = SystemClock.elapsedRealtime() + 15000;
                return;
            }
            File history = new File(root, historyPrefix + "-attachments.txt");
            android.content.SharedPreferences marker = contextForLedger.getSharedPreferences(historyPrefix + "-history", Context.MODE_PRIVATE);
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
                if (historyFence != null) historyFence.advanced(historyStore.read());
            } catch (Tbs5530AutonomousAttachmentLedger.AlreadyConsumed error) {
                preNativeRefusal = true; failure(error); throw error;
            }
        }
    }
    String reloadEpochKey() throws IOException {
        require();
        if (!reloadEnabled() || !recoveryConfirmed || recoveryEpoch.isEmpty()) throw new IOException("No approved B2 physical epoch");
        if (expires - SystemClock.elapsedRealtime() < (nativeStream ? 15000 : 132L * 2000 + 100 + Tbs5530Fx2Reload.WAIT_MS + 5000))
            throw new IOException("Insufficient bounded session budget before reload consume");
        if (nativeStream) reloadIoDeadline = SystemClock.elapsedRealtime() + 15000;
        return handoffEpochKey();
    }
    String handoffEpochKey() throws IOException {
        require();
        if (!reloadEnabled() || !recoveryConfirmed || recoveryEpoch.isEmpty()) throw new IOException("No approved B2 physical epoch");
        return Tbs5530AutonomousPlans.hash(hostProfile + ":" + recoveryEpoch);
    }
    Tbs5530OneShotBootstrap.Guard firmwareGuard(Boundary physical) {
        return new Tbs5530OneShotBootstrap.Guard() {
            // General transition/observation checks must not require the released upload attachment.
            public void requireCurrent() throws IOException { require(); }
            public void requireStartBudget() throws IOException { requireDeviceIo(); require(); physical.require(); }
            public void requireWrite(int ordinal) throws IOException { requireDeviceIo(); require(); physical.require(); requireBootstrapIoBudget(); }
            public void requireNative(int ordinal) throws IOException { requireDeviceIo(); require(); physical.require(); requireBootstrapIoBudget(); policy.uploadNative(ordinal); }
            public void requireDispatch(int ordinal) throws IOException { requireDeviceIo(); require(); physical.require(); requireBootstrapIoBudget(); policy.uploadDispatch(ordinal); }
            public String runEpoch() { return id; }
            public String physicalEpochKey() { return reloadEnabled() ? Tbs5530AutonomousPlans.hash(hostProfile + ":" + recoveryEpoch) : null; }
        };
    }
    synchronized void failure(Throwable error) {
        if (!"NONE".equals(stopReason)) return;
        failureStage = policy.state() == Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN ? lastStage : policy.state().name();
        errorClass = safeClass(error);
        causeClass = safeClass(error == null ? null : error.getCause());
        String message = error == null ? "" : error.getMessage();
        Throwable nested = error == null ? null : error.getCause();
        for (int depth = 0; nested != null && depth < 8; depth++, nested = nested.getCause()) {
            String detail = nested.getMessage();
            if ("Control approval expired or host heartbeat stale".equals(detail)
                    || "Session stopped, expired, background or host offline".equals(detail)
                    || "Invalid heartbeat".equals(detail) || "Host stopped session".equals(detail)
                    || "Foreground, OS permission or attachment changed".equals(detail)) {
                message = detail; break;
            }
        }
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
        else if ("PLP_MANUAL0_NO_LOCK".equals(message)) stopReason = "PLP_MANUAL0_NO_LOCK";
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
        return new JSONObject().put("evidence_layer", "ANDROID_APP_OBSERVED").put("boundary", "UsbDeviceConnection.controlTransfer")
                .put("syscall_proof", false).put("state", snapshot.state.name()).put("attempted", snapshot.attempted)
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
    static JSONObject interfaceSelectionMetrics(Tbs5530ExplicitInterfaceSelection.Snapshot value) throws org.json.JSONException {
        if (value == null) return null;
        return new JSONObject().put("option", Tbs5530ExplicitInterfaceSelection.OPTION)
                .put("evidence_layer", "ANDROID_APP_OBSERVED").put("clock", "ANDROID_ELAPSED_REALTIME_NS")
                .put("api", "UsbDeviceConnection.setInterface(UsbInterface)").put("state", value.state)
                .put("api_calls", value.apiCalled ? 1 : 0).put("api_result", value.apiResult == null ? JSONObject.NULL : value.apiResult)
                .put("started_ns", value.startedNanos < 0 ? JSONObject.NULL : value.startedNanos)
                .put("returned_ns", value.returnedNanos < 0 ? JSONObject.NULL : value.returnedNanos)
                .put("outstanding_before", value.outstandingBefore < 0 ? JSONObject.NULL : value.outstandingBefore)
                .put("outstanding_after", value.outstandingAfter < 0 ? JSONObject.NULL : value.outstandingAfter)
                .put("before", interfaceObservation(value.before)).put("after", interfaceObservation(value.after));
    }
    private static Object interfaceObservation(Tbs5530ExplicitInterfaceSelection.Observation value) throws org.json.JSONException {
        if (value == null) return JSONObject.NULL;
        return new JSONObject().put("descriptor_sha256", value.descriptorHash).put("configuration_descriptor", 1)
                .put("interface_metadata", value.interfaceId).put("alternate_metadata", value.alternateSetting)
                .put("endpoint", 0x82).put("max_packet_size", value.streamPacketSize).put("negotiated_usb_speed", value.negotiatedUsbSpeed);
    }
    void recordSonySnapshot(Tbs5530SonyTsOutputReadback.Snapshot snapshot) throws IOException {
        if (snapshot==null)throw new IOException("Missing Sony snapshot");
        synchronized(sonySnapshots){sonySnapshots.put(snapshot.stage,snapshot);}
        try { write("sony-readback-"+snapshot.stage.name().toLowerCase(java.util.Locale.ROOT)+".json",sonySnapshot(snapshot).toString()); }
        catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static JSONObject sonySnapshot(Tbs5530SonyTsOutputReadback.Snapshot snapshot) throws org.json.JSONException {
        return new JSONObject().put("stage",snapshot.stage.name()).put("plan_sha256",snapshot.planSha256)
                .put("evidence_layer","ANDROID_APP_OBSERVED").put("clock","ANDROID_ELAPSED_REALTIME_NS")
                .put("started_ns",snapshot.startedNanos).put("ended_ns",snapshot.endedNanos)
                .put("sync_before",snapshot.syncBefore<0?JSONObject.NULL:snapshot.syncBefore)
                .put("sync_after",snapshot.syncAfter<0?JSONObject.NULL:snapshot.syncAfter)
                .put("bank_provenance","PLAN_INFERRED").put("snapshot_atomic",false)
                .put("restore_transfer_confirmed",snapshot.restoreTransferConfirmed).put("complete",snapshot.complete)
                .put("intent_transfers",snapshot.intentTransfers).put("attempted_transfers",snapshot.attemptedTransfers).put("completed_transfers",snapshot.completedTransfers)
                .put("outcome",snapshot.outcome).put("registers",new JSONObject(snapshot.registers))
                .put("C3","NOT_AVAILABLE_SOURCE_WRITES_ONLY").put("96_97","NOT_AVAILABLE_SOURCE_WRITES_ONLY")
                .put("D9","NOT_AVAILABLE_SOURCE_WRITES_ONLY").put("actual_selected_plp","NOT_AVAILABLE")
                .put("l1_post_valid","NOT_AVAILABLE").put("read_side_effect_free","UNKNOWN")
                .put("read_81_provenance","SOURCE_SERIAL_RMW_NOT_PARALLEL_READ_PROOF");
    }
    private JSONObject sonyMetrics() throws org.json.JSONException {
        org.json.JSONArray snapshots=new org.json.JSONArray();
        synchronized(sonySnapshots){for(Tbs5530SonyTsOutputReadback.Snapshot snapshot:sonySnapshots.values())snapshots.put(sonySnapshot(snapshot));}
        return new JSONObject().put("option",Tbs5530SonyTsOutputReadback.OPTION).put("enabled",sonyTsOutputReadback)
                .put("maximum_snapshots",2).put("snapshots",snapshots).put("during_pending","NOT_AVAILABLE_NO_CONCURRENT_I2C");
    }
    static boolean knownB4LockStop(boolean b4, boolean lockLost, String reason, int firstStatus, int firstErrno,
                                   int outstanding, boolean quarantined) {
        return b4 && lockLost && "WATCHDOG".equals(reason) && firstStatus == 0 && firstErrno == 0
                && outstanding == 0 && !quarantined;
    }
    static boolean completedStreamError(Tbs5530TsSmoke.StreamMetrics stream) {
        return stream != null && completedStreamError(stream.firstStatus, stream.firstErrno, stream.outstanding, stream.quarantined);
    }
    static boolean completedStreamError(int firstStatus, int firstErrno, int outstanding, boolean quarantined) {
        return !quarantined && outstanding == 0 && (firstStatus < 0 || firstErrno > 0);
    }
    void finishReceiveAudit(boolean drained) throws IOException {
        if (receiveAudit != null) receiveAudit.finishAfterDrain(drained);
    }
    void report(String result, long bytes, long packets, int errno) throws IOException {
        try {
            JSONObject json = new JSONObject().put("policy", Tbs5530AutonomousPolicy.VERSION).put("state", policy.state().name())
                    .put("result", result).put("ts_bytes", bytes).put("ts_packets", packets).put("last_errno", errno < 0 ? JSONObject.NULL : errno)
                    .put("cleanup", cleanupConfirmed ? "DRAIN_CONFIRMED" : "DRAIN_NOT_CERTIFIED")
                    .put("cleanup_path", cleanupPath).put("terminal", terminal)
                    .put("launch_token", launchToken == null ? JSONObject.NULL : launchToken)
                    .put("control_plane_only", controlPlaneOnly).put("grant_accepted", accepted)
                    .put("base_git_sha", BuildConfig.SOURCE_GIT_SHA)
                    .put("ts_variant", readApi.name()).put("host_profile", hostProfile).put("retry", 0).put("redacted", true)
                    .put("control_mirror", controlMirror == null ? "OFF" : controlMirror.durable() ? "SYNCED_CLOSED" : "NOT_CERTIFIED")
                    .put("control_mirror_records", controlMirror == null ? 0 : controlMirror.count())
                    .put("stop_reason", stopReason).put("failure_stage", failureStage)
                    .put("physical_recovery_epoch_bound", recoveryConfirmed && !recoveryEpoch.isEmpty())
                    .put("pre_native_refusal", preNativeRefusal)
                    .put("native_return_status", preNativeRefusal ? "NOT_CALLED_PRE_NATIVE_REFUSAL" : "SEE_TYPED_RESULT_AND_BOUNDARIES")
                    .put("error_class", errorClass).put("cause_class", causeClass)
                    .put("raw_received_bytes", capture == null ? 0 : capture.bytes())
                    .put("raw_capture", capture == null ? "NOT_STARTED" : capture.durable() ? "SYNCED_CLOSED" : "NOT_CERTIFIED")
                    .put("receive_audit", receiveAudit == null ? "NOT_STARTED" : receiveAudit.state())
                    .put("receive_audit_bytes", receiveAudit == null ? 0 : receiveAudit.bytes())
                    .put("history_guard_warning", historyWarning)
                    .put("b4_enabled", b4Capture)
                    .put("b4_duration_ms", b4Capture ? streamDurationMillis : JSONObject.NULL)
                    .put("b4_max_bytes", b4Capture ? streamMaxBytes : JSONObject.NULL)
                    .put("b4_tuner_rssi", b4Capture && tunerRssi)
                    .put("b4_parity", b4Capture ? "PENDING_PAIRED_OFFLINE_ANALYSIS" : "NOT_APPLICABLE")
                    .put("signal_monitor_samples", signalSampleCount)
                    .put("signal_monitor_failure", signalMonitorFailure == null ? JSONObject.NULL : signalMonitorFailure);
            if (tsResult != null && tsResult.nativeStream != null) {
                Tbs5530TsSmoke.StreamMetrics stream = tsResult.nativeStream;
                json.put("native_stream", new JSONObject().put("bytes", stream.bytes)
                        .put("submitted", stream.submitted).put("completed", stream.completed)
                        .put("starvation_counter", stream.starvationCount).put("min_outstanding", stream.minOutstanding)
                        .put("max_reap_resubmit_ns", stream.maxResubmitNanos).put("median_reap_resubmit_ns", stream.medianResubmitNanos)
                        .put("elapsed_ns", stream.elapsedNanos).put("throughput_bytes_per_second", stream.bytesPerSecond())
                        .put("first_status", stream.firstStatus).put("first_errno", stream.firstErrno)
                        .put("outstanding", stream.outstanding).put("quarantined", stream.quarantined)
                        .put("stop_reason", stream.stopReason).put("stale_prefix_bytes", 2048)
                        .put("writer_written_bytes", stream.writerWrittenBytes).put("writer_drop_bytes", stream.writerDropBytes)
                        .put("writer_failure", stream.writerFailure == null ? JSONObject.NULL : stream.writerFailure)
                        .put("starvation_scope", "SOFTWARE_ZERO_OUTSTANDING_ONLY_GAPS_CLASSIFIED_OFFLINE")
                        .put("gap_classification", "PENDING_COMPLETION_TIMESTAMP_ANALYSIS"));
            }
            File permissionEvidence = new File(directory, "permission.json");
            JSONObject permission = permissionEvidence.isFile() ? new JSONObject(read(permissionEvidence, 8192)) : null;
            boolean matchingPermission = permission != null && id.equals(permission.optString("session"));
            json.put("permission_outcome", matchingPermission ? permission.optString("outcome", "UNVERIFIED") : "UNVERIFIED");
            json.put("permission_state", matchingPermission ? permission.optString("state", "UNVERIFIED") : "UNVERIFIED");
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
            Tbs5530OperationalHandoff.NativeReceiveSnapshot urb = nativeReceiveSnapshot;
            json.put("attachment_history_sha256", historyFence == null ? JSONObject.NULL : historyFence.sha256());
            json.put("frontend_experiment", frontendExperiment.name());
            json.put("requested_plp", frontendExperiment != Tbs5530FrontendExperiment.BASELINE_AUTO ? "MANUAL:0" : "AUTO");
            if (reloadEnabled()) {
                Tbs5530Fx2Reload.Snapshot r = reloadSnapshot;
                json.put("fx2_reload_store_sha256", reloadLedger.sha256()).put("fx2_reload_anchor_sha256", reloadLedger.anchorSha256());
                json.put("fx2_reload", r == null ? JSONObject.NULL : new JSONObject().put("state", r.state.name())
                        .put("attempted", r.attempted).put("completed", r.completed).put("consumed", r.consumed)
                        .put("final_release_ns", r.finalReleaseNanos).put("wait_ended_ns", r.waitEndedNanos).put("detach_ns", r.detachedNanos)
                        .put("bootstrap_original_clean_proven", bootstrapProof != null).put("timebase", "ANDROID_ELAPSED_REALTIME_NS"));
            }
            json.put("actual_plp", "UNKNOWN");
            json.put("explicit_set_interface_alt0", explicitSetInterfaceAlt0);
            json.put("sony_ts_output_readback",sonyMetrics());
            json.put("interface_selection", urb == null ? JSONObject.NULL : interfaceSelectionMetrics(urb.interfaceSelection));
            boolean urbKnown = urb != null && urb.completionKnown() && !urb.quarantined;
            json.put("native_urb", urb == null ? JSONObject.NULL : new JSONObject()
                    .put("attempted", urb.attempted).put("submitted", urb.submitted).put("completed", urb.completed)
                    .put("outstanding", urb.outstanding).put("copied_bytes", urb.copiedBytes).put("cleanup_bytes", urb.cleanupBytes)
                    .put("completion_sequence", urb.completionSequence).put("completion_known", urb.completionKnown())
                    .put("syscall_return", urb.syscallReturn).put("syscall_errno", urb.syscallErrno)
                    .put("urb_status", urb.urbStatus == Integer.MIN_VALUE ? JSONObject.NULL : urb.urbStatus)
                    .put("actual_length", urb.actualLength).put("slot", urb.slot).put("quarantined", urb.quarantined));
            boolean nativeKnown = urbKnown || ts != null && Tbs5530AutonomousPolicy.nativeReturnKnown(synchronous,
                    ts.reads, ts.lastReadResult, ts.lastReadErrno);
            boolean completedError = ts != null && ts.nativeStream != null
                    ? completedStreamError(ts.nativeStream)
                    : urbKnown && urb.urbStatus < 0 || ts != null && ts.status == Tbs5530TsSmoke.Status.NO_DATA_OR_USB_FAILURE
                    && Tbs5530AutonomousPolicy.completedReceiveError(synchronous, ts.reads, ts.lastReadResult, ts.lastReadErrno);
            json.put("native_return_known", nativeKnown);
            json.put("receive_error", !completedError ? "NOT_CLASSIFIED_AS_COMPLETED_ERROR"
                    : policy.state() == Tbs5530AutonomousPolicy.State.ERROR_UNCERTAIN ? "COMPLETED_USB_ERROR_WITH_UNCERTAIN_SESSION_OR_CLEANUP"
                    : cleanupConfirmed ? "KNOWN_COMPLETED_USB_ERROR" : "COMPLETED_USB_ERROR_AWAITING_DRAIN");
            json.put("usb_data_received", ts == null ? bytes > 0 : ts.usbDataReceived());
            json.put("structure_confirmed", ts != null && ts.structureConfirmed());
            json.put("quality_observation", ts == null ? "NOT_ASSESSED" : ts.qualityObservation());
            json.put("quality_min_bytes", 32768);
            json.put("ts_smoke_pass", !b4Capture && ts != null && ts.structureConfirmed() && ts.bytes >= 32768
                    && "NO_ERRORS_IN_BOUNDED_SAMPLE".equals(ts.qualityObservation())
                    && cleanupConfirmed && policy.state() == Tbs5530AutonomousPolicy.State.REPORT
                    && capture != null && capture.durable() && capture.bytes() == ts.bytes
                    && (!reloadEnabled() || receiveAudit != null && "SYNCED_CLOSED".equals(receiveAudit.state()))
                    && (urb == null || !urb.quarantined && urb.outstanding == 0));
            json.put("ts_metrics", ts == null ? JSONObject.NULL : tsMetrics(ts));
            write("report.json", json.toString());
            Tbs5530DurableFile.write(new File(root, PREFIX + "-report.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException error) { throw new IOException(error); }
    }
}
