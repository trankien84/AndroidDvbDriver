// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530OperationalDescriptorTest {
    // Standard numeric fields reconstructed from the observed post-upload report; no strings or firmware.
    private static byte[] observedProfile() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,
                7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    @Test public void bulkInIntervalIsNotAPollingIntervalOrReadinessGate() {
        for (int interval=0;interval<=255;interval++) {
            byte[] bytes=observedProfile(); bytes[42]=(byte)interval;
            Tbs5530RawDescriptors.Result raw=Tbs5530RawDescriptors.parse(bytes);
            assertEquals("COMPLETE_RETURNED_TREE",raw.status); assertTrue(raw.issues.isEmpty());
            assertEquals(interval,raw.entries.get(3).fields.get("interval").intValue());
            assertEquals(Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE,Tbs5530Fx2Fingerprint.assess(raw).candidate);
        }
    }
    @Test public void intervalDoesNotRelaxTypeDirectionPacketOrInterfaceChecks() {
        for (int offset:new int[]{38,39,40,41}) {
            byte[] bytes=observedProfile(); bytes[offset]^=1;
            assertNotEquals(Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE,
                    Tbs5530Fx2Fingerprint.assess(Tbs5530RawDescriptors.parse(bytes)).candidate);
        }
        byte[] alternate=observedProfile(); alternate[30]=1;
        assertNotEquals(Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE,
                Tbs5530Fx2Fingerprint.assess(Tbs5530RawDescriptors.parse(alternate)).candidate);
    }
}
