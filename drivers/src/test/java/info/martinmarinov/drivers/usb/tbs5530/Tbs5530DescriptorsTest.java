// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class Tbs5530DescriptorsTest {
    private static Tbs5530Descriptors.Endpoint endpoint(int address, int type, int packetSize) {
        return new Tbs5530Descriptors.Endpoint(0, 0, address, address & 0x80, type, packetSize);
    }

    private static void rejects(Runnable action) {
        try { action.run(); fail("Invalid descriptors accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void selects82RatherThanFirstEndpointAndKeepsAllMetadata() {
        Tbs5530Descriptors.Endpoint stream = endpoint(0x82, 2, 512);
        Tbs5530Descriptors.Endpoint otherIn = endpoint(0x81, 2, 512);
        Tbs5530Descriptors.Endpoint otherOut = endpoint(0x02, 2, 64);
        for (List<Tbs5530Descriptors.Endpoint> order : Arrays.asList(
                Arrays.asList(otherIn, otherOut, stream), Arrays.asList(otherOut, otherIn, stream),
                Arrays.asList(otherIn, stream, otherOut), Arrays.asList(otherOut, stream, otherIn),
                Arrays.asList(stream, otherIn, otherOut), Arrays.asList(stream, otherOut, otherIn))) {
            assertSame(stream, Tbs5530Descriptors.selectBulkEndpoint(order, 0));
        }
        assertEquals(0, stream.interfaceId);
        assertEquals(0, stream.alternateSetting);
        assertEquals(0x82, stream.address);
        assertEquals(0x80, stream.direction);
        assertEquals(2, stream.transferType);
        assertEquals(512, stream.maxPacketSize);
    }

    @Test public void supportsExplicitNonzeroAlternateWithoutInferringSpeed() {
        Tbs5530Descriptors.Endpoint stream = new Tbs5530Descriptors.Endpoint(0, 1, 0x82, 0x80, 2, 64);
        assertSame(stream, Tbs5530Descriptors.selectBulkEndpoint(Collections.singletonList(stream), 1));
    }

    @Test public void acceptsReviewed64And512PacketSizes() {
        for (int size : new int[] {64, 512}) {
            Tbs5530Descriptors.Endpoint stream = endpoint(0x82, 2, size);
            assertSame(stream, Tbs5530Descriptors.selectBulkEndpoint(Collections.singletonList(stream), 0));
        }
    }

    @Test public void rejectsUnreviewedPacketSizesIncludingOtherwiseLegalUsbSizes() {
        for (int size : new int[] {1, 8, 16, 32, 63, 65, 256, 511, 513, 1024}) {
            rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Collections.singletonList(endpoint(0x82, 2, size)), 0));
        }
    }

    @Test public void rejectsMissing82WithActionableDiagnostic() {
        for (List<Tbs5530Descriptors.Endpoint> endpoints : Arrays.asList(
                Collections.<Tbs5530Descriptors.Endpoint>emptyList(),
                Collections.singletonList(endpoint(0x81, 2, 512)),
                Collections.singletonList(endpoint(0x02, 2, 64)),
                Collections.singletonList(endpoint(0x82, 3, 64)))) {
            try { Tbs5530Descriptors.selectBulkEndpoint(endpoints, 0); fail("Missing stream accepted"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("0x82")); }
        }
    }

    @Test public void onePlusObservedFourAlternatesDoNotProveTsProfileOrSelect88Instead() {
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Collections.emptyList(),0));
        int[] addresses={0x01,0x81,0x02,0x04,0x86,0x88};
        int[][] types={{2,2,2,2,2,2},{3,3,3,2,3,2},{3,3,1,2,1,2}};
        for (int alternate=1;alternate<=3;alternate++) {
            List<Tbs5530Descriptors.Endpoint> entries=new ArrayList<>();
            for(int index=0;index<addresses.length;index++) entries.add(new Tbs5530Descriptors.Endpoint(
                    0,alternate,addresses[index],addresses[index]&0x80,types[alternate-1][index],
                    alternate>1 && index<2?64:512));
            final int alt=alternate;
            rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(entries,alt));
            assertTrue(entries.stream().anyMatch(ep -> ep.address==0x88));
            assertFalse(entries.stream().anyMatch(ep -> ep.address==0x82));
        }
    }

    @Test public void rejectsDuplicateStreamAndNonstreamAddresses() {
        Tbs5530Descriptors.Endpoint stream = endpoint(0x82, 2, 512);
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Arrays.asList(stream, stream), 0));
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Arrays.asList(stream,
                endpoint(0x81, 2, 512), endpoint(0x81, 3, 64)), 0));
    }

    @Test public void rejectsMixedInterfaceAndAlternateMetadata() {
        for (Tbs5530Descriptors.Endpoint other : Arrays.asList(
                new Tbs5530Descriptors.Endpoint(1, 0, 0x81, 0x80, 2, 512),
                new Tbs5530Descriptors.Endpoint(0, 1, 0x81, 0x80, 2, 512))) {
            rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Arrays.asList(endpoint(0x82, 2, 512), other), 0));
        }
    }

    @Test public void rejectsNullListsEntriesAndInvalidAlternateBounds() {
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(null, 0));
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Collections.singletonList(null), 0));
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Collections.emptyList(), -1));
        rejects(() -> Tbs5530Descriptors.selectBulkEndpoint(Collections.emptyList(), 256));
    }

    @Test public void validatesEveryEndpointFieldAndDirectionAddressConsistency() {
        int[][] invalid = {
                {-1, 0, 0x82, 0x80, 2, 512}, {256, 0, 0x82, 0x80, 2, 512},
                {0, -1, 0x82, 0x80, 2, 512}, {0, 256, 0x82, 0x80, 2, 512},
                {0, 0, -1, 0x80, 2, 512}, {0, 0, 256, 0x80, 2, 512},
                {0, 0, 0x92, 0x80, 2, 512}, {0, 0, 0, 0, 2, 64}, {0, 0, 0x80, 0x80, 2, 64},
                {0, 0, 0x82, 0, 2, 512}, {0, 0, 2, 0x80, 2, 64}, {0, 0, 0x82, 1, 2, 512},
                {0, 0, 0x82, 0x80, -1, 512}, {0, 0, 0x82, 0x80, 0, 512},
                {0, 0, 0x82, 0x80, 4, 512}, {0, 0, 0x82, 0x80, 2, 0},
                {0, 0, 0x82, 0x80, 2, -1}, {0, 0, 0x82, 0x80, 2, 1025}
        };
        for (int[] fields : invalid) rejects(() -> new Tbs5530Descriptors.Endpoint(
                fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]));
    }

    @Test public void metadataSnapshotDoesNotDependOnSourceList() {
        Tbs5530Descriptors.Endpoint stream = endpoint(0x82, 2, 512);
        List<Tbs5530Descriptors.Endpoint> endpoints = new ArrayList<>(Collections.singletonList(stream));
        Tbs5530Descriptors.Endpoint selected = Tbs5530Descriptors.selectBulkEndpoint(endpoints, 0);
        endpoints.clear();
        assertEquals(0x82, selected.address);
        assertEquals(512, selected.maxPacketSize);
    }
}
