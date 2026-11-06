// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class Tbs5530TsParserTest {
    private static final int SIZE = Tbs5530TsParser.PACKET_SIZE;
    private static final int PID = 0x100;

    private static byte[] packet(int pid, int cc) {
        byte[] result = new byte[SIZE];
        Arrays.fill(result, (byte) 0x55);
        result[0] = 0x47;
        result[1] = (byte) (pid >>> 8);
        result[2] = (byte) pid;
        result[3] = (byte) (0x10 | cc);
        return result;
    }

    private static byte[] adaptation(int pid, int cc, boolean payload, int length, int flags) {
        byte[] result = packet(pid, cc);
        result[3] = (byte) ((payload ? 0x30 : 0x20) | cc);
        result[4] = (byte) length;
        if (length > 0) result[5] = (byte) flags;
        return result;
    }

    private static byte[] sequence(int pid, int firstCc, int count) {
        byte[] result = new byte[count * SIZE];
        for (int i = 0; i < count; i++) {
            System.arraycopy(packet(pid, (firstCc + i) & 15), 0, result, i * SIZE, SIZE);
        }
        return result;
    }

    private static byte[] garbage(int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 0x13);
        return result;
    }

    private static byte[] join(byte[]... chunks) {
        int length = 0;
        for (byte[] chunk : chunks) length += chunk.length;
        byte[] result = new byte[length];
        int cursor = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, cursor, chunk.length);
            cursor += chunk.length;
        }
        return result;
    }

    private static void feed(Tbs5530TsParser parser, byte[] bytes) {
        parser.accept(bytes, 0, bytes.length);
    }

    private static Tbs5530TsParser acquired() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        feed(parser, sequence(PID, 0, 3));
        return parser;
    }

    private static void sameSnapshot(Tbs5530TsParser.Snapshot expected,
                                     Tbs5530TsParser.Snapshot actual) {
        assertEquals(expected.bytesReceived, actual.bytesReceived);
        assertEquals(expected.discardedBytes, actual.discardedBytes);
        assertEquals(expected.packets, actual.packets);
        assertEquals(expected.syncLosses, actual.syncLosses);
        assertEquals(expected.malformedPackets, actual.malformedPackets);
        assertEquals(expected.transportErrorPackets, actual.transportErrorPackets);
        assertEquals(expected.nullPackets, actual.nullPackets);
        assertEquals(expected.continuityErrors, actual.continuityErrors);
        assertEquals(expected.expectedDiscontinuities, actual.expectedDiscontinuities);
        assertEquals(expected.duplicatePackets, actual.duplicatePackets);
        assertEquals(expected.bufferedBytes, actual.bufferedBytes);
        assertEquals(expected.synchronizedToStream, actual.synchronizedToStream);
    }

    private static void conserved(Tbs5530TsParser parser) {
        Tbs5530TsParser.Snapshot snapshot = parser.snapshot();
        assertEquals(snapshot.bytesReceived,
                snapshot.discardedBytes + SIZE * snapshot.packets + snapshot.bufferedBytes);
        assertTrue(snapshot.bufferedBytes >= 0);
        assertTrue(snapshot.bufferedBytes < Tbs5530TsParser.MAX_BUFFERED_BYTES);
    }

    @Test public void requiresThreeCompletePacketsBeforeCountingAny() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] stream = sequence(PID, 0, 3);
        parser.accept(stream, 0, 2 * SIZE + 1);
        assertEquals(0, parser.snapshot().packets);
        assertFalse(parser.snapshot().synchronizedToStream);
        parser.accept(stream, 2 * SIZE + 1, SIZE - 2);
        assertEquals(0, parser.snapshot().packets);
        parser.accept(stream, stream.length - 1, 1);
        assertEquals(3, parser.snapshot().packets);
        assertTrue(parser.snapshot().synchronizedToStream);
        assertEquals(0, parser.snapshot().bufferedBytes);
        conserved(parser);
    }

    @Test public void everyTwoChunkSplitHasIdenticalResultsIncludingResyncAndTrailingCarry() {
        byte[] stream = join(garbage(19), sequence(PID, 0, 6), garbage(7),
                sequence(PID, 10, 4), Arrays.copyOf(packet(PID, 14), 67));
        Tbs5530TsParser whole = new Tbs5530TsParser();
        feed(whole, stream);
        Tbs5530TsParser.Snapshot expected = whole.snapshot();
        assertEquals(10, expected.packets);
        assertEquals(26, expected.discardedBytes);
        assertEquals(1, expected.syncLosses);
        assertEquals(0, expected.continuityErrors);
        for (int split = 0; split <= stream.length; split++) {
            Tbs5530TsParser parser = new Tbs5530TsParser();
            parser.accept(stream, 0, split);
            parser.accept(stream, split, stream.length - split);
            sameSnapshot(expected, parser.snapshot());
            conserved(parser);
        }
    }

    @Test public void arbitraryChunkSizesAndPaddedOffsetsAreEquivalent() {
        byte[] stream = join(garbage(23), sequence(PID, 14, 35), garbage(277),
                sequence(0x1ffe, 2, 21));
        Tbs5530TsParser whole = new Tbs5530TsParser();
        feed(whole, stream);
        for (int size : new int[] {1, 2, 3, 7, 187, 188, 189, 375, 376, 511, 564, 4096}) {
            Tbs5530TsParser parser = new Tbs5530TsParser();
            for (int offset = 0; offset < stream.length; offset += size) {
                int length = Math.min(size, stream.length - offset);
                byte[] padded = new byte[length + 8];
                Arrays.fill(padded, (byte) 0x47);
                System.arraycopy(stream, offset, padded, 3, length);
                parser.accept(padded, 3, length);
                conserved(parser);
            }
            sameSnapshot(whole.snapshot(), parser.snapshot());
        }
    }

    @Test public void deterministicRandomChunksPreserveMixedPacketCounters() {
        byte[] error = packet(PID, 9);
        error[1] |= (byte) 0x80;
        byte[] malformed = packet(PID, 0);
        malformed[3] = 0;
        byte[] stream = join(sequence(PID, 14, 3), packet(PID, 0),
                adaptation(PID, 0, false, 183, 0), packet(PID, 1), error, malformed,
                packet(0x1fff, 5), garbage(88), sequence(PID, 7, 4));
        Tbs5530TsParser whole = new Tbs5530TsParser();
        feed(whole, stream);
        Random random = new Random(5530);
        for (int run = 0; run < 32; run++) {
            Tbs5530TsParser parser = new Tbs5530TsParser();
            int cursor = 0;
            while (cursor < stream.length) {
                int length = Math.min(1 + random.nextInt(700), stream.length - cursor);
                parser.accept(stream, cursor, length);
                cursor += length;
                conserved(parser);
            }
            sameSnapshot(whole.snapshot(), parser.snapshot());
        }
    }

    @Test public void isolatedSyncBytesAndTwoValidPacketsDoNotAcquire() {
        for (int count = 1; count <= 2; count++) {
            Tbs5530TsParser parser = new Tbs5530TsParser();
            feed(parser, join(garbage(11), sequence(PID, 0, count), garbage(600)));
            assertEquals(0, parser.snapshot().packets);
            assertEquals(0, parser.snapshot().syncLosses);
            assertFalse(parser.snapshot().synchronizedToStream);
            conserved(parser);
        }
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] isolated = garbage(1200);
        isolated[0] = 0x47;
        isolated[187] = 0x47;
        isolated[376] = 0x47;
        feed(parser, isolated);
        assertEquals(0, parser.snapshot().packets);
    }

    @Test public void threeSyncBytesWithInvalidHeadersDoNotAcquire() {
        byte[] fake = new byte[3 * SIZE];
        fake[0] = fake[SIZE] = fake[2 * SIZE] = 0x47;
        Tbs5530TsParser parser = new Tbs5530TsParser();
        feed(parser, fake);
        assertEquals(0, parser.snapshot().packets);
        assertFalse(parser.snapshot().synchronizedToStream);
        feed(parser, sequence(PID, 0, 3));
        assertEquals(3, parser.snapshot().packets);
        assertEquals(fake.length, parser.snapshot().discardedBytes);
        assertEquals(0, parser.snapshot().malformedPackets);
        conserved(parser);
    }

    @Test public void acquisitionRejectsStructurallyInvalidThirdPacket() {
        byte[] bad = adaptation(PID, 2, true, 183, 0);
        Tbs5530TsParser parser = new Tbs5530TsParser();
        feed(parser, join(sequence(PID, 0, 2), bad));
        assertEquals(0, parser.snapshot().packets);
        feed(parser, sequence(PID, 5, 3));
        assertEquals(3, parser.snapshot().packets);
        assertEquals(3 * SIZE, parser.snapshot().discardedBytes);
        assertEquals(0, parser.snapshot().continuityErrors);
    }

    @Test public void garbageCausesOneLossEpisodeAndRebaselinesAllPids() {
        Tbs5530TsParser parser = acquired();
        feed(parser, garbage(3 * SIZE + 29));
        assertEquals(1, parser.snapshot().syncLosses);
        assertFalse(parser.snapshot().synchronizedToStream);
        feed(parser, sequence(PID, 9, 3));
        Tbs5530TsParser.Snapshot snapshot = parser.snapshot();
        assertEquals(6, snapshot.packets);
        assertEquals(1, snapshot.syncLosses);
        assertEquals(3 * SIZE + 29, snapshot.discardedBytes);
        assertEquals(0, snapshot.continuityErrors);
        conserved(parser);
    }

    @Test public void oneMissingSyncByteIsRecoveredWithoutTreatingTailAsPacket() {
        Tbs5530TsParser parser = acquired();
        byte[] missingSync = Arrays.copyOfRange(packet(PID, 3), 1, SIZE);
        feed(parser, join(missingSync, sequence(PID, 4, 3)));
        assertEquals(6, parser.snapshot().packets);
        assertEquals(SIZE - 1, parser.snapshot().discardedBytes);
        assertEquals(1, parser.snapshot().syncLosses);
        assertEquals(0, parser.snapshot().malformedPackets);
        assertEquals(0, parser.snapshot().continuityErrors);
        conserved(parser);
    }

    @Test public void incompleteFinalPacketIsNotCountedAndCanBeCompletedLater() {
        Tbs5530TsParser parser = acquired();
        byte[] next = packet(PID, 3);
        parser.accept(next, 0, SIZE - 1);
        assertEquals(3, parser.snapshot().packets);
        assertEquals(SIZE - 1, parser.snapshot().bufferedBytes);
        parser.accept(next, SIZE - 1, 1);
        assertEquals(4, parser.snapshot().packets);
        assertEquals(0, parser.snapshot().bufferedBytes);
        conserved(parser);
    }

    @Test public void largeGarbageInputKeepsBoundedCarryAndDoesNotInventPackets() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] input = garbage(2 * 1024 * 1024);
        feed(parser, input);
        feed(parser, input);
        Tbs5530TsParser.Snapshot snapshot = parser.snapshot();
        assertEquals(2L * input.length, snapshot.bytesReceived);
        assertEquals(0, snapshot.packets);
        assertEquals(0, snapshot.syncLosses);
        assertEquals(Tbs5530TsParser.MAX_BUFFERED_BYTES - 1, snapshot.bufferedBytes);
        conserved(parser);
        feed(parser, sequence(PID, 0, 3));
        assertEquals(3, parser.snapshot().packets);
        assertEquals(2L * input.length, parser.snapshot().discardedBytes);
        conserved(parser);
    }

    @Test public void payloadContinuityWrapsModuloSixteen() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        feed(parser, sequence(PID, 14, 40));
        assertEquals(40, parser.snapshot().packets);
        assertEquals(0, parser.snapshot().continuityErrors);
    }

    @Test public void missingPayloadCountsOneEventAndRebaselinesInsteadOfCascading() {
        Tbs5530TsParser parser = acquired();
        feed(parser, sequence(PID, 5, 4));
        assertEquals(1, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().duplicatePackets);
    }

    @Test public void interleavedPidsHaveIndependentContinuityIncludingBoundaryPids() {
        Tbs5530TsParser parser = acquired();
        for (int cc = 0; cc < 32; cc++) {
            feed(parser, packet(0, cc & 15));
            feed(parser, packet(0x1ffe, (cc + 7) & 15));
        }
        feed(parser, packet(PID, 3));
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(68, parser.snapshot().packets);
    }

    @Test public void allNonNullPidsCanRetainExactDuplicateBaselinesWithoutEviction() {
        Tbs5530TsParser parser = acquired();
        for (int pid = 0; pid < 0x1fff; pid++) {
            feed(parser, packet(pid, pid == PID ? 3 : 0));
        }
        for (int pid = 0; pid < 0x1fff; pid++) {
            feed(parser, packet(pid, pid == PID ? 3 : 0));
        }
        assertEquals(8191, parser.snapshot().duplicatePackets);
        assertEquals(0, parser.snapshot().continuityErrors);
        for (int pid = 0; pid < 0x1fff; pid++) {
            feed(parser, packet(pid, pid == PID ? 4 : 1));
        }
        assertEquals(0, parser.snapshot().continuityErrors);
        conserved(parser);
    }

    @Test public void adaptationOnlyDoesNotIncrementCounterOrReplacePayloadBaseline() {
        Tbs5530TsParser parser = acquired();
        feed(parser, adaptation(PID, 2, false, 183, 0));
        feed(parser, adaptation(PID, 2, false, 183, 0));
        feed(parser, packet(PID, 2));
        feed(parser, packet(PID, 3));
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(1, parser.snapshot().duplicatePackets);
    }

    @Test public void adaptationOnlyIncrementIsAnErrorButFollowingPayloadUsesItsCounter() {
        Tbs5530TsParser parser = acquired();
        feed(parser, adaptation(PID, 3, false, 183, 0));
        feed(parser, packet(PID, 4));
        assertEquals(1, parser.snapshot().continuityErrors);
    }

    @Test public void firstAdaptationOnlyPacketEstablishesCounterWithoutPretendingPayload() {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        byte[] only = adaptation(PID, 7, false, 183, 0);
        feed(parser, join(only, only, only, packet(PID, 8)));
        assertEquals(4, parser.snapshot().packets);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().duplicatePackets);
    }

    @Test public void discontinuityInPayloadRebaselinesItsPidWithoutAContinuityError() {
        Tbs5530TsParser parser = acquired();
        feed(parser, adaptation(PID, 11, true, 1, 0x80));
        feed(parser, packet(PID, 12));
        assertEquals(1, parser.snapshot().expectedDiscontinuities);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().duplicatePackets);
    }

    @Test public void adaptationOnlyDiscontinuityRebaselinesToFlaggedCounter() {
        Tbs5530TsParser parser = acquired();
        feed(parser, adaptation(PID, 8, false, 183, 0x80));
        feed(parser, adaptation(PID, 8, false, 183, 0));
        feed(parser, packet(PID, 9));
        assertEquals(1, parser.snapshot().expectedDiscontinuities);
        assertEquals(0, parser.snapshot().continuityErrors);
        feed(parser, packet(PID, 13));
        assertEquals(1, parser.snapshot().continuityErrors);
    }

    @Test public void discontinuityDoesNotResetAnotherPid() {
        Tbs5530TsParser parser = acquired();
        feed(parser, packet(0x101, 4));
        feed(parser, adaptation(PID, 8, true, 1, 0x80));
        feed(parser, packet(0x101, 6));
        assertEquals(1, parser.snapshot().continuityErrors);
        assertEquals(1, parser.snapshot().expectedDiscontinuities);
    }

    @Test public void teiIsCountedButNotTrustedForContinuityOrDiscontinuity() {
        Tbs5530TsParser parser = acquired();
        byte[] error = adaptation(PID, 11, true, 1, 0x80);
        error[1] |= (byte) 0x80;
        feed(parser, error);
        feed(parser, sequence(PID, 6, 3));
        assertEquals(1, parser.snapshot().transportErrorPackets);
        assertEquals(0, parser.snapshot().expectedDiscontinuities);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(7, parser.snapshot().packets);
    }

    @Test public void nullPidIsExemptFromContinuityAndDuplicateChecks() {
        Tbs5530TsParser parser = acquired();
        for (int cc : new int[] {4, 4, 4, 0, 12, 3, 15, 0}) feed(parser, packet(0x1fff, cc));
        feed(parser, adaptation(0x1fff, 9, false, 183, 0x80));
        byte[] error = packet(0x1fff, 12);
        error[1] |= (byte) 0x80;
        feed(parser, error);
        assertEquals(10, parser.snapshot().nullPackets);
        assertEquals(1, parser.snapshot().transportErrorPackets);
        assertEquals(1, parser.snapshot().expectedDiscontinuities);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().duplicatePackets);
    }

    @Test public void oneExactPayloadRetransmissionIsAllowedButFurtherRepeatsAreErrors() {
        Tbs5530TsParser parser = acquired();
        feed(parser, packet(PID, 2));
        assertEquals(1, parser.snapshot().duplicatePackets);
        assertEquals(0, parser.snapshot().continuityErrors);
        feed(parser, packet(PID, 2));
        feed(parser, packet(PID, 2));
        assertEquals(2, parser.snapshot().continuityErrors);
        feed(parser, packet(PID, 3));
        feed(parser, packet(PID, 3));
        assertEquals(2, parser.snapshot().duplicatePackets);
        assertEquals(2, parser.snapshot().continuityErrors);
    }

    @Test public void sameCounterWithDifferentPayloadOrHeaderIsNotALegalDuplicate() {
        for (int index : new int[] {1, 3, 4, 187}) {
            Tbs5530TsParser parser = acquired();
            byte[] changed = packet(PID, 2);
            changed[index] ^= (byte) (index == 1 ? 0x40 : index == 3 ? 0x80 : 1);
            feed(parser, changed);
            feed(parser, packet(PID, 3));
            assertEquals(1, parser.snapshot().continuityErrors);
            assertEquals(0, parser.snapshot().duplicatePackets);
        }
    }

    @Test public void legalDuplicateMayUpdatePcrButNotOtherAdaptationOrPayloadBytes() {
        byte[] pcr = adaptation(PID, 3, true, 7, 0x10);
        for (int changedIndex : new int[] {6, 7, 8, 9, 10, 11, 12, 187}) {
            Tbs5530TsParser parser = acquired();
            feed(parser, pcr);
            byte[] duplicate = pcr.clone();
            duplicate[changedIndex] ^= 1;
            feed(parser, duplicate);
            boolean legal = changedIndex < 12;
            assertEquals(legal ? 1 : 0, parser.snapshot().duplicatePackets);
            assertEquals(legal ? 0 : 1, parser.snapshot().continuityErrors);
        }
    }

    @Test public void malformedAfcAndAdaptationLengthsAreCountedWithoutLosingAlignment() {
        int malformedCount = 0;
        Tbs5530TsParser parser = acquired();
        for (int afc : new int[] {0, 2, 3}) {
            for (int length : new int[] {0, 1, 2, 181, 182, 183, 184, 255}) {
                byte[] candidate = adaptation(PID, 3, afc == 3, length, 0);
                candidate[3] = (byte) (afc << 4 | 3);
                feed(parser, candidate);
                boolean valid = (afc == 2 && length == 183) || (afc == 3 && length <= 182);
                if (!valid) malformedCount++;
                feed(parser, packet(PID, 4));
            }
        }
        assertEquals(malformedCount, parser.snapshot().malformedPackets);
        assertEquals(0, parser.snapshot().syncLosses);
        assertEquals(51, parser.snapshot().packets);
        conserved(parser);
    }

    @Test public void adaptationLengthEdgesWithAndWithoutPayloadAreLegal() {
        Tbs5530TsParser parser = acquired();
        feed(parser, adaptation(PID, 3, true, 0, 0));
        feed(parser, adaptation(PID, 4, true, 182, 0));
        feed(parser, adaptation(PID, 4, false, 183, 0));
        feed(parser, packet(PID, 5));
        assertEquals(0, parser.snapshot().malformedPackets);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().expectedDiscontinuities);
    }

    @Test public void optionalAdaptationFieldsMustFitInsideDeclaredLength() {
        for (int[] shape : new int[][] {{1, 0x10}, {6, 0x10}, {6, 0x08}, {1, 0x04},
                {1, 0x02}, {1, 0x01}, {12, 0x18}}) {
            Tbs5530TsParser parser = acquired();
            feed(parser, adaptation(PID, 3, true, shape[0], shape[1]));
            assertEquals(1, parser.snapshot().malformedPackets);
            assertEquals(0, parser.snapshot().syncLosses);
        }
        for (int flag : new int[] {0x02, 0x01}) {
            Tbs5530TsParser parser = acquired();
            byte[] bad = adaptation(PID, 3, true, 4, flag);
            bad[6] = 4;
            feed(parser, bad);
            assertEquals(1, parser.snapshot().malformedPackets);
        }
        for (int extensionFlag : new int[] {0x80, 0x40, 0x20, 0xe0}) {
            Tbs5530TsParser parser = acquired();
            byte[] bad = adaptation(PID, 3, true, 3, 1);
            bad[6] = 1;
            bad[7] = (byte) extensionFlag;
            feed(parser, bad);
            assertEquals(1, parser.snapshot().malformedPackets);
        }
    }

    @Test public void validOptionalFieldsIncludingPrivateDataAndExtensionAreAccepted() {
        Tbs5530TsParser parser = acquired();
        byte[] rich = adaptation(PID, 3, true, 60, 0x1f);
        rich[19] = 2;
        rich[22] = 11;
        rich[23] = (byte) 0xe0;
        feed(parser, rich);
        byte[] emptyPrivate = adaptation(PID, 4, true, 2, 0x02);
        emptyPrivate[6] = 0;
        byte[] emptyExtension = adaptation(PID, 5, true, 2, 0x01);
        emptyExtension[6] = 0;
        feed(parser, emptyPrivate);
        feed(parser, emptyExtension);
        assertEquals(0, parser.snapshot().malformedPackets);
        assertEquals(0, parser.snapshot().continuityErrors);
    }

    @Test public void malformedTeiAndNullCategoriesCanOverlapWithoutContinuityNoise() {
        Tbs5530TsParser parser = acquired();
        byte[] bad = adaptation(0x1fff, 7, true, 255, 0x80);
        bad[1] |= (byte) 0x80;
        feed(parser, bad);
        assertEquals(4, parser.snapshot().packets);
        assertEquals(1, parser.snapshot().malformedPackets);
        assertEquals(1, parser.snapshot().transportErrorPackets);
        assertEquals(1, parser.snapshot().nullPackets);
        assertEquals(0, parser.snapshot().expectedDiscontinuities);
        assertEquals(0, parser.snapshot().continuityErrors);
        assertEquals(0, parser.snapshot().syncLosses);
    }

    @Test public void malformedPacketClearsOnlyItsOwnPidBaseline() {
        Tbs5530TsParser parser = acquired();
        feed(parser, packet(0x101, 2));
        feed(parser, adaptation(PID, 3, true, 255, 0));
        feed(parser, packet(PID, 9));
        feed(parser, packet(0x101, 4));
        assertEquals(1, parser.snapshot().malformedPackets);
        assertEquals(1, parser.snapshot().continuityErrors);
    }

    @Test public void inputArraysAreCopiedForPartialCarryAndDuplicateComparisons() {
        byte[] stream = sequence(PID, 0, 3);
        Tbs5530TsParser parser = new Tbs5530TsParser();
        parser.accept(stream, 0, stream.length - 1);
        byte last = stream[stream.length - 1];
        Arrays.fill(stream, (byte) 0);
        feed(parser, new byte[] {last});
        assertEquals(3, parser.snapshot().packets);
        byte[] next = packet(PID, 3);
        feed(parser, next);
        Arrays.fill(next, (byte) 0);
        feed(parser, packet(PID, 3));
        assertEquals(1, parser.snapshot().duplicatePackets);
        assertEquals(0, parser.snapshot().continuityErrors);
    }

    @Test public void snapshotsAreImmutableScalarCopiesNotLiveViews() {
        Tbs5530TsParser parser = acquired();
        Tbs5530TsParser.Snapshot old = parser.snapshot();
        feed(parser, packet(PID, 3));
        assertEquals(3, old.packets);
        assertEquals(4, parser.snapshot().packets);
        assertNotSame(old, parser.snapshot());
        assertTrue(Modifier.isFinal(Tbs5530TsParser.Snapshot.class.getModifiers()));
        for (Field field : Tbs5530TsParser.Snapshot.class.getFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType().isPrimitive());
        }
    }

    @Test public void nullNegativeOverflowingAndOutOfBoundsRangesDoNotMutateState() {
        Tbs5530TsParser parser = acquired();
        Tbs5530TsParser.Snapshot before = parser.snapshot();
        try {
            parser.accept(null, 0, 0);
            fail("Null input accepted");
        } catch (NullPointerException expected) { }
        byte[] data = new byte[8];
        for (int[] range : new int[][] {{-1, 0}, {0, -1}, {9, 0}, {8, 1}, {7, 2},
                {Integer.MAX_VALUE, 0}, {0, Integer.MAX_VALUE}, {1, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MIN_VALUE, 1},
                {1, Integer.MIN_VALUE}}) {
            try {
                parser.accept(data, range[0], range[1]);
                fail("Invalid range accepted");
            } catch (IndexOutOfBoundsException expected) { }
            sameSnapshot(before, parser.snapshot());
        }
        parser.accept(data, data.length, 0);
        parser.accept(new byte[0], 0, 0);
        sameSnapshot(before, parser.snapshot());
    }

    @Test public void byteCounterSaturatesInsteadOfOverflowing() throws Exception {
        Tbs5530TsParser parser = new Tbs5530TsParser();
        Field bytes = Tbs5530TsParser.class.getDeclaredField("bytesReceived");
        bytes.setAccessible(true);
        bytes.setLong(parser, Long.MAX_VALUE - 1);
        feed(parser, garbage(4));
        assertEquals(Long.MAX_VALUE, parser.snapshot().bytesReceived);
        feed(parser, garbage(4));
        assertEquals(Long.MAX_VALUE, parser.snapshot().bytesReceived);
    }

    @Test public void eventCountersSaturateDuringParsingInsteadOfWrapping() throws Exception {
        Tbs5530TsParser parser = acquired();
        for (Field field : Tbs5530TsParser.class.getDeclaredFields()) {
            if (field.getType() == long.class) {
                field.setAccessible(true);
                field.setLong(parser, Long.MAX_VALUE);
            }
        }
        byte[] tei = packet(0x1fff, 0);
        tei[1] |= (byte) 0x80;
        feed(parser, join(packet(PID, 2), packet(PID, 9),
                adaptation(PID, 4, true, 1, 0x80), adaptation(PID, 3, true, 255, 0),
                tei, garbage(600), sequence(PID, 0, 3)));
        for (Field field : Tbs5530TsParser.Snapshot.class.getFields()) {
            if (field.getType() == long.class) assertEquals(Long.MAX_VALUE, field.getLong(parser.snapshot()));
        }
    }

    @Test public void arbitraryHeaderAndAdaptationBytesNeverReadPastPacketBoundaries() {
        Tbs5530TsParser parser = acquired();
        Random random = new Random(1885530);
        for (int i = 0; i < 4096; i++) {
            byte[] candidate = new byte[SIZE];
            random.nextBytes(candidate);
            candidate[0] = 0x47;
            feed(parser, candidate);
            conserved(parser);
        }
        assertEquals(4099, parser.snapshot().packets);
        assertEquals(0, parser.snapshot().syncLosses);
        assertTrue(parser.snapshot().malformedPackets > 0);
    }
}
