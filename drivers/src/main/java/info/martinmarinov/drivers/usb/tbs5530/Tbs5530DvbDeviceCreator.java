// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import info.martinmarinov.drivers.usb.DvbUsbDevice;

/** Discovery may bootstrap cold RAM, but returns only an operational, unopened device. */
public final class Tbs5530DvbDeviceCreator implements DvbUsbDevice.Creator {
    private static final DeviceFilter FILTER = new DeviceFilter(0x734c, 0x5530, "TBS5530 DVB-T2");
    @Override public Set<DeviceFilter> getSupportedDevices() { return Collections.singleton(FILTER); }
    @Override public DvbDevice create(UsbDevice device, Context context, DeviceFilter filter) throws DvbException {
        if (!Tbs5530ProductionAttachment.target(device)) return null;
        Tbs5530ProductionAttachment owner = null;
        try {
            owner = new Tbs5530ProductionAttachment(context, device);
            UsbDevice operational = owner.prepare(true);
            Tbs5530DvbDevice result = new Tbs5530DvbDevice(context, operational, filter, owner);
            owner = null;
            return result;
        } catch (IOException | RuntimeException e) {
            close(owner, e);
            throw Tbs5530ProductionFailure.localized(context, DvbException.ErrorCode.CANNOT_OPEN_USB,
                    R.string.tbs5530_discovery_failed, e);
        } catch (Error e) {
            close(owner, e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); close(owner, e);
            throw Tbs5530ProductionFailure.localized(context, DvbException.ErrorCode.IO_EXCEPTION,
                    R.string.tbs5530_interrupted, e);
        }
    }
    private static void close(Tbs5530ProductionAttachment owner, Throwable error) {
        if (owner != null) try { owner.close(); }
        catch (IOException | RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }
}
