// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Shared admission code, synthetic identities only; never opens UsbManager or a production store. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530SameSessionHandoffIntegrationTest {
    private final Device device = new Device("SYNTHETIC_HANDOFF", 2, 0x734c, 0x5530);
    private final Tbs5530PermissionState permission = new Tbs5530PermissionState();
    private final Tbs5530SameSessionHandoff handoff = new Tbs5530SameSessionHandoff("fake-session", "fake-epoch");
    private void pending() throws Exception { handoff.select(device); permission.begin(device, false, 1000); }
    private void duplicate() throws IOException { handoff.attach(permission, device, "fake-session", "fake-epoch"); }
    private static byte[] descriptors() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,1,2,0,1,9,2,32,0,1,1,0,(byte)128,50,
                9,4,0,0,2,(byte)255,0,0,0,7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    @Test public void mainThreadDuplicateBeforeAndAfterPublicationThenWorkerDescriptorSeal() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                handoff.detached(new Device("SYNTHETIC_DEFAULT", 1, 0x734c, 0x5530));
                handoff.observeAttach(device);
                pending();
                Tbs5530PermissionState.Attempt attempt = permission.attempt();
                duplicate(); duplicate();
                assertSame(attempt, permission.attempt());
                assertEquals(Tbs5530PermissionState.Callback.ACCEPTED,
                        permission.callback(attempt.token, attempt.generation, device, true));
                permission.confirmFresh(device, true);
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        handoff.descriptorsBeforeOwner(device, descriptors(), "fake-session", "fake-epoch");
        handoff.requireOwnerAdmission("fake-session", "fake-epoch");
        permission.requireReady(device);
        assertEquals("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED", assertThrows(IOException.class, this::duplicate).getMessage());
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
    }
    @Test public void detachAndChangedDescriptorNeverAdmitOwner() throws Exception {
        pending(); duplicate();
        assertEquals("USB_DETACHED", assertThrows(IOException.class, () -> handoff.detached(device)).getMessage());
        assertEquals("USB_DETACHED", assertThrows(IOException.class,
                () -> handoff.descriptorsBeforeOwner(device, descriptors(), "fake-session", "fake-epoch")).getMessage());
        Tbs5530SameSessionHandoff wrong = new Tbs5530SameSessionHandoff("fake-session", "fake-epoch");
        wrong.select(device); byte[] raw = descriptors(); raw[12] ^= 1;
        assertEquals("HANDOFF_DESCRIPTOR_MISMATCH", assertThrows(IOException.class,
                () -> wrong.descriptorsBeforeOwner(device, raw, "fake-session", "fake-epoch")).getMessage());
    }
    @Test public void genericPermissionAndEpochRemainFailClosed() throws Exception {
        pending();
        assertEquals("HANDOFF_SESSION_OR_EPOCH_CHANGED", assertThrows(IOException.class,
                () -> handoff.attach(permission, device, "fake-session", "other-epoch")).getMessage());
        assertEquals(Tbs5530PermissionState.State.INVALIDATED, permission.state());
        Tbs5530PermissionState generic = new Tbs5530PermissionState(); generic.begin(device, true, 1000);
        assertEquals(Tbs5530PermissionState.Attach.AMBIGUOUS_DUPLICATE, generic.attach(device));
        assertThrows(IOException.class, () -> generic.requireReady(device));
    }
}
