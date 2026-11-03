// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Debug-only bounded parser. Never retains raw bytes, strings or unknown payloads. */
public final class Tbs5530RawDescriptors {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final class Entry {
        public final Map<String, Integer> fields;
        private Entry(Map<String, Integer> fields) {
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }
    public static final class Result {
        public final int length;
        public final String sha256, status;
        public final List<Entry> entries;
        public final List<String> issues;
        private Result(int length, String hash, String status, List<Entry> entries, List<String> issues) {
            this.length = length; this.sha256 = hash; this.status = status;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }
    }
    private Tbs5530RawDescriptors() { }
    private static int u8(byte[] data, int at) { return data[at] & 255; }
    private static int u16(byte[] data, int at) { return u8(data, at) | u8(data, at + 1) << 8; }
    private static void put(Map<String, Integer> fields, String key, int value) { fields.put(key, value); }

    public static Result parse(byte[] data) {
        List<Entry> entries = new ArrayList<>(); List<String> issues = new ArrayList<>();
        if (data == null) return new Result(0, "", "NULL", entries, issues);
        if (data.length > MAX_BYTES) return new Result(data.length, "", "TOO_LARGE", entries, issues);
        String hash = hash(data);
        if (data.length == 0) return new Result(0, hash, "EMPTY", entries, issues);
        int at = 0, configEnd = -1, configValue = -1, iface = -1, alternate = -1;
        int expectedConfigs = -1, configs = 0, expectedInterfaces = -1, expectedEndpoints = -1, endpoints = 0;
        Set<Integer> interfaceIds = new HashSet<>(); Set<Integer> alternates = new HashSet<>();
        Set<Integer> addresses = new HashSet<>();
        boolean malformed = false, truncated = false, deviceSeen = false;
        while (at < data.length) {
            if (configEnd >= 0 && at >= configEnd) {
                if (expectedEndpoints >= 0 && endpoints != expectedEndpoints) issues.add("ENDPOINT_COUNT_MISMATCH");
                if (interfaceIds.size() != expectedInterfaces) issues.add("INTERFACE_COUNT_MISMATCH");
                configEnd = -1; configValue = -1; iface = -1; alternate = -1; expectedEndpoints = -1;
            }
            if (data.length - at < 2) { issues.add("TRUNCATED_HEADER"); truncated = true; break; }
            int size = u8(data, at), type = u8(data, at + 1);
            if (size < 2) { issues.add("INVALID_BLENGTH"); malformed = true; break; }
            if (size > data.length - at) { issues.add("TRUNCATED_DESCRIPTOR"); truncated = true; break; }
            if (configEnd >= 0 && size > configEnd - at) { issues.add("DESCRIPTOR_EXCEEDS_WTOTAL_LENGTH"); malformed = true; break; }
            int minimum = type == 1 ? 18 : type == 2 ? 9 : type == 4 ? 9 : type == 5 ? 7 : 2;
            if (size < minimum) { issues.add("SHORT_STANDARD_DESCRIPTOR"); malformed = true; break; }
            Map<String, Integer> f = new LinkedHashMap<>();
            put(f, "offset", at); put(f, "length", size); put(f, "type", type);
            if (type == 1) {
                if (deviceSeen || at != 0) { issues.add("UNEXPECTED_DEVICE_DESCRIPTOR"); malformed = true; }
                deviceSeen = true; expectedConfigs = u8(data, at + 17);
                put(f, "bcdUSB", u16(data, at + 2)); put(f, "device_class", u8(data, at + 4));
                put(f, "device_subclass", u8(data, at + 5)); put(f, "device_protocol", u8(data, at + 6));
                put(f, "ep0_packet_size", u8(data, at + 7)); put(f, "vid", u16(data, at + 8));
                put(f, "pid", u16(data, at + 10)); put(f, "bcdDevice", u16(data, at + 12));
                put(f, "configuration_count", expectedConfigs);
            } else if (type == 2) {
                if (configEnd >= 0) { issues.add("OVERLAPPING_CONFIGURATION"); malformed = true; break; }
                int total = u16(data, at + 2);
                put(f, "wTotalLength", total); expectedInterfaces = u8(data, at + 4);
                put(f, "bNumInterfaces", expectedInterfaces); configValue = u8(data, at + 5);
                put(f, "configuration_value", configValue); put(f, "attributes", u8(data, at + 7));
                put(f, "max_power_units_2ma", u8(data, at + 8));
                if (total < size) { issues.add("INVALID_WTOTAL_LENGTH"); malformed = true; break; }
                if (total > data.length - at) { issues.add("TRUNCATED_CONFIGURATION_TREE"); truncated = true; }
                configEnd = at + total;
                interfaceIds.clear(); alternates.clear(); addresses.clear();
                iface = -1; alternate = -1; expectedEndpoints = -1; configs++;
            } else if (type == 4) {
                if (expectedEndpoints >= 0 && endpoints != expectedEndpoints) issues.add("ENDPOINT_COUNT_MISMATCH");
                iface = u8(data, at + 2); alternate = u8(data, at + 3); expectedEndpoints = u8(data, at + 4); endpoints = 0;
                if (configEnd < 0) { issues.add("INTERFACE_WITHOUT_CONFIGURATION"); malformed = true; }
                interfaceIds.add(iface);
                if (!alternates.add(iface * 256 + alternate)) { issues.add("DUPLICATE_ALTERNATE"); malformed = true; }
                addresses.clear();
                put(f, "configuration_value", configValue); put(f, "interface_id", iface); put(f, "alternate", alternate);
                put(f, "endpoint_count", expectedEndpoints); put(f, "class", u8(data, at + 5));
                put(f, "subclass", u8(data, at + 6)); put(f, "protocol", u8(data, at + 7));
            } else if (type == 5) {
                int address = u8(data, at + 2), attributes = u8(data, at + 3), packet = u16(data, at + 4);
                if (configEnd < 0 || iface < 0) { issues.add("ENDPOINT_WITHOUT_INTERFACE"); malformed = true; }
                if ((address & 0x70) != 0 || (address & 15) == 0 || !addresses.add(address)) {
                    issues.add("INVALID_OR_DUPLICATE_ENDPOINT"); malformed = true;
                }
                endpoints++;
                put(f, "configuration_value", configValue); put(f, "interface_id", iface); put(f, "alternate", alternate);
                put(f, "address", address); put(f, "direction", address & 128); put(f, "transfer_type", attributes & 3);
                put(f, "attributes", attributes); put(f, "wMaxPacketSize", packet); put(f, "packet_bytes", packet & 2047);
                put(f, "additional_transactions", packet >> 11 & 3); put(f, "interval", u8(data, at + 6));
            }
            entries.add(new Entry(f));
            at += size;
        }
        if (configEnd >= 0) {
            if (at < configEnd) truncated = true;
            if (expectedEndpoints >= 0 && endpoints != expectedEndpoints) issues.add("ENDPOINT_COUNT_MISMATCH");
            if (interfaceIds.size() != expectedInterfaces) issues.add("INTERFACE_COUNT_MISMATCH");
        }
        if (!deviceSeen) issues.add("DEVICE_DESCRIPTOR_NOT_RETURNED");
        if (configs == 0) issues.add("CONFIGURATION_TREE_NOT_RETURNED");
        if (expectedConfigs >= 0 && configs != expectedConfigs) issues.add("CONFIGURATION_COUNT_MISMATCH");
        String status = malformed ? "MALFORMED" : truncated ? "TRUNCATED"
                : !issues.isEmpty() ? "PARTIAL_OR_INCONSISTENT" : "COMPLETE_RETURNED_TREE";
        return new Result(data.length, hash, status, entries, issues);
    }
    private static String hash(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data); StringBuilder out = new StringBuilder(64);
            for (byte value : hash) { out.append(Character.forDigit(value >> 4 & 15, 16)); out.append(Character.forDigit(value & 15, 16)); }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
