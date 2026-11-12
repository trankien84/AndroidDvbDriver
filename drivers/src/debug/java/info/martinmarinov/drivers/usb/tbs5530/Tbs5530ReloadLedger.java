// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A distinct append-only journal. Never parses or changes the historic firmware ledger. */
public final class Tbs5530ReloadLedger {
    public static final String FILE = "fx2-reload-v1.txt", ANCHOR = "fx2-reload-v1.anchor";
    public static final String HEADER = "TBS5530-FX2-RELOAD/v1\nHOST samsung-sm-x926b-api36\n"
            + "INITIAL_HISTORY_SHA256 b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb\n";
    public static final String ANCHOR_HEADER = "TBS5530-FX2-RELOAD-ANCHOR/v1\n" + hash(bytes(HEADER)) + "\n";
    public static final String INITIALIZED_HEADER = "TBS5530-FX2-RELOAD-INITIALIZED/v1\n" + hash(bytes(HEADER)) + "\n";
    public interface Storage {
        byte[] read() throws IOException;
        byte[] anchor() throws IOException;
        /** Compare both exact prefixes, append journal then anchor, fsync both; never create or replace. */
        void appendAndSync(byte[] expected, byte[] anchor, byte[] suffix, byte[] anchorSuffix) throws IOException;
    }
    private final Storage storage;
    private byte[] expected, anchor;
    private boolean faulted;

    public Tbs5530ReloadLedger(Storage storage) throws IOException {
        this.storage = java.util.Objects.requireNonNull(storage);
        expected = storage.read(); anchor = storage.anchor(); validate(expected, anchor);
    }
    public synchronized String sha256() { return hash(expected); }
    public synchronized String anchorSha256() { return hash(anchor); }
    public synchronized void require() throws IOException {
        try {
            if (faulted || !Arrays.equals(expected, storage.read()) || !Arrays.equals(anchor, storage.anchor()))
                throw new IOException("Reload store missing, changed or rolled back; no rearm");
            validate(expected, anchor);
        } catch (IOException | RuntimeException error) { faulted = true; throw error; }
    }
    public synchronized void consume(String epochKey) throws IOException {
        try {
            require();
            if (epochKey == null || !epochKey.matches("[a-f0-9]{64}")) throw new IOException("Invalid physical reload epoch key");
            if (validate(expected, anchor).contains(epochKey)) throw new IOException("Physical epoch reload already consumed");
            byte[] suffix = bytes("FX2_RELOAD_CONSUMED " + epochKey + "\n");
            byte[] next = concat(expected, suffix), nextAnchorSuffix = bytes(hash(next) + "\n");
            storage.appendAndSync(expected.clone(), anchor.clone(), suffix, nextAnchorSuffix);
            byte[] nextAnchor = concat(anchor, nextAnchorSuffix);
            if (!Arrays.equals(next, storage.read()) || !Arrays.equals(nextAnchor, storage.anchor()))
                throw new IOException("Reload consume durability uncertain");
            validate(next, nextAnchor);
            expected = next; anchor = nextAnchor;
        } catch (IOException | RuntimeException error) { faulted = true; throw error; }
    }
    public static Set<String> validate(byte[] journal, byte[] anchor) throws IOException {
        if (journal == null || anchor == null || journal.length > 1024 * 1024 || anchor.length > 1024 * 1024)
            throw new IOException("Reload store missing or oversized");
        String text = new String(journal, StandardCharsets.UTF_8), proof = new String(anchor, StandardCharsets.UTF_8);
        if (!Arrays.equals(journal, bytes(text)) || !Arrays.equals(anchor, bytes(proof))
                || !text.startsWith(HEADER) || !proof.startsWith(ANCHOR_HEADER) || !text.endsWith("\n"))
            throw new IOException("Reload header or anchor corrupted");
        Set<String> keys = new HashSet<>();
        StringBuilder prefix = new StringBuilder(HEADER), requiredAnchor = new StringBuilder(ANCHOR_HEADER);
        String tail = text.substring(HEADER.length());
        if (!tail.isEmpty()) for (String line : tail.split("\n", -1)) {
            if (line.isEmpty() && prefix.length() == text.length()) break;
            if (!line.matches("FX2_RELOAD_CONSUMED [a-f0-9]{64}") || !keys.add(line.substring(20)))
                throw new IOException("Invalid or duplicated reload consumption");
            prefix.append(line).append('\n'); requiredAnchor.append(hash(bytes(prefix.toString()))).append('\n');
        }
        if (!proof.equals(requiredAnchor.toString())) throw new IOException("Reload journal/anchor rollback or partial append");
        return keys;
    }
    public static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    public static String hash(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(64);
            for (byte b : digest) text.append("0123456789abcdef".charAt((b & 255) >>> 4)).append("0123456789abcdef".charAt(b & 15));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static byte[] concat(byte[] left, byte[] right) {
        byte[] next = Arrays.copyOf(left, left.length + right.length); System.arraycopy(right, 0, next, left.length, right.length); return next;
    }
}
