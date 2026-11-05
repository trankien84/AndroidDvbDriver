// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class Tbs5530BootstrapStopTest {
    @Test public void sanitizedCauseAndSuppressedChainCannotLeakMessagesOrLoop() {
        IOException privateCause = new IOException("serial or private path");
        Tbs5530BootstrapStop stop = new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.PRE_NATIVE_ACK_TIMEOUT,
                "secret permit", privateCause);
        stop.addSuppressed(new Tbs5530BootstrapStop(Tbs5530BootstrapStop.Kind.CLEANUP_FAILED, "private cleanup"));
        privateCause.addSuppressed(stop);
        java.util.List<String> chain = Tbs5530BootstrapStop.sanitizedChain(stop);
        assertEquals(java.util.Arrays.asList("PRE_NATIVE_ACK_TIMEOUT", "IOException", "CLEANUP_FAILED"), chain);
        assertFalse(chain.toString().contains("serial")); assertFalse(chain.toString().contains("permit"));
    }
    @Test public void everyStopKindSurvivesTransportWrappingWithoutExportingMessage() {
        for (Tbs5530BootstrapStop.Kind kind : Tbs5530BootstrapStop.Kind.values()) {
            IOException original = new IOException("private identity must not enter report");
            Tbs5530BootstrapStop stop = new Tbs5530BootstrapStop(kind, "local diagnostic", original);
            Throwable wrapper = new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,
                    "transport boundary", stop);
            assertEquals(kind, Tbs5530BootstrapStop.kindOf(wrapper));
            assertSame(original, stop.getCause());
            assertFalse(Tbs5530BootstrapStop.kindOf(wrapper).name().contains("private"));
        }
    }
}
