// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.annotation.TargetApi;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** API26+ diagnostic only. No requestWait() overload with an unbounded wait is used. */
@TargetApi(26)
final class Tbs5530QueuedUsbBackend implements Tbs5530AndroidUsbFacade.ReceiveBackend {
    private static final Set<Tbs5530QueuedUsbBackend> QUARANTINE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final UsbDeviceConnection connection;
    private final UsbRequest[] requests = new UsbRequest[Tbs5530QueuedReceiver.DEPTH];
    private final ByteBuffer[] buffers = new ByteBuffer[Tbs5530QueuedReceiver.DEPTH];
    private final Tbs5530QueuedReceiver receiver;
    private final File audit;
    private boolean released;

    Tbs5530QueuedUsbBackend(UsbDeviceConnection connection, UsbEndpoint endpoint, File directory) throws IOException {
        if (android.os.Build.VERSION.SDK_INT < 26) throw new IOException("Bounded queued diagnostic requires API26; no unbounded API21 fallback");
        if (connection == null || connection.getFileDescriptor() < 0 || endpoint == null
                || endpoint.getAddress() != 0x82 || endpoint.getDirection() != 128 || endpoint.getType() != 2
                || endpoint.getMaxPacketSize() != 64 && endpoint.getMaxPacketSize() != 512) {
            throw new IOException("Owned terrestrial Bulk IN82 endpoint required");
        }
        this.connection = connection;
        audit = new File(directory, "phase31-queued-" + UUID.randomUUID() + ".json");
        try {
            for (int slot = 0; slot < requests.length; slot++) {
                requests[slot] = new UsbRequest();
                if (!requests[slot].initialize(connection, endpoint)) throw new IOException("Cannot initialize receive request");
                buffers[slot] = ByteBuffer.allocateDirect(Tbs5530QueuedReceiver.CHUNK);
            }
        } catch (IOException | RuntimeException error) {
            for (UsbRequest request : requests) if (request != null) request.close();
            throw error;
        }
        receiver = new Tbs5530QueuedReceiver(new Tbs5530QueuedReceiver.Port() {
            public void submit(int slot) throws IOException {
                buffers[slot].clear();
                if (!requests[slot].queue(buffers[slot])) throw new IOException("Receive submit rejected; status unknown");
            }
            public Tbs5530QueuedReceiver.Completion await(int timeout) throws IOException {
                final UsbRequest request;
                try { request = connection.requestWait(timeout); }
                catch (TimeoutException | RuntimeException error) { throw new IOException("Bounded queued completion failed", error); }
                if (request == null) return null;
                for (int slot = 0; slot < requests.length; slot++) if (requests[slot] == request) {
                    return new Tbs5530QueuedReceiver.Completion(slot, buffers[slot].position());
                }
                throw new IOException("Foreign completion on owned connection");
            }
            public void cancel(int slot) { requests[slot].cancel(); }
            public void copy(int slot, byte[] destination, int length) {
                ByteBuffer data = buffers[slot].duplicate(); data.flip(); data.get(destination, 0, length);
            }
            public void release() {
                for (UsbRequest request : requests) request.close();
                released = true;
                synchronized (QUARANTINE) { QUARANTINE.remove(Tbs5530QueuedUsbBackend.this); }
            }
        }, android.os.SystemClock::elapsedRealtime);
        // In an uncertain drain, retain requests, direct buffers and fd until physical recovery.
        synchronized (QUARANTINE) { QUARANTINE.add(this); }
    }
    @Override public int read(byte[] bytes, int length, int timeout) throws IOException { return receiver.read(bytes, length, timeout); }
    @Override public int errno() { return -1; }
    @Override public void close() throws IOException {
        IOException failure = null;
        try { receiver.close(); }
        catch (IOException error) { failure = error; }
        Tbs5530QueuedReceiver.Snapshot result = receiver.snapshot();
        String text = "{\"read_api\":\"QUEUED_USB_REQUEST_8X4096\",\"errno\":null,\"submitted\":" + result.submitted
                + ",\"completed\":" + result.completed + ",\"delivered_bytes\":" + result.deliveredBytes
                + ",\"cleanup_received_bytes_not_delivered\":" + result.cleanupBytes
                + ",\"outstanding\":" + result.outstanding + ",\"uncertain\":" + result.uncertain
                + ",\"requests_released\":" + released + "}";
        try { Tbs5530DurableFile.write(audit, text.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) throw failure;
    }
}
