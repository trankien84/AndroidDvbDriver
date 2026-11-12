// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import info.martinmarinov.usbxfer.NativeUsbfsStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class Tbs5530NativeStreamBackend implements Tbs5530AndroidUsbFacade.ReceiveBackend {
    private static final Set<Tbs5530NativeStreamBackend> RETAINED = Collections.newSetFromMap(new IdentityHashMap<>());
    private final UsbDeviceConnection connection;
    private final NativeUsbfsStream receiver;
    private final FileOutputStream output;
    private final Writer audit;
    private boolean closed;
    NativeUsbfsStream.Result result;

    Tbs5530NativeStreamBackend(UsbDeviceConnection connection, UsbInterface intf, UsbEndpoint endpoint,
                              File directory, String runId, Tbs5530TsSmoke.Limits limits,
                              Tbs5530OperationalHandoff.Consent consent) throws IOException {
        if (connection == null || connection.getFileDescriptor() < 0 || intf == null || intf.getId() != 0
                || intf.getAlternateSetting() != 0 || endpoint == null || endpoint.getAddress() != 0x82
                || endpoint.getType() != 2 || endpoint.getDirection() != 128 || endpoint.getMaxPacketSize() != 512
                || limits == null || limits.readApi != Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_STREAM)
            throw new IOException("Exact owned high-speed terrestrial stream required");
        this.connection = connection;
        consent.requireCurrent();
        File file = new File(directory, "phase31-native-stream-" + runId + ".jsonl");
        if (!file.createNewFile()) throw new IOException("Native stream audit already exists");
        output = new FileOutputStream(file);
        audit = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        try {
            receiver = new NativeUsbfsStream(connection.getFileDescriptor(), limits.queueDepth, limits.readChunkBytes,
                    limits.maxBytes, limits.durationMillis, runId, consent::requireCurrent, audit);
        } catch (IOException | RuntimeException | Error error) {
            try { audit.close(); } catch (IOException close) { error.addSuppressed(close); }
            throw error;
        }
        synchronized (RETAINED) { RETAINED.add(this); }
    }
    NativeUsbfsStream.Result receive() throws IOException {
        if (closed || result != null) throw new IOException("Native stream already used");
        result = receiver.receive();
        return result;
    }
    @Override public int read(byte[] bytes, int length, int timeout) throws IOException {
        throw new IOException("Native stream must not use the legacy per-read loop");
    }
    @Override public int errno() { return (int) receiver.snapshot()[7]; }
    long[] snapshot() { return receiver.snapshot(); }
    boolean quarantined() { return receiver.isQuarantined(); }
    @Override public void close() throws IOException {
        if (closed) {
            if (receiver.isQuarantined()) throw new IOException("Native stream QUARANTINED; retain owned fd");
            return;
        }
        try { receiver.close(); }
        finally {
            closed = true;
            try { audit.flush(); output.getFD().sync(); }
            finally { audit.close(); }
            if (!receiver.isQuarantined()) synchronized (RETAINED) { RETAINED.remove(this); }
        }
    }
}
