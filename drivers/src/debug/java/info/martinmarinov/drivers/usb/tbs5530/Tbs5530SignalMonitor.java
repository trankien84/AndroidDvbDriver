// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Debug-only, RAM-only observer. Caller schedules ticks on its control worker and must wrap the
 * ENTIRE sample in its generation-bound EP0 transaction, including cleanup. The injected io must
 * not call session.failed on monitor errors. No worker, receive callback, file I/O or owner is created.
 * Windows golden inventory: SHA 70fced47614ff8ec17c9e78f79fca16408c23e6bcadcfb6ad25a4c69cd7f9ded.
 * Address literals in USB payloads are eight-bit C8 (seven-bit I2C 64), not tuner C0 or LED CC.
 */
public final class Tbs5530SignalMonitor {
    public interface Clock { long nanoTime(); }
    public static final long TICK_NANOS = 250_000_000L;
    public static final long LOCK_LOSS_NANOS = 2_000_000_000L;
    private static final long MAX_CONFIRMED_GAP_NANOS = 2 * TICK_NANOS;
    private static final int TIMEOUT_MILLIS = 2000;
    private static final int[][] LEGACY_INVENTORY = {
            {0x20, 0x10, 1}, {0x20, 0x28, 2}, {0x20, 0x72, 1},
            {0x22, 0x12, 1}, {0x22, 0x5b, 1}, {0x22, 0x5c, 1}, {0x22, 0x5e, 1},
            {0x24, 0x40, 4}
    };
    private static final int[][] B5_INVENTORY = {
            {0x20, 0x10, 1}, {0x20, 0x28, 2}, {0x20, 0x72, 1},
            {0x22, 0x12, 1}, {0x22, 0x54, 1}, {0x22, 0x5b, 1}, {0x22, 0x5c, 1}, {0x22, 0x5e, 1}, {0x22, 0x62, 1},
            {0x24, 0x40, 4}
    };
    private final Tbs5530ControlIo io;
    private final Clock clock;
    private final int[][] inventory;
    private boolean sampled, losingLock;
    private long lastStart, lossSince, lastLossObservation;

    public Tbs5530SignalMonitor(Tbs5530ControlIo io, Clock clock) {
        this(io, clock, false);
    }

    /** Tuner ADC/repeater writes are intentionally unsupported, even with an explicit opt-in. */
    public Tbs5530SignalMonitor(Tbs5530ControlIo io, Clock clock, boolean tunerRssi) {
        this(io, clock, tunerRssi, false);
    }
    public Tbs5530SignalMonitor(Tbs5530ControlIo io, Clock clock, boolean tunerRssi, boolean b5) {
        inventory = b5 ? B5_INVENTORY : LEGACY_INVENTORY;
        if (tunerRssi) throw new IllegalArgumentException(
                "TUNER_RSSI_UNSUPPORTED: Windows 88=00 differs from official ASCOT3 RSSI cleanup 88=04; no approved exact sequence");
        this.io = Objects.requireNonNull(io);
        this.clock = Objects.requireNonNull(clock);
    }

    public static final class RegisterRead {
        public final int bank, register;
        public final long startNanos, endNanos;
        private final byte[] bytes;
        public RegisterRead(int bank, int register, byte[] bytes, long startNanos, long endNanos) {
            if (bank < 0 || bank > 255 || register < 0 || register > 255 || bytes == null
                    || bytes.length < 1 || bytes.length > 50 || endNanos - startNanos < 0)
                throw new IllegalArgumentException("Invalid register observation");
            this.bank = bank; this.register = register; this.bytes = bytes.clone();
            this.startNanos = startNanos; this.endNanos = endNanos;
        }
        public byte[] getBytes() { return bytes.clone(); }
    }

    public static final class Error {
        public final String operation, exceptionType, message;
        public final int bank, register;
        public final long timestampNanos;
        private Error(String operation, int bank, int register, long timestamp, Exception error) {
            this.operation = operation; this.bank = bank; this.register = register;
            timestampNanos = timestamp; exceptionType = error.getClass().getName();
            // Do not propagate transport exception text, which may contain USB identities or paths.
            message = error instanceof ShortTransfer ? "SHORT_CONTROL_TRANSFER" : "MONITOR_IO_ERROR";
        }
    }

    public static final class Sample {
        public final long startNanos, endNanos, confirmedLockLossNanos;
        public final List<RegisterRead> rawReads;
        public final List<Error> errors;
        public final Tbs5530SignalMetrics metrics;
        public final boolean stopForLockLoss, cleanupSucceeded;
        private Sample(long start, long end, List<RegisterRead> reads, List<Error> errors,
                       Tbs5530SignalMetrics metrics, boolean cleanup, long lossNanos) {
            startNanos = start; endNanos = end;
            rawReads = Collections.unmodifiableList(new ArrayList<>(reads));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.metrics = metrics; cleanupSucceeded = cleanup;
            confirmedLockLossNanos = lossNanos;
            stopForLockLoss = lossNanos > LOCK_LOSS_NANOS;
        }
    }

    /** Null means no tick was due. Missed ticks are not replayed in a catch-up burst. */
    public synchronized Sample sampleIfDue() {
        long now = clock.nanoTime();
        if (sampled && now - lastStart >= 0 && now - lastStart < TICK_NANOS) return null;
        return sample();
    }

    /** One bounded observation, no retries; errors are samples, not session failures or unlocks. */
    public synchronized Sample sample() {
        long start = clock.nanoTime();
        sampled = true; lastStart = start;
        List<RegisterRead> reads = new ArrayList<>();
        List<Error> errors = new ArrayList<>();
        boolean cleanup = true;
        int bank = -1;
        try {
            write(0x00, 0x20); bank = 0x20;
            write(0x01, 1);
            for (int[] reg : inventory) {
                if (bank != reg[0]) {
                    try { write(0x00, reg[0]); bank = reg[0]; }
                    catch (IOException | RuntimeException e) {
                        bank = -1;
                        errors.add(new Error("BANK_SELECT", reg[0], 0, clock.nanoTime(), e));
                        continue;
                    }
                }
                long before = clock.nanoTime();
                try {
                    byte[] bytes = read(reg[1], reg[2]);
                    reads.add(new RegisterRead(bank, reg[1], bytes, before, clock.nanoTime()));
                } catch (IOException | RuntimeException e) {
                    errors.add(new Error("READ", bank, reg[1], clock.nanoTime(), e));
                }
            }
        } catch (IOException | RuntimeException e) {
            errors.add(new Error("FREEZE_SETUP", bank, 1, clock.nanoTime(), e));
        } finally {
            try { write(0x01, 0); }
            catch (IOException | RuntimeException e) {
                cleanup = false;
                errors.add(new Error("UNFREEZE", bank, 1, clock.nanoTime(), e));
            }
            try { write(0x00, 0x20); }
            catch (IOException | RuntimeException e) {
                cleanup = false;
                errors.add(new Error("BANK_RESTORE", 0x20, 0, clock.nanoTime(), e));
            }
        }
        long end = clock.nanoTime();
        Tbs5530SignalMetrics metrics = Tbs5530SignalMetrics.decode(reads);
        long observation = end;
        for (RegisterRead r : reads) if (r.bank == 0x20 && r.register == 0x10) observation = r.endNanos;
        if (!cleanup || end - start < 0 || end - observation > MAX_CONFIRMED_GAP_NANOS
                || !demodLost(reads)) {
            losingLock = false;
        } else {
            long gap = observation - lastLossObservation;
            if (!losingLock || gap < 0 || gap > MAX_CONFIRMED_GAP_NANOS) {
                lossSince = observation;
                losingLock = true;
            }
            lastLossObservation = observation;
        }
        long loss = losingLock ? observation - lossSince : 0;
        return new Sample(start, end, reads, errors, metrics, cleanup, loss);
    }

    private static boolean demodLost(List<RegisterRead> reads) {
        for (RegisterRead r : reads) if (r.bank == 0x20 && r.register == 0x10) {
            int status = r.getBytes()[0] & 255;
            int sync = status & 7;
            return sync != 7 && (sync != 6 || (status & 0x10) != 0);
        }
        return false;
    }

    private void write(int register, int value) throws IOException {
        if (!(register == 0 && (value == 0x20 || value == 0x22 || value == 0x24)
                || register == 1 && (value == 0 || value == 1)))
            throw new IllegalArgumentException("Monitor write outside bank/freeze allowlist");
        byte[] bytes = {3, (byte) 0xc8, (byte) register, (byte) value};
        transfer(0x40, 0x80, bytes);
    }

    private byte[] read(int register, int length) throws IOException {
        byte[] pointer = {(byte) length, (byte) 0xc8, (byte) register};
        transfer(0x40, 0x90, pointer);
        byte[] result = new byte[length];
        transfer(0xc0, 0x91, result);
        return result;
    }

    private void transfer(int type, int request, byte[] bytes) throws IOException {
        if (io.controlTransfer(type, request, 0, 0, bytes, 0, bytes.length, TIMEOUT_MILLIS) != bytes.length)
            throw new ShortTransfer();
    }
    private static final class ShortTransfer extends IOException { private static final long serialVersionUID = 1; }
}
