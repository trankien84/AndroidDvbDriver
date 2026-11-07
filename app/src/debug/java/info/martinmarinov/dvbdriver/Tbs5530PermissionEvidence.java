// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AutonomousPlans;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/** Private host/app handshake. A receipt labels evidence; it cannot grant USB access. */
final class Tbs5530PermissionEvidence {
    private Tbs5530PermissionEvidence() { }
    static String identity(Tbs5530PermissionState.Attempt attempt) throws IOException {
        return Tbs5530AutonomousPlans.hash(attempt.device.path + ":" + attempt.device.id);
    }
    static JSONObject challenge(String session, Tbs5530PermissionState permission, long now) throws IOException {
        synchronized (permission) {
            Tbs5530PermissionState.Attempt attempt = permission.attempt();
            try {
                JSONObject value = new JSONObject().put("version", 1).put("session", session)
                        .put("state", permission.state().name()).put("outcome", permission.outcome().name())
                        .put("elapsed_ms", now).put("vid", Tbs5530PermissionState.VID).put("pid", Tbs5530PermissionState.PID);
                if (attempt != null) value.put("request_token", attempt.token)
                        .put("attachment_generation", attempt.generation).put("device_identity_sha256", identity(attempt))
                        .put("requested_elapsed_ms", attempt.requestedAt);
                return value;
            } catch (JSONException error) { throw new IOException("Cannot encode private permission evidence", error); }
        }
    }
    static boolean receipt(String session, Tbs5530PermissionState permission, String text, long now) throws IOException {
        synchronized (permission) {
            Tbs5530PermissionState.Attempt attempt = permission.attempt();
            if (attempt == null) return false;
            try {
                JSONObject value = new JSONObject(text);
                if (value.getInt("version") != 1 || !session.equals(value.getString("session"))
                        || !"GRANT".equals(value.getString("action"))
                        || value.getInt("vid") != Tbs5530PermissionState.VID || value.getInt("pid") != Tbs5530PermissionState.PID
                        || !identity(attempt).equals(value.getString("device_identity_sha256"))) return false;
                return permission.uiReceipt(value.getString("request_token"), value.getLong("attachment_generation"),
                        attempt.device, value.getLong("observed_elapsed_ms"), value.getLong("clicked_elapsed_ms"), now);
            } catch (JSONException error) { return false; }
        }
    }
}
