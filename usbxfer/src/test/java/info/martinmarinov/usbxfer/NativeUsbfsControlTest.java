// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import android.hardware.usb.UsbDeviceConnection;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativeUsbfsControlTest {
    @Test public void rejectsMissingTruncatedOversizeAndUnpinnedFirmwareBeforeJni() throws Exception {
        byte[][] images = {null, new byte[8191], new byte[8193], new byte[8192]};
        for (byte[] image : images) {
            try { NativeUsbfsControl.validateImage(image); fail("Invalid firmware accepted"); }
            catch (IOException expected) { }
        }
    }
    @Test public void failingGuardPreventsFirmwareAccessAndNativeDispatch() throws Exception {
        UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
        IOException detached = new IOException("detached");
        try {
            NativeUsbfsControl.reloadFx2(connection, null, () -> { throw detached; });
            fail("Guard refusal ignored");
        } catch (IOException actual) { assertSame(detached, actual); }
        verifyNoInteractions(connection);
    }
    @Test public void interruptionPreventsGuardOrNativeDispatchAndKeepsFlag() throws Exception {
        UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
        Thread.currentThread().interrupt();
        try {
            NativeUsbfsControl.reloadFx2(connection, null, () -> fail("Interrupted guard called"));
            fail("Interrupted reload accepted");
        } catch (IOException expected) { assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
        verifyNoInteractions(connection);
    }
    @Test public void requiresOwnedConnectionBeforeNativeDispatch() throws Exception {
        try { NativeUsbfsControl.reloadFx2(null, new byte[8192]); fail("Missing connection accepted"); }
        catch (IOException expected) { }
    }
}
