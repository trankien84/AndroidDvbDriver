// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Looper;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Debug-only Android boundary exposing exactly open/getRawDescriptors/close, never transfers. */
public final class Tbs5530AndroidRawDescriptorPort implements Tbs5530RawDescriptorInspection.Port {
    private final Context application;
    private final Tbs5530AndroidUsbFacade metadata;
    public Tbs5530AndroidRawDescriptorPort(Context context) {
        application = Objects.requireNonNull(context.getApplicationContext());
        metadata = new Tbs5530AndroidUsbFacade(application);
    }
    @Override public List<Tbs5530UsbBinding.Device> devices() throws IOException { return metadata.devices(); }
    @Override public boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException { return metadata.hasPermission(device); }
    @Override public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) throws IOException {
        return metadata.listen(events);
    }
    private static void worker() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("USB inspection requires a worker");
    }
    @Override public Tbs5530RawDescriptorInspection.Connection open(Tbs5530UsbBinding.Device expected) throws IOException {
        worker();
        if (expected.vendorId != 0x734c || expected.productId != 0x5530) throw new IOException("Not TBS5530");
        UsbManager manager = (UsbManager) application.getSystemService(Context.USB_SERVICE);
        if (manager == null) throw new IOException("USB service unavailable");
        UsbDevice selected = null;
        for (UsbDevice candidate : manager.getDeviceList().values()) {
            if (expected.equals(new Tbs5530UsbBinding.Device(candidate.getDeviceName(), candidate.getDeviceId(),
                    candidate.getVendorId(), candidate.getProductId()))) selected = candidate;
        }
        if (selected == null || !manager.hasPermission(selected)) throw new IOException("Attachment or permission changed");
        UsbDeviceConnection raw = manager.openDevice(selected);
        if (raw == null) return null;
        return new Tbs5530RawDescriptorInspection.Connection() {
            private boolean closed;
            @Override public synchronized byte[] getRawDescriptors() throws IOException {
                worker();
                if (closed) throw new IOException("Connection closed");
                return raw.getRawDescriptors();
            }
            @Override public synchronized void close() throws IOException {
                worker();
                if (closed) return;
                closed = true;
                raw.close();
            }
        };
    }
}
