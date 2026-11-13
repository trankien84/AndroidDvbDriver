// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530SignalSamplerTest {
    @Test public void monitorIoErrorsDoNotStopConcurrentBulkOrReleaseItsOwner() throws Exception {
        Tbs5530Session session = new Tbs5530Session();
        AtomicInteger releases = new AtomicInteger(), closes = new AtomicInteger();
        long generation = session.requestPermission(); session.permissionResult(generation, true);
        session.connected(generation, new Tbs5530Session.Resources() {
            public void cancel() { }
            public void release() { releases.incrementAndGet(); }
            public void close() { closes.incrementAndGet(); }
        });
        session.interfaceClaimed(generation); session.ready(generation);
        CountDownLatch bulkStarted = new CountDownLatch(1), releaseBulk = new CountDownLatch(1);
        CountDownLatch sampledTwice = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread bulk = new Thread(() -> {
            try {
                session.receiveTransaction(generation, () -> {
                    bulkStarted.countDown();
                    try { if (!releaseBulk.await(4, TimeUnit.SECONDS)) throw new IOException("Fixture timeout"); }
                    catch (IOException | InterruptedException error) { throw new Tbs5530ControlIo.TransferException(
                            Tbs5530ControlIo.FailureKind.IO_FAILURE, "Fixture", error); }
                });
            } catch (Throwable error) { failure.set(error); }
        });
        bulk.start();
        Tbs5530SignalSampler sampler = new Tbs5530SignalSampler((thread, stopping) -> {
            Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor((type, request, value, index, bytes, offset, length, timeout) -> {
                assertSame(thread, Thread.currentThread());
                if (request == 0x91) throw new IOException("Synthetic I2C timeout");
                return length;
            }, System::nanoTime);
            return () -> {
                Tbs5530SignalMonitor.Sample[] sample = new Tbs5530SignalMonitor.Sample[1];
                session.transaction(generation, () -> sample[0] = monitor.sample());
                sampledTwice.countDown(); return sample[0];
            };
        });
        try {
            assertTrue(bulkStarted.await(2, TimeUnit.SECONDS)); sampler.start();
            assertTrue("EP0 was blocked by the receive lease", sampledTwice.await(2, TimeUnit.SECONDS));
            sampler.close();
            assertFalse(sampler.lockLost()); assertNull(sampler.failure());
            assertTrue(sampler.samples().size() >= 2);
            for (Tbs5530SignalMonitor.Sample sample : sampler.samples()) assertFalse(sample.errors.isEmpty());
            assertTrue(bulk.isAlive()); assertEquals(Tbs5530Session.State.READY, session.getState());
            session.requestCancellation(generation, false);
            assertEquals(0, releases.get()); assertEquals(0, closes.get());
        } finally { sampler.close(); releaseBulk.countDown(); bulk.join(3000); }
        assertFalse(bulk.isAlive()); assertNull(failure.get());
        assertEquals(1, releases.get()); assertEquals(1, closes.get());
    }
}
