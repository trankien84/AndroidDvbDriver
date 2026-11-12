// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.system.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Fixed no-backup tombstone. An empty, corrupt or partial file remains consumed. No reset API. */
final class Tbs5530ReloadInitJournal {
    private final File file, root;
    Tbs5530ReloadInitJournal(File root) throws IOException {
        if (!root.isDirectory()) throw new IOException("Existing no-backup root required");
        this.root = root; file = new File(root, Tbs5530ReloadInitPlan.TOMBSTONE);
        if (!file.getCanonicalFile().equals(new File(root.getCanonicalFile(), file.getName()))) throw new IOException("Initialization journal symlink refused");
    }
    synchronized boolean consumed() throws IOException {
        try { Os.lstat(file.getPath()); return true; }
        catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return false;
            throw new IOException("Initialization journal state unavailable", error);
        }
    }
    synchronized void consume(String record) throws IOException {
        if (record == null || record.length() > 16384 || consumed()) throw new IOException("Initialization consumed or record invalid");
        byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
        try {
            FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, 0600);
            try (FileOutputStream output = new FileOutputStream(fd)) {
                syncDirectory(root); output.write(bytes); output.getFD().sync();
            }
            syncDirectory(root); requireRecord(record);
        } catch (ErrnoException error) { throw new IOException("Initialization tombstone uncertain; never retry", error); }
    }
    synchronized void requireRecord(String record) throws IOException {
        if (!Arrays.equals(record.getBytes(StandardCharsets.UTF_8), Tbs5530ReloadStore.readExisting(file)))
            throw new IOException("Initialization tombstone lost or changed; no rearm");
    }
    static void syncDirectory(File root) throws IOException {
        try {
            FileDescriptor fd = Os.open(root.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
            try { if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw new IOException("Journal parent is not a directory"); Os.fsync(fd); }
            finally { Os.close(fd); }
        } catch (ErrnoException error) { throw new IOException("Initialization journal directory sync failed", error); }
    }
}
