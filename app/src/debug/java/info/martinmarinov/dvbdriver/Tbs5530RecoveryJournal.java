// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Exclusive no-backup tombstone: incomplete creation remains consumed, never auto-deleted or rearmed. */
final class Tbs5530RecoveryJournal {
    private final File directory;
    Tbs5530RecoveryJournal(File noBackupRoot) throws IOException {
        directory = new File(noBackupRoot, "tbs5530-history-recovery-v1");
        if (!directory.isDirectory()) {
            if (directory.exists() || !directory.mkdir()) throw new IOException("Cannot create recovery journal");
            syncDirectory(noBackupRoot);
        }
        if (!directory.getCanonicalFile().equals(new File(noBackupRoot.getCanonicalFile(), directory.getName())))
            throw new IOException("Journal symlink refused");
    }
    synchronized boolean consumed(String plan) throws IOException { return file(plan).exists(); }
    synchronized void consume(String plan, String record) throws IOException {
        File file = file(plan);
        if (record == null || record.length() > 8192) throw new IOException("Invalid recovery journal record");
        byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
        try {
            FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, 0600);
            try (FileOutputStream output = new FileOutputStream(fd)) {
                // Persist the name first. A crash after this point blocks even when the file remains empty.
                syncDirectory(directory); output.write(bytes); output.getFD().sync();
            }
        } catch (ErrnoException error) { throw new IOException("Recovery journal consume failed; no retry",error); }
        syncDirectory(directory);
        if (!Arrays.equals(bytes, read(file))) throw new IOException("Recovery journal verification failed");
    }
    private File file(String plan) throws IOException {
        if (plan == null || !plan.matches("[a-f0-9]{64}")) throw new IOException("Invalid recovery plan digest");
        return new File(directory, plan + ".consumed.json");
    }
    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            if (file.length() > 8192) throw new IOException("Oversized journal");
            byte[] bytes = new byte[(int)file.length()]; int at=0,count;
            while (at<bytes.length && (count=input.read(bytes,at,bytes.length-at))>0) at+=count;
            if(at!=bytes.length || input.read()!=-1) throw new IOException("Journal changed"); return bytes;
        }
    }
    private static void syncDirectory(File directory) throws IOException {
        try {
            FileDescriptor fd = Os.open(directory.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
            try {
                if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw new IOException("Journal parent is not a directory");
                Os.fsync(fd);
            } finally { Os.close(fd); }
        } catch (ErrnoException error) { throw new IOException("Journal directory sync failed", error); }
    }
}
