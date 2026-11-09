// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded application EP0 observations, not a kernel or wire capture. No disk I/O during transfers. */
public final class Tbs5530ControlMirror {
    private static final int MAX_RECORDS = 4096, PAYLOAD_LIMIT = 64;
    private final File target;
    private final String run, epoch, context;
    public interface Clock { long nowNanos(); }
    private final Clock clock;
    private final List<Record> records = new ArrayList<>();
    private boolean finished;
    private int active;

    public Tbs5530ControlMirror(File directory, String run, String epoch, String context, Clock clock) throws IOException {
        if (directory == null || !directory.isDirectory() || !token(run) || !token(epoch) || !token(context) || clock == null)
            throw new IOException("Invalid control mirror context");
        target = new File(directory, "control-ep0.jsonl");
        if (target.exists() || new File(directory, "control-ep0.jsonl.part").exists()) throw new IOException("Control trace already exists");
        File part = new File(directory, "control-ep0.jsonl.part");
        if (!part.createNewFile()) throw new IOException("Cannot reserve private control evidence");
        try (FileOutputStream output = new FileOutputStream(part)) { output.getFD().sync(); }
        this.run = run; this.epoch = epoch; this.context = context; this.clock = clock;
    }
    private static boolean token(String text) { return text != null && text.matches("[A-Za-z0-9_.:-]{1,200}"); }

    private static final class Record {
        long sequence, generation, thread, before, after;
        int type, request, value, index, requested, offset, timeout;
        Integer returned;
        String phase, out = "", in = "", error = "NONE";
    }
    private static String hex(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0) return "";
        char[] text = new char[Math.min(PAYLOAD_LIMIT, length) * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < text.length / 2; i++) {
            int b = bytes[offset + i] & 255; text[2 * i] = alphabet[b >>> 4]; text[2 * i + 1] = alphabet[b & 15];
        }
        return new String(text);
    }
    public int transfer(String phase, long generation, Tbs5530ControlIo io, int type, int request, int value, int index,
                        byte[] bytes, int offset, int length, int timeout) throws IOException {
        if (!token(phase) || generation < 0 || io == null || type < 0 || type > 255 || request < 0 || request > 255
                || value < 0 || value > 65535 || index < 0 || index > 65535 || timeout <= 0 || offset < 0 || length < 0
                || length > 65535 || (bytes == null ? length != 0 || offset != 0 : offset > bytes.length || length > bytes.length - offset))
            throw new IOException("Invalid mirrored control parameters");
        Record r = new Record();
        synchronized (this) {
            if (finished || records.size() >= MAX_RECORDS) throw new IOException("Control mirror closed or full before dispatch");
            r.sequence = records.size() + 1; records.add(r); active++;
        }
        r.phase = phase; r.generation = generation; r.thread = Thread.currentThread().getId();
        r.type = type; r.request = request; r.value = value; r.index = index; r.offset = offset; r.timeout = timeout; r.requested = length;
        boolean input = (type & 128) != 0, firmware = request == 0xa0;
        if (!input && !firmware) r.out = hex(bytes, offset, length);
        r.before = clock.nowNanos();
        try {
            int result = io.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
            r.after = clock.nowNanos(); r.returned = result;
            if (input && !firmware && result > 0 && result <= length) r.in = hex(bytes, offset, result);
            return result;
        } catch (IOException | RuntimeException error) {
            r.after = clock.nowNanos(); r.error = error instanceof IOException ? "IOException" : "RuntimeException";
            throw error;
        } finally { synchronized (this) { active--; } }
    }
    public synchronized int count() { return records.size(); }
    public synchronized boolean durable() { return finished; }

    /** Call only after the owned worker has drained; never closes the USB connection. */
    public synchronized void finish() throws IOException {
        if (finished) return;
        if (active != 0) throw new IOException("Control calls have not drained");
        File part = new File(target.getPath() + ".part");
        if (target.exists() || !part.isFile() || part.length() != 0) throw new IOException("Refusing to overwrite control evidence");
        try (FileOutputStream output = new FileOutputStream(part)) {
            for (Record r : records) {
                String text = "{\"schema\":\"TBS5530-EP0/v1\",\"evidence_layer\":\"ANDROID_APP_OBSERVED\",\"clock\":\"ANDROID_ELAPSED_REALTIME_NS\""
                        + ",\"run_id\":\"" + run + "\",\"attachment_epoch\":\"" + epoch + "\",\"context\":\"" + context
                        + "\",\"vid\":29516,\"pid\":21808,\"endpoint\":0,\"sequence\":" + r.sequence + ",\"phase\":\"" + r.phase
                        + "\",\"generation\":" + r.generation + ",\"thread\":" + r.thread + ",\"before_ns\":" + r.before + ",\"after_ns\":" + r.after
                        + ",\"request_type\":" + r.type + ",\"direction\":\"" + ((r.type & 128) == 0 ? "OUT" : "IN")
                        + "\",\"type\":" + ((r.type >>> 5) & 3) + ",\"recipient\":" + (r.type & 31) + ",\"request\":" + r.request
                        + ",\"value\":" + r.value + ",\"index\":" + r.index + ",\"buffer_offset\":" + r.offset + ",\"timeout_ms\":" + r.timeout
                        + ",\"requested_length\":" + r.requested + ",\"returned_length\":" + r.returned + ",\"errno\":null,\"exception\":\"" + r.error
                        + "\",\"success\":" + (r.returned != null && r.returned == r.requested) + ",\"out_hex\":\"" + r.out + "\",\"in_hex\":\"" + r.in
                        + "\",\"payload_limit\":64,\"firmware_payload_omitted\":" + (r.request == 0xa0)
                        + ",\"physical_dispatch_proven\":false}\n";
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
            output.getFD().sync();
        }
        if (target.exists() || !part.renameTo(target)) throw new IOException("Cannot publish control evidence");
        finished = true;
    }
}
