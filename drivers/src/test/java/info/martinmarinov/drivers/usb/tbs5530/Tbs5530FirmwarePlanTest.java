// SPDX-License-Identifier: GPL-2.0-or-later

package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwarePlan.Kind.DATA;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530FirmwarePlan.Kind.DELAY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Pure synthetic-data tests: no transport, real firmware, hardware, sleeps or file access. */
public class Tbs5530FirmwarePlanTest {
    @Test
    public void plansExactSequenceAndTailForChunkBoundaries() {
        for (int size : new int[]{1, 63, 64, 65, 128}) {
            byte[] image = syntheticImage(size);
            Tbs5530FirmwarePlan plan = Tbs5530FirmwarePlan.create(image, policy(image));
            List<Tbs5530FirmwarePlan.Step> steps = plan.getSteps();
            int chunks = (size + 63) / 64;
            assertEquals(chunks + 5, steps.size());
            assertControl(steps.get(0), 0x7f92, new byte[]{1});
            assertControl(steps.get(1), 0xe600, new byte[]{1});
            int imageBytes = 0;
            for (int chunk = 0; chunk < chunks; chunk++) {
                int offset = chunk * 64;
                byte[] expected = Arrays.copyOfRange(image, offset, Math.min(offset + 64, size));
                Tbs5530FirmwarePlan.Step step = steps.get(chunk + 2);
                assertControl(step, offset, expected);
                assertTrue(step.getPayload().length > 0 && step.getPayload().length <= 64);
                imageBytes += step.getPayload().length;
            }
            assertEquals(size, imageBytes);
            assertControl(steps.get(chunks + 2), 0x7f92, new byte[]{0});
            assertControl(steps.get(chunks + 3), 0xe600, new byte[]{0});
            assertDelay(steps.get(chunks + 4));
        }
    }

    @Test
    public void resetMetadataDoesNotNeedImageAddressPermission() {
        byte[] image = syntheticImage(1);
        Tbs5530FirmwarePlan.Policy policy = policy(image,
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, 1)),
                Arrays.asList(new Tbs5530FirmwarePlan.Range(0x7f92, 0x7f93),
                        new Tbs5530FirmwarePlan.Range(0xe600, 0xe601)), 1);
        assertEquals(6, Tbs5530FirmwarePlan.create(image, policy).getSteps().size());
    }

    @Test
    public void rejectsNullAndEmptyImagesAndNullPolicy() {
        byte[] image = syntheticImage(1);
        Tbs5530FirmwarePlan.Policy policy = policy(image);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(null, policy));
        expectIllegal(() -> Tbs5530FirmwarePlan.create(new byte[0], policy));
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, null));
    }

    @Test
    public void rejectsDigestMismatchIncludingChangedTailByte() {
        byte[] image = syntheticImage(65);
        Tbs5530FirmwarePlan.Policy policy = policy(image);
        image[64] ^= 1;
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
    }

    @Test
    public void digestMustMatchEveryByteAndLength() {
        byte[] image = syntheticImage(64);
        byte[] digest = sha256(image);
        digest[31] ^= 1;
        Tbs5530FirmwarePlan.Policy policy = new Tbs5530FirmwarePlan.Policy(digest,
                allAddresses(), noRanges(), 0, 64);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
        expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(null, allAddresses(), noRanges(), 0, 64));
        for (int length : new int[]{0, 31, 33, 64}) {
            expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(new byte[length],
                    allAddresses(), noRanges(), 0, 64));
        }
    }

    @Test
    public void acceptsExactSizeLimitAndRejectsOneByteOver() {
        byte[] image = syntheticImage(65);
        Tbs5530FirmwarePlan.Policy exact = policy(image, allAddresses(), noRanges(), 65);
        assertEquals(7, Tbs5530FirmwarePlan.create(image, exact).getSteps().size());
        Tbs5530FirmwarePlan.Policy tooSmall = policy(image, allAddresses(), noRanges(), 64);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, tooSmall));
    }

    @Test
    public void rejectsZeroNegativeAndOverflowingSizeLimits() {
        for (int limit : new int[]{0, -1, Integer.MIN_VALUE, 0x10001, Integer.MAX_VALUE}) {
            expectIllegal(() -> policy(syntheticImage(1), allAddresses(), noRanges(), limit));
        }
        assertEquals(0x10000,
                policy(syntheticImage(1), allAddresses(), noRanges(), 0x10000).getMaxSize());
    }

    @Test
    public void rejectsNonzeroAndOverflowingBaseAddresses() {
        for (int base : new int[]{-1, 1, 0xffff, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(sha256(syntheticImage(1)),
                    allAddresses(), noRanges(), base, 64));
        }
    }

    @Test
    public void rejectsImagesTooLargeForAddressSpaceWithoutTruncation() {
        byte[] image = syntheticImage(0x10001);
        Tbs5530FirmwarePlan.Policy policy = policy(image, allAddresses(), noRanges(), 0x10000);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
    }

    @Test
    public void validatesHalfOpenRangeBoundsWithoutIntegerOverflow() {
        int[][] invalid = {
                {-1, 1}, {0, 0}, {1, 0}, {0x10000, 0x10000}, {0, 0x10001},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {0, Integer.MIN_VALUE},
                {Integer.MAX_VALUE, Integer.MIN_VALUE}, {0, Integer.MAX_VALUE}
        };
        for (int[] range : invalid) {
            expectIllegal(() -> new Tbs5530FirmwarePlan.Range(range[0], range[1]));
        }
        Tbs5530FirmwarePlan.Range last = new Tbs5530FirmwarePlan.Range(0xffff, 0x10000);
        assertEquals(0xffff, last.getStartInclusive());
        assertEquals(0x10000, last.getEndExclusive());
    }

    @Test
    public void allowsAdjacentUnsortedAndOverlappingRangesAsAUnion() {
        byte[] image = syntheticImage(65);
        List<Tbs5530FirmwarePlan.Range> ranges = Arrays.asList(
                new Tbs5530FirmwarePlan.Range(32, 65),
                new Tbs5530FirmwarePlan.Range(16, 48),
                new Tbs5530FirmwarePlan.Range(0, 16));
        Tbs5530FirmwarePlan.Policy policy = policy(image, ranges, noRanges(), 65);
        assertEquals(7, Tbs5530FirmwarePlan.create(image, policy).getSteps().size());
    }

    @Test
    public void rejectsAnAllowedRangeGapInsideAChunk() {
        byte[] image = syntheticImage(64);
        List<Tbs5530FirmwarePlan.Range> ranges = Arrays.asList(
                new Tbs5530FirmwarePlan.Range(0, 31),
                new Tbs5530FirmwarePlan.Range(32, 64));
        Tbs5530FirmwarePlan.Policy policy = policy(image, ranges, noRanges(), 64);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
    }

    @Test
    public void rejectsTailOutsideAllowedRanges() {
        byte[] image = syntheticImage(65);
        Tbs5530FirmwarePlan.Policy policy = policy(image,
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, 64)), noRanges(), 65);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
    }

    @Test
    public void checksEveryPayloadAddressAgainstCallerReservedRanges() {
        byte[] image = syntheticImage(65);
        for (int reserved : new int[]{0, 1, 31, 63, 64}) {
            Tbs5530FirmwarePlan.Policy policy = policy(image, allAddresses(),
                    Collections.singletonList(new Tbs5530FirmwarePlan.Range(reserved, reserved + 1)),
                    65);
            expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
        }
    }

    @Test
    public void permitsImageEndingAtReservedRangeStart() {
        byte[] image = syntheticImage(64);
        Tbs5530FirmwarePlan.Policy policy = policy(image, allAddresses(),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(64, 65)), 64);
        assertEquals(6, Tbs5530FirmwarePlan.create(image, policy).getSteps().size());
    }

    @Test
    public void alwaysReservesResetRegistersEvenWithAnEmptyCallerReservedMap() {
        byte[] safe = syntheticImage(0x7f92);
        Tbs5530FirmwarePlan.create(safe, policy(safe));
        for (int size : new int[]{0x7f93, 0xe200, 0xe601, 0x10000}) {
            byte[] image = syntheticImage(size);
            Tbs5530FirmwarePlan.Policy policy = policy(image);
            expectIllegal(() -> Tbs5530FirmwarePlan.create(image, policy));
        }
    }

    @Test
    public void distinguishesOptionalReservedRangesFromInvalidRequiredAddressMaps() {
        byte[] image = syntheticImage(1);
        byte[] digest = sha256(image);
        Tbs5530FirmwarePlan.Policy optionalReserved = new Tbs5530FirmwarePlan.Policy(
                digest, allAddresses(), null, 0, 1);
        assertTrue(optionalReserved.getReservedRanges().isEmpty());
        assertEquals(6, Tbs5530FirmwarePlan.create(image, optionalReserved).getSteps().size());
        expectUnsupported(() -> optionalReserved.getReservedRanges().add(
                new Tbs5530FirmwarePlan.Range(0, 1)));
        byte[] resetOverlap = syntheticImage(0x7f93);
        Tbs5530FirmwarePlan.Policy noCallerReservations = new Tbs5530FirmwarePlan.Policy(
                sha256(resetOverlap), allAddresses(), null, 0, resetOverlap.length);
        expectIllegal(() -> Tbs5530FirmwarePlan.create(resetOverlap, noCallerReservations));
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, null));
        expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(digest, null, noRanges(), 0, 1));
        expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(digest, noRanges(), noRanges(), 0, 1));
        List<Tbs5530FirmwarePlan.Range> withNull = Arrays.asList(
                new Tbs5530FirmwarePlan.Range(0, 1), null);
        expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(digest, withNull, noRanges(), 0, 1));
        expectIllegal(() -> new Tbs5530FirmwarePlan.Policy(digest, allAddresses(), withNull, 0, 1));
    }

    @Test
    public void policyCopiesDigestAndRangeInputsAndProtectsOutputs() {
        byte[] image = syntheticImage(65);
        byte[] digest = sha256(image);
        List<Tbs5530FirmwarePlan.Range> allowed = new ArrayList<>(allAddresses());
        List<Tbs5530FirmwarePlan.Range> reserved = new ArrayList<>();
        reserved.add(new Tbs5530FirmwarePlan.Range(128, 129));
        Tbs5530FirmwarePlan.Policy policy = new Tbs5530FirmwarePlan.Policy(
                digest, allowed, reserved, 0, 65);
        assertNotSame(allowed.get(0), policy.getAllowedRanges().get(0));
        assertNotSame(reserved.get(0), policy.getReservedRanges().get(0));
        Arrays.fill(digest, (byte) 0);
        allowed.clear();
        reserved.clear();
        byte[] exposedDigest = policy.getExpectedSha256();
        Arrays.fill(exposedDigest, (byte) 0);
        assertArrayEquals(sha256(image), policy.getExpectedSha256());
        assertEquals(1, policy.getAllowedRanges().size());
        assertEquals(1, policy.getReservedRanges().size());
        assertEquals(128, policy.getReservedRanges().get(0).getStartInclusive());
        assertEquals(0, policy.getImageBaseAddress());
        assertEquals(65, policy.getMaxSize());
        expectUnsupported(() -> policy.getAllowedRanges().clear());
        expectUnsupported(() -> policy.getReservedRanges().add(new Tbs5530FirmwarePlan.Range(0, 1)));
        assertEquals(7, Tbs5530FirmwarePlan.create(image, policy).getSteps().size());

        List<Tbs5530FirmwarePlan.Range> blocked = new ArrayList<>();
        blocked.add(new Tbs5530FirmwarePlan.Range(31, 32));
        Tbs5530FirmwarePlan.Policy deniesImage = policy(image, allAddresses(), blocked, 65);
        blocked.clear();
        expectIllegal(() -> Tbs5530FirmwarePlan.create(image, deniesImage));
    }

    @Test
    public void planCopiesImageAndEveryReturnedPayload() {
        byte[] image = syntheticImage(65);
        byte[] expected = image.clone();
        Tbs5530FirmwarePlan plan = Tbs5530FirmwarePlan.create(image, policy(image));
        Arrays.fill(image, (byte) 0);
        List<Tbs5530FirmwarePlan.Step> steps = plan.getSteps();
        expectUnsupported(steps::clear);
        expectUnsupported(() -> steps.set(0, steps.get(1)));
        for (Tbs5530FirmwarePlan.Step step : steps) {
            byte[] before = step.getPayload();
            byte[] exposed = step.getPayload();
            assertNotSame(before, exposed);
            Arrays.fill(exposed, (byte) 99);
            assertArrayEquals(before, step.getPayload());
        }
        assertControl(steps.get(0), 0x7f92, new byte[]{1});
        assertControl(steps.get(2), 0, Arrays.copyOfRange(expected, 0, 64));
        assertControl(steps.get(3), 64, Arrays.copyOfRange(expected, 64, 65));
        assertControl(steps.get(4), 0x7f92, new byte[]{0});
    }

    private static void assertControl(Tbs5530FirmwarePlan.Step step, int address, byte[] payload) {
        assertEquals(DATA, step.getKind());
        assertEquals(0x40, step.getRequestType());
        assertEquals(0xa0, step.getRequest());
        assertEquals(address, step.getValue());
        assertEquals(0, step.getIndex());
        assertArrayEquals(payload, step.getPayload());
        assertEquals(2000, step.getTimeoutMillis());
        assertEquals(0, step.getDelayMillis());
    }

    private static void assertDelay(Tbs5530FirmwarePlan.Step step) {
        assertEquals(DELAY, step.getKind());
        assertEquals(100, step.getDelayMillis());
        assertEquals(0, step.getRequestType());
        assertEquals(0, step.getRequest());
        assertEquals(0, step.getValue());
        assertEquals(0, step.getIndex());
        assertEquals(0, step.getTimeoutMillis());
        assertArrayEquals(new byte[0], step.getPayload());
    }

    private static Tbs5530FirmwarePlan.Policy policy(byte[] image) {
        return policy(image, allAddresses(), noRanges(), Math.max(1, image.length));
    }

    private static Tbs5530FirmwarePlan.Policy policy(byte[] image,
            List<Tbs5530FirmwarePlan.Range> allowed, List<Tbs5530FirmwarePlan.Range> reserved,
            int maxSize) {
        return new Tbs5530FirmwarePlan.Policy(sha256(image), allowed, reserved, 0, maxSize);
    }

    private static List<Tbs5530FirmwarePlan.Range> allAddresses() {
        return Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, 0x10000));
    }

    private static List<Tbs5530FirmwarePlan.Range> noRanges() {
        return Collections.emptyList();
    }

    private static byte[] syntheticImage(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 37 + 11);
        }
        return data;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Invalid synthetic data must fail before a usable plan exists.
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            fail("Expected immutable collection");
        } catch (UnsupportedOperationException expected) {
            // All publicly exposed collections are immutable.
        }
    }
}
