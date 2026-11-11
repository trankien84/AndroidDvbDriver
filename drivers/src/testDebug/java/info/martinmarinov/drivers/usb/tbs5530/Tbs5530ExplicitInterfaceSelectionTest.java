// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Fake-only guards and API ordering. No Android service, JNI or physical USB. */
public class Tbs5530ExplicitInterfaceSelectionTest {
    static byte[] descriptors() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,
                7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    static final class Port implements Tbs5530ExplicitInterfaceSelection.Port {
        byte[] raw = descriptors();
        int speed = 3, id, alt, calls, reads, observations, closes;
        int[] addresses = {0x82, 0x81}, directions = {128, 128}, types = {2, 2}, sizes = {512, 512};
        long[] receive = new long[14];
        boolean permission = true, present = true, claimed = true, active = true, consent = true, result = true;
        IOException failure;
        RuntimeException runtimeFailure;
        Runnable during;
        long now = 100;
        final List<String> events = new ArrayList<>();
        public void requireCurrent() throws IOException {
            if (!permission || !present || !claimed || !active || !consent) throw new IOException("Owner/permission revoked");
        }
        public Tbs5530ExplicitInterfaceSelection.Observation observe() throws IOException {
            observations++; events.add("OBSERVE");
            return new Tbs5530ExplicitInterfaceSelection.Observation(raw, speed, id, alt, addresses, directions, types, sizes);
        }
        public long[] receiveState() { return receive.clone(); }
        public boolean setInterfaceAlt0() throws IOException {
            calls++; events.add("SET_INTERFACE"); now += 200;
            if (during != null) during.run();
            if (failure != null) throw failure;
            if (runtimeFailure != null) throw runtimeFailure;
            return result;
        }
        int bulk() { reads++; events.add("BULK"); return 4096; }
        Tbs5530ExplicitInterfaceSelection gate(boolean on) { return new Tbs5530ExplicitInterfaceSelection(on, this, () -> now); }
        void close() { active = false; closes++; events.add("DRAIN_RELEASE_CLOSE"); }
    }
    @Test public void offPerformsNoProfileReadOrInterfaceApi() throws Exception {
        Port p = new Port(); p.permission = false;
        Tbs5530ExplicitInterfaceSelection gate = p.gate(false);
        assertEquals(4096, gate.read(p::bulk)); assertEquals(0, p.calls); assertEquals(0, p.observations);
        assertEquals("OFF", gate.snapshot().state); assertNull(gate.snapshot().apiResult);
        assertEquals(-1, gate.snapshot().startedNanos);
    }
    @Test public void onceBeforeFirstBulkWithAppLevelBeforeAfterAndNoReconfiguration() throws Exception {
        Port p = new Port(); Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
        for (int i = 0; i < 16; i++) assertEquals(4096, gate.read(p::bulk));
        assertEquals(1, p.calls); assertEquals(2, p.observations); assertEquals(16, p.reads);
        assertEquals(Arrays.asList("OBSERVE", "SET_INTERFACE", "OBSERVE", "BULK"), p.events.subList(0, 4));
        Tbs5530ExplicitInterfaceSelection.Snapshot s = gate.snapshot();
        assertEquals("API_TRUE_PROFILE_UNCHANGED", s.state); assertTrue(s.apiCalled); assertEquals(Boolean.TRUE, s.apiResult);
        assertEquals(100, s.startedNanos); assertEquals(300, s.returnedNanos); assertEquals(0, s.outstandingBefore); assertEquals(0, s.outstandingAfter);
        assertEquals(s.before.descriptorHash, s.after.descriptorHash); assertEquals(0, s.before.interfaceId);
        assertEquals(0, s.after.alternateSetting); assertEquals(512, s.before.streamPacketSize); assertEquals(3, s.after.negotiatedUsbSpeed);
    }
    @Test public void exactRawIdentityConfigurationAltEndpointAndSpeedAreMandatory() throws Exception {
        for (int fault = 0; fault < 13; fault++) {
            Port p = new Port();
            switch (fault) {
                case 0: p.raw[8] ^= 1; break;
                case 1: p.raw[10] ^= 1; break;
                case 2: p.raw[23] = 2; break;
                case 3: p.raw[29] = 1; break;
                case 4: p.raw[30] = 1; break;
                case 5: p.raw[38] = (byte)0x83; break;
                case 6: p.speed = -1; break;
                case 7: p.speed = 2; break;
                case 8: p.id = 1; break;
                case 9: p.alt = 1; break;
                case 10: p.addresses[0] = 0x81; break;
                case 11: p.sizes[0] = 64; break;
                default: p.types[0] = 3;
            }
            Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
            assertThrows("fault=" + fault, IOException.class, () -> gate.read(p::bulk));
            assertThrows(IOException.class, () -> gate.read(p::bulk));
            assertEquals(0, p.calls); assertEquals(0, p.reads);
        }
    }
    @Test public void fullSpeedDescriptorsAndAmbiguousEndpointTreesFailClosed() throws Exception {
        for (int fault = 0; fault < 4; fault++) {
            Port p = new Port();
            if (fault == 0) { p.raw[40] = 64; p.raw[41] = 0; }
            if (fault == 1) { p.raw[47] = 64; p.raw[48] = 0; }
            if (fault == 2) p.raw[45] = (byte)0x82;
            if (fault == 3) p.raw = Arrays.copyOf(p.raw, 40);
            assertThrows(IOException.class, () -> p.gate(true).read(p::bulk)); assertEquals(0, p.calls); assertEquals(0, p.reads);
        }
    }
    @Test public void permissionPresentClaimActiveAndGrantAllRequired() throws Exception {
        for (int fault = 0; fault < 5; fault++) {
            Port p = new Port();
            if (fault == 0) p.permission = false;
            if (fault == 1) p.present = false;
            if (fault == 2) p.claimed = false;
            if (fault == 3) p.active = false;
            if (fault == 4) p.consent = false;
            assertThrows(IOException.class, () -> p.gate(true).read(p::bulk)); assertEquals(0, p.calls); assertEquals(0, p.reads);
        }
    }
    @Test public void pendingOrPreviouslySubmittedOrQuarantinedUrbCannotSelectAlt() throws Exception {
        for (int field : new int[]{0, 1, 2, 3, 6}) {
            Port p = new Port(); p.receive[field] = 1;
            Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
            assertThrows(IOException.class, () -> gate.read(p::bulk));
            p.receive[field] = 0;
            assertThrows(IOException.class, () -> gate.read(p::bulk)); assertEquals(0, p.calls); assertEquals(0, p.reads);
        }
    }
    @Test public void falseFailsBeforeBulkWithoutRetryAndRetainsKnownApiResult() throws Exception {
        Port p = new Port(); p.result = false; Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
        assertThrows(IOException.class, () -> gate.read(p::bulk)); p.result = true;
        assertThrows(IOException.class, () -> gate.read(p::bulk));
        assertEquals(1, p.calls); assertEquals(0, p.reads); assertEquals(Boolean.FALSE, gate.snapshot().apiResult);
        assertEquals("API_FALSE", gate.snapshot().state); assertNull(gate.snapshot().after); assertEquals(300, gate.snapshot().returnedNanos);
        p.close(); assertEquals(1, p.closes);
    }
    @Test public void apiExceptionsAreTerminalWithUnknownResultAndKnownReturnBoundary() throws Exception {
        for (boolean runtime : new boolean[]{false, true}) {
            Port p = new Port();
            if (runtime) p.runtimeFailure = new IllegalStateException("Synthetic framework failure");
            else p.failure = new IOException("Synthetic disconnect");
            Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
            assertThrows(Exception.class, () -> gate.read(p::bulk));
            p.failure = null; p.runtimeFailure = null;
            assertThrows(IOException.class, () -> gate.read(p::bulk));
            assertEquals(1, p.calls); assertEquals(0, p.reads); assertEquals("API_EXCEPTION", gate.snapshot().state);
            assertNull(gate.snapshot().apiResult); assertEquals(300, gate.snapshot().returnedNanos);
        }
    }
    @Test public void disconnectRevocationProfileChangeAndPendingAfterApiStopBeforeBulk() throws Exception {
        for (int fault = 0; fault < 5; fault++) {
            Port p = new Port(); final int f = fault;
            p.during = () -> {
                if (f == 0) p.present = false;
                if (f == 1) p.permission = false;
                if (f == 2) p.raw[14] = 1;
                if (f == 3) p.receive[3] = 1;
                if (f == 4) p.consent = false;
            };
            Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
            assertThrows(IOException.class, () -> gate.read(p::bulk));
            assertEquals(1, p.calls); assertEquals(0, p.reads); assertEquals(Boolean.TRUE, gate.snapshot().apiResult);
            assertEquals("POST_API_VALIDATION_FAILED", gate.snapshot().state);
        }
    }
    @Test public void postApiObservationDeadlineDoesNotCancelAnActiveCallButStopsBulk() throws Exception {
        Port p = new Port(); p.during = () -> p.now += 2_000_000_000L;
        Tbs5530ExplicitInterfaceSelection gate = p.gate(true);
        assertThrows(IOException.class, () -> gate.read(p::bulk)); assertEquals(1, p.calls); assertEquals(0, p.reads);
    }
    @Test public void closedOwnerAndForeignWorkerCannotRepeatSuccessfulSelection() throws Exception {
        Port p = new Port(); Tbs5530ExplicitInterfaceSelection gate = p.gate(true); gate.read(p::bulk); p.close();
        assertThrows(IOException.class, () -> gate.read(p::bulk)); assertEquals(1, p.calls); assertEquals(1, p.reads);
        Port other = new Port(); Tbs5530ExplicitInterfaceSelection foreign = other.gate(true);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread t = new Thread(() -> { try { foreign.read(other::bulk); } catch (Throwable error) { failure.set(error); } });
        t.start(); t.join(1000); assertFalse(t.isAlive()); assertTrue(failure.get() instanceof IOException);
        assertEquals(0, other.calls); assertEquals(0, other.reads);
    }
    @Test public void limitsDefaultOffAndAlt0CannotCombineWith8192QueueOrClearHalt() throws Exception {
        assertFalse(new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500).explicitSetInterfaceAlt0);
        Tbs5530TsSmoke.Limits limits = new Tbs5530TsSmoke.Limits(65536, 16, 5000, 500, 4096, false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096, true);
        Tbs5530AutonomousPlans.requireTs(limits); assertTrue(limits.explicitSetInterfaceAlt0); assertEquals(10000, limits.maximumMillis());
        for (Tbs5530TsSmoke.ReadApi api : Tbs5530TsSmoke.ReadApi.values()) if (api != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096) {
            assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,
                    api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192 ? 8192 : 4096,false,api,true));
        }
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(65536,16,5000,500,4096,true,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true));
        assertThrows(IllegalArgumentException.class, () -> new Tbs5530TsSmoke.Limits(32768,16,5000,500,4096,false,
                Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096,true));
    }
}
