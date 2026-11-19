// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource;
import static org.junit.Assert.*;

public class Tbs5530DumpDiagnosticsTest {
    @Test public void sidecarPreservesRealWindowAndDoesNotInventMissingMetrics() throws Exception {
        File ts = File.createTempFile("p4-dump-", ".ts", InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        File sidecar = new File(ts.getPath() + ".usb.json");
        try {
            try (FileOutputStream out = new FileOutputStream(ts)) { out.write(new byte[188]); }
            Tbs5530RecordingTimeline timeline = new Tbs5530RecordingTimeline(100);
            timeline.observe(1000, 188, true);
            Constructor<NativeUsbfsByteSource.CompletionSnapshot> constructor = NativeUsbfsByteSource.CompletionSnapshot.class
                    .getDeclaredConstructor(long[].class, long.class, boolean.class);
            constructor.setAccessible(true);
            NativeUsbfsByteSource.CompletionSnapshot snapshot = constructor.newInstance(
                    new long[]{65536, 1, 0, 0, 1, 1, 0, 2000, 500, 0, 188}, 2049L, true);
            Tbs5530DumpDiagnostics.write(ts, 100, 1000, 506000000, 8000000, snapshot, timeline);
            byte[] bytes = new byte[(int) sidecar.length()];
            try (FileInputStream input = new FileInputStream(sidecar)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) fail("Truncated sidecar");
                    offset += count;
                }
            }
            JSONObject json = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(json.getBoolean("completions_complete"));
            assertTrue(json.getBoolean("consumer_offsets_complete"));
            assertEquals(188, json.getLong("ts_size"));
            assertTrue(json.getBoolean("prefix_locked"));
            assertEquals(2049, json.getLong("discarded_prefix_bytes"));
            assertEquals(1, json.getJSONArray("completions").length());
            assertEquals(188, json.getJSONArray("consumer_offsets").getJSONObject(1).getLong("byte_offset"));
            assertTrue(json.isNull("metrics"));
        } finally {
            assertTrue(!sidecar.exists() || sidecar.delete());
            assertTrue(ts.delete());
        }
    }
}
