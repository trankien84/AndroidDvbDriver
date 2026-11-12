// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Durable boundary audit only; never a grant, consumed ledger, or replacement for authority checks. */
final class Tbs5530AppendAudit {
    private final File file;
    private byte[] expected = new byte[0];
    private long device, inode;
    private boolean created, faulted;
    Tbs5530AppendAudit(File file) { this.file = file; }
    synchronized void write(byte[] next) throws IOException {
        try {
            if (faulted || next == null || next.length <= expected.length || next.length > 1024 * 1024
                    || !Arrays.equals(expected, Arrays.copyOf(next, expected.length)))
                throw new IOException("Audit must extend its exact committed prefix");
            int flags = OsConstants.O_WRONLY | OsConstants.O_APPEND | OsConstants.O_NOFOLLOW;
            if (!created) flags |= OsConstants.O_CREAT | OsConstants.O_EXCL;
            FileDescriptor fd = Os.open(file.getPath(), flags, 0600);
            try (FileOutputStream output = new FileOutputStream(fd)) {
                StructStat stat = Os.fstat(fd), live = Os.lstat(file.getPath());
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_dev != live.st_dev || stat.st_ino != live.st_ino
                        || stat.st_size != expected.length || created && (stat.st_dev != device || stat.st_ino != inode))
                    throw new IOException("Audit inode or size changed");
                if (!created) {
                    device = stat.st_dev; inode = stat.st_ino;
                    syncParent(); created = true;
                }
                if (!Arrays.equals(expected, read())) throw new IOException("Audit prefix changed before append");
                output.write(next, expected.length, next.length - expected.length);
                output.getFD().sync();
            }
            if (!Arrays.equals(next, read())) throw new IOException("Audit append not verified");
            expected = next.clone();
        } catch (ErrnoException | IOException | RuntimeException error) {
            faulted = true;
            throw new IOException("Boundary audit commit uncertain; no retry or rollback", error);
        }
    }
    private void syncParent() throws ErrnoException {
        FileDescriptor parent = Os.open(file.getParent(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
        try { Os.fsync(parent); } finally { Os.close(parent); }
    }
    private byte[] read() throws IOException, ErrnoException {
        FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
        try (FileInputStream input = new FileInputStream(fd)) {
            StructStat stat = Os.fstat(fd), live = Os.lstat(file.getPath());
            if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_dev != device || stat.st_ino != inode
                    || stat.st_dev != live.st_dev || stat.st_ino != live.st_ino || stat.st_size < 0 || stat.st_size > 1024 * 1024)
                throw new IOException("Audit read identity changed");
            byte[] bytes = new byte[(int) stat.st_size]; int at = 0, count;
            while (at < bytes.length && (count = input.read(bytes, at, bytes.length - at)) > 0) at += count;
            if (at != bytes.length || input.read() != -1) throw new IOException("Audit changed while reading");
            return bytes;
        }
    }
}
