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
    @Test public void initialBroadcastOnMainPreservesAttemptUntilWorkerChecksFx2Tree() throws Exception {
        Tbs5530SameSessionHandoff initial = Tbs5530SameSessionHandoff.initialBootstrap("fake-session", "fake-epoch");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                initial.select(device);
                Tbs5530PermissionState.Attempt attempt = permission.begin(device, false, 1000);
                permission.dispatched(attempt);
                initial.attach(permission, device, "fake-session", "fake-epoch");
                assertSame(attempt, permission.attempt()); assertEquals(Tbs5530PermissionState.State.PENDING, permission.state());
                permission.callback(attempt.token, attempt.generation, device, true);
                permission.confirmFresh(device, true);
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        initial.descriptorsBeforeOwner(device, fx2Descriptors(), "fake-session", "fake-epoch");
        initial.requireOwnerAdmission("fake-session", "fake-epoch");
        permission.requireReady(device);
        assertThrows(IOException.class, () -> initial.attach(permission, device, "fake-session", "fake-epoch"));
    }
    @Test public void initialDetachBeforePollingOrOperationalTreeNeverAdmitsBootstrap() throws Exception {
        Tbs5530SameSessionHandoff initial = Tbs5530SameSessionHandoff.initialBootstrap("fake-session", "fake-epoch");
        assertEquals("USB_DETACHED", assertThrows(IOException.class, () -> initial.detached(device)).getMessage());
        assertThrows(IOException.class, () -> initial.select(device));
        Tbs5530SameSessionHandoff wrong = Tbs5530SameSessionHandoff.initialBootstrap("fake-session", "fake-epoch");
        wrong.select(device);
        assertThrows(IOException.class, () -> wrong.descriptorsBeforeOwner(device, descriptors(), "fake-session", "fake-epoch"));
        assertThrows(IOException.class, () -> wrong.requireOwnerAdmission("fake-session", "fake-epoch"));
    }
    private static byte[] fx2Descriptors() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        add(out,18,1,0,2,255,255,255,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1); add(out,9,2,171,0,1,1,0,128,50);
        int[] addresses = {1,129,2,4,134,136};
        for (int alt = 0; alt < 4; alt++) {
            add(out,9,4,0,alt,alt == 0 ? 0 : 6,255,255,255,0); if (alt == 0) continue;
            for (int address : addresses) {
                int type = alt == 1 || address == 4 || address == 136 ? 2 : alt == 3 && (address == 2 || address == 134) ? 1 : 3;
                int packet = alt != 1 && (address == 1 || address == 129) ? 64 : 512;
                add(out,7,5,address,type,packet & 255,packet >> 8,type == 2 ? 0 : 1);
            }
        }
        return out.toByteArray();
    }
    private static void add(java.io.ByteArrayOutputStream out, int... bytes) { for (int value : bytes) out.write(value); }
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
