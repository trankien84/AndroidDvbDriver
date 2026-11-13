// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530B5OffControlTest {
    private boolean write(int address, int register, int... values) {
        byte[] payload = new byte[values.length + 3];
        payload[0] = (byte)(values.length + 2); payload[1] = (byte)address; payload[2] = (byte)register;
        for (int i = 0; i < values.length; i++) payload[i + 3] = (byte)values[i];
        return Tbs5530ReloadAndroidManager.b5OffTunerCommand(0x40, 0x80, payload, 0, payload.length);
    }
    @Test public void exactUserRssiAndSlvxRepeaterAreTheOnlyOffTunerWrites() {
        assertTrue(write(0xcc, 0, 0)); assertTrue(write(0xcc, 8, 1)); assertTrue(write(0xcc, 8, 0));
        assertTrue(write(0xc0, 0x87, 0xc4, 0x41)); assertTrue(write(0xc0, 0x59, 5, 1));
        assertTrue(write(0xc0, 0x59, 3, 1)); assertTrue(write(0xc0, 0x59, 4));
        assertTrue(write(0xc0, 0x88, 0)); assertTrue(write(0xc0, 0x87, 0xc0));
        assertFalse(write(0xc0, 0x88, 4)); assertFalse(write(0xc0, 0x59, 5, 2));
        assertFalse(write(0xcc, 8, 2)); assertFalse(write(0xc8, 8, 1)); assertFalse(write(0xc0, 0x69, 0));
    }
    @Test public void offTunerReadPointersCannotExpandAddressRegisterOrLength() {
        for (int register : new int[]{0x5b, 0x69})
            assertTrue(Tbs5530ReloadAndroidManager.b5OffTunerCommand(0x40, 0x90,
                    new byte[]{1, (byte)0xc0, (byte)register}, 0, 3));
        assertFalse(Tbs5530ReloadAndroidManager.b5OffTunerCommand(0x40, 0x90, new byte[]{2, (byte)0xc0, 0x5b}, 0, 3));
        assertFalse(Tbs5530ReloadAndroidManager.b5OffTunerCommand(0x40, 0x90, new byte[]{1, (byte)0xc0, 0}, 0, 3));
        assertFalse(Tbs5530ReloadAndroidManager.b5OffTunerCommand(0x40, 0xa0, new byte[]{1, (byte)0xc0, 0x5b}, 0, 3));
    }
}
