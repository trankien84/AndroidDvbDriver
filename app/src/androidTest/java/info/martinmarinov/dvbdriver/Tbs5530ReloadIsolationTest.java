// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import static org.junit.Assert.*;

/** Emulator-only refusals. Never creates a reload store, opens USB or initializes physical history. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530ReloadIsolationTest {
    @Test public void b2RejectsUnsupportedHostBeforeSessionNamespaceOrDeviceIo() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int before = context.getFilesDir().list().length;
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context, "NATIVE_USBFS_URB_8X4096",
                "GM1900",30,1,true,false,null,false,false,"B2_RELOAD_AFTER_SET_CONFIG"));
        assertEquals(before,context.getFilesDir().list().length);
    }
    @Test public void realReloadJniRejectsInvalidFdOrImageSizeWithoutAnyUsbSyscall() throws Exception {
        System.loadLibrary("Tbs5530UsbfsDiagnostic");
        java.lang.reflect.Method create = info.martinmarinov.usbxfer.NativeFx2Control.class.getDeclaredMethod("create", int.class, byte[].class);
        create.setAccessible(true);
        assertEquals(0L, ((Long)create.invoke(null,-1,new byte[8192])).longValue());
        assertEquals(0L, ((Long)create.invoke(null,Integer.MIN_VALUE,new byte[8192])).longValue());
        // fd 0 is never used: JNI rejects each invalid image length before allocation or ioctl.
        for (int size : new int[]{0,8191,8193}) assertEquals(0L, ((Long)create.invoke(null,0,new byte[size])).longValue());
    }
    @Test public void combinedB2OptionsRefuseEvenBeforeUnsupportedHostOrMissingStore() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X8192",
                "SM-X926B",36,1,true,false,null,false,false,"B2_RELOAD_AFTER_SET_CONFIG"));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096",
                "SM-X926B",36,1,true,true,null,false,false,"B2_RELOAD_AFTER_SET_CONFIG"));
        assertThrows(IOException.class, () -> new Tbs5530AutonomousSession(context,"NATIVE_USBFS_URB_8X4096",
                "SM-X926B",36,1,true,false,null,false,true,"B2_RELOAD_AFTER_SET_CONFIG"));
    }
}
