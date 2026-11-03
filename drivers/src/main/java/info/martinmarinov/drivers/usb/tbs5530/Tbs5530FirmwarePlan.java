// SPDX-License-Identifier: GPL-2.0-or-later

package info.martinmarinov.drivers.usb.tbs5530;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, offline description of a synthetic firmware transfer. This class performs no IO.
 * A policy and digest for synthetic data do not authorize use of a real firmware image.
 *
 * Protocol provenance: official linux_media 2ce787de9f6e81b4294692a45de15596ae2e1322,
 * drivers/media/usb/dvb-usb/tbs5530.c, tbs5530_load_firmware; cxd28xx
 * c5fa27eecbe80633cbb365cc44656daea1c0e2e3, tbs5530.c, tbs5530_load_firmware;
 * coax 11228b05fb1626bf41c4e1879ace10cd0a8d575c,
 * internal/coaxd/usb/bridge/tbs/tbs.go, Bridge.LoadFirmware.
 * Unlike the fixed-size final transfer in the C sources, the final payload has its actual length.
 * The Go size cap alone does not protect the reset register at 0x7f92.
 */
public final class Tbs5530FirmwarePlan {
    private static final int ADDRESS_SPACE_SIZE = 0x10000;
    private static final int RESET_REGISTER_LEGACY = 0x7f92;
    private static final int RESET_REGISTER_FX2 = 0xe600;
    private static final int CHUNK_SIZE = 64;
    private static final int CONTROL_TIMEOUT_MILLIS = 2000;
    private static final int RESTART_DELAY_MILLIS = 100;

    public enum Kind { DATA, DELAY }

    /** A nonempty half-open interval of actual memory addresses, not transfer offsets. */
    public static final class Range {
        private final int startInclusive;
        private final int endExclusive;

        public Range(int startInclusive, int endExclusive) {
            if (startInclusive < 0 || startInclusive >= ADDRESS_SPACE_SIZE
                    || endExclusive <= startInclusive || endExclusive > ADDRESS_SPACE_SIZE) {
                throw new IllegalArgumentException("Invalid 16-bit address range");
            }
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        public int getStartInclusive() {
            return startInclusive;
        }

        public int getEndExclusive() {
            return endExclusive;
        }

        private boolean contains(int address) {
            return address >= startInclusive && address < endExclusive;
        }
    }

    /**
     * Explicit approval boundary for one synthetic image. Allowed ranges form a union;
     * reserved ranges take precedence. A null reserved-ranges list means no caller reservations;
     * allowed ranges remain mandatory. Both reset addresses are always reserved for image data.
     */
    public static final class Policy {
        private final byte[] expectedSha256;
        private final List<Range> allowedRanges;
        private final List<Range> reservedRanges;
        private final int imageBaseAddress;
        private final int maxSize;

        public Policy(byte[] expectedSha256, List<Range> allowedRanges,
                      List<Range> reservedRanges, int imageBaseAddress, int maxSize) {
            if (expectedSha256 == null || expectedSha256.length != 32) {
                throw new IllegalArgumentException("Expected SHA-256 must contain 32 bytes");
            }
            if (imageBaseAddress != 0) {
                throw new IllegalArgumentException("The board image format starts at address zero");
            }
            if (maxSize <= 0 || maxSize > ADDRESS_SPACE_SIZE) {
                throw new IllegalArgumentException("Maximum image size must be 1 to 65536 bytes");
            }
            this.expectedSha256 = expectedSha256.clone();
            this.allowedRanges = copyRanges(allowedRanges);
            this.reservedRanges = reservedRanges == null
                    ? Collections.<Range>emptyList() : copyRanges(reservedRanges);
            if (this.allowedRanges.isEmpty()) {
                throw new IllegalArgumentException("At least one allowed address range is required");
            }
            this.imageBaseAddress = imageBaseAddress;
            this.maxSize = maxSize;
        }

        public byte[] getExpectedSha256() {
            return expectedSha256.clone();
        }

        public List<Range> getAllowedRanges() {
            return allowedRanges;
        }

        public List<Range> getReservedRanges() {
            return reservedRanges;
        }

        public int getImageBaseAddress() {
            return imageBaseAddress;
        }

        public int getMaxSize() {
            return maxSize;
        }

        private static List<Range> copyRanges(List<Range> ranges) {
            if (ranges == null) {
                throw new IllegalArgumentException("Address ranges must not be null");
            }
            List<Range> copy = new ArrayList<>(ranges.size());
            for (Range range : ranges) {
                if (range == null) {
                    throw new IllegalArgumentException("Address ranges must not contain null");
                }
                copy.add(new Range(range.startInclusive, range.endExclusive));
            }
            return Collections.unmodifiableList(copy);
        }
    }

    /** DATA describes a control write; DELAY describes time only and has zeroed control fields. */
    public static final class Step {
        private final Kind kind;
        private final int value;
        private final byte[] payload;

        private Step(Kind kind, int value, byte[] payload) {
            this.kind = kind;
            this.value = value;
            this.payload = payload.clone();
        }

        public Kind getKind() {
            return kind;
        }

        public int getRequestType() {
            return kind == Kind.DATA ? 0x40 : 0;
        }

        public int getRequest() {
            return kind == Kind.DATA ? 0xa0 : 0;
        }

        public int getValue() {
            return value;
        }

        public int getIndex() {
            return 0;
        }

        public byte[] getPayload() {
            return payload.clone();
        }

        public int getTimeoutMillis() {
            return kind == Kind.DATA ? CONTROL_TIMEOUT_MILLIS : 0;
        }

        public int getDelayMillis() {
            return kind == Kind.DELAY ? RESTART_DELAY_MILLIS : 0;
        }
    }

    private final List<Step> steps;

    private Tbs5530FirmwarePlan(List<Step> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * Validates the complete image before constructing any steps. All invalid images or policies
     * are rejected with IllegalArgumentException. Reset control steps intentionally bypass the
     * image address policy; they are protocol metadata, not bytes from the image.
     */
    public static Tbs5530FirmwarePlan create(byte[] syntheticImage, Policy policy) {
        if (syntheticImage == null || policy == null) {
            throw new IllegalArgumentException("Synthetic image and policy are required");
        }
        if (syntheticImage.length == 0 || syntheticImage.length > policy.maxSize) {
            throw new IllegalArgumentException("Synthetic image size is outside the policy limit");
        }
        long endExclusive = (long) policy.imageBaseAddress + syntheticImage.length;
        if (endExclusive > ADDRESS_SPACE_SIZE) {
            throw new IllegalArgumentException("Synthetic image exceeds 16-bit address space");
        }
        for (int address = policy.imageBaseAddress; address < endExclusive; address++) {
            if (address == RESET_REGISTER_LEGACY || address == RESET_REGISTER_FX2
                    || contains(policy.reservedRanges, address)
                    || !contains(policy.allowedRanges, address)) {
                throw new IllegalArgumentException("Synthetic image contains a forbidden address");
            }
        }
        byte[] image = syntheticImage.clone();
        if (!MessageDigest.isEqual(policy.expectedSha256, sha256(image))) {
            throw new IllegalArgumentException("Synthetic image SHA-256 does not match policy");
        }

        List<Step> steps = new ArrayList<>();
        addResetSteps(steps, (byte) 1);
        for (int offset = 0; offset < image.length; offset += CHUNK_SIZE) {
            int end = Math.min(offset + CHUNK_SIZE, image.length);
            steps.add(new Step(Kind.DATA, policy.imageBaseAddress + offset,
                    Arrays.copyOfRange(image, offset, end)));
        }
        addResetSteps(steps, (byte) 0);
        steps.add(new Step(Kind.DELAY, 0, new byte[0]));
        return new Tbs5530FirmwarePlan(steps);
    }

    public List<Step> getSteps() {
        return steps;
    }

    private static void addResetSteps(List<Step> steps, byte value) {
        steps.add(new Step(Kind.DATA, RESET_REGISTER_LEGACY, new byte[]{value}));
        steps.add(new Step(Kind.DATA, RESET_REGISTER_FX2, new byte[]{value}));
    }

    private static boolean contains(List<Range> ranges, int address) {
        for (Range range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] sha256(byte[] image) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(image);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }
}
