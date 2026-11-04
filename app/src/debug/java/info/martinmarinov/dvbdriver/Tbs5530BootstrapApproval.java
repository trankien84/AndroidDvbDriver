// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A private challenge is not approval. Only a separately provisioned, fresh session permit is accepted. */
final class Tbs5530BootstrapApproval {
    private final Context context;
    private final String challenge = UUID.randomUUID().toString();
    private final String install;
    private final String attachmentHash;
    private final long created = SystemClock.elapsedRealtime();
    private volatile boolean foreground;

    Tbs5530BootstrapApproval(Context context, info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device device) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (device.path + ":" + device.id + ":" + device.vendorId + ":" + device.productId).getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            attachmentHash = text.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
        this.context = context.getApplicationContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("phase29-attempt", Context.MODE_PRIVATE);
        String id = prefs.getString("install", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            if (!prefs.edit().putString("install", id).commit()) throw new IOException("Install identity unavailable");
        }
        install = id;
    }
    void foreground(boolean current) { foreground = current; }
    void publishChallenge() throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("challenge", challenge); json.put("install", install);
            json.put("created_elapsed_ms", created); json.put("upload_epoch", 1);
            json.put("attachment_hash", attachmentHash);
            json.put("plan", "TBS_SOURCE_DUAL_RESET");
            json.put("sha256", info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareValidation.SHA256);
            write(new File(context.getFilesDir(), "phase29-challenge.json"), json.toString());
        } catch (org.json.JSONException error) { throw new IOException("Challenge export failed", error); }
    }
    void requireCurrent() throws IOException {
        long now = SystemClock.elapsedRealtime();
        if (!foreground || now - created > 300000 || now < created) throw new IOException("Foreground approval expired");
        try {
            JSONObject permit = new JSONObject(read(new File(context.getFilesDir(), "phase29-session-approval.json")));
            long expires = permit.getLong("expires_elapsed_ms"), heartbeat = permit.getLong("heartbeat_elapsed_ms");
            if (!challenge.equals(permit.getString("challenge")) || !install.equals(permit.getString("install"))
                    || !attachmentHash.equals(permit.getString("attachment_hash"))
                    || permit.getLong("upload_epoch") != 1
                    || !"TBS_SOURCE_DUAL_RESET".equals(permit.getString("plan"))
                    || !info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwareValidation.SHA256.equals(permit.getString("sha256"))
                    || !permit.getBoolean("explicit_session_upload_approval")
                    || !permit.getBoolean("physical_host_and_otg_confirmed")
                    || expires <= now || expires > created + 300000 || heartbeat > now || now - heartbeat > 2000) {
                throw new IOException("Session permit or live Wireless ADB heartbeat invalid");
            }
        } catch (org.json.JSONException | RuntimeException error) { throw new IOException("No valid session approval", error); }
    }
    void requireWrite(int ordinal) throws IOException {
        requireCurrent();
        if (ordinal < 1 || ordinal > 132) throw new IOException("Invalid write ordinal");
        try {
            JSONObject request = new JSONObject(); request.put("challenge", challenge); request.put("ordinal", ordinal);
            write(new File(context.getFilesDir(), "phase29-write-request.json"), request.toString());
            long deadline = SystemClock.elapsedRealtime() + 1500;
            while (true) {
                requireCurrent();
                JSONObject permit = new JSONObject(read(new File(context.getFilesDir(), "phase29-session-approval.json")));
                if (permit.optInt("authorized_ordinal", 0) == ordinal) return;
                if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("No fresh Wireless ADB step acknowledgment");
                try { Thread.sleep(10); } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IOException("Approval interrupted");
                }
            }
        } catch (org.json.JSONException error) { throw new IOException("Invalid acknowledgment", error); }
    }
    void consume() throws IOException {
        requireCurrent();
        android.content.SharedPreferences prefs = context.getSharedPreferences("phase29-attempt", Context.MODE_PRIVATE);
        synchronized (Tbs5530BootstrapApproval.class) {
            if (prefs.getBoolean("consumed", false)) throw new IOException("Physical attempt already consumed; no retry");
            if (!prefs.edit().putBoolean("consumed", true).commit()) throw new IOException("Attempt persistence failed");
        }
    }
    boolean consumed() { return context.getSharedPreferences("phase29-attempt", Context.MODE_PRIVATE).getBoolean("consumed", false); }
    private static String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[4097]; int size = 0, n;
            while (size < bytes.length && (n = in.read(bytes, size, bytes.length - size)) > 0) size += n;
            if (size == bytes.length) throw new IOException("Oversized permit");
            return new String(bytes, 0, size, StandardCharsets.UTF_8);
        }
    }
    private static void write(File file, String content) throws IOException {
        android.util.AtomicFile atomic = new android.util.AtomicFile(file);
        java.io.FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(content.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (IOException error) { if (out != null) atomic.failWrite(out); throw error; }
    }
}
