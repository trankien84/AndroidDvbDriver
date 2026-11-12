// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530AttachPermissionWindowTest {
    private final Tbs5530AttachPermissionWindow window = new Tbs5530AttachPermissionWindow();
    @Test public void unobservedTransitionCannotPause() { assertFalse(window.allows(1000, true, false)); }
    @Test public void verifiedDrainedTransitionAllowsThirtySecondsOnly() {
        window.observe(1000, true, true, false);
        assertTrue(window.allows(1000, true, false));
        assertTrue(window.allows(11000, true, false));
        assertTrue(window.allows(30999, true, false));
        assertFalse(window.allows(31000, true, false));
    }
    @Test public void partialOrUncertainFirmwareCannotArm() {
        window.observe(1000, false, true, false); assertFalse(window.allows(1001, true, false));
    }
    @Test public void pendingBootstrapCleanupCannotArm() {
        window.observe(1000, true, false, false); assertFalse(window.allows(1001, true, false));
    }
    @Test public void ownerCannotArmOrUsePauseException() {
        window.observe(1000, true, true, true); assertFalse(window.allows(1001, true, false));
        window.observe(1002, true, true, false); assertFalse(window.allows(1003, true, true));
    }
    @Test public void pollingCannotExtendWindow() {
        window.observe(1000, true, true, false); window.observe(30999, true, true, false);
        assertFalse(window.allows(31000, true, false));
    }
    @Test public void transitionMustStillBeExpected() {
        window.observe(1000, true, true, false); assertFalse(window.allows(1001, false, false));
    }
    @Test public void clockRollbackRejectsPause() {
        window.observe(1000, true, true, false); assertFalse(window.allows(999, true, false));
    }
    @Test public void closeCannotBeRearmedByDuplicateAttach() {
        window.observe(1000, true, true, false); window.close(); window.observe(1001, true, true, false);
        assertFalse(window.allows(1002, true, false));
    }
}
