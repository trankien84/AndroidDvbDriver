// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Map;

/** A fresh descriptor observation, not chip identity, frontend readiness or USB speed. */
public final class Tbs5530OperationalProfile {
    public enum Kind { FX2_DEFAULT, OPERATIONAL_CANDIDATE, UNKNOWN }
    public final Kind kind;
    public final String descriptorHash;
    public final int streamPacketSize, controlPacketSize;
    /** Linux USB_SPEED enum from host ioctl, not inferred from descriptors; negative means unavailable. */
    public final int negotiatedUsbSpeed;

    private Tbs5530OperationalProfile(Kind kind, String hash, int stream, int control) {
        this(kind, hash, stream, control, -1);
    }
    private Tbs5530OperationalProfile(Kind kind, String hash, int stream, int control, int speed) {
        this.kind = kind; descriptorHash = hash; streamPacketSize = stream; controlPacketSize = control;
        negotiatedUsbSpeed = speed;
    }
    Tbs5530OperationalProfile withSpeed(int speed) {
        return new Tbs5530OperationalProfile(kind, descriptorHash, streamPacketSize, controlPacketSize, speed);
    }

    public static Tbs5530OperationalProfile observe(byte[] bytes) {
        Tbs5530RawDescriptors.Result raw = Tbs5530RawDescriptors.parse(bytes);
        if (!"COMPLETE_RETURNED_TREE".equals(raw.status) || !raw.issues.isEmpty() || raw.entries.isEmpty()) {
            return unknown(raw.sha256);
        }
        Map<String, Integer> device = raw.entries.get(0).fields;
        if (!eq(device, "vid", 0x734c) || !eq(device, "pid", 0x5530)
                || !eq(device, "configuration_count", 1) || !eq(device, "ep0_packet_size", 64)) {
            return unknown(raw.sha256);
        }
        if (Tbs5530Fx2Fingerprint.assess(raw).candidate == Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE) {
            return new Tbs5530OperationalProfile(Kind.FX2_DEFAULT, raw.sha256, 0, 0);
        }
        int configs = 0, interfaces = 0, endpoints = 0, stream = 0, control = 0;
        for (Tbs5530RawDescriptors.Entry entry : raw.entries) {
            Map<String, Integer> f = entry.fields;
            int type = f.get("type");
            if (type == 2) {
                configs++;
                if (!eq(f, "configuration_value", 1) || !eq(f, "bNumInterfaces", 1)) return unknown(raw.sha256);
            } else if (type == 4) {
                interfaces++;
                if (!eq(f, "configuration_value", 1) || !eq(f, "interface_id", 0)
                        || !eq(f, "alternate", 0) || !eq(f, "endpoint_count", 2)
                        || !eq(f, "class", 255) || !eq(f, "subclass", 0) || !eq(f, "protocol", 0)) {
                    return unknown(raw.sha256);
                }
            } else if (type == 5) {
                endpoints++;
                if (!eq(f, "configuration_value", 1) || !eq(f, "interface_id", 0) || !eq(f, "alternate", 0)
                        || !eq(f, "direction", 128) || !eq(f, "attributes", 2)
                        || !eq(f, "additional_transactions", 0)) return unknown(raw.sha256);
                int size = f.get("wMaxPacketSize"), address = f.get("address");
                if (size != 64 && size != 512) return unknown(raw.sha256);
                if (address == 0x82 && stream == 0) stream = size;
                else if (address == 0x81 && control == 0) control = size;
                else return unknown(raw.sha256);
            } else if (type != 1) {
                // Unknown descriptors require review, not a guess about a different board revision.
                return unknown(raw.sha256);
            }
        }
        return configs == 1 && interfaces == 1 && endpoints == 2 && stream != 0 && control != 0
                ? new Tbs5530OperationalProfile(Kind.OPERATIONAL_CANDIDATE, raw.sha256, stream, control)
                : unknown(raw.sha256);
    }

    public void requireOperational() throws IOException {
        if (kind != Kind.OPERATIONAL_CANDIDATE) throw new IOException("Fresh operational descriptor profile required: " + kind);
    }

    public void requireMetadata(Tbs5530AndroidUsbFacade.RawConnection raw) throws IOException {
        requireOperational();
        if (raw.interfaceCount() != 1 || raw.interfaceId(0) != 0 || raw.alternateSetting(0) != 0
                || raw.endpointCount(0) != 2) throw new IOException("Raw and Android interface trees differ");
        boolean stream = false, control = false;
        for (int ep = 0; ep < 2; ep++) {
            int address = raw.endpointAddress(0, ep);
            if (raw.endpointDirection(0, ep) != 128 || raw.endpointType(0, ep) != 2) {
                throw new IOException("Raw and Android endpoint metadata differ");
            }
            int size = raw.endpointMaxPacketSize(0, ep);
            if (address == 0x82 && !stream && size == streamPacketSize) stream = true;
            else if (address == 0x81 && !control && size == controlPacketSize) control = true;
            else throw new IOException("Raw and Android endpoint addresses or packet sizes differ");
        }
        if (!stream || !control) throw new IOException("Operational endpoints unavailable");
    }

    private static boolean eq(Map<String, Integer> values, String key, int expected) {
        return Integer.valueOf(expected).equals(values.get(key));
    }
    private static Tbs5530OperationalProfile unknown(String hash) {
        return new Tbs5530OperationalProfile(Kind.UNKNOWN, hash, 0, 0);
    }
}
