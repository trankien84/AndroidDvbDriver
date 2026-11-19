// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import info.martinmarinov.drivers.DvbException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamFailureReporterTest {
    private final List<Exception> shown = new ArrayList<>(), logged = new ArrayList<>();
    private final StreamFailureReporter reporter = new StreamFailureReporter(shown::add, logged::add);

    @Test public void confirmedUnplugReportsOnceAcrossPumpAndPipe() throws InterruptedException {
        DvbException detach = new DvbException(DvbException.ErrorCode.DEVICE_DISCONNECTED, "fixture");
        IOException pipe = new InterruptedIOException("read interrupted");
        IOException cleanup = new IOException("TS receive stopped");
        Thread pump = new Thread(() -> reporter.report(detach));
        pump.start();
        pump.join();
        reporter.report(pipe);
        reporter.beginClosing();
        reporter.report(cleanup);
        assertEquals(Collections.singletonList(detach), shown);
        assertEquals(Arrays.asList(pipe, cleanup), logged);
    }

    @Test public void userStopSuppressesPipeFailureEvenWithoutThreadInterrupt() {
        IOException pipe = new InterruptedIOException("read interrupted");
        reporter.beginClosing();
        reporter.report(pipe);
        assertTrue(shown.isEmpty());
        assertEquals(Collections.singletonList(pipe), logged);
    }
}
