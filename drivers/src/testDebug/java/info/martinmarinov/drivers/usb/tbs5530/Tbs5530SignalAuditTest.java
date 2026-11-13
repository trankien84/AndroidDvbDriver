// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530SignalAuditTest {
    @Test public void invalidPhaseIsRejectedBeforeCreatingMonitorFile() throws Exception {
        try {
            Tbs5530SignalAudit.write(new File("missing-b5-audit-directory"), "unused",
                    Collections.emptyList(), null, "ON3");
            fail("Unknown phase accepted");
        } catch (IllegalArgumentException expected) { assertEquals("Invalid B5 phase", expected.getMessage()); }
    }
    @Test public void rssiAuditRejectsOnOrMissingPhaseBeforeAnyFileIo() throws Exception {
        for (String phase : new String[]{null, "ON1", "ON2"}) {
            try {
                Tbs5530SignalAudit.writeRssi(new File("missing-b5-audit-directory"), "unused", phase,
                        Collections.emptyList());
                fail("ON RSSI audit accepted");
            } catch (IllegalArgumentException expected) { assertEquals("RSSI_REQUIRES_OFF_PHASE", expected.getMessage()); }
        }
    }
    @Test public void existingAuditIsNeverOverwrittenEvenForEmptySamples() throws Exception {
        File directory = new File("build/test-results/b5-signal-audit-" + java.util.UUID.randomUUID());
        assertTrue(directory.mkdirs());
        File file = new File(directory, "phase31-signal-monitor-run.jsonl");
        try {
            assertTrue(file.createNewFile());
            try {
                Tbs5530SignalAudit.write(directory, "run", Collections.emptyList(), null, "ON1");
                fail("Existing audit overwritten");
            } catch (IOException expected) { assertEquals("Signal audit already exists", expected.getMessage()); }
            assertEquals(0, file.length());
        } finally { assertTrue(file.delete()); assertTrue(directory.delete()); }
    }
}
