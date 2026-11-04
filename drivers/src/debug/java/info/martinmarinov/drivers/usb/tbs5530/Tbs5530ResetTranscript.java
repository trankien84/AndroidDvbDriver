// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline USBPcap/TShark TSV analysis. Successful URBs do not establish CPUCS semantics or Android readiness. */
public final class Tbs5530ResetTranscript {
    public static final class Reset {
        public final int requestFrame, completionFrame, address, value;
        public final long completionStatus;
        private Reset(Packet start, Packet end) throws IOException {
            requestFrame = start.frame;
            completionFrame = end.frame;
            address = start.number("usb.setup.wValue");
            value = start.payload()[0] & 255;
            completionStatus = end.status();
        }
    }

    public static final class Attempt {
        public final int firstFrame, lastCompletionFrame, writes, imageBytes;
        public final String imageSha256;
        public final boolean allCompletionsSuccessful;
        public final List<Reset> resets;
        private Attempt(List<Pair> pairs, String expectedSha256) throws IOException {
            if (pairs.size() != 132) throw new IOException("Incomplete dual-reset attempt");
            firstFrame = pairs.get(0).start.frame;
            lastCompletionFrame = pairs.get(131).end.frame;
            writes = pairs.size();
            List<Reset> observed = new ArrayList<>();
            ByteArrayOutputStream image = new ByteArrayOutputStream(8192);
            boolean success = true;
            for (int i = 0; i < pairs.size(); i++) {
                Pair pair = pairs.get(i);
                Packet start = pair.start;
                byte[] bytes = start.payload();
                if (start.number("usb.bmRequestType") != 0x40 || start.number("usb.setup.wIndex") != 0
                        || start.number("usb.setup.wLength") != bytes.length) {
                    throw new IOException("Invalid A0 request fields or missing payload");
                }
                int address = start.number("usb.setup.wValue");
                if (i == 0 || i == 1 || i == 130 || i == 131) {
                    int expectedAddress = i == 0 || i == 130 ? 0x7f92 : 0xe600;
                    int expectedValue = i < 2 ? 1 : 0;
                    if (address != expectedAddress || bytes.length != 1 || bytes[0] != expectedValue) {
                        throw new IOException("Unexpected reset order");
                    }
                    observed.add(new Reset(start, pair.end));
                } else {
                    if (address != (i - 2) * 64 || bytes.length != 64) {
                        throw new IOException("Missing, reordered or padded image chunk");
                    }
                    image.write(bytes, 0, bytes.length);
                }
                success &= pair.end.status() == 0;
            }
            imageBytes = image.size();
            imageSha256 = digest(image.toByteArray());
            if (!imageSha256.equals(expectedSha256)) throw new IOException("Captured image hash mismatch");
            allCompletionsSuccessful = success;
            resets = Collections.unmodifiableList(observed);
        }
    }

    private static final class Packet {
        final Map<String, String> fields;
        final int frame;
        Packet(String[] headers, String line) throws IOException {
            String[] values = line.split("\t", -1);
            if (values.length != headers.length) throw new IOException("Truncated TSV row");
            fields = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String value = values[i];
                if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
                fields.put(headers[i], value);
            }
            frame = number("frame.number");
            if (number("frame.len") != number("frame.cap_len")) throw new IOException("Truncated capture frame");
        }
        String get(String key) throws IOException {
            String value = fields.get(key);
            if (value == null) throw new IOException("Missing TSV field: " + key);
            return value;
        }
        int number(String key) throws IOException {
            try { return Integer.decode(get(key)); }
            catch (NumberFormatException error) { throw new IOException("Invalid numeric field: " + key, error); }
        }
        long status() throws IOException {
            try { return Long.decode(get("usb.usbd_status")); }
            catch (NumberFormatException error) { throw new IOException("Missing completion status", error); }
        }
        byte[] payload() throws IOException {
            String hex = get("usb.data_fragment");
            if (hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]*")) throw new IOException("Invalid payload encoding");
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return bytes;
        }
    }

    private static final class Pair {
        final Packet start, end;
        Pair(Packet start, Packet end) { this.start = start; this.end = end; }
    }

    private Tbs5530ResetTranscript() { }

    public static List<Attempt> parse(String tsv, String expectedSha256) throws IOException {
        if (tsv == null || expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IOException("TSV and exact image SHA-256 required");
        }
        String[] lines = tsv.split("\r?\n");
        if (lines.length < 2) throw new IOException("Empty capture");
        String[] headers = lines[0].split("\t", -1);
        Map<String, Packet> pending = new HashMap<>();
        List<Pair> firmware = new ArrayList<>();
        String target = null;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            Packet packet = new Packet(headers, lines[i]);
            String identity = packet.get("usb.bus_id") + ":" + packet.get("usb.device_address");
            String irp = packet.get("usb.irp_id");
            String key = identity + ":" + irp;
            if (irp.isEmpty()) continue;
            String stage = packet.get("usb.control_stage");
            if (packet.get("usb.irp_info").equals("0x00") && stage.equals("0")) {
                if (pending.put(key, packet) != null) throw new IOException("Concurrent reuse of IRP ID");
            } else if (packet.get("usb.irp_info").equals("0x01") && stage.equals("3")) {
                Packet start = pending.remove(key);
                if (start == null) continue;
                if (!packet.get("usb.request_in").equals(Integer.toString(start.frame))) {
                    throw new IOException("Completion points to another request frame");
                }
                if (!start.get("usb.setup.bRequest").equals("160")) continue;
                if (target == null) target = identity;
                if (!target.equals(identity)) throw new IOException("Multiple firmware devices in transcript");
                firmware.add(new Pair(start, packet));
            }
        }
        for (Packet start : pending.values()) {
            if (start.get("usb.setup.bRequest").equals("160")) throw new IOException("A0 completion missing");
        }
        if (firmware.isEmpty() || firmware.size() % 132 != 0) throw new IOException("Incomplete firmware sequence");
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < firmware.size(); i += 132) {
            attempts.add(new Attempt(firmware.subList(i, i + 132), expectedSha256));
        }
        return Collections.unmodifiableList(attempts);
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
