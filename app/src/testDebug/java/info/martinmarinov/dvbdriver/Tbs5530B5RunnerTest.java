// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530B5RunnerTest {
    static class Fake implements Tbs5530B5Runner.Port {
        long time; boolean stopped, active, monitorFailure;
        int drains, on, rssi; String failPhase;
        final List<String> order = new ArrayList<>();
        public long nanos() { return time; }
        public void require() throws IOException { if (stopped) throw new IOException("STOP"); }
        public void pause(int millis) { assertTrue(millis <= 100); time += millis * 1_000_000L; }
        public void selectStream() { order.add("8A0A00"); }
        public void rssi(Tbs5530B5Runner.Phase phase) throws IOException {
            assertFalse(active); rssi++; order.add(phase.name());
            if (monitorFailure) throw new IOException("MONITOR_ERROR");
        }
        public Tbs5530B5Runner.Outcome receive(Tbs5530B5Runner.Phase phase) throws IOException {
            require(); active = true; on++; order.add(phase.name());
            time += phase.durationMillis * 1_000_000L;
            active = false;
            return new Tbs5530B5Runner.Outcome(16384, true, false, phase.name().equals(failPhase) ? "COMPLETION_STATUS" : "DEADLINE");
        }
        public void drain() { assertFalse(active); drains++; }
    }
    @Test public void fixedFourPhasesKeepOffIdleAndUseOneSelectWithStalePrefixOnlyOn1() throws Exception {
        Fake f = new Fake(); Tbs5530B5Runner runner = new Tbs5530B5Runner(); runner.run(f);
        assertEquals(Arrays.asList("8A0A00", "OFF1", "OFF1", "OFF1", "ON1", "OFF2", "OFF2", "OFF2", "ON2"), f.order);
        assertEquals(60_000_000_000L, f.time); assertEquals(6, f.rssi); assertEquals(2, f.on);
        assertEquals(4, runner.boundaries().size());
        long end = 0;
        for (Tbs5530B5Runner.Boundary b : runner.boundaries()) {
            assertEquals(end, b.startNanos); end = b.endNanos;
            assertEquals(b.phase.durationMillis * 1_000_000L, b.endNanos - b.startNanos);
            assertEquals(b.phase == Tbs5530B5Runner.Phase.ON1 ? 2048 : 0, b.phase.stalePrefixBytes());
        }
    }
    @Test public void monitorErrorsRemainRecordedAndDoNotSkipOnPhases() throws Exception {
        Fake f = new Fake(); f.monitorFailure = true;
        Tbs5530B5Runner runner = new Tbs5530B5Runner(); runner.run(f);
        assertEquals(2, f.on); assertEquals(3, runner.boundaries().get(0).rssiErrors);
        assertEquals(3, runner.boundaries().get(2).rssiErrors);
    }
    @Test public void offDemodMonitorFailuresAreRecordedWithoutCancellingPhases() throws Exception {
        Fake f = new Fake() {
            @Override public boolean monitorOff(Tbs5530B5Runner.Phase phase) throws IOException { throw new IOException("MONITOR_READ_ERROR"); }
        };
        Tbs5530B5Runner runner = new Tbs5530B5Runner(); runner.run(f);
        assertEquals(2, f.on); assertEquals(100, runner.boundaries().get(0).monitorErrors);
        assertEquals(100, runner.boundaries().get(2).monitorErrors);
    }
    @Test public void confirmedOffDemodUnlockStopsBeforeAnyOnAndDrains() {
        Fake f = new Fake() { @Override public boolean monitorOff(Tbs5530B5Runner.Phase phase) { return true; } };
        Tbs5530B5Runner runner = new Tbs5530B5Runner();
        assertThrows(IOException.class, () -> runner.run(f)); assertEquals(0, f.on); assertTrue(f.drains >= 2);
    }
    @Test public void completedUsbErrorNeverContinuesSecondOnAndAlwaysDrains() {
        Fake f = new Fake(); f.failPhase = "ON1"; Tbs5530B5Runner runner = new Tbs5530B5Runner();
        assertThrows(IOException.class, () -> runner.run(f));
        assertEquals(1, f.on); assertEquals(2, runner.boundaries().size()); assertTrue(f.drains >= 2);
    }
    @Test public void stopDuringOffDrainsAndDoesNotSubmit() {
        Fake f = new Fake() { @Override public void pause(int millis) { super.pause(millis); stopped = true; } };
        Tbs5530B5Runner runner = new Tbs5530B5Runner();
        assertThrows(IOException.class, () -> runner.run(f)); assertEquals(0, f.on); assertTrue(f.drains >= 2);
    }
    @Test public void quarantineCannotAdvanceIntoOff2() {
        Fake f = new Fake() {
            @Override public Tbs5530B5Runner.Outcome receive(Tbs5530B5Runner.Phase phase) {
                on++; return new Tbs5530B5Runner.Outcome(0, false, true, "QUARANTINED");
            }
        };
        Tbs5530B5Runner runner = new Tbs5530B5Runner();
        assertThrows(IOException.class, () -> runner.run(f)); assertEquals(2, runner.boundaries().size()); assertEquals(1, f.on);
    }
    @Test public void stoppedRunnerCannotBeRearmed() {
        Fake f = new Fake(); f.stopped = true; Tbs5530B5Runner runner = new Tbs5530B5Runner();
        assertThrows(IOException.class, () -> runner.run(f));
        f.stopped = false; assertThrows(IOException.class, () -> runner.run(f));
        assertTrue(f.order.isEmpty()); assertEquals(0, f.on);
    }
    @Test public void bulkStallStopsWholeRunAfterNativeDrain() {
        Fake f = new Fake() {
            @Override public Tbs5530B5Runner.Outcome receive(Tbs5530B5Runner.Phase phase) {
                on++; return new Tbs5530B5Runner.Outcome(0, true, false, "BULK_STALL_OVER_2_SECONDS");
            }
        };
        assertThrows(IOException.class, () -> new Tbs5530B5Runner().run(f)); assertEquals(1, f.on);
    }
}
