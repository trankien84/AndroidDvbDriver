// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Golden dry-run never opens a device. Live plans must match its pinned canonical fingerprints. */
public final class Tbs5530AutonomousPlans {
    private static final String[] PINNED = {
        "c61e9a824d916665266f2d053a2775e7410e64944f9b32f7b9dce97e5bd7af6c",
        "baa6c6179eaea75c48a25f10dd1477f7890f7ad594af3b55fe5e46bb2e832f78",
        "29df060578b3283022c39c32697d130fb16e148b2cecfa89e8a59c3799efa68f",
        "0f25cd22b085422552f38178405fba11f4a2b8ff42e93b0a22da7fc42b0b1d7b"
    };
    private static final Tbs5530Frontend.Operation[] OPERATIONS = {
        Tbs5530Frontend.Operation.PROBE, Tbs5530Frontend.Operation.INITIALIZE,
        Tbs5530Frontend.Operation.TUNE, Tbs5530Frontend.Operation.POLL_STATUS
    };
    private static final String MANUAL_PLP0_TUNE = "cd0b9eb33bd972db4d4dc37fbeae8ee7465b3402a86b5de36f9f0a289eff9b66";
    private final Map<Tbs5530Frontend.Operation, String> hashes;
    private final Tbs5530FrontendExperiment experiment;
    public Tbs5530AutonomousPlans() throws IOException {
        this(Tbs5530FrontendExperiment.BASELINE_AUTO);
    }
    public Tbs5530AutonomousPlans(Tbs5530FrontendExperiment experiment) throws IOException {
        if (experiment == null) throw new IOException("Explicit frontend experiment required");
        this.experiment = experiment;
        hashes = dryRun(experiment);
        for (int i = 0; i < OPERATIONS.length; i++) {
            String expected = experiment != Tbs5530FrontendExperiment.BASELINE_AUTO && OPERATIONS[i] == Tbs5530Frontend.Operation.TUNE
                    ? MANUAL_PLP0_TUNE : PINNED[i];
            if (!expected.equals(hashes.get(OPERATIONS[i]))) throw new IOException("Golden dry-run mismatch: " + OPERATIONS[i]);
        }
    }
    public String fingerprint() { return hash(hashes.toString()); }
    public void require(Tbs5530Frontend.Plan plan) throws IOException {
        if (plan == null || !canonical(plan).equals(hashes.get(plan.operation))) throw new IOException("Plan is not allowlisted");
        if (plan.operation == Tbs5530Frontend.Operation.TUNE && !experiment.matches(plan.tuneParameters))
            throw new IOException("Tune differs from the exact frontend experiment");
    }
    public static boolean nativeUrb(Tbs5530TsSmoke.ReadApi api) {
        return api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X4096 || api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192;
    }
    public static int chunkBytes(Tbs5530TsSmoke.ReadApi api) {
        return api == Tbs5530TsSmoke.ReadApi.NATIVE_USBFS_URB_8X8192 ? 8192 : 4096;
    }
    public static void requireTs(Tbs5530TsSmoke.Limits limits) throws IOException {
        if (limits == null || limits.maxBytes != 65536 || limits.maxReads != 16 || limits.durationMillis != 5000
                || limits.readTimeoutMillis != 500 || limits.readChunkBytes != chunkBytes(limits.readApi) || limits.prepareEndpoint)
            throw new IOException("TS limits are not allowlisted");
    }
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append("0123456789abcdef".charAt((b & 255) >>> 4)).append("0123456789abcdef".charAt(b & 15));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static String canonical(Tbs5530Frontend.Plan plan) {
        String transcript = plan.getApprovalTranscript();
        int start = transcript.indexOf('\n') + 1, end = transcript.indexOf('\n', start);
        String[] scope = transcript.substring(start, end).split("\\|", -1);
        scope[1] = "0"; scope[2] = "0";
        StringBuilder normalized = new StringBuilder(transcript.substring(0, start));
        for (int i = 0; i < scope.length; i++) { if (i != 0) normalized.append('|'); normalized.append(scope[i]); }
        return hash(normalized.append(transcript.substring(end)).toString());
    }
    public static Map<Tbs5530Frontend.Operation, String> dryRun() throws IOException {
        return dryRun(Tbs5530FrontendExperiment.BASELINE_AUTO);
    }
    public static Map<Tbs5530Frontend.Operation, String> dryRun(Tbs5530FrontendExperiment experiment) throws IOException {
        Map<Tbs5530Frontend.Operation, String> result = new LinkedHashMap<>();
        for (Tbs5530Frontend.Plan plan : dryRunPlans(experiment).values()) result.put(plan.operation, canonical(plan));
        return result;
    }
    /** Immutable approval programs for offline review; the only transport here is the fake below. */
    public static Map<Tbs5530Frontend.Operation, Tbs5530Frontend.Plan> dryRunPlans(Tbs5530FrontendExperiment experiment) throws IOException {
        if (experiment == null) throw new IOException("Explicit frontend experiment required");
        Tbs5530Session session = new Tbs5530Session();
        long token = session.requestPermission(); session.permissionResult(token, true);
        session.connected(token, new Tbs5530Session.Resources() {
            public void cancel() { } public void release() { } public void close() { }
        }); session.interfaceClaimed(token);
        final long[] now = { 0 }; final int[] register = { 0 };
        Tbs5530ControlIo fake = (type, request, value, index, bytes, offset, length, timeout) -> {
            if (request == 0x90) register[0] = bytes[2] & 255;
            if (request == 0x91) {
                java.util.Arrays.fill(bytes, (byte) 0);
                bytes[0] = (byte) (register[0] == 0xfb ? 1 : register[0] == 0xfd ? 0x97
                        : register[0] == 0x7f ? 0xe1 : register[0] == 0x10 ? 0x26 : 0);
            }
            return length;
        };
        Tbs5530Frontend frontend = new Tbs5530Frontend(new Tbs5530Bridge(fake, session, token), session, token,
                new Tbs5530Frontend.Clock() {
                    public long elapsedRealtimeMillis() { return now[0]; }
                    public void delayMillis(long millis) { now[0] += millis; }
                });
        Map<Tbs5530Frontend.Operation, Tbs5530Frontend.Plan> result = new LinkedHashMap<>();
        try {
            Tbs5530Frontend.Plan probe = frontend.planProbe(); result.put(probe.operation, probe); frontend.execute(probe);
            Tbs5530Frontend.Plan init = frontend.planInitialize(); result.put(init.operation, init); frontend.execute(init);
            frontend.readyForTune();
            Tbs5530Frontend.Plan tune = frontend.planTune(experiment.parameters(), experiment == Tbs5530FrontendExperiment.BASELINE_AUTO);
            result.put(tune.operation, tune); frontend.execute(tune);
            Tbs5530Frontend.Plan status = frontend.planPollStatus(3); result.put(status.operation, status);
            if (!frontend.execute(status).status.locked) throw new IOException("Fake golden path did not lock");
            return result;
        } finally { session.close(); }
    }
}
