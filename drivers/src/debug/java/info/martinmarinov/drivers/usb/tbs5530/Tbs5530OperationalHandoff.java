// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in debug acquisition through the existing binding/coordinator; the default owner forbids every A0. */
public final class Tbs5530OperationalHandoff implements AutoCloseable {
    public interface Consent { void requireCurrent() throws IOException; }
    public interface SonyReadbackHook { void beforeFirstBulk() throws IOException; }
    private SonyReadbackHook sonyReadbackHook;
    private final Tbs5530Session session;
    private final Tbs5530UsbBinding binding;
    private final Consent consent;
    private Tbs5530TsSmoke.ReadApi readApi = Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO;
    private Tbs5530ControlMirror controlMirror;
    private NativeConfiguration nativeConfiguration;
    private java.io.File signalDirectory;
    private volatile Tbs5530SignalSampler signalSampler;
    public void signalMonitorAudit(java.io.File directory) throws IOException {
        if (device != null || signalDirectory != null || directory == null || !directory.isDirectory())
            throw new IOException("Signal audit must bind before acquisition");
        signalDirectory = directory;
    }
    public int signalSampleCount() { return signalSampler == null ? 0 : signalSampler.samples().size(); }
    public String signalMonitorFailure() { return signalSampler == null ? null : signalSampler.failure(); }
    public boolean signalLockLost() { return signalSampler != null && signalSampler.lockLost(); }
    private void requireMonitorLock() throws IOException {
        Tbs5530SignalSampler sampler = signalSampler;
        if (sampler != null && sampler.lockLost()) throw new IOException("B4_LOCK_LOST_OVER_2_SECONDS");
    }
    private volatile Tbs5530Fx2Reload reload;
    private boolean reloadComplete;
    private final java.util.List<Tbs5530Fx2Reload.Boundary> reloadBoundaries = new java.util.ArrayList<>();
    private static final class NativeConfiguration {
        final String runId;
        volatile Tbs5530TsSmoke.Limits limits;
        volatile Consent pollGuard;
        volatile Tbs5530NativeUsbfsBackend backend;
        volatile Tbs5530NativeStreamBackend streamBackend;
        volatile boolean k36AutoTuneConfirmed;
        boolean reloadRequired;
        Tbs5530ReloadAndroidManager reloadManager;
        NativeConfiguration(String runId) {
            if (runId == null || !runId.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid diagnostic run ID");
            this.runId = runId;
        }
    }
    private final String leaseNonce = java.util.UUID.randomUUID().toString();
    private Tbs5530UsbBinding.Device device;
    private final AtomicReference<Tbs5530OperationalProfile> profile = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent) throws IOException {
        this(context, runtime, new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext()), consent);
    }
    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent,
                                    Tbs5530TsSmoke.ReadApi readApi) throws IOException {
        this(context, runtime, consent, readApi, java.util.UUID.randomUUID().toString());
    }
    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent,
                                    Tbs5530TsSmoke.ReadApi readApi, String runId) throws IOException {
        this(context, runtime, consent, readApi, new NativeConfiguration(runId));
    }
    public Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent,
                                     Tbs5530TsSmoke.ReadApi readApi, String runId, boolean reloadRequired) throws IOException {
        this(context, runtime, consent, readApi, reloadConfiguration(runId, readApi, reloadRequired));
    }
    private static NativeConfiguration reloadConfiguration(String runId, Tbs5530TsSmoke.ReadApi api, boolean required) throws IOException {
        if (required && api != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096 && api != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM)
            throw new IOException("Reload requires native4096 or the bounded native stream");
        if (api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM && !required) throw new IOException("B3 requires the B2 reload pipeline");
        NativeConfiguration configuration = new NativeConfiguration(runId); configuration.reloadRequired = required; return configuration;
    }
    private Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime, Consent consent,
                                     Tbs5530TsSmoke.ReadApi readApi, NativeConfiguration configuration) throws IOException {
        this(context, runtime, manager(context, readApi, consent, configuration), consent);
        this.readApi = java.util.Objects.requireNonNull(readApi);
        nativeConfiguration = configuration;
    }
    private static Tbs5530AndroidUsbFacade.Manager manager(Context context, Tbs5530TsSmoke.ReadApi api,
                                                           Consent consent, NativeConfiguration configuration) throws IOException {
        if (api == Tbs5530TsSmoke.ReadApi.SYNC_NATIVE_ERRNO) return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext());
        if (Tbs5530AutonomousPlans.nativeUrb(api)) {
            Tbs5530AndroidUsbFacade.ReceiveBackendFactory factory = (connection, intf, endpoint) -> {
                        if (api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM) {
                            Tbs5530NativeStreamBackend backend = new Tbs5530NativeStreamBackend(connection, intf, endpoint,
                                    context.getFilesDir(), configuration.runId + (configuration.limits != null && configuration.limits.b5Capture ? "-" + configuration.limits.b5Phase : ""), configuration.limits, () -> {
                                        consent.requireCurrent();
                                        if (configuration.pollGuard == null) throw new IOException("Stream watchdog unavailable");
                                        configuration.pollGuard.requireCurrent();
                                    });
                            configuration.streamBackend = backend;
                            return backend;
                        }
                        Tbs5530NativeUsbfsBackend backend = new Tbs5530NativeUsbfsBackend(connection, intf, endpoint, context.getFilesDir(),
                                configuration.runId, configuration.limits, () -> {
                                    consent.requireCurrent();
                                    if (configuration.pollGuard == null) throw new IOException("Native poll guard unavailable");
                                    configuration.pollGuard.requireCurrent();
                                });
                        configuration.backend = backend;
                        return backend;
                    };
            if (configuration.reloadRequired) {
                configuration.reloadManager = new Tbs5530ReloadAndroidManager(context.getApplicationContext(), factory);
                return configuration.reloadManager;
            }
            return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext(), factory);
        }
        if (api != Tbs5530TsSmoke.ReadApi.QUEUED_USB_REQUEST_8X4096 || android.os.Build.VERSION.SDK_INT < 26) {
            throw new IOException("Bounded queued diagnostic requires API26+");
        }
        return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext(),
                (connection, intf, endpoint) -> new Tbs5530QueuedUsbBackend(connection, endpoint, context.getFilesDir()));
    }
    Tbs5530OperationalHandoff(Context context, Tbs5530AndroidRuntime runtime,
                             Tbs5530AndroidUsbFacade.Manager manager, Consent consent) throws IOException {
        this.consent = java.util.Objects.requireNonNull(consent);
        session = new Tbs5530Session();
        Tbs5530UsbBinding.Facade facade = strictFacade(context, manager, consent, profile, this);
        binding = new Tbs5530UsbBinding(session, facade, runtime, runtime, failure::set, 30000, 10000);
    }

    public void reloadNativeAudit(java.io.File directory) throws IOException {
        if (device != null || nativeConfiguration == null || nativeConfiguration.reloadManager == null)
            throw new IOException("Reload audit must bind before acquisition");
        nativeConfiguration.reloadManager.audit(new Tbs5530Fx2NativeAudit(directory, "RELOAD"));
    }
    public void controlMirror(Tbs5530ControlMirror mirror) {
        if (device != null) throw new IllegalStateException("Control mirror must be configured before acquisition");
        controlMirror = java.util.Objects.requireNonNull(mirror);
    }

    static Tbs5530UsbBinding.Facade strictFacade(Context context, Tbs5530AndroidUsbFacade.Manager manager,
                                                Consent consent, AtomicReference<Tbs5530OperationalProfile> observation) {
        return strictFacade(context, manager, consent, observation, null);
    }
    private static Tbs5530UsbBinding.Facade strictFacade(Context context, Tbs5530AndroidUsbFacade.Manager manager,
                                                Consent consent, AtomicReference<Tbs5530OperationalProfile> observation,
                                                Tbs5530OperationalHandoff handoffOwner) {
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
                        if (handoffOwner != null) handoffOwner.requireReloadComplete();
                        int result = handoffOwner == null || handoffOwner.controlMirror == null
                                ? connection.controlTransfer(type, request, value, index, bytes, offset, length, timeout)
                                : handoffOwner.controlMirror.transfer("OPERATIONAL", handoffOwner.session.getGeneration(), connection::controlTransfer,
                                        type, request, value, index, bytes, offset, length, timeout);
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
    public void requireReloadComplete() throws IOException {
        if (nativeConfiguration != null && nativeConfiguration.reloadRequired) {
            if (!reloadComplete || reload == null) throw new IOException("B2 reload must complete before any frontend or stream control");
            reload.requireComplete();
        }
    }
    public boolean reloadWindow() { return reload != null && reload.window(); }
    public void reloadReenumerated() { Tbs5530Fx2Reload active = reload; if (active != null) active.reenumerated(); }
    public Tbs5530Fx2Reload.Snapshot reloadSnapshot() { return reload == null ? null : reload.snapshot(); }
    public java.util.List<Tbs5530Fx2Reload.Boundary> reloadNativeBoundaries() { return new java.util.ArrayList<>(reloadBoundaries); }
    public void reloadBeforeFrontend(Tbs5530FirmwarePlan plan, Tbs5530ReloadLedger ledger, String epochKey, Consent authorization,
                                    Tbs5530AndroidBootstrap.CleanProof bootstrapProof,
                                    Tbs5530Fx2Reload.Clock clock) throws IOException {
        consent.requireCurrent(); authorization.requireCurrent();
        if (nativeConfiguration == null || !nativeConfiguration.reloadRequired || nativeConfiguration.reloadManager == null
                || reload != null || !operationalOwner() || controlMirror == null || bootstrapProof == null)
            throw new IOException("B2 needs clean same-session original bootstrap132/API boundaries and drained lease, not host flags");
        requireOriginalBootstrapProof(bootstrapProof, nativeConfiguration.runId, epochKey);
        Tbs5530Fx2Reload.requireProfile(profile.get());
        byte[] image = Tbs5530Fx2Reload.lockedImage(plan);
        Tbs5530ReloadAndroidManager manager = nativeConfiguration.reloadManager;
        manager.prepareReload(image);
        Tbs5530Fx2Reload.Port port = new Tbs5530Fx2Reload.Port() {
            Tbs5530Fx2Reload.Boundary boundary;
            public int write(Tbs5530FirmwarePlan.Step step) throws IOException {
                return controlMirror.transfer("RELOAD", generation(), (type, request, value, index, bytes, offset, length, timeout) -> {
                    consent.requireCurrent(); authorization.requireCurrent(); session.requireUsable(generation());
                    boundary = manager.nextReload(); reloadBoundaries.add(boundary); return boundary.result;
                }, step.getRequestType(), step.getRequest(), step.getValue(), step.getIndex(), step.getPayload(), 0,
                        step.getPayload().length, step.getTimeoutMillis());
            }
            public Tbs5530Fx2Reload.Boundary boundary() { return boundary; }
            public void cleanup() { binding.cancel(); }
        };
        reload = new Tbs5530Fx2Reload(plan, () -> {
            consent.requireCurrent(); authorization.requireCurrent(); session.requireUsable(generation());
            if (!operationalOwner()) throw new IOException("Reload owner changed");
        }, clock, port, ledger, epochKey);
        session.transaction(generation(), () -> {
            try { reload.execute(); }
            catch (IOException error) { throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE, "B2 reload stopped; no retry", error); }
        });
        reloadComplete = true;
    }
    static void requireOriginalBootstrapProof(Tbs5530AndroidBootstrap.CleanProof proof, String runId, String epochKey) throws IOException {
        if (proof == null) throw new IOException("Original bootstrap proof missing");
        proof.require(runId, epochKey);
    }
    public boolean pending() { return binding.isPending(); }
    public Tbs5530OperationalProfile observation() { return profile.get(); }
    public Throwable failure() { return failure.get() == null ? session.getFailure() : failure.get(); }
    public boolean operationalOwner() {
        return (session.getState() == Tbs5530Session.State.CONNECTED || session.getState() == Tbs5530Session.State.READY)
                && session.isInterfaceClaimed() && !pending();
    }
    void requireSonyReadbackOwner() throws IOException {
        consent.requireCurrent(); session.requireUsable(generation());
        Tbs5530OperationalProfile current = profile.get();
        if (readApi != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096 || nativeConfiguration == null || !nativeConfiguration.k36AutoTuneConfirmed
                || !operationalOwner() || current == null || current.negotiatedUsbSpeed != 3 || current.streamPacketSize != 512 || !session.isFrontendLocked())
            throw new IOException("Exact idle native4096 locked operational owner required");
        NativeReceiveSnapshot nativeState = nativeReceiveSnapshot();
        if (nativeState != null && (nativeState.attempted != 0 || nativeState.submitted != 0 || nativeState.completed != 0
                || nativeState.outstanding != 0 || nativeState.quarantined)) throw new IOException("Readback cannot run after any URB attempt");
    }
    Tbs5530Session session() { return session; }
    Tbs5530ControlIo controlIo() throws IOException { return binding.controlIo(); }
    Tbs5530BulkTransport bulkIo() throws IOException { return binding.bulkIo(); }
    public void sonyReadbackHook(SonyReadbackHook hook) throws IOException {
        requireSonyReadbackOwner();
        if (sonyReadbackHook != null || nativeConfiguration.limits != null) throw new IOException("Readback hook already bound or receive started");
        sonyReadbackHook=java.util.Objects.requireNonNull(hook);
    }
    public Tbs5530TsSmoke smoke(Tbs5530TsSmoke.Permission permission, Tbs5530TsSmoke.Clock clock) throws IOException {
        return smoke(permission, clock, (bytes, length) -> { });
    }
    public Tbs5530TsSmoke smoke(Tbs5530TsSmoke.Permission permission, Tbs5530TsSmoke.Clock clock,
                               Tbs5530TsSmoke.ChunkObserver observer) throws IOException {
        session.requireUsable(session.getGeneration());
        long generation = session.getGeneration();
        Tbs5530TsSmoke.Permission effective = permission;
        if (Tbs5530AutonomousPlans.nativeUrb(readApi)) {
            effective = new Tbs5530TsSmoke.Permission() {
                public void consume(Tbs5530TsSmoke.Limits limits, long token) throws IOException {
                    if (nativeConfiguration.limits != null) throw new IOException("Native diagnostic already configured");
                    if (limits.explicitSetInterfaceAlt0 && (!nativeConfiguration.k36AutoTuneConfirmed
                            || !permission.explicitSetInterfaceAlt0Approved()))
                        throw new IOException("Exact K36 AUTO tune and explicit interface selection grant required");
                    if (limits.sonyTsOutputReadback && (!permission.sonyTsOutputReadbackApproved() || sonyReadbackHook == null))
                        throw new IOException("Bound Sony readback hook and grant required");
                    permission.consume(limits, token);
                    nativeConfiguration.pollGuard = () -> {
                        session.requireUsable(token);
                        if (!operationalOwner()) throw new IOException("Claimed active exclusive owner required");
                        if (readApi == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM
                                && (!session.isFrontendInitialized() || !session.isFrontendLocked() || !session.isStreaming()))
                            throw new IOException("B3 frontend lock or stream state lost");
                        permission.requireCurrent(token);
                        if (limits.b4Capture) requireMonitorLock();
                        if (limits.explicitSetInterfaceAlt0 && (!nativeConfiguration.k36AutoTuneConfirmed
                                || !permission.explicitSetInterfaceAlt0Approved()))
                            throw new IOException("Explicit interface selection grant or K36 AUTO revoked");
                    };
                    nativeConfiguration.limits = limits;
                }
                public void requireCurrent(long token) throws IOException { permission.requireCurrent(token); }
                public void audit(String event, int ordinal, int count) throws IOException { permission.audit(event, ordinal, count); }
                public boolean nativeUrbReceptionApproved() { return permission.nativeUrbReceptionApproved(); }
                public boolean explicitSetInterfaceAlt0Approved() { return permission.explicitSetInterfaceAlt0Approved(); }
                public boolean sonyTsOutputReadbackApproved() { return permission.sonyTsOutputReadbackApproved(); }
                public void beforeFirstBulk() throws IOException {
                    requireSonyReadbackOwner(); permission.requireCurrent(generation);
                    if (nativeConfiguration.limits == null || !nativeConfiguration.limits.sonyTsOutputReadback
                            || !permission.sonyTsOutputReadbackApproved() || sonyReadbackHook == null)
                        throw new IOException("Sony readback hook or permission unavailable");
                    sonyReadbackHook.beforeFirstBulk();
                }
            };
        }
        Tbs5530TsSmoke smoke = new Tbs5530TsSmoke(session, generation, new Tbs5530Bridge(controlIo(), session, generation), bulkIo(), effective, clock, readApi, observer);
        if (readApi == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM) smoke.streamReceiver(limits -> {
            consent.requireCurrent(); session.requireUsable(generation); requireReloadComplete();
            if (!session.isFrontendLocked() || !session.isStreaming() || nativeConfiguration.reloadManager == null)
                throw new IOException("Locked owned B3 stream required");
            final info.martinmarinov.usbxfer.NativeUsbfsStream.Result[] result = new info.martinmarinov.usbxfer.NativeUsbfsStream.Result[1];
            Tbs5530Session.Transaction receive = () -> {
                try {
                    if (limits.b4Capture) {
                        if (signalDirectory == null) throw new IOException("B4 signal audit destination required");
                        if (limits.tunerRssi) throw new IOException("TUNER_RSSI_UNSUPPORTED_REQUIRES_REVIEWED_EXACT_SEQUENCE");
                        signalSampler = new Tbs5530SignalSampler((thread, stopping) -> {
                            Tbs5530ControlIo rawMonitor = nativeConfiguration.reloadManager.monitorControl(thread);
                            Tbs5530ControlIo currentMonitor = (type, request, value, index, bytes, offset, length, timeout) -> {
                                if (stopping.get() && !(request == 0x80 && length == 4
                                        && ((bytes[offset + 2] == 1 && bytes[offset + 3] == 0)
                                        || (bytes[offset + 2] == 0 && (bytes[offset + 3] & 255) == 0x20))))
                                    throw new IOException("Monitor stopping");
                                consent.requireCurrent(); session.requireUsable(generation);
                                return rawMonitor.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                            };
                            Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor(currentMonitor, System::nanoTime);
                            return () -> {
                                final Tbs5530SignalMonitor.Sample[] sample = new Tbs5530SignalMonitor.Sample[1];
                                session.transaction(generation, () -> sample[0] = monitor.sample());
                                return sample[0];
                            };
                        });
                        try {
                            signalSampler.start();
                            result[0] = nativeConfiguration.reloadManager.receiveStream();
                        } finally { signalSampler.close(); }
                        Tbs5530SignalAudit.write(signalDirectory, nativeConfiguration.runId,
                                signalSampler.samples(), signalSampler.failure());
                    } else result[0] = nativeConfiguration.reloadManager.receiveStream();
                } catch (IOException error) { throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE, "Native stream failed", error); }
            };
            if (limits.b4Capture) session.receiveTransaction(generation, receive);
            else session.transaction(generation, receive);
            return result[0];
        });
        return smoke;
    }
    private final java.util.Map<String, Tbs5530SignalMonitor> b5OffMonitors = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.List<Tbs5530SignalMonitor.Sample>> b5OffSamples = new java.util.LinkedHashMap<>();
    public boolean sampleB5OffMonitor(String phase) throws IOException {
        consent.requireCurrent(); session.requireUsable(generation());
        if (!b5Selected || !("OFF1".equals(phase) && b5Phase == null || "OFF2".equals(phase) && "ON1".equals(b5Phase)))
            throw new IOException("Demod OFF monitor requires an idle B5 phase");
        drainB5();
        Tbs5530SignalMonitor monitor = b5OffMonitors.get(phase);
        if (monitor == null) {
            monitor = new Tbs5530SignalMonitor(b5OffControl(), info.martinmarinov.usbxfer.NativeUsbfsStream::monotonicNanos, false, true);
            b5OffMonitors.put(phase, monitor); b5OffSamples.put(phase, new java.util.ArrayList<>());
        }
        java.util.List<Tbs5530SignalMonitor.Sample> samples = b5OffSamples.get(phase);
        if (samples.size() >= 256) throw new IOException("B5_OFF_MONITOR_CAPACITY");
        final Tbs5530SignalMonitor current = monitor;
        final Tbs5530SignalMonitor.Sample[] sample = new Tbs5530SignalMonitor.Sample[1];
        session.transaction(generation(), () -> sample[0] = current.sample());
        samples.add(sample[0]); return sample[0].stopForLockLoss;
    }
    public void writeB5OffMonitor() throws IOException {
        for (java.util.Map.Entry<String, java.util.List<Tbs5530SignalMonitor.Sample>> entry : b5OffSamples.entrySet())
            Tbs5530SignalAudit.write(signalDirectory, nativeConfiguration.runId + "-" + entry.getKey(), entry.getValue(), null, entry.getKey());
    }
    private boolean b5Selected;
    private String b5Phase, b5WatchdogReason;
    private long b5OnStarted, b5SessionStarted;
    public void requireB5Deadline() throws IOException {
        if (b5SessionStarted == 0) return;
        long elapsed = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos() - b5SessionStarted;
        if (elapsed < 0 || elapsed >= 65_000_000_000L) throw new IOException("B5_PROGRAM_DEADLINE");
    }
    public String b5WatchdogReason() { return b5WatchdogReason; }
    public void beginB5(Tbs5530TsSmoke.Permission permission, Tbs5530TsSmoke.Limits limits) throws IOException {
        consent.requireCurrent(); session.requireUsable(generation()); requireReloadComplete();
        if (b5Selected || nativeConfiguration == null || nativeConfiguration.reloadManager == null
                || readApi != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM || !session.isFrontendLocked())
            throw new IOException("B5 requires a fresh locked reload owner");
        if (!limits.b5Capture || !"ON1".equals(limits.b5Phase)) throw new IOException("Exact B5 ON1 scope required");
        permission.requireCurrent(generation()); permission.consume(limits, generation());
        b5SessionStarted = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos();
        if (b5SessionStarted <= 0) throw new IOException("B5_CLOCK_UNAVAILABLE");
        permission.audit("B5_STREAM_SELECT_INTENT", 0, 2);
        new Tbs5530Bridge(controlIo(), session, generation()).selectTerrestrialStream();
        session.streamingStarted(generation());
        permission.audit("B5_STREAM_SELECT_COMPLETE", 0, 2);
        b5Selected = true;
        nativeConfiguration.pollGuard = () -> {
            consent.requireCurrent(); session.requireUsable(generation()); permission.requireCurrent(generation()); requireB5Deadline();
            if (!operationalOwner() || !session.isStreaming()) throw new IOException("B5_OWNER_REVOKED");
            if (signalLockLost()) { b5WatchdogReason = "DEMOD_UNLOCK_OVER_2_SECONDS"; throw new IOException(b5WatchdogReason); }
            Tbs5530NativeStreamBackend backend = nativeConfiguration.streamBackend;
            long completion = backend == null ? 0 : backend.lastCompletionNanos();
            long now = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos();
            if (now < b5OnStarted || completion < 0 || completion > now) throw new IOException("B5_NATIVE_CLOCK_INVALID");
            if (info.martinmarinov.usbxfer.NativeUsbfsStream.completionStalled(now, b5OnStarted, completion)) {
                b5WatchdogReason = "BULK_STALL_OVER_2_SECONDS"; throw new IOException(b5WatchdogReason);
            }
        };
    }
    public void drainB5() throws IOException {
        if (nativeConfiguration == null || nativeConfiguration.reloadManager == null) throw new IOException("B5 owner unavailable");
        nativeConfiguration.reloadManager.drainStream();
    }
    public info.martinmarinov.usbxfer.NativeUsbfsStream.Result receiveB5(String phase) throws IOException {
        consent.requireCurrent(); session.requireUsable(generation());
        if (!b5Selected || !("ON1".equals(phase) && b5Phase == null || "ON2".equals(phase) && "ON1".equals(b5Phase)))
            throw new IOException("B5 phases cannot be reordered or retried");
        drainB5(); b5Phase = phase; b5WatchdogReason = null;
        nativeConfiguration.limits = Tbs5530TsSmoke.Limits.b5(phase);
        nativeConfiguration.streamBackend = null;
        b5OnStarted = info.martinmarinov.usbxfer.NativeUsbfsStream.monotonicNanos();
        final info.martinmarinov.usbxfer.NativeUsbfsStream.Result[] result = new info.martinmarinov.usbxfer.NativeUsbfsStream.Result[1];
        session.receiveTransaction(generation(), () -> {
            try {
            signalSampler = new Tbs5530SignalSampler((thread, stopping) -> {
                Tbs5530ControlIo raw = nativeConfiguration.reloadManager.monitorControl(thread);
                Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor((type, request, value, index, bytes, offset, length, timeout) -> {
                    consent.requireCurrent(); session.requireUsable(generation());
                    return raw.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                }, info.martinmarinov.usbxfer.NativeUsbfsStream::monotonicNanos, false, true);
                return () -> {
                    final Tbs5530SignalMonitor.Sample[] sample = new Tbs5530SignalMonitor.Sample[1];
                    session.transaction(generation(), () -> sample[0] = monitor.sample()); return sample[0];
                };
            }, true);
            try { signalSampler.start(); result[0] = nativeConfiguration.reloadManager.receiveStream(); }
            finally { signalSampler.close(); }
            } catch (IOException error) { throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE, "B5 receive failed", error); }
        });
        if (signalDirectory == null) throw new IOException("B5 signal audit destination required");
        Tbs5530SignalAudit.write(signalDirectory, nativeConfiguration.runId + "-" + phase,
                signalSampler.samples(), signalSampler.failure(), phase);
        return result[0];
    }
    public Tbs5530SignalRssi.Result sampleB5Rssi(String phase, int frequencyKhz) throws IOException {
        consent.requireCurrent(); session.requireUsable(generation());
        if (!b5Selected || !("OFF1".equals(phase) && b5Phase == null || "OFF2".equals(phase) && "ON1".equals(b5Phase)))
            throw new IOException("RSSI requires an idle B5 OFF phase");
        drainB5();
        final Tbs5530SignalRssi.Result[] result = new Tbs5530SignalRssi.Result[1];
        session.transaction(generation(), () -> {
            try {
                Tbs5530ControlIo io = b5OffControl();
                try {
                    b5Repeater(io, true);
                    result[0] = Tbs5530SignalRssi.sample(Tbs5530SignalRssi.Phase.OFF,
                            io, info.martinmarinov.usbxfer.NativeUsbfsStream::monotonicNanos, frequencyKhz);
                } finally { b5Repeater(io, false); }
            }
            catch (IOException error) { throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE, "B5 RSSI sample failed", error); }
        });
        return result[0];
    }
    private Tbs5530ControlIo b5OffControl() throws IOException {
        Tbs5530ControlIo raw = nativeConfiguration.reloadManager.offMonitorControl();
        return (type, request, value, index, bytes, offset, length, timeout) -> {
            consent.requireCurrent(); session.requireUsable(generation()); requireReloadComplete(); requireB5Deadline();
            int result = raw.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
            consent.requireCurrent(); session.requireUsable(generation()); return result;
        };
    }
    private static void b5Repeater(Tbs5530ControlIo io, boolean enabled) throws IOException {
        // Same SLV-X bank0/08 sequence and 20 ms delay as cxd2878_i2c_repeater.
        for (byte[] payload : new byte[][]{{3, (byte)0xcc, 0, 0}, {3, (byte)0xcc, 8, (byte)(enabled ? 1 : 0)}})
            if (io.controlTransfer(0x40, 0x80, 0, 0, payload, 0, payload.length, 2000) != payload.length)
                throw new IOException("B5_REPEATER_SHORT_TRANSFER");
        try { Thread.sleep(20); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
    }
    public void writeB5Rssi(String phase, java.util.List<Tbs5530SignalRssi.Result> samples) throws IOException {
        Tbs5530SignalAudit.writeRssi(signalDirectory, nativeConfiguration.runId, phase, samples);
    }

    void programCompleted(Tbs5530Frontend.Plan plan) {
        if (nativeConfiguration == null) return;
        if (plan.operation == Tbs5530Frontend.Operation.INITIALIZE || plan.operation == Tbs5530Frontend.Operation.TUNE) {
            Tbs5530DvbT2TuneParameters tune = plan.tuneParameters;
            nativeConfiguration.k36AutoTuneConfirmed = plan.operation == Tbs5530Frontend.Operation.TUNE && tune != null
                    && tune.frequencyHz == 594000000L && tune.bandwidthHz == 8000000
                    && tune.selection.mode == Tbs5530DvbT2TuneParameters.Mode.AUTO;
        }
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
    /** Read on the owner worker or after completion/cleanup; never races a native syscall. */
    public NativeReceiveSnapshot nativeReceiveSnapshot() {
        Tbs5530NativeUsbfsBackend backend = nativeConfiguration == null ? null : nativeConfiguration.backend;
        if (backend != null) return new NativeReceiveSnapshot(backend.snapshot(), backend.quarantined(), backend.interfaceSelectionSnapshot());
        Tbs5530NativeStreamBackend stream = nativeConfiguration == null ? null : nativeConfiguration.streamBackend;
        return stream == null ? null : new NativeReceiveSnapshot(stream.snapshot(), stream.quarantined(), null);
    }
    public static final class NativeReceiveSnapshot {
        public final long attempted, submitted, completed, outstanding, copiedBytes, cleanupBytes, completionSequence;
        public final int syscallErrno, syscallReturn, urbStatus, actualLength, slot;
        public final boolean quarantined;
        public final Tbs5530ExplicitInterfaceSelection.Snapshot interfaceSelection;
        NativeReceiveSnapshot(long[] s, boolean quarantined, Tbs5530ExplicitInterfaceSelection.Snapshot interfaceSelection) {
            this.interfaceSelection = interfaceSelection;
            attempted = s[0]; submitted = s[1]; completed = s[2]; outstanding = s[3]; copiedBytes = s[4]; cleanupBytes = s[5];
            syscallErrno = (int)s[7]; urbStatus = (int)s[9]; actualLength = (int)s[10]; slot = (int)s[11];
            completionSequence = s[12]; syscallReturn = (int)s[13]; this.quarantined = quarantined || s[6] != 0;
        }
        public boolean completionKnown() { return !quarantined && syscallReturn == 0 && urbStatus != Integer.MIN_VALUE; }
    }
    public long generation() { return session.getGeneration(); }
    public IOException cleanupFailure() { return session.getCleanupFailure(); }
    public void revoke() { binding.cancel(); }
    @Override public void close() throws IOException { binding.close(); }
}
