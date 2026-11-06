// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

/** Explicit DVB-T2 inputs. No implicit bandwidth, PLP, or frequency rounding. */
public final class Tbs5530DvbT2TuneParameters {
    /** Legacy integer sentinel is rejected; use Selection.automatic(), never a register byte. */
    public static final int AUTO_PLP = -1;
    public enum Mode { MANUAL, AUTO, MONITOR_RESOLVED }
    public static final class Selection {
        public final Mode mode;
        public final Integer id;
        public final String evidence;
        private Selection(Mode mode, Integer id, String evidence) {
            this.mode = mode; this.id = id; this.evidence = evidence;
        }
        public static Selection manual(int id) {
            return new Selection(Mode.MANUAL, Tbs5530Bridge.manualPlp(id), "EXPLICIT_MANUAL_NOT_RF_MEASURED");
        }
        public static Selection automatic() {
            return new Selection(Mode.AUTO, null, "CROSS_CHIP_ANALOGY_CXD2841ER_NOT_CXD6802_VERIFIED");
        }
        /** Reserved representation only: no verified CXD6802 L1 monitor exists in this port. */
        public static Selection monitorResolved(int id) {
            return new Selection(Mode.MONITOR_RESOLVED, Tbs5530Bridge.manualPlp(id), "UNVERIFIED_MONITOR");
        }
        public String transcript() {
            return "mode=" + mode + "|id=" + (id == null ? "NOT_MEASURED" : id)
                    + "|evidence=" + evidence + "|activeDataPLP=UNVERIFIED|commonPLP=UNVERIFIED";
        }
    }
    public final long frequencyHz;
    public final int bandwidthHz;
    /** Compatibility field only. Register programs must use selection.mode and selection.id. */
    public final int plpId;
    public final Selection selection;

    public Tbs5530DvbT2TuneParameters(long frequencyHz, int bandwidthHz, int plpId) {
        this(frequencyHz, bandwidthHz, legacySelection(plpId));
    }
    private static Selection legacySelection(int id) {
        if (id == AUTO_PLP) throw new UnsupportedOperationException("Use explicit AUTO selection and experimental approval");
        return Selection.manual(id);
    }
    /** Frequency range: official cxd2878_set_property; resolution: tuner kHz. */
    public Tbs5530DvbT2TuneParameters(long frequencyHz, int bandwidthHz, Selection selection) {
        if (frequencyHz < 174_000_000L || frequencyHz > 868_000_000L || frequencyHz % 1000 != 0) {
            throw new IllegalArgumentException("DVB-T2 requires 174..868 MHz in exact 1 kHz units");
        }
        switch (bandwidthHz) {
            case 1_700_000: case 5_000_000: case 6_000_000: case 7_000_000: case 8_000_000: break;
            default: throw new IllegalArgumentException("Unreviewed DVB-T2 bandwidth");
        }
        if (selection == null) throw new IllegalArgumentException("Explicit PLP selection required");
        this.frequencyHz = frequencyHz;
        this.bandwidthHz = bandwidthHz;
        this.selection = selection;
        this.plpId = selection.id == null ? AUTO_PLP : selection.id;
    }
}
