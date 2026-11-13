// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Four fixed phases; OFF never owns submitted requests. All boundaries share the native clock. */
final class Tbs5530B5Runner {
    static final String LAYOUT = "OFF1:10000,ON1:30000,OFF2:10000,ON2:10000";
    enum Phase {
        OFF1(10000, false), ON1(30000, true), OFF2(10000, false), ON2(10000, true);
        final int durationMillis; final boolean on;
        Phase(int duration, boolean on) { durationMillis = duration; this.on = on; }
        int stalePrefixBytes() { return this == ON1 ? 2048 : 0; }
    }
    interface Port {
        long nanos();
        void require() throws IOException;
        void pause(int millis) throws IOException;
        void selectStream() throws IOException;
        void rssi(Phase phase) throws IOException;
        Outcome receive(Phase phase) throws IOException;
        void drain() throws IOException;
        default boolean monitorOff(Phase phase) throws IOException { return false; }
        default int monitorDelayMillis() { return 100; }
    }
    static final class Outcome {
        final long bytes; final boolean drained, quarantined; final String reason;
        Outcome(long bytes, boolean drained, boolean quarantined, String reason) {
            this.bytes = bytes; this.drained = drained; this.quarantined = quarantined; this.reason = reason;
        }
        boolean completed() { return drained && !quarantined && "DEADLINE".equals(reason); }
    }
    static final class Boundary {
        final Phase phase; final long startNanos; long endNanos;
        Outcome outcome; int rssiSamples, rssiErrors, monitorErrors; boolean drained;
        Boundary(Phase phase, long start) { this.phase = phase; startNanos = start; }
    }
    private final List<Boundary> boundaries = new ArrayList<>();
    private boolean used;
    List<Boundary> boundaries() { return Collections.unmodifiableList(new ArrayList<>(boundaries)); }
    void run(Port port) throws IOException {
        synchronized (this) {
            if (used) throw new IOException("B5_RUNNER_ALREADY_CONSUMED");
            used = true;
        }
        Throwable failure = null;
        try {
            port.require(); port.selectStream();
            for (Phase phase : Phase.values()) {
                port.require();
                long start = port.nanos();
                if (start < 0) throw new IOException("B5_CLOCK_UNAVAILABLE");
                Boundary b = new Boundary(phase, start); boundaries.add(b);
                try {
                    if (phase.on) {
                        b.outcome = port.receive(phase);
                        port.drain(); b.drained = b.outcome.drained && !b.outcome.quarantined;
                        if (!b.outcome.completed()) throw new IOException("B5_" + phase + "_" + b.outcome.reason);
                    } else {
                        port.drain(); b.drained = true;
                        for (int i = 0; i < 3; i++) {
                            port.require();
                            try { port.rssi(phase); b.rssiSamples++; }
                            catch (IOException error) { b.rssiErrors++; }
                        }
                        while (true) {
                            port.require(); long now = port.nanos();
                            if (now < b.startNanos) throw new IOException("B5_CLOCK_ROLLBACK");
                            long remaining = phase.durationMillis * 1_000_000L - (now - b.startNanos);
                            if (remaining <= 0) break;
                            boolean unlock = false;
                            try { unlock = port.monitorOff(phase); }
                            catch (IOException error) { b.monitorErrors++; }
                            if (unlock) throw new IOException("B5_DEMOD_UNLOCK_OVER_2_SECONDS");
                            port.require();
                            int delay = port.monitorDelayMillis();
                            if (delay < 80 || delay > 120) throw new IOException("B5_MONITOR_JITTER_OUT_OF_SCOPE");
                            long after = port.nanos();
                            if (after < now) throw new IOException("B5_CLOCK_ROLLBACK");
                            remaining = phase.durationMillis * 1_000_000L - (after - b.startNanos);
                            long untilTick = delay * 1_000_000L - (after - now);
                            if (remaining > 0 && untilTick > 0)
                                port.pause((int)Math.max(1, Math.min(remaining, untilTick) / 1_000_000L));
                        }
                    }
                } finally { b.endNanos = port.nanos(); }
            }
        } catch (IOException | RuntimeException error) { failure = error; throw error; }
        finally {
            try { port.drain(); }
            catch (IOException error) { if (failure != null) failure.addSuppressed(error); else throw error; }
        }
    }
}
