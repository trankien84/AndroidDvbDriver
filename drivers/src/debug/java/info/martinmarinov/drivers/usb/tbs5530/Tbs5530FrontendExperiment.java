// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

/** One frontend variable per fresh, explicitly granted debug attachment. */
public enum Tbs5530FrontendExperiment {
    BASELINE_AUTO,
    MANUAL_PLP0_K36,
    B2_RELOAD_AFTER_SET_CONFIG;

    public static Tbs5530FrontendExperiment parse(String value) throws IOException {
        if (value == null) return BASELINE_AUTO;
        try { return valueOf(value); }
        catch (IllegalArgumentException error) { throw new IOException("Unknown frontend experiment", error); }
    }

    public void requireOptions(String readApi, boolean mirror, boolean alt0, boolean controlOnly,
                               boolean sonyReadback) throws IOException {
        if (this != BASELINE_AUTO && (!"NATIVE_USBFS_URB_8X4096".equals(readApi) || !mirror
                || alt0 || controlOnly || sonyReadback))
            throw new IOException("MANUAL_PLP0_K36 requires native4096/mirror and no other experiment");
    }

    public Tbs5530DvbT2TuneParameters parameters() {
        return new Tbs5530DvbT2TuneParameters(594000000L, 8000000,
                this != BASELINE_AUTO ? Tbs5530DvbT2TuneParameters.Selection.manual(0)
                        : Tbs5530DvbT2TuneParameters.Selection.automatic());
    }

    public boolean matches(Tbs5530DvbT2TuneParameters value) {
        return value != null && value.frequencyHz == 594000000L && value.bandwidthHz == 8000000
                && (this != BASELINE_AUTO
                ? value.selection.mode == Tbs5530DvbT2TuneParameters.Mode.MANUAL && Integer.valueOf(0).equals(value.selection.id)
                : value.selection.mode == Tbs5530DvbT2TuneParameters.Mode.AUTO && value.selection.id == null);
    }
}
