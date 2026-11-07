// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Offline statistics for 188-byte MPEG-TS, without Android, I/O or payload delivery.
 * Acquisition requires three complete, structurally valid packets at 188-byte intervals.
 * Before acquisition, rejected candidates are discarded bytes, not malformed packets.
 * A lost sync byte clears all continuity baselines; malformed/TEI packets clear their PID.
 * Packet alignment alone is not program-structure evidence. Current, complete PAT/PMT
 * tables require section bounds, MPEG-2 CRC and matching program/PID association.
 * Syntax follows ITU-T H.222.0, 2.4.3/2.4.4; SDT follows ETSI EN 300 468, 5.2.3.
 * PES checking is limited to the three-byte start-code prefix on known audio/video PIDs;
 * it does not validate codecs, timestamps, RF quality or successful playback.
 *
 * <p>PSI sections are bounded to 1024 bytes; tables have at most 256 sections. Only PAT,
 * actual SDT and PAT-announced PMT PIDs are assembled. Carry is at most 564 bytes.
 * Exact duplicate detection retains at most one 188-byte packet per non-null PID
 * (8191 packets), allocated lazily and reused. Memory is bounded
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
    private long validPatSections;
    private long validPmtSections;
    private long validSdtSections;
    private long psiCrcErrors;
    private long invalidPsiSections;
    private long invalidPesHeaders;
    private long pesStarts;
    private long scrambledPackets;
    private final boolean[] seenPid = new boolean[PID_COUNT];
    private final Section[] sections = new Section[PID_COUNT];
    private final boolean[] pesPid = new boolean[PID_COUNT];
    private final boolean[] announcedPmtPid = new boolean[PID_COUNT];
    private final int[] pesPrefix = new int[PID_COUNT];
    private final Table pat = new Table();
    private final Table sdt = new Table();
    private final TreeMap<Integer, Program> programs = new TreeMap<>();
    private int uniquePidCount;
    private int transportStreamId = -1;
    private int serviceCount;

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
                Arrays.fill(sections, null);
                Arrays.fill(pesPid, false);
                Arrays.fill(pesPrefix, 0);
                programs.clear();
                Arrays.fill(announcedPmtPid, false);
                pat.reset();
                sdt.reset();
                transportStreamId = -1;
                serviceCount = 0;
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
        if (read(start) != 0x47 || (read(start + 3) & 0xc0) == 0x40) return -1;
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
        if (!seenPid[pid]) {
            seenPid[pid] = true;
            uniquePidCount++;
        }
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
            clearAssembly(pid);
        }
        lastCounter[pid] = (byte) cc;
        hasPayload[pid] = true;
        duplicateSeen[pid] = repeated;
        if (lastPacket[pid] == null) lastPacket[pid] = new byte[PACKET_SIZE];
        for (int i = 0; i < PACKET_SIZE; i++) lastPacket[pid][i] = (byte) read(i);
        if ((read(3) & 0xc0) != 0) {
            scrambledPackets = add(scrambledPackets, 1);
            clearAssembly(pid);
            return;
        }
        boolean start = (read(1) & 0x40) != 0;
        if (pid == 0 || pid == 0x11 || isPmtPid(pid)) {
            if (sections[pid] == null) sections[pid] = new Section();
            sections[pid].accept(pid, payload, start);
        } else if (pesPid[pid]) {
            if (start) {
                if (pesPrefix[pid] != 0) invalidPesHeaders = add(invalidPesHeaders, 1);
                pesPrefix[pid] = 1;
                pesStarts = add(pesStarts, 1);
            }
            for (int i = payload; i < PACKET_SIZE && pesPrefix[pid] != 0; i++) {
                int position = pesPrefix[pid] - 1;
                if (read(i) != (position == 2 ? 1 : 0)) {
                    invalidPesHeaders = add(invalidPesHeaders, 1);
                    pesPrefix[pid] = 0;
                } else {
                    pesPrefix[pid] = position == 2 ? 0 : pesPrefix[pid] + 1;
                }
            }
        }
    }

    private void clearPid(int pid) {
        lastCounter[pid] = -1;
        hasPayload[pid] = false;
        duplicateSeen[pid] = false;
        clearAssembly(pid);
    }

    private void clearAssembly(int pid) {
        if (sections[pid] != null) sections[pid].reset();
        pesPrefix[pid] = 0;
    }

    private boolean isPmtPid(int pid) {
        return announcedPmtPid[pid];
    }

    private final class Section {
        final byte[] bytes = new byte[1024];
        int size;
        int target;

        void reset() { size = target = 0; }

        void accept(int pid, int offset, boolean start) {
            if (start) {
                int pointer = read(offset++);
                if (pointer > PACKET_SIZE - offset) {
                    invalidPsiSections = add(invalidPsiSections, 1);
                    reset();
                    return;
                }
                int boundary = offset + pointer;
                while (offset < boundary && size != 0) append(pid, read(offset++));
                if (size != 0) invalidPsiSections = add(invalidPsiSections, 1);
                reset();
                offset = boundary;
            } else if (size == 0) {
                return; // Joining mid-section is not evidence of a malformed section.
            }
            while (offset < PACKET_SIZE) {
                if (size == 0 && (!start || read(offset) == 0xff)) return;
                if (!append(pid, read(offset++))) return;
            }
        }

        boolean append(int pid, int value) {
            bytes[size++] = (byte) value;
            if (size == 3) {
                target = 3 + ((bytes[1] & 0x0f) << 8) + (bytes[2] & 0xff);
                if (target < 12 || target > bytes.length) {
                    invalidPsiSections = add(invalidPsiSections, 1);
                    reset();
                    return false;
                }
            }
            if (target != 0 && size == target) {
                parseSection(pid, Arrays.copyOf(bytes, size));
                reset();
            }
            return true;
        }
    }

    private static final class Table {
        final byte[][] parts = new byte[256][];
        int extension = -1;
        int version = -1;
        int last = -1;
        int count;
        boolean applied;

        void reset() {
            Arrays.fill(parts, null);
            extension = version = last = -1;
            count = 0;
            applied = false;
        }

        boolean add(byte[] section) {
            int ext = word(section, 3);
            int ver = (section[5] >>> 1) & 31;
            int end = section[7] & 0xff;
            int number = section[6] & 0xff;
            if (extension != ext || version != ver || last != end) {
                reset();
                extension = ext;
                version = ver;
                last = end;
            }
            if (parts[number] != null && !Arrays.equals(parts[number], section)) {
                // Same-version mutation must not combine sections from different tables.
                reset();
                extension = ext;
                version = ver;
                last = end;
            }
            if (parts[number] == null) {
                parts[number] = section;
                count++;
            }
            return count == last + 1 && !applied;
        }
    }

    private static final class Program {
        final int pid;
        final Table table = new Table();
        int pcrPid = -1;
        int[] elementaryPids = new int[0];
        boolean[] pes = new boolean[0];

        Program(int pid) { this.pid = pid; }
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static boolean descriptorsFit(byte[] bytes, int offset, int end) {
        while (offset < end) {
            if (offset + 2 > end) return false;
            offset += 2 + (bytes[offset + 1] & 0xff);
        }
        return offset == end;
    }

    private void parseSection(int pid, byte[] bytes) {
        int id = bytes[0] & 0xff;
        if (!((pid == 0 && id == 0) || (pid == 0x11 && id == 0x42)
                || (isPmtPid(pid) && id == 2))) return;
        int end = bytes.length - 4;
        if ((bytes[1] & 0xf0) != (id == 0x42 ? 0xf0 : 0xb0)
                || (bytes[5] & 0xc0) != 0xc0 || (bytes[6] & 0xff) > (bytes[7] & 0xff)) {
            invalidPsiSections = add(invalidPsiSections, 1);
            return;
        }
        int crc = -1;
        for (byte value : bytes) {
            crc ^= (value & 0xff) << 24;
            for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
        }
        if (crc != 0) {
            psiCrcErrors = add(psiCrcErrors, 1);
            return;
        }
        if ((bytes[5] & 1) == 0) return; // Valid next tables are not current evidence.
        boolean valid = id == 0 ? parsePat(bytes, end)
                : id == 2 ? parsePmt(pid, bytes, end) : parseSdt(bytes, end);
        if (!valid) invalidPsiSections = add(invalidPsiSections, 1);
    }

    private boolean parsePat(byte[] bytes, int end) {
        if ((end - 8) % 4 != 0) return false;
        for (int i = 8; i < end; i += 4) {
            int pid = word(bytes, i + 2) & 0x1fff;
            if ((bytes[i + 2] & 0xe0) != 0xe0 || pid < 0x10 || pid == NULL_PID) return false;
        }
        validPatSections = add(validPatSections, 1);
        if (!pat.add(bytes)) return true;
        TreeMap<Integer, Program> next = new TreeMap<>();
        for (byte[] part : pat.parts) {
            if (part == null) continue;
            for (int i = 8; i < part.length - 4; i += 4) {
                int number = word(part, i);
                if (number == 0) continue; // Network PID, not a PMT.
                if (next.put(number, new Program(word(part, i + 2) & 0x1fff)) != null) return false;
            }
        }
        programs.clear();
        programs.putAll(next);
        Arrays.fill(announcedPmtPid, false);
        for (Program program : programs.values()) announcedPmtPid[program.pid] = true;
        Arrays.fill(pesPid, false);
        Arrays.fill(pesPrefix, 0);
        for (int pid = 1; pid < PID_COUNT; pid++) if (pid != 0x11) sections[pid] = null;
        transportStreamId = pat.extension;
        serviceCount = 0;
        sdt.reset();
        pat.applied = true;
        return true;
    }

    private boolean parsePmt(int pid, byte[] bytes, int end) {
        Program program = programs.get(word(bytes, 3));
        if (program == null || program.pid != pid) return false;
        if (end < 12 || bytes[6] != 0 || bytes[7] != 0
                || (bytes[8] & 0xe0) != 0xe0 || (bytes[10] & 0xf0) != 0xf0) return false;
        int pcr = word(bytes, 8) & 0x1fff;
        if (pcr < 0x10) return false;
        int cursor = 12 + (word(bytes, 10) & 0x0fff);
        if (cursor > end || !descriptorsFit(bytes, 12, cursor)) return false;
        int count = 0;
        int[] pids = new int[PID_COUNT];
        boolean[] pes = new boolean[PID_COUNT];
        while (cursor < end) {
            if (cursor + 5 > end || (bytes[cursor + 1] & 0xe0) != 0xe0
                    || (bytes[cursor + 3] & 0xf0) != 0xf0) return false;
            int elementary = word(bytes, cursor + 1) & 0x1fff;
            if (elementary < 0x10 || elementary == NULL_PID) return false;
            for (int i = 0; i < count; i++) if (pids[i] == elementary) return false;
            pids[count] = elementary;
            int type = bytes[cursor] & 0xff;
            pes[count++] = type == 1 || type == 2 || type == 3 || type == 4
                    || type == 0x0f || type == 0x10 || type == 0x11 || type == 0x1b || type == 0x24;
            int next = cursor + 5 + (word(bytes, cursor + 3) & 0x0fff);
            if (next > end || !descriptorsFit(bytes, cursor + 5, next)) return false;
            cursor = next;
        }
        validPmtSections = add(validPmtSections, 1);
        if (program.table.add(bytes)) {
            program.pcrPid = word(bytes, 8) & 0x1fff;
            program.elementaryPids = Arrays.copyOf(pids, count);
            program.pes = Arrays.copyOf(pes, count);
            program.table.applied = true;
            Arrays.fill(pesPid, false);
            Arrays.fill(pesPrefix, 0);
            for (Program p : programs.values()) {
                for (int i = 0; i < p.elementaryPids.length; i++) {
                    if (p.pes[i]) pesPid[p.elementaryPids[i]] = true;
                }
            }
        }
        return true;
    }

    private boolean parseSdt(byte[] bytes, int end) {
        if (end < 11 || transportStreamId != word(bytes, 3)) return false;
        int cursor = 11;
        while (cursor < end) {
            if (cursor + 5 > end) return false;
            int next = cursor + 5 + (word(bytes, cursor + 3) & 0x0fff);
            if (next > end || !descriptorsFit(bytes, cursor + 5, next)) return false;
            cursor = next;
        }
        validSdtSections = add(validSdtSections, 1);
        if (sdt.add(bytes)) {
            boolean[] services = new boolean[65536];
            serviceCount = 0;
            for (byte[] part : sdt.parts) {
                if (part == null) continue;
                for (int i = 11; i < part.length - 4; i += 5 + (word(part, i + 3) & 0x0fff)) {
                    int service = word(part, i);
                    if (!services[service]) { services[service] = true; serviceCount++; }
                }
            }
            sdt.applied = true;
        }
        return true;
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
        public final long validPatSections;
        public final long validPmtSections;
        public final long validSdtSections;
        public final long psiCrcErrors;
        public final long invalidPsiSections;
        public final long pesStarts;
        /** Only invalid PES start-code prefixes, not full PES/codec validation. */
        public final long invalidPesHeaders;
        /** Valid non-TEI, non-null payload packets skipped because they are scrambled. */
        public final long scrambledPackets;
        /** Distinct observed header PIDs, including null, TEI and malformed packets. */
        public final int uniquePidCount;
        /** Current completed PAT identity, or -1 if none since the last sync loss. */
        public final int transportStreamId;
        public final int programCount;
        public final int serviceCount;
        public final int programsWithPmt;
        public final int elementaryPidCount;
        /** First validated program with an elementary PID; -1 when unavailable. */
        public final int programNumber;
        public final int pmtPid;
        public final int pcrPid;
        private final boolean validProgramStructure;

        /**
         * Structure evidence only: complete current PAT plus CRC/bounds-validated PMT
         * matching its program/PID, with at least one elementary PID. Three sync packets
         * alone cannot pass. This does not hide TEI/CC errors or establish RF confidence;
         * callers must evaluate error counters separately. Sync loss revokes this evidence.
         */
        public boolean hasValidProgramStructure() { return validProgramStructure; }

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
            validPatSections = parser.validPatSections;
            validPmtSections = parser.validPmtSections;
            validSdtSections = parser.validSdtSections;
            psiCrcErrors = parser.psiCrcErrors;
            invalidPsiSections = parser.invalidPsiSections;
            pesStarts = parser.pesStarts;
            invalidPesHeaders = parser.invalidPesHeaders;
            scrambledPackets = parser.scrambledPackets;
            uniquePidCount = parser.uniquePidCount;
            transportStreamId = parser.transportStreamId;
            programCount = parser.programs.size();
            serviceCount = parser.serviceCount;
            int complete = 0;
            int number = -1;
            int pmt = -1;
            int pcr = -1;
            boolean[] elementary = new boolean[PID_COUNT];
            int pidCount = 0;
            for (Map.Entry<Integer, Program> entry : parser.programs.entrySet()) {
                Program program = entry.getValue();
                if (!program.table.applied) continue;
                complete++;
                for (int pid : program.elementaryPids) {
                    if (!elementary[pid]) { elementary[pid] = true; pidCount++; }
                }
                if (number == -1 && program.elementaryPids.length != 0) {
                    number = entry.getKey();
                    pmt = program.pid;
                    pcr = program.pcrPid;
                }
            }
            programsWithPmt = complete;
            elementaryPidCount = pidCount;
            programNumber = number;
            pmtPid = pmt;
            pcrPid = pcr;
            validProgramStructure = synchronizedToStream && parser.pat.applied && number != -1;
        }
    }
}
