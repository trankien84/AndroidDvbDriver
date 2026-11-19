// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

final class Tbs5530TsPrefixFilter {
    static final int PACKET_SIZE = 188;
    private static final int STALE_BYTES = 2048;
    private static final int SYNC_LOOKAHEAD = 4 * PACKET_SIZE;
    private final byte[] pending;
    private int held;
    private long discarded;
    private boolean locked;
    private byte[] output;

    Tbs5530TsPrefixFilter(int maxChunkSize) {
        pending = new byte[maxChunkSize + SYNC_LOOKAHEAD];
    }

    int filter(byte[] input, int length) {
        if (locked) {
            output = input;
            return length;
        }
        int skip = (int) Math.min(length, Math.max(0, STALE_BYTES - discarded));
        discarded += skip;
        if (skip == length) return 0;

        int end = held + length - skip;
        System.arraycopy(input, skip, pending, held, length - skip);
        int limit = end - SYNC_LOOKAHEAD;
        for (int candidate = 0; candidate < limit; candidate++) {
            int sync = candidate;
            while (sync <= candidate + SYNC_LOOKAHEAD && pending[sync] == 0x47) sync += PACKET_SIZE;
            if (sync > candidate + SYNC_LOOKAHEAD) {
                int count = end - candidate;
                System.arraycopy(pending, candidate, pending, 0, count);
                discarded += candidate;
                held = 0;
                locked = true;
                output = pending;
                return count;
            }
        }

        // Keep only candidates that still need bytes from a later chunk.
        int rejected = Math.max(0, limit);
        held = end - rejected;
        System.arraycopy(pending, rejected, pending, 0, held);
        discarded += rejected;
        return 0;
    }

    void discardPending() {
        discarded += held;
        held = 0;
    }

    byte[] output() { return output; }
    long discardedBytes() { return discarded; }
    boolean isLocked() { return locked; }
}
