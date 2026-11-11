// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import info.martinmarinov.usbxfer.NativeUsbfsUrb;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Separate diagnostic backend; no legacy IsoRequest or Java UsbRequest is used. */
final class Tbs5530NativeUsbfsBackend implements Tbs5530AndroidUsbFacade.ReceiveBackend {
    private static final Set<Tbs5530NativeUsbfsBackend> QUARANTINE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final UsbDeviceConnection connection;
    private final NativeUsbfsUrb receiver;
    private final Tbs5530ExplicitInterfaceSelection interfaceSelection;
    private final FileOutputStream stream;
    private final Writer audit;
    private boolean auditClosed;

    Tbs5530NativeUsbfsBackend(UsbDeviceConnection connection, UsbInterface intf, UsbEndpoint endpoint, File directory,
                             String runId, Tbs5530TsSmoke.Limits limits,
                             Tbs5530OperationalHandoff.Consent consent) throws IOException {
        if (connection == null || connection.getFileDescriptor() < 0 || intf == null || endpoint == null
                || endpoint.getAddress() != 0x82 || endpoint.getDirection() != 128 || endpoint.getType() != 2
                || endpoint.getMaxPacketSize() != 64 && endpoint.getMaxPacketSize() != 512
                || limits == null || !limits.readApi.isNativeUrb()
                || runId == null || !runId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IOException("Owned terrestrial IN82 and consumed exact native diagnostic limits required");
        }
        consent.requireCurrent(); this.connection = connection;
        File target = new File(directory, "phase31-native-urb-" + runId + ".jsonl");
        if (!target.createNewFile()) throw new IOException("Native audit already exists; diagnostic cannot be retried");
        stream = new FileOutputStream(target);
        audit = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        try {
            receiver = new NativeUsbfsUrb(connection.getFileDescriptor(), Math.min(limits.maxReads, limits.maxBytes / limits.readChunkBytes),
                    limits.durationMillis, limits.readChunkBytes, runId, consent::requireCurrent, audit);
        } catch (IOException | RuntimeException | Error error) {
            try { audit.close(); } catch (IOException close) { error.addSuppressed(close); }
            throw error;
        }
        interfaceSelection = new Tbs5530ExplicitInterfaceSelection(limits.explicitSetInterfaceAlt0,
                new Tbs5530ExplicitInterfaceSelection.Port() {
                    public void requireCurrent() throws IOException {
                        if (auditClosed || receiver.isQuarantined()) throw new IOException("Native owner closed or quarantined");
                        consent.requireCurrent();
                    }
                    public Tbs5530ExplicitInterfaceSelection.Observation observe() throws IOException {
                        int count = intf.getEndpointCount();
                        if (count != 2) throw new IOException("Exact operational interface required");
                        int[] addresses = new int[count], directions = new int[count], types = new int[count], sizes = new int[count];
                        for (int i = 0; i < count; i++) {
                            UsbEndpoint ep = intf.getEndpoint(i);
                            addresses[i] = ep.getAddress(); directions[i] = ep.getDirection();
                            types[i] = ep.getType(); sizes[i] = ep.getMaxPacketSize();
                        }
                        return new Tbs5530ExplicitInterfaceSelection.Observation(connection.getRawDescriptors(),
                                info.martinmarinov.usbxfer.UsbHiSpeedBulk.negotiatedSpeed(connection),
                                intf.getId(), intf.getAlternateSetting(), addresses, directions, types, sizes);
                    }
                    public long[] receiveState() { return receiver.snapshot(); }
                    public boolean setInterfaceAlt0() { return connection.setInterface(intf); }
                }, android.os.SystemClock::elapsedRealtimeNanos);
        // Retain both Java connection and native buffers before the first submit, not only on failure.
        synchronized (QUARANTINE) { QUARANTINE.add(this); }
    }
    @Override public int read(byte[] bytes, int length, int timeout) throws IOException {
        return interfaceSelection.read(() -> receiver.read(bytes, length, timeout));
    }
    Tbs5530ExplicitInterfaceSelection.Snapshot interfaceSelectionSnapshot() { return interfaceSelection.snapshot(); }
    @Override public int errno() { return (int) receiver.snapshot()[7]; }
    long[] snapshot() { return receiver.snapshot(); }
    boolean quarantined() { return receiver.isQuarantined(); }
    @Override public void close() throws IOException {
        try { receiver.close(); }
        finally {
            if (!auditClosed) {
                try { audit.flush(); stream.getFD().sync(); }
                finally { auditClosed = true; audit.close(); }
            }
            if (!receiver.isQuarantined()) synchronized (QUARANTINE) { QUARANTINE.remove(this); }
        }
    }
}
