// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.Collections;

/** Offline identity of the official archive entry. This is not hardware or distribution approval. */
public final class Tbs5530FirmwareValidation {
    public static final String SOURCE = "https://www.tbsdtv.com/download/document/linux/tbs-tuner-firmwares_v1.0.tar.bz2";
    public static final String ENTRY = "./dvb-usb-id5530.fw";
    public static final String SHA256 = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc";
    public static final int SIZE = 8192;
    public enum Format { RAW_LINEAR, INTEL_HEX, EEPROM_RECORDS, UNKNOWN }
    private Tbs5530FirmwareValidation() { }

    public static Tbs5530FirmwarePlan validate(byte[] image, String source, String entry, Format format) {
        if (!SOURCE.equals(source) || !ENTRY.equals(entry) || format != Format.RAW_LINEAR) {
            throw new IllegalArgumentException("Unreviewed source, entry or format");
        }
        if (image == null || image.length != SIZE) throw new IllegalArgumentException("Unexpected image size");
        byte[] digest = new byte[32];
        for (int i=0;i<digest.length;i++) digest[i]=(byte)Integer.parseInt(SHA256.substring(i*2,i*2+2),16);
        return Tbs5530FirmwarePlan.create(image,new Tbs5530FirmwarePlan.Policy(digest,
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,0x2000)),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0x2000,0x10000)),0,SIZE));
    }

    /** The legacy write is source-confirmed but has no proven FX2 mapping on this board. */
    public static boolean hardwareUploadAllowed() { return false; }
    public static String hardwareBlocker() { return "UNVERIFIED_7F92_MAPPING_AND_BOARD_RECOVERY"; }
}
