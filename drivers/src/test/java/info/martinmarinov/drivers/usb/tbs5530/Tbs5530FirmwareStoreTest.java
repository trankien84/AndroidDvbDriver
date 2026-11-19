// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class Tbs5530FirmwareStoreTest {
    @Test public void acceptsExactDigestAndHandlesZeroLengthReads() throws Exception {
        byte[] image = new byte[8192];
        InputStream stream = new ByteArrayInputStream(image) {
            private boolean zero = true;
            @Override public synchronized int read(byte[] b, int off, int len) {
                if (zero) { zero = false; return 0; }
                return super.read(b, off, len);
            }
        };
        assertArrayEquals(image, Tbs5530FirmwareStore.readValidated(stream, digest(image)));
    }

    @Test public void rejectsEmptyShortLongAndWrongDigest() throws Exception {
        for (int size : new int[]{0, 8191, 8193, 8192}) {
            try {
                Tbs5530FirmwareStore.readValidated(new ByteArrayInputStream(new byte[size]), Tbs5530FirmwareStore.SHA256);
                fail("Accepted invalid image of length " + size);
            } catch (IOException expected) { }
        }
    }

    @Test public void neverReadsMoreThanSizePlusOne() throws Exception {
        final int[] read = {0};
        InputStream endless = new InputStream() {
            @Override public int read() { read[0]++; return 0; }
        };
        try {
            Tbs5530FirmwareStore.readValidated(endless, Tbs5530FirmwareStore.SHA256);
            fail();
        } catch (IOException expected) {
            assertEquals(8193, read[0]);
        }
    }

    @Test public void propagatesReadFailure() throws Exception {
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("fixture"); }
        };
        try {
            Tbs5530FirmwareStore.readValidated(broken, Tbs5530FirmwareStore.SHA256);
            fail();
        } catch (IOException expected) {
            assertEquals("fixture", expected.getMessage());
        }
    }

    static String digest(byte[] image) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(image)) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
}
