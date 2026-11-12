// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;

/** Debug access to the existing read-only descriptor/permission manager, not a new USB transport. */
public final class Tbs5530AutonomousAndroidAccess {
    private Tbs5530AutonomousAndroidAccess() { }
    public static Tbs5530AndroidUsbFacade.Manager manager(Context context) {
        return new Tbs5530AndroidUsbFacade.AndroidManager(context.getApplicationContext());
    }
}
