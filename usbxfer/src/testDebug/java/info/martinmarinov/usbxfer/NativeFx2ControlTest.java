// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeFx2ControlTest {
    @Test public void invalidFdSizeOrHashRefusesBeforeLoadingJniOrDispatchingIo() {
        assertThrows(IOException.class, () -> new NativeFx2Control(-1, new byte[8192]));
        assertThrows(IOException.class, () -> new NativeFx2Control(1, null));
        assertThrows(IOException.class, () -> new NativeFx2Control(1, new byte[8191]));
        assertThrows(IOException.class, () -> new NativeFx2Control(1, new byte[8193]));
        IOException error = assertThrows(IOException.class, () -> new NativeFx2Control(1, new byte[8192]));
        assertEquals("Native FX2 image hash mismatch", error.getMessage());
    }
}
