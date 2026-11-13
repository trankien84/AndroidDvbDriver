// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

/** One frontend variable per fresh, explicitly granted debug attachment. */
public enum Tbs5530FrontendExperiment {
    BASELINE_AUTO,
    MANUAL_PLP0_K36,
    B2_RELOAD_AFTER_SET_CONFIG,
    B5_K25_MANUAL_PLP0,
    B5_K25_AUTO,
    B5_K36_MANUAL_PLP0;

    public boolean isB5() {
        return this == B5_K25_MANUAL_PLP0 || this == B5_K25_AUTO || this == B5_K36_MANUAL_PLP0;
    }

    public boolean automatic() { return this == BASELINE_AUTO || this == B5_K25_AUTO; }

    public long frequencyHz() {
        return this == B5_K25_MANUAL_PLP0 || this == B5_K25_AUTO ? 506000000L : 594000000L;
    }

    public static Tbs5530FrontendExperiment parse(String value) throws IOException {
        if (value == null) return BASELINE_AUTO;
        try { return valueOf(value); }
        catch (IllegalArgumentException error) { throw new IOException("Unknown frontend experiment", error); }
    }

    public void requireOptions(String readApi, boolean mirror, boolean alt0, boolean controlOnly,
                               boolean sonyReadback) throws IOException {
        boolean stream = (this == B2_RELOAD_AFTER_SET_CONFIG || isB5()) && "NATIVE_USBFS_STREAM".equals(readApi);
        if (isB5() && !stream) throw new IOException("B5 requires native continuous stream");
        if (this != BASELINE_AUTO && ((!"NATIVE_USBFS_URB_8X4096".equals(readApi) && !stream) || !mirror
                || alt0 || controlOnly || sonyReadback))
            throw new IOException("MANUAL_PLP0_K36 requires native4096/mirror and no other experiment");
    }

    public Tbs5530DvbT2TuneParameters parameters() {
        return new Tbs5530DvbT2TuneParameters(frequencyHz(), 8000000,
                automatic() ? Tbs5530DvbT2TuneParameters.Selection.automatic()
                        : Tbs5530DvbT2TuneParameters.Selection.manual(0));
    }

    public boolean matches(Tbs5530DvbT2TuneParameters value) {
        return value != null && value.frequencyHz == frequencyHz() && value.bandwidthHz == 8000000
                && (automatic()
                ? value.selection.mode == Tbs5530DvbT2TuneParameters.Mode.AUTO && value.selection.id == null
                : value.selection.mode == Tbs5530DvbT2TuneParameters.Mode.MANUAL && Integer.valueOf(0).equals(value.selection.id));
    }
}
