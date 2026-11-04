// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class Tbs5530ResetTranscriptTest {
    private static final String HEADER = "frame.number\tframe.len\tframe.cap_len\tusb.bus_id\tusb.device_address"
            + "\tusb.irp_id\tusb.irp_info\tusb.control_stage\tusb.bmRequestType\tusb.setup.bRequest"
            + "\tusb.setup.wValue\tusb.setup.wIndex\tusb.setup.wLength\tusb.usbd_status\tusb.data_fragment\tusb.request_in\n";
    private static String hash() throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(new byte[8192])) text.append(String.format(Locale.ROOT,"%02x",b & 255));
        return text.toString();
    }
    private static String transcript(int attempts) {
        StringBuilder tsv = new StringBuilder(HEADER);
        for (int attempt = 0; attempt < attempts; attempt++) {
            for (int i = 0; i < 132; i++) {
                int frame = attempt * 264 + i * 2 + 1;
                int address = i == 0 || i == 130 ? 0x7f92 : i == 1 || i == 131 ? 0xe600 : (i - 2) * 64;
                boolean reset = i < 2 || i > 129;
                String payload = reset ? i < 2 ? "01" : "00" : zeros(128);
                int length = reset ? 1 : 64;
                tsv.append(frame).append("\t100\t100\t3\t4\tsame-irp\t0x00\t0\t0x40\t160\t")
                        .append(address).append("\t0\t").append(length).append("\t0x00000000\t").append(payload).append("\t\n");
                tsv.append(frame + 1).append("\t28\t28\t3\t4\tsame-irp\t0x01\t3\t\t\t\t\t\t0x00000000\t\t")
                        .append(frame).append('\n');
            }
        }
        return tsv.toString();
    }
    private static String zeros(int n) { StringBuilder s = new StringBuilder(); for (int i = 0; i < n; i++) s.append('0'); return s.toString(); }
    private static void rejects(String tsv) throws Exception {
        try { Tbs5530ResetTranscript.parse(tsv, hash()); fail(); } catch (IOException expected) { }
    }
    @Test public void pairsReusedIrpIdsByFrameWithoutLeakingPayloads() throws Exception {
        List<Tbs5530ResetTranscript.Attempt> parsed = Tbs5530ResetTranscript.parse(transcript(2), hash());
        assertEquals(2, parsed.size());
        for (Tbs5530ResetTranscript.Attempt attempt : parsed) {
            assertEquals(132, attempt.writes); assertEquals(8192, attempt.imageBytes);
            assertEquals(hash(), attempt.imageSha256); assertTrue(attempt.allCompletionsSuccessful);
            assertEquals(4, attempt.resets.size()); assertEquals(0x7f92, attempt.resets.get(0).address);
            assertEquals(1, attempt.resets.get(0).value); assertEquals(0, attempt.resets.get(3).value);
        }
        try { parsed.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void completionFailureIsRetainedNotCountedAsSuccess() throws Exception {
        String bad = transcript(1).replace("2\t28\t28\t3\t4\tsame-irp\t0x01\t3\t\t\t\t\t\t0x00000000",
                "2\t28\t28\t3\t4\tsame-irp\t0x01\t3\t\t\t\t\t\t0xc0000004");
        Tbs5530ResetTranscript.Attempt parsed = Tbs5530ResetTranscript.parse(bad, hash()).get(0);
        assertFalse(parsed.allCompletionsSuccessful); assertEquals(0xc0000004L, parsed.resets.get(0).completionStatus);
    }
    @Test public void rejectsMissingCompletionTruncatedFrameAndWrongFrameCorrelation() throws Exception {
        String tsv = transcript(1);
        rejects(tsv.substring(0, tsv.lastIndexOf("264\t")));
        rejects(tsv.replaceFirst("100\t100", "100\t99"));
        rejects(tsv.replace("\t0x00000000\t\t1\n", "\t0x00000000\t\t99\n"));
        rejects(HEADER);
    }
    @Test public void rejectsHashMismatchReorderedAddressAndBadLength() throws Exception {
        try { Tbs5530ResetTranscript.parse(transcript(1), zeros(64)); fail(); } catch (IOException expected) { }
        rejects(transcript(1).replace("\t64\t0\t64\t", "\t65\t0\t64\t"));
        rejects(transcript(1).replace("\t32658\t0\t1\t", "\t32658\t0\t2\t"));
    }
    @Test public void rejectsMixedFirmwareDevicesAndMalformedRows() throws Exception {
        rejects(transcript(2).replace("265\t100\t100\t3\t4", "265\t100\t100\t3\t5")
                .replace("266\t28\t28\t3\t4", "266\t28\t28\t3\t5"));
        rejects(transcript(1).replaceFirst("\t0x40\t160\t", "\t0x40\t160\textra\t"));
        try { Tbs5530ResetTranscript.parse(null, hash()); fail(); } catch (IOException expected) { }
    }
}
