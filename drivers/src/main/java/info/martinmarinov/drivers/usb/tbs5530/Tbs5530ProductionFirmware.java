// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Collections;

/** Only the reviewed original 8 KiB RAM image and dual-reset order, never EEPROM. */
final class Tbs5530ProductionFirmware {
    interface Guard { void require() throws IOException; }
    interface Pause { void sleep(long millis) throws InterruptedException; }
    static final int SIZE = 8192;
    static final String SHA256 = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc";
    // B5 ordinal 4 read-only export, session-initial-descriptors.bin (189 bytes).
    static final String DEFAULT_SHA256 = "e2887bad8b5a0e8dcaaa69bd636a01a2d11e53fe4b532b5b14828a2d3df952de";
    static final String OPERATIONAL_SHA256 = "22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5";

    static byte[] load(android.content.Context context) throws IOException {
        try { return new Tbs5530FirmwareStore(context).load(); }
        catch (IOException e) { throw new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.FIRMWARE,
                "Cannot load the imported pinned firmware", e); }
    }

    static Tbs5530FirmwarePlan plan(byte[] image) throws IOException {
        if (image == null || image.length != SIZE) throw new Tbs5530ProductionFailure(
                Tbs5530ProductionFailure.Kind.FIRMWARE, "TBS5530 firmware must contain 8192 bytes");
        byte[] hash = new byte[32];
        for (int i = 0; i < hash.length; i++) hash[i] = (byte) Integer.parseInt(SHA256.substring(2 * i, 2 * i + 2), 16);
        try {
            return Tbs5530FirmwarePlan.create(image, new Tbs5530FirmwarePlan.Policy(hash,
                    Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, SIZE)), null, 0, SIZE));
        } catch (IllegalArgumentException e) { throw new Tbs5530ProductionFailure(
                Tbs5530ProductionFailure.Kind.FIRMWARE, "TBS5530 firmware hash mismatch", e); }
    }

    static Tbs5530OperationalProfile profile(byte[] descriptors) throws IOException {
        Tbs5530OperationalProfile p = Tbs5530OperationalProfile.observe(descriptors);
        if (p.kind == Tbs5530OperationalProfile.Kind.FX2_DEFAULT && descriptors.length == 189
                && DEFAULT_SHA256.equals(p.descriptorHash)) return p;
        if (p.kind == Tbs5530OperationalProfile.Kind.OPERATIONAL_CANDIDATE && descriptors.length == 50
                && OPERATIONAL_SHA256.equals(p.descriptorHash)) return p;
        throw new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.DESCRIPTOR,
                "Unsupported TBS5530 USB descriptor fingerprint");
    }

    /** No retry, even after a short or failed transfer; the guard owns detach/cancellation. */
    static void bootstrap(Tbs5530FirmwarePlan plan, Tbs5530ControlIo io, Guard guard,
                          Pause pause, Runnable finalRelease) throws IOException, InterruptedException {
        if (plan.getSteps().size() != 133) throw new IOException("Expected exact 132-transfer RAM plan");
        int ordinal = 0;
        for (Tbs5530FirmwarePlan.Step step : plan.getSteps()) {
            guard.require();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (step.getKind() == Tbs5530FirmwarePlan.Kind.DELAY) {
                pause.sleep(step.getDelayMillis());
            } else {
                ordinal++;
                if (ordinal == 132) finalRelease.run();
                byte[] bytes = step.getPayload();
                int actual = io.controlTransfer(step.getRequestType(), step.getRequest(), step.getValue(),
                        step.getIndex(), bytes, 0, bytes.length, step.getTimeoutMillis());
                if (actual != bytes.length) throw new Tbs5530ProductionFailure(
                        Tbs5530ProductionFailure.Kind.BOOTSTRAP, "Partial TBS5530 RAM transfer " + ordinal);
            }
        }
    }
    private Tbs5530ProductionFirmware() { }
}
