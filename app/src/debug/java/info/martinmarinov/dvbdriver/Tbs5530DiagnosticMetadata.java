// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

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
        final List<Boolean> acquisitionAllowed;
        Snapshot(String report, List<String> labels, List<Tbs5530UsbBinding.Device> devices) {
            this(report,labels,devices,Collections.nCopies(devices.size(),true));
        }
        Snapshot(String report, List<String> labels, List<Tbs5530UsbBinding.Device> devices, List<Boolean> acquisitionAllowed) {
            this.acquisitionAllowed = Collections.unmodifiableList(new ArrayList<>(acquisitionAllowed));
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
        List<Boolean> acquisitionAllowed = new ArrayList<>();
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
                    boolean allowed = supportsDefaultProfile(device);
                    labels.add(label);
                    acquisitionAllowed.add(allowed);
                    if (!allowed) out.append("  Connection cycle BLOCKED: interface 0/alternate 0 không có Bulk IN 0x82 hợp lệ. Vẫn cho phép yêu cầu quyền và đọc raw riêng khi đã có quyền.\n");
                    devices.add(new Tbs5530UsbBinding.Device(device.getDeviceName(), device.getDeviceId(), device.getVendorId(), device.getProductId()));
                }
            }
            if (ordinal == 0) out.append("Không có thiết bị USB.\n");
            if (devices.isEmpty()) out.append("Không có TBS5530 734c:5530.\n");
        } catch (RuntimeException failure) {
            out.append("Không đọc được metadata: ").append(failure instanceof SecurityException ? "SecurityException" : "RuntimeException").append('\n');
            labels.clear();
            devices.clear();
            acquisitionAllowed.clear();
        }
        return new Snapshot(out.toString(), labels, devices, acquisitionAllowed);
    }

    private static boolean supportsDefaultProfile(UsbDevice device) {
        UsbInterface selected = null;
        for (int i=0;i<device.getInterfaceCount();i++) {
            UsbInterface iface=device.getInterface(i);
            if (iface.getId()==0 && iface.getAlternateSetting()==0) {
                if (selected!=null) return false;
                selected=iface;
            }
        }
        if (selected==null) return false;
        List<info.martinmarinov.drivers.usb.tbs5530.Tbs5530Descriptors.Endpoint> endpoints=new ArrayList<>();
        try {
            for (int e=0;e<selected.getEndpointCount();e++) {
                UsbEndpoint ep=selected.getEndpoint(e);
                endpoints.add(new info.martinmarinov.drivers.usb.tbs5530.Tbs5530Descriptors.Endpoint(
                        0,0,ep.getAddress(),ep.getDirection(),ep.getType(),ep.getMaxPacketSize()));
            }
            info.martinmarinov.drivers.usb.tbs5530.Tbs5530Descriptors.selectBulkEndpoint(endpoints,0);
            return true;
        } catch (IllegalArgumentException error) { return false; }
    }

    static JSONObject structured(Context context) throws JSONException {
        JSONObject report = new JSONObject();
        report.put("usb_host", context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST));
        JSONArray devices = new JSONArray(); report.put("devices", devices);
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) return report;
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (device.getVendorId() != 0x734c || device.getProductId() != 0x5530) continue;
            JSONObject d = new JSONObject(); devices.put(d);
            d.put("vid", hex(device.getVendorId(),4)); d.put("pid",hex(device.getProductId(),4));
            d.put("permission",manager.hasPermission(device));
            JSONArray configurations = new JSONArray(); d.put("configurations",configurations);
            for (int c=0;c<device.getConfigurationCount();c++) {
                UsbConfiguration configuration=device.getConfiguration(c); JSONObject config=new JSONObject(); configurations.put(config);
                config.put("index",c);config.put("id",configuration.getId());
                JSONArray interfaces=new JSONArray();config.put("interfaces",interfaces);
                for(int i=0;i<configuration.getInterfaceCount();i++) {
                    UsbInterface iface=configuration.getInterface(i);JSONObject item=new JSONObject();interfaces.put(item);
                    item.put("index",i);item.put("id",iface.getId());item.put("alternate",iface.getAlternateSetting());
                    item.put("class",iface.getInterfaceClass());item.put("subclass",iface.getInterfaceSubclass());item.put("protocol",iface.getInterfaceProtocol());
                    JSONArray endpoints=new JSONArray();item.put("endpoints",endpoints);
                    for(int e=0;e<iface.getEndpointCount();e++) {
                        UsbEndpoint ep=iface.getEndpoint(e);JSONObject endpoint=new JSONObject();endpoints.put(endpoint);
                        endpoint.put("address",hex(ep.getAddress(),2));endpoint.put("direction",ep.getDirection()==128?"IN":"OUT");
                        endpoint.put("type",ep.getType());endpoint.put("max_packet_size",ep.getMaxPacketSize());endpoint.put("interval",ep.getInterval());
                    }
                }
            }
        }
        return report;
    }

    private static String hex(int value, int width) { return String.format(Locale.ROOT, "%0" + width + "x", value); }
}
