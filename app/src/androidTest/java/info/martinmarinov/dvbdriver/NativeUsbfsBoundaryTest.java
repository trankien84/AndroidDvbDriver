// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import info.martinmarinov.usbxfer.NativeUsbfsUrb;

import static org.junit.Assert.*;

/** Loads the real diagnostic JNI, but never calls fill/poll/discard or obtains a USB connection. */
@RunWith(AndroidJUnit4.class)
public class NativeUsbfsBoundaryTest {
    private static Method nativeCreate() throws Exception {
        Class.forName("info.martinmarinov.usbxfer.NativeUsbfsUrb$Bindings", true,
                NativeUsbfsUrb.class.getClassLoader());
        Method method = NativeUsbfsUrb.class.getDeclaredMethod("create", int.class, int.class, int.class);
        method.setAccessible(true);
        return method;
    }

    @Test public void realLibraryLoadsAndNativeRejectsNegativeFdBeforeAllocation() throws Exception {
        Method create = nativeCreate();
        for (int fd : new int[]{-1, Integer.MIN_VALUE}) {
            for (int budget : new int[]{8, 16, 256}) {
                assertEquals(0L, ((Long) create.invoke(null, fd, budget, 4096)).longValue());
            }
        }
    }

    @Test public void realNativeRejectsInvalidBudgetsWithoutSubmitting() throws Exception {
        Method create = nativeCreate();
        // This does not open or use fd 0: JNI create rejects budget before allocation or any ioctl.
        for (int budget : new int[]{Integer.MIN_VALUE, 0, 7, 257, Integer.MAX_VALUE}) {
            assertEquals(0L, ((Long) create.invoke(null, 0, budget, 4096)).longValue());
        }
    }

    @Test public void streamJniLoadsAndRejectsBadFdOrBoundsWithoutAnyIo() throws Exception {
        Class<?> stream = Class.forName("info.martinmarinov.usbxfer.NativeUsbfsStream");
        Class.forName("info.martinmarinov.usbxfer.NativeUsbfsStream$Bindings", true, stream.getClassLoader());
        Method create = stream.getDeclaredMethod("create", int.class, int.class, int.class, int.class, int.class);
        create.setAccessible(true);
        assertEquals(0L, ((Long)create.invoke(null, -1, 16, 16384, 4194304, 2000)).longValue());
        assertEquals(0L, ((Long)create.invoke(null, 0, 7, 16384, 4194304, 2000)).longValue());
        assertEquals(0L, ((Long)create.invoke(null, 0, 16, 16385, 4194304, 2000)).longValue());
    }
    @Test public void b4FileJniRejectsFdAndBoundsWithoutOpeningAnyFileOrUsb() throws Exception {
        Class<?> stream = Class.forName("info.martinmarinov.usbxfer.NativeUsbfsStream");
        Class.forName("info.martinmarinov.usbxfer.NativeUsbfsStream$Bindings", true, stream.getClassLoader());
        Method create = stream.getDeclaredMethod("createFile", int.class, int.class, int.class, int.class, int.class);
        create.setAccessible(true);
        assertEquals(0L, ((Long)create.invoke(null, -1, 16, 16384, 268435456, 60000)).longValue());
        assertEquals(0L, ((Long)create.invoke(null, 0, 24, 16384, 268435456, 30000)).longValue());
        assertEquals(0L, ((Long)create.invoke(null, 0, 16, 65536, 268435456, 30000)).longValue());
        assertEquals(0L, ((Long)create.invoke(null, 0, 16, 16384, 268435456, 60001)).longValue());
    }

    @Test public void publicConstructorRejectsNegativeFdWithoutConsentOrAudit() throws Exception {
        AtomicInteger consentCalls = new AtomicInteger();
        StringWriter audit = new StringWriter();
        assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                -1, 16, 5000, "native-boundary", consentCalls::incrementAndGet, audit));
        assertEquals(0, consentCalls.get());
        assertEquals("", audit.toString());
    }

    @Test public void publicConstructorRejectsBoundsBeforeNativeAllocation() throws Exception {
        AtomicInteger consentCalls = new AtomicInteger();
        StringWriter audit = new StringWriter();
        for (int duration : new int[]{0, -1, 10001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                    -1, 16, duration, "native-boundary", consentCalls::incrementAndGet, audit));
        }
        for (int budget : new int[]{0, 7, 257, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                    -1, budget, 5000, "native-boundary", consentCalls::incrementAndGet, audit));
        }
        assertEquals(0, consentCalls.get());
        assertEquals("", audit.toString());
    }

    @Test public void publicConstructorRejectsMalformedRunIdAndMissingObservers() throws Exception {
        AtomicInteger consentCalls = new AtomicInteger();
        StringWriter audit = new StringWriter();
        for (String runId : new String[]{null, "", "../other", "\"json", "run\nnext", "run with spaces"}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                    -1, 16, 5000, runId, consentCalls::incrementAndGet, audit));
        }
        assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                -1, 16, 5000, "native-boundary", null, audit));
        assertThrows(IllegalArgumentException.class, () -> new NativeUsbfsUrb(
                -1, 16, 5000, "native-boundary", consentCalls::incrementAndGet, null));
        assertEquals(0, consentCalls.get());
        assertEquals("", audit.toString());
    }
}
