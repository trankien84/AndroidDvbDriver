// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

/** Exact immutable no-tuner grant. An Activity launch or challenge never grants initialization. */
final class Tbs5530ReloadInitPermit implements Tbs5530ReloadStore.InitializationApproval {
    static final long MAX_MS = 120000, HEARTBEAT_MS = 2000;
    interface Port {
        long now(); boolean foreground();
        String grant() throws IOException;
        String heartbeat() throws IOException;
        boolean consumed() throws IOException;
        void consume(String record) throws IOException;
        void requireConsumed(String record) throws IOException;
    }
    static final String[] KEYS = { "protocol", "session", "launch_token", "model", "api", "boot_count", "apk_sha256",
        "source_git_sha", "plan_sha256", "history_sha256", "history_bytes", "history_records", "store_sha256", "anchor_sha256",
        "marker_sha256", "store_bytes", "anchor_bytes", "marker_bytes", "created_ms" };
    private final JSONObject challenge;
    private final Port port;
    private final long created;
    private long last;
    private String immutable, consumedRecord;
    private boolean accepted;
    private volatile boolean stopped;
    Tbs5530ReloadInitPermit(JSONObject challenge, Port port) throws IOException {
        try { this.challenge = new JSONObject(challenge.toString()); created = challenge.getLong("created_ms"); }
        catch (JSONException error) { throw new IOException("Initialization challenge malformed", error); }
        this.port = port; last = created;
    }
    synchronized void accept() throws IOException {
        try {
            if (accepted || stopped) throw new IOException("Initialization cannot be rearmed");
            String text = port.grant(); JSONObject grant = new JSONObject(text);
            for (String key : KEYS) if (!challenge.get(key).equals(grant.get(key))) throw new IOException("Initialization grant mismatch: " + key);
            if (!Boolean.TRUE.equals(grant.opt("first_initialization_authorized")) || !Boolean.TRUE.equals(grant.opt("no_tuner_confirmed"))
                    || !Boolean.FALSE.equals(grant.opt("hardware_authorized")) || !Integer.valueOf(0).equals(grant.opt("retry"))
                    || !grant.getString("host_script_sha256").matches("[a-f0-9]{64}")) throw new IOException("Exact initialization authorization missing");
            if (port.consumed()) throw new IOException("Initialization tombstone already exists; no rearm");
            immutable = text; accepted = true; requireCurrent();
        } catch (JSONException | RuntimeException error) { stopped = true; throw new IOException("Initialization grant malformed", error); }
        catch (IOException error) { stopped = true; throw error; }
    }
    void requireCurrent() throws IOException {
        try {
            long now = port.now();
            if (!accepted || stopped || !port.foreground() || now < last || now < created || now - created >= MAX_MS
                    || !immutable.equals(port.grant())) throw new IOException("Initialization permission lost or changed");
            last = now;
            JSONObject grant = new JSONObject(immutable), beat = new JSONObject(port.heartbeat());
            long expires = grant.getLong("expires_ms"), time = beat.getLong("elapsed_ms");
            if (expires <= now || expires > created + MAX_MS || time < created || time > now || now - time >= HEARTBEAT_MS
                    || !challenge.getString("session").equals(beat.getString("session")) || !Boolean.FALSE.equals(beat.opt("stop")))
                throw new IOException("Initialization heartbeat or deadline expired");
            if (consumedRecord != null) port.requireConsumed(consumedRecord);
            else if (port.consumed()) throw new IOException("Foreign initialization consumption");
        } catch (JSONException | RuntimeException error) { stopped = true; throw new IOException("Initialization state malformed", error); }
        catch (IOException error) { stopped = true; throw error; }
    }
    public void requireExact(String store, String anchor, String marker, String history) throws IOException {
        requireCurrent();
        if (!Tbs5530ReloadInitPlan.STORE.equals(store) || !Tbs5530ReloadInitPlan.ANCHOR.equals(anchor)
                || !Tbs5530ReloadInitPlan.MARKER.equals(marker) || !Tbs5530ReloadInitPlan.HISTORY.equals(history)) {
            stopped = true; throw new IOException("Initialization template/history differs from exact plan");
        }
    }
    public synchronized void consume() throws IOException {
        try {
            requireCurrent();
            if (consumedRecord != null || port.consumed()) throw new IOException("Initialization already consumed");
            String record = new JSONObject(immutable).put("event", "RELOAD_INITIALIZATION_CONSUMED_BEFORE_CREATE").toString();
            port.consume(record); consumedRecord = record; requireCurrent();
        } catch (JSONException error) { stopped = true; throw new IOException(error); }
        catch (IOException error) { stopped = true; throw error; }
    }
    void stop() { stopped = true; }
}
