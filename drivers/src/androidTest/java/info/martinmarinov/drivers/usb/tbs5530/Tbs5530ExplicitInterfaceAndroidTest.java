// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real Android worker/binding/permission checks; API and USB are injected fakes only. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530ExplicitInterfaceAndroidTest {
    private static final Tbs5530UsbBinding.Device DEVICE = new Tbs5530UsbBinding.Device("/fake/alt0",1,0x734c,0x5530);
    private static byte[] descriptors() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,
                7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    private static final class Fake implements Tbs5530AndroidUsbFacade.Manager, Tbs5530AndroidUsbFacade.RawConnection {
        boolean permission = true, present = true, claimed, active = true, result = true, failApi, disconnect;
        int claims, releases, closes, selects, bulk, controls;
        Tbs5530ExplicitInterfaceSelection gate;
        Tbs5530OperationalHandoff owner;
        public List<Tbs5530UsbBinding.Device> devices() { return present ? Arrays.asList(DEVICE) : java.util.Collections.emptyList(); }
        public boolean hasPermission(Tbs5530UsbBinding.Device device) { return permission; }
        public void requestPermission(Tbs5530UsbBinding.Device device, android.app.PendingIntent intent) { fail("No real OS dialog"); }
        public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device device) { return this; }
        public byte[] rawDescriptors() { return descriptors(); }
        public int interfaceCount() { return 1; }
        public int interfaceId(int index) { return 0; }
        public int alternateSetting(int index) { return 0; }
        public int endpointCount(int index) { return 2; }
        public int endpointAddress(int index, int ep) { return ep == 0 ? 0x82 : 0x81; }
        public int endpointDirection(int index, int ep) { return 128; }
        public int endpointType(int index, int ep) { return 2; }
        public int endpointMaxPacketSize(int index, int ep) { return 512; }
        public int negotiatedUsbSpeed() { return 3; }
        public boolean claim(int index, boolean force) { assertFalse(force); assertEquals(0,index); claims++; claimed = true; return true; }
        public boolean release(int index) { releases++; claimed = false; return true; }
        public void close() { closes++; active = false; }
        public int controlTransfer(int type,int request,int value,int index,byte[] bytes,int offset,int length,int timeout) {
            controls++; fail("No firmware or vendor request expected in interface-only fake test"); return -1;
        }
        void configure(Tbs5530OperationalHandoff owner) { this.owner = owner; }
        private void createGate() {
            gate = new Tbs5530ExplicitInterfaceSelection(true,new Tbs5530ExplicitInterfaceSelection.Port() {
                public void requireCurrent() throws IOException {
                    if (!permission || !present || !claimed || !active || !owner.operationalOwner()) throw new IOException("Fake owner revoked");
                }
                public Tbs5530ExplicitInterfaceSelection.Observation observe() throws IOException {
                    return new Tbs5530ExplicitInterfaceSelection.Observation(descriptors(),3,0,0,
                            new int[]{0x82,0x81},new int[]{128,128},new int[]{2,2},new int[]{512,512});
                }
                public long[] receiveState() { return new long[14]; }
                public boolean setInterfaceAlt0() throws IOException {
                    assertTrue(claimed); selects++;
                    if (disconnect) present = false;
                    if (failApi) throw new IOException("Fake setInterface exception");
                    return result;
                }
            },SystemClock::elapsedRealtimeNanos);
        }
        public int bulkTransfer(int index,int endpoint,byte[] bytes,int length,int timeout) throws IOException {
            if (gate == null) createGate();
            return gate.read(() -> { assertEquals(0x82,endpoint); bulk++; return 0; });
        }
    }
    private static void exercise(int fault) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AndroidRuntime runtime = new Tbs5530AndroidRuntime(); Fake fake = new Fake();
        try (Tbs5530OperationalHandoff owner = new Tbs5530OperationalHandoff(context,runtime,fake,() -> { })) {
            fake.configure(owner); fake.result = fault != 1; fake.failApi = fault == 2; fake.disconnect = fault == 3;
            owner.start(DEVICE);
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (owner.pending() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(5);
            assertFalse(owner.pending()); assertTrue(owner.operationalOwner()); assertEquals(1,fake.claims); assertEquals(0,fake.selects);
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
            runtime.execute(() -> {
                try {
                    Tbs5530Session session = owner.session(); long generation = owner.generation();
                    session.ready(generation); session.frontendInitialized(generation); session.frontendLock(generation,true); session.streamingStarted(generation);
                    Tbs5530BulkTransport bulk = owner.bulkIo();
                    if (fault == 0) {
                        bulk.read(new byte[4096],4096,500); bulk.read(new byte[4096],4096,500);
                        assertEquals(1,fake.selects); assertEquals(2,fake.bulk);
                    } else {
                        assertThrows(IOException.class,() -> bulk.read(new byte[4096],4096,500));
                        assertThrows(IOException.class,() -> bulk.read(new byte[4096],4096,500));
                        assertEquals(1,fake.selects); assertEquals(0,fake.bulk);
                    }
                    session.cancel(generation); assertEquals(1,fake.releases); assertEquals(1,fake.closes); assertEquals(0,fake.controls);
                } catch (Throwable failure) { error.set(failure); }
                finally { done.countDown(); }
            });
            assertTrue(done.await(5,TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
        } finally { runtime.close(); assertTrue(runtime.awaitTermination(5,TimeUnit.SECONDS)); }
        assertEquals(1,fake.closes); assertEquals(1,fake.releases); assertEquals(0,fake.controls);
    }
    @Test public void fakeClaimThenOneSelectionBeforeBulkAndCleanup() throws Exception { exercise(0); }
    @Test public void fakeFalseStopsBulkAndNeverRunsFirmware() throws Exception { exercise(1); }
    @Test public void fakeExceptionStopsBulkAndNeverRunsFirmware() throws Exception { exercise(2); }
    @Test public void fakeDisconnectStopsBulkAndKeepsSingleReleaseClose() throws Exception { exercise(3); }
}
