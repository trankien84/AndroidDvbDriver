// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530SameSessionHandoffTest {
    @Test public void selectedOperationalAttachDuringPermissionMustPreserveAttempt() throws Exception {
        Device bootstrap = new Device("fake-default", 1, 0x734c, 0x5530);
        Device operational = new Device("fake-operational", 2, 0x734c, 0x5530);
        Tbs5530PermissionState permission = new Tbs5530PermissionState();
        permission.begin(bootstrap, true, 1000);
        permission.firmwareTransition();
        Tbs5530SameSessionHandoff handoff = new Tbs5530SameSessionHandoff("fake-session", "fake-epoch");
        handoff.select(operational);
        Tbs5530PermissionState.Attempt attempt = permission.begin(operational, false, 2000);
        permission.dispatched(attempt);
        handoff.attach(permission, new Device(operational.path, operational.id, 0x734c, 0x5530), "fake-session", "fake-epoch");
        assertSame(attempt, permission.attempt());
        assertEquals(Tbs5530PermissionState.State.PENDING, permission.state());
        assertEquals(Tbs5530PermissionState.Callback.ACCEPTED,
                permission.callback(attempt.token, attempt.generation, operational, true));
        permission.confirmFresh(operational, true);
        permission.requireReady(operational);
    }

    private final Device device = new Device("fake-operational", 2, 0x734c, 0x5530);
    private final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private final Tbs5530SameSessionHandoff handoff = new Tbs5530SameSessionHandoff("session", "epoch");
    private void pending() throws Exception {
        handoff.select(device); permission.begin(device, false, 1000);
    }
    private void duplicate() throws Exception { handoff.attach(permission, device, "session", "epoch"); }
    private void admit() throws Exception { handoff.descriptorsBeforeOwner(device, descriptors(), "session", "epoch"); }
    private static byte[] descriptors() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,1,2,0,1,9,2,32,0,1,1,0,(byte)128,50,
                9,4,0,0,2,(byte)255,0,0,0,7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    private void failure(String reason, org.junit.function.ThrowingRunnable call) {
        assertEquals(reason, assertThrows(java.io.IOException.class, call).getMessage());
    }
    @Test public void duplicateBeforeAndAfterUiPublicationIsOnlyProvisionalUntilExactDescriptor() throws Exception {
        handoff.detached(new Device("old-default", 1, 0x734c, 0x5530));
        handoff.observeAttach(device);
        pending(); duplicate(); duplicate();
        Tbs5530PermissionState.Attempt attempt = permission.attempt();
        permission.callback(attempt.token, attempt.generation, device, true);
        permission.confirmFresh(device, true);
        admit(); handoff.requireOwnerAdmission("session", "epoch");
        permission.requireReady(device);
    }
    @Test public void pendingDuplicateIsNeverDescriptorOrOwnerProof() throws Exception {
        pending(); duplicate();
        failure("HANDOFF_DESCRIPTOR_UNVERIFIED", () -> handoff.requireOwnerAdmission("session", "epoch"));
    }
    @Test public void detachBetweenAttachAndUiPublicationIsLatched() throws Exception {
        handoff.observeAttach(device);
        failure("USB_DETACHED", () -> handoff.detached(device));
        failure("USB_DETACHED", () -> handoff.select(device));
    }
    @Test public void detachAfterSelectionRejectsSamePathAba() throws Exception {
        pending(); duplicate();
        failure("USB_DETACHED", () -> handoff.detached(device));
        failure("USB_DETACHED", this::duplicate);
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    @Test public void changedDeviceIdInvalidatesAttempt() throws Exception {
        pending();
        failure("ATTACH_IDENTITY_MISMATCH", () -> handoff.attach(permission,
                new Device(device.path, 3, 0x734c, 0x5530), "session", "epoch"));
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    @Test public void changedDevicePathInvalidatesAttempt() throws Exception {
        pending();
        failure("ATTACH_IDENTITY_MISMATCH", () -> handoff.attach(permission,
                new Device("other-path", device.id, 0x734c, 0x5530), "session", "epoch"));
    }
    @Test public void changedVidPidIsNotAnOperationalDuplicate() throws Exception {
        pending();
        failure("ATTACH_IDENTITY_MISMATCH", () -> handoff.attach(permission,
                new Device(device.path, device.id, 0x734c, 0x5531), "session", "epoch"));
    }
    @Test public void changedDescriptorHashBlocksBeforeOwner() throws Exception {
        pending(); duplicate(); byte[] raw = descriptors(); raw[12] ^= 1;
        failure("HANDOFF_DESCRIPTOR_MISMATCH", () -> handoff.descriptorsBeforeOwner(device, raw, "session", "epoch"));
        failure("HANDOFF_DESCRIPTOR_MISMATCH", this::admit);
    }
    @Test public void changedDescriptorLengthBlocksBeforeOwner() throws Exception {
        pending(); duplicate();
        failure("HANDOFF_DESCRIPTOR_MISMATCH", () -> handoff.descriptorsBeforeOwner(device,
                java.util.Arrays.copyOf(descriptors(), 49), "session", "epoch"));
    }
    @Test public void attachAfterOwnerAdmissionRemainsAbaAndInvalidates() throws Exception {
        pending(); admit(); handoff.requireOwnerAdmission("session", "epoch");
        failure("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED", this::duplicate);
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    @Test public void differentEpochAndRestoredEpochCannotRearm() throws Exception {
        pending();
        failure("HANDOFF_SESSION_OR_EPOCH_CHANGED", () -> handoff.attach(permission, device, "session", "other-epoch"));
        failure("HANDOFF_SESSION_OR_EPOCH_CHANGED", this::duplicate);
    }
    @Test public void differentSessionCannotReuseScope() throws Exception {
        pending();
        failure("HANDOFF_SESSION_OR_EPOCH_CHANGED", () -> handoff.attach(permission, device, "other-session", "epoch"));
    }
    @Test public void stalePermissionGenerationCannotUseDuplicateException() throws Exception {
        pending(); permission.invalidate();
        failure("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED", this::duplicate);
    }
}
