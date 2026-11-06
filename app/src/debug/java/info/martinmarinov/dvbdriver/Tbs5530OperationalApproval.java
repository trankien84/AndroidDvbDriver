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
    private final long generation;
    private final String operation, planHash;
    private final String challenge = UUID.randomUUID().toString();
    private final long created;
    private volatile boolean foreground = true, revoked, rf;
    private boolean consumed;
    private String approvalId;
    private final StringBuilder audit = new StringBuilder();

    Tbs5530OperationalApproval(Context context, Tbs5530Frontend.Plan plan) throws IOException {
        this(androidStorage(context), SystemClock::elapsedRealtime, plan);
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan) throws IOException {
        this(storage, clock, plan, null, plan.generation);
    }
    Tbs5530OperationalApproval(Context context, long generation, Tbs5530TsSmoke.Limits limits) throws IOException {
        this(androidStorage(context), SystemClock::elapsedRealtime, null, limits, generation);
    }
    Tbs5530OperationalApproval(Storage storage, Clock clock, Tbs5530Frontend.Plan plan,
                              Tbs5530TsSmoke.Limits limits, long generation) throws IOException {
        this.storage = storage; this.clock = clock; this.plan = plan; tsLimits = limits;
        this.generation = generation; created = clock.now();
        operation = plan == null ? "TS_SMOKE" : plan.operation.name();
        String transcript = plan == null ? "TBS5530-TS-SMOKE/v1|generation=" + generation
                + "|OUT=40/8A/0/0/0a00|timeoutMs=2000|BulkIN=82|bytes=" + limits.maxBytes
                + "|reads=" + limits.maxReads + "|durationMs=" + limits.durationMillis
                + "|readTimeoutMs=" + limits.readTimeoutMillis + "|RF_IN_CONFIRMED|LOCK_REQUIRED|retry=0"
                : plan.getApprovalTranscript();
        planHash = plan == null ? hash(transcript) : plan.getSha256();
        try {
            JSONObject json = new JSONObject();
            json.put("protocol", 1); json.put("challenge", challenge); json.put("plan_sha256", planHash);
            json.put("operation", operation); json.put("generation", generation);
            json.put("created_elapsed_ms", created); json.put("max_session_ms", 300000); json.put("heartbeat_ms", 2000);
            json.put("usb_out_count", plan == null ? 1 : plan.usbOutCount); json.put("usb_in_count", plan == null ? 0 : plan.usbInCount);
            json.put("maximum_ms", plan == null ? limits.durationMillis + 2000L : plan.maximumMillis); json.put("transcript", transcript);
            storage.write("phase30-challenge.json", json.toString());
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
    void foreground(boolean value) { if (!value) revoked = true; foreground = value; }
    void rfIn(boolean value) { rf = value; }
    boolean eligible() { try { requireCurrent(); return !consumed; } catch (IOException error) { return false; } }
    public void requireCurrent() throws IOException {
        if (!foreground || revoked) throw new IOException("Control foreground/consent revoked");
        try {
            JSONObject permit = new JSONObject(storage.read()); long now = clock.now();
            if (now < created || now - created >= 300000 || permit.getInt("protocol") != 1
                    || !challenge.equals(permit.getString("challenge")) || !planHash.equals(permit.getString("plan_sha256"))
                                        || !operation.equals(permit.getString("operation")) || permit.getLong("generation") != generation
                    || !permit.getBoolean("explicit_control_approval") || !permit.getBoolean("physical_current")) {
                throw new IOException("Control approval scope/attachment/clock mismatch");
            }
            long expires = permit.getLong("expires_elapsed_ms"), heartbeat = permit.getLong("heartbeat_elapsed_ms");
            if (expires <= now || expires > created + 300000 || heartbeat < created || heartbeat > now || now - heartbeat >= 2000) {
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
        storage.write("phase30-audit-" + approvalId + ".jsonl", "{\"event\":\"CONSUMED\",\"plan_sha256\":\"" + planHash + "\"}\n");
    }
    public synchronized void audit(String boundary, int ordinal, int type, int request, int length, int result) throws IOException {
        if (!consumed) throw new IOException("Audit requires consumed approval");
        try {
            JSONObject event = new JSONObject(); event.put("event", boundary); event.put("ordinal", ordinal);
            event.put("type", type); event.put("request", request); event.put("length", length); event.put("result", result);
            event.put("elapsed_ms", clock.now());
            audit.append(event).append('\n');
            storage.write("phase30-boundaries-" + approvalId + ".jsonl", audit.toString());
        } catch (JSONException error) { throw new IOException(error); }
    }
    public boolean rfInConfirmed() { return rf && foreground && !revoked; }

    private static Storage androidStorage(Context context) {
        Context app = context.getApplicationContext(); File directory = app.getFilesDir();
        android.content.SharedPreferences preferences = app.getSharedPreferences("phase30-control-attempts", Context.MODE_PRIVATE);
        return new Storage() {
            public String read() throws IOException {
                File file = new File(directory, "phase30-permit.json");
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
