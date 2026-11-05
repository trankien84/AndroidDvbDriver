// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** AtomicFile logs some sync/rename failures instead of throwing them to its caller. */
public final class Tbs5530DurableFile {
    interface Commit {
        void sync(FileOutputStream output) throws IOException;
        void finish(AtomicFile file, FileOutputStream output) throws IOException;
    }
    private static final Commit ANDROID = new Commit() {
        public void sync(FileOutputStream output) throws IOException { output.getFD().sync(); }
        public void finish(AtomicFile file, FileOutputStream output) { file.finishWrite(output); }
    };
    public static final class Timing {
        public final int bytes;
        public final long startNanos, writeNanos, syncNanos, finishNanos, verifyNanos;
        Timing(int bytes, long a, long b, long c, long d, long e, long f) {
            this.bytes = bytes; startNanos = b - a; writeNanos = c - b; syncNanos = d - c;
            finishNanos = e - d; verifyNanos = f - e;
        }
    }
    private Tbs5530DurableFile() { }
    public static Timing write(File path, byte[] bytes) throws IOException { return write(path, bytes, ANDROID); }
    static Timing write(File path, byte[] bytes, Commit commit) throws IOException {
        AtomicFile file = new AtomicFile(path);
        FileOutputStream output = null;
        try {
            long a = android.os.SystemClock.elapsedRealtimeNanos();
            output = file.startWrite(); long b = android.os.SystemClock.elapsedRealtimeNanos();
            output.write(bytes); long c = android.os.SystemClock.elapsedRealtimeNanos();
            commit.sync(output); long d = android.os.SystemClock.elapsedRealtimeNanos();
            commit.finish(file, output); output = null; long e = android.os.SystemClock.elapsedRealtimeNanos();
            if (!Arrays.equals(bytes, file.readFully())) throw new IOException("Atomic commit verification failed");
            return new Timing(bytes.length, a, b, c, d, e, android.os.SystemClock.elapsedRealtimeNanos());
        } catch (IOException | RuntimeException error) {
            if (output != null) {
                try { file.failWrite(output); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
    }
}
