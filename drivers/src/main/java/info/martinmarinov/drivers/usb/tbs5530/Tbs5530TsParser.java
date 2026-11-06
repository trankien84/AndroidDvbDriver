// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.Arrays;

/**
 * Offline statistics for 188-byte MPEG-TS, without Android, I/O or payload delivery.
 * Acquisition requires three complete, structurally valid packets at 188-byte intervals.
 * Before acquisition, rejected candidates are discarded bytes, not malformed packets.
 * A lost sync byte clears all continuity baselines; malformed/TEI packets clear their PID.
 * No PSI, PES, codec, PCR-value or frontend/lock validation is performed.
 *
 * <p>Carry is at most 564 bytes. Exact duplicate detection retains at most one 188-byte
 * packet per non-null PID (8191 packets), allocated lazily and reused. Memory is bounded
 * independently of input size/duration. Input arrays are never retained. Counters saturate
 * at {@link Long#MAX_VALUE}; instances represent one stream and have no implicit EOF flush.
 * Both public operations are synchronized to provide coherent snapshots across threads.</p>
 */
public final class Tbs5530TsParser {
    public static final int PACKET_SIZE = 188;
    public static final int MAX_BUFFERED_BYTES = 3 * PACKET_SIZE;
    private static final int PID_COUNT = 8192;
    private static final int NULL_PID = 0x1fff;

    private final byte[] carry = new byte[MAX_BUFFERED_BYTES];
    private final byte[] lastCounter = new byte[PID_COUNT];
    private final byte[][] lastPacket = new byte[PID_COUNT][];
    private final boolean[] hasPayload = new boolean[PID_COUNT];
    private final boolean[] duplicateSeen = new boolean[PID_COUNT];
    private int head;
    private int buffered;
    private boolean synced;
    private long bytesReceived;
    private long discardedBytes;
    private long packets;
    private long syncLosses;
    private long malformedPackets;
    private long transportErrorPackets;
    private long nullPackets;
    private long continuityErrors;
    private long expectedDiscontinuities;
    private long duplicatePackets;

    public Tbs5530TsParser() {
        Arrays.fill(lastCounter, (byte) -1);
    }

    /**
     * Copies/consumes exactly the requested range, including garbage and partial packets.
     * Validation precedes any mutation; zero-length ranges, including at array end, are valid.
     *
     * @throws NullPointerException if data is null
     * @throws IndexOutOfBoundsException if the range is negative, overflowing or outside data
     */
    public synchronized void accept(byte[] data, int offset, int length) {
        if (data == null) throw new NullPointerException("data");
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IndexOutOfBoundsException("Invalid TS input range");
        }
        bytesReceived = add(bytesReceived, length);
        while (length > 0) {
            int tail = (head + buffered) % carry.length;
            int copy = Math.min(length, Math.min(carry.length - buffered, carry.length - tail));
            System.arraycopy(data, offset, carry, tail, copy);
            buffered += copy;
            offset += copy;
            length -= copy;
            drain();
        }
    }

    /** Returns an immutable scalar-only copy; no packet bytes or mutable PID state escape. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(this);
    }

    private void drain() {
        while (true) {
            if (!synced) {
                if (buffered < MAX_BUFFERED_BYTES) return;
                if (payloadOffset(0) < 0 || payloadOffset(PACKET_SIZE) < 0
                        || payloadOffset(2 * PACKET_SIZE) < 0) {
                    remove(1);
                    discardedBytes = add(discardedBytes, 1);
                    continue;
                }
                synced = true;
            }
            if (buffered < PACKET_SIZE) return;
            if (read(0) != 0x47) {
                syncLosses = add(syncLosses, 1);
                synced = false;
                Arrays.fill(lastCounter, (byte) -1);
                Arrays.fill(hasPayload, false);
                Arrays.fill(duplicateSeen, false);
                continue;
            }
            consumePacket();
            remove(PACKET_SIZE);
        }
    }

    private void remove(int length) {
        head = (head + length) % carry.length;
        buffered -= length;
    }

    private int read(int offset) {
        int index = head + offset;
        if (index >= carry.length) index -= carry.length;
        return carry[index] & 0xff;
    }

    /** Returns 188 for adaptation-only, otherwise the payload start, or -1 if malformed. */
    private int payloadOffset(int start) {
        if (read(start) != 0x47) return -1;
        int afc = (read(start + 3) >>> 4) & 3;
        if (afc == 0) return -1;
        if (afc == 1) return 4;
        int length = read(start + 4);
        if ((afc == 2 && length != 183) || (afc == 3 && length > 182)) return -1;
        int end = 5 + length;
        if (length == 0) return end;
        int flags = read(start + 5);
        int cursor = 6;
        if ((flags & 0x10) != 0) cursor += 6; // PCR
        if ((flags & 0x08) != 0) cursor += 6; // OPCR
        if ((flags & 0x04) != 0) cursor++; // splice countdown
        if (cursor > end) return -1;
        if ((flags & 0x02) != 0) {
            if (cursor >= end) return -1;
            cursor += 1 + read(start + cursor);
            if (cursor > end) return -1;
        }
        if ((flags & 0x01) != 0) {
            if (cursor >= end) return -1;
            int extensionLength = read(start + cursor++);
            int extensionEnd = cursor + extensionLength;
            if (extensionEnd > end) return -1;
            if (extensionLength > 0) {
                int extensionFlags = read(start + cursor++);
                if ((extensionFlags & 0x80) != 0) cursor += 2; // LTW
                if ((extensionFlags & 0x40) != 0) cursor += 3; // piecewise rate
                if ((extensionFlags & 0x20) != 0) cursor += 5; // seamless splice
                if (cursor > extensionEnd) return -1;
            }
        }
        return end;
    }

    private void consumePacket() {
        packets = add(packets, 1);
        int pid = ((read(1) & 0x1f) << 8) | read(2);
        boolean tei = (read(1) & 0x80) != 0;
        if (tei) transportErrorPackets = add(transportErrorPackets, 1);
        if (pid == NULL_PID) nullPackets = add(nullPackets, 1);
        int payload = payloadOffset(0);
        if (payload < 0) malformedPackets = add(malformedPackets, 1);
        if (tei || payload < 0) {
            clearPid(pid);
            return;
        }
        boolean discontinuity = ((read(3) & 0x20) != 0)
                && read(4) > 0 && (read(5) & 0x80) != 0;
        if (discontinuity) {
            expectedDiscontinuities = add(expectedDiscontinuities, 1);
            clearPid(pid);
        }
        if (pid == NULL_PID) return;
        int cc = read(3) & 0x0f;
        int previous = lastCounter[pid];
        if (payload == PACKET_SIZE) {
            if (previous >= 0 && cc != previous) {
                continuityErrors = add(continuityErrors, 1);
                clearPid(pid);
            }
            lastCounter[pid] = (byte) cc;
            return;
        }
        boolean repeated = previous >= 0 && cc == previous;
        if (previous >= 0 && cc != ((previous + 1) & 0x0f)) {
            if (repeated && hasPayload[pid] && !duplicateSeen[pid] && samePacket(pid)) {
                duplicatePackets = add(duplicatePackets, 1);
                duplicateSeen[pid] = true;
                return;
            }
            continuityErrors = add(continuityErrors, 1);
        }
        lastCounter[pid] = (byte) cc;
        hasPayload[pid] = true;
        duplicateSeen[pid] = repeated;
        if (lastPacket[pid] == null) lastPacket[pid] = new byte[PACKET_SIZE];
        for (int i = 0; i < PACKET_SIZE; i++) lastPacket[pid][i] = (byte) read(i);
    }

    private void clearPid(int pid) {
        lastCounter[pid] = -1;
        hasPayload[pid] = false;
        duplicateSeen[pid] = false;
    }

    private boolean samePacket(int pid) {
        byte[] previous = lastPacket[pid];
        // A legal duplicate may update PCR, but all other bytes must remain identical.
        boolean pcr = (read(3) & 0x20) != 0 && read(4) >= 7 && (read(5) & 0x10) != 0
                && (previous[3] & 0x20) != 0 && (previous[5] & 0x10) != 0;
        for (int i = 0; i < PACKET_SIZE; i++) {
            if (pcr && i >= 6 && i < 12) continue;
            if ((byte) read(i) != previous[i]) return false;
        }
        return true;
    }

    private static long add(long value, long amount) {
        return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
    }

    /**
     * Cumulative counters. packets includes malformed/TEI/null/duplicate packets after
     * acquisition; these categories may overlap. syncLosses counts loss episodes, not bytes.
     * continuityErrors counts unexpected counter/duplicate events, not inferred lost packets.
     * Adaptation-only must keep CC; payload increments modulo 16. One identical retransmission
     * per payload CC run is allowed (PCR alone may differ); further repeats are errors.
     * expectedDiscontinuities counts valid non-TEI discontinuity indicators, including null PID,
     * and rebaselines that packet's PID to its CC, even when adaptation-only. TEI/malformed
     * packets do not participate in continuity, and null PID is always exempt.
     */
    public static final class Snapshot {
        public final long bytesReceived;
        public final long discardedBytes;
        public final long packets;
        public final long syncLosses;
        public final long malformedPackets;
        public final long transportErrorPackets;
        public final long nullPackets;
        public final long continuityErrors;
        public final long expectedDiscontinuities;
        public final long duplicatePackets;
        public final int bufferedBytes;
        public final boolean synchronizedToStream;

        private Snapshot(Tbs5530TsParser parser) {
            bytesReceived = parser.bytesReceived;
            discardedBytes = parser.discardedBytes;
            packets = parser.packets;
            syncLosses = parser.syncLosses;
            malformedPackets = parser.malformedPackets;
            transportErrorPackets = parser.transportErrorPackets;
            nullPackets = parser.nullPackets;
            continuityErrors = parser.continuityErrors;
            expectedDiscontinuities = parser.expectedDiscontinuities;
            duplicatePackets = parser.duplicatePackets;
            bufferedBytes = parser.buffered;
            synchronizedToStream = parser.synced;
        }
    }
}
