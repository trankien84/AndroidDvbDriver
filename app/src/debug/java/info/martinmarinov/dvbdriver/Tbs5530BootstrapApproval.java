// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.AtomicFile;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop.Kind;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Android storage adapter. New attempts have separate consumed stores; legacy state is never reset. */
final class Tbs5530BootstrapApproval extends Tbs5530ApprovalSession {
    static final Clock ANDROID_CLOCK = new Clock() {
        public long now() { return SystemClock.elapsedRealtime(); }
        public void pause(long millis) throws InterruptedException { Thread.sleep(millis); }
    };
    Tbs5530BootstrapApproval(Context context, info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device device) throws IOException {
        super(new AndroidStorage(context.getApplicationContext(), context.getFilesDir(), "phase29-attempt"),
                ANDROID_CLOCK, attachmentHash(device), true);
    }
    Tbs5530BootstrapApproval(Storage storage, Clock clock, String attachmentHash) throws IOException {
        super(storage, clock, attachmentHash, false);
    }
    private static String attachmentHash(info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device device) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (device.path + ":" + device.id + ":" + device.vendorId + ":" + device.productId).getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
    }
    private static String readFile(File file) throws IOException {
        try (FileInputStream in = new AtomicFile(file).openRead()) {
            byte[] bytes = new byte[8193]; int size = 0, n;
            while (size < bytes.length && (n = in.read(bytes, size, bytes.length - size)) > 0) size += n;
            if (size == bytes.length) throw new IOException("Oversized approval metadata");
            return new String(bytes, 0, size, StandardCharsets.UTF_8);
        }
    }
    private static void writeFile(File file, String content) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(content.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (IOException error) { if (out != null) atomic.failWrite(out); throw error; }
    }
    static final class AndroidStorage implements Storage {
        private final File directory;
        private final Context context;
        private final String preferencesName;
        private final SharedPreferences prefs;
        AndroidStorage(Context context, File directory, String preferencesName) {
            this.context = context; this.preferencesName = preferencesName;
            this.directory = directory; prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        }
        private SharedPreferences attempt(String id) {
            if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                throw new IllegalArgumentException("Invalid new approval ID");
            return context.getSharedPreferences(preferencesName + "-new-" + id, Context.MODE_PRIVATE);
        }
        public boolean attemptConsumed(String id) { return attempt(id).getBoolean("consumed", false); }
        public void consumeAttempt(String id) throws IOException {
            synchronized (Tbs5530ApprovalSession.class) {
                SharedPreferences store = attempt(id);
                if (store.getBoolean("consumed", false)) throw new Tbs5530BootstrapStop(Kind.ATTEMPT_CONSUMED, "New attempt already consumed");
                if (!store.edit().putBoolean("consumed", true).commit()) throw new IOException("New attempt persistence failed");
            }
        }
        public String install() throws IOException {
            synchronized (Tbs5530BootstrapApproval.class) {
                String id = prefs.getString("install", null);
                if (id == null) {
                    id = UUID.randomUUID().toString();
                    if (!prefs.edit().putString("install", id).commit()) throw new IOException("Install identity unavailable");
                }
                return id;
            }
        }
        public boolean consumed() { return prefs.getBoolean("consumed", false); }
        public void consume() throws IOException {
            if (consumed()) throw new Tbs5530BootstrapStop(Kind.ATTEMPT_CONSUMED, "Attempt already consumed");
            if (!prefs.edit().putBoolean("consumed", true).commit()) throw new IOException("Attempt persistence failed");
        }
        public String read(String name) throws IOException { return readFile(new File(directory, name)); }
        public void write(String name, String content) throws IOException { writeFile(new File(directory, name), content); }
    }
}
