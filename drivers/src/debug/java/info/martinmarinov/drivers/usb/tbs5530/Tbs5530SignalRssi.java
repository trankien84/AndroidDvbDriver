// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** OFF-only ASCOT3 ADC measurement; caller owns the entire generation-bound EP0 transaction. */
public final class Tbs5530SignalRssi {
    public enum Phase { OFF, ON }
    private static final int[] BPF_GAIN = {0, 0, 0, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 20, 20};
    private static final int[] RF_THRESHOLDS = {700000, 600000, 532000, 464000, 400000, 350000,
            320000, 285000, 215000, 184000, 172000, 150000, 86000, 65000};
    private static final int[] RF_MAX = {4150, 4130, 4170, 4050, 4280, 4260, 4110,
            4310, 4250, 4020, 3920, 4080, 4180, 4200};

    public static final class Result {
        public final long startNanos, endNanos;
        public final int ifAgc, rfAgc, reg69;
        public final double rssiDbm;
        public final boolean cleanupSucceeded = true;
        private Result(long start, long end, int ifAgc, int rfAgc, int reg69, int frequencyKhz) {
            startNanos = start; endNanos = end;
            this.ifAgc = ifAgc; this.rfAgc = rfAgc; this.reg69 = reg69;
            rssiDbm = ascot3Dbm(ifAgc, rfAgc, reg69, frequencyKhz);
        }
    }

    /**
     * User-reviewed Windows sequence: cleanup 88=00 then 87=C0 (not Linux 88=04).
     * Requires Bulk stopped/drained and tuner repeater already enabled by the owner.
     * Never call from an ON worker. No retry or receive/session ownership is created here.
     */
    public static Result sample(Phase phase, Tbs5530ControlIo io,
                                Tbs5530SignalMonitor.Clock clock, int frequencyKhz) throws IOException {
        if (phase != Phase.OFF) throw new IllegalArgumentException("RSSI_REQUIRES_OFF_PHASE");
        Objects.requireNonNull(io); Objects.requireNonNull(clock);
        if (frequencyKhz <= 0) throw new IllegalArgumentException("Invalid frequency kHz");
        long start = clock.nanoTime();
        int ifAgc, rfAgc;
        IOException failure = null;
        try {
            write(io, 0x87, 0xc4, 0x41);
            write(io, 0x59, 0x05, 0x01);
            ifAgc = read(io, 0x5b);
            write(io, 0x59, 0x03, 0x01);
            rfAgc = read(io, 0x5b);
        } catch (IOException error) {
            failure = error;
            throw error;
        } finally {
            IOException cleanup = null;
            for (int[] command : new int[][]{{0x59, 0x04}, {0x88, 0x00}, {0x87, 0xc0}}) {
                try { write(io, command[0], command[1]); }
                catch (IOException error) {
                    if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error);
                }
            }
            if (cleanup != null) {
                if (failure != null) failure.addSuppressed(cleanup); else throw cleanup;
            }
        }
        int reg69 = read(io, 0x69);
        return new Result(start, clock.nanoTime(), ifAgc, rfAgc, reg69, frequencyKhz);
    }

    /** ASCOT3 gain estimate in dBm, not calibrated RF power or a Windows BDA percentage. */
    public static double ascot3Dbm(int ifAgc, int rfAgc, int reg69, int frequencyKhz) {
        if ((ifAgc & ~255) != 0 || (rfAgc & ~255) != 0 || (reg69 & ~255) != 0 || frequencyKhz <= 0)
            throw new IllegalArgumentException("Invalid ASCOT3 measurement");
        int ifGain = 870 + 100 * BPF_GAIN[reg69 & 15];
        int agc = ifAgc * 140;
        if (agc <= 9945) ifGain += (769 * (9945 - agc) + 1275) / 2550;
        int maximum = Math.max(ifAgc, rfAgc) * 140;
        int rfGain = 4020;
        for (int i = 0; i < RF_THRESHOLDS.length; i++) {
            if (frequencyKhz > RF_THRESHOLDS[i]) { rfGain = RF_MAX[i]; break; }
        }
        if (maximum >= 30090) rfGain -= 4536 + 1186 + (160 * (maximum - 30090) + 127) / 255;
        else if (maximum >= 24786) rfGain -= 4536 + (57 * (maximum - 24786) + 127) / 255;
        else if (maximum >= 8823) rfGain -= 154 + (70 * (maximum - 8823) + 127) / 255;
        else if (maximum >= 5457) rfGain -= 154;
        else if (maximum >= 4896) rfGain -= (70 * (maximum - 4896) + 127) / 255;
        return (-ifGain - rfGain) / 100.0;
    }

    private static void write(Tbs5530ControlIo io, int register, int... values) throws IOException {
        byte[] bytes = new byte[values.length + 3];
        bytes[0] = (byte) (values.length + 2); bytes[1] = (byte) 0xc0; bytes[2] = (byte) register;
        for (int i = 0; i < values.length; i++) bytes[i + 3] = (byte) values[i];
        transfer(io, 0x40, 0x80, bytes);
    }
    private static int read(Tbs5530ControlIo io, int register) throws IOException {
        transfer(io, 0x40, 0x90, new byte[]{1, (byte) 0xc0, (byte) register});
        byte[] value = new byte[1];
        transfer(io, 0xc0, 0x91, value);
        return value[0] & 255;
    }
    private static void transfer(Tbs5530ControlIo io, int type, int request, byte[] bytes) throws IOException {
        if (io.controlTransfer(type, request, 0, 0, bytes, 0, bytes.length, 2000) != bytes.length)
            throw new IOException("RSSI_SHORT_CONTROL_TRANSFER");
    }
    private Tbs5530SignalRssi() { }
}
