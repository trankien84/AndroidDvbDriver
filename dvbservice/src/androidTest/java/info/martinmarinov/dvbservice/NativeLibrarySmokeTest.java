/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2022 by Signalware Ltd <driver at aerialtv.eu>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package info.martinmarinov.dvbservice;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.martinmarinov.usbxfer.UsbHiSpeedBulk;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NativeLibrarySmokeTest {
    @Test
    public void usbTransferLibraryLoadsWithoutOpeningUsb() {
        // Static initialization loads the library without creating requests or issuing USB ioctls.
        assertTrue("libUsbXfer must load on the Android runtime",
                UsbHiSpeedBulk.IS_PLATFORM_SUPPORTED);
        System.loadLibrary("UsbXfer");
    }
}
