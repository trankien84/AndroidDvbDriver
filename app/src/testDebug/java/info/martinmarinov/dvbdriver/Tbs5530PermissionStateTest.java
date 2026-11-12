// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static info.martinmarinov.dvbdriver.Tbs5530PermissionState.*;

public class Tbs5530PermissionStateTest {
    private final Device device = new Device("synthetic-a", 1, VID, PID);
    private final Device other = new Device("synthetic-b", 2, VID, PID);
    private final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private Attempt pending() throws IOException { return permission.begin(device, false, 1000); }
    private Callback grant(Attempt attempt) { return permission.callback(attempt.token, attempt.generation, device, true); }

    @Test public void exactTargetOnly() {
        assertTrue(target(device)); assertFalse(target(null));
        assertFalse(target(new Device("synthetic", 1, VID, PID + 1)));
        assertFalse(target(new Device("synthetic", 1, VID + 1, PID)));
    }
    @Test public void enumerationIgnoresOtherDrivers() throws Exception {
        assertEquals(device, single(Arrays.asList(new Device("legacy", 9, 1, 2), device)));
        assertNull(single(Collections.emptyList()));
    }
    @Test public void multipleMatchingAttachmentsFailClosed() {
        assertThrows(IOException.class, () -> single(Arrays.asList(device, other)));
        assertThrows(IOException.class, () -> single(Arrays.asList(device, device)));
    }
    @Test public void existingOsPermissionNeedsNoDialog() throws Exception {
        permission.begin(device, true, 1000);
        assertEquals(Outcome.AUTO_PERMISSION_HANDLED_BY_OS, permission.outcome());
        assertFalse(permission.pending()); assertFalse(permission.mayPause(false));
        permission.confirmFresh(device, true); permission.requireReady(device);
    }
    @Test public void pendingPermissionNeverAllowsIo() throws Exception {
        pending(); assertEquals(Outcome.USER_ACTION_REQUIRED, permission.outcome());
        assertThrows(IOException.class, () -> permission.requireReady(device));
    }
    @Test public void pendingCannotBeReRequested() throws Exception {
        pending(); assertThrows(IOException.class, () -> permission.begin(device, false, 1001));
    }
    @Test public void grantCallbackIsNotOsPermissionOrUiProof() throws Exception {
        assertEquals(Callback.ACCEPTED, grant(pending()));
        assertEquals(Outcome.UNVERIFIED, permission.outcome());
        assertThrows(IOException.class, () -> permission.requireReady(device));
        assertThrows(IOException.class, () -> permission.confirmFresh(device, false));
    }
    @Test public void grantThenFreshEnumerationAndPermissionAllowsIo() throws Exception {
        grant(pending()); permission.confirmFresh(device, true); permission.requireReady(device);
        assertEquals(State.GRANTED, permission.state()); assertEquals(Outcome.UNVERIFIED, permission.outcome());
    }
    @Test public void denialCannotBeRetriedOrOverriddenByGrant() throws Exception {
        Attempt attempt = pending();
        assertEquals(Callback.DENIED, permission.callback(attempt.token, attempt.generation, device, false));
        assertEquals(Callback.IGNORED, grant(attempt));
        assertEquals(Outcome.PERMISSION_DENIED, permission.outcome());
        assertThrows(IOException.class, () -> permission.begin(device, true, 1001));
        permission.cancel(); assertEquals(Outcome.PERMISSION_DENIED, permission.outcome());
    }
    @Test public void duplicateCallbackCannotReverseDecision() throws Exception {
        Attempt attempt = pending(); grant(attempt);
        assertEquals(Callback.IGNORED, permission.callback(attempt.token, attempt.generation, device, false));
        permission.confirmFresh(device, true);
    }
    @Test public void staleTokenOrGenerationCannotCompletePending() throws Exception {
        Attempt attempt = pending();
        assertEquals(Callback.IGNORED, permission.callback("old-token", attempt.generation, device, true));
        assertEquals(Callback.IGNORED, permission.callback(attempt.token, attempt.generation + 1, device, true));
        assertTrue(permission.pending());
    }
    @Test public void matchingTokenWithDifferentDeviceInvalidates() throws Exception {
        Attempt attempt = pending();
        assertEquals(Callback.INVALIDATED, permission.callback(attempt.token, attempt.generation, other, true));
        assertEquals(State.INVALIDATED, permission.state());
    }
    @Test public void missingPermissionResultInvalidates() throws Exception {
        Attempt attempt = pending();
        assertEquals(Callback.INVALIDATED, permission.callback(attempt.token, attempt.generation, device, null));
    }
    @Test public void cancellationRejectsLateCallbackAndIo() throws Exception {
        Attempt attempt = pending(); permission.cancel();
        assertEquals(Callback.IGNORED, grant(attempt));
        assertThrows(IOException.class, () -> permission.requireReady(device));
        assertEquals(State.CANCELLED, permission.state());
    }
    @Test public void detachAndSamePathIdAbaRejectsOldPermission() throws Exception {
        Attempt attempt = pending(); permission.invalidate();
        Device reused = new Device(device.path, device.id, VID, PID);
        assertEquals(Callback.IGNORED, permission.callback(attempt.token, attempt.generation, reused, true));
        assertThrows(IOException.class, () -> permission.confirmFresh(reused, true));
    }
    @Test public void duplicateAttachIsAmbiguousNotProofOfSameGeneration() throws Exception {
        permission.begin(device, true, 1000);
        assertEquals(Attach.AMBIGUOUS_DUPLICATE, permission.attach(device));
        assertThrows(IOException.class, () -> permission.requireReady(device));
    }
    @Test public void differentAttachHasDistinctMismatchResult() throws Exception {
        pending(); assertEquals(Attach.MISMATCH, permission.attach(other));
    }
    @Test public void unrelatedDeviceDoesNotClaimOrInvalidateTbsPermission() throws Exception {
        pending(); assertEquals(Attach.UNRELATED, permission.attach(new Device("legacy", 9, 1, 2)));
        assertTrue(permission.pending());
    }
    @Test public void attachBeforeSelectionIsOnlyDiscoveryHint() {
        assertEquals(Attach.UNSELECTED, permission.attach(device));
        assertEquals(State.UNVERIFIED, permission.state());
    }
    @Test public void firmwareTransitionRequiresFreshAttemptEvenWhenPathReused() throws Exception {
        Attempt old = permission.begin(device, true, 1000);
        permission.firmwareTransition(); Attempt fresh = permission.begin(device, false, 2000);
        assertNotEquals(old.token, fresh.token); assertTrue(fresh.generation > old.generation);
        assertEquals(Callback.IGNORED, grant(old)); assertTrue(permission.pending());
        assertThrows(IOException.class, () -> permission.requireReady(device));
        grant(fresh); permission.confirmFresh(device, true); permission.requireReady(device);
    }
    @Test public void firmwareTransitionMayUseFreshOsGrantWithoutPrompt() throws Exception {
        permission.begin(device, true, 1000); permission.firmwareTransition();
        permission.begin(other, true, 2000); permission.confirmFresh(other, true);
        assertEquals(Outcome.AUTO_PERMISSION_HANDLED_BY_OS, permission.outcome());
    }
    @Test public void firmwareCannotResetDeniedPendingOrCancelledAttempt() throws Exception {
        pending(); assertThrows(IOException.class, permission::firmwareTransition);
        permission.cancel(); assertThrows(IOException.class, permission::firmwareTransition);
    }
    @Test public void pauseExceptionIsOnlyDispatchedRequestWithoutOwner() throws Exception {
        Attempt attempt = pending(); assertFalse(permission.mayPause(false));
        permission.dispatched(attempt); assertTrue(permission.mayPause(false)); assertFalse(permission.mayPause(true));
        grant(attempt); assertFalse(permission.mayPause(false));
    }
    @Test public void receiptOnlyLabelsVerifiedUiNeverGrantsAccess() throws Exception {
        Attempt attempt = pending();
        assertTrue(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1001, 1002));
        assertEquals(Outcome.USER_ACTION_REQUIRED, permission.outcome());
        assertThrows(IOException.class, () -> permission.requireReady(device));
        grant(attempt); assertEquals(Outcome.UNVERIFIED, permission.outcome());
        permission.confirmFresh(device, true); permission.requireReady(device);
        assertEquals(Outcome.AUTO_PERMISSION_UI_HANDLED, permission.outcome());
    }
    @Test public void lateMatchingReceiptMayLabelGrantWithoutGrantingAnything() throws Exception {
        Attempt attempt = pending(); grant(attempt); permission.confirmFresh(device, true);
        assertTrue(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1001, 1002));
        assertEquals(Outcome.AUTO_PERMISSION_UI_HANDLED, permission.outcome());
    }
    @Test public void receiptAfterStopMayLabelPriorVerifiedGrantButNeverReopen() throws Exception {
        Attempt attempt = pending(); grant(attempt); permission.confirmFresh(device, true); permission.cancel();
        assertTrue(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1001, 1002));
        assertEquals(Outcome.AUTO_PERMISSION_UI_HANDLED, permission.outcome());
        assertEquals(State.CANCELLED, permission.state());
        assertThrows(IOException.class, () -> permission.requireReady(device));
    }
    @Test public void receiptAfterPendingCancellationCannotClaimUiGrant() throws Exception {
        Attempt attempt = pending(); permission.cancel();
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1001, 1002));
    }
    @Test public void staleFutureOrPreRequestReceiptIsRejected() throws Exception {
        Attempt attempt = pending();
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, device, 999, 1001, 1002));
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, device, 1001, 1000, 1002));
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1003, 1002));
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, device, 1000, 1001, 31001));
        assertFalse(permission.uiReceipt("old", attempt.generation, device, 1000, 1001, 1002));
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation + 1, device, 1000, 1001, 1002));
        assertFalse(permission.uiReceipt(attempt.token, attempt.generation, other, 1000, 1001, 1002));
    }
    @Test public void permissionRevocationFailsClosedWithoutAnotherDialog() throws Exception {
        permission.begin(device, true, 1000);
        assertThrows(IOException.class, () -> permission.confirmFresh(device, false));
        assertEquals(State.INVALIDATED, permission.state()); assertFalse(permission.pending());
    }
    @Test public void postCallbackEnumerationMismatchNeverOpens() throws Exception {
        grant(pending()); assertThrows(IOException.class, () -> permission.confirmFresh(other, true));
        assertEquals(State.INVALIDATED, permission.state());
    }
}
