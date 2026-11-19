// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;

import androidx.annotation.Keep;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Fixed TBS5530 RAM reload; no generic native vendor-control API or retries. */
public final class NativeUsbfsControl {
    private static final String HASH = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc";
    private NativeUsbfsControl() { }

    @Keep
    public interface Guard {
        /** Called before every transfer; throw to latch detach/cancellation without retry. */
        void require() throws IOException;
    }

    /**
     * Runs exactly the diagnostic's 132 A0 writes, stopping at the first short/error
     * result. Caller owns the connection and verifies device identity and the firmware digest,
     * excludes concurrent controls/close, and handles settling/re-enumeration.
     * This method neither claims nor closes the connection and never retries.
     */
    public static void reloadFx2(UsbDeviceConnection connection, byte[] image) throws IOException {
        reloadFx2(connection, image, () -> { });
    }

    public static void reloadFx2(UsbDeviceConnection connection, byte[] image, Guard guard) throws IOException {
        if (connection == null || guard == null) throw new IOException("Owned TBS5530 connection and guard required");
        Guard checked = () -> {
            if (Thread.currentThread().isInterrupted()) throw new IOException("FX2 reload interrupted; no retry");
            guard.require();
        };
        checked.require();
        byte[] verified = validateImage(image);
        int fd = connection.getFileDescriptor();
        if (fd < 0) throw new IOException("TBS5530 connection is closed");
        try {
            System.loadLibrary("UsbXfer");
            long[] result = nativeReload(fd, verified, checked);
            if (result == null || result.length != 4) throw new IOException("Missing native reload result; no retry");
            if (result[0] != 132 || result[1] != 132 || result[2] != 1 || result[3] != 0) {
                throw new IOException("FX2 reload incomplete; no retry (completed=" + result[0]
                        + ", ordinal=" + result[1] + ", result=" + result[2] + ", errno=" + result[3] + ")");
            }
        } catch (LinkageError e) {
            throw new IOException("Native FX2 reload unavailable", e);
        }
    }

    static byte[] validateImage(byte[] image) throws IOException {
        if (image == null || image.length != 8192) throw new IOException("FX2 requires exact 8192-byte pinned image");
        byte[] snapshot = image.clone();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(snapshot);
            for (int i = 0; i < digest.length; ++i) {
                int expected = Integer.parseInt(HASH.substring(i * 2, i * 2 + 2), 16);
                if ((digest[i] & 255) != expected) throw new IOException("FX2 firmware SHA-256 mismatch");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        return snapshot;
    }
    private static native long[] nativeReload(int fd, byte[] image, Guard guard) throws IOException;
}
