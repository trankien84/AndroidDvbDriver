// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.os.Build;
import android.system.*;
import info.martinmarinov.drivers.usb.tbs5530.*;
import java.io.*;
import java.util.Arrays;

/** No auto-init, reset, exported Activity or intent. Missing journal, anchor or marker is terminal. */
final class Tbs5530ReloadStore implements Tbs5530ReloadLedger.Storage {
    private final File journal, anchor, initialized;
    private static final byte[] INITIALIZED = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.INITIALIZED_HEADER);
    Tbs5530ReloadStore(Context context) throws IOException {
        if (!"SM-X926B".equals(Build.MODEL) || Build.VERSION.SDK_INT != 36) throw new IOException("B2 store is Samsung/API36 only");
        journal = new File(context.getNoBackupFilesDir(), Tbs5530ReloadLedger.FILE);
        anchor = new File(context.getNoBackupFilesDir(), Tbs5530ReloadLedger.ANCHOR);
        initialized = new File(context.getNoBackupFilesDir(), "fx2-reload-v1.initialized");
    }
    private void requireInitialized() throws IOException {
        if (!Arrays.equals(INITIALIZED, readExisting(initialized))) throw new IOException("Reload initialization marker lost or changed; no rearm");
    }
    public byte[] read() throws IOException { requireInitialized(); return readExisting(journal); }
    public byte[] anchor() throws IOException { requireInitialized(); return readExisting(anchor); }
    static byte[] readExisting(File file) throws IOException {
        try {
            FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
            try (FileInputStream input = new FileInputStream(fd)) {
                StructStat stat = Os.fstat(fd), live = Os.lstat(file.getPath());
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_ino != live.st_ino || stat.st_dev != live.st_dev
                        || stat.st_size < 1 || stat.st_size > 1024 * 1024) throw new IOException("Existing nonempty reload regular file required");
                byte[] bytes = new byte[(int)stat.st_size]; int at = 0, n;
                while (at < bytes.length && (n = input.read(bytes, at, bytes.length - at)) > 0) at += n;
                if (at != bytes.length || input.read() != -1) throw new IOException("Reload store changed while reading");
                return bytes;
            }
        } catch (ErrnoException error) { throw new IOException("Reload store unavailable; never initialize on missing", error); }
    }
    public void appendAndSync(byte[] expected, byte[] expectedAnchor, byte[] suffix, byte[] anchorSuffix) throws IOException {
        synchronized (Tbs5530AutonomousSession.class) {
            if (!Arrays.equals(expected, read()) || !Arrays.equals(expectedAnchor, anchor())) throw new IOException("Reload hash fence changed");
            Tbs5530HistoryRecoveryStore.appendExisting(journal, expected.length, suffix, () -> {
                if (!Arrays.equals(expected, read()) || !Arrays.equals(expectedAnchor, anchor())) throw new IOException("Reload compare-and-append mismatch");
            });
            byte[] next = Arrays.copyOf(expected, expected.length + suffix.length);
            System.arraycopy(suffix, 0, next, expected.length, suffix.length);
            Tbs5530HistoryRecoveryStore.appendExisting(anchor, expectedAnchor.length, anchorSuffix, () -> {
                if (!Arrays.equals(next, read()) || !Arrays.equals(expectedAnchor, anchor())) throw new IOException("Reload anchor compare-and-append mismatch");
            });
        }
    }
    static void requireNeverInitialized(Context context) throws IOException {
        Tbs5530ReloadStore store = new Tbs5530ReloadStore(context);
        for (File file : new File[]{store.initialized, store.journal, store.anchor}) {
            try { Os.lstat(file.getPath()); throw new IOException("Reload file already exists; no initialization or rearm"); }
            catch (ErrnoException error) { if (error.errno != OsConstants.ENOENT) throw new IOException("Reload file state uncertain", error); }
        }
    }
    interface InitializationApproval {
        void requireExact(String templateSha256, String anchorSha256, String markerSha256, String historySha256) throws IOException;
        void consume() throws IOException;
    }
    /** A future separately reviewed D caller must supply approval; ordinary session launch cannot call this. */
    static void initializeApproved(Context context, InitializationApproval approval) throws IOException {
        Tbs5530ReloadStore store = new Tbs5530ReloadStore(context);
        Object reservation = new Object();
        if (approval == null || !Tbs5530DiagnosticController.reserveOperational(reservation)) throw new IOException("Independent initialization approval required");
        try {
            synchronized (Tbs5530AutonomousSession.class) {
                byte[] header = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER), anchor = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER);
                String history = Tbs5530ReloadLedger.hash(Tbs5530HistoryRecoveryStore.readSnapshot(context, "autonomous-v1-samsung-sm-x926b-api36"));
                if (!Tbs5530ReloadLedger.HEADER.contains("INITIAL_HISTORY_SHA256 " + history + "\n")
                        || !Tbs5530AutonomousAndroidAccess.manager(context).devices().isEmpty()
                        || store.journal.exists() || store.anchor.exists() || store.initialized.exists()) throw new IOException("Initialization requires exact historic history, no USB and both files never created");
                requireNeverInitialized(context);
                approval.requireExact(Tbs5530ReloadLedger.hash(header), Tbs5530ReloadLedger.hash(anchor), Tbs5530ReloadLedger.hash(INITIALIZED), history);
                approval.consume();
                Tbs5530HistoryRecoveryStore.Check current = () -> {
                    approval.requireExact(Tbs5530ReloadLedger.hash(header), Tbs5530ReloadLedger.hash(anchor), Tbs5530ReloadLedger.hash(INITIALIZED), history);
                    if (!Tbs5530AutonomousAndroidAccess.manager(context).devices().isEmpty()
                            || !history.equals(Tbs5530ReloadLedger.hash(Tbs5530HistoryRecoveryStore.readSnapshot(context, "autonomous-v1-samsung-sm-x926b-api36"))))
                        throw new IOException("Initialization no-tuner/history guard changed");
                };
                current.require(); createExclusive(store.initialized, INITIALIZED);
                current.require(); createExclusive(store.journal, header);
                current.require(); createExclusive(store.anchor, anchor);
                current.require();
                Tbs5530ReloadLedger.validate(store.read(), store.anchor());
            }
        } finally { Tbs5530DiagnosticController.releaseOperational(reservation); }
    }
    private static void createExclusive(File file, byte[] bytes) throws IOException {
        try {
            FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, 0600);
            try (FileOutputStream output = new FileOutputStream(fd)) { output.write(bytes); output.getFD().sync(); }
            FileDescriptor parent = Os.open(file.getParent(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
            try {
                if (!OsConstants.S_ISDIR(Os.fstat(parent).st_mode)) throw new IOException("Reload parent must be a directory");
                Os.fsync(parent);
            } finally { Os.close(parent); }
        } catch (ErrnoException error) { throw new IOException("Exclusive reload initialization uncertain; never retry/reset", error); }
    }
}
