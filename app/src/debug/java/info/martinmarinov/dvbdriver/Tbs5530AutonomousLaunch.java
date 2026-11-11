// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** A launch correlation is not a hardware grant or an attachment epoch. */
final class Tbs5530AutonomousLaunch {
    static final String TOKEN = "launch_token", CONTROL_ONLY = "control_plane_only";
    private final Set<String> used = new HashSet<>();
    private boolean active, acquisitionStarted, cleanupConfirmed;
    private String token;

    static boolean valid(String value) {
        return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                && !value.equals("00000000-0000-0000-0000-000000000000");
    }
    synchronized void begin(String value) throws IOException {
        if (active && !cleanupConfirmed) throw new IOException("Previous workflow cleanup is not certified");
        if (value != null && (!valid(value) || used.contains(value) || used.size() >= 16))
            throw new IOException("Fresh launch correlation required");
        if (value != null) used.add(value);
        token = value; active = true; acquisitionStarted = false; cleanupConfirmed = false;
    }
    synchronized boolean matches(String value) { return value != null && value.equals(token); }
    synchronized void requireFresh(String value) throws IOException {
        if (!valid(value) || used.contains(value) || used.size() >= 16) throw new IOException("Fresh launch correlation required");
    }
    synchronized void acquiring() throws IOException {
        if (!active || cleanupConfirmed) throw new IOException("Workflow is not active");
        acquisitionStarted = true;
    }
    synchronized boolean certifyNoResources(int resourceMask) {
        cleanupConfirmed = active && !acquisitionStarted && resourceMask == 0;
        return cleanupConfirmed;
    }
    synchronized void runtimeCleanup(boolean confirmed) { cleanupConfirmed = confirmed; }
    synchronized boolean cleaned() { return cleanupConfirmed; }
}
