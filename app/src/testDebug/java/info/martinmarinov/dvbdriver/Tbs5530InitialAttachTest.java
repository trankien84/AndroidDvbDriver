// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530InitialAttachTest {
    @Test public void recordedSingleInitialBroadcastAfterSelectionMustNotInvalidatePendingPermission() throws Exception {
        Device selected = new Device("fake-first-attachment", 1, 0x734c, 0x5530);
        Tbs5530PermissionState permission = new Tbs5530PermissionState();
        Tbs5530PermissionState.Attempt attempt = permission.begin(selected, false, 1000);
        permission.dispatched(attempt);
        Tbs5530SameSessionHandoff scope = Tbs5530SameSessionHandoff.initialBootstrap("session", "epoch");
        scope.select(selected);
        scope.attach(permission, selected, "session", "epoch");
        assertEquals(Tbs5530PermissionState.State.PENDING, permission.state());
        assertSame(attempt, permission.attempt());
        assertEquals(Tbs5530PermissionState.Callback.ACCEPTED, permission.callback(attempt.token, attempt.generation, selected, true));
        permission.confirmFresh(selected, true);
        scope.descriptorsBeforeOwner(selected, fx2Descriptors(), "session", "epoch");
        scope.requireOwnerAdmission("session", "epoch");
        permission.requireReady(selected);
    }
    private static final Device DEVICE = new Device("fake-first-attachment", 1, 0x734c, 0x5530);
    private static Tbs5530PermissionState pending() throws Exception {
        Tbs5530PermissionState permission = new Tbs5530PermissionState();
        permission.dispatched(permission.begin(DEVICE, false, 1000));
        return permission;
    }
    private static Tbs5530SameSessionHandoff selected() throws Exception {
        Tbs5530SameSessionHandoff scope = Tbs5530SameSessionHandoff.initialBootstrap("session", "epoch");
        scope.select(DEVICE); return scope;
    }
    @Test public void attachObservedBeforePollingIsStillOnlyProvisional() throws Exception {
        Tbs5530SameSessionHandoff scope = Tbs5530SameSessionHandoff.initialBootstrap("session", "epoch");
        scope.observeAttach(DEVICE); scope.select(DEVICE);
        Tbs5530PermissionState permission = pending();
        scope.attach(permission, DEVICE, "session", "epoch");
        assertThrows(java.io.IOException.class, () -> scope.requireOwnerAdmission("session", "epoch"));
        assertThrows(java.io.IOException.class, () -> permission.requireReady(DEVICE));
    }
    @Test public void detachBeforeSelectionIsLatchedAndCannotBeHiddenBySameIdentity() throws Exception {
        Tbs5530SameSessionHandoff scope = Tbs5530SameSessionHandoff.initialBootstrap("session", "epoch");
        assertThrows(java.io.IOException.class, () -> scope.detached(DEVICE));
        assertThrows(java.io.IOException.class, () -> scope.select(DEVICE));
        assertThrows(java.io.IOException.class, () -> scope.observeAttach(DEVICE));
    }
    @Test public void changedIdentityEpochSessionOrGenerationInvalidatesInitialAdmission() throws Exception {
        for (int fault = 0; fault < 6; fault++) {
            Tbs5530SameSessionHandoff scope = selected(); Tbs5530PermissionState permission = pending();
            Device fresh = fault == 0 ? new Device(DEVICE.path, 2, 0x734c, 0x5530)
                    : fault == 1 ? new Device("other", DEVICE.id, 0x734c, 0x5530)
                    : fault == 2 ? new Device(DEVICE.path, DEVICE.id, 0x734c, 0x5531) : DEVICE;
            String session = fault == 3 ? "other-session" : "session", epoch = fault == 4 ? "other-epoch" : "epoch";
            if (fault == 5) permission.invalidate();
            assertThrows(java.io.IOException.class, () -> scope.attach(permission, fresh, session, epoch));
            assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
            assertThrows(java.io.IOException.class, () -> scope.attach(permission, DEVICE, "session", "epoch"));
        }
    }
    @Test public void malformedTruncatedAndOperationalDescriptorsNeverAuthorizeInitialBootstrap() throws Exception {
        byte[] changed = fx2Descriptors(); changed[51] ^= 1;
        byte[] operational = new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,1,2,0,1,9,2,32,0,1,1,0,(byte)128,50,
                9,4,0,0,2,(byte)255,0,0,0,7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
        for (byte[] raw : new byte[][]{null, changed, java.util.Arrays.copyOf(fx2Descriptors(), 188), operational}) {
            Tbs5530SameSessionHandoff scope = selected();
            assertThrows(java.io.IOException.class, () -> scope.descriptorsBeforeOwner(DEVICE, raw, "session", "epoch"));
            assertThrows(java.io.IOException.class, () -> scope.requireOwnerAdmission("session", "epoch"));
        }
    }
    @Test public void lateAttachAfterInitialDescriptorSealRemainsAba() throws Exception {
        Tbs5530SameSessionHandoff scope = selected(); Tbs5530PermissionState permission = pending();
        scope.descriptorsBeforeOwner(DEVICE, fx2Descriptors(), "session", "epoch");
        scope.requireOwnerAdmission("session", "epoch");
        assertThrows(java.io.IOException.class, () -> scope.attach(permission, DEVICE, "session", "epoch"));
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    @Test public void genericNonB2AttachStillInvalidates() throws Exception {
        Tbs5530PermissionState permission = pending();
        assertEquals(Tbs5530PermissionState.Attach.AMBIGUOUS_DUPLICATE, permission.attach(DEVICE));
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    static byte[] fx2Descriptors() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        add(out,18,1,0,2,255,255,255,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1);
        add(out,9,2,171,0,1,1,0,128,50);
        int[] addresses = {1,129,2,4,134,136};
        for (int alt = 0; alt < 4; alt++) {
            add(out,9,4,0,alt,alt == 0 ? 0 : 6,255,255,255,0);
            if (alt == 0) continue;
            for (int address : addresses) {
                int type = alt == 1 || address == 4 || address == 136 ? 2 : alt == 3 && (address == 2 || address == 134) ? 1 : 3;
                int packet = alt != 1 && (address == 1 || address == 129) ? 64 : 512;
                add(out,7,5,address,type,packet & 255,packet >> 8,type == 2 ? 0 : 1);
            }
        }
        return out.toByteArray();
    }
    private static void add(java.io.ByteArrayOutputStream out, int... bytes) { for (int value : bytes) out.write(value); }
}

