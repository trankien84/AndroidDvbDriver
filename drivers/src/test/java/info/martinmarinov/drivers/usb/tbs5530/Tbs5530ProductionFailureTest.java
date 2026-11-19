// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import java.io.IOException;
import java.io.InterruptedIOException;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class Tbs5530ProductionFailureTest {
    @Test public void localizedPermissionAndTimeoutAndReloadRemainDistinctThroughTransportWrappers() {
        Context context=mock(Context.class);
        when(context.getString(anyInt())).thenAnswer(a -> "resource-"+a.getArgument(0));
        Tbs5530ProductionFailure.Kind[] kinds={Tbs5530ProductionFailure.Kind.BUSY,Tbs5530ProductionFailure.Kind.PERMISSION,
                Tbs5530ProductionFailure.Kind.PERMISSION_TIMEOUT,Tbs5530ProductionFailure.Kind.HANDOFF_TIMEOUT,
                Tbs5530ProductionFailure.Kind.FIRMWARE,Tbs5530ProductionFailure.Kind.DESCRIPTOR,
                Tbs5530ProductionFailure.Kind.BOOTSTRAP,Tbs5530ProductionFailure.Kind.RELOAD,
                Tbs5530ProductionFailure.Kind.REMOVED};
        int[] texts={R.string.tbs5530_busy,R.string.tbs5530_permission_denied,R.string.tbs5530_permission_timeout,
                R.string.tbs5530_handoff_timeout,R.string.tbs5530_firmware_failed,R.string.tbs5530_descriptor_failed,
                R.string.tbs5530_bootstrap_failed,R.string.tbs5530_reload_failed,R.string.tbs5530_removed};
        for(int i=0;i<kinds.length;i++) {
            IOException error=new IOException("outer transport",new Tbs5530ProductionFailure(kinds[i],"private detail"));
            DvbException result=Tbs5530ProductionFailure.localized(context,DvbException.ErrorCode.IO_EXCEPTION,
                    R.string.tbs5530_open_failed,error);
            assertEquals("resource-"+texts[i],result.getMessage()); assertSame(error,result.getCause());
            assertFalse(result.getMessage().contains("private detail"));
        }
    }
    @Test public void rootRemovalOrInterruptionIsNotMisreportedAsReloadFailure() {
        IOException removed=new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.RELOAD,"reload",
                new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.REMOVED,"removed"));
        assertEquals(Tbs5530ProductionFailure.Kind.REMOVED,Tbs5530ProductionFailure.classify(removed));
        IOException interrupted=new Tbs5530ProductionFailure(Tbs5530ProductionFailure.Kind.RELOAD,"reload",new InterruptedIOException());
        assertEquals(Tbs5530ProductionFailure.Kind.INTERRUPTED,Tbs5530ProductionFailure.classify(interrupted));
        assertNull(Tbs5530ProductionFailure.classify(new IOException("unclassified")));
    }
}
