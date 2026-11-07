// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Events;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.PermissionCallback;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Registration;

/**
 * Opt-in Android lifecycle adapter; construction performs no USB operation.
 * Receivers only decode events: the binding must queue and revalidate them on its worker.
 * Android cannot identify a missed reconnect that reuses every observable device field.
 * API 21-30 uses default mutability (IMMUTABLE, added in 23, loses USB fill-in extras).
 * API 31+ declares MUTABLE; API 33+ uses typed parcelables and NOT_EXPORTED receivers.
 * API 34+ requires a component or package for a mutable PendingIntent; setPackage
 * satisfies this rule without ALLOW_UNSAFE_IMPLICIT_INTENT, including API 37.
 * Before 33, the unpredictable request action/URI limits unsolicited callbacks;
 * only the binding's live enumeration/permission checks authorize acquisition.
 * @see <a href="https://developer.android.com/reference/android/app/PendingIntent">PendingIntent API</a>
 * @see <a href="https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents">Android 14 intent rules</a>
 */
public final class Tbs5530AndroidUsbFacade implements Tbs5530UsbBinding.Facade {
    /** Injection boundary: tests need neither UsbDevice objects nor an actual USB service. */
    public interface Manager {
        List<Device> devices() throws IOException;
        boolean hasPermission(Device device) throws IOException;
        void requestPermission(Device device, PendingIntent result) throws IOException;
        RawConnection open(Device device) throws IOException;
    }

    public interface RawConnection {
        default byte[] rawDescriptors() throws IOException { throw new IOException("Raw descriptors unavailable"); }
        int interfaceCount() throws IOException;
        int interfaceId(int index) throws IOException;
        int alternateSetting(int index) throws IOException;
        default int endpointCount(int interfaceIndex) throws IOException {
            throw new IOException("Endpoint descriptors are unavailable");
        }
        default int endpointAddress(int interfaceIndex, int endpointIndex) throws IOException {
            throw new IOException("Endpoint address is unavailable");
        }
        default int endpointDirection(int interfaceIndex, int endpointIndex) throws IOException {
            throw new IOException("Endpoint direction is unavailable");
        }
        default int endpointType(int interfaceIndex, int endpointIndex) throws IOException {
            throw new IOException("Endpoint type is unavailable");
        }
        default int endpointMaxPacketSize(int interfaceIndex, int endpointIndex) throws IOException {
            throw new IOException("Endpoint packet size is unavailable");
        }
        default int controlTransfer(int requestType, int request, int value, int index,
                                    byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
            throw new IOException("Control transfer is unavailable");
        }
        default int bulkTransfer(int interfaceIndex, int endpointAddress, byte[] buffer, int length,
                                 int timeoutMillis) throws IOException {
            throw new IOException("Bulk transfer is unavailable");
        }
        default int negotiatedUsbSpeed() { return -1; }
        default int lastBulkErrno() throws IOException { return -1; }
        default void prepareBulkIn82() throws IOException { throw new IOException("Endpoint preparation unavailable"); }
        boolean claim(int index, boolean force) throws IOException;
        boolean release(int index) throws IOException;
        void close() throws IOException;
    }

    private final Context application;
    private final Manager manager;
    private final int alternateSetting;
    interface Validation { void validate(RawConnection raw) throws IOException; }
    private final Validation validation;

    public Tbs5530AndroidUsbFacade(Context context) {
        this(context, new AndroidManager(application(context)), 0);
    }

    public Tbs5530AndroidUsbFacade(Context context, Manager manager) {
        this(context, manager, 0);
    }

    public Tbs5530AndroidUsbFacade(Context context, Manager manager, int alternateSetting) {
        this(context, manager, alternateSetting, raw -> { });
    }

    Tbs5530AndroidUsbFacade(Context context, Manager manager, int alternateSetting, Validation validation) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.application = application(context);
        this.manager = Objects.requireNonNull(manager, "manager");
        if (alternateSetting < 0 || alternateSetting > 255) throw new IllegalArgumentException("Invalid alternate setting");
        this.alternateSetting = alternateSetting;
    }

    private static Context application(Context context) {
        return Objects.requireNonNull(Objects.requireNonNull(context, "context")
                .getApplicationContext(), "applicationContext");
    }

    @Override public List<Device> devices() throws IOException {
        return new ArrayList<>(manager.devices());
    }

    @Override public boolean hasPermission(Device device) throws IOException {
        return manager.hasPermission(device);
    }

    static int permissionFlags(int sdk) {
        // USB's system sender fills EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED at send time.
        return PendingIntent.FLAG_ONE_SHOT | (sdk >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }

    static Intent permissionIntent(String packageName, String correlation, String nonce) {
        return new Intent(packageName + ".tbs5530.USB_PERMISSION." + nonce)
                .setPackage(packageName)
                .setData(new Uri.Builder().scheme("tbs5530-permission").authority(packageName)
                        .appendPath(correlation).appendPath(nonce).build());
    }

    @Override public Registration requestPermission(Device device, String correlation,
                                                    PermissionCallback callback) throws IOException {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(callback, "callback");
        if (correlation == null || correlation.isEmpty()) throw new IllegalArgumentException("Missing correlation");
        Intent target = permissionIntent(application.getPackageName(), correlation, UUID.randomUUID().toString());
        IntentFilter filter = new IntentFilter(target.getAction());
        filter.addDataScheme(target.getData().getScheme());
        filter.addDataAuthority(target.getData().getAuthority(), null);
        AtomicBoolean active = new AtomicBoolean(true);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!active.get() || intent == null || !target.getAction().equals(intent.getAction())
                        || !target.getData().equals(intent.getData())) return;
                Device reported = readDevice(intent);
                callback.result(correlation, reported, readGranted(intent, reported));
            }
        };
        PendingIntent pending = null;
        boolean registered = false;
        try {
            register(receiver, filter);
            registered = true;
            pending = PendingIntent.getBroadcast(application, 0, target, permissionFlags(Build.VERSION.SDK_INT));
            PendingIntent ownedPending = pending;
            Registration registration = registration(receiver, active, ownedPending);
            try {
                manager.requestPermission(device, pending);
            } catch (IOException | RuntimeException failure) {
                try { registration.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            return registration;
        } catch (IOException | RuntimeException failure) {
            if (active.getAndSet(false)) {
                if (pending != null) {
                    try { pending.cancel(); }
                    catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                }
                if (registered) {
                    try { application.unregisterReceiver(receiver); }
                    catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                }
            }
            throw io("Cannot request USB permission", failure);
        }
    }

    @Override public Registration listen(Events events) throws IOException {
        Objects.requireNonNull(events, "events");
        AtomicBoolean active = new AtomicBoolean(true);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!active.get() || intent == null) return;
                String action = intent.getAction();
                if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                        && !UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) return;
                Device device = readDevice(intent);
                if (device == null) return;
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) events.attached(device);
                else events.detached(device);
            }
        };
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        try { register(receiver, filter); }
        catch (RuntimeException failure) { throw io("Cannot listen for USB events", failure); }
        return registration(receiver, active, null);
    }

    private void register(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else application.registerReceiver(receiver, filter);
    }

    private Registration registration(BroadcastReceiver receiver, AtomicBoolean active, PendingIntent pending) {
        return () -> {
            if (!active.getAndSet(false)) return;
            try {
                if (pending != null) pending.cancel();
            } finally {
                try { application.unregisterReceiver(receiver); }
                catch (RuntimeException failure) { throw io("Cannot unregister USB receiver", failure); }
            }
        };
    }

    static Boolean readGranted(Intent intent, Device reported) {
        if (reported == null) return null;
        try {
            Bundle extras = intent.getExtras();
            Object value = extras == null ? null : extras.get(UsbManager.EXTRA_PERMISSION_GRANTED);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    static Device readDevice(Intent intent) {
        try {
            Object raw = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                    : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (!(raw instanceof UsbDevice)) return null;
            return identity((UsbDevice) raw);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static Device identity(UsbDevice device) {
        return new Device(device.getDeviceName(), device.getDeviceId(), device.getVendorId(), device.getProductId());
    }

    private void requireCurrent(Device device) throws IOException {
        if (!manager.devices().contains(device) || !manager.hasPermission(device)) {
            throw new IOException("USB attachment or permission changed");
        }
    }

    private static void requireWorker() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("USB acquisition requires a worker");
    }

    @Override public Tbs5530LifecycleCoordinator.Connection open(Device device) throws IOException {
        requireWorker();
        Objects.requireNonNull(device, "device");
        requireCurrent(device);
        RawConnection raw = manager.open(device);
        if (raw == null) throw new IOException("USB open returned no connection");
        try {
            requireCurrent(device);
            validation.validate(raw);
            requireCurrent(device);
            int selected = -1;
            for (int index = 0, count = raw.interfaceCount(); index < count; index++) {
                if (raw.interfaceId(index) == 0 && raw.alternateSetting(index) == alternateSetting) {
                    if (selected >= 0) throw new IOException("Ambiguous USB interface 0 alternate setting");
                    selected = index;
                }
            }
            if (selected < 0) throw new IOException("USB interface 0 alternate setting unavailable");
            List<Tbs5530Descriptors.Endpoint> endpoints = new ArrayList<>();
            int count = raw.endpointCount(selected);
            if (count < 0 || count > 30) throw new IOException("Invalid USB endpoint count");
            for (int endpoint = 0; endpoint < count; endpoint++) {
                endpoints.add(new Tbs5530Descriptors.Endpoint(0, alternateSetting,
                        raw.endpointAddress(selected, endpoint), raw.endpointDirection(selected, endpoint),
                        raw.endpointType(selected, endpoint), raw.endpointMaxPacketSize(selected, endpoint)));
            }
            Tbs5530Descriptors.Endpoint stream = Tbs5530Descriptors.selectBulkEndpoint(endpoints, alternateSetting);
            return new OwnedConnection(raw, selected, device, stream);
        } catch (IOException | RuntimeException failure) {
            try { raw.close(); } catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw io("Cannot acquire USB interface", failure);
        }
    }

    private final class OwnedConnection implements Tbs5530LifecycleCoordinator.Connection {
        private final RawConnection raw;
        private final int index;
        private final Device device;
        private final Tbs5530Descriptors.Endpoint stream;
        private boolean claimed, releaseAttempted, closed;

        OwnedConnection(RawConnection raw, int index, Device device, Tbs5530Descriptors.Endpoint stream) {
            this.raw = raw;
            this.index = index;
            this.device = device;
            this.stream = stream;
        }

        private void requireClaimed() throws IOException {
            if (!claimed || closed || releaseAttempted) throw new IOException("USB connection is not claimed and active");
        }

        @Override public Tbs5530Descriptors.Endpoint streamEndpoint() throws IOException {
            requireWorker();
            synchronized (this) {
                requireClaimed();
                return stream;
            }
        }

        @Override public void requireControlWorker() throws IOException { requireWorker(); }

        @Override public int controlTransfer(int requestType, int request, int value, int index,
                                             byte[] buffer, int offset, int length,
                                             int timeoutMillis) throws IOException {
            // Reject main-thread calls before acquiring a lock that a native transfer may hold.
            requireControlWorker();
            synchronized (this) {
                requireClaimed();
                if (requestType < 0 || requestType > 255 || request < 0 || request > 255
                        || value < 0 || value > 65535 || index < 0 || index > 65535
                        || offset < 0 || length < 0 || length > 65535 || timeoutMillis <= 0
                        || (buffer == null ? offset != 0 || length != 0
                        : offset > buffer.length || length > buffer.length - offset)) {
                    throw new IllegalArgumentException("Invalid USB control-transfer parameters");
                }
                return raw.controlTransfer(requestType, request, value, index, buffer, offset, length, timeoutMillis);
            }
        }

        @Override public synchronized void claim(Tbs5530LifecycleCoordinator.Claim receipt) throws IOException {
            requireWorker();
            if (closed || claimed || releaseAttempted) throw new IOException("USB claim is no longer available");
            requireCurrent(device);
            if (!raw.claim(index, false)) throw new IOException("USB interface claim failed");
            claimed = true;
            receipt.acquired();
            requireCurrent(device);
        }

        @Override public int bulkTransfer(byte[] buffer, int length, int timeoutMillis) throws IOException {
            requireWorker();
            synchronized (this) {
                requireClaimed();
                requireCurrent(device);
                if (buffer == null || length < 1 || length > buffer.length || length > 16384
                        || timeoutMillis < 1 || timeoutMillis > 2000) throw new IllegalArgumentException("Invalid bounded bulk read");
                int result = raw.bulkTransfer(index, stream.address, buffer, length, timeoutMillis);
                requireCurrent(device);
                return result;
            }
        }

        @Override public int lastBulkErrno() throws IOException {
            requireWorker(); synchronized (this) { requireClaimed(); requireCurrent(device); return raw.lastBulkErrno(); }
        }
        @Override public void prepareBulkIn82() throws IOException {
            requireWorker();
            synchronized (this) {
                requireClaimed(); requireCurrent(device); raw.prepareBulkIn82(); requireCurrent(device);
            }
        }
        @Override public void cancel() { /* Synchronous reads drain before resource cleanup. */ }

        @Override public synchronized void release() throws IOException {
            if (!claimed || releaseAttempted || closed) return;
            releaseAttempted = true;
            if (!raw.release(index)) throw new IOException("USB interface release failed");
        }

        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            raw.close();
        }
    }

    private static IOException io(String message, Throwable failure) {
        return failure instanceof IOException ? (IOException) failure : new IOException(message, failure);
    }

    interface ReceiveBackend extends AutoCloseable {
        int read(byte[] bytes, int length, int timeoutMillis) throws IOException;
        int errno();
        @Override void close() throws IOException;
    }
    interface ReceiveBackendFactory {
        ReceiveBackend create(UsbDeviceConnection connection, UsbEndpoint endpoint) throws IOException;
    }
    static final class AndroidManager implements Manager {
        private final Context application;
        private final ReceiveBackendFactory receiveFactory;
        AndroidManager(Context application) { this(application, null); }
        AndroidManager(Context application, ReceiveBackendFactory receiveFactory) {
            this.application = application; this.receiveFactory = receiveFactory;
        }

        private UsbManager usb() throws IOException {
            UsbManager usb = (UsbManager) application.getSystemService(Context.USB_SERVICE);
            if (usb == null) throw new IOException("USB service unavailable");
            return usb;
        }

        private UsbDevice resolve(Device expected) throws IOException {
            return resolvePresentDevice(expected, usb().getDeviceList().values());
        }

        static UsbDevice resolvePresentDevice(Device expected, Iterable<UsbDevice> devices) throws IOException {
            for (UsbDevice candidate : devices) {
                if (expected.equals(identity(candidate))) return candidate;
            }
            // Re-enumeration can race the binding's preceding presence check.
            throw new Tbs5530UsbBinding.AttachmentChangedException();
        }

        @Override public List<Device> devices() throws IOException {
            List<Device> result = new ArrayList<>();
            for (UsbDevice device : usb().getDeviceList().values()) result.add(identity(device));
            return result;
        }

        @Override public boolean hasPermission(Device device) throws IOException {
            return usb().hasPermission(resolve(device));
        }

        @Override public void requestPermission(Device device, PendingIntent result) throws IOException {
            usb().requestPermission(resolve(device), result);
        }

        @Override public RawConnection open(Device device) throws IOException {
            UsbDevice selected = resolve(device);
            UsbDeviceConnection connection = usb().openDevice(selected);
            if (connection == null) return null;
            return new RawConnection() {
                @Override public byte[] rawDescriptors() { return connection.getRawDescriptors(); }
                @Override public int negotiatedUsbSpeed() {
                    return info.martinmarinov.usbxfer.UsbHiSpeedBulk.negotiatedSpeed(connection);
                }
                private UsbInterface descriptor(int index) { return selected.getInterface(index); }
                @Override public int interfaceCount() { return selected.getInterfaceCount(); }
                @Override public int interfaceId(int index) { return descriptor(index).getId(); }
                @Override public int alternateSetting(int index) { return descriptor(index).getAlternateSetting(); }
                private UsbEndpoint endpoint(int interfaceIndex, int endpointIndex) {
                    return descriptor(interfaceIndex).getEndpoint(endpointIndex);
                }
                @Override public int endpointCount(int interfaceIndex) { return descriptor(interfaceIndex).getEndpointCount(); }
                @Override public int endpointAddress(int interfaceIndex, int endpointIndex) {
                    return endpoint(interfaceIndex, endpointIndex).getAddress();
                }
                @Override public int endpointDirection(int interfaceIndex, int endpointIndex) {
                    return endpoint(interfaceIndex, endpointIndex).getDirection();
                }
                @Override public int endpointType(int interfaceIndex, int endpointIndex) {
                    return endpoint(interfaceIndex, endpointIndex).getType();
                }
                @Override public int endpointMaxPacketSize(int interfaceIndex, int endpointIndex) {
                    return endpoint(interfaceIndex, endpointIndex).getMaxPacketSize();
                }
                @Override public int controlTransfer(int requestType, int request, int value, int index,
                                                     byte[] buffer, int offset, int length, int timeoutMillis) {
                    return connection.controlTransfer(requestType, request, value, index,
                            buffer, offset, length, timeoutMillis);
                }
                @Override public int bulkTransfer(int interfaceIndex, int address, byte[] buffer, int length,
                                                   int timeoutMillis) throws IOException {
                    UsbInterface intf = descriptor(interfaceIndex);
                    UsbEndpoint selectedEndpoint = null;
                    for (int ep = 0; ep < intf.getEndpointCount(); ep++) {
                        UsbEndpoint candidate = intf.getEndpoint(ep);
                        if (candidate.getAddress() == address) {
                            if (selectedEndpoint != null) throw new IOException("Duplicate bulk endpoint");
                            selectedEndpoint = candidate;
                        }
                    }
                    if (address != 0x82 || selectedEndpoint == null || selectedEndpoint.getDirection() != 128
                            || selectedEndpoint.getType() != 2) throw new IOException("Bulk IN 0x82 unavailable");
                    if (receiveFactory != null) {
                        if (receive == null) receive = receiveFactory.create(connection, selectedEndpoint);
                        int count = receive.read(buffer, length, timeoutMillis);
                        bulkErrno = receive.errno();
                        return count;
                    }
                    info.martinmarinov.usbxfer.UsbHiSpeedBulk.BulkReadResult result =
                            info.martinmarinov.usbxfer.UsbHiSpeedBulk.readBulkIn82(connection, buffer, length, timeoutMillis);
                    bulkErrno = result.errno;
                    return result.count;
                }
                private ReceiveBackend receive;
                private int bulkErrno = -1;
                @Override public int lastBulkErrno() { return bulkErrno; }
                @Override public void prepareBulkIn82() throws IOException {
                    info.martinmarinov.usbxfer.UsbHiSpeedBulk.prepareBulkIn82(connection);
                }
                @Override public boolean claim(int index, boolean force) {
                    return connection.claimInterface(descriptor(index), force);
                }
                @Override public boolean release(int index) throws IOException {
                    if (receive != null) receive.close();
                    return connection.releaseInterface(descriptor(index));
                }
                @Override public void close() throws IOException {
                    if (receive != null) receive.close();
                    connection.close();
                }
            };
        }
    }
}
