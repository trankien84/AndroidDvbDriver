// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530StreamErrorClassificationTest {
    @Test public void successfulDataStoppedAtDeadlineIsNotUsbErrorAfterCancellationDrain() {
        assertFalse(Tbs5530AutonomousSession.completedStreamError(0, 0, 0, false));
    }
    @Test public void firstDataCompletionOrSyscallErrorIsRetainedAfterCleanDrain() {
        for (int status : new int[]{-75, -71, -19, -32})
            assertTrue(Tbs5530AutonomousSession.completedStreamError(status, 0, 0, false));
        assertTrue(Tbs5530AutonomousSession.completedStreamError(0, 19, 0, false));
    }
    @Test public void confirmedLockStopNeverMasksUsbErrorOrUncertainDrain() {
        assertTrue(Tbs5530AutonomousSession.knownB4LockStop(true, true, "WATCHDOG", 0, 0, 0, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(false, true, "WATCHDOG", 0, 0, 0, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, false, "WATCHDOG", 0, 0, 0, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, true, "WATCHDOG", -75, 0, 0, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, true, "WATCHDOG", 0, 19, 0, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, true, "WATCHDOG", 0, 0, 1, false));
        assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, true, "WATCHDOG", 0, 0, 0, true));
        for (String reason : new String[]{"DEADLINE", "BYTE_LIMIT", "STOP", null})
            assertFalse(Tbs5530AutonomousSession.knownB4LockStop(true, true, reason, 0, 0, 0, false));
    }
    @Test public void incompleteOrQuarantinedDrainIsNotCertifiedAsCompletedError() {
        assertFalse(Tbs5530AutonomousSession.completedStreamError(-75, 0, 1, false));
        assertFalse(Tbs5530AutonomousSession.completedStreamError(-75, 0, 0, true));
    }
}
