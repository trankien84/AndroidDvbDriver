// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530UsbBinding.Device;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530OperationalProfile;
import java.io.IOException;

/** Provisional attach admission before an I/O owner; never a permission or upload-completion proof. */
final class Tbs5530SameSessionHandoff {
    static final String DESCRIPTOR_SHA256 = "22e2f1df9f66c58dbb6c554e400b6b8abcb78ee4f277e03679bc0fac7a25add5";
    private final String session, epoch;
    private final boolean initialBootstrap;
    private Device selected, observed;
    private boolean sealed;
    private String failure;

    Tbs5530SameSessionHandoff(String session, String epoch) { this(session, epoch, false); }
    static Tbs5530SameSessionHandoff initialBootstrap(String session, String epoch) {
        return new Tbs5530SameSessionHandoff(session, epoch, true);
    }
    private Tbs5530SameSessionHandoff(String session, String epoch, boolean initialBootstrap) {
        if (session == null || session.isEmpty() || epoch == null || epoch.isEmpty())
            throw new IllegalArgumentException("Same-session handoff identity required");
        this.session = session; this.epoch = epoch; this.initialBootstrap = initialBootstrap;
    }
    private void check(String currentSession, String currentEpoch) throws IOException {
        if (failure != null) throw new IOException(failure);
        if (!session.equals(currentSession) || !epoch.equals(currentEpoch)) reject("HANDOFF_SESSION_OR_EPOCH_CHANGED");
    }
    private void reject(String reason) throws IOException { failure = reason; throw new IOException(reason); }
    synchronized void observeAttach(Device device) throws IOException {
        check(session, epoch);
        if (!Tbs5530PermissionState.target(device) || observed != null && !observed.equals(device)
                || selected != null && !selected.equals(device)) reject("ATTACH_IDENTITY_MISMATCH");
        if (sealed) reject("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED");
        observed = device;
    }
    synchronized void detached(Device device) throws IOException {
        check(session, epoch);
        // Only the operational phase permits the old FX2 detach before its first observation.
        if (Tbs5530PermissionState.target(device) && (initialBootstrap || observed != null || selected != null)) reject("USB_DETACHED");
    }
    synchronized void select(Device device) throws IOException {
        check(session, epoch);
        if (!Tbs5530PermissionState.target(device) || selected != null && !selected.equals(device)
                || observed != null && !observed.equals(device)) reject("ATTACH_IDENTITY_MISMATCH");
        selected = device;
    }
    synchronized void attach(Tbs5530PermissionState permission, Device device, String currentSession, String currentEpoch) throws IOException {
        try {
            check(currentSession, currentEpoch);
            observeAttach(device);
            Tbs5530PermissionState.Attempt attempt = permission.attempt();
            Tbs5530PermissionState.State state = permission.state();
            if (selected == null || attempt == null || !selected.equals(device) || !attempt.device.equals(device)
                    || attempt.generation != permission.generation()
                    || state != Tbs5530PermissionState.State.PENDING && state != Tbs5530PermissionState.State.CALLBACK_GRANTED
                    && state != Tbs5530PermissionState.State.GRANTED) reject("ATTACH_DUPLICATE_OR_ABA_UNVERIFIED");
            // Provisional only: the exact raw tree must still pass descriptorsBeforeOwner before any owner or I/O.
        } catch (IOException error) { permission.invalidate(); throw error; }
    }
    synchronized void descriptorsBeforeOwner(Device device, byte[] descriptors, String currentSession, String currentEpoch) throws IOException {
        check(currentSession, currentEpoch);
        if (sealed || selected == null || !selected.equals(device)) reject("HANDOFF_OWNER_OR_IDENTITY_UNVERIFIED");
        if (descriptors == null) reject("HANDOFF_DESCRIPTOR_MISMATCH");
        Tbs5530OperationalProfile profile = Tbs5530OperationalProfile.observe(descriptors);
        if (initialBootstrap ? profile.kind != Tbs5530OperationalProfile.Kind.FX2_DEFAULT
                : descriptors.length != 50 || !DESCRIPTOR_SHA256.equals(profile.descriptorHash))
            reject("HANDOFF_DESCRIPTOR_MISMATCH");
        sealed = true;
    }
    synchronized void requireOwnerAdmission(String currentSession, String currentEpoch) throws IOException {
        check(currentSession, currentEpoch);
        if (!sealed) reject("HANDOFF_DESCRIPTOR_UNVERIFIED");
    }
}
