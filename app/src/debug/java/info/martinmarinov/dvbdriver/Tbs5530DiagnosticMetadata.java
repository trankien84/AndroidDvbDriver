// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding;

/** Descriptor-only enumeration: no names, strings, serials or connection APIs in reports. */
final class Tbs5530DiagnosticMetadata {
    static final class Snapshot {
        final String report;
        final List<String> labels;
        final List<Tbs5530UsbBinding.Device> devices;
        Snapshot(String report, List<String> labels, List<Tbs5530UsbBinding.Device> devices) {
            this.report = report;
            this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
            this.devices = Collections.unmodifiableList(new ArrayList<>(devices));
        }
    }

    static Snapshot read(Context context) {
        StringBuilder out = new StringBuilder("USB Host: ");
        out.append(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST) ? "có" : "không").append('\n');
        List<String> labels = new ArrayList<>();
        List<Tbs5530UsbBinding.Device> devices = new ArrayList<>();
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) return new Snapshot(out + "Không có dịch vụ USB.", labels, devices);
        try {
            int ordinal = 0;
            for (UsbDevice device : manager.getDeviceList().values()) {
                String label = "Thiết bị " + (++ordinal) + " VID:PID " + hex(device.getVendorId(), 4) + ":" + hex(device.getProductId(), 4);
                out.append(label).append("; quyền: ").append(manager.hasPermission(device) ? "có" : "không").append('\n');
                out.append("Số cấu hình: ").append(device.getConfigurationCount()).append('\n');
                for (int c = 0; c < device.getConfigurationCount(); c++) {
                    UsbConfiguration config = device.getConfiguration(c);
                    out.append("  Cấu hình ").append(config.getId()).append("; interfaces: ").append(config.getInterfaceCount()).append('\n');
                    for (int i = 0; i < config.getInterfaceCount(); i++) {
                        UsbInterface iface = config.getInterface(i);
                        out.append("    Interface ").append(iface.getId()).append("; alternate: ").append(iface.getAlternateSetting())
                                .append("; class/subclass/protocol: ").append(iface.getInterfaceClass()).append('/')
                                .append(iface.getInterfaceSubclass()).append('/').append(iface.getInterfaceProtocol()).append('\n');
                        for (int e = 0; e < iface.getEndpointCount(); e++) {
                            UsbEndpoint ep = iface.getEndpoint(e);
                            out.append("      Endpoint 0x").append(hex(ep.getAddress(), 2)).append("; direction: ")
                                    .append(ep.getDirection()).append("; type: ").append(ep.getType())
                                    .append("; packet: ").append(ep.getMaxPacketSize()).append("; interval: ").append(ep.getInterval()).append('\n');
                        }
                    }
                }
                if (device.getVendorId() == 0x734c && device.getProductId() == 0x5530) {
                    labels.add(label);
                    devices.add(new Tbs5530UsbBinding.Device(device.getDeviceName(), device.getDeviceId(), device.getVendorId(), device.getProductId()));
                }
            }
            if (ordinal == 0) out.append("Không có thiết bị USB.\n");
            if (devices.isEmpty()) out.append("Không có TBS5530 734c:5530.\n");
        } catch (RuntimeException failure) {
            out.append("Không đọc được metadata: ").append(failure instanceof SecurityException ? "SecurityException" : "RuntimeException").append('\n');
            labels.clear();
            devices.clear();
        }
        return new Snapshot(out.toString(), labels, devices);
    }

    private static String hex(int value, int width) { return String.format(Locale.ROOT, "%0" + width + "x", value); }
}
