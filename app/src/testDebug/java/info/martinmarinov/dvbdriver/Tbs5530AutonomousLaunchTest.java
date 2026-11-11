// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530AutonomousLaunchTest {
    @Test public void launchCorrelationIsNotPermissionToReplaceAnActiveWorkflow() throws Exception {
        Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); String first = UUID.randomUUID().toString();
        gate.begin(first); assertTrue(gate.matches(first)); assertFalse(gate.cleaned());
        assertThrows(IOException.class, () -> gate.begin(UUID.randomUUID().toString()));
        assertTrue(gate.certifyNoResources(0));
        gate.begin(UUID.randomUUID().toString()); assertFalse(gate.matches(first)); assertFalse(gate.cleaned());
    }
    @Test public void everyResourceOrUnknownBitRefusesTheNoResourceProof() throws Exception {
        for (int bit = 0; bit < 31; bit++) {
            Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); gate.begin(UUID.randomUUID().toString());
            assertFalse(gate.certifyNoResources(1 << bit)); assertFalse(gate.cleaned());
            assertThrows(IOException.class, () -> gate.begin(UUID.randomUUID().toString()));
        }
    }
    @Test public void acquisitionAttemptCannotBeReclassifiedByZeroByteCounters() throws Exception {
        Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); gate.begin(UUID.randomUUID().toString()); gate.acquiring();
        assertFalse(gate.certifyNoResources(0)); gate.runtimeCleanup(false); assertFalse(gate.cleaned());
        assertThrows(IOException.class, () -> gate.begin(UUID.randomUUID().toString()));
        gate.runtimeCleanup(true); assertTrue(gate.cleaned()); gate.begin(UUID.randomUUID().toString());
    }
    @Test public void consumedLaunchCannotBeRearmedAfterCleanup() throws Exception {
        Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); String token = UUID.randomUUID().toString();
        gate.begin(token); gate.certifyNoResources(0);
        assertThrows(IOException.class, () -> gate.begin(token)); assertThrows(IOException.class, () -> gate.requireFresh(token));
        assertTrue(gate.matches(token));
    }
    @Test public void malformedOrUnboundedLaunchesFailClosed() throws Exception {
        for (String token : new String[]{"", "../../grant", "00000000-0000-0000-0000-000000000000", "11111111-1111-4111-8111-11111111111X"}) {
            Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); assertFalse(Tbs5530AutonomousLaunch.valid(token));
            assertThrows(IOException.class, () -> gate.begin(token));
        }
        assertFalse(Tbs5530AutonomousLaunch.valid(null));
        Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch();
        for (int i = 0; i < 16; i++) { gate.begin(UUID.randomUUID().toString()); assertTrue(gate.certifyNoResources(0)); }
        assertThrows(IOException.class, () -> gate.begin(UUID.randomUUID().toString()));
    }
    @Test public void legacyInitialLaunchStillRequiresCleanupAndCannotAcquireAfterCertification() throws Exception {
        Tbs5530AutonomousLaunch gate = new Tbs5530AutonomousLaunch(); gate.begin(null);
        assertFalse(gate.matches(null)); assertTrue(gate.certifyNoResources(0));
        assertThrows(IOException.class, gate::acquiring); gate.begin(UUID.randomUUID().toString()); gate.acquiring();
    }
}
