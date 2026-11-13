// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530SignalMonitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Tbs5530B4SignalReportTest {
    @Test public void failedUnfreezeCannotPublishQualifiedSnrOrLock() throws Exception {
        Method write = Class.forName("info.martinmarinov.drivers.usb.tbs5530.Tbs5530SignalAudit")
                .getDeclaredMethod("write", File.class, String.class, java.util.List.class, String.class);
        write.setAccessible(true);
        for (boolean failCleanup : new boolean[]{false, true}) {
            final int[] register = {0};
            Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor((type, request, value, index, bytes, offset, length, timeout) -> {
                if (request == 0x80 && bytes[2] == 1 && bytes[3] == 0 && failCleanup)
                    throw new IOException("Synthetic unfreeze failure");
                if (request == 0x90) register[0] = bytes[2] & 255;
                if (request == 0x91) {
                    java.util.Arrays.fill(bytes, (byte)0);
                    if (register[0] == 0x10) bytes[0] = 0x26;
                    if (register[0] == 0x28) { bytes[0] = 3; bytes[1] = 0x70; }
                }
                return length;
            }, System::nanoTime);
            Tbs5530SignalMonitor.Sample sample = monitor.sample();
            File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                    "b4-signal-test-" + UUID.randomUUID());
            assertTrue(directory.mkdir());
            File audit = new File(directory, "phase31-signal-monitor-fixture.jsonl");
            try {
                write.invoke(null, directory, "fixture", Collections.singletonList(sample), null);
                JSONObject row;
                try (BufferedReader input = new BufferedReader(new FileReader(audit))) { row = new JSONObject(input.readLine()); }
                assertEquals("CLOCK_MONOTONIC", row.getString("clock"));
                assertEquals(880, row.getInt("snr_raw"));
                assertEquals(20.75555060468097, row.getDouble("snr_db_observed"), 0.000001);
                assertTrue(row.isNull("ber"));
                assertEquals(!failCleanup, row.getBoolean("cleanup_succeeded"));
                if (failCleanup) {
                    assertTrue(row.isNull("snr_db")); assertTrue(row.isNull("snr_db_curve"));
                    assertEquals("UNKNOWN", row.getString("lock"));
                    assertEquals("UNKNOWN", row.getString("demod_lock"));
                    assertEquals(1, row.getJSONArray("errors").length());
                    assertEquals("UNFREEZE", row.getJSONArray("errors").getJSONObject(0).getString("operation"));
                    assertEquals("MONITOR_IO_ERROR", row.getJSONArray("errors").getJSONObject(0).getString("code"));
                    assertFalse(row.getBoolean("stop_for_lock_loss"));
                } else {
                    assertEquals(20.75555060468097, row.getDouble("snr_db"), 0.000001);
                    assertEquals(row.getDouble("snr_db"), row.getDouble("snr_db_curve"), 0.0);
                    assertEquals("LOCKED", row.getString("lock"));
                    assertEquals("LOCKED", row.getString("demod_lock"));
                    assertEquals(0, row.getJSONArray("errors").length());
                }
            } finally { assertTrue(audit.delete()); assertTrue(directory.delete()); }
        }
    }
}
