// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Offline plans only: no Android services, USB, firmware or physical attachment. */
public class Tbs5530FrontendExperimentTest {
    @Test public void offKeepsAllFourProductionFingerprints() throws Exception {
        assertEquals(Tbs5530FrontendExperiment.BASELINE_AUTO, Tbs5530FrontendExperiment.parse(null));
        assertEquals(Tbs5530AutonomousPlans.dryRun(), Tbs5530AutonomousPlans.dryRun(Tbs5530FrontendExperiment.BASELINE_AUTO));
        Tbs5530AutonomousPlans off = new Tbs5530AutonomousPlans();
        for (Tbs5530Frontend.Plan plan : plans(Tbs5530FrontendExperiment.BASELINE_AUTO).values()) off.require(plan);
        assertThrows(IOException.class, () -> Tbs5530FrontendExperiment.parse("TS_DRIVE_55_55"));
        assertThrows(IOException.class, () -> Tbs5530FrontendExperiment.parse("MANUAL_PLP1_K36"));
        assertThrows(IOException.class, () -> Tbs5530FrontendExperiment.parse(""));
    }
    @Test public void manualChangesOnlyAfAdWithOneAdditionalOutAndNoExtraRead() throws Exception {
        Map<Tbs5530Frontend.Operation,Tbs5530Frontend.Plan> off = plans(Tbs5530FrontendExperiment.BASELINE_AUTO);
        Map<Tbs5530Frontend.Operation,Tbs5530Frontend.Plan> on = plans(Tbs5530FrontendExperiment.MANUAL_PLP0_K36);
        for (Tbs5530Frontend.Operation operation : off.keySet()) {
            Tbs5530Frontend.Plan a = off.get(operation), b = on.get(operation);
            if (operation != Tbs5530Frontend.Operation.TUNE) {
                assertEquals(a.getApprovalTranscript(), b.getApprovalTranscript()); continue;
            }
            assertEquals(a.usbOutCount + 1, b.usbOutCount); assertEquals(a.usbInCount, b.usbInCount);
            assertEquals(a.delayMillis, b.delayMillis); assertEquals(a.maximumMillis + 2000, b.maximumMillis);
            List<Tbs5530Frontend.RegisterTransaction> aa = a.getTransactions(), bb = b.getTransactions();
            assertEquals(aa.size() + 1, bb.size());
            assertWrite(bb.get(1), 0x64, 0x23, 0xaf, 0);
            assertWrite(bb.get(2), 0x64, 0x23, 0xad, 1);
            assertWrite(aa.get(1), 0x64, 0x23, 0xad, 0);
            for (int i = 0; i < aa.size(); i++) {
                Tbs5530Frontend.RegisterTransaction x = aa.get(i), y = bb.get(i < 1 ? i : i + 1);
                assertEquals(x.kind, y.kind); assertEquals(x.address, y.address); assertEquals(x.bank, y.bank);
                assertEquals(x.register, y.register); assertEquals(x.readLength, y.readLength);
                assertEquals(x.delayMillis, y.delayMillis); assertEquals(x.sourceMask, y.sourceMask);
                assertEquals(x.shift, y.shift); assertEquals(x.value, y.value);
                assertEquals(x.sourceStep, y.sourceStep < 1 ? y.sourceStep : y.sourceStep - 1);
                assertEquals(x.secondSourceStep, y.secondSourceStep < 1 ? y.secondSourceStep : y.secondSourceStep - 1);
                if (i != 1) assertArrayEquals(x.getData(), y.getData());
            }
        }
        System.out.println("MANUAL_PLP0_TUNE=" + Tbs5530AutonomousPlans.canonical(on.get(Tbs5530Frontend.Operation.TUNE)));
    }
    @Test public void manualRejectsEveryCombinedTransportOrObservationOption() throws Exception {
        Tbs5530FrontendExperiment e = Tbs5530FrontendExperiment.MANUAL_PLP0_K36;
        e.requireOptions("NATIVE_USBFS_URB_8X4096", true, false, false, false);
        for (String api : Arrays.asList("SYNC_NATIVE_ERRNO", "QUEUED_USB_REQUEST_8X4096", "NATIVE_USBFS_URB_8X8192"))
            assertThrows(IOException.class, () -> e.requireOptions(api, true, false, false, false));
        assertThrows(IOException.class, () -> e.requireOptions("NATIVE_USBFS_URB_8X4096", false, false, false, false));
        for (int i = 0; i < 3; i++) {
            final int option = i;
            assertThrows(IOException.class, () -> e.requireOptions("NATIVE_USBFS_URB_8X4096", true, option == 0, option == 1, option == 2));
        }
        Tbs5530AutonomousPlans manual = new Tbs5530AutonomousPlans(e);
        for (Tbs5530Frontend.Plan p : plans(e).values()) manual.require(p);
        assertThrows(IOException.class, () -> manual.require(plans(Tbs5530FrontendExperiment.BASELINE_AUTO).get(Tbs5530Frontend.Operation.TUNE)));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousPlans().require(plans(e).get(Tbs5530Frontend.Operation.TUNE)));
        assertTrue(e.matches(e.parameters())); assertFalse(e.matches(Tbs5530FrontendExperiment.BASELINE_AUTO.parameters()));
        assertFalse(e.matches(new Tbs5530DvbT2TuneParameters(594000000L,8000000,1)));
        assertFalse(e.matches(new Tbs5530DvbT2TuneParameters(602000000L,8000000,0)));
    }
    @Test public void manualKeepsDrive54AndDoesNotAddAnyMonitor() throws Exception {
        Tbs5530Frontend.Plan init = plans(Tbs5530FrontendExperiment.MANUAL_PLP0_K36).get(Tbs5530Frontend.Operation.INITIALIZE);
        int drive = 0;
        for (Tbs5530Frontend.RegisterTransaction t : init.getTransactions()) {
            if (t.kind == Tbs5530Frontend.Kind.WRITE && t.address == 0x64 && t.bank == 0 && (t.register == 0x96 || t.register == 0x97)) {
                assertArrayEquals(new byte[]{0x54}, t.getData()); drive++;
            }
        }
        assertEquals(2,drive);
        assertEquals(Tbs5530AutonomousPlans.dryRun().get(Tbs5530Frontend.Operation.POLL_STATUS),
                Tbs5530AutonomousPlans.dryRun(Tbs5530FrontendExperiment.MANUAL_PLP0_K36).get(Tbs5530Frontend.Operation.POLL_STATUS));
    }
    @Test public void k25GoldenDiffContainsOnlyFrequencyParameters() throws Exception {
        Map<Tbs5530Frontend.Operation,Tbs5530Frontend.Plan> k36 = plans(Tbs5530FrontendExperiment.B5_K36_MANUAL_PLP0);
        Map<Tbs5530Frontend.Operation,Tbs5530Frontend.Plan> k25 = plans(Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0);
        for (Tbs5530Frontend.Operation operation : k36.keySet()) {
            Tbs5530Frontend.Plan a = k36.get(operation), b = k25.get(operation);
            if (operation != Tbs5530Frontend.Operation.TUNE) {
                assertEquals(a.getApprovalTranscript(), b.getApprovalTranscript()); continue;
            }
            assertEquals(a.usbOutCount, b.usbOutCount); assertEquals(a.usbInCount, b.usbInCount);
            assertEquals(a.delayMillis, b.delayMillis); assertEquals(a.maximumMillis, b.maximumMillis);
            List<Tbs5530Frontend.RegisterTransaction> aa = a.getTransactions(), bb = b.getTransactions();
            assertEquals(aa.size(), bb.size()); int differences = 0;
            for (int i = 0; i < aa.size(); i++) {
                Tbs5530Frontend.RegisterTransaction x = aa.get(i), y = bb.get(i);
                assertEquals(x.kind, y.kind); assertEquals(x.address, y.address); assertEquals(x.bank, y.bank);
                assertEquals(x.register, y.register); assertEquals(x.readLength, y.readLength);
                assertEquals(x.delayMillis, y.delayMillis); assertEquals(x.sourceMask, y.sourceMask);
                assertEquals(x.shift, y.shift); assertEquals(x.value, y.value);
                assertEquals(x.sourceStep, y.sourceStep); assertEquals(x.secondSourceStep, y.secondSourceStep);
                byte[] xd = x.getData(), yd = y.getData();
                if (!Arrays.equals(xd, yd)) {
                    assertEquals(Tbs5530Frontend.Kind.WRITE, x.kind); assertEquals(0x60, x.address);
                    assertEquals(0x68, x.register); assertEquals(17, xd.length); assertEquals(xd.length, yd.length);
                    byte[] normalized = yd.clone();
                    // ASCOT3 frequency in kHz is the little-endian field at registers 0x70..0x72.
                    assertArrayEquals(new byte[]{0x50, 0x10, 0x09}, Arrays.copyOfRange(xd, 8, 11));
                    assertArrayEquals(new byte[]{(byte)0x90, (byte)0xb8, 0x07}, Arrays.copyOfRange(yd, 8, 11));
                    System.arraycopy(xd, 8, normalized, 8, 3); assertArrayEquals(xd, normalized); differences++;
                }
            }
            assertEquals(1, differences);
        }
        for (Tbs5530FrontendExperiment e : new Tbs5530FrontendExperiment[]{Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0,
                Tbs5530FrontendExperiment.B5_K25_AUTO, Tbs5530FrontendExperiment.B5_K36_MANUAL_PLP0})
            System.out.println(e + "_TUNE=" + Tbs5530AutonomousPlans.canonical(plans(e).get(Tbs5530Frontend.Operation.TUNE)));
    }
    @Test public void fallbackIsRetuneNotColdAutoProgram() throws Exception {
        Tbs5530Frontend.Plan fallback = Tbs5530AutonomousPlans.dryRunK25FallbackPlan();
        Tbs5530Frontend.Plan cold = plans(Tbs5530FrontendExperiment.B5_K25_AUTO).get(Tbs5530Frontend.Operation.TUNE);
        assertTrue(Tbs5530FrontendExperiment.B5_K25_AUTO.matches(fallback.tuneParameters));
        assertNotEquals(Tbs5530AutonomousPlans.canonical(cold), Tbs5530AutonomousPlans.canonical(fallback));
        assertTrue(fallback.usbOutCount < cold.usbOutCount);
        Tbs5530AutonomousPlans manual = new Tbs5530AutonomousPlans(Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0);
        manual.requireK25Fallback(fallback);
        assertThrows(IOException.class, () -> manual.requireK25Fallback(cold));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousPlans(Tbs5530FrontendExperiment.B5_K36_MANUAL_PLP0)
                .requireK25Fallback(fallback));
        System.out.println("B5_K25_AUTO_FALLBACK=" + Tbs5530AutonomousPlans.canonical(fallback));
    }
    @Test public void b5RejectsUnreviewedFrequencyPlpAndTransport() throws Exception {
        for (Tbs5530FrontendExperiment e : new Tbs5530FrontendExperiment[]{Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0,
                Tbs5530FrontendExperiment.B5_K25_AUTO, Tbs5530FrontendExperiment.B5_K36_MANUAL_PLP0}) {
            assertTrue(e.matches(e.parameters()));
            Tbs5530AutonomousPlans allowlist = new Tbs5530AutonomousPlans(e);
            for (Tbs5530Frontend.Plan p : plans(e).values()) allowlist.require(p);
            Tbs5530FrontendExperiment other = e == Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0
                    ? Tbs5530FrontendExperiment.B5_K36_MANUAL_PLP0 : Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0;
            assertThrows(IOException.class, () -> allowlist.require(plans(other).get(Tbs5530Frontend.Operation.TUNE)));
            e.requireOptions("NATIVE_USBFS_STREAM", true, false, false, false);
            assertThrows(IOException.class, () -> e.requireOptions("NATIVE_USBFS_URB_8X4096", true, false, false, false));
            assertFalse(e.matches(new Tbs5530DvbT2TuneParameters(514000000L, 8000000, 0)));
            assertFalse(e.matches(new Tbs5530DvbT2TuneParameters(e.frequencyHz(), 8000000, 1)));
        }
    }
    @Test public void exportDeterministicPlansWhenRequested() throws Exception {
        String destination = System.getenv("TBS5530_PLAN_OUTPUT");
        if (destination == null) return;
        Path root = Paths.get(destination); Files.createDirectories(root);
        Tbs5530Frontend.Plan fallback = Tbs5530AutonomousPlans.dryRunK25FallbackPlan();
        new Tbs5530AutonomousPlans(Tbs5530FrontendExperiment.B5_K25_MANUAL_PLP0).requireK25Fallback(fallback);
        Files.write(root.resolve("B5_K25_AUTO_FALLBACK-TUNE.txt"), fallback.getApprovalTranscript().getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("B5_K25_AUTO_FALLBACK-TUNE.sha256"),
                (fallback.getSha256() + "\ncanonical=" + Tbs5530AutonomousPlans.canonical(fallback) + "\n").getBytes(StandardCharsets.UTF_8));
        for (Tbs5530FrontendExperiment e : Tbs5530FrontendExperiment.values()) {
            Files.write(root.resolve(e.name() + "-golden.sha256"),
                    (new Tbs5530AutonomousPlans(e).fingerprint() + "\n").getBytes(StandardCharsets.UTF_8));
            for (Tbs5530Frontend.Plan p : plans(e).values()) {
                String name = e.name() + "-" + p.operation;
                Files.write(root.resolve(name + ".txt"), p.getApprovalTranscript().getBytes(StandardCharsets.UTF_8));
                Files.write(root.resolve(name + ".sha256"), (p.getSha256() + "\ncanonical=" + Tbs5530AutonomousPlans.canonical(p) + "\n").getBytes(StandardCharsets.UTF_8));
                List<String> rows = new ArrayList<>(); rows.add("ordinal\tstep\ttype\trequest\tpayload_or_read\tprovenance");
                int ordinal = 0, step = 0;
                for (Tbs5530Frontend.RegisterTransaction t : p.getTransactions()) {
                    if (t.kind == Tbs5530Frontend.Kind.WRITE)
                        rows.add(++ordinal + "\t" + step + "\t40\t80\t" + (t.isReadDerivedWrite()
                                ? "RMW(readStep=" + t.sourceStep + ",mask=" + t.sourceMask + ",shift=" + t.shift + ",or=" + t.value + ")"
                                : hex(t.usbWritePayload(0))) + "\t" + t.provenance);
                    else if (t.kind == Tbs5530Frontend.Kind.READ) {
                        rows.add(++ordinal + "\t" + step + "\t40\t90\t" + hex(t.usbReadPointerPayload()) + "\t" + t.provenance);
                        rows.add(++ordinal + "\t" + step + "\tc0\t91\tREAD:" + t.readLength + "\t" + t.provenance);
                    }
                    step++;
                }
                assertEquals(p.usbOutCount + p.usbInCount, ordinal);
                Files.write(root.resolve(name + "-ordinals.tsv"), rows, StandardCharsets.UTF_8);
            }
        }
    }
    private static Map<Tbs5530Frontend.Operation,Tbs5530Frontend.Plan> plans(Tbs5530FrontendExperiment e) throws IOException {
        return Tbs5530AutonomousPlans.dryRunPlans(e);
    }
    private static void assertWrite(Tbs5530Frontend.RegisterTransaction t,int address,int bank,int register,int value) {
        assertEquals(Tbs5530Frontend.Kind.WRITE,t.kind);assertEquals(address,t.address);assertEquals(bank,t.bank);
        assertEquals(register,t.register);assertArrayEquals(new byte[]{(byte)value},t.getData());
    }
    private static String hex(byte[] data) {
        StringBuilder text = new StringBuilder(); for (byte b : data) text.append(String.format("%02x",b & 255)); return text.toString();
    }
}
