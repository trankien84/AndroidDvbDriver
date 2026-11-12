// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530PermissionPreflightTest {
    private static final String TOKEN = "00000000-0000-4000-8000-000000000001";
    private final Device device = new Device("SYNTHETIC_ONLY", 1, 0x734c, 0x5530);
    private final long[] now = {1000};
    private final Fake port = new Fake();
    private int evidenceWrites;
    private long evidenceAt;
    private final Tbs5530PermissionPreflight preflight = create(TOKEN);
    private Tbs5530PermissionPreflight create(String token) {
        try { return new Tbs5530PermissionPreflight(token, () -> now[0], port, (value, at) -> { evidenceWrites++; evidenceAt = at; }); }
        catch (IOException error) { throw new IllegalArgumentException(error); }
    }
    private final class Fake implements Tbs5530PermissionPreflight.Port {
        List<Device> devices = Collections.singletonList(device);
        boolean allowed, synchronous, closedRequest, closedListener;
        int requests, checks, enumerations;
        String correlation;
        Tbs5530UsbBinding.PermissionCallback callback;
        Tbs5530UsbBinding.Events events;
        public List<Device> devices() { enumerations++; return devices; }
        public boolean hasPermission(Device candidate) { assertEquals(device, candidate); checks++; return allowed; }
        public Tbs5530UsbBinding.Registration request(Device candidate, String token, Tbs5530UsbBinding.PermissionCallback callback) {
            assertEquals(device, candidate); requests++; correlation = token; this.callback = callback;
            if (synchronous) callback.result(token, device, true);
            return () -> closedRequest = true;
        }
        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) { this.events = events; return () -> closedListener = true; }
    }
    private void start() throws IOException { preflight.start(); preflight.tick(true); }
    private void approve() { port.allowed = true; port.callback.result(port.correlation, device, true); }

    @Test public void portCannotOpenClaimTransferOrConsumeSession() {
        for (Method method : Tbs5530PermissionPreflight.Port.class.getDeclaredMethods())
            assertTrue(Arrays.asList("devices", "hasPermission", "request", "listen").contains(method.getName()));
        assertEquals(4, Tbs5530PermissionPreflight.Port.class.getDeclaredMethods().length);
    }
    @Test public void freshHostTokenRequired() {
        assertThrows(IllegalArgumentException.class, () -> create(null));
        assertThrows(IllegalArgumentException.class, () -> create("old-file"));
    }
    @Test public void alreadyGrantedNeverPromptsAndRechecksEnumerationAndOs() throws Exception {
        port.allowed = true; start();
        assertEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
        assertEquals(0, port.requests); assertEquals(2, port.checks); assertEquals(2, port.enumerations);
        assertTrue(port.closedListener); assertTrue(preflight.terminal());
    }
    @Test public void pendingMakesOnlyOneRequestAcrossTicks() throws Exception {
        start(); for (int i = 0; i < 20; i++) { now[0] += 200; preflight.tick(false); }
        assertEquals(1, port.requests); assertEquals(1, port.checks);
        assertEquals(Tbs5530PermissionPreflight.State.USER_ACTION_REQUIRED, preflight.state());
        assertEquals(now[0], evidenceAt); assertTrue(evidenceWrites > 20); assertTrue(preflight.mayPause());
    }
    @Test public void approvalIsNotGrantedUntilForegroundAndFreshChecks() throws Exception {
        start(); approve(); assertTrue(port.closedRequest); assertFalse(preflight.mayPause());
        preflight.tick(false); assertNotEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
        preflight.tick(true); assertEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
        assertEquals(Tbs5530PermissionState.Outcome.UNVERIFIED, preflight.permission.outcome());
    }
    @Test public void callbackTrueWithoutOsGrantFailsClosed() throws Exception {
        start(); port.callback.result(port.correlation, device, true);
        assertThrows(IOException.class, () -> preflight.tick(true));
        assertEquals(Tbs5530PermissionPreflight.State.UNVERIFIED, preflight.state());
    }
    @Test public void denialIsTerminalWithoutRetry() throws Exception {
        start(); port.callback.result(port.correlation, device, false); approve(); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.DENIED, preflight.state()); assertEquals(1, port.requests);
        assertEquals(Tbs5530PermissionState.Outcome.PERMISSION_DENIED, preflight.permission.outcome());
        assertTrue(port.closedRequest); assertTrue(port.closedListener);
    }
    @Test public void staleCallbackCannotCompleteRequest() throws Exception {
        start(); port.callback.result("old-token", device, true); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.USER_ACTION_REQUIRED, preflight.state()); assertFalse(port.closedRequest);
    }
    @Test public void duplicateCallbackCannotReverseApproval() throws Exception {
        start(); approve(); port.callback.result(port.correlation, device, false); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
    }
    @Test public void detachAbaCannotReuseCallbackOrSamePath() throws Exception {
        start(); port.events.detached(device); approve(); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.UNVERIFIED, preflight.state()); assertTrue(port.closedRequest);
    }
    @Test public void ambiguousDuplicateAttachAndMismatchFailClosed() throws Exception {
        start(); port.events.attached(new Device(device.path, device.id, device.vendorId, device.productId));
        assertEquals("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED", preflight.reason());
    }
    @Test public void unrelatedDeviceDoesNotAffectPermissionOrGetClaimed() throws Exception {
        start(); port.events.attached(new Device("LEGACY", 3, 1, 2)); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.USER_ACTION_REQUIRED, preflight.state()); assertEquals(1, port.requests);
    }
    @Test public void multipleTbsDevicesFailBeforeRequest() throws Exception {
        port.devices = Arrays.asList(device, new Device("OTHER", 2, 0x734c, 0x5530));
        assertThrows(IOException.class, this::start); assertEquals(0, port.requests);
        assertEquals(Tbs5530PermissionPreflight.State.UNVERIFIED, preflight.state());
    }
    @Test public void missingDeviceWaitsWithoutRequestOrOpen() throws Exception {
        port.devices = Collections.emptyList(); start();
        assertEquals(Tbs5530PermissionPreflight.State.WAITING, preflight.state()); assertEquals(0, port.requests); assertEquals(0, port.checks);
    }
    @Test public void pendingTimesOutAtSixtySecondsAndClosesRegistration() throws Exception {
        start(); now[0] = preflight.deadline; preflight.tick(false); approve();
        assertEquals(Tbs5530PermissionPreflight.State.TIMED_OUT, preflight.state());
        assertTrue(port.closedRequest); assertTrue(port.closedListener); assertFalse(preflight.mayPause());
    }
    @Test public void callbackAtDeadlineCannotPublishGranted() throws Exception {
        start(); now[0] = preflight.deadline; approve();
        assertEquals(Tbs5530PermissionPreflight.State.TIMED_OUT, preflight.state());
    }
    @Test public void clockRollbackFailsClosed() throws Exception {
        start(); now[0]--; preflight.tick(true); assertEquals("CLOCK_ROLLBACK", preflight.reason());
    }
    @Test public void cancelClosesPendingAndLateCallbackDoesNothing() throws Exception {
        start(); preflight.cancel(); approve(); preflight.tick(true);
        assertEquals(Tbs5530PermissionPreflight.State.CANCELLED, preflight.state());
        assertTrue(port.closedRequest); assertTrue(port.closedListener);
    }
    @Test public void terminalSuccessSurvivesActivityHandoffButCannotRestart() throws Exception {
        port.allowed = true; start(); preflight.cancel();
        assertEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
        assertThrows(IOException.class, preflight::start);
    }
    @Test public void synchronousCallbackRegistrationIsClosedBeforeNextTick() throws Exception {
        port.synchronous = true; start(); assertTrue(port.closedRequest);
        port.allowed = true; preflight.tick(true); assertEquals(Tbs5530PermissionPreflight.State.GRANTED, preflight.state());
    }
}
