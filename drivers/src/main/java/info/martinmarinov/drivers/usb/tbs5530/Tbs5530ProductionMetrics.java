// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

/** Framework units: milli-dB SNR, BER ratio scaled to 0xffff, unknown is never perfect zero. */
final class Tbs5530ProductionMetrics {
    static int snr(Tbs5530SignalMetrics sample) {
        return sample.lock != Tbs5530SignalMetrics.Lock.LOCKED || sample.officialCnrMilliDb == null
                ? -1 : sample.officialCnrMilliDb;
    }
    static int ber(Tbs5530SignalMetrics sample) {
        return sample.lock != Tbs5530SignalMetrics.Lock.LOCKED || sample.ber == null
                ? 0xffff : (int) Math.round(Math.max(0, Math.min(1, sample.ber)) * 0xffff);
    }
    /** Display estimate only: -100 dBm = 0%, -20 dBm = 100%; not calibrated BDA parity. */
    static int rfPercentage(double dbm) {
        if (Double.isNaN(dbm) || Double.isInfinite(dbm)) return -1;
        return (int) Math.round(Math.max(0, Math.min(100, (dbm + 100) * 100 / 80)));
    }
    private Tbs5530ProductionMetrics() { }
}
