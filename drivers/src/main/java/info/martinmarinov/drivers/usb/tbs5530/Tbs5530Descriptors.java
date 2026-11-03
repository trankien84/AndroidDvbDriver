// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Android-independent, validated metadata for the selected interface, not a stream reader. */
public final class Tbs5530Descriptors {
    private Tbs5530Descriptors() { }

    public static final class Endpoint {
        public final int interfaceId;
        public final int alternateSetting;
        public final int address;
        public final int direction;
        public final int transferType;
        public final int maxPacketSize;

        public Endpoint(int interfaceId, int alternateSetting, int address, int direction,
                        int transferType, int maxPacketSize) {
            if (interfaceId < 0 || interfaceId > 255 || alternateSetting < 0 || alternateSetting > 255
                    || address < 0 || address > 255 || (address & 0x70) != 0 || (address & 0x0f) == 0
                    || (direction != 0 && direction != 0x80) || direction != (address & 0x80)
                    || transferType < 1 || transferType > 3 || maxPacketSize <= 0 || maxPacketSize > 1024) {
                throw new IllegalArgumentException("Invalid USB interface endpoint metadata");
            }
            this.interfaceId = interfaceId;
            this.alternateSetting = alternateSetting;
            this.address = address;
            this.direction = direction;
            this.transferType = transferType;
            this.maxPacketSize = maxPacketSize;
        }
    }

    /**
     * Accepts exactly one interface/alternate's endpoint set. The 64/512-byte policy is
     * deliberately narrower than all legal USB bulk sizes (e.g. full-speed 8/16/32).
     * 512 is historically observed metadata, not a fresh measurement or silicon requirement. Neither accepted
     * size proves the negotiated USB speed; no speed or streaming capability is inferred.
     */
    public static Endpoint selectBulkEndpoint(List<Endpoint> endpoints, int alternateSetting) {
        if (endpoints == null || alternateSetting < 0 || alternateSetting > 255) {
            throw new IllegalArgumentException("Endpoint descriptors and alternate setting are required");
        }
        List<Tbs5530Bridge.Endpoint> converted = new ArrayList<>();
        Set<Integer> addresses = new HashSet<>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint == null || endpoint.interfaceId != 0 || endpoint.alternateSetting != alternateSetting) {
                throw new IllegalArgumentException("Endpoint is not on selected interface 0 alternate setting");
            }
            if (!addresses.add(endpoint.address)) throw new IllegalArgumentException("Duplicate USB endpoint address");
            converted.add(new Tbs5530Bridge.Endpoint(endpoint.interfaceId, endpoint.alternateSetting,
                    endpoint.address, endpoint.transferType));
        }
        Tbs5530Bridge.Endpoint selected = Tbs5530Bridge.selectBulkEndpoint(converted, alternateSetting);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.address == selected.address) {
                if (endpoint.maxPacketSize != 64 && endpoint.maxPacketSize != 512) {
                    throw new IllegalArgumentException("Unsupported bulk IN 0x82 packet size; policy accepts 64 or 512 bytes");
                }
                return endpoint;
            }
        }
        throw new IllegalArgumentException("Missing bulk IN 0x82 on interface 0");
    }
}
