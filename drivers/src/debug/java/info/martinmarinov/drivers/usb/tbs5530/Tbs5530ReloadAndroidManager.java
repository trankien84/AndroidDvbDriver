// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.*;
import info.martinmarinov.usbxfer.NativeFx2Control;
import info.martinmarinov.usbxfer.UsbHiSpeedBulk;
import java.io.IOException;
import java.util.List;

/** Debug-only manager retaining the same OS-permitted connection, not a second fd owner. */
final class Tbs5530ReloadAndroidManager implements Tbs5530AndroidUsbFacade.Manager {
    private final Context context;
    private final Tbs5530AndroidUsbFacade.Manager metadata;
    private final Tbs5530AndroidUsbFacade.ReceiveBackendFactory receiveFactory;
    private Raw owned;
    interface Audit { void recorded(Tbs5530Fx2Reload.Boundary boundary) throws IOException; }
    private Audit audit;
    void audit(Audit audit) throws IOException {
        if (owned != null) throw new IOException("Native audit must be bound before USB acquisition");
        this.audit = java.util.Objects.requireNonNull(audit);
    }
    Tbs5530ReloadAndroidManager(Context context, Tbs5530AndroidUsbFacade.ReceiveBackendFactory receiveFactory) {
        this.context = context; metadata = new Tbs5530AndroidUsbFacade.AndroidManager(context);
        this.receiveFactory = receiveFactory;
    }
    public List<Tbs5530UsbBinding.Device> devices() throws IOException { return metadata.devices(); }
    public boolean hasPermission(Tbs5530UsbBinding.Device d) throws IOException { return metadata.hasPermission(d); }
    public void requestPermission(Tbs5530UsbBinding.Device d, PendingIntent result) throws IOException { metadata.requestPermission(d, result); }
    public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device d) throws IOException {
        if (owned != null && !owned.closed) throw new IOException("Another debug connection exists");
        UsbManager usb = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        if (usb == null) throw new IOException("USB service unavailable");
        UsbDevice device = Tbs5530AndroidUsbFacade.AndroidManager.resolvePresentDevice(d, usb.getDeviceList().values());
        if (!usb.hasPermission(device)) throw new IOException("OS USB permission required");
        UsbDeviceConnection connection = usb.openDevice(device);
        if (connection == null) throw new IOException("OS USB open failed");
        owned = new Raw(device, connection); return owned;
    }
    void prepareReload(byte[] image) throws IOException {
        if (owned == null || !owned.claimed) throw new IOException("Claimed operational owner required");
        owned.prepare(image);
    }
    Tbs5530Fx2Reload.Boundary nextReload() throws IOException {
        if (owned == null || !owned.claimed) throw new IOException("Claimed operational owner required");
        return owned.next();
    }
    info.martinmarinov.usbxfer.NativeUsbfsStream.Result receiveStream() throws IOException {
        if (owned == null) throw new IOException("No owned stream connection");
        owned.worker();
        if (!owned.claimed || owned.receive != null) throw new IOException("Stream requires a fresh claimed owner");
        UsbInterface intf = owned.intf(0);
        UsbEndpoint endpoint = null;
        for (int e = 0; e < intf.getEndpointCount(); e++) if (intf.getEndpoint(e).getAddress() == 0x82) endpoint = intf.getEndpoint(e);
        owned.receive = receiveFactory.create(owned.connection, intf, endpoint);
        if (!(owned.receive instanceof Tbs5530NativeStreamBackend)) throw new IOException("Foreign stream backend");
        return ((Tbs5530NativeStreamBackend)owned.receive).receive();
    }
    Tbs5530ControlIo monitorControl(Thread monitorThread) throws IOException {
        if (owned == null || !owned.claimed || owned.closed || monitorThread == null)
            throw new IOException("Claimed B4 monitor owner required");
        final Raw raw = owned;
        return (type, request, value, index, bytes, offset, length, timeout) -> {
            if (Thread.currentThread() != monitorThread || raw.closed || !raw.claimed
                    || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
                    || value != 0 || index != 0 || timeout < 1 || timeout > 2000
                    || bytes == null || offset < 0 || length < 1 || offset > bytes.length - length
                    || !((type == 0x40 && request == 0x80 && length >= 4)
                    || (type == 0x40 && request == 0x90 && length == 3)
                    || (type == 0xc0 && request == 0x91 && length <= 50)))
                throw new IOException("B4 monitor control scope changed");
            if (request == 0x80 && (length != 4 || (bytes[offset] & 255) != 3 || (bytes[offset + 1] & 255) != 0xc8
                    || !((bytes[offset + 2] == 0 && ((bytes[offset + 3] & 255) == 0x20
                    || (bytes[offset + 3] & 255) == 0x22 || (bytes[offset + 3] & 255) == 0x24))
                    || (bytes[offset + 2] == 1 && (bytes[offset + 3] == 0 || bytes[offset + 3] == 1)))))
                throw new IOException("Unreviewed B4 monitor write refused");
            if (request == 0x90 && (bytes[offset + 1] & 255) != 0xc8)
                throw new IOException("Unreviewed B4 monitor address refused");
            int count = raw.connection.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
            if (count != length) throw new IOException("Monitor short control transfer: " + count + "/" + length);
            return count;
        };
    }
    private final class Raw implements Tbs5530AndroidUsbFacade.RawConnection {
        final UsbDevice device;
        final UsbDeviceConnection connection;
        final Thread worker = Thread.currentThread();
        NativeFx2Control nativeControl;
        Tbs5530AndroidUsbFacade.ReceiveBackend receive;
        boolean claimed, closed, nativeClean = true;
        int ordinal, full;
        Raw(UsbDevice device, UsbDeviceConnection connection) { this.device = device; this.connection = connection; }
        void worker() throws IOException {
            if (closed || Thread.currentThread() != worker || android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                throw new IOException("Wrong or closed debug USB worker");
        }
        void prepare(byte[] image) throws IOException {
            worker(); if (nativeControl != null || ordinal != 0) throw new IOException("Native FX2 capability already issued");
            nativeControl = new NativeFx2Control(connection.getFileDescriptor(), image);
        }
        Tbs5530Fx2Reload.Boundary next() throws IOException {
            worker(); if (nativeControl == null) throw new IOException("No exact FX2 capability");
            long[] b;
            try { b = nativeControl.writeNext(); } catch (IOException | RuntimeException error) { nativeClean = false; throw error; }
            ordinal++;
            if (b[0] != ordinal || b[5] < 0 || b[6] < b[5] || b[4] != 0 || b[3] != b[2]) nativeClean = false;
            if (nativeClean) full++;
            Tbs5530Fx2Reload.Boundary boundary = new Tbs5530Fx2Reload.Boundary((int)b[0], (int)b[1], (int)b[2], (int)b[3], (int)b[4], b[5], b[6]);
            if (audit == null) { nativeClean = false; throw new IOException("Native boundary audit unavailable"); }
            try { audit.recorded(boundary); }
            catch (IOException | RuntimeException error) { nativeClean = false; throw error; }
            return boundary;
        }
        UsbInterface intf(int i) { return device.getInterface(i); }
        UsbEndpoint ep(int i, int e) { return intf(i).getEndpoint(e); }
        public byte[] rawDescriptors() throws IOException { worker(); return connection.getRawDescriptors(); }
        public int interfaceCount() { return device.getInterfaceCount(); }
        public int interfaceId(int i) { return intf(i).getId(); }
        public int alternateSetting(int i) { return intf(i).getAlternateSetting(); }
        public int endpointCount(int i) { return intf(i).getEndpointCount(); }
        public int endpointAddress(int i, int e) { return ep(i,e).getAddress(); }
        public int endpointDirection(int i, int e) { return ep(i,e).getDirection(); }
        public int endpointType(int i, int e) { return ep(i,e).getType(); }
        public int endpointMaxPacketSize(int i, int e) { return ep(i,e).getMaxPacketSize(); }
        public int negotiatedUsbSpeed() { return UsbHiSpeedBulk.negotiatedSpeed(connection); }
        public int controlTransfer(int type, int request, int value, int index, byte[] bytes, int offset, int length, int timeout) throws IOException {
            worker();
            if (request != 0xa0) return connection.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
            throw new IOException("Operational arbitrary A0 forbidden");
        }
        public int bulkTransfer(int i, int address, byte[] bytes, int length, int timeout) throws IOException {
            worker(); if (!claimed || address != 0x82 || receiveFactory == null) throw new IOException("Native4096 claimed receive required");
            if (receive == null) {
                UsbEndpoint endpoint = null;
                for (int e = 0; e < intf(i).getEndpointCount(); e++) if (ep(i,e).getAddress() == address) endpoint = ep(i,e);
                if (endpoint == null) throw new IOException("Endpoint82 unavailable");
                receive = receiveFactory.create(connection, intf(i), endpoint);
            }
            return receive.read(bytes, length, timeout);
        }
        public int lastBulkErrno() { return receive == null ? -1 : receive.errno(); }
        public boolean claim(int i, boolean force) throws IOException {
            worker(); if (claimed || force) throw new IOException("One non-forced operational claim required");
            claimed = connection.claimInterface(intf(i), false); return claimed;
        }
        public boolean release(int i) throws IOException {
            worker(); if (receive != null) receive.close(); return connection.releaseInterface(intf(i));
        }
        public void close() throws IOException {
            if (closed) return;
            worker();
            if (receive != null) receive.close();
            if (nativeControl != null) nativeControl.close();
            connection.close(); closed = true;
        }
    }
}
