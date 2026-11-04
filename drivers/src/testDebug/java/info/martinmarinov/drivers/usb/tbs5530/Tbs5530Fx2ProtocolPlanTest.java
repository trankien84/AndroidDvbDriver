// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class Tbs5530Fx2ProtocolPlanTest {
    @Test public void exactAllowlistRejectsUnknownNullAndNormalizedIds() throws Exception {
        Tbs5530FirmwarePlan source = Tbs5530BootstrapDryRunTest.plan(new byte[64]);
        for (String id : new String[]{null, "", "DOCUMENTED_FX2_E600_ONLY ", "documented_fx2_e600_only", "AUTO"}) {
            try { Tbs5530Fx2ProtocolPlan.describe(source, id); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        try { Tbs5530Fx2ProtocolPlan.describe(null, "TBS_SOURCE_DUAL_RESET"); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void comparatorKeepsSourceHistoryAndRejectsLegacyRegisterInFx2Plan() throws Exception {
        byte[] image = Tbs5530BootstrapDryRunTest.image(8192);
        Tbs5530FirmwarePlan original = Tbs5530BootstrapDryRunTest.plan(image);
        Tbs5530Fx2ProtocolPlan dual = Tbs5530Fx2ProtocolPlan.describe(original, "TBS_SOURCE_DUAL_RESET");
        Tbs5530Fx2ProtocolPlan fx2 = Tbs5530Fx2ProtocolPlan.describe(original, "DOCUMENTED_FX2_E600_ONLY");
        assertEquals(133, dual.getSteps().size());
        assertEquals(131, fx2.getSteps().size());
        assertEquals(original.getSteps(), dual.getSteps());
        assertNotEquals(dual.getProvenance(), fx2.getProvenance());
        assertTrue(dual.getProvenance().contains("2ce787de"));
        assertTrue(fx2.getProvenance().contains("TRM"));
        int bytes = 0;
        for (int i = 0; i < fx2.getSteps().size(); i++) {
            Tbs5530FirmwarePlan.Step step = fx2.getSteps().get(i);
            assertNotEquals(0x7f92, step.getValue());
            if (i > 0 && i < 129) {
                int offset = (i - 1) * 64;
                assertEquals(offset, step.getValue());
                assertArrayEquals(Arrays.copyOfRange(image, offset, offset + 64), step.getPayload());
                bytes += step.getPayload().length;
            }
        }
        assertEquals(8192, bytes);
        assertEquals(0xe600, fx2.getSteps().get(0).getValue());
        assertArrayEquals(new byte[]{1}, fx2.getSteps().get(0).getPayload());
        assertEquals(0x1fc0, fx2.getSteps().get(128).getValue());
        assertEquals(0xe600, fx2.getSteps().get(129).getValue());
        assertArrayEquals(new byte[]{0}, fx2.getSteps().get(129).getPayload());
        assertEquals(Tbs5530FirmwarePlan.Kind.DELAY, fx2.getSteps().get(130).getKind());
        assertFalse(Tbs5530FirmwareValidation.hardwareUploadAllowed());
        try { fx2.getSteps().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void partialTailIsNeverPaddedAndFx2DataCannotCrossMainRam() throws Exception {
        for (int size : new int[]{1, 63, 64, 65, 8191, 8192}) {
            byte[] image = Tbs5530BootstrapDryRunTest.image(size);
            Tbs5530Fx2ProtocolPlan plan = Tbs5530Fx2ProtocolPlan.describe(
                    Tbs5530BootstrapDryRunTest.plan(image), "DOCUMENTED_FX2_E600_ONLY");
            int last = (size - 1) / 64 + 1;
            assertEquals((size - 1) / 64 * 64, plan.getSteps().get(last).getValue());
            assertEquals((size - 1) % 64 + 1, plan.getSteps().get(last).getPayload().length);
        }
        byte[] oversized = new byte[8193];
        Tbs5530FirmwarePlan source = Tbs5530FirmwarePlan.create(oversized,
                new Tbs5530FirmwarePlan.Policy(java.security.MessageDigest.getInstance("SHA-256").digest(oversized),
                        Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, 8193)),
                        Collections.emptyList(), 0, 8193));
        for (Tbs5530Fx2ProtocolPlan.Id id : Tbs5530Fx2ProtocolPlan.Id.values()) {
            try { Tbs5530Fx2ProtocolPlan.describe(source, id.name()); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
