// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Assume;
import org.junit.Test;
import java.io.FileInputStream;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Optional private Windows RF fixture, never bundled in CI or mistaken for Android reception. */
public class Tbs5530WindowsTsFixtureTest {
    @Test public void cold2ContainsRealProgramStructureButHasTransportErrors() throws Exception {
        String path = System.getenv("TBS5530_WINDOWS_TS_FIXTURE");
        Assume.assumeTrue("Private Windows capture not configured", path != null);
        Tbs5530TsParser parser = new Tbs5530TsParser();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) { digest.update(chunk,0,count); parser.accept(chunk,0,count); }
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
        assertEquals("11a6a1c098fdf8adae94b8aea71d60162f236b2993fc9169295ec79dd97f1132",hash.toString());
        Tbs5530TsParser.Snapshot s = parser.snapshot();
        assertEquals(30897800L,s.bytesReceived); assertEquals(164350L,s.packets);
        assertEquals(3054L,s.transportErrorPackets); assertEquals(0L,s.syncLosses);
        assertEquals(0L,s.discardedBytes); assertEquals(0,s.bufferedBytes);
        long invalidBasicHeaders = countInvalidBasicHeaders(path);
        assertEquals(115L,invalidBasicHeaders);
        assertTrue("Reserved fields and invalid adaptation lengths must not be reported as clean", s.malformedPackets >= invalidBasicHeaders);
        assertTrue(s.hasValidProgramStructure()); assertTrue(s.validPatSections > 0); assertTrue(s.validPmtSections > 0);
        assertEquals(14,s.programCount); assertEquals(14,s.programsWithPmt);
        System.out.println("WINDOWS_BDA_ONLY bytes="+s.bytesReceived+" packets="+s.packets+" TEI="+s.transportErrorPackets
                +" continuity="+s.continuityErrors+" PAT="+s.validPatSections+" PMT="+s.validPmtSections
                +" psiCRC="+s.psiCrcErrors+" PIDs="+s.uniquePidCount+" services="+s.serviceCount
                +" programs="+s.programCount+" withPMT="+s.programsWithPmt+" malformed="+s.malformedPackets
                +" Android_TS_SMOKE_PASS=NOT_TESTED");
    }
    private static long countInvalidBasicHeaders(String path) throws Exception {
        long count = 0;
        try (java.io.DataInputStream input = new java.io.DataInputStream(new FileInputStream(path))) {
            byte[] packet = new byte[188];
            for (int i = 0; i < 164350; i++) {
                input.readFully(packet);
                int control = packet[3] & 255, afc = (control >>> 4) & 3, length = packet[4] & 255;
                if ((control & 0xc0) == 0x40 || afc == 0 || afc == 2 && length != 183 || afc == 3 && length > 182) count++;
            }
            assertEquals(-1,input.read());
        }
        return count;
    }
}
