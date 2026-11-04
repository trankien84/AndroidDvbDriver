// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline comparison only. Neither provenance nor a plan ID authorizes USB execution. */
public final class Tbs5530Fx2ProtocolPlan {
    public enum Id { TBS_SOURCE_DUAL_RESET, DOCUMENTED_FX2_E600_ONLY }

    private final Id id;
    private final List<Tbs5530FirmwarePlan.Step> steps;

    private Tbs5530Fx2ProtocolPlan(Id id, List<Tbs5530FirmwarePlan.Step> steps) {
        this.id = id;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public static Tbs5530Fx2ProtocolPlan describe(Tbs5530FirmwarePlan source, String planId) {
        if (source == null || planId == null) {
            throw new IllegalArgumentException("An offline source plan and exact plan ID are required");
        }
        Id id = Id.valueOf(planId);
        List<Tbs5530FirmwarePlan.Step> original = source.getSteps();
        int end = original.size();
        if (end < 6) throw new IllegalArgumentException("Incomplete source sequence");
        requireReset(original.get(0), 0x7f92, 1);
        requireReset(original.get(1), 0xe600, 1);
        requireReset(original.get(end - 3), 0x7f92, 0);
        requireReset(original.get(end - 2), 0xe600, 0);
        if (original.get(end - 1).getKind() != Tbs5530FirmwarePlan.Kind.DELAY) {
            throw new IllegalArgumentException("Missing transition delay");
        }
        int nextAddress = 0;
        for (int i = 2; i < end - 3; i++) {
            Tbs5530FirmwarePlan.Step step = original.get(i);
            int length = step.getPayload().length;
            if (step.getKind() != Tbs5530FirmwarePlan.Kind.DATA || step.getValue() != nextAddress
                    || length < 1 || length > 64 || nextAddress + length > 0x2000) {
                throw new IllegalArgumentException("FX2 image must stay within main RAM 0000-1fff");
            }
            nextAddress += length;
        }
        List<Tbs5530FirmwarePlan.Step> selected = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            if (id == Id.DOCUMENTED_FX2_E600_ONLY && (i == 0 || i == end - 3)) continue;
            selected.add(original.get(i));
        }
        return new Tbs5530Fx2ProtocolPlan(id, selected);
    }

    private static void requireReset(Tbs5530FirmwarePlan.Step step, int address, int value) {
        byte[] payload = step.getPayload();
        if (step.getKind() != Tbs5530FirmwarePlan.Kind.DATA || step.getValue() != address
                || payload.length != 1 || payload[0] != value) {
            throw new IllegalArgumentException("Unexpected source reset sequence");
        }
    }

    public Id getId() { return id; }
    public List<Tbs5530FirmwarePlan.Step> getSteps() { return steps; }

    public String getProvenance() {
        return id == Id.TBS_SOURCE_DUAL_RESET
                ? "linux_media@2ce787de9f6e81b4294692a45de15596ae2e1322:tbs5530.c:286-310"
                : "FX2 TRM v2.2:2.3.11,3.8,7.3; linux@05441d697f7f9a927264f4058cbbc6a03dfcac9d:cypress_firmware.c";
    }
}
