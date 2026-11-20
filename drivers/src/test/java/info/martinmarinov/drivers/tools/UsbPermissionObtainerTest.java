// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.tools;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UsbPermissionObtainerTest {
    @Test public void callbackIsPrivateAndCompletesGrantOrDenialForRequestedDevice() throws Exception {
        for (boolean granted : new boolean[] {true, false}) {
            String action = "com.android.example.USB_PERMISSION";
            String packageName = "info.martinmarinov.dvbdriver";
            Context context = mock(Context.class);
            UsbManager manager = mock(UsbManager.class);
            UsbDevice tuner = mock(UsbDevice.class);
            UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
            PendingIntent pending = mock(PendingIntent.class);
            Intent callback = mock(Intent.class);
            AtomicReference<BroadcastReceiver> receiver = new AtomicReference<>();
            when(context.getSystemService(Context.USB_SERVICE)).thenReturn(manager);
            when(context.getPackageName()).thenReturn(packageName);
            when(manager.hasPermission(tuner)).thenReturn(false, granted);
            when(manager.openDevice(tuner)).thenReturn(connection);
            when(callback.getAction()).thenReturn(action);
            UsbDevice otherDevice = mock(UsbDevice.class);
            when(callback.getParcelableExtra(UsbManager.EXTRA_DEVICE)).thenReturn(otherDevice, tuner);
            when(callback.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)).thenReturn(granted);
            try (MockedStatic<Log> log = mockStatic(Log.class);
                 MockedStatic<ContextCompat> compat = mockStatic(ContextCompat.class);
                 MockedStatic<PendingIntent> pendingIntents = mockStatic(PendingIntent.class);
                 MockedConstruction<IntentFilter> filters = mockConstruction(IntentFilter.class);
                 MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                         (intent, args) -> when(intent.setPackage(packageName)).thenReturn(intent))) {
                compat.when(() -> ContextCompat.registerReceiver(eq(context), any(BroadcastReceiver.class),
                        any(IntentFilter.class), eq(ContextCompat.RECEIVER_NOT_EXPORTED)))
                        .thenAnswer(call -> { receiver.set(call.getArgument(1)); return null; });
                pendingIntents.when(() -> PendingIntent.getBroadcast(eq(context), eq(0), any(Intent.class), anyInt()))
                        .thenReturn(pending);
                Future<UsbDeviceConnection> result = UsbPermissionObtainer.obtainFdFor(context, tuner);
                verify(intents.constructed().get(0)).setPackage(packageName);
                verify(manager).requestPermission(tuner, pending);
                assertNotNull(receiver.get());
                receiver.get().onReceive(context, callback);
                assertFalse(result.isDone());
                receiver.get().onReceive(context, callback);
                assertSame(granted ? connection : null, result.get(1, TimeUnit.SECONDS));
                verify(context).unregisterReceiver(receiver.get());
                verify(manager, times(granted ? 1 : 0)).openDevice(tuner);
            }
        }
    }
}
