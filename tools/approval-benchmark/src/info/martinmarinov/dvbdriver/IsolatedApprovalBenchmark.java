// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530BootstrapStop;

public final class IsolatedApprovalBenchmark {
    private IsolatedApprovalBenchmark() { }
    private static final class TotalDeadline extends IOException {
        TotalDeadline() { super("TOTAL_BENCHMARK_DEADLINE: overall benchmark bound reached"); }
    }
    /** app_process entry point: no Context, package preferences, USB or production files. */
    public static void runIsolatedBenchmark(String[] args, Tbs5530ApprovalSession.Clock clock) throws Exception {
        if (args.length != 2 || !"--isolated-benchmark".equals(args[0])
                || !args[1].matches("/sdcard/Download/tbs5530-approval-benchmark/[a-f0-9-]{36}"))
            throw new IllegalArgumentException("Only a new isolated benchmark namespace is allowed");
        File directory = new File(args[1]);
        if (directory.exists() || !directory.mkdirs()) throw new IOException("Benchmark directory must be new");
        Tbs5530ApprovalSession.Storage files = new Tbs5530ApprovalSession.Storage() {
            private boolean used;
            public String install() { return "benchmark-only"; }
            public boolean consumed() { return used; }
            public void consume() { used = true; }
            public String read(String name) throws IOException { return readFile(new File(directory, name)); }
            public void write(String name, String content) throws IOException { writeFile(new File(directory, name), content); }
        };
        Tbs5530ApprovalSession approval = new Tbs5530ApprovalSession(files, clock,
                "0000000000000000000000000000000000000000000000000000000000000000");
        approval.foreground(true); approval.publishChallenge();
        System.out.println("BENCHMARK_READY " + args[1]);
        // Overall 240 s covers 132 x 1500 ms plus setup; individual guard deadlines stay unchanged.
        long hardDeadline = clock.now() + 240000;
        try {
            while (!new File(directory, "phase29-session-approval.json").isFile()) {
                if (clock.now() >= hardDeadline) throw new TotalDeadline();
                Thread.sleep(10);
            }
            StringBuilder samples = new StringBuilder();
            for (int ordinal = 1; ordinal <= Tbs5530ApprovalProtocol.ORDINALS; ordinal++) {
                if (clock.now() >= hardDeadline) throw new TotalDeadline();
                approval.requireWrite(ordinal);
                approval.requireNative(ordinal);
                samples.append(files.read("phase29-write-observation.json")).append('\n');
            }
            files.write("benchmark-observations.jsonl", samples.toString());
            files.write("benchmark-result.json", "{\"status\":\"PASS\",\"roundtrips\":132,\"usb_calls\":0}");
            System.out.println("BENCHMARK_PASS 132 Android request/host acknowledgment/Android observations; USB calls=0");
        } catch (Exception error) {
            String failure = error instanceof TotalDeadline ? "TOTAL_BENCHMARK_DEADLINE"
                    : Tbs5530BootstrapStop.kindOf(error) == Tbs5530BootstrapStop.Kind.PRE_NATIVE_ACK_TIMEOUT
                    ? "ACK_TIMEOUT" : "ANDROID_GUARD_STOP";
            files.write("benchmark-result.json", "{\"status\":\"FAIL\",\"failure_kind\":\"" + failure + "\",\"stop_kind\":\""
                    + Tbs5530BootstrapStop.kindOf(error).name() + "\",\"usb_calls\":0}");
            throw error;
        }
    }
    private static String readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[8193]; int size = 0, n;
            while (size < bytes.length && (n = in.read(bytes, size, bytes.length - size)) > 0) size += n;
            if (size == bytes.length) throw new IOException("Oversized approval metadata");
            return new String(bytes, 0, size, StandardCharsets.UTF_8);
        }
    }
    private static void writeFile(File file, String content) throws IOException {
        File staged = new File(file.getPath() + ".new");
        try (FileOutputStream out = new FileOutputStream(staged)) {
            out.write(content.getBytes(StandardCharsets.UTF_8)); out.flush(); out.getFD().sync();
        }
        if (!staged.renameTo(file)) throw new IOException("Benchmark atomic rename failed");
    }
}
