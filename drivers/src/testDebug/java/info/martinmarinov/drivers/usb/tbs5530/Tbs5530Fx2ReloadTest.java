// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic image and in-memory ports only; never authorizes real firmware or USB. */
public class Tbs5530Fx2ReloadTest {
    static final String KEY = Tbs5530AutonomousPlans.hash("synthetic fixed physical epoch");
    static final class Store implements Tbs5530ReloadLedger.Storage {
        byte[] journal = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER), anchor = Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER);
        boolean fail, partial;
        public byte[] read() throws IOException { if (journal == null) throw new IOException("missing"); return journal.clone(); }
        public byte[] anchor() throws IOException { if (anchor == null) throw new IOException("missing"); return anchor.clone(); }
        public void appendAndSync(byte[] expected, byte[] priorAnchor, byte[] suffix, byte[] anchorSuffix) throws IOException {
            assertArrayEquals(expected, journal); assertArrayEquals(priorAnchor, anchor);
            if (fail) throw new IOException("fsync failed");
            journal = append(journal, suffix); if (!partial) anchor = append(anchor, anchorSuffix);
        }
        static byte[] append(byte[] left, byte[] right) { byte[] n = Arrays.copyOf(left, left.length + right.length); System.arraycopy(right,0,n,left.length,right.length); return n; }
    }
    static class Rig implements Tbs5530Fx2Reload.Port, Tbs5530Fx2Reload.Clock {
        final Store store = new Store();
        final Tbs5530ReloadLedger ledger = new Tbs5530ReloadLedger(store);
        final Tbs5530FirmwarePlan firmware = Tbs5530BootstrapDryRunTest.plan(Tbs5530BootstrapDryRunTest.image(8192));
        final List<String> events = new ArrayList<>();
        final Tbs5530Fx2Reload reload = new Tbs5530Fx2Reload(firmware, () -> {
            if (!this.permission) throw new IOException("permission lost");
        }, this, this, ledger, KEY);
        int writes, cleanup, failOrdinal = -1, result;
        boolean ioException, permission = true, losePermission, detachInTransfer, detachInWait;
        long now;
        Tbs5530Fx2Reload.Boundary boundary;
        Rig() throws Exception { }
        public int write(Tbs5530FirmwarePlan.Step step) throws IOException {
            assertTrue(new String(store.journal, java.nio.charset.StandardCharsets.UTF_8).contains("FX2_RELOAD_CONSUMED " + KEY));
            assertEquals(0, cleanup); writes++;
            Tbs5530FirmwarePlan.Step source = firmware.getSteps().get(writes - 1);
            assertEquals(source.getRequestType(),step.getRequestType()); assertEquals(source.getRequest(),step.getRequest());
            assertEquals(source.getValue(),step.getValue()); assertEquals(source.getIndex(),step.getIndex());
            assertEquals(source.getTimeoutMillis(),step.getTimeoutMillis()); assertArrayEquals(source.getPayload(),step.getPayload());
            result = step.getPayload().length;
            if (writes == failOrdinal) {
                if (detachInTransfer) reload.reenumerated();
                if (losePermission) permission = false;
                if (ioException) throw new IOException("native synthetic failure");
                if (!losePermission && !detachInTransfer) result--;
            }
            boundary = new Tbs5530Fx2Reload.Boundary(writes,step.getValue(),step.getPayload().length,result,0,now,++now);
            events.add("A0:"+writes); return result;
        }
        public Tbs5530Fx2Reload.Boundary boundary() { return boundary; }
        public void cleanup() { cleanup++; }
        public long nanos() { return now; }
        public void pause(int ms) { events.add("wait:"+ms); now += ms * 1000000L; if (detachInWait) reload.reenumerated(); }
    }
    @Test public void source132ExactConsumeBeforeIoAndBoundedWaitThenFrontend() throws Exception {
        Rig r = new Rig(); r.events.add("OS_PERMISSION"); r.events.add("CLAIM"); r.reload.execute(); r.events.add("FRONTEND80");
        assertEquals(132,r.writes); assertEquals(0,r.cleanup); assertEquals(Tbs5530Fx2Reload.State.COMPLETE,r.reload.snapshot().state);
        assertEquals(132,r.reload.snapshot().completed); assertEquals("OS_PERMISSION",r.events.get(0)); assertEquals("CLAIM",r.events.get(1));
        assertEquals("wait:100",r.events.get(134)); assertEquals("FRONTEND80",r.events.get(r.events.size()-1));
        assertTrue(r.reload.snapshot().waitEndedNanos-r.reload.snapshot().finalReleaseNanos>=1600000000L);
        assertThrows(IOException.class,r.reload::execute); assertEquals(132,r.writes);
    }
    @Test public void shortAndIOExceptionAtCriticalOrdinalsAreUncertainCleanupOnceWithoutRetry() throws Exception {
        for (int ordinal : new int[]{1,66,131,132}) for (boolean exception : new boolean[]{false,true}) {
            Rig r = new Rig(); r.failOrdinal=ordinal; r.ioException=exception;
            assertThrows(IOException.class,r.reload::execute); assertEquals(ordinal,r.writes); assertEquals(ordinal-1,r.reload.snapshot().completed);
            assertEquals(Tbs5530Fx2Reload.State.UNCERTAIN,r.reload.snapshot().state); assertEquals(1,r.cleanup);
            assertThrows(IOException.class,r.reload::execute); assertEquals(1,r.cleanup); assertEquals(ordinal,r.writes);
        }
    }
    @Test public void permissionOrDetachInNativeTransferIsUncertainAndNeverReleasesCpuAgain() throws Exception {
        for (boolean detach : new boolean[]{false,true}) {
            Rig r = new Rig(); r.failOrdinal=132; r.detachInTransfer=detach; r.losePermission=!detach;
            assertThrows(IOException.class,r.reload::execute); assertEquals(132,r.writes); assertEquals(1,r.cleanup);
            assertEquals(Tbs5530Fx2Reload.State.UNCERTAIN,r.reload.snapshot().state);
        }
    }
    @Test public void detachDuringWaitIsTerminalBeforeAnyFrontendIo() throws Exception {
        Rig r = new Rig(); r.detachInWait=true;
        assertThrows(IOException.class,r.reload::execute); assertEquals(132,r.writes); assertEquals(1,r.cleanup);
        assertEquals(Tbs5530Fx2Reload.State.RELOAD_CAUSED_REENUMERATION,r.reload.snapshot().state);
        assertFalse(r.events.contains("FRONTEND80"));
        Rig race = new Rig(); race.reload.execute(); race.reload.requireComplete();
        race.reload.reenumerated();
        assertThrows(IOException.class,race.reload::requireComplete);
        assertEquals(Tbs5530Fx2Reload.State.RELOAD_CAUSED_REENUMERATION,race.reload.snapshot().state);
        assertEquals(132,race.writes);
    }
    @Test public void consumeFailureDuplicateEpochAndPartialAppendAllRefuseBeforeIo() throws Exception {
        Rig fail = new Rig(); fail.store.fail=true; assertThrows(IOException.class,fail.reload::execute); assertEquals(0,fail.writes);
        Rig duplicate = new Rig(); duplicate.ledger.consume(KEY); assertThrows(IOException.class,duplicate.reload::execute); assertEquals(0,duplicate.writes);
        Rig partial = new Rig(); partial.store.partial=true; assertThrows(IOException.class,partial.reload::execute); assertEquals(0,partial.writes);
        assertThrows(IOException.class,()->new Tbs5530ReloadLedger(partial.store));
    }
    @Test public void missingCorruptAndRollbackLatchRatherThanRearm() throws Exception {
        Rig r = new Rig(); byte[] original=r.store.journal.clone(); r.ledger.consume(KEY); r.store.journal=original;
        assertThrows(IOException.class,r.ledger::require); r.store.journal=Store.append(original,Tbs5530ReloadLedger.bytes("FX2_RELOAD_CONSUMED "+KEY+"\n"));
        assertThrows(IOException.class,r.ledger::require);
        for (int mode=0;mode<3;mode++) {
            Store s=new Store(); if(mode==0)s.journal=null; else if(mode==1)s.anchor=null; else s.journal[0]=0;
            assertThrows(IOException.class,()->new Tbs5530ReloadLedger(s));
        }
    }
    @Test public void badNativeBoundaryAndPreflightPermissionRefuseBeforeFrontend() throws Exception {
        Rig r = new Rig() {
            @Override public Tbs5530Fx2Reload.Boundary boundary() {
                return new Tbs5530Fx2Reload.Boundary(66,0,1,1,0,0,1);
            }
        };
        assertThrows(IOException.class,r.reload::execute); assertEquals(1,r.writes);
        assertEquals(Tbs5530Fx2Reload.State.UNCERTAIN,r.reload.snapshot().state); assertEquals(1,r.cleanup);
        Rig denied = new Rig(); denied.permission=false;
        assertThrows(IOException.class,denied.reload::execute); assertEquals(0,denied.writes);
        assertEquals(Tbs5530Fx2Reload.State.BLOCKED,denied.reload.snapshot().state);
        assertArrayEquals(Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER),denied.store.journal);
    }
    @Test public void realEntryRejectsSyntheticHashBeforeAnyDeviceIoAndB2KeepsB1Goldens() throws Exception {
        Rig r=new Rig(); assertThrows(IOException.class,()->Tbs5530Fx2Reload.lockedImage(r.firmware)); assertEquals(0,r.writes);
        assertEquals(Tbs5530AutonomousPlans.dryRun(Tbs5530FrontendExperiment.MANUAL_PLP0_K36),
                Tbs5530AutonomousPlans.dryRun(Tbs5530FrontendExperiment.B2_RELOAD_AFTER_SET_CONFIG));
        new Tbs5530AutonomousPlans(); new Tbs5530AutonomousPlans(Tbs5530FrontendExperiment.MANUAL_PLP0_K36);
        new Tbs5530AutonomousPlans(Tbs5530FrontendExperiment.B2_RELOAD_AFTER_SET_CONFIG);
        Tbs5530FrontendExperiment b2=Tbs5530FrontendExperiment.B2_RELOAD_AFTER_SET_CONFIG;
        for (int mode=0;mode<5;mode++) {
            final int m=mode;
            assertThrows(IOException.class,()->b2.requireOptions(m==0?"NATIVE_USBFS_URB_8X8192":"NATIVE_USBFS_URB_8X4096",m!=1,m==2,m==3,m==4));
        }
        assertTrue(b2.matches(Tbs5530FrontendExperiment.MANUAL_PLP0_K36.parameters()));
        String destination = System.getenv("TBS5530_PLAN_OUTPUT");
        if (destination != null) {
            java.nio.file.Path directory = java.nio.file.Paths.get(destination); java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.write(directory.resolve("b2-reload-plan.txt"), Tbs5530ReloadLedger.bytes(Tbs5530Fx2Reload.transcript()));
            java.nio.file.Files.write(directory.resolve("b2-reload-store-template.txt"), Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER));
            java.nio.file.Files.write(directory.resolve("b2-reload-anchor-template.txt"), Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER));
            java.nio.file.Files.write(directory.resolve("b2-reload-initialized-template.txt"), Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.INITIALIZED_HEADER));
            System.out.println("B2_RELOAD_PLAN_SHA256="+Tbs5530Fx2Reload.planSha256());
        }
    }
}
