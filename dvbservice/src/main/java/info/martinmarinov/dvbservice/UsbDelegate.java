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

package info.martinmarinov.dvbservice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ProductionSession;

public class UsbDelegate extends Activity {
    private static final String TAG = UsbDelegate.class.getSimpleName();
    private static final String ACTION_DVB_DEVICE_ATTACHED = "info.martinmarinov.dvbdriver.DVB_DEVICE_ATTACHED";
    private static final String TELEVIZOR_PACKAGE = "net.petrjanousek.DVBTTelevizor";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            handleAttachment(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
        }

        finish();
    }

    void handleAttachment(UsbDevice usbDevice) {
        if (usbDevice == null) return;
        boolean tbs5530 = usbDevice.getVendorId() == 0x734c && usbDevice.getProductId() == 0x5530;
        if (tbs5530 && Tbs5530ProductionSession.isActive()) {
            Log.d(TAG, "TBS5530 production owner active; attachment forwarding suppressed");
            return;
        }
        Log.d(TAG, "USB DVB-T attached; forwarding to TV client");
        Intent newIntent = new Intent(ACTION_DVB_DEVICE_ATTACHED);
        newIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);
        try {
            startActivity(newIntent);
        } catch (ActivityNotFoundException e) {
            if (tbs5530) {
                Intent launcher = getPackageManager().getLaunchIntentForPackage(TELEVIZOR_PACKAGE);
                if (launcher != null) {
                    try {
                        startActivity(launcher);
                        Log.i(TAG, "Opened DVBTTelevizor; client must request the driver");
                        return;
                    } catch (ActivityNotFoundException ignored) {
                        // The client may have been removed after resolving its launcher.
                    }
                }
            }
            Log.d(TAG, "No activity found for DVB-T handling");
        }
    }
}
