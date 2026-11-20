// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.SystemClock;
import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.usbxfer.NativeUsbfsControl;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Entirely fake Android/firmware/native boundaries: no firmware image or USB access. */
public class Tbs5530DvbDeviceTest {
    @Test public void normalCloseAndReopenGetsNewSessionAndExactlyOneReloadPerOpen() throws Exception {
        exercise(false, false);
    }
    @Test public void partialFrontendInitializationCleansOwnerAndCannotRetrySameDeviceObject() throws Exception {
        exercise(true, false);
    }
    @Test public void serviceNoSignalDoesNotFailStatusOrReloadOnNextTune() throws Exception {
        exercise(false, true);
    }
    private static void exercise(boolean shortProbe, boolean scan) throws Exception {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context); when(context.getString(anyInt())).thenReturn("fixture");
        UsbDevice device = mock(UsbDevice.class); UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
        Tbs5530ProductionAttachment first = mock(Tbs5530ProductionAttachment.class);
        Tbs5530DetachConfirmation confirmation = mock(Tbs5530DetachConfirmation.class);
        when(first.confirmation()).thenReturn(confirmation);
        when(first.openControl()).thenReturn(connection);
        ScriptedFakeControlIo script = new ScriptedFakeControlIo();
        Tbs5530FrontendTest.probeFixture(script, 0x197);
        if (!shortProbe) {
            Tbs5530FrontendTest.initFixture(script);
            if (scan) {
                for (int i = 0; i < 2; i++) {
                    Tbs5530FrontendTest.tuneFixture(script, new Tbs5530DvbT2TuneParameters(506_000_000L, 8_000_000, 0), i > 0);
                    Tbs5530FrontendTest.statusFixture(script, 0x10);
                    Tbs5530FrontendTest.tuneFixture(script, new Tbs5530DvbT2TuneParameters(506_000_000L, 8_000_000,
                            Tbs5530DvbT2TuneParameters.Selection.automatic()), true);
                    Tbs5530FrontendTest.statusFixture(script, 0x10);
                }
            } else {
                Tbs5530FrontendTest.probeFixture(script, 0x197); Tbs5530FrontendTest.initFixture(script);
            }
        }
        AtomicInteger calls = new AtomicInteger(), reloads = new AtomicInteger();
        long[] now = {0};
        Tbs5530Frontend.Clock testClock = new Tbs5530Frontend.Clock() {
            @Override public long elapsedRealtimeMillis() { return now[0]; }
            @Override public void delayMillis(long millis) { now[0] += millis; }
        };
        when(connection.controlTransfer(anyInt(),anyInt(),anyInt(),anyInt(),any(byte[].class),anyInt(),anyInt(),anyInt()))
                .thenAnswer(a -> {
                    if (shortProbe && calls.incrementAndGet() == 2) return 0;
                    return script.controlTransfer(a.getArgument(0),a.getArgument(1),a.getArgument(2),a.getArgument(3),
                            a.getArgument(4),a.getArgument(5),a.getArgument(6),a.getArgument(7));
                });
        try (MockedStatic<DvbDemux> demux = mockStatic(DvbDemux.class);
             MockedStatic<Tbs5530ProductionFirmware> firmware = mockStatic(Tbs5530ProductionFirmware.class);
             MockedStatic<NativeUsbfsControl> nativeControl = mockStatic(NativeUsbfsControl.class);
             MockedConstruction<Tbs5530FirmwareStore> store = mockConstruction(Tbs5530FirmwareStore.class,
                     (mock, args) -> when(mock.load()).thenReturn(new byte[8192]));
             MockedConstruction<Tbs5530ProductionAttachment> reopened = mockConstruction(Tbs5530ProductionAttachment.class,
                     (mock, args) -> {
                         when(mock.openControl()).thenReturn(connection);
                         when(mock.prepare(false)).thenReturn(device);
                         when(mock.confirmation()).thenReturn(confirmation);
                     })) {
            demux.when(DvbDemux::DvbDmxSwfilter).thenAnswer(a -> mock(DvbDemux.class));
            firmware.when(() -> Tbs5530ProductionFirmware.load(context)).thenReturn(new byte[8192]);
            nativeControl.when(() -> NativeUsbfsControl.reloadFx2(eq(connection),any(byte[].class),any(NativeUsbfsControl.Guard.class)))
                    .thenAnswer(a -> { ((NativeUsbfsControl.Guard)a.getArgument(2)).require(); reloads.incrementAndGet(); return null; });
            Tbs5530DvbDevice dvb = new Tbs5530DvbDevice(context,device,new DeviceFilter(0x734c,0x5530,"fixture"),first,testClock);
            try {
                if (shortProbe) {
                    assertThrows(DvbException.class,dvb::open); assertThrows(DvbException.class,dvb::open);
                    assertEquals(1,reloads.get()); verify(first).close(); verify(connection,atLeastOnce()).close();
                } else {
                    dvb.open(); assertEquals(1,reloads.get());
                    assertEquals(java.util.Collections.singleton(DeliverySystem.DVBT2),dvb.readCapabilities().getSupportedDeliverySystems());
                    if (scan) {
                        assertEquals(DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED, assertThrows(DvbException.class,
                                () -> dvb.tuneForService(506_000_000L, 8_000_000, DeliverySystem.DVBT)).getErrorCode());
                        assertEquals(DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ, assertThrows(DvbException.class,
                                () -> dvb.tuneForService(900_000_000L, 8_000_000, DeliverySystem.DVBT2)).getErrorCode());
                        for (int i = 0; i < 2; i++) {
                            dvb.tuneForService(506_000_000L, 8_000_000, DeliverySystem.DVBT2);
                            assertEquals(-1, dvb.readSnr());
                            assertEquals(0xffff, dvb.readBitErrorRate());
                            assertTrue(dvb.getStatus().isEmpty());
                            assertEquals(1, reloads.get());
                        }
                    } else {
                        dvb.close(); verify(first).close(); dvb.open(); assertEquals(2,reloads.get());
                        assertEquals(1,reopened.constructed().size());
                    }
                    script.assertComplete();
                }
            } finally { dvb.close(); }
        }
    }
}
