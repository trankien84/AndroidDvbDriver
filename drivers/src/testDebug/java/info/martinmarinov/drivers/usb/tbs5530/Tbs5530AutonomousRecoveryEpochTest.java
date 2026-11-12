// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/** Operator-confirmed physical events, not automatically generated attempt/session identities. */
public class Tbs5530AutonomousRecoveryEpochTest {
    private static final String EPOCH_A = "11111111-1111-4111-8111-111111111111";
    private static final String EPOCH_B = "22222222-2222-4222-8222-222222222222";
    private static final String ATTACHMENT = Tbs5530AutonomousPlans.hash("boot17:" + Tbs5530AutonomousPlans.hash("/dev/bus/usb/001/002:2"));
    private static final class Store implements Tbs5530AutonomousAttachmentLedger.Storage {
        String text = ""; int writes;
        public String read() { return text; }
        public void write(String value) { text = value; writes++; }
        Tbs5530AutonomousAttachmentLedger ledger() { return new Tbs5530AutonomousAttachmentLedger(this); }
    }
    @Test public void samePathIdBootAndSameEpochCannotReconsumeAcrossSessionRestart() throws Exception {
        Store store = new Store(); store.ledger().consume(ATTACHMENT);
        String legacy = store.text;
        store.ledger().consume(ATTACHMENT, EPOCH_A, true);
        String consumed = store.text;
        assertTrue(consumed.startsWith(legacy));
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class,
                () -> store.ledger().consume(ATTACHMENT, EPOCH_A, true));
        assertEquals(consumed, store.text);
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class, () -> store.ledger().consume(ATTACHMENT));
    }
    @Test public void newExplicitConfirmedPhysicalEpochAllowsExactlyOneUploadWithoutRewritingHistory() throws Exception {
        Store store = new Store(); store.ledger().consume(ATTACHMENT, EPOCH_A, true);
        String first = store.text;
        store.ledger().consume(ATTACHMENT, EPOCH_B, true);
        assertTrue(store.text.startsWith(first));
        String second = store.text;
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class,
                () -> store.ledger().consume(ATTACHMENT, EPOCH_B, true));
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class,
                () -> store.ledger().consume(ATTACHMENT, EPOCH_A, true));
        assertEquals(second, store.text);
    }
    @Test public void epochIsSingleUseEvenIfAndroidChangesPathOrBoot() throws Exception {
        Store store = new Store(); store.ledger().consume(ATTACHMENT, EPOCH_A, true);
        String before = store.text;
        String anotherAttachment = Tbs5530AutonomousPlans.hash("boot18:different-path-id");
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class,
                () -> store.ledger().consume(anotherAttachment, EPOCH_A, true));
        assertEquals(before, store.text);
    }
    @Test public void missingMalformedUnconfirmedOrZeroEpochNeverRearmsOrWrites() throws Exception {
        Store store = new Store(); store.ledger().consume(ATTACHMENT); String before = store.text; int writes = store.writes;
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class, () -> store.ledger().consume(ATTACHMENT, "", false));
        for (String epoch :         new String[]{null, "", "bad", "00000000-0000-0000-0000-000000000000", "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"}) {
            assertThrows(IOException.class, () -> store.ledger().consume(ATTACHMENT, epoch, true));
        }
        assertThrows(IOException.class, () -> store.ledger().consume(ATTACHMENT, EPOCH_A, false));
        assertEquals(before, store.text); assertEquals(writes, store.writes);
    }
    @Test public void recoveryAlsoConsumesDefaultAttachmentKeyAndCannotBypassCorruptLedger() throws Exception {
        Store store = new Store(); store.ledger().consume(ATTACHMENT, EPOCH_A, true);
        assertTrue(store.text.startsWith("FIRMWARE_CONSUMED " + ATTACHMENT + "\n"));
        assertThrows(Tbs5530AutonomousAttachmentLedger.AlreadyConsumed.class, () -> store.ledger().consume(ATTACHMENT));
        store.text += "partial"; String corrupt = store.text;
        assertThrows(IOException.class, () -> store.ledger().consume(ATTACHMENT, EPOCH_B, true));
        assertEquals(corrupt, store.text);
    }
}
