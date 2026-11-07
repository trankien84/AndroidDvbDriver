// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

/** One bounded UI allowance after a proven, fully drained firmware transition; never I/O consent. */
final class Tbs5530AttachPermissionWindow {
    static final long MAX_MILLIS = 10000;
    private long opened = -1;
    private boolean closed;

    void observe(long now, boolean verifiedTransition, boolean bootstrapDrained, boolean ownerExists) {
        if (!closed && opened < 0 && now >= 0 && verifiedTransition && bootstrapDrained && !ownerExists) opened = now;
    }
    boolean allows(long now, boolean waitingTransition, boolean ownerExists) {
        return !closed && opened >= 0 && now >= opened && now - opened < MAX_MILLIS
                && waitingTransition && !ownerExists;
    }
    void close() { closed = true; }
}
