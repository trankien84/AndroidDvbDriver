// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.usbxfer.ByteSink;
import info.martinmarinov.usbxfer.ByteSource;
import info.martinmarinov.usbxfer.NativeUsbfsByteSource.ReceiveFailure;
import static org.junit.Assert.*;

public class Tbs5530ProductionTest {
    @Test public void nativeReadFailureRequiresDetachEvidenceAndRetainsOriginalCause() throws Exception {
        for (int[] detail : new int[][]{{-71, 0}, {-19, 0}, {-108, 0}, {0, 19}}) {
            ReceiveFailure original = new ReceiveFailure(2, detail[0], detail[1]);
            try (FailedRead read = new FailedRead(original)) {
                read.confirmation.detached();
                read.assertDisconnected(read.run());
            }
        }
    }

    @Test public void nativeErrorWaitsForDetachBroadcast() throws Exception {
        try (FailedRead read = new FailedRead(new ReceiveFailure(2, -71, 0))) {
            Future<IOException> result = read.start();
            Thread.sleep(300);
            assertFalse(result.isDone());
            read.confirmation.detached();
            read.assertDisconnected(result.get(2, TimeUnit.SECONDS));
            assertTrue(read.trace.contains("detach_broadcast"));
            assertTrue(read.present.get());
        }
    }

    @Test public void nativeErrorWaitsForDeviceListToLoseTuner() throws Exception {
        try (FailedRead read = new FailedRead(new ReceiveFailure(2, -71, 0))) {
            Future<IOException> result = read.start();
            Thread.sleep(1500);
            assertFalse(result.isDone());
            read.present.set(false);
            read.assertDisconnected(result.get(2, TimeUnit.SECONDS));
            assertTrue(read.trace.contains("device_list_gone"));
            assertFalse(read.trace.contains("detach_broadcast"));
        }
    }

    @Test public void nativeErrorWithTunerStillPresentTimesOutWithoutMaskingError() throws Exception {
        try (FailedRead read = new FailedRead(new ReceiveFailure(2, -71, 0))) {
            long before = System.nanoTime();
            assertSame(read.original, read.run());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
            assertTrue("Waited " + elapsed + " ms", elapsed >= 3000 && elapsed < 4000);
            assertEquals(Collections.singletonList(read.original), read.reports);
            assertTrue(read.trace.stream().anyMatch(event -> event.startsWith("decision=USB_ERROR ")));
        }
    }

    @Test public void closingDuringConfirmationDoesNotBlockOrReport() throws Exception {
        try (FailedRead read = new FailedRead(new ReceiveFailure(2, -71, 0))) {
            Future<InterruptedException> result = read.worker.submit(() ->
                    assertThrows(InterruptedException.class, () -> read.gate.readNext((bytes, count) -> { })));
            assertTrue(read.failed.await(2, TimeUnit.SECONDS));
            Thread.sleep(500);
            assertFalse(result.isDone());
            long before = System.nanoTime();
            read.gate.close();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 200);
            result.get(2, TimeUnit.SECONDS);
            assertTrue(read.reports.isEmpty());
            assertTrue(read.trace.stream().anyMatch(event -> event.startsWith("decision=STOPPED ")));
        }
    }

    @Test public void nativeErrorThatIsNotDetachCandidateNeverQueriesDeviceList() throws Exception {
        try (FailedRead read = new FailedRead(new ReceiveFailure(2, -75, 0))) {
            read.present.set(false);
            assertSame(read.original, read.run());
            assertEquals(Collections.singletonList(read.original), read.reports);
            assertEquals(0, read.polls.get());
        }
    }

    @Test public void controlReadWaitsForRemovalInsteadOfReportingI2cError() throws Exception {
        Tbs5530DetachConfirmation confirmation = new Tbs5530DetachConfirmation(() -> true,
                System::nanoTime, (now, event) -> { });
        Tbs5530SignalMonitor monitor = new Tbs5530SignalMonitor((type, request, value, index, bytes, offset, length, timeout) -> {
            throw new Tbs5530DetachConfirmation.ControlTransferFailure(-1);
        }, System::nanoTime, false);
        DvbException original = new DvbException(DvbException.ErrorCode.IO_EXCEPTION,
                "SNR read failed", monitor.sample().errors.get(0).detachCandidate);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        try {
            Future<DvbException> result = worker.submit(() -> confirmation.controlFailure(original, () -> {
                entered.countDown();
                return false;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            confirmation.detached();
            DvbException error = result.get(2, TimeUnit.SECONDS);
            assertEquals(DvbException.ErrorCode.DEVICE_DISCONNECTED, error.getErrorCode());
            assertSame(original, error.getCause());
        } finally { worker.shutdownNow(); }
    }

    private static final class FailedRead implements AutoCloseable {
        final ReceiveFailure original;
        final AtomicBoolean present = new AtomicBoolean(true);
        final AtomicInteger polls = new AtomicInteger();
        final CountDownLatch failed = new CountDownLatch(1);
        final List<String> trace = Collections.synchronizedList(new ArrayList<>());
        final List<IOException> reports = new ArrayList<>();
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final Tbs5530DetachConfirmation confirmation = new Tbs5530DetachConfirmation(() -> {
            polls.incrementAndGet();
            return present.get();
        }, System::nanoTime, (now, event) -> {
            trace.add(event);
            if (event.startsWith("native_fail ")) failed.countDown();
        });
        final Tbs5530StreamGate gate;

        FailedRead(ReceiveFailure original) {
            this.original = original;
            gate = new Tbs5530StreamGate(() -> new ByteSource() {
                @Override public void open() { }
                @Override public void close() { }
                @Override public void readNext(ByteSink sink) throws IOException { throw original; }
            }, true, reports::add, confirmation, () -> false);
        }

        IOException run() {
            return assertThrows(IOException.class, () -> gate.readNext((bytes, count) -> { }));
        }

        Future<IOException> start() throws InterruptedException {
            Future<IOException> result = worker.submit(this::run);
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            return result;
        }

        void assertDisconnected(IOException error) {
            assertEquals(DvbException.ErrorCode.DEVICE_DISCONNECTED, ((DvbException) error).getErrorCode());
            assertSame(original, error.getCause());
            assertEquals(Collections.singletonList(error), reports);
        }

        @Override public void close() throws IOException {
            try { gate.close(); }
            finally { worker.shutdownNow(); }
        }
    }

    private static Tbs5530FirmwarePlan syntheticPlan() throws Exception {
        byte[] image = new byte[8192];
        return Tbs5530FirmwarePlan.create(image, new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(image),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0, 8192)), null, 0, 8192));
    }
    @Test public void productionDefaultPinsEveryDescriptorByteNotOnlyTheStructuralFingerprint() throws Exception {
        byte[] standard = Tbs5530Fx2FingerprintTest.bootstrap();
        assertEquals(Tbs5530OperationalProfile.Kind.FX2_DEFAULT,
                Tbs5530ProductionFirmware.profile(standard).kind);
        // Device release/string indexes, configuration string and all four interface strings
        // are deliberately outside the structural fingerprint, but still part of the production seal.
        for (int offset : new int[]{12, 13, 14, 15, 16, 24, 35, 44, 95, 146}) {
            byte[] changed = standard.clone();
            changed[offset] ^= 1;
            assertEquals("structural candidate at offset " + offset,
                    Tbs5530OperationalProfile.Kind.FX2_DEFAULT,
                    Tbs5530OperationalProfile.observe(changed).kind);
            Tbs5530ProductionFailure failure = assertThrows(Tbs5530ProductionFailure.class,
                    () -> Tbs5530ProductionFirmware.profile(changed));
            assertEquals(Tbs5530ProductionFailure.Kind.DESCRIPTOR, failure.kind);
        }
    }

    @Test public void cold132ThenOperationalHandoffKeepsOneObserverAndSealsDuplicates() throws Exception {
        Tbs5530AttachmentEpoch e = new Tbs5530AttachmentEpoch("cold");
        e.attach("cold"); e.attach("cold"); e.seal("cold");
        List<Integer> addresses = new ArrayList<>();
        List<Long> pauses = new ArrayList<>();
        Tbs5530ProductionFirmware.bootstrap(syntheticPlan(), (type, request, value, index, bytes, offset, size, timeout) -> {
            assertEquals(0x40, type); assertEquals(0xa0, request); assertEquals(0, index);
            addresses.add(value);
            if (addresses.size() == 132) { e.detach("cold"); e.attach("operational"); e.attach("operational"); }
            return size;
        }, e::require, pauses::add, e::beginTransition);
        assertEquals(132, addresses.size()); assertEquals(Integer.valueOf(0x7f92), addresses.get(0));
        assertEquals(Integer.valueOf(0xe600), addresses.get(1)); assertEquals(Integer.valueOf(0x1fc0), addresses.get(129));
        assertEquals(Integer.valueOf(0x7f92), addresses.get(130)); assertEquals(Integer.valueOf(0xe600), addresses.get(131));
        assertEquals(Collections.singletonList(100L), pauses);
        e.observeCandidate("operational"); e.acceptOperational("operational"); e.require();
        e.attach("operational"); assertThrows(IOException.class, e::require);
    }
    @Test public void anyColdShortResultStopsWithoutRetryOrReleaseRecovery() throws Exception {
        Tbs5530FirmwarePlan plan = syntheticPlan();
        for (int ordinal = 1; ordinal <= 132; ordinal++) {
            final int stop = ordinal;
            AtomicInteger attempted = new AtomicInteger();
            assertThrows(IOException.class, () -> Tbs5530ProductionFirmware.bootstrap(plan,
                    (type, request, value, index, bytes, offset, size, timeout) -> attempted.incrementAndGet() == stop ? -1 : size,
                    () -> { }, millis -> fail("No post-failure sleep"), () -> { }));
            assertEquals(stop, attempted.get());
        }
    }
    @Test public void detachBetweenCandidateAndSealNeverDisappearsEvenWhenIdentityIsReused() throws Exception {
        Tbs5530AttachmentEpoch e = new Tbs5530AttachmentEpoch("cold");
        e.seal("cold"); e.beginTransition(); e.detach("cold"); e.observeCandidate("operational");
        e.detach("operational"); e.attach("operational");
        assertThrows(IOException.class, () -> e.acceptOperational("operational"));
        Tbs5530AttachmentEpoch initial = new Tbs5530AttachmentEpoch("same");
        initial.detach("same"); initial.attach("same"); assertThrows(IOException.class, initial::require);
    }
    @Test public void interruptionAndGuardStopBeforeTheNextRamWrite() throws Exception {
        Tbs5530FirmwarePlan plan = syntheticPlan(); AtomicInteger attempts = new AtomicInteger();
        assertThrows(IOException.class, () -> Tbs5530ProductionFirmware.bootstrap(plan,
                (t,r,v,i,b,o,l,m) -> { attempts.incrementAndGet(); return l; },
                () -> { if (attempts.get() == 3) throw new IOException("removed"); }, millis -> {}, () -> {}));
        assertEquals(3, attempts.get());
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> Tbs5530ProductionFirmware.bootstrap(plan,
                    (t,r,v,i,b,o,l,m) -> { fail(); return l; }, () -> {}, millis -> {}, () -> {}));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test public void productionRejectsSyntheticImageBeforeAnyIo() {
        assertThrows(IOException.class, () -> Tbs5530ProductionFirmware.plan(new byte[8192]));
        assertThrows(IOException.class, () -> Tbs5530ProductionFirmware.plan(new byte[8191]));
    }
    private static final class Source implements ByteSource {
        boolean open, closed;
        @Override public void open() { assertFalse(closed); open = true; }
        @Override public void readNext(ByteSink sink) throws IOException { assertTrue(open); assertFalse(closed); sink.consume(new byte[]{0x47}, 1); }
        @Override public void close() { assertTrue(open); closed = true; }
    }
    @Test public void streamWaitsForTuneDrainsRetuneAndCreatesFreshSource() throws Exception {
        List<Source> sources = new ArrayList<>();
        Tbs5530StreamGate gate = new Tbs5530StreamGate(() -> { Source s = new Source(); sources.add(s); return s; }, false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch waiting = new CountDownLatch(1), received = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            waiting.countDown();
            try { gate.open(); gate.readNext((bytes,count) -> received.countDown()); }
            catch (Throwable e) { error.set(e); }
        });
        reader.start(); assertTrue(waiting.await(2, TimeUnit.SECONDS));
        assertFalse(received.await(25, TimeUnit.MILLISECONDS)); assertTrue(sources.isEmpty());
        gate.resume(); assertTrue(received.await(2, TimeUnit.SECONDS)); reader.join(2000);
        assertFalse(reader.isAlive()); assertNull(error.get()); assertEquals(1, sources.size());
        gate.pause(); assertTrue(sources.get(0).closed); gate.resume(); gate.readNext((b,n) -> {});
        assertEquals(2, sources.size()); gate.close(); gate.close(); assertTrue(sources.get(1).closed);
        assertThrows(IOException.class, gate::resume);
    }
    @Test public void closeUnblocksBeforeTuneAndFailedStreamDoesNotRestart() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        Tbs5530StreamGate gate = new Tbs5530StreamGate(() -> { creations.incrementAndGet(); throw new IOException("fixture"); }, false);
        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(() -> { started.countDown(); try { gate.readNext((b,n) -> {}); fail(); }
            catch (IOException expected) {} catch (InterruptedException e) { throw new AssertionError(e); } });
        reader.start(); assertTrue(started.await(2, TimeUnit.SECONDS)); gate.close(); reader.join(2000);
        assertFalse(reader.isAlive()); assertEquals(0, creations.get());
        Tbs5530StreamGate broken = new Tbs5530StreamGate(() -> { creations.incrementAndGet(); throw new IOException(); }, true);
        assertThrows(IOException.class, () -> broken.readNext((b,n) -> {}));
        assertThrows(IOException.class, () -> broken.readNext((b,n) -> {})); assertEquals(1, creations.get()); broken.close();
    }
    @Test public void pauseAndFailureCleanupRetainUndrainedSourceForFinalClose() throws Exception {
        for (boolean runtime : new boolean[]{false, true}) for (boolean pause : new boolean[]{false, true}) {
            boolean[] allowDrain = {false};
            AtomicInteger closes = new AtomicInteger();
            ByteSource source = new ByteSource() {
                @Override public void open() { }
                @Override public void readNext(ByteSink sink) { }
                @Override public void close() throws IOException {
                    closes.incrementAndGet();
                    if (!allowDrain[0]) {
                        if (runtime) throw new IllegalStateException("fixture pending URBs");
                        throw new IOException("fixture pending URBs");
                    }
                }
            };
            Tbs5530StreamGate gate = new Tbs5530StreamGate(() -> source, true);
            gate.readNext((bytes, count) -> { });
            assertFalse(gate.drained());
            if (pause) assertThrows(Exception.class, gate::pause);
            else {
                IOException failure = new IOException("fixture receive failure");
                gate.fail(failure);
                assertEquals(1, failure.getSuppressed().length);
            }
            assertFalse("Failed cleanup must retain the source", gate.drained());
            assertEquals(1, closes.get());
            allowDrain[0] = true;
            gate.close();
            assertTrue(gate.drained());
            gate.close();
            assertEquals(2, closes.get());
        }
    }

    @Test public void metricsUseMilliDbScaledBerAndUnknownSentinels() {
        List<Tbs5530SignalMonitor.RegisterRead> reads = new ArrayList<>();
        add(reads,0x20,0x10,6); add(reads,0x20,0x28,0x10,0); add(reads,0x20,0x72,0);
        add(reads,0x22,0x12,1); add(reads,0x22,0x5b,0); add(reads,0x22,0x5e,0); add(reads,0x24,0x40,1,0,0,72);
        Tbs5530SignalMetrics m = Tbs5530SignalMetrics.decode(reads);
        assertEquals(Tbs5530SignalMetrics.Lock.LOCKED,m.lock); assertFalse(m.tsLock);
        assertEquals(28827,Tbs5530ProductionMetrics.snr(m)); assertEquals(655,Tbs5530ProductionMetrics.ber(m));
        Tbs5530SignalMetrics unknown = Tbs5530SignalMetrics.decode(Collections.emptyList());
        assertEquals(-1,Tbs5530ProductionMetrics.snr(unknown)); assertEquals(65535,Tbs5530ProductionMetrics.ber(unknown));
        assertEquals(0,Tbs5530ProductionMetrics.rfPercentage(-120)); assertEquals(50,Tbs5530ProductionMetrics.rfPercentage(-60));
        assertEquals(100,Tbs5530ProductionMetrics.rfPercentage(0)); assertEquals(-1,Tbs5530ProductionMetrics.rfPercentage(Double.NaN));
    }
    private static void add(List<Tbs5530SignalMonitor.RegisterRead> reads, int bank, int reg, int... values) {
        byte[] b = new byte[values.length]; for(int i=0;i<b.length;i++) b[i]=(byte)values[i];
        reads.add(new Tbs5530SignalMonitor.RegisterRead(bank,reg,b,0,1));
    }
}
