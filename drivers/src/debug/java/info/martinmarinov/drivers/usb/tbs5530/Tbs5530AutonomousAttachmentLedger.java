// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Append-only v1 attachment history, separate from every legacy ledger. */
public final class Tbs5530AutonomousAttachmentLedger {
    public interface Storage { String read() throws IOException; void write(String text) throws IOException; }
    public static final class AlreadyConsumed extends IOException {
        public AlreadyConsumed() { super("Attachment already consumed; no retry or rearm"); }
    }
    private final Storage storage;
    public Tbs5530AutonomousAttachmentLedger(Storage storage) { this.storage = storage; }
    public static void requireRecoveryEpoch(String epoch, boolean confirmed) throws IOException {
        if (epoch == null || epoch.isEmpty()) {
            if (confirmed) throw new IOException("Physical recovery confirmation requires an epoch");
            return;
        }
        if (!confirmed || !epoch.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                || "00000000-0000-0000-0000-000000000000".equals(epoch))
            throw new IOException("Invalid or unconfirmed physical recovery epoch");
    }
    public synchronized void consume(String attachment) throws IOException { consume(attachment, "", false); }
    public synchronized void consume(String attachment, String recoveryEpoch, boolean confirmed) throws IOException {
        if (attachment == null || !attachment.matches("[a-f0-9]{64}")) throw new IOException("Invalid attachment digest");
        requireRecoveryEpoch(recoveryEpoch, confirmed);
        String history = storage.read(); Set<String> used = new HashSet<>();
        if (!history.isEmpty()) {
            if (!history.endsWith("\n")) throw new IOException("Truncated attachment ledger");
            for (String record : history.split("\n")) {
                if (!record.matches("FIRMWARE_CONSUMED [a-f0-9]{64}") || !used.add(record.substring(18)))
                    throw new IOException("Corrupt attachment ledger");
            }
        }
        if (recoveryEpoch == null || recoveryEpoch.isEmpty()) {
            if (used.contains(attachment)) throw new AlreadyConsumed();
            storage.write(history + "FIRMWARE_CONSUMED " + attachment + "\n");
            return;
        }
        String epochKey = Tbs5530AutonomousPlans.hash("TBS5530-PHYSICAL-RECOVERY-EPOCH/v1|" + recoveryEpoch);
        String attachmentKey = Tbs5530AutonomousPlans.hash("TBS5530-PHYSICAL-RECOVERY-ATTACHMENT/v1|" + attachment + "|" + recoveryEpoch);
        if (used.contains(epochKey) || used.contains(attachmentKey)) throw new AlreadyConsumed();
        // One durable append binds this attachment and consumes the operator's epoch across sessions/boots.
        String legacyMarker = used.contains(attachment) ? "" : "FIRMWARE_CONSUMED " + attachment + "\n";
        storage.write(history + legacyMarker + "FIRMWARE_CONSUMED " + epochKey + "\nFIRMWARE_CONSUMED " + attachmentKey + "\n");
    }
}
