// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure report revision checks; never creates an Activity, USB owner or approval. */
public class Tbs5530BootstrapReportTest {
    @Test public void everyCriticalBoundaryChangesRevisionButPollingDurationDoesNot() throws Exception {
        JSONObject report = new JSONObject(); report.put("operation", "DEBUG_ONE_SHOT_FX2");
        String first = Tbs5530UsbDiagnosticActivity.bootstrapRevision(report);
        for (String field : new String[]{"run_epoch", "state", "planned_ordinal", "guard_requested", "guard_accepted",
                "native_call_started", "native_call_completed", "full_length_completed", "image_bytes_full_length",
                "pending", "cleanup_pending", "failure", "cleanup_failure"}) {
            JSONObject changed = new JSONObject(report.toString()); changed.put(field, "changed");
            assertNotEquals(field, first, Tbs5530UsbDiagnosticActivity.bootstrapRevision(changed));
        }
        report.put("duration_ms", 123); report.put("timestamp_epoch_ms", 456);
        assertEquals(first, Tbs5530UsbDiagnosticActivity.bootstrapRevision(report));
        report.put("operation", "RAW_DESCRIPTOR_INSPECTION");
        assertNull(Tbs5530UsbDiagnosticActivity.bootstrapRevision(report));
    }
}
