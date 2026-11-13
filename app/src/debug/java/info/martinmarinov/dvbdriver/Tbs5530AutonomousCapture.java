// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530TsSmoke;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Exact delivered chunks only. Private session file, never embedded in JSON or the APK. */
final class Tbs5530AutonomousCapture implements Tbs5530TsSmoke.ChunkObserver, AutoCloseable {
    private final FileOutputStream output;
    private final int limit;
    private long bytes;
    private boolean closed, durable, writeFailed;
    Tbs5530AutonomousCapture(File directory, int maximumBytes) throws IOException {
        this(directory, maximumBytes, false);
    }
    Tbs5530AutonomousCapture(File directory, int maximumBytes, boolean nativeStream) throws IOException {
        this(directory, maximumBytes, nativeStream, false);
    }
    Tbs5530AutonomousCapture(File directory, int maximumBytes, boolean nativeStream, boolean b4Capture) throws IOException {
        if (b4Capture ? !nativeStream || maximumBytes != 256 * 1024 * 1024
                : nativeStream ? maximumBytes < 65536 || maximumBytes > 16 * 1024 * 1024 : maximumBytes != 65536)
            throw new IOException("Raw capture limits not allowlisted");
        File file = new File(directory, "received.ts");
        if (!file.createNewFile()) throw new IOException("Raw capture already exists; no overwrite");
        output = new FileOutputStream(file); limit = maximumBytes;
    }
    @Override public synchronized void accept(byte[] chunk, int length) throws IOException {
        if (closed || chunk == null || length < 1 || length > chunk.length || length > limit - bytes)
            throw new IOException("Raw capture bounds or lifetime changed");
        try { output.write(chunk, 0, length); bytes += length; }
        catch (IOException error) { writeFailed = true; throw error; }
    }
    synchronized long bytes() { return bytes; }
    synchronized boolean durable() { return durable; }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { output.getFD().sync(); }
        finally { output.close(); }
        durable = !writeFailed;
    }
}
