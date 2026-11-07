// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.Arrays;

/** Synthetic PAT/PMT/PES, never recorded broadcast or a firmware payload. */
final class Tbs5530AndroidTsFixture {
    static byte[] stream() {
        byte[] bytes = new byte[564]; Arrays.fill(bytes, (byte) 0xff);
        section(bytes, 0, 0, new byte[]{0,(byte)0xb0,13,0,7,(byte)0xc1,0,0,0,1,(byte)0xe1,0});
        section(bytes, 188, 0x100, new byte[]{2,(byte)0xb0,18,0,1,(byte)0xc1,0,0,(byte)0xe1,1,(byte)0xf0,0,0x1b,(byte)0xe1,1,(byte)0xf0,0});
        bytes[376] = 0x47; bytes[377] = 0x41; bytes[378] = 1; bytes[379] = 0x10;
        bytes[380] = 0; bytes[381] = 0; bytes[382] = 1; bytes[383] = (byte)0xe0;
        return bytes;
    }
    private static void section(byte[] target, int at, int pid, byte[] body) {
        target[at] = 0x47; target[at + 1] = (byte)(0x40 | pid >> 8); target[at + 2] = (byte)pid;
        target[at + 3] = 0x10; target[at + 4] = 0;
        System.arraycopy(body, 0, target, at + 5, body.length);
        int crc = -1;
        for (byte value : body) {
            crc ^= (value & 255) << 24;
            for (int bit = 0; bit < 8; bit++) crc = crc << 1 ^ (crc < 0 ? 0x04c11db7 : 0);
        }
        for (int i = 0; i < 4; i++) target[at + 5 + body.length + i] = (byte)(crc >>> (24 - i * 8));
    }
}
