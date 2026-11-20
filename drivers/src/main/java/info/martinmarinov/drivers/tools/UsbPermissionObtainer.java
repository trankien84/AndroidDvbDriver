/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2022 by Signalware Ltd <driver at aerialtv.eu>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package info.martinmarinov.drivers.tools;


import static android.app.PendingIntent.FLAG_MUTABLE;
import static androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.concurrent.Future;

public class UsbPermissionObtainer {
    private static final String TAG = UsbPermissionObtainer.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public static Future<UsbDeviceConnection> obtainFdFor(Context context, UsbDevice usbDevice) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? FLAG_MUTABLE : 0;
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (!manager.hasPermission(usbDevice)) {
            AsyncFuture<UsbDeviceConnection> task = new AsyncFuture<>();
            BroadcastReceiver receiver = registerNewBroadcastReceiver(context, usbDevice, task);
            try {
                Intent callback = new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName());
                manager.requestPermission(usbDevice, PendingIntent.getBroadcast(context, 0, callback, flags));
            } catch (RuntimeException e) {
                context.unregisterReceiver(receiver);
                throw e;
            }
            return task;
        } else {
            return new CompletedFuture<>(manager.openDevice(usbDevice));
        }
    }

    private static BroadcastReceiver registerNewBroadcastReceiver(final Context context, final UsbDevice usbDevice, final AsyncFuture<UsbDeviceConnection> task) {
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        if (task.isDone()) {
                            Log.d(TAG, "Permission already should be processed, ignoring.");
                            return;
                        }
                        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (usbDevice.equals(device)) {
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                if (!manager.hasPermission(device)) {
                                    Log.d(TAG, "Permissions were granted but can't access the device");
                                    task.setDone(null);
                                } else {
                                    Log.d(TAG, "Permissions granted and device is accessible");
                                    task.setDone(manager.openDevice(device));
                                }
                            } else {
                                Log.d(TAG, "Extra permission was not granted");
                                task.setDone(null);
                            }
                            context.unregisterReceiver(this);
                        }
                    }
                }
            }
        };
        ContextCompat.registerReceiver(context, broadcastReceiver,
                new IntentFilter(ACTION_USB_PERMISSION), RECEIVER_NOT_EXPORTED);
        return broadcastReceiver;
    }

    private UsbPermissionObtainer() {}
}
