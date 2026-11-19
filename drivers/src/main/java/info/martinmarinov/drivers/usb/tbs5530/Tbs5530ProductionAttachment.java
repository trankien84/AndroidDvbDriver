// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.util.UUID;

/** A receiver spans permission, cold bootstrap, handoff and the operational owner's lifetime. */
final class Tbs5530ProductionAttachment implements AutoCloseable {
    private static final long PERMISSION_MILLIS = 60_000;
    private final Context context;
    private final UsbManager manager;
    private final Tbs5530AndroidUsbFacade facade;
    private final Object events = new Object();
    private final Tbs5530AttachmentEpoch epoch;
    private final Tbs5530UsbBinding.Registration observer;
    private volatile Runnable removed;
    private UsbDevice selected;
    private boolean closed;

    Tbs5530ProductionAttachment(Context context, UsbDevice initial) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("USB discovery requires a worker thread");
        this.context = context.getApplicationContext();
        this.manager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        if (manager == null) throw new IOException("USB host service unavailable");
        selected = initial;
        epoch = new Tbs5530AttachmentEpoch(identity(initial));
        facade = new Tbs5530AndroidUsbFacade(this.context);
        observer = facade.listen(new Tbs5530UsbBinding.Events() {
            @Override public void attached(Tbs5530UsbBinding.Device d) { event(d, false); }
            @Override public void detached(Tbs5530UsbBinding.Device d) { event(d, true); }
        });
    }

    private void event(Tbs5530UsbBinding.Device d, boolean detach) {
        if (d == null || d.vendorId != 0x734c || d.productId != 0x5530) return;
        if (detach) epoch.detach(identity(d)); else epoch.attach(identity(d));
        synchronized (events) { events.notifyAll(); }
        Runnable callback = removed;
        if (epoch.invalid() && callback != null) callback.run();
    }

    void onRemoval(Runnable callback) {
        removed = callback;
        if (epoch.invalid()) callback.run();
    }

    UsbDevice prepare(boolean allowBootstrap) throws IOException, InterruptedException {
        permission(selected);
        Tbs5530OperationalProfile profile;
        try (Connection connection = connect(selected)) {
            profile = Tbs5530ProductionFirmware.profile(connection.raw.getRawDescriptors());
            epoch.seal(identity(selected));
            requireCurrent();
            if (profile.kind == Tbs5530OperationalProfile.Kind.FX2_DEFAULT) {
                if (!allowBootstrap) throw new IOException("Operational attachment required for reopen");
                Tbs5530FirmwarePlan plan = Tbs5530ProductionFirmware.plan(Tbs5530ProductionFirmware.load(context));
                // Descriptor bytes cannot change between approval and the first RAM write.
                if (!profile.descriptorHash.equals(Tbs5530RawDescriptors.parse(connection.raw.getRawDescriptors()).sha256))
                    throw new IOException("Default descriptor changed before bootstrap");
                try {
                    Tbs5530ProductionFirmware.bootstrap(plan, connection.raw::controlTransfer, epoch::require,
                            Thread::sleep, epoch::beginTransition);
                } catch (IOException e) { throw new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.BOOTSTRAP,
                        "Cold RAM bootstrap failed; no retry", e); }
            } else validateAndroid(selected);
        }
        if (profile.kind == Tbs5530OperationalProfile.Kind.FX2_DEFAULT) {
            UsbDevice operational = awaitOperational();
            permission(operational);
            try (Connection connection = connect(operational)) {
                if (Tbs5530ProductionFirmware.profile(connection.raw.getRawDescriptors()).kind
                        != Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE)
                    throw new IOException("Bootstrap did not produce operational descriptors");
                validateAndroid(operational);
                epoch.acceptOperational(identity(operational));
                selected = operational;
                requireCurrent();
            }
        }
        return selected;
    }

    private UsbDevice awaitOperational() throws IOException, InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + PERMISSION_MILLIS;
        while (true) {
            epoch.require();
            if (epoch.oldDetached()) {
                UsbDevice candidate = null;
                for (UsbDevice d : manager.getDeviceList().values()) if (target(d)) {
                    if (candidate != null) throw new IOException("Ambiguous TBS5530 operational handoff");
                    candidate = d;
                }
                if (candidate != null) {
                    epoch.observeCandidate(identity(candidate));
                    return candidate;
                }
            }
            waitEvent(deadline, Tbs5530ProductionFailure.Kind.HANDOFF_TIMEOUT);
        }
    }

    private void permission(UsbDevice device) throws IOException, InterruptedException {
        epoch.require();
        requirePresent(device);
        if (manager.hasPermission(device)) return;
        final boolean[] result = {false, false};
        String correlation = UUID.randomUUID().toString();
        Tbs5530UsbBinding.Device expected = metadata(device);
        Tbs5530UsbBinding.Registration request = facade.requestPermission(expected, correlation, (key, reported, granted) -> {
            if (!correlation.equals(key) || reported == null || !identity(reported).equals(identity(device))) return;
            synchronized (events) { result[0] = true; result[1] = Boolean.TRUE.equals(granted); events.notifyAll(); }
        });
        try {
            long deadline = SystemClock.elapsedRealtime() + PERMISSION_MILLIS;
            while (true) {
                epoch.require();
                requirePresent(device);
                synchronized (events) {
                    if (result[0]) {
                        if (!result[1] || !manager.hasPermission(device)) throw new Tbs5530ProductionFailure(
                                Tbs5530ProductionFailure.Kind.PERMISSION, "USB permission denied");
                        break;
                    }
                }
                waitEvent(deadline, Tbs5530ProductionFailure.Kind.PERMISSION_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally { request.close(); }
    }

    private void waitEvent(long deadline, Tbs5530ProductionFailure.Kind timeout) throws IOException, InterruptedException {
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (remaining <= 0) throw new Tbs5530ProductionFailure(timeout, "USB wait timed out");
        synchronized (events) { events.wait(Math.min(remaining, 100)); }
    }

    void requireCurrent() throws IOException {
        epoch.require();
        requirePresent(selected);
        if (!manager.hasPermission(selected)) throw new Tbs5530ProductionFailure(
                Tbs5530ProductionFailure.Kind.PERMISSION, "USB permission was revoked");
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("TBS5530 operation interrupted");
    }

    UsbDeviceConnection openControl() throws IOException {
        requireCurrent();
        UsbDeviceConnection result = manager.openDevice(selected);
        if (result == null) throw new IOException("Cannot open TBS5530 control connection");
        try {
            requireCurrent();
            if (Tbs5530ProductionFirmware.profile(result.getRawDescriptors()).kind
                    != Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE) throw new IOException("Operational descriptor changed");
            validateAndroid(selected);
            return result;
        } catch (IOException | RuntimeException e) { result.close(); throw e; }
    }

    UsbDeviceConnection openStream() throws IOException { return openControl(); }

    private Connection connect(UsbDevice device) throws IOException {
        epoch.require(); requirePresent(device);
        UsbDeviceConnection raw = manager.openDevice(device);
        if (raw == null) throw new IOException("Cannot open TBS5530 USB device");
        return new Connection(raw);
    }
    private static final class Connection implements AutoCloseable {
        final UsbDeviceConnection raw;
        Connection(UsbDeviceConnection raw) { this.raw = raw; }
        @Override public void close() { raw.close(); }
    }

    private void requirePresent(UsbDevice expected) throws IOException {
        UsbDevice current = manager.getDeviceList().get(expected.getDeviceName());
        if (current == null || !identity(expected).equals(identity(current)) || !target(current))
            throw new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.REMOVED,
                    "TBS5530 attachment is no longer present");
    }

    static void validateAndroid(UsbDevice device) throws IOException {
        if (device.getConfigurationCount() != 1 || device.getInterfaceCount() != 1)
            throw new IOException("Unexpected TBS5530 interface tree");
        UsbInterface i = device.getInterface(0);
        if (i.getId() != 0 || i.getAlternateSetting() != 0 || i.getEndpointCount() != 2
                || i.getInterfaceClass() != 255 || i.getInterfaceSubclass() != 0 || i.getInterfaceProtocol() != 0)
            throw new IOException("Unexpected TBS5530 interface");
        boolean stream = false, control = false;
        for (int n = 0; n < 2; n++) {
            android.hardware.usb.UsbEndpoint ep = i.getEndpoint(n);
            if (ep.getType() != 2 || ep.getDirection() != 128 || ep.getMaxPacketSize() != 512)
                throw new IOException("Unexpected TBS5530 bulk endpoint");
            if (ep.getAddress() == 0x82 && !stream) stream = true;
            else if (ep.getAddress() == 0x81 && !control) control = true;
            else throw new IOException("Unexpected TBS5530 endpoint address");
        }
    }

    static boolean target(UsbDevice d) { return d != null && d.getVendorId() == 0x734c && d.getProductId() == 0x5530; }
    private static Tbs5530UsbBinding.Device metadata(UsbDevice d) {
        return new Tbs5530UsbBinding.Device(d.getDeviceName(), d.getDeviceId(), d.getVendorId(), d.getProductId());
    }
    private static String identity(UsbDevice d) { return d.getDeviceName() + "|" + d.getDeviceId(); }
    private static String identity(Tbs5530UsbBinding.Device d) { return d.path + "|" + d.id; }

    @Override public void close() throws IOException {
        synchronized (events) { if (closed) return; closed = true; epoch.close(); events.notifyAll(); }
        observer.close();
    }
}
