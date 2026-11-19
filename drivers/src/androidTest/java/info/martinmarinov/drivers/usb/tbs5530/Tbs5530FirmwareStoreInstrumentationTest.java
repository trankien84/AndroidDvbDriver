// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class Tbs5530FirmwareStoreInstrumentationTest {
    @Test public void atomicImportReplacementRejectedImportAndDeletion() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File path = new File(context.getNoBackupFilesDir(), "p4-fixture-" + System.nanoTime() + ".bin");
        byte[] first = new byte[8192];
        byte[] second = new byte[8192];
        second[0] = 1;
        Tbs5530FirmwareStore store = new Tbs5530FirmwareStore(path, digest(first));
        try {
            assertFalse(store.isAvailable());
            store.importFirmware(new ByteArrayInputStream(first));
            assertArrayEquals(first, store.load());
            try {
                store.importFirmware(new ByteArrayInputStream(new byte[10]));
                fail("Accepted truncated replacement");
            } catch (IOException expected) { }
            assertArrayEquals(first, store.load());
            Tbs5530FirmwareStore replacement = new Tbs5530FirmwareStore(path, digest(second));
            replacement.importFirmware(new ByteArrayInputStream(second));
            assertArrayEquals(second, replacement.load());
            assertFalse(store.isAvailable());
            replacement.delete();
            assertFalse(replacement.isAvailable());
            assertFalse(path.exists());
        } finally {
            store.delete();
        }
    }

    private static String digest(byte[] image) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(image)) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
}
