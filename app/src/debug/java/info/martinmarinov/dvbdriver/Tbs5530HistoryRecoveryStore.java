// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530AutonomousAndroidAccess;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530HistoryRecovery;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** No exported Activity or apply intent. A separately reviewed caller must supply exact durable authorization. */
final class Tbs5530HistoryRecoveryStore {
    interface Authorization {
        void requireCurrent() throws IOException;
        void consume(String exactPlanSha256) throws IOException;
    }
    static byte[] readSnapshot(Context context, String prefix) throws IOException {
        File base = history(context, prefix);
        requireClean(context, prefix);
        try (FileInputStream input = new FileInputStream(base)) {
            byte[] bytes = new byte[(int) base.length()]; int at = 0, count;
            while (at < bytes.length && (count = input.read(bytes, at, bytes.length - at)) > 0) at += count;
            if (at != bytes.length || input.read() != -1) throw new IOException("History changed while reading");
            Tbs5530HistoryRecovery.validate(bytes); return bytes;
        }
    }
    private static File history(Context context, String prefix) throws IOException {
        if (!"autonomous-v1-samsung-sm-x926b-api36".equals(prefix)) throw new IOException("Recovery is Samsung-only");
        File base = new File(context.getFilesDir(), prefix + "-attachments.txt");
        if (!base.isFile() || base.length() == 0 || base.length() > Tbs5530HistoryRecovery.MAX_BYTES
                || !base.getCanonicalFile().equals(new File(context.getFilesDir().getCanonicalFile(), base.getName())))
            throw new IOException("Existing regular history required");
        return base;
    }
    private static void requireClean(Context context, String prefix) throws IOException {
        File base = history(context, prefix);
        File marker = new File(context.getApplicationInfo().dataDir, "shared_prefs/" + prefix + "-history.xml");
        if (new File(base.getPath() + ".bak").exists() || new File(base.getPath() + ".new").exists()
                || !marker.isFile() || new File(marker.getPath() + ".bak").exists()
                || !context.getSharedPreferences(prefix + "-history", Context.MODE_PRIVATE).getBoolean("initialized", false))
            throw new IOException("History marker or AtomicFile state is uncertain");
    }
    static Tbs5530HistoryRecovery.State append(Context context, Tbs5530HistoryRecovery.Plan plan,
                                               Authorization authorization) throws IOException {
        if (plan == null || authorization == null || !"SM-X926B".equals(Build.MODEL) || Build.VERSION.SDK_INT != 36)
            throw new IOException("Exact Samsung recovery plan and independent authorization required");
        final String prefix = "autonomous-v1-samsung-sm-x926b-api36";
        final Object reservation = new Object();
        if (!Tbs5530DiagnosticController.reserveOperational(reservation)) throw new IOException("Another debug owner is active");
        try {
            synchronized (Tbs5530AutonomousSession.class) {
                Tbs5530HistoryRecovery operation = new Tbs5530HistoryRecovery(plan, new Tbs5530HistoryRecovery.Port() {
                    public void requireCurrent() throws IOException {
                        authorization.requireCurrent(); requireClean(context, prefix);
                        if (!Tbs5530AutonomousAndroidAccess.manager(context).devices().isEmpty())
                            throw new IOException("Recovery forbids an attached tuner");
                    }
                    public byte[] readCurrent() throws IOException { return readSnapshot(context, prefix); }
                    public void consume(String hash) throws IOException { authorization.consume(hash); }
                    public void appendAndSync(String expected, byte[] suffix) throws IOException {
                        requireCurrent();
                        if (!Tbs5530HistoryRecovery.hash(readCurrent()).equals(expected)) throw new IOException("History compare-and-append mismatch");
                        appendExisting(history(context, prefix), plan.baselineBytes, suffix, () -> {
                            requireCurrent();
                            if (!Tbs5530HistoryRecovery.hash(readCurrent()).equals(expected)) throw new IOException("History changed before append");
                        });
                    }
                });
                operation.apply(); return operation.state();
            }
        } finally { Tbs5530DiagnosticController.releaseOperational(reservation); }
    }
    interface Check { void require() throws IOException; }
    static void appendExisting(File base, int baselineBytes, byte[] suffix, Check check) throws IOException {
        try {
            FileDescriptor fd = Os.open(base.getPath(), OsConstants.O_WRONLY | OsConstants.O_APPEND | OsConstants.O_NOFOLLOW, 0);
            try (FileOutputStream output = new FileOutputStream(fd)) {
                StructStat opened = Os.fstat(fd), live = Os.stat(base.getPath());
                if (!OsConstants.S_ISREG(opened.st_mode) || opened.st_ino != live.st_ino || opened.st_dev != live.st_dev
                        || opened.st_size != baselineBytes) throw new IOException("History inode or size changed");
                check.require(); output.write(suffix); output.getFD().sync();
            }
        } catch (ErrnoException error) { throw new IOException("History append could not be certified", error); }
    }
    private Tbs5530HistoryRecoveryStore() { }
}
