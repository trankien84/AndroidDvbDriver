// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530DurabilityFaultTest {
    private static File directory() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(c.getPackageName().equals("info.martinmarinov.dvbdriver.phase29offline")
                || c.getPackageName().equals("info.martinmarinov.dvbdriver.phase31offline"));
        File d = new File(c.getCacheDir(),"durability-"+UUID.randomUUID()); assertTrue(d.mkdirs()); return d;
    }
    @Test public void standaloneRawObservationIsSavedPrivatelyWithoutChangingItsBytes() throws Exception {
        File d = directory(); String id = UUID.randomUUID().toString(); byte[] bytes = new byte[]{18,1,2,3};
        assertSame(bytes,Tbs5530AndroidRawDescriptorPort.persistObservation(d,id,bytes));
        File evidence = new File(d,"phase29-raw-observation-"+id+".bin");
        assertArrayEquals(bytes,new AtomicFile(evidence).readFully());
        assertNull(Tbs5530AndroidRawDescriptorPort.persistObservation(d,UUID.randomUUID().toString(),null));
        try { Tbs5530AndroidRawDescriptorPort.persistObservation(d,"../outside",bytes); fail(); } catch (IOException expected) { }
        assertEquals(1,d.listFiles().length); assertTrue(evidence.delete()); assertTrue(d.delete());
    }
    @Test public void fsyncAndSilentCommitFailuresCannotPassDurabilityGate() throws Exception {
        File d = directory(); File file = new File(d,"audit.json");
        byte[] old = new byte[]{1}; Tbs5530DurableFile.write(file,old);
        try {
            Tbs5530DurableFile.write(file,new byte[]{2},new Tbs5530DurableFile.Commit() {
                public void sync(FileOutputStream output) throws IOException { throw new IOException("Injected fsync"); }
                public void finish(AtomicFile file, FileOutputStream output) { fail("Commit after failed sync"); }
            }); fail();
        } catch (IOException expected) { assertArrayEquals(old,new AtomicFile(file).readFully()); }
        try {
            Tbs5530DurableFile.write(file,new byte[]{3},new Tbs5530DurableFile.Commit() {
                public void sync(FileOutputStream output) throws IOException { output.getFD().sync(); }
                public void finish(AtomicFile atomic, FileOutputStream output) throws IOException { output.close(); }
            }); fail();
        } catch (IOException expected) { assertArrayEquals(old,new AtomicFile(file).readFully()); }
        assertTrue(file.delete()); assertTrue(d.delete());
    }
    @Test public void auditFailuresAtBothBoundariesPreserveNativeResultAndCloseGate() throws Exception {
        for (int failCheckpoint : new int[]{1,2}) {
            for (int nativeResult : new int[]{1,0,-1}) {
                runPort(failCheckpoint,nativeResult,false);
            }
        }
    }
    @Test public void deviceLossDuringFinalAuthorizationNeverEntersFakeNative() throws Exception { runPort(0,1,true); }
    @Test public void expiredDispatchCannotEnterNativeAfterAllOtherChecks() throws Exception { runPort(0,1,false,true); }
    private static void runPort(int failCheckpoint, int result, boolean loseAtFinal) throws Exception {
        runPort(failCheckpoint,result,loseAtFinal,false);
    }
    private static void runPort(int failCheckpoint, int result, boolean loseAtFinal, boolean denyDispatch) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().equals("info.martinmarinov.dvbdriver.phase29offline")
                || context.getPackageName().equals("info.martinmarinov.dvbdriver.phase31offline"));
        Tbs5530UsbBinding.Device d = new Tbs5530UsbBinding.Device("fake",1,0x734c,0x5530);
        boolean[] present = {true}; int[] calls = {0}, checkpoints = {0};
        Tbs5530AndroidUsbFacade.Manager manager = new Tbs5530AndroidUsbFacade.Manager() {
            public List<Tbs5530UsbBinding.Device> devices() { return present[0] ? Collections.singletonList(d) : Collections.emptyList(); }
            public boolean hasPermission(Tbs5530UsbBinding.Device device) { return present[0]; }
            public void requestPermission(Tbs5530UsbBinding.Device device, PendingIntent intent) { throw new AssertionError(); }
            public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device device) {
                return new Tbs5530AndroidUsbFacade.RawConnection() {
                    public int interfaceCount() { throw new AssertionError(); }
                    public int interfaceId(int i) { throw new AssertionError(); }
                    public int alternateSetting(int i) { throw new AssertionError(); }
                    public boolean claim(int i,boolean f) { throw new AssertionError(); }
                    public boolean release(int i) { throw new AssertionError(); }
                    public void close() { }
                    public int controlTransfer(int t,int r,int v,int i,byte[] b,int o,int l,int timeout) { calls[0]++; return result; }
                };
            }
        };
        byte[] image = new byte[8192];
        Tbs5530FirmwarePlan firmware = Tbs5530FirmwarePlan.create(image,new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(image),Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,8192)),
                Collections.emptyList(),0,8192));
        Tbs5530OneShotBootstrap.Guard guard = new Tbs5530OneShotBootstrap.Guard() {
            public void requireCurrent() { }
            public void requireNative(int ordinal) { if (loseAtFinal) present[0] = false; }
            public void requireDispatch(int ordinal) throws IOException {
                if (denyDispatch) throw new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.PRE_NATIVE_ACK_TIMEOUT,"Injected dispatch deadline");
            }
        };
        Tbs5530AndroidBootstrap.Port port = new Tbs5530AndroidBootstrap.Port(context,manager,
                Tbs5530Fx2ProtocolPlan.describe(firmware,"TBS_SOURCE_DUAL_RESET"),guard);
        port.telemetry(new Tbs5530BootstrapTelemetry(s -> {
            if (++checkpoints[0] == failCheckpoint) throw new IOException("Injected audit boundary");
        },SystemClock::elapsedRealtimeNanos,"ANDROID_ELAPSED_REALTIME_NS"));
        Tbs5530UsbBinding.Registration listener = port.listen(new Tbs5530UsbBinding.Events() {
            public void attached(Tbs5530UsbBinding.Device device) { } public void detached(Tbs5530UsbBinding.Device device) { }
        });
        Tbs5530LifecycleCoordinator.Connection connection = port.openBootstrap(d);
        try {
            connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000); fail();
        } catch (Tbs5530BootstrapStop stop) {
            assertEquals(denyDispatch ? Tbs5530BootstrapStop.Kind.PRE_NATIVE_ACK_TIMEOUT : loseAtFinal ? Tbs5530BootstrapStop.Kind.USB_DETACHED : failCheckpoint == 1 || result == 1
                    ? Tbs5530BootstrapStop.Kind.AUDIT_FAILED : result < 0 ? Tbs5530BootstrapStop.Kind.NATIVE_NEGATIVE
                    : Tbs5530BootstrapStop.Kind.NATIVE_SHORT,stop.kind);
        }
        int count = denyDispatch || loseAtFinal || failCheckpoint == 1 ? 0 : 1;
        assertEquals(count,calls[0]); assertEquals(count,port.boundaries().nativeCallStarted);
        assertEquals(count,port.boundaries().nativeCallCompleted);
        assertEquals(count == 1 && result == 1 ? 1 : 0,port.boundaries().fullLengthCompleted);
        try { connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000); fail(); }
        catch (IOException expected) { }
        assertEquals(count,calls[0]); connection.close(); listener.close();
    }
}
