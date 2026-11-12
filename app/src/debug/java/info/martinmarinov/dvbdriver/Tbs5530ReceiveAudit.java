// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ReloadLedger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded observation log only. Critical consume/grant/authority durability is unchanged. */
final class Tbs5530ReceiveAudit {
    private final File file;
    private byte[] expected = new byte[0];
    private String state = "NOT_STARTED";
    private boolean finished;
    Tbs5530ReceiveAudit(File file) { this.file = file; }
    synchronized void write(byte[] next) throws IOException {
        try {
            if (finished || "FAILED".equals(state) || next == null || next.length <= expected.length || next.length > 65536
                    || next[next.length - 1] != '\n' || !Arrays.equals(expected, Arrays.copyOf(next, expected.length)))
                throw new IOException("Receive observation audit bounds or prefix changed");
            int records = 0; for (byte b : next) if (b == '\n') records++;
            if (records > 64) throw new IOException("Receive observation audit record bound exceeded");
            if ("NOT_STARTED".equals(state)) {
                new Tbs5530AppendAudit(new File(file.getPath() + ".started")).write("INCOMPLETE_RECEIVE_OBSERVATION/v1\n".getBytes(StandardCharsets.UTF_8));
                state = "BUFFERING_UNCERTIFIED";
            }
            expected = next.clone();
        } catch (IOException | RuntimeException error) { state = "FAILED"; throw error; }
    }
    synchronized void finishAfterDrain(boolean drained) throws IOException {
        if (finished || "FAILED".equals(state) || !drained || expected.length == 0) {
            state = "FAILED"; throw new IOException("Receive audit cannot be certified without one successful drain/commit");
        }
        finished = true;
        try {
            new Tbs5530AppendAudit(file).write(expected);
            byte[] seal = ("COMPLETE_RECEIVE_OBSERVATION/v1\n" + Tbs5530ReloadLedger.hash(expected) + "\n").getBytes(StandardCharsets.UTF_8);
            new Tbs5530AppendAudit(new File(file.getPath() + ".complete")).write(seal);
            state = "SYNCED_CLOSED";
        } catch (IOException | RuntimeException error) { state = "FAILED"; throw error; }
    }
    synchronized String state() { return state; }
    synchronized int bytes() { return expected.length; }
}
