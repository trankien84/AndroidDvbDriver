// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;
import static org.junit.Assert.*;

/** Local files only under the build directory. No Context, Android service, firmware or USB. */
public class Tbs5530AutonomousCaptureTest {
    private File directory() throws IOException {
        File dir = new File("build", "autonomous-capture-test-" + UUID.randomUUID());
        if (!dir.mkdirs()) throw new IOException("Cannot create private test directory");
        return dir;
    }
    private void clean(File dir) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                java.nio.file.Files.deleteIfExists(new File(dir, "received.ts").toPath());
                java.nio.file.Files.delete(dir.toPath());
                return;
            } catch (java.nio.file.FileSystemException shared) {
                // Windows can briefly retain a directory handle after a file operation.
                if (attempt == 9) throw shared;
                Thread.sleep(50);
            }
        }
    }
    @Test public void writesExactPrefixesSyncsAndClosesBeforeReporting() throws Exception {
        File dir = directory();
        try {
            Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(dir, 65536);
            capture.accept(new byte[]{1, 2, 3, 99}, 3); capture.accept(new byte[]{4, 5}, 2);
            assertFalse(capture.durable()); capture.close(); assertTrue(capture.durable()); assertEquals(5, capture.bytes());
            byte[] actual = new byte[5];
            try (FileInputStream input = new FileInputStream(new File(dir, "received.ts"))) {
                assertEquals(5, input.read(actual)); assertEquals(-1, input.read());
            }
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, actual);
            assertThrows(IOException.class, () -> capture.accept(new byte[]{6}, 1)); capture.close();
            assertThrows(IOException.class, () -> new Tbs5530AutonomousCapture(dir, 65536));
        } finally { clean(dir); }
    }
    @Test public void rejectsOverflowWithoutDiscardingPreviouslyWrittenBytes() throws Exception {
        File dir = directory();
        try {
            Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(dir, 65536);
            try {
                capture.accept(new byte[65536], 65536);
                assertThrows(IOException.class, () -> capture.accept(new byte[]{1}, 1));
                assertEquals(65536, capture.bytes());
            } finally { capture.close(); }
            assertTrue(capture.durable()); assertEquals(65536, new File(dir, "received.ts").length());
        } finally { clean(dir); }
    }
    @Test public void b3WritesOnePostDrainBufferWithoutChangingLegacyLimit() throws Exception {
        File dir = directory();
        try {
            assertThrows(IOException.class, () -> new Tbs5530AutonomousCapture(dir, 4194304));
            assertThrows(IOException.class, () -> new Tbs5530AutonomousCapture(dir, 16777217, true));
            byte[] data = new byte[4194304]; data[2048] = 0x47;
            try (Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(dir, data.length, true)) {
                capture.accept(data, data.length); assertEquals(data.length, capture.bytes());
            }
            assertEquals(data.length, new File(dir, "received.ts").length());
        } finally { clean(dir); }
    }
    @Test public void rejectsInvalidInputAndUnreviewedLimitBeforeOutput() throws Exception {
        File dir = directory();
        try {
            assertThrows(IOException.class, () -> new Tbs5530AutonomousCapture(dir, 65537));
            assertFalse(new File(dir, "received.ts").exists());
            try (Tbs5530AutonomousCapture capture = new Tbs5530AutonomousCapture(dir, 65536)) {
                assertThrows(IOException.class, () -> capture.accept(null, 1));
                assertThrows(IOException.class, () -> capture.accept(new byte[1], 2));
                assertThrows(IOException.class, () -> capture.accept(new byte[1], 0));
                assertEquals(0, capture.bytes());
            }
        } finally { clean(dir); }
    }
}
