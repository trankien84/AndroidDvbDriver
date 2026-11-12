// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.IOException;
import java.security.MessageDigest;

/** Debug-only fixed 132-step capability; JNI derives all setup fields from the ordinal. */
public final class NativeFx2Control implements AutoCloseable {
    private long handle;
    private final Thread worker = Thread.currentThread();
    private boolean stopped;
    public NativeFx2Control(int fd, byte[] image) throws IOException {
        try {
            if (fd < 0 || image == null || image.length != 8192) throw new IOException("Exact owned fd and image required");
            image = image.clone();
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(image)) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (!"b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc".equals(hex.toString()))
                throw new IOException("Native FX2 image hash mismatch");
            System.loadLibrary("Tbs5530UsbfsDiagnostic");
            handle = create(fd, image);
            if (handle == 0) throw new IOException("Native FX2 capability allocation failed");
        } catch (java.security.NoSuchAlgorithmException | LinkageError error) { throw new IOException("Native FX2 unavailable", error); }
    }
    public long[] writeNext() throws IOException {
        if (Thread.currentThread() != worker || stopped || handle == 0) throw new IOException("FX2 capability unavailable; no retry");
        long[] boundary;
        try { boundary = next(handle); }
        catch (RuntimeException | Error error) { stopped = true; throw error; }
        if (boundary == null || boundary.length != 7) { stopped = true; throw new IOException("Missing native FX2 boundary"); }
        if (boundary[3] != boundary[2] || boundary[4] != 0 || boundary[0] == 132) stopped = true;
        return boundary;
    }
    @Override public void close() throws IOException {
        if (Thread.currentThread() != worker) throw new IOException("FX2 disposal must use the drained worker");
        stopped = true;
        if (handle != 0) { destroy(handle); handle = 0; }
    }
    private static native long create(int fd, byte[] image);
    private static native long[] next(long handle);
    private static native void destroy(long handle);
}
