// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

/** Exact immutable recovery scope, not a USB grant. All clocks are Android elapsed realtime. */
final class Tbs5530RecoveryPermit implements Tbs5530HistoryRecoveryStore.Authorization {
    static final long MAX_MS = 120000, HEARTBEAT_MS = 2000;
    interface Port {
        long now(); boolean foreground();
        String grant() throws IOException;
        String heartbeat() throws IOException;
        boolean consumed() throws IOException;
        void consume(String record) throws IOException;
    }
    private static final String[] KEYS = { "protocol", "session", "launch_token", "model", "api", "boot_count",
        "apk_sha256", "source_git_sha", "plan_sha256", "before_sha256", "after_sha256", "before_bytes", "after_bytes", "created_ms" };
    private final JSONObject challenge;
    private final Port port;
    private final long created;
    private String immutable;
    private boolean accepted, consumed;
    private volatile boolean stopped;
    private long last;
    Tbs5530RecoveryPermit(JSONObject challenge, Port port) throws IOException {
        try { this.challenge = new JSONObject(challenge.toString()); created = challenge.getLong("created_ms"); }
        catch (JSONException error) { throw new IOException("Recovery challenge malformed", error); }
        this.port = port; last = created;
    }
    void accept() throws IOException {
        if (accepted || stopped) throw new IOException("Recovery permit cannot be rearmed");
        String text = port.grant();
        try {
            JSONObject grant = new JSONObject(text);
            for (String key : KEYS) if (!challenge.get(key).equals(grant.get(key))) throw new IOException("Recovery grant scope mismatch: " + key);
            if (!Boolean.TRUE.equals(grant.opt("append_only_authorized")) || !Boolean.TRUE.equals(grant.opt("no_tuner_confirmed"))
                    || !Boolean.FALSE.equals(grant.opt("hardware_authorized")) || !Boolean.FALSE.equals(grant.opt("full_history_proven"))
                    || !grant.getString("host_script_sha256").matches("[a-f0-9]{64}")) throw new IOException("Recovery authorization missing");
            immutable = text; accepted = true;
            requireCurrent();
            if (port.consumed()) throw new IOException("Recovery plan already consumed");
        } catch (JSONException | RuntimeException error) { stopped=true; throw new IOException("Recovery grant malformed", error); }
        catch (IOException error) { stopped=true; throw error; }
    }
    @Override public void requireCurrent() throws IOException {
        try {
            long now=port.now();
            if (!accepted || stopped || !port.foreground() || now<last || now<created || now-created>=MAX_MS
                    || !immutable.equals(port.grant())) throw new IOException("Recovery permission lost or changed");
            last=now;
            JSONObject grant=new JSONObject(immutable), beat=new JSONObject(port.heartbeat());
            long expires=grant.getLong("expires_ms"), time=beat.getLong("elapsed_ms");
            if(expires<=now || expires>created+MAX_MS || time<created || time>now || now-time>=HEARTBEAT_MS
                    || !challenge.getString("session").equals(beat.getString("session")) || !Boolean.FALSE.equals(beat.opt("stop")))
                throw new IOException("Recovery heartbeat or deadline expired");
            if(consumed && !port.consumed()) throw new IOException("Recovery consumption record missing");
        } catch (JSONException | RuntimeException error) { stopped=true; throw new IOException("Recovery state malformed",error); }
        catch (IOException error) { stopped=true; throw error; }
    }
    @Override public synchronized void consume(String plan) throws IOException {
        requireCurrent();
        try {
            if (consumed || !challenge.getString("plan_sha256").equals(plan) || port.consumed())
                throw new IOException("Recovery consumption refused");
            port.consume(new JSONObject(immutable).put("event","RECOVERY_CONSUMED_BEFORE_APPEND").toString());
            consumed=true; requireCurrent();
        } catch(JSONException error){stopped=true;throw new IOException(error);}
        catch(IOException error){stopped=true;throw error;}
    }
    synchronized void stop() { stopped=true; }
}
