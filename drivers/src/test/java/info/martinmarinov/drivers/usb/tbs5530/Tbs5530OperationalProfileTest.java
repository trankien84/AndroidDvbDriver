// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530OperationalProfileTest {
    static byte[] operational() {
        return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,
                7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};
    }
    @Test public void strictProfileRequiresBothEndpointsNotOnlyDebugCandidate() {
        byte[] bytes = operational();
        assertEquals(Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE, Tbs5530OperationalProfile.observe(bytes).kind);
        bytes[45] = (byte)0x83;
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE,
                Tbs5530Fx2Fingerprint.assess(Tbs5530RawDescriptors.parse(bytes)).candidate);
        assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(bytes).kind);
    }
    @Test public void rawIdentityStructureDirectionAndPacketErrorsFailClosed() {
        for (int at : new int[]{8,10,17,22,23,29,30,31,32,38,39,40,41,45,46,47,48}) {
            byte[] bytes = operational(); bytes[at] ^= 1;
            assertEquals("offset " + at, Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(bytes).kind);
        }
        for (int size = 0; size < 50; size++) {
            assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(Arrays.copyOf(operational(), size)).kind);
        }
        byte[] duplicate = operational(); duplicate[45] = (byte)0x82;
        assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(duplicate).kind);
    }
    @Test public void bulkIntervalIsNotAPollingOrSpeedGate() {
        for (int interval = 0; interval < 256; interval++) {
            byte[] bytes = operational(); bytes[42] = (byte)interval; bytes[49] = (byte)(255-interval);
            Tbs5530OperationalProfile profile = Tbs5530OperationalProfile.observe(bytes);
            assertEquals(Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE, profile.kind);
            assertEquals(512, profile.streamPacketSize);
        }
    }
    @Test public void defaultNeedsSeparateBootstrapWhileOperationalNeverRequestsIt() throws Exception {
        Tbs5530OperationalProfile cold = Tbs5530OperationalProfile.observe(Tbs5530Fx2FingerprintTest.bootstrap());
        assertEquals(Tbs5530OperationalProfile.Kind.FX2_DEFAULT, cold.kind);
        assertThrows(java.io.IOException.class, cold::requireOperational);
        Tbs5530OperationalProfile.observe(operational()).requireOperational();
        byte[] nearDefault = Tbs5530Fx2FingerprintTest.bootstrap(); nearDefault[51] = 2;
        assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(nearDefault).kind);
    }
    @Test public void noNullOrUnknownTreeCanAuthorizeFirmwareOrOperational() {
        assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(null).kind);
        assertEquals(Tbs5530OperationalProfile.Kind.UNKNOWN, Tbs5530OperationalProfile.observe(new byte[]{2,99}).kind);
    }
}
