// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

/** In-memory attachment continuity; a reused Android identity never erases a detach. */
final class Tbs5530AttachmentEpoch {
    private String selected;
    private boolean sealed, transition, oldDetached, selectedDetached, failed, closed;
    private String candidate;

    Tbs5530AttachmentEpoch(String initial) { selected = initial; }
    synchronized void attach(String identity) {
        if (closed || failed) return;
        if (transition) {
            if (!oldDetached) { if (!selected.equals(identity)) failed = true; }
            else if (candidate == null) candidate = identity;
            else if (!candidate.equals(identity) || sealed) failed = true;
        } else if (!selected.equals(identity) || sealed) failed = true;
        notifyAll();
    }
    synchronized void detach(String identity) {
        if (!closed && !transition && selected.equals(identity)) selectedDetached = true;
        if (closed || failed) return;
        if (transition && selected.equals(identity) && !oldDetached && candidate == null) oldDetached = true;
        else if (selected.equals(identity) || identity.equals(candidate)) failed = true;
        notifyAll();
    }
    synchronized void seal(String identity) throws IOException {
        require();
        if (!selected.equals(identity) || transition) throw new IOException("Attachment selection changed");
        sealed = true;
    }
    synchronized void beginTransition() {
        transition = true; sealed = false;
    }
    synchronized boolean oldDetached() { return oldDetached; }
    synchronized String candidate() { return candidate; }
    synchronized void observeCandidate(String identity) throws IOException {
        require();
        if (!transition || !oldDetached) throw new IOException("Bootstrap re-enumeration was not observed");
        if (candidate == null) candidate = identity;
        else if (!candidate.equals(identity)) throw new IOException("Ambiguous operational attachment");
    }
    synchronized void acceptOperational(String identity) throws IOException {
        require();
        if (!transition || !oldDetached || !identity.equals(candidate)) throw new IOException("Invalid operational handoff");
        selected = identity; candidate = null; transition = false; sealed = true;
    }
    synchronized void require() throws IOException {
        if (failed || closed) throw new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.REMOVED,
                "TBS5530 attachment was removed or replaced");
    }
    synchronized boolean selectedDetached() { return selectedDetached; }
    synchronized boolean invalid() { return failed || closed; }
    synchronized void close() { closed = true; notifyAll(); }
}
