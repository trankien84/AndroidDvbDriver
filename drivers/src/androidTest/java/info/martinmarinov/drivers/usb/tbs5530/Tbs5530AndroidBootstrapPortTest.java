// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.app.PendingIntent;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Android adapter with injected manager/raw connection only; never accesses UsbManager hardware. */
public class Tbs5530AndroidBootstrapPortTest {
    private static final class Raw implements Tbs5530AndroidUsbFacade.RawConnection {
        int calls, closes, result = 1;
        boolean throwsError;
        public int interfaceCount() { throw new AssertionError("No interface access"); }
        public int interfaceId(int i) { throw new AssertionError(); }
        public int alternateSetting(int i) { throw new AssertionError(); }
        public boolean claim(int i, boolean force) { throw new AssertionError("No claim"); }
        public boolean release(int i) { throw new AssertionError("No release"); }
        public byte[] rawDescriptors() { return new byte[]{18,1}; }
        public int controlTransfer(int type, int request, int value, int index, byte[] bytes, int offset, int length, int timeout) {
            calls++; assertEquals(0x40,type); assertEquals(0xa0,request); assertEquals(0x7f92,value);
            assertEquals(0,index); assertEquals(0,offset); assertEquals(1,length); assertEquals(2000,timeout);
            assertArrayEquals(new byte[]{1},bytes);
            if (throwsError) throw new IllegalStateException("synthetic"); return result;
        }
        public void close() { closes++; }
    }
    private static Tbs5530Fx2ProtocolPlan plan() throws Exception {
        byte[] bytes = new byte[8192];
        Tbs5530FirmwarePlan source = Tbs5530FirmwarePlan.create(bytes, new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(bytes), Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,8192)),
                Collections.emptyList(),0,8192));
        return Tbs5530Fx2ProtocolPlan.describe(source,"TBS_SOURCE_DUAL_RESET");
    }
    private static final Tbs5530UsbBinding.Device DEVICE = new Tbs5530UsbBinding.Device("synthetic",1,0x734c,0x5530);
    private static Tbs5530AndroidBootstrap.Port port(Raw raw, Tbs5530OneShotBootstrap.Guard guard) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new Tbs5530AndroidBootstrap.Port(context, new Tbs5530AndroidUsbFacade.Manager() {
            public List<Tbs5530UsbBinding.Device> devices() { return Collections.singletonList(DEVICE); }
            public boolean hasPermission(Tbs5530UsbBinding.Device d) { return true; }
            public void requestPermission(Tbs5530UsbBinding.Device d, PendingIntent p) { throw new AssertionError(); }
            public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device d) { return raw; }
        }, plan(), guard);
    }
    @Test public void fullShortNegativeAndExceptionReturnExactlyAndroidResult() throws Exception {
        for (int value : new int[]{1,0,-1,2}) {
            Raw raw = new Raw(); raw.result = value;
            Tbs5530AndroidBootstrap.Port port = port(raw, () -> { });
            Tbs5530UsbBinding.Registration listener = port.listen(new Tbs5530UsbBinding.Events() {
                public void attached(Tbs5530UsbBinding.Device d) { } public void detached(Tbs5530UsbBinding.Device d) { }
            });
            Tbs5530LifecycleCoordinator.Connection connection = port.openBootstrap(DEVICE);
            assertEquals(value, connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000));
            try { connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000); fail(); } catch(IOException expected) { }
            port.finishWrites(); connection.close(); listener.close(); assertEquals(1,raw.calls); assertEquals(1,raw.closes);
        }
        Raw raw = new Raw(); raw.throwsError = true;
        Tbs5530AndroidBootstrap.Port port = port(raw, () -> { });
        Tbs5530UsbBinding.Registration listener = port.listen(new Tbs5530UsbBinding.Events() {
            public void attached(Tbs5530UsbBinding.Device d) { } public void detached(Tbs5530UsbBinding.Device d) { }
        });
        Tbs5530LifecycleCoordinator.Connection connection = port.openBootstrap(DEVICE);
        try { connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000); fail(); } catch(IllegalStateException expected) { }
        connection.close(); listener.close(); assertEquals(1,raw.calls);
    }
    @Test public void missingObserverAuthorizationAndMainThreadNeverOpen() throws Exception {
        Raw raw = new Raw(); Tbs5530AndroidBootstrap.Port port = port(raw, () -> { });
        try { port.openBootstrap(DEVICE); fail(); } catch(IOException expected) { }
        Tbs5530AndroidBootstrap.Port denied = port(raw, () -> { throw new IOException("denied"); });
        try { denied.openBootstrap(DEVICE); fail(); } catch(IOException expected) { }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { port.openBootstrap(DEVICE); } catch(Throwable error) { failure.set(error); }
        });
        assertTrue(failure.get() instanceof IOException); assertEquals(0,raw.calls); assertEquals(0,raw.closes);
    }
    @Test public void secondLeaseWrongFieldsAndWriteAcknowledgmentAreRejected() throws Exception {
        Raw raw = new Raw();
        Tbs5530AndroidBootstrap.Port port = port(raw, new Tbs5530OneShotBootstrap.Guard() {
            public void requireCurrent() { }
            public void requireWrite(int n) throws IOException { throw new IOException("ADB acknowledgment missing"); }
        });
        Tbs5530UsbBinding.Registration listener = port.listen(new Tbs5530UsbBinding.Events() {
            public void attached(Tbs5530UsbBinding.Device d) { } public void detached(Tbs5530UsbBinding.Device d) { }
        });
        Tbs5530LifecycleCoordinator.Connection connection = port.openBootstrap(DEVICE);
        try { port.openBootstrap(DEVICE); fail(); } catch(IOException expected) { }
        try { connection.controlTransfer(0x40,0xa0,0xe600,0,new byte[]{1},0,1,2000); fail(); } catch(IOException expected) { }
        try { connection.controlTransfer(0x40,0xa0,0x7f92,0,new byte[]{1},0,1,2000); fail(); } catch(IOException expected) { }
        assertEquals(0,raw.calls); connection.close(); listener.close();
    }
}
