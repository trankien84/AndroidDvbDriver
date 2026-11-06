// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

/** Explicit DVB-T2 inputs. No implicit bandwidth, PLP, or frequency rounding. */
public final class Tbs5530DvbT2TuneParameters {
    public static final int AUTO_PLP = -1;
    public final long frequencyHz;
    public final int bandwidthHz;
    public final int plpId;

    /**
     * Frequency range: official cxd2878_set_property (DVB-T2); resolution: tuner kHz.
     * Only manual PLP has a reviewed mechanism in cxd2878_set_dvbt2, bank 23/AF/AD.
     */
    public Tbs5530DvbT2TuneParameters(long frequencyHz, int bandwidthHz, int plpId) {
        if (frequencyHz < 174_000_000L || frequencyHz > 868_000_000L || frequencyHz % 1000 != 0) {
            throw new IllegalArgumentException("DVB-T2 requires 174..868 MHz in exact 1 kHz units");
        }
        switch (bandwidthHz) {
            case 1_700_000: case 5_000_000: case 6_000_000: case 7_000_000: case 8_000_000: break;
            default: throw new IllegalArgumentException("Unreviewed DVB-T2 bandwidth");
        }
        if (plpId == AUTO_PLP) {
            throw new UnsupportedOperationException("Automatic PLP is not source-verified; choose explicit PLP 0..255");
        }
        if (plpId < 0 || plpId > 255) throw new IllegalArgumentException("Manual PLP must be 0..255");
        this.frequencyHz = frequencyHz;
        this.bandwidthHz = bandwidthHz;
        this.plpId = plpId;
    }
}
