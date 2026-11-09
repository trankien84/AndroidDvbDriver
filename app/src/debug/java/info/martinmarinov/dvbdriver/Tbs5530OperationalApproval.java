// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.SystemClock;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlProbe;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DurableFile;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530Frontend;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530TsSmoke;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Separate one-program approval. Never reads or modifies Phase 2.9 stores/permits/ledger. */
final class Tbs5530OperationalApproval implements Tbs5530ControlProbe.Authorization, Tbs5530TsSmoke.Permission {
    interface Storage {
        String read() throws IOException;
        void write(String name, String text) throws IOException;
        boolean consumed(String id);
        void consume(String id) throws IOException;
    }
    interface Clock { long now(); }
    private final Storage storage;
    private final Clock clock;
    private final Tbs5530Frontend.Plan plan;
    private final Tbs5530TsSmoke.Limits tsLimits;
    private static final long START_MARGIN_MS = 5000;
    private final long generation, maximumMillis;
    private final String ownerIdentity;
    private final boolean experimentalAuto, endpointPreparation, queuedReception, nativeUrbReception;
    private final String operation, planHash;
    private final String challenge = UUID.randomUUID().toString();
    private final long created, heartbeatNotBefore;
    private volatile boolean foreground = true, revoked, rf;
    private boolean consumed;
    private String approvalId;
    private final StringBuilder audit = new StringBuilder();

    Tbs5530OperationalApproval(Context context, Tbs5530Frontend.Plan plan, String owner) throws IOException {
        this(androidStorage(context), SystemClock::elapsedRealtime, plan, null, plan.generation, bootBoundIdentity(context, owner));
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan) throws IOException {
        this(storage, clock, plan, null, plan.generation);
    }
    Tbs5530OperationalApproval(Context context, long generation, Tbs5530TsSmoke.Limits limits, String owner) throws IOException {
        this(androidStorage(context), SystemClock::elapsedRealtime, null, limits, generation, bootBoundIdentity(context, owner));
    }
    private static String bootBoundIdentity(Context context, String owner) throws IOException {
        int boot = android.provider.Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        if (boot < 0 || owner == null || !owner.matches("[a-f0-9]{64}")) throw new IOException("Boot/USB owner identity unavailable");
        return hash("TBS5530-PHASE31-OWNER/v1|boot=" + boot + "|lease=" + owner);
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan,
                              Tbs5530TsSmoke.Limits limits, long generation) throws IOException {
        this(storage, clock, plan, limits, generation, "SYNTHETIC_TEST_FIXTURE");
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan,
                              Tbs5530TsSmoke.Limits limits, long generation, String ownerIdentity) throws IOException {
        this(storage, clock, plan, limits, generation, ownerIdentity, -1);
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan,
                              Tbs5530TsSmoke.Limits limits, long generation, String ownerIdentity,
                              long sessionCreated) throws IOException {
        if (ownerIdentity == null || ownerIdentity.isEmpty()) throw new IOException("Owner identity required");
        this.storage = storage; this.clock = clock; this.plan = plan; tsLimits = limits;
        this.ownerIdentity = ownerIdentity;
        maximumMillis = plan == null ? limits.maximumMillis() : plan.maximumMillis;
        endpointPreparation = plan == null && limits.prepareEndpoint;
        queuedReception = plan == null && limits.readApi == Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096;
        nativeUrbReception = plan == null && info.martinmarinov.drivers.usb.tbs5530.Tbs5530AutonomousPlans.nativeUrb(limits.readApi);
        experimentalAuto = plan != null && plan.tuneParameters != null
                && plan.tuneParameters.selection.mode == info.martinmarinov.drivers.usb.tbs5530.Tbs5530DvbT2TuneParameters.Mode.AUTO;
        this.generation = generation; created = clock.now();
        heartbeatNotBefore = sessionCreated == -1 ? created : sessionCreated;
        if (heartbeatNotBefore < 0 || heartbeatNotBefore > created || created - heartbeatNotBefore >= 300000)
            throw new IOException("Invalid session heartbeat origin");
        operation = plan == null ? "TS_SMOKE" : plan.operation.name();
        String transcript = plan == null ? (nativeUrbReception ? "TBS5530-TS-SMOKE/v6|generation="
                : queuedReception ? "TBS5530-TS-SMOKE/v5|generation=" : "TBS5530-TS-SMOKE/v4|generation=") + generation
                + "|OUT=40/8A/0/0/0a00|timeoutMs=2000|BulkIN=82|bytes=" + limits.maxBytes
                + "|reads=" + limits.maxReads + "|durationMs=" + limits.durationMillis
                + "|readTimeoutMs=" + limits.readTimeoutMillis + "|chunkBytes=" + limits.readChunkBytes
                + (nativeUrbReception ? "|readApi=USBDEVFS_URB_8X" + limits.readChunkBytes + "|queueDepth=8|drainMaxMs=1000|bulkStreamId=0|errorPayload=METADATA_ONLY"
                : queuedReception ? "|readApi=USB_REQUEST_QUEUE_8X4096_API26|queueDepth=8|drainMaxMs=1000" : "|readApi=USBDEVFS_BULK_ERRNO")
                + (endpointPreparation ? "|prepare=USBDEVFS_CLEAR_HALT:82|prepareMaxMs=5000"
                + "|prepareSource=tbsdtv/linux_media@2ce787de9f6e81b4294692a45de15596ae2e1322:usb-urb.c:usb_urb_init:220" : "")
                + "|RF_IN_CONFIRMED|LOCK_REQUIRED|retry=0"
                : plan.getApprovalTranscript();
        planHash = plan == null ? hash(transcript) : plan.getSha256();
        try {
            JSONObject json = new JSONObject();
            json.put("protocol", 2); json.put("challenge", challenge); json.put("plan_sha256", planHash);
            json.put("owner_identity", ownerIdentity); json.put("experimental_auto_plp", experimentalAuto);
            json.put("endpoint_preparation", endpointPreparation);
            if (queuedReception) json.put("queued_reception", true);
            if (nativeUrbReception) json.put("native_urb_reception", true);
            json.put("start_margin_ms", START_MARGIN_MS);
            json.put("operation", operation); json.put("generation", generation);
            json.put("created_elapsed_ms", created); json.put("max_session_ms", 300000); json.put("heartbeat_ms", 2000);
            json.put("usb_out_count", plan == null ? (endpointPreparation ? 2 : 1) : plan.usbOutCount); json.put("usb_in_count", plan == null ? 0 : plan.usbInCount);
            json.put("maximum_ms", maximumMillis); json.put("transcript", transcript);
            storage.write("phase31-challenge.json", json.toString());
        } catch (JSONException error) { throw new IOException("Cannot publish control challenge", error); }
    }
    private static String hash(String text) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte b : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
    }
    String planSha256() { return planHash; }
    void foreground(boolean value) { if (!value) revoked = true; foreground = value; }
    void rfIn(boolean value) { rf = value; }
    boolean eligible() { try { requireCurrent(); return !consumed; } catch (IOException error) { return false; } }
    public void requireCurrent() throws IOException {
        if (!foreground || revoked) throw new IOException("Control foreground/consent revoked");
        try {
            JSONObject permit = new JSONObject(storage.read()); long now = clock.now();
            if (now < created || now - created >= 300000 || permit.getInt("protocol") != 2
                    || !ownerIdentity.equals(permit.getString("owner_identity"))
                    || permit.getLong("approved_maximum_ms") != maximumMillis
                    || experimentalAuto && !permit.getBoolean("explicit_experimental_auto_plp")
                    || endpointPreparation && !permit.getBoolean("explicit_endpoint_preparation")
                    || queuedReception && !permit.getBoolean("explicit_queued_reception")
                    || nativeUrbReception && !permit.getBoolean("explicit_native_urb_reception")
                    || !challenge.equals(permit.getString("challenge")) || !planHash.equals(permit.getString("plan_sha256"))
                                        || !operation.equals(permit.getString("operation")) || permit.getLong("generation") != generation
                    || !permit.getBoolean("explicit_control_approval") || !permit.getBoolean("physical_current")) {
                throw new IOException("Control approval scope/attachment/clock mismatch");
            }
            long expires = permit.getLong("expires_elapsed_ms"), heartbeat = permit.getLong("heartbeat_elapsed_ms");
            if (expires <= now || expires > heartbeatNotBefore + 300000 || heartbeat < heartbeatNotBefore || heartbeat > now || now - heartbeat >= 2000
                    || !consumed && Math.min(expires, heartbeatNotBefore + 300000) - now < maximumMillis + START_MARGIN_MS) {
                throw new IOException("Control approval expired or host heartbeat stale");
            }
            String id = permit.getString("approval_id");
            if (!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || approvalId != null && !approvalId.equals(id) || !consumed && storage.consumed(id)) {
                throw new IOException("Control approval consumed or changed");
            }
            if (consumed && !storage.consumed(id)) throw new IOException("Control consumption record missing");
        } catch (JSONException | RuntimeException error) { throw new IOException("Invalid control permit", error); }
    }
    public synchronized void consume(Tbs5530Frontend.Plan approved) throws IOException {
        if (plan == null || approved != plan) throw new IOException("Foreign control plan");
        consumeScope();
    }
    public synchronized void consume(Tbs5530TsSmoke.Limits limits, long token) throws IOException {
        if (tsLimits == null || tsLimits != limits || token != generation) throw new IOException("Foreign TS limits or epoch");
        consumeScope();
    }
    public void requireCurrent(long token) throws IOException {
        requireCurrent();
        if (token != generation || !rfInConfirmed()) throw new IOException("TS epoch/RF IN conditions changed");
    }
    public void audit(String event, int ordinal, int count) throws IOException { audit(event, ordinal, 0, 0, 0, count); }
    private void consumeScope() throws IOException {
        if (consumed) throw new IOException("Control program already consumed");
        requireCurrent();
        try { approvalId = new JSONObject(storage.read()).getString("approval_id"); }
        catch (JSONException error) { throw new IOException(error); }
        storage.consume(approvalId); consumed = true;
        storage.write("phase31-audit-" + approvalId + ".jsonl", "{\"event\":\"CONSUMED\",\"plan_sha256\":\"" + planHash + "\"}\n");
    }
    public synchronized void audit(String boundary, int ordinal, int type, int request, int length, int result) throws IOException {
        if (!consumed) throw new IOException("Audit requires consumed approval");
        try {
            JSONObject event = new JSONObject(); event.put("event", boundary); event.put("ordinal", ordinal);
            event.put("type", type); event.put("request", request); event.put("length", length); event.put("result", result);
            event.put("elapsed_ms", clock.now());
            audit.append(event).append('\n');
            storage.write("phase31-boundaries-" + approvalId + ".jsonl", audit.toString());
        } catch (JSONException error) { throw new IOException(error); }
    }
    public boolean experimentalAutoPlpApproved() {
        try { requireCurrent(); return experimentalAuto; } catch (IOException error) { return false; }
    }
    public boolean queuedReceptionApproved() {
        try { requireCurrent(); return queuedReception; } catch (IOException error) { return false; }
    }
    public boolean nativeUrbReceptionApproved() {
        try { requireCurrent(); return nativeUrbReception; } catch (IOException error) { return false; }
    }
    public boolean endpointPreparationApproved() {
        try { requireCurrent(); return endpointPreparation; } catch (IOException error) { return false; }
    }
    public boolean rfInConfirmed() { return rf && foreground && !revoked; }

    private static Storage androidStorage(Context context) {
        Context app = context.getApplicationContext(); File directory = app.getFilesDir();
        android.content.SharedPreferences preferences = app.getSharedPreferences("phase31-control-attempts", Context.MODE_PRIVATE);
        return new Storage() {
            public String read() throws IOException {
                File file = new File(directory, "phase31-permit.json");
                try (java.io.FileInputStream input = new android.util.AtomicFile(file).openRead()) {
                    byte[] bytes = new byte[8193]; int size = 0, n;
                    while (size < bytes.length && (n = input.read(bytes, size, bytes.length-size)) > 0) size += n;
                    if (size == bytes.length) throw new IOException("Control permit too large");
                    return new String(bytes, 0, size, StandardCharsets.UTF_8);
                }
            }
            public void write(String name, String text) throws IOException {
                Tbs5530DurableFile.write(new File(directory, name), text.getBytes(StandardCharsets.UTF_8));
            }
            public boolean consumed(String id) { return preferences.getBoolean(id, false); }
            public void consume(String id) throws IOException {
                synchronized (Tbs5530OperationalApproval.class) {
                    if (consumed(id) || !preferences.edit().putBoolean(id, true).commit()) throw new IOException("Control attempt persistence failed");
                }
            }
        };
    }
}
