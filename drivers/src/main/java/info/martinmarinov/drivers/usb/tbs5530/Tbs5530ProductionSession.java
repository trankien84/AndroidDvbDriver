// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local ownership, including discovery, firmware handoff and final USB drain. */
public final class Tbs5530ProductionSession implements AutoCloseable {
    private static final AtomicReference<Tbs5530ProductionSession> OWNER = new AtomicReference<>();

    private volatile boolean drainUnconfirmed;

    private Tbs5530ProductionSession() { }

    static Tbs5530ProductionSession acquire() throws Tbs5530ProductionFailure {
        Tbs5530ProductionSession next = new Tbs5530ProductionSession();
        if (!OWNER.compareAndSet(null, next)) {
            Tbs5530ProductionSession current = OWNER.get();
            throw new Tbs5530ProductionFailure(current != null && current.drainUnconfirmed
                    ? Tbs5530ProductionFailure.Kind.DRAIN_UNCONFIRMED : Tbs5530ProductionFailure.Kind.BUSY,
                    "Another production TBS5530 owner is active or awaiting safe cleanup");
        }
        return next;
    }

    void retainUndrained() { drainUnconfirmed = true; }

    /** Activity routing must not start a second owner during re-enumeration. */
    public static boolean isActive() { return OWNER.get() != null; }

    @Override public void close() { OWNER.compareAndSet(this, null); }
}
