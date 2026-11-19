// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530RecordingTimelineTest {
    @Test public void samplesBoundedIntervalsAndRetainsFinalOffset() {
        Tbs5530RecordingTimeline timeline = new Tbs5530RecordingTimeline(100);
        timeline.observe(101, 1024, false);
        assertEquals(1, timeline.size());
        timeline.observe(100_000_100, 2048, false);
        timeline.observe(100_000_101, 2300, true);
        assertEquals(3, timeline.size());
        assertEquals(2300, timeline.offset(2));
        assertEquals(100_000_101, timeline.time(2));
        assertTrue(timeline.complete());
    }
    @Test public void overflowAndClockRollbackCannotClaimCompleteness() {
        Tbs5530RecordingTimeline overflow = new Tbs5530RecordingTimeline(0);
        for (int i = 1; i <= 4096; i++) overflow.observe(i * 100_000_000L, i, true);
        assertFalse(overflow.complete());
        assertEquals(4096, overflow.size());
        Tbs5530RecordingTimeline rollback = new Tbs5530RecordingTimeline(10);
        rollback.observe(9, 1, true);
        assertFalse(rollback.complete());
        Tbs5530RecordingTimeline lostBytes = new Tbs5530RecordingTimeline(0);
        lostBytes.observe(1, 2, true);
        lostBytes.observe(2, 1, true);
        assertFalse(lostBytes.complete());
    }
}
