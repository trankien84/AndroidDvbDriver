// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in debug acquisition through the existing binding/coordinator. Never uploads firmware. */
public final class Tbs5530OperationalHandoff implements AutoCloseable {
    public interface Consent { void requireCurrent() throws IOException; }
    private final Tbs5530Session session;
    private final Tbs5530UsbBinding binding;
    private final Consent consent;
    private Tbs5530TsSmoke.ReadApi readApi = Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO;
    private final String leaseNonce = java.util.UUID.randomUUID().toString();
    private Tbs5530UsbBinding.Device device;
    private final AtomicReference<Tbs5530OperationalProfile> profile = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent) throws IOException {
        this(context, runtime, new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext()), consent);
    }
    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent,
                                    Tbs5530TsSmoke.ReadApi readApi) throws IOException {
        this(context, runtime, manager(context, readApi), consent);
        this.readApi = java.util.Objects.requireNonNull(readApi);
    }
    private static Tbs5530AndroidUsbFacade.Manager manager(Context context, Tbs5530TsSmoke.ReadApi api) throws IOException {
        if (api == Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO) return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext());
        if (api != Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096 || android.os.Build.VERSION.SDK_INT < 26) {
            throw new IOException("Bounded queued diagnostic requires API26+");
        }
        return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext(),
                (connection, endpoint) -> new Tbs5530QueuedUsbBackend(connection, endpoint, context.getFilesDir()));
    }
    Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime,
                             Tbs5530AndroidUsbFacade.Manager manager, Consent consent) throws IOException {
        this.consent = java.util.Objects.requireNonNull(consent);
        session = new Tbs5530Session();
        Tbs5530UsbBinding.Facade facade = strictFacade(context, manager, consent, profile);
        binding = new Tbs5530UsbBinding(session, facade, runtime, runtime, failure::set, 30000, 10000);
    }

    static Tbs5530UsbBinding.Facade strictFacade(Context context, Tbs5530AndroidUsbFacade.Manager manager,
                                                Consent consent, AtomicReference<Tbs5530OperationalProfile> observation) {
        Tbs5530AndroidUsbFacade android = new Tbs5530AndroidUsbFacade(context, manager, 0, raw -> {
            consent.requireCurrent();
            Tbs5530OperationalProfile fresh = Tbs5530OperationalProfile.observe(raw.rawDescriptors());
            observation.set(fresh);
            fresh.requireOperational();
            fresh.requireMetadata(raw);
            observation.set(fresh.withSpeed(raw.negotiatedUsbSpeed()));
            consent.requireCurrent();
        });
        return new Tbs5530UsbBinding.Facade() {
            public List<Tbs5530UsbBinding.Device> devices() throws IOException { return android.devices(); }
            public boolean hasPermission(Tbs5530UsbBinding.Device device) throws IOException {
                consent.requireCurrent(); requireSingle(android.devices(), device); return android.hasPermission(device);
            }
            public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device device, String correlation,
                                                                   Tbs5530UsbBinding.PermissionCallback callback) throws IOException {
                consent.requireCurrent(); requireSingle(android.devices(), device);
                return android.requestPermission(device, correlation, callback);
            }
            public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events events) throws IOException {
                return android.listen(events);
            }
            public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device device) throws IOException {
                consent.requireCurrent(); requireSingle(android.devices(), device); observation.set(null);
                Tbs5530LifecycleCoordinator.Connection connection = android.open(device);
                Thread owner = Thread.currentThread();
                return new Tbs5530LifecycleCoordinator.Connection() {
                    public void requireControlWorker() throws IOException {
                        if (Thread.currentThread() != owner) throw new IOException("Operational owner worker changed");
                        connection.requireControlWorker(); consent.requireCurrent();
                        requireSingle(android.devices(), device);
                        if (!android.hasPermission(device)) throw new IOException("Operational permission lost");
                    }
                    public void claim(Tbs5530LifecycleCoordinator.Claim claim) throws IOException {
                        requireControlWorker(); connection.claim(claim); requireControlWorker();
                    }
                    public int controlTransfer(int type, int request, int value, int index, byte[] bytes,
                                               int offset, int length, int timeout) throws IOException {
                        requireControlWorker();
                        if (request == 0xa0) throw new IOException("Firmware writes forbidden on operational owner");
                        int result = connection.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                        requireControlWorker(); return result;
                    }
                    public int bulkTransfer(byte[] bytes, int length, int timeout) throws IOException {
                        requireControlWorker(); int result = connection.bulkTransfer(bytes, length, timeout);
                        requireControlWorker(); return result;
                    }
                    public Tbs5530Descriptors.Endpoint streamEndpoint() throws IOException {
                        requireControlWorker(); return connection.streamEndpoint();
                    }
                    public int lastBulkErrno() throws IOException { requireControlWorker(); return connection.lastBulkErrno(); }
                    public void prepareBulkIn82() throws IOException {
                        requireControlWorker(); connection.prepareBulkIn82(); requireControlWorker();
                    }
                    public void cancel() throws IOException { connection.cancel(); }
                    public void release() throws IOException { connection.release(); }
                    public void close() throws IOException { connection.close(); }
                };
            }
        };
    }

    static void requireSingle(List<Tbs5530UsbBinding.Device> devices, Tbs5530UsbBinding.Device expected) throws IOException {
        int count = 0;
        for (Tbs5530UsbBinding.Device device : devices) if (device.vendorId == 0x734c && device.productId == 0x5530) count++;
        if (count != 1 || !devices.contains(expected) || expected.vendorId != 0x734c || expected.productId != 0x5530) {
            throw new IOException("Exactly one fresh TBS5530 attachment required");
        }
    }

    public void start(Tbs5530UsbBinding.Device device) throws IOException {
        consent.requireCurrent(); profile.set(null); this.device = device; binding.start(device);
    }
    public boolean pending() { return binding.isPending(); }
    public Tbs5530OperationalProfile observation() { return profile.get(); }
    public Throwable failure() { return failure.get() == null ? session.getFailure() : failure.get(); }
    public boolean operationalOwner() {
        return (session.getState() == Tbs5530Session.State.CONNECTED || session.getState() == Tbs5530Session.State.READY)
                && session.isInterfaceClaimed() && !pending();
    }
    Tbs5530Session session() { return session; }
    Tbs5530ControlIo controlIo() throws IOException { return binding.controlIo(); }
    Tbs5530BulkTransport bulkIo() throws IOException { return binding.bulkIo(); }
    public Tbs5530TsSmoke smoke(Tbs5530TsSmoke.Permission permission, Tbs5530TsSmoke.Clock clock) throws IOException {
        return smoke(permission, clock, (bytes, length) -> { });
    }
    public Tbs5530TsSmoke smoke(Tbs5530TsSmoke.Permission permission, Tbs5530TsSmoke.Clock clock,
                               Tbs5530TsSmoke.ChunkObserver observer) throws IOException {
        session.requireUsable(session.getGeneration());
        long generation = session.getGeneration();
        return new Tbs5530TsSmoke(session, generation, new Tbs5530Bridge(controlIo(), session, generation), bulkIo(), permission, clock, readApi, observer);
    }
    /** Opaque binding, kept in private approval files; raw USB identity never enters project logs. */
    public String approvalIdentity() throws IOException {
        consent.requireCurrent(); session.requireUsable(generation());
        if (!operationalOwner() || profile.get() == null || device == null) throw new IOException("No fresh operational owner");
        String text = leaseNonce + "|" + device.path + "|" + device.id + "|" + generation() + "|" + profile.get().descriptorHash;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
    }
    public long generation() { return session.getGeneration(); }
    public IOException cleanupFailure() { return session.getCleanupFailure(); }
    public void revoke() { binding.cancel(); }
    @Override public void close() throws IOException { binding.close(); }
}
