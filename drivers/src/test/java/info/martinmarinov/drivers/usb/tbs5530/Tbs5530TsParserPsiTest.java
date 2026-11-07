// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530TsParserPsiTest {
    private static final int PMT = 0x100;
    private static final int VIDEO = 0x101;

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    private static int crc(byte[] bytes, int end) {
        int crc = 0xffffffff;
        for (int i = 0; i < end; i++) {
            for (int bit = 7; bit >= 0; bit--) {
                boolean high = ((crc >>> 31) ^ ((bytes[i] >>> bit) & 1)) != 0;
                crc <<= 1;
                if (high) crc ^= 0x04c11db7;
            }
        }
        return crc;
    }

    private static byte[] checksum(byte[] bytes) {
        int crc = crc(bytes, bytes.length - 4);
        for (int i = 0; i < 4; i++) bytes[bytes.length - 4 + i] = (byte) (crc >>> (24 - 8 * i));
        return bytes;
    }

    private static byte[] section(int id, int extension, int number, int last, byte[] body) {
        int length = 9 + body.length;
        byte[] bytes = new byte[length + 3];
        bytes[0] = (byte) id;
        bytes[1] = (byte) ((id == 0x42 ? 0xf0 : 0xb0) | (length >>> 8));
        bytes[2] = (byte) length;
        bytes[3] = (byte) (extension >>> 8);
        bytes[4] = (byte) extension;
        bytes[5] = (byte) 0xc1;
        bytes[6] = (byte) number;
        bytes[7] = (byte) last;
        System.arraycopy(body, 0, bytes, 8, body.length);
        return checksum(bytes);
    }

    private static byte[] pat(int number, int pid) {
        return section(0, 7, 0, 0, new byte[] {(byte) (number >>> 8), (byte) number,
                (byte) (0xe0 | pid >>> 8), (byte) pid});
    }

    private static byte[] pmt(int number, byte[] descriptors) {
        return section(2, number, 0, 0, join(new byte[] {(byte) 0xe1, 1,
                (byte) (0xf0 | descriptors.length >>> 8), (byte) descriptors.length},
                descriptors, new byte[] {0x1b, (byte) 0xe1, 1, (byte) 0xf0, 0}));
    }

    private static byte[] packet(int pid, int cc, boolean start, byte[] payload) {
        if (payload.length < 1 || payload.length > 184) throw new AssertionError();
        byte[] bytes = new byte[188];
        Arrays.fill(bytes, (byte) 0xff);
        bytes[0] = 0x47;
        bytes[1] = (byte) ((pid >>> 8) | (start ? 0x40 : 0));
        bytes[2] = (byte) pid;
        int offset = 4;
        bytes[3] = (byte) (0x10 | cc);
        if (payload.length != 184) {
            bytes[3] |= 0x20;
            bytes[4] = (byte) (183 - payload.length);
            if (bytes[4] != 0) bytes[5] = 0;
            offset = 188 - payload.length;
        }
        System.arraycopy(payload, 0, bytes, offset, payload.length);
        return bytes;
    }

    private static byte[] psi(int pid, int cc, byte[] section) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < section.length) {
            int length = Math.min(offset == 0 ? 183 : 184, section.length - offset);
            byte[] payload = Arrays.copyOfRange(section, offset, offset + length);
            boolean first = offset == 0;
            if (first) payload = join(new byte[] {0}, payload);
            byte[] bytes = packet(pid, cc++ & 15, first, payload);
            out.write(bytes, 0, bytes.length);
            offset += length;
        }
        return out.toByteArray();
    }

    private static void feed(Tbs5530TsParser parser, byte[] bytes) {
        parser.accept(bytes, 0, bytes.length);
    }

    private static Tbs5530TsParser aligned() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] n = packet(0x1fff, 0, false, new byte[184]);
        feed(parser, join(n, n, n));
        return parser;
    }

    /** Test-only fixture: PAT (TSID 7/program 1), PMT PID 0x100, AVC PID 0x101. */
    public static byte[] patPmtFixture() {
        return join(psi(0, 0, pat(1, PMT)), psi(PMT, 0, pmt(1, new byte[0])),
                packet(VIDEO, 0, true, new byte[] {0, 0, 1, (byte) 0xe0}));
    }

    @Test public void reusableFixtureProvidesStructureWithoutDiscardedOrNullBytes() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] fixture = patPmtFixture();
        assertNotSame(fixture, patPmtFixture());
        feed(parser, fixture);
        Tbs5530TsParser.Snapshot snapshot = parser.snapshot();
        assertTrue(snapshot.hasValidProgramStructure());
        assertEquals(3, snapshot.packets);
        assertEquals(1, snapshot.validPatSections);
        assertEquals(1, snapshot.validPmtSections);
        assertEquals(0, snapshot.discardedBytes);
        assertEquals(0, snapshot.bufferedBytes);
        assertEquals(0, snapshot.nullPackets);
        assertEquals(0, snapshot.transportErrorPackets);
        assertEquals(0, snapshot.continuityErrors);
        assertEquals(0, snapshot.malformedPackets);
        assertEquals(0, snapshot.invalidPesHeaders);
        conserved(parser);
    }

    private static Tbs5530TsParser withProgram() {
        Tbs5530TsParser parser = aligned();
        feed(parser, psi(0, 0, pat(1, PMT)));
        feed(parser, psi(PMT, 0, pmt(1, new byte[0])));
        return parser;
    }

    private static void conserved(Tbs5530TsParser parser) {
        Tbs5530TsParser.Snapshot s = parser.snapshot();
        assertEquals(s.bytesReceived, s.discardedBytes + 188 * s.packets + s.bufferedBytes);
    }

    @Test public void crcHasIndependentMpegCheckVector() throws Exception {
        assertEquals(0x0376e6e7, crc("123456789".getBytes("US-ASCII"), 9));
    }

    @Test public void alignmentIsNotStructureAndPatPmtMustMatch() {
        Tbs5530TsParser parser = aligned();
        assertFalse(parser.snapshot().hasValidProgramStructure());
        feed(parser, psi(PMT, 0, pmt(1, new byte[0])));
        assertEquals(0, parser.snapshot().validPmtSections);
        feed(parser, psi(0, 0, pat(1, PMT)));
        assertFalse(parser.snapshot().hasValidProgramStructure());
        feed(parser, psi(PMT, 1, pmt(2, new byte[0])));
        assertFalse(parser.snapshot().hasValidProgramStructure());
        feed(parser, psi(PMT, 2, pmt(1, new byte[0])));
        Tbs5530TsParser.Snapshot s = parser.snapshot();
        assertTrue(s.hasValidProgramStructure());
        assertEquals(7, s.transportStreamId);
        assertEquals(1, s.programCount);
        assertEquals(1, s.programsWithPmt);
        assertEquals(1, s.programNumber);
        assertEquals(PMT, s.pmtPid);
        assertEquals(VIDEO, s.pcrPid);
        assertEquals(1, s.elementaryPidCount);
    }

    @Test public void everyByteSplitReassemblesPsiAcrossTsAndInputChunks() throws Exception {
        byte[] descriptors = new byte[304];
        descriptors[1] = (byte) 200;
        descriptors[203] = 100;
        byte[] n = packet(0x1fff, 0, false, new byte[184]);
        byte[] stream = join(n, n, n, psi(0, 0, pat(1, PMT)),
                psi(PMT, 0, pmt(1, descriptors)), new byte[] {0x47, 0});
        Tbs5530TsParser whole = new Tbs5530TsParser();
        feed(whole, stream);
        assertTrue(whole.snapshot().hasValidProgramStructure());
        for (int split = 0; split <= stream.length; split++) {
            Tbs5530TsParser parser = new Tbs5530TsParser();
            parser.accept(stream, 0, split);
            parser.accept(stream, split, stream.length - split);
            for (Field field : Tbs5530TsParser.Snapshot.class.getFields()) {
                assertEquals(field.getName() + " split=" + split,
                        field.get(whole.snapshot()), field.get(parser.snapshot()));
            }
            assertTrue(parser.snapshot().hasValidProgramStructure());
            conserved(parser);
        }
    }

    @Test public void byteAtATimeAcceptsAllPsiAndRetainsTrailingPartialPacket() {
        byte[] n = packet(0x1fff, 0, false, new byte[184]);
        byte[] stream = join(n, n, n, psi(0, 0, pat(1, PMT)),
                psi(PMT, 0, pmt(1, new byte[0])), new byte[37]);
        Tbs5530TsParser parser = new Tbs5530TsParser();
        for (int i = 0; i < stream.length; i++) parser.accept(stream, i, 1);
        assertTrue(parser.snapshot().hasValidProgramStructure());
        assertEquals(37, parser.snapshot().bufferedBytes);
        conserved(parser);
    }

    @Test public void pointerCompletesPreviousAndStartsNextSectionIncludingStuffing() {
        Tbs5530TsParser parser = aligned();
        byte[] p = pat(1, PMT);
        feed(parser, packet(0, 0, true, join(new byte[] {0}, Arrays.copyOf(p, 9))));
        feed(parser, packet(0, 1, true, join(new byte[] {7}, Arrays.copyOfRange(p, 9, 16),
                p, new byte[] {(byte) 0xff, (byte) 0xff})));
        assertEquals(2, parser.snapshot().validPatSections);
        assertEquals(0, parser.snapshot().invalidPsiSections);
    }

    @Test public void pointerOutOfBoundsAndIncompleteTailAreRejected() {
        Tbs5530TsParser parser = aligned();
        feed(parser, packet(0, 0, true, new byte[] {9, 0, 0}));
        byte[] p = pat(1, PMT);
        feed(parser, packet(0, 1, true, join(new byte[] {0}, Arrays.copyOf(p, 9))));
        feed(parser, packet(0, 2, true, join(new byte[] {0}, p)));
        assertEquals(2, parser.snapshot().invalidPsiSections);
        assertEquals(1, parser.snapshot().validPatSections);
    }

    @Test public void crcAndSectionBoundsRejectPatAndPmt() {
        Tbs5530TsParser parser = aligned();
        byte[] bad = pat(1, PMT);
        bad[12] ^= 1;
        feed(parser, psi(0, 0, bad));
        assertEquals(1, parser.snapshot().psiCrcErrors);
        assertEquals(0, parser.snapshot().programCount);
        feed(parser, packet(0, 1, true, new byte[] {0, 0, (byte) 0xbf, (byte) 0xff}));
        assertEquals(1, parser.snapshot().invalidPsiSections);
        feed(parser, psi(0, 2, pat(1, PMT)));
        byte[] malformed = pmt(1, new byte[0]);
        malformed[11] = 100;
        feed(parser, psi(PMT, 0, checksum(malformed)));
        assertFalse(parser.snapshot().hasValidProgramStructure());
        assertEquals(2, parser.snapshot().invalidPsiSections);
        byte[] crcBadPmt = pmt(1, new byte[0]);
        crcBadPmt[crcBadPmt.length - 1] ^= 1;
        feed(parser, psi(PMT, 1, crcBadPmt));
        assertEquals(2, parser.snapshot().psiCrcErrors);
    }

    @Test public void teiAndScrambledPacketsNeverBecomePsiEvidence() {
        for (boolean tei : new boolean[] {false, true}) {
            Tbs5530TsParser parser = aligned();
            byte[] bytes = psi(0, 0, pat(1, PMT));
            if (tei) bytes[1] |= (byte) 0x80; else bytes[3] |= (byte) 0x80;
            feed(parser, bytes);
            assertEquals(0, parser.snapshot().validPatSections);
            assertFalse(parser.snapshot().hasValidProgramStructure());
            assertEquals(tei ? 1 : 0, parser.snapshot().transportErrorPackets);
            assertEquals(tei ? 0 : 1, parser.snapshot().scrambledPackets);
        }
    }

    @Test public void teiOrCounterGapAbandonsPartialPsiWithoutInventingValidSections() {
        for (boolean tei : new boolean[] {false, true}) {
            Tbs5530TsParser parser = aligned();
            byte[] p = pat(1, PMT);
            feed(parser, packet(0, 0, true, join(new byte[] {0}, Arrays.copyOf(p, 9))));
            byte[] tail = packet(0, tei ? 1 : 3, false, Arrays.copyOfRange(p, 9, 16));
            if (tei) tail[1] |= (byte) 0x80;
            feed(parser, tail);
            assertEquals(0, parser.snapshot().validPatSections);
            assertEquals(tei ? 0 : 1, parser.snapshot().continuityErrors);
            assertEquals(tei ? 1 : 0, parser.snapshot().transportErrorPackets);
        }
    }

    @Test public void duplicatePsiIsNotAssembledTwiceButChangedPayloadIsAnError() {
        Tbs5530TsParser parser = aligned();
        byte[] bytes = psi(0, 0, pat(1, PMT));
        feed(parser, bytes);
        feed(parser, bytes);
        assertEquals(1, parser.snapshot().duplicatePackets);
        assertEquals(1, parser.snapshot().validPatSections);
        byte[] changed = bytes.clone();
        changed[187] ^= 1;
        feed(parser, changed);
        assertEquals(1, parser.snapshot().continuityErrors);
        assertEquals(1, parser.snapshot().psiCrcErrors);
    }

    @Test public void completeCurrentMultiSectionPatIsRequiredAndNetworkPidIsNotProgram() {
        Tbs5530TsParser parser = aligned();
        byte[] first = section(0, 7, 0, 1, new byte[] {0, 0, (byte) 0xe0, 0x10});
        byte[] second = section(0, 7, 1, 1, new byte[] {0, 1, (byte) 0xe1, 0});
        feed(parser, psi(0, 0, first));
        assertEquals(0, parser.snapshot().programCount);
        feed(parser, psi(PMT, 0, pmt(1, new byte[0])));
        assertFalse(parser.snapshot().hasValidProgramStructure());
        feed(parser, psi(0, 1, second));
        assertEquals(1, parser.snapshot().programCount);
        feed(parser, psi(PMT, 1, pmt(1, new byte[0])));
        assertTrue(parser.snapshot().hasValidProgramStructure());
    }

    @Test public void nextTablesAndInvalidSectionNumbersAreNotEvidence() {
        Tbs5530TsParser parser = aligned();
        byte[] next = pat(1, PMT);
        next[5] &= (byte) 0xfe;
        feed(parser, psi(0, 0, checksum(next)));
        assertEquals(0, parser.snapshot().validPatSections);
        next[5] |= 1;
        next[6] = 1;
        feed(parser, psi(0, 1, checksum(next)));
        assertEquals(1, parser.snapshot().invalidPsiSections);
        assertEquals(0, parser.snapshot().programCount);
    }

    @Test public void newPatRevokesOldPmtAndSyncLossRevokesAllStructure() {
        Tbs5530TsParser parser = withProgram();
        assertTrue(parser.snapshot().hasValidProgramStructure());
        byte[] changed = pat(2, 0x200);
        changed[5] += 2;
        feed(parser, psi(0, 1, checksum(changed)));
        assertFalse(parser.snapshot().hasValidProgramStructure());
        feed(parser, psi(0x200, 0, pmt(2, new byte[0])));
        assertTrue(parser.snapshot().hasValidProgramStructure());
        feed(parser, new byte[188]);
        assertFalse(parser.snapshot().hasValidProgramStructure());
        assertEquals(-1, parser.snapshot().transportStreamId);
        conserved(parser);
    }

    @Test public void sdtRequiresCrcBoundsAndMatchingTransportStream() {
        Tbs5530TsParser parser = withProgram();
        byte[] body = {0, 1, (byte) 0xff, 0, 1, (byte) 0xfc, (byte) 0x80, 0};
        feed(parser, psi(0x11, 0, section(0x42, 8, 0, 0, body)));
        assertEquals(0, parser.snapshot().serviceCount);
        feed(parser, psi(0x11, 1, section(0x42, 7, 0, 0, body)));
        assertEquals(1, parser.snapshot().serviceCount);
        assertEquals(1, parser.snapshot().validSdtSections);
    }

    @Test public void pesPrefixValidationIsLimitedToKnownAudioVideoPidsAndSpansPackets() {
        Tbs5530TsParser parser = withProgram();
        feed(parser, packet(VIDEO, 0, true, new byte[] {0}));
        feed(parser, packet(VIDEO, 1, false, new byte[] {0, 1, (byte) 0xe0}));
        assertEquals(0, parser.snapshot().invalidPesHeaders);
        feed(parser, packet(VIDEO, 2, true, new byte[] {0, 0, 2}));
        feed(parser, packet(0x321, 0, true, new byte[] {1, 2, 3}));
        assertEquals(1, parser.snapshot().invalidPesHeaders);
        assertEquals(2, parser.snapshot().pesStarts);
    }

    @Test public void adaptationOnlyPreservesPartialPsiButDiscontinuityRevokesIt() {
        for (boolean discontinuity : new boolean[] {false, true}) {
            Tbs5530TsParser parser = aligned();
            byte[] p = pat(1, PMT);
            feed(parser, packet(0, 0, true, join(new byte[] {0}, Arrays.copyOf(p, 9))));
            byte[] only = new byte[188];
            Arrays.fill(only, (byte) 0xff);
            only[0] = 0x47; only[1] = 0; only[2] = 0; only[3] = 0x20;
            only[4] = (byte) 183; only[5] = (byte) (discontinuity ? 0x80 : 0);
            feed(parser, only);
            feed(parser, packet(0, 1, false, Arrays.copyOfRange(p, 9, 16)));
            assertEquals(discontinuity ? 0 : 1, parser.snapshot().validPatSections);
            assertEquals(discontinuity ? 1 : 0, parser.snapshot().expectedDiscontinuities);
            assertEquals(0, parser.snapshot().continuityErrors);
        }
    }

    @Test public void pmtDescriptorsAndEmptyProgramCannotManufactureStructure() {
        Tbs5530TsParser parser = aligned();
        feed(parser, psi(0, 0, pat(1, PMT)));
        byte[] empty = section(2, 1, 0, 0,
                new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xf0, 0});
        feed(parser, psi(PMT, 0, empty));
        assertEquals(1, parser.snapshot().validPmtSections);
        assertFalse(parser.snapshot().hasValidProgramStructure());
        byte[] invalidDescriptor = pmt(1, new byte[] {1, 10});
        feed(parser, psi(PMT, 1, invalidDescriptor));
        assertEquals(1, parser.snapshot().invalidPsiSections);
        assertFalse(parser.snapshot().hasValidProgramStructure());
    }

    @Test public void reservedScramblingControlIsMalformedAndNeverUsedAsPsi() {
        Tbs5530TsParser parser = aligned();
        byte[] bytes = psi(0, 0, pat(1, PMT));
        bytes[3] |= 0x40;
        feed(parser, bytes);
        assertEquals(1, parser.snapshot().malformedPackets);
        assertEquals(0, parser.snapshot().validPatSections);
    }

    @Test public void errorCountersRemainVisibleAfterStructureEvidenceAndSnapshotsStayDetached() {
        Tbs5530TsParser parser = withProgram();
        Tbs5530TsParser.Snapshot before = parser.snapshot();
        byte[] tei = packet(VIDEO, 0, true, new byte[] {0, 0, 1});
        tei[1] |= (byte) 0x80;
        feed(parser, tei);
        feed(parser, packet(VIDEO, 4, false, new byte[] {0}));
        feed(parser, packet(VIDEO, 7, false, new byte[] {0}));
        assertTrue(parser.snapshot().hasValidProgramStructure());
        assertEquals(1, parser.snapshot().transportErrorPackets);
        assertEquals(1, parser.snapshot().continuityErrors);
        assertEquals(0, before.transportErrorPackets);
        assertEquals(0, before.continuityErrors);
    }
}
