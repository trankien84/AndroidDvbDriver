// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbservice;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ProductionSession;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UsbDelegateTest {
    @Test public void activeProductionSuppressesOnlyTbs5530AndIdleProductionKeepsForwarding() {
        UsbDevice tbs = mock(UsbDevice.class), other = mock(UsbDevice.class);
        when(tbs.getVendorId()).thenReturn(0x734c);
        when(tbs.getProductId()).thenReturn(0x5530);
        when(other.getVendorId()).thenReturn(0x734c);
        when(other.getProductId()).thenReturn(0x5520);
        UsbDelegate delegate = mock(UsbDelegate.class);
        doCallRealMethod().when(delegate).handleAttachment(any());
        try (MockedStatic<Log> logs = mockStatic(Log.class);
             MockedStatic<Tbs5530ProductionSession> production = mockStatic(Tbs5530ProductionSession.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class)) {
            production.when(Tbs5530ProductionSession::isActive).thenReturn(true);
            delegate.handleAttachment(tbs);
            verify(delegate, never()).startActivity(any());
            assertTrue(intents.constructed().isEmpty());
            logs.verify(() -> Log.d(anyString(), contains("forwarding suppressed")));

            delegate.handleAttachment(other);
            assertEquals(1, intents.constructed().size());
            Intent forwardedOther = intents.constructed().get(0);
            verify(forwardedOther).putExtra(UsbManager.EXTRA_DEVICE, other);
            verify(delegate).startActivity(forwardedOther);

            production.when(Tbs5530ProductionSession::isActive).thenReturn(false);
            delegate.handleAttachment(tbs);
            assertEquals(2, intents.constructed().size());
            Intent forwardedTbs = intents.constructed().get(1);
            verify(forwardedTbs).putExtra(UsbManager.EXTRA_DEVICE, tbs);
            verify(delegate).startActivity(forwardedTbs);
        }
    }

    @Test public void tbsWithoutLegacyHandlerOpensTelevizorUsingItsLauncher() {
        UsbDevice tuner = mock(UsbDevice.class);
        when(tuner.getVendorId()).thenReturn(0x734c);
        when(tuner.getProductId()).thenReturn(0x5530);
        UsbDelegate delegate = mock(UsbDelegate.class);
        PackageManager packages = mock(PackageManager.class);
        Intent launcher = mock(Intent.class);
        when(delegate.getPackageManager()).thenReturn(packages);
        when(packages.getLaunchIntentForPackage("net.petrjanousek.DVBTTelevizor")).thenReturn(launcher);
        doThrow(mock(ActivityNotFoundException.class)).when(delegate).startActivity(argThat(i -> i != launcher));
        doCallRealMethod().when(delegate).handleAttachment(tuner);
        try (MockedStatic<Log> logs = mockStatic(Log.class);
             MockedStatic<Tbs5530ProductionSession> production = mockStatic(Tbs5530ProductionSession.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class)) {
            delegate.handleAttachment(tuner);
            verify(delegate).startActivity(launcher);
        }
    }

    @Test public void missingDeviceDoesNotLaunchAnActivity() {
        UsbDelegate delegate = mock(UsbDelegate.class);
        doCallRealMethod().when(delegate).handleAttachment(any());
        delegate.handleAttachment(null);
        verify(delegate, never()).startActivity(any());
    }
}
