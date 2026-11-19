// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Validated, app-private firmware supplied by the user; never an APK asset. */
public final class Tbs5530FirmwareStore {
    public static final int SIZE = 8192;
    public static final String SHA256 = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc";
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    private final String expectedDigest;

    public Tbs5530FirmwareStore(Context context) {
        this(new File(context.getNoBackupFilesDir(), "tbs5530-firmware.bin"), SHA256);
    }

    Tbs5530FirmwareStore(File location, String expectedDigest) {
        file = new AtomicFile(location);
        this.expectedDigest = expectedDigest;
    }

    public byte[] load() throws IOException {
        synchronized (LOCK) {
            try (InputStream input = file.openRead()) {
                return readValidated(input, expectedDigest);
            }
        }
    }

    public boolean isAvailable() {
        try {
            load();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void importFirmware(InputStream input) throws IOException {
        byte[] image = readValidated(input, expectedDigest);
        synchronized (LOCK) {
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                output.write(image);
                file.finishWrite(output);
            } catch (IOException | RuntimeException e) {
                if (output != null) file.failWrite(output);
                throw e;
            }
        }
    }

    public void delete() throws IOException {
        synchronized (LOCK) {
            file.delete();
            if (file.getBaseFile().exists()
                    || new File(file.getBaseFile().getPath() + ".bak").exists()) {
                throw new IOException("Cannot delete TBS5530 firmware");
            }
        }
    }

    static byte[] readValidated(InputStream input, String expectedDigest) throws IOException {
        if (input == null) throw new IOException("Firmware input is unavailable");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(SIZE);
        byte[] buffer = new byte[1024];
        while (bytes.size() <= SIZE) {
            int count = input.read(buffer, 0, Math.min(buffer.length, SIZE + 1 - bytes.size()));
            if (count < 0) break;
            if (count == 0) {
                int one = input.read();
                if (one < 0) break;
                bytes.write(one);
            } else {
                bytes.write(buffer, 0, count);
            }
            if (bytes.size() > SIZE) throw new IOException("Firmware must contain exactly 8192 bytes");
        }
        byte[] image = bytes.toByteArray();
        if (image.length != SIZE) throw new IOException("Firmware must contain exactly 8192 bytes");
        if (!digest(image).equals(expectedDigest)) throw new IOException("Firmware SHA-256 does not match the original TBS image");
        return image;
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) {
                result.append("0123456789abcdef".charAt((value & 255) >>> 4));
                result.append("0123456789abcdef".charAt(value & 15));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
