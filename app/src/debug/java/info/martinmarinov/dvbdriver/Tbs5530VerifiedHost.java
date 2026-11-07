// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;

final class Tbs5530VerifiedHost {
    static String profile(String model, int api) throws IOException {
        if ("GM1900".equals(model) && api == 30) return "oneplus-gm1900-api30";
        if ("SM-X926B".equals(model) && api == 36) return "samsung-sm-x926b-api36";
        throw new IOException("Unverified Android model/API");
    }
    static String historyPrefix(String profile) throws IOException {
        if ("oneplus-gm1900-api30".equals(profile)) return "autonomous-v1";
        if ("samsung-sm-x926b-api36".equals(profile)) return "autonomous-v1-" + profile;
        throw new IOException("Unverified host history profile");
    }
}
