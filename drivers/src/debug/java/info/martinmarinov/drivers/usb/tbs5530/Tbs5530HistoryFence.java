// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/** Session-local fence. The host must independently pin its initial hash in the immutable grant. */
public final class Tbs5530HistoryFence {
    public interface Store { byte[] read() throws IOException; }
    private byte[] expected;
    private boolean faulted;
    public Tbs5530HistoryFence(byte[] snapshot) throws IOException {
        Tbs5530HistoryRecovery.validate(snapshot); expected = snapshot.clone();
    }
    public synchronized String sha256() { return Tbs5530HistoryRecovery.hash(expected); }
    public synchronized void require(byte[] current) throws IOException {
        if (faulted || current == null || !Arrays.equals(current, expected)) {
            faulted = true; throw new IOException("Attachment history differs from host-pinned state; no rearm");
        }
    }
    /** Only an owned, completed consume may advance the cache; rollback or a no-op is never advancement. */
    public synchronized void advanced(byte[] current) throws IOException {
        try {
            if (faulted) throw new IOException("History fence already faulted");
            Set<String> prior = Tbs5530HistoryRecovery.validate(expected), next = Tbs5530HistoryRecovery.validate(current);
            if (current.length <= expected.length || next.size() <= prior.size() || !next.containsAll(prior)
                    || !Arrays.equals(expected, Arrays.copyOf(current, expected.length)))
                throw new IOException("History advancement is not append-only");
            expected = current.clone();
        } catch (IOException error) { faulted = true; throw error; }
    }
}
