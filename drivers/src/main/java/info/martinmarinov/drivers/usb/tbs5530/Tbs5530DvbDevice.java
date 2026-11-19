// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.SystemClock;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDemux;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.R;
import info.martinmarinov.usbxfer.AlternateUsbInterface;
import info.martinmarinov.usbxfer.ByteSource;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource;
import info.martinmarinov.usbxfer.NativeUsbfsControl;

/** Operational TBS5530 owner. Unlike DvbUsbDevice, firmware and tuning are never retried. */
public final class Tbs5530DvbDevice extends DvbDevice {
    private final Context context;
    private final Tbs5530Frontend.Clock clock;
    private final UsbDevice device;
    private final DeviceFilter filter;
    private final Object lifecycle = new Object();
    private final Object streams = new Object();
    private final AtomicBoolean removalCleanup = new AtomicBoolean();
    private volatile Tbs5530ProductionAttachment attachment;
    private volatile Tbs5530StreamGate stream;
    private volatile NativeUsbfsByteSource lastNativeSource;
    private volatile boolean closing, opened, confirmedDetach;
    private volatile IOException cleanupFailure;
    private boolean demuxClosed;
    private int closers;
    private volatile boolean poisoned;
    private volatile boolean tuned;
    private Tbs5530Session session;
    private long generation;
    private UsbDeviceConnection control;
    private Tbs5530ControlIo io;
    private Tbs5530Bridge bridge;
    private Tbs5530Frontend frontend;
    private Tbs5530SignalMonitor monitor;
    private Tbs5530SignalMonitor.Sample sample;
    private long frequencyHz, lastRssiNanos;
    private int rfPercentage = -1;

    Tbs5530DvbDevice(Context context, UsbDevice operational, DeviceFilter filter,
                    Tbs5530ProductionAttachment attachment) {
        this(context, operational, filter, attachment, new Tbs5530Frontend.Clock() {
            @Override public long elapsedRealtimeMillis() { return SystemClock.elapsedRealtime(); }
            @Override public void delayMillis(long millis) throws InterruptedException { Thread.sleep(millis); }
        });
    }
    Tbs5530DvbDevice(Context context, UsbDevice operational, DeviceFilter filter,
                    Tbs5530ProductionAttachment attachment, Tbs5530Frontend.Clock clock) {
        super(DvbDemux.DvbDmxSwfilter());
        this.clock = clock;
        this.context = context.getApplicationContext(); this.device = operational;
        this.filter = filter; this.attachment = attachment;
        attachment.onRemoval(this::attachmentRemoved);
    }

    @Override public void open() throws DvbException {
        synchronized (lifecycle) {
            if (closers != 0) throw error(DvbException.ErrorCode.BAD_API_USAGE, R.string.tbs5530_busy, null);
            if (opened) throw error(DvbException.ErrorCode.BAD_API_USAGE, R.string.tbs5530_already_open, null);
            if (poisoned) throw error(DvbException.ErrorCode.HARDWARE_EXCEPTION, R.string.tbs5530_no_retry, null);
            closing = false;
            lastNativeSource = null;
            try {
                if (demuxClosed) { resetClosedDemux(); demuxClosed = false; }
                if (attachment == null) {
                    attachment = new Tbs5530ProductionAttachment(context, device);
                    attachment.prepare(false);
                    attachment.onRemoval(this::attachmentRemoved);
                }
                attachment.requireCurrent();
                byte[] image = Tbs5530ProductionFirmware.load(context);
                Tbs5530ProductionFirmware.plan(image);
                control = attachment.openControl();
                session = new Tbs5530Session();
                generation = session.requestPermission(); session.permissionResult(generation, true);
                final UsbDeviceConnection owner = control;
                session.connected(generation, new Tbs5530Session.Resources() {
                    @Override public void cancel() { }
                    @Override public void release() { }
                    @Override public void close() { owner.close(); }
                });
                session.beginFirmwareTransition(generation);
                // A0 reload is after Android's configuration, before any bridge/frontend controls.
                session.transaction(generation, () -> {
                    try {
                        NativeUsbfsControl.reloadFx2(owner, image, this::requireAttachment);
                        java.util.logging.Logger.getLogger("Tbs5530Production").info("Operational RAM reload complete: 132/132 A0");
                        settle(100); settle(1500);
                    } catch (IOException e) {
                        throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                                "Operational RAM reload failed; no retry", new Tbs5530ProductionFailure(
                                        Tbs5530ProductionFailure.Kind.RELOAD, "Native RAM reload failed", e));
                    }
                });
                session.firmwareUploaded(generation); session.ready(generation);
                io = (type, request, value, index, bytes, offset, length, timeout) -> {
                    requireAttachment();
                    int result = owner.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
                    requireAttachment();
                    return result;
                };
                bridge = new Tbs5530Bridge(io, session, generation);
                frontend = new Tbs5530Frontend(bridge, session, generation, clock);
                frontend.execute(frontend.planProbe());
                frontend.execute(frontend.planInitialize(Tbs5530Frontend.DriveStrength.RAW_55));
                frontend.readyForTune(); session.frontendInitialized(generation);
                monitor = new Tbs5530SignalMonitor(io, System::nanoTime, false, true);
                sample = null; tuned = false; rfPercentage = -1; lastRssiNanos = 0;
                opened = true;
            } catch (IOException | RuntimeException | LinkageError e) {
                poisoned = control != null;
                IOException cleanup = cleanupControl();
                if (cleanup != null) e.addSuppressed(cleanup);
                if (e instanceof java.io.InterruptedIOException) Thread.currentThread().interrupt();
                throw error(DvbException.ErrorCode.HARDWARE_EXCEPTION, R.string.tbs5530_open_failed, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                IOException cleanup = cleanupControl(); if (cleanup != null) e.addSuppressed(cleanup);
                throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_interrupted, e);
            } catch (Error e) {
                poisoned = control != null;
                IOException cleanup = cleanupControl(); if (cleanup != null) e.addSuppressed(cleanup);
                throw e;
            }
        }
    }

    private void settle(long millis) throws IOException {
        long deadline = clock.elapsedRealtimeMillis() + millis;
        while (true) {
            requireAttachment();
            long remaining = deadline - clock.elapsedRealtimeMillis();
            if (remaining <= 0) return;
            try { clock.delayMillis(Math.min(25, remaining)); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("RAM reload settling interrupted");
            }
        }
    }

    private void requireAttachment() throws IOException {
        if (closing) throw new IOException("TBS5530 is closing");
        Tbs5530ProductionAttachment current = attachment;
        if (current == null) throw new IOException("TBS5530 attachment unavailable");
        current.requireCurrent();
    }

    private void requireOpen() throws IOException {
        requireAttachment();
        if (!opened || poisoned) throw new IOException("TBS5530 is not open");
        session.requireUsable(generation);
    }

    @Override protected void tuneTo(long frequency, long bandwidth, @NonNull DeliverySystem system) throws DvbException {
        if (system != DeliverySystem.DVBT2) throw error(DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED, R.string.tbs5530_dvbt2_only, null);
        if (bandwidth != 6_000_000 && bandwidth != 7_000_000 && bandwidth != 8_000_000)
            throw error(DvbException.ErrorCode.UNSUPPORTED_BANDWIDTH, R.string.tbs5530_bandwidth, null);
        final Tbs5530DvbT2TuneParameters manual;
        try { manual = new Tbs5530DvbT2TuneParameters(frequency, (int) bandwidth, 0); }
        catch (IllegalArgumentException e) { throw error(DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ, R.string.tbs5530_frequency, e); }
        synchronized (lifecycle) {
            try {
                requireOpen();
                Tbs5530StreamGate gate = stream;
                if (gate != null) gate.pause();
                tuned = false; sample = null; rfPercentage = -1;
                frontend.execute(frontend.planTerrestrialTune(manual));
                Tbs5530Frontend.Status status = frontend.execute(frontend.planPollStatus(3)).status;
                // AUTO is a single different PLP strategy after bounded clean no-lock, never an I/O retry.
                if (!status.demodLocked) {
                    frontend.execute(frontend.planTerrestrialTune(new Tbs5530DvbT2TuneParameters(frequency,
                            (int) bandwidth, Tbs5530DvbT2TuneParameters.Selection.automatic())));
                    status = frontend.execute(frontend.planPollStatus(3)).status;
                }
                session.frontendLock(generation, status.demodLocked);
                if (!status.demodLocked) throw error(DvbException.ErrorCode.CANNOT_TUNE_TO_FREQ, R.string.tbs5530_no_lock, null);
                frequencyHz = frequency;
                sampleRssi();
                bridge.selectTerrestrialStream();
                tuned = true;
                if (gate != null) gate.resume();
            } catch (DvbException e) { throw e; }
            catch (IOException | RuntimeException e) {
                fail(e);
                throw error(DvbException.ErrorCode.HARDWARE_EXCEPTION, R.string.tbs5530_tune_failed, e);
            }
        }
    }

    private Tbs5530SignalMetrics metrics() throws IOException {
        requireOpen();
        if (!tuned) throw new IOException("No completed DVB-T2 tune");
        long now = System.nanoTime();
        if (sample == null || now - sample.startNanos >= Tbs5530SignalMonitor.TICK_NANOS) {
            final Tbs5530SignalMonitor.Sample[] result = {null};
            session.transaction(generation, () -> result[0] = monitor.sample());
            sample = result[0];
            if (!sample.cleanupSucceeded || !sample.errors.isEmpty()) throw new IOException("Signal sample or cleanup failed");
        }
        return sample.metrics;
    }

    @Override public int readSnr() throws DvbException {
        synchronized (lifecycle) {
            try { return Tbs5530ProductionMetrics.snr(metrics()); }
            catch (IOException e) { fail(e); throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_metrics_failed, e); }
        }
    }
    @Override public int readBitErrorRate() throws DvbException {
        synchronized (lifecycle) {
            try { return Tbs5530ProductionMetrics.ber(metrics()); }
            catch (IOException e) { fail(e); throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_metrics_failed, e); }
        }
    }
    /** Unscaled pre-BCH ratio from the shared signal sample; null means unavailable. */
    public Double readPreBchBitErrorRate() throws DvbException {
        synchronized (lifecycle) {
            try {
                Tbs5530SignalMetrics current = metrics();
                return current.lock == Tbs5530SignalMetrics.Lock.LOCKED ? current.ber : null;
            } catch (IOException e) {
                fail(e);
                throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_metrics_failed, e);
            }
        }
    }

    @Override public Set<DvbStatus> getStatus() throws DvbException {
        synchronized (lifecycle) {
            try {
                requireOpen();
                if (!tuned) return Collections.emptySet();
                Tbs5530SignalMetrics m = metrics();
                if (m.lock == Tbs5530SignalMetrics.Lock.LOCKED) return EnumSet.of(DvbStatus.FE_HAS_SIGNAL,
                        DvbStatus.FE_HAS_CARRIER, DvbStatus.FE_HAS_VITERBI, DvbStatus.FE_HAS_SYNC, DvbStatus.FE_HAS_LOCK);
                return Collections.emptySet();
            } catch (IOException e) { fail(e); throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_metrics_failed, e); }
        }
    }
    @Override public int readRfStrengthPercentage() throws DvbException {
        synchronized (lifecycle) {
            try {
                requireOpen();
                // ADC changes tuner registers: never disturb a running Bulk receiver for a display update.
                Tbs5530StreamGate gate = stream;
                if (tuned && System.nanoTime() - lastRssiNanos >= 2_000_000_000L) {
                    if (gate == null) sampleRssi();
                    else synchronized (gate) { if (!gate.active()) sampleRssi(); }
                }
                return rfPercentage;
            } catch (IOException e) { fail(e); throw error(DvbException.ErrorCode.IO_EXCEPTION, R.string.tbs5530_metrics_failed, e); }
        }
    }
    private void sampleRssi() throws IOException {
        final Tbs5530SignalRssi.Result[] result = {null};
        session.transaction(generation, () -> {
            try {
                try {
                    repeater(true);
                    result[0] = Tbs5530SignalRssi.sample(Tbs5530SignalRssi.Phase.OFF, io, System::nanoTime, (int) (frequencyHz / 1000));
                } finally { repeater(false); }
            } catch (IOException e) {
                throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE, "RSSI sample failed", e);
            }
        });
        lastRssiNanos = System.nanoTime();
        rfPercentage = Tbs5530ProductionMetrics.rfPercentage(result[0].rssiDbm);
    }
    private void repeater(boolean enabled) throws IOException {
        for (byte[] bytes : new byte[][]{{3, (byte) 0xcc, 0, 0}, {3, (byte) 0xcc, 8, (byte) (enabled ? 1 : 0)}})
            if (io.controlTransfer(0x40, 0x80, 0, 0, bytes, 0, bytes.length, 2000) != bytes.length)
                throw new IOException("RSSI repeater short transfer");
        try { Thread.sleep(20); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("RSSI interrupted"); }
    }

    @Override public InputStream getTransportStream(StreamCallback callback) throws DvbException {
        synchronized (lifecycle) {
            try { requireOpen(); }
            catch (IOException e) { throw error(DvbException.ErrorCode.BAD_API_USAGE, R.string.tbs5530_open_failed, e); }
            return super.getTransportStream(new StreamCallback() {
                @Override public void onStreamException(IOException error) {
                    try { callback.onStreamException(error); }
                    finally { if (poisoned) removed(); }
                }
                @Override public void onStoppedStreaming() {
                    try { callback.onStoppedStreaming(); }
                    finally { if (poisoned) removed(); }
                }
            });
        }
    }
    @Override protected ByteSource createTsSource() {
        synchronized (streams) {
            final Tbs5530Session active = session;
            final long token = generation;
            stream = new Tbs5530StreamGate(() -> new ByteSource() {
                private NativeUsbfsByteSource source;
                @Override public void open() throws IOException {
                    active.transaction(token, () -> {
                        UsbDeviceConnection connection = null;
                        try {
                            requireAttachment();
                            connection = attachment.openStream();
                            AlternateUsbInterface iface = AlternateUsbInterface.forUsbInterface(connection, device.getInterface(0)).get(0);
                            source = new NativeUsbfsByteSource(connection, iface);
                            lastNativeSource = source;
                            // Native claim/SET_INTERFACE/CLEAR_HALT also share the EP0 mutex.
                            source.open();
                            requireAttachment();
                        } catch (IOException | RuntimeException e) {
                            if (source == null && connection != null) connection.close();
                            throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                                    "Cannot start native TS source", e);
                        }
                    });
                }
                @Override public void readNext(info.martinmarinov.usbxfer.ByteSink sink) throws IOException, InterruptedException {
                    source.readNext(sink);
                }
                @Override public void close() throws IOException { if (source != null) source.close(); }
            }, tuned && !closing, this::streamFailed, this::attachmentDisconnected);
            return stream;
        }
    }
    private void streamFailed(IOException error) {
        poisoned = true;
        Tbs5530Session active = session;
        if (active != null) active.failed(generation, error);
        // The pump must publish this native error before asynchronous cleanup can interrupt it.
    }
    private void attachmentRemoved() {
        attachmentDisconnected();
        removed();
    }

    private boolean attachmentDisconnected() {
        Tbs5530ProductionAttachment current = attachment;
        if (!confirmedDetach && current != null && current.disconnected()) confirmedDetach = true;
        return confirmedDetach;
    }

    public Exception classifyControlFailure(Exception error) {
        return error instanceof IOException && attachmentDisconnected()
                ? Tbs5530StreamGate.disconnected((IOException) error) : error;
    }

    private void removed() {
        if (!removalCleanup.compareAndSet(false, true)) return;
        poisoned = true; closing = true;
        Tbs5530Session active = session;
        if (active != null) active.requestCancellation(generation, true);
        Thread cleanup = new Thread(() -> {
            try {
                if (attachmentDisconnected()) reportStreamFailure(Tbs5530StreamGate.disconnected(null));
            } finally {
                try { close(); } catch (IOException error) { cleanupFailure = error; }
            }
        }, "tbs5530-remove-cleanup");
        cleanup.start();
    }
    private void fail(Exception e) {
        poisoned = true; tuned = false;
        if (session != null) session.failed(generation, e);
        Tbs5530StreamGate gate = stream;
        if (gate != null) gate.fail(attachmentDisconnected()
                ? Tbs5530StreamGate.disconnected(new IOException("TBS5530 session failed", e))
                : new IOException("TBS5530 session failed", e));
        // Callers hold lifecycle here; joining the pump under it could deadlock a stopped callback.
        removed();
    }

    /**
     * Optional native completion evidence for the latest receive window, retained after close.
     * Null means no source was created in this open; reopen clears previous-window evidence.
     * Native CLOCK_MONOTONIC timestamps and complete/truncated flags are passed through unchanged.
     * A live prefix, overflow or clock failure cannot certify transport duty or RF quality.
     */
    public NativeUsbfsByteSource.CompletionSnapshot receptionSnapshot() throws IOException {
        NativeUsbfsByteSource source = lastNativeSource;
        return source == null ? null : source.snapshotCompletions();
    }

    public IOException getCleanupFailure() { return cleanupFailure; }

    @Override public void close() throws IOException {
        beginStreamStop();
        closing = true;
        synchronized (lifecycle) { closing = true; closers++; }
        try {
            IOException failure = null;
            synchronized (streams) {
                if (stream != null) try { stream.close(); }
                catch (IOException | RuntimeException e) { failure = asIo(e); }
            }
            try { super.close(); } catch (IOException | RuntimeException e) { failure = append(failure, asIo(e)); }
            synchronized (lifecycle) {
                opened = false; tuned = false; demuxClosed = true;
                Tbs5530StreamGate gate = stream;
                if (gate != null && !gate.drained()) {
                    Tbs5530ProductionAttachment current = attachment;
                    if (current != null) current.retainUndrained();
                    failure = new IOException(context.getString(R.string.tbs5530_drain_unconfirmed), failure);
                } else {
                    IOException cleanup = cleanupControl();
                    if (cleanup != null) failure = append(failure, cleanup);
                    stream = null;
                }
            }
            if (cleanupFailure != null) failure = append(failure, cleanupFailure);
            if (failure != null) { poisoned = true; cleanupFailure = failure; throw failure; }
        } finally {
            // No reopen may race the gap between native drain and framework/control cleanup.
            synchronized (lifecycle) { closers--; }
        }
    }
    private IOException cleanupControl() {
        IOException failure = null;
        try { if (session != null) session.close(); }
        catch (IOException | RuntimeException e) { failure = asIo(e); }
        finally {
            if (session != null) try { session.drainCleanup(); }
            catch (IOException | RuntimeException e) { failure = append(failure, asIo(e)); }
            if (control != null) {
                try { control.close(); } catch (RuntimeException e) { failure = append(failure, asIo(e)); }
                control = null;
            }
            session = null; frontend = null; monitor = null;
            Tbs5530ProductionAttachment previous = attachment; attachment = null;
            if (previous != null) try { previous.close(); }
            catch (IOException | RuntimeException e) { failure = append(failure, asIo(e)); }
        }
        return failure;
    }
    private static IOException asIo(Exception error) {
        return error instanceof IOException ? (IOException) error : new IOException("TBS5530 cleanup failed", error);
    }
    private static IOException append(IOException first, IOException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private DvbException error(DvbException.ErrorCode code, int resource, Throwable cause) {
        return Tbs5530ProductionFailure.localized(context, code, resource, cause);
    }
    @Override public DeviceFilter getDeviceFilter() { return filter; }
    @Override public String getDebugString() { return "TBS5530 DVB-T2 CXD6802/ASCOT3I"; }
    @Override public String toString() { return filter.getName(); }
    @Override public DvbCapabilities readCapabilities() {
        return new DvbCapabilities(174_000_000, 868_000_000, 1000, Collections.singleton(DeliverySystem.DVBT2));
    }
}
