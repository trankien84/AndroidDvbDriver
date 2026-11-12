// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Debug-only deny-set reconciliation. No Android, USB, file access or implicit permission. */
public final class Tbs5530HistoryRecovery {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final String WITNESS_BEFORE = "e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9";
    public static final String WITNESS_AFTER = "b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb";
    public enum State { PREPARED, CONSUMED, APPEND_VERIFIED, COMMIT_UNKNOWN, REFUSED }
    public interface Port {
        void requireCurrent() throws IOException;
        byte[] readCurrent() throws IOException;
        /** Must persist a distinct exact-plan attempt before any append. */
        void consume(String planSha256) throws IOException;
        /** Open an existing file only, append suffix, sync, never rollback/truncate/create. */
        void appendAndSync(String expectedBeforeSha256, byte[] suffix) throws IOException;
    }
    public static final class Plan {
        public final String baselineSha256, candidateSha256, sha256;
        public final int baselineBytes, candidateBytes;
        private final byte[] before, after;
        private Plan(byte[] before, byte[] after, boolean witness) {
            this.before = before.clone(); this.after = after.clone();
            baselineBytes = before.length; candidateBytes = after.length;
            baselineSha256 = hash(before); candidateSha256 = hash(after);
            sha256 = witness ? witnessPlanSha256() : Tbs5530AutonomousPlans.hash("TBS5530-HISTORY-RECOVERY/v1|namespace=autonomous-v1-samsung-sm-x926b-api36"
                    + "|before=" + baselineSha256 + "|after=" + candidateSha256
                    + "|beforeBytes=" + baselineBytes + "|afterBytes=" + candidateBytes
                    + "|added=2|NO_USB|APPEND_ONLY|FULL_HISTORY_UNPROVEN|NO_HARDWARE_AUTHORIZATION");
        }
    }
    private final Plan plan;
    private final Port port;
    private State state = State.PREPARED;
    public Tbs5530HistoryRecovery(Plan plan, Port port) {
        if (plan == null || port == null) throw new IllegalArgumentException("Plan and port required");
        this.plan = plan; this.port = port;
    }
    public static Plan prepare(byte[] before, byte[] after, String expectedBefore, String expectedAfter) throws IOException {
        Set<String> prior = validate(before), next = validate(after);
        if (!hash(before).equals(expectedBefore) || !hash(after).equals(expectedAfter)
                || after.length != before.length + 166 || next.size() != prior.size() + 2
                || !next.containsAll(prior) || !Arrays.equals(before, Arrays.copyOf(after, before.length)))
            throw new IOException("Recovery must preserve exact prefix and add exactly two approved denials");
        return new Plan(before, after, false);
    }
    public static String witnessPlanSha256() {
        return Tbs5530AutonomousPlans.hash("TBS5530-HISTORY-WITNESS-RECOVERY/v2|namespace=autonomous-v1-samsung-sm-x926b-api36"
                + "|before=" + WITNESS_BEFORE + "|after=" + WITNESS_AFTER
                + "|beforeBytes=581|afterBytes=996|beforeRecords=7|afterRecords=12|added=5"
                + "|NO_USB|APPEND_ONLY|EXACT_HOST_WITNESS|FULL_HISTORY_UNPROVEN|NO_HARDWARE_AUTHORIZATION");
    }
    public static Plan prepareWitness(byte[] before, byte[] after) throws IOException {
        Set<String> prior = validate(before), next = validate(after);
        if (before.length != 581 || after.length != 996 || prior.size() != 7 || next.size() != 12
                || !WITNESS_BEFORE.equals(hash(before)) || !WITNESS_AFTER.equals(hash(after))
                || !next.containsAll(prior) || !Arrays.equals(before, Arrays.copyOf(after, before.length)))
            throw new IOException("Exact witnessed 7-to-12 append required; no replacement or rearm");
        return new Plan(before, after, true);
    }
    public synchronized State state() { return state; }
    public synchronized void apply() throws IOException {
        if (state != State.PREPARED) throw new IOException("Recovery attempt already used; no retry");
        boolean appendEntered = false;
        try {
            port.requireCurrent();
            if (!Arrays.equals(plan.before, port.readCurrent())) throw new IOException("Recovery baseline changed");
            state = State.CONSUMED; port.consume(plan.sha256);
            port.requireCurrent();
            if (!Arrays.equals(plan.before, port.readCurrent())) throw new IOException("Recovery baseline changed after consume");
            appendEntered = true;
            port.appendAndSync(plan.baselineSha256, Arrays.copyOfRange(plan.after, plan.before.length, plan.after.length));
            port.requireCurrent();
            if (!Arrays.equals(plan.after, port.readCurrent())) throw new IOException("Recovery commit not verified");
            state = State.APPEND_VERIFIED;
        } catch (IOException | RuntimeException error) {
            state = appendEntered ? State.COMMIT_UNKNOWN : State.REFUSED;
            throw error;
        }
    }
    /** Strict byte format, including UTF-8/ASCII, newlines and duplicate rejection. */
    public static Set<String> validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid history size");
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.endsWith("\n")) throw new IOException("Truncated history");
        Set<String> result = new HashSet<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            if (!line.matches("FIRMWARE_CONSUMED [a-f0-9]{64}")) throw new IOException("Invalid history format");
            if (!result.add(line.substring(18))) throw new IOException("Duplicate history record");
        }
        return result;
    }
    public static String hash(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(64);
            for (byte b : digest) text.append("0123456789abcdef".charAt((b & 255) >>> 4))
                    .append("0123456789abcdef".charAt(b & 15));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
