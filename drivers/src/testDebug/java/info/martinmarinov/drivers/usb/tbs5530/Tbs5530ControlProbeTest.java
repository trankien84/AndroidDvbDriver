// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class Tbs5530ControlProbeTest {
    private static final class Fixture implements Tbs5530ControlProbe.Authorization, Tbs5530Frontend.Clock {
        final Tbs5530Session session = new Tbs5530Session();
        final long token;
        final Tbs5530ControlProbe probe;
        final List<String> calls = new ArrayList<>();
        int register, chipId = 0x197, consumed, audit;
        boolean approved = true, failAudit, revoked;
        Fixture() throws Exception {
            token = session.requestPermission(); session.permissionResult(token, true);
            session.connected(token, new Tbs5530Session.Resources() {
                public void cancel() { } public void release() { } public void close() { }
            }); session.interfaceClaimed(token);
            probe = new Tbs5530ControlProbe(session, token, (type, request, value, index, bytes, offset, length, timeout) -> {
                assertEquals(1, consumed); calls.add(Integer.toHexString(request));
                assertEquals(2000, timeout); assertEquals(0, value); assertEquals(0, index);
                if (request == 0x80) assertArrayEquals(new byte[]{3,(byte)0xcc,0,0}, bytes);
                else if (request == 0x90) { assertEquals(1, bytes[0]); assertEquals(0xcc, bytes[1]&255); register=bytes[2]&255; }
                else if (request == 0x91) bytes[0]=(byte)(register == 0xfb ? chipId >> 8 : chipId);
                else fail("Non-whitelisted request");
                return length;
            }, this, this);
        }
        public void requireCurrent() throws IOException { if (!approved || revoked) throw new IOException("Denied"); }
        public void consume(Tbs5530Frontend.Plan plan) throws IOException { requireCurrent(); if (consumed++ != 0) throw new IOException("Consumed"); }
        public void audit(String boundary,int ordinal,int type,int request,int length,int result) throws IOException {
            audit++; if (failAudit) throw new IOException("Audit unavailable");
        }
        public boolean rfInConfirmed() { return false; }
        public long elapsedRealtimeMillis() { return 1000; }
        public void delayMillis(long millis) { fail("ID probe must not init/tune or sleep"); }
    }
    @Test public void exactIdWhitelistHasThreeOutTwoInAndOnlyValidIdentityCanReadyBridge() throws Exception {
        Fixture f = new Fixture(); Tbs5530Frontend.Plan plan=f.probe.planProbe();
        assertEquals(3,plan.usbOutCount);assertEquals(2,plan.usbInCount);assertEquals(10000,plan.maximumMillis);
        assertEquals(Tbs5530Session.State.CONNECTED,f.session.getState());
        Tbs5530Frontend.Result result=f.probe.execute(plan);
        assertEquals(0x197,result.chipId);assertEquals(Tbs5530Frontend.State.DEMOD_IDENTIFIED,result.state);
        assertEquals("[80, 90, 91, 90, 91]",f.calls.toString()); assertEquals(10,f.audit);
        assertEquals(Tbs5530Session.State.READY,f.session.getState());assertFalse(f.session.isFrontendInitialized());
        assertFalse(f.session.isFrontendLocked());assertFalse(f.session.isStreaming());f.session.close();
    }
    @Test public void usbFullLengthJunkIsNotBridgeIdentityAndNeverTriggersRepair() throws Exception {
        for(int id:new int[]{0,0xff,0x3ff,0x123}) {
            Fixture f=new Fixture();f.chipId=id;
            assertThrows(IOException.class,()->f.probe.execute(f.probe.planProbe()));
            assertEquals(5,f.calls.size());assertEquals(Tbs5530Session.State.FAULTED,f.session.getState());
            assertEquals(Tbs5530Frontend.State.UNCERTAIN,f.probe.state());f.session.close();
        }
    }
    @Test public void unapprovedProbeOrFailedDurableAuditSendsNoUsb() throws Exception {
        Fixture denied=new Fixture();denied.approved=false;
        assertThrows(IOException.class,()->denied.probe.execute(denied.probe.planProbe()));assertTrue(denied.calls.isEmpty());
        Fixture audit=new Fixture();audit.failAudit=true;
        assertThrows(IOException.class,()->audit.probe.execute(audit.probe.planProbe()));assertTrue(audit.calls.isEmpty());
        denied.session.close();audit.session.close();
    }
    @Test public void consumedPlanStaleEpochAndCancelledOwnerCannotReexecute() throws Exception {
        Fixture f=new Fixture();Tbs5530Frontend.Plan plan=f.probe.planProbe();f.probe.execute(plan);
        assertThrows(IOException.class,()->f.probe.execute(plan));assertEquals(5,f.calls.size());f.session.close();
        Fixture cancelled=new Fixture();Tbs5530Frontend.Plan stale=cancelled.probe.planProbe();cancelled.probe.cancel();
        assertThrows(IOException.class,()->cancelled.probe.execute(stale));assertTrue(cancelled.calls.isEmpty());cancelled.session.drainCleanup();
    }
}
