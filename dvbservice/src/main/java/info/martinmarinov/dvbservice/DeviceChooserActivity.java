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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;
import info.martinmarinov.dvbservice.dialogs.ListPickerFragmentDialog;
import info.martinmarinov.dvbservice.tools.StackTraceSerializer;
import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

import static info.martinmarinov.drivers.DvbException.ErrorCode.NO_DVB_DEVICES_FOUND;

public class DeviceChooserActivity extends FragmentActivity implements ListPickerFragmentDialog.OnSelected<DeviceFilter> {
    private final static int RESULT_ERROR = RESULT_FIRST_USER;

    private final static String CONTRACT_ERROR_CODE = "ErrorCode";
    private final static String CONTRACT_CONTROL_PORT = "ControlPort";
    private final static String CONTRACT_TRANSFER_PORT = "TransferPort";
    private final static String CONTRACT_DEVICE_NAME = "DeviceName";
    private final static String CONTRACT_RAW_TRACE = "RawTrace";
    private final static String CONTRACT_USB_PRODUCT_IDS = "ProductIds";
    private final static String CONTRACT_USB_VENDOR_IDS = "VendorIds";

    private final Intent response = new Intent();
    private final DvbServiceResponseReceiver serviceReceiver = new DvbServiceResponseReceiver();
    private Thread discovery;
    private DvbService.OpenRequest openRequest;
    private boolean discoveryStarted;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress);
        findViewById(R.id.cancel_device_open).setOnClickListener(view -> onListPickerDialogCanceled());

        // Set to receive info from the DvbService
        IntentFilter statusIntentFilter = new IntentFilter(DvbService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceReceiver, statusIntentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (discoveryStarted) return;
        discoveryStarted = true;
        discovery = new Thread(() -> {
            List<DvbDevice> devices = new ArrayList<>();
            try {
                List<DeviceFilter> filters = DvbUsbDeviceRegistry.getUsbDvbDeviceFilters(getApplicationContext());
                devices.addAll(TsDumpFileUtils.getDevicesForAllRecordings(this));
                for (DvbDevice device : devices) filters.add(device.getDeviceFilter());
                if (filters.isEmpty()) throw new DvbException(NO_DVB_DEVICES_FOUND,
                        getString(info.martinmarinov.drivers.R.string.no_devices_found));
                boolean showPicker = filters.size() > 1 || !devices.isEmpty();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    if (showPicker) ListPickerFragmentDialog.showOneInstanceOnly(getSupportFragmentManager(), filters);
                    else onListPickerDialogItemSelected(filters.get(0));
                });
            } catch (DvbException | RuntimeException e) {
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) handleException(asDvbException(e));
                });
            } finally {
                for (DvbDevice device : devices) {
                    try {
                        device.close();
                    } catch (IOException e) {
                        android.util.Log.w("DvbChooser", "Discovery cleanup failed", e);
                    }
                }
            }
        }, "DvbDeviceDiscovery");
        discovery.start();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (openRequest != null) openRequest.cancel();
        if (discovery != null) discovery.interrupt();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
        super.onDestroy();
    }

    @Override
    public void onListPickerDialogItemSelected(@NonNull DeviceFilter deviceFilter) {
        if (openRequest != null || isFinishing()) return;
        try {
            openRequest = DvbService.requestOpen(this, deviceFilter);
        } catch (DvbException e) {
            handleException(e);
        }
    }

    @Override
    public void onListPickerDialogCanceled() {
        if (openRequest != null) openRequest.cancel();
        finishWith(RESULT_CANCELED);
    }

    // Open selected device

    private class DvbServiceResponseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            DvbService.StatusMessage statusMessage = DvbService.parseMessage(intent);
            if (statusMessage == null || openRequest == null || isFinishing()
                    || !openRequest.id.equals(statusMessage.requestId)) return;
            openRequest = null;
            if (statusMessage.exception != null) {
                handleException(asDvbException(statusMessage.exception));
            } else {
                handleSuccess(statusMessage.deviceFilter, statusMessage.serverAddresses);
            }
        }
    }

    // API for returning response to caller

    private void handleSuccess(DeviceFilter deviceFilter, DvbServerPorts addresses) {
        int[] productIds = new int[] {deviceFilter.getProductId()};
        int[] vendorIds = new int[] {deviceFilter.getVendorId()};

        response.putExtra(CONTRACT_DEVICE_NAME, deviceFilter.getName());
        response.putExtra(CONTRACT_USB_PRODUCT_IDS, productIds);
        response.putExtra(CONTRACT_USB_VENDOR_IDS, vendorIds);

        response.putExtra(CONTRACT_CONTROL_PORT, addresses.getControlPort());
        response.putExtra(CONTRACT_TRANSFER_PORT, addresses.getTransferPort());

        finishWith(RESULT_OK);
    }

    private static DvbException asDvbException(Exception exception) {
        return exception instanceof DvbException ? (DvbException) exception
                : new DvbException(DvbException.ErrorCode.IO_EXCEPTION, exception);
    }

    private void handleException(DvbException e) {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> availableDevices = manager.getDeviceList().values();
        int[] productIds = new int[availableDevices.size()];
        int[] vendorIds = new int[availableDevices.size()];

        int id = 0;
        for (UsbDevice usbDevice : availableDevices) {
            productIds[id] = usbDevice.getProductId();
            vendorIds[id] = usbDevice.getVendorId();
            id++;
        }

        response.putExtra(CONTRACT_ERROR_CODE, e.getErrorCode().name());
        response.putExtra(CONTRACT_RAW_TRACE, StackTraceSerializer.serialize(e));
        response.putExtra(CONTRACT_USB_PRODUCT_IDS, productIds);
        response.putExtra(CONTRACT_USB_VENDOR_IDS, vendorIds);

        finishWith(RESULT_ERROR);
    }

    private void finishWith(int code) {
        if (getParent() == null) {
            setResult(code, response);
        } else {
            getParent().setResult(code, response);
        }
        finish();
    }
}
