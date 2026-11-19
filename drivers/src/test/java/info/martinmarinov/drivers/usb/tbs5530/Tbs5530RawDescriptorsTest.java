// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530RawDescriptorsTest {
    static byte[] device() { return new byte[]{18,1,0,2,(byte)255,(byte)255,(byte)255,64,0x4c,0x73,0x30,0x55,0,1,1,2,3,1}; }
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length]; for (int i=0;i<values.length;i++) out[i]=(byte)values[i]; return out;
    }
    static byte[] tree() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[][] parts = {device(), bytes(9,2,48,0,1,1,0,0x80,50), bytes(9,4,0,0,0,255,255,255,0),
                bytes(9,4,0,1,3,255,255,255,0), bytes(7,5,2,2,0,2,0),
                bytes(7,5,0x81,2,0,2,0), bytes(7,5,0x88,2,0,2,0)};
        for(byte[] part:parts) out.write(part,0,part.length); return out.toByteArray();
    }
    @Test public void alternateCountIsNotInterfaceCountAndDirectionsRemainDistinct() {
        Tbs5530RawDescriptors.Result result=Tbs5530RawDescriptors.parse(tree());
        assertEquals("COMPLETE_RETURNED_TREE",result.status); assertTrue(result.issues.isEmpty());
        assertEquals(0x200,(int)result.entries.get(0).fields.get("bcdUSB"));
        assertEquals(0x100,(int)result.entries.get(0).fields.get("bcdDevice"));
        assertEquals(1,(int)result.entries.get(1).fields.get("bNumInterfaces"));
        assertEquals(0,(int)result.entries.get(2).fields.get("alternate"));
        assertEquals(1,(int)result.entries.get(3).fields.get("alternate"));
        assertEquals(2,(int)result.entries.get(4).fields.get("address"));
        assertEquals(0,(int)result.entries.get(4).fields.get("direction"));
        assertEquals(128,(int)result.entries.get(6).fields.get("direction"));
        assertFalse(result.entries.stream().anyMatch(e -> Integer.valueOf(0x82).equals(e.fields.get("address"))));
        assertEquals(64,result.sha256.length());
    }
    @Test public void nullEmptyAndDeviceOnlyDoNotInventConfigurationTree() {
        assertEquals("NULL",Tbs5530RawDescriptors.parse(null).status);
        assertEquals("EMPTY",Tbs5530RawDescriptors.parse(new byte[0]).status);
        Tbs5530RawDescriptors.Result result=Tbs5530RawDescriptors.parse(device());
        assertEquals("PARTIAL_OR_INCONSISTENT",result.status);
        assertTrue(result.issues.contains("CONFIGURATION_TREE_NOT_RETURNED"));
    }
    @Test(timeout=1000) public void zeroAndOneLengthsCannotLoop() {
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(bytes(0,1)).status);
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(bytes(1,1)).status);
    }
    @Test public void malformedTotalLengthAndDescriptorBoundsAreRejected() {
        byte[] data=tree(); data[20]=8;
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(data).status);
        data=tree(); data[20]=47;
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(data).status);
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(bytes(2,1)).status);
    }
    @Test public void truncationAtEveryBoundaryAndMaximumUnsignedTotalAreSafe() {
        byte[] data=tree();
        for(int end=1;end<data.length;end++) {
            Tbs5530RawDescriptors.Result result=Tbs5530RawDescriptors.parse(Arrays.copyOf(data,end));
            assertNotEquals("COMPLETE_RETURNED_TREE",result.status);
        }
        data[20]=(byte)255;data[21]=(byte)255;
        assertEquals("TRUNCATED",Tbs5530RawDescriptors.parse(data).status);
        assertEquals("TOO_LARGE",Tbs5530RawDescriptors.parse(new byte[Tbs5530RawDescriptors.MAX_BYTES+1]).status);
    }
    @Test public void stringsAndUnknownPayloadNeverEscapeAndOutputIsImmutable() {
        byte[] data=bytes(10,3,'s','e','c','r','e','t',0,0);
        Tbs5530RawDescriptors.Result result=Tbs5530RawDescriptors.parse(data);
        assertEquals(3,result.entries.get(0).fields.size());
        try { result.entries.get(0).fields.put("serial",123); fail(); } catch(UnsupportedOperationException expected) { }
        try { result.entries.clear(); fail(); } catch(UnsupportedOperationException expected) { }
    }
    @Test public void duplicateAlternatesEndpointsAndWrongCountsAreReported() {
        byte[] data=tree(); data[39]=0;
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(data).status);
        data=tree();data[61]= (byte)0x81;
        assertEquals("MALFORMED",Tbs5530RawDescriptors.parse(data).status);
        data=tree();data[40]=4;
        assertTrue(Tbs5530RawDescriptors.parse(data).issues.contains("ENDPOINT_COUNT_MISMATCH"));
    }
}
