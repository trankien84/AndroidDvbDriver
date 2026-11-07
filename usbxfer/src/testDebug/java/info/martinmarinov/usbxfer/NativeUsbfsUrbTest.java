// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import org.junit.Test;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import static org.junit.Assert.*;

public class NativeUsbfsUrbTest {
    private static final class Clock implements NativeUsbfsUrb.Clock {
        long now;
        int pauses;
        public long nanos() { return now; }
        public void pause() { pauses++; now += 10_000_000L; }
    }
    private static final class Port implements NativeUsbfsUrb.Port {
        int pending, fills, polls, takes, discards, frees;
        boolean draining, uncertain, ready, failReap, throwPoll, stuck, throwStop;
        int count = 5, status, syscallErrno;
        public int fill() { if (++fills <= 8) { pending++; return 1; } return 0; }
        public int poll() {
            polls++;
            if (throwPoll) throw new IllegalStateException("JNI boundary fault");
            if (failReap) { syscallErrno = 19; return -11; }
            if (stuck) { syscallErrno = 11; return -10; }
            if (draining) { pending--; return 0; }
            pending--; ready = true; syscallErrno = 0; return 0;
        }
        public int take(byte[] bytes) {
            takes++;
            if (!ready) return -10;
            ready = false;
            if (status != 0) return -11;
            if (count > 0) bytes[0] = 0x47;
            return count;
        }
        public void stop() {
            draining = true; discards += pending;
            if (throwStop) throw new IllegalStateException("stop boundary fault");
        }
        public void quarantine() { uncertain = true; }
        public long[] events() { return new long[0]; }
        public long[] snapshot() { return new long[]{fills,fills,8 - pending,pending,0,0,uncertain ? 1 : 0,syscallErrno,0}; }
        public boolean destroy() { assertEquals(0,pending); assertFalse(uncertain); frees++; return true; }
    }
    private static final class Fixture {
        final Port port = new Port(); final Clock clock = new Clock(); final StringWriter audit = new StringWriter();
        boolean consent = true;
        int checks;
        final NativeUsbfsUrb receiver = new NativeUsbfsUrb(port, 5000, "test-run", () -> {
            checks++; if (!consent) throw new IOException("STOP/permission/heartbeat lost");
        }, audit, clock);
        int read() throws IOException { return receiver.read(new byte[4096],4096,500); }
    }
    @Test public void positiveShortAndZeroAreDistinctAndNotReplenishedOnReturn() throws Exception {
        Fixture f = new Fixture(); assertEquals(5,f.read()); assertEquals(8,f.port.fills);
        assertEquals(0,f.receiver.errno()); f.receiver.close(); assertEquals(1,f.port.frees);
        f.receiver.close(); assertEquals(1,f.port.frees); assertTrue(f.audit.toString().contains("\"quarantined\":false"));
        f = new Fixture(); f.port.count = 0; assertEquals(0,f.read()); assertEquals(8,f.port.fills);
        Fixture zero = f; assertThrows(IOException.class,zero::read); f.receiver.close();
    }
    @Test public void completionStatusNeverBecomesSyscallErrno() throws Exception {
        Fixture f = new Fixture(); f.port.status = -75; f.port.count = 100;
        assertEquals(-1,f.read()); assertEquals(0,f.receiver.errno()); assertEquals(8,f.port.fills);
        assertThrows(IOException.class,f::read); f.receiver.close();
    }
    @Test public void syscallFailureRetainsRealErrnoAndCanDrainLater() throws Exception {
        Fixture f = new Fixture(); f.port.failReap = true;
        assertEquals(-1,f.read()); assertEquals(19,f.receiver.errno());
        f.port.failReap = false; f.receiver.close(); assertEquals(1,f.port.frees);
    }
    @Test public void stopAndHeartbeatAreCheckedBetweenNonblockingPolls() throws Exception {
        Fixture f = new Fixture(); f.port.stuck = true;
        NativeUsbfsUrb receiver = new NativeUsbfsUrb(f.port,5000,"heartbeat-test",() -> {
            if (f.clock.now >= 30_000_000L) throw new IOException("heartbeat lost");
        },f.audit,f.clock);
        assertThrows(IOException.class,() -> receiver.read(new byte[4096],4096,500));
        assertTrue(f.port.polls <= 4); f.port.stuck = false; receiver.close(); assertEquals(1,f.port.frees);
    }
    @Test public void timeoutNeverResubmitsOrInventsCompletionErrno() throws Exception {
        Fixture f = new Fixture(); f.port.stuck = true;
        assertThrows(IOException.class,f::read); assertEquals(500_000_000L,f.clock.now);
        assertEquals(8,f.port.fills); assertEquals(11,f.receiver.errno());
        f.port.stuck = false; f.receiver.close();
    }
    @Test public void deadlineAndPollCapsBoundUncertainDrainAndKeepAllocations() throws Exception {
        Fixture f = new Fixture(); f.port.stuck = true;
        assertThrows(IOException.class,f::read);
        assertThrows(IOException.class,f.receiver::close);
        assertTrue(f.receiver.isQuarantined()); assertEquals(0,f.port.frees); assertEquals(8,f.port.pending);
        assertTrue(f.clock.now <= 1_500_000_000L); assertThrows(IOException.class,f.receiver::close);
        assertEquals(0,f.port.frees);
    }
    @Test public void foreignOrDuplicateCompletionQuarantines() throws Exception {
        Fixture f = new Fixture(); f.port.uncertain = true; f.port.failReap = true;
        assertEquals(-1,f.read()); assertThrows(IOException.class,f.receiver::close);
        assertEquals(0,f.port.frees); assertTrue(f.receiver.isQuarantined());
    }
    @Test public void javaNativeBoundaryExceptionStillDrainsOrQuarantines() throws Exception {
        Fixture f = new Fixture(); f.port.throwPoll = true;
        assertThrows(IllegalStateException.class,f::read); f.port.throwPoll = false;
        f.receiver.close(); assertEquals(1,f.port.frees);
        Fixture g = new Fixture(); assertEquals(5,g.read()); g.port.throwStop = true;
        assertThrows(IllegalStateException.class,g.receiver::close); assertTrue(g.receiver.isQuarantined());
        assertEquals(0,g.port.frees); assertThrows(IOException.class,g.receiver::close);
    }
    @Test public void auditFailureCannotPublishSuccessButKnownDrainStillFreesSafely() throws Exception {
        Fixture f = new Fixture(); Writer broken = new Writer() {
            public void write(char[] c,int at,int count) throws IOException { throw new IOException("disk full"); }
            public void flush() { }
            public void close() { }
        };
        NativeUsbfsUrb receiver = new NativeUsbfsUrb(f.port,5000,"audit-test",() -> { },broken,f.clock);
        assertEquals(5,receiver.read(new byte[4096],4096,500));
        assertThrows(IOException.class,receiver::close); assertEquals(1,f.port.frees);
        assertFalse(receiver.isQuarantined()); receiver.close(); assertEquals(1,f.port.frees);
    }
    @Test public void interruptDoesNotSkipFiniteDrain() throws Exception {
        Fixture f = new Fixture(); assertEquals(5,f.read()); Thread.currentThread().interrupt();
        try { f.receiver.close(); assertTrue(Thread.currentThread().isInterrupted()); assertEquals(1,f.port.frees); }
        finally { Thread.interrupted(); }
    }
    @Test public void otherWorkerCannotReadOrCloseOwnedNativeState() throws Exception {
        Fixture f = new Fixture(); final Throwable[] failure = new Throwable[1];
        Thread other = new Thread(() -> { try { f.read(); } catch (Throwable error) { failure[0] = error; } });
        other.start(); other.join(); assertTrue(failure[0] instanceof IOException); assertEquals(0,f.port.fills);
        f.receiver.close();
    }
}
