// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class Tbs5530QueuedReceiverTest {
    private static class Fake implements Tbs5530QueuedReceiver.Port, Tbs5530QueuedReceiver.Clock {
        final Deque<Tbs5530QueuedReceiver.Completion> completions = new ArrayDeque<>();
        int submitted, cancelled, copies, released, failSubmit = -1, failCancel = -1;
        boolean autoComplete = true, failAwait;
        long now;
        public long millis() { return now; }
        public void submit(int slot) throws IOException {
            if (slot == failSubmit) throw new IOException("injected submit");
            submitted++;
            if (autoComplete) completions.add(new Tbs5530QueuedReceiver.Completion(slot, 4096));
        }
        public Tbs5530QueuedReceiver.Completion await(int timeout) throws IOException {
            assertTrue(timeout > 0 && timeout <= 2000); now++;
            if (failAwait) { now += timeout; throw new IOException("injected timeout"); }
            return completions.poll();
        }
        public void cancel(int slot) throws IOException {
            cancelled++; if (slot == failCancel) throw new IOException("injected cancel");
        }
        public void copy(int slot, byte[] bytes, int length) { copies++; Arrays.fill(bytes, 0, length, (byte) slot); }
        public void release() { released++; }
        Tbs5530QueuedReceiver receiver() { return new Tbs5530QueuedReceiver(this, this); }
    }
    private static void fails(Io action) throws Exception {
        try { action.run(); fail("Expected fail-stop"); } catch (IOException expected) { }
    }
    interface Io { void run() throws Exception; }
    @Test public void eightRequestsOrderedAcrossMultipleRoundsAndDrainAccountsTail() throws Exception {
        Fake fake = new Fake(); Tbs5530QueuedReceiver receiver = fake.receiver();
        byte[] bytes = new byte[4096];
        for (int read = 0; read < 24; read++) {
            assertEquals(4096, receiver.read(bytes, bytes.length, 500));
            assertEquals((byte)(read % 8), bytes[0]);
        }
        assertEquals(32, fake.submitted); assertEquals(8, receiver.snapshot().outstanding);
        receiver.close(); receiver.close();
        assertEquals(1, fake.released); assertEquals(8, fake.cancelled);
        assertEquals(24 * 4096L, receiver.snapshot().deliveredBytes);
        assertEquals(8 * 4096L, receiver.snapshot().cleanupBytes);
        assertEquals(0, receiver.snapshot().outstanding);
        fails(() -> receiver.read(bytes, bytes.length, 500));
    }
    @Test public void outOfOrderCompletionsNeverReorderStream() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        for (int slot : new int[]{7, 5, 1, 3, 6, 2, 4, 0}) fake.completions.add(new Tbs5530QueuedReceiver.Completion(slot, 4096));
        Tbs5530QueuedReceiver receiver = fake.receiver(); byte[] bytes = new byte[4096];
        for (int slot = 0; slot < 8; slot++) {
            assertEquals(4096, receiver.read(bytes, 4096, 500)); assertEquals((byte) slot, bytes[0]);
        }
        for (int slot = 0; slot < 8; slot++) fake.completions.add(new Tbs5530QueuedReceiver.Completion(slot, 0));
        receiver.close(); assertEquals(0, receiver.snapshot().cleanupBytes);
    }
    @Test public void shortCompletionCopiesOnlyReturnedBytes() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(0, 187));
        Tbs5530QueuedReceiver receiver = fake.receiver(); byte[] bytes = new byte[4096]; Arrays.fill(bytes, (byte) 99);
        assertEquals(187, receiver.read(bytes, 4096, 500)); assertEquals(0, bytes[186]); assertEquals(99, bytes[187]);
        for (int slot = 0; slot < 8; slot++) fake.completions.add(new Tbs5530QueuedReceiver.Completion(slot, 0));
        receiver.close();
    }
    @Test public void zeroCompletionStopsWithoutImplicitResubmission() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(0, 0));
        Tbs5530QueuedReceiver receiver = fake.receiver();
        assertEquals(0, receiver.read(new byte[4096], 4096, 500));
        assertEquals(8, fake.submitted);
        fails(() -> receiver.read(new byte[4096], 4096, 500));
        for (int slot = 1; slot < 8; slot++) fake.completions.add(new Tbs5530QueuedReceiver.Completion(slot, 0));
        receiver.close(); assertEquals(0, receiver.snapshot().outstanding); assertEquals(1, fake.released);
    }
    @Test public void invalidLengthRejectedBeforeAnySubmission() throws Exception {
        Fake fake = new Fake(); Tbs5530QueuedReceiver receiver = fake.receiver();
        for (int length : new int[]{0, 512, 4095, 8192}) {
            try { receiver.read(new byte[8192], length, 500); fail(); } catch (IllegalArgumentException expected) { }
        }
        assertEquals(0, fake.submitted); receiver.close();
    }
    @Test public void foreignCompletionRetainsResourcesAndBlocksRetry() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(9, 4096));
        Tbs5530QueuedReceiver receiver = fake.receiver(); byte[] bytes = new byte[4096];
        fails(() -> receiver.read(bytes, 4096, 500)); fails(receiver::close);
        assertEquals(0, fake.copies); assertEquals(0, fake.released); assertTrue(receiver.snapshot().uncertain);
        fails(() -> receiver.read(bytes, 4096, 500));
    }
    @Test public void oversizedCompletionNeverCopiesOrReleasesPendingBuffers() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(0, 4097));
        Tbs5530QueuedReceiver receiver = fake.receiver();
        fails(() -> receiver.read(new byte[4096], 4096, 500)); fails(receiver::close);
        assertEquals(0, fake.copies); assertEquals(0, fake.released);
    }
    @Test public void duplicateCompletionIsUncertainNotData() throws Exception {
        Fake fake = new Fake(); fake.autoComplete = false;
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(1, 4096));
        fake.completions.add(new Tbs5530QueuedReceiver.Completion(1, 4096));
        Tbs5530QueuedReceiver receiver = fake.receiver();
        fails(() -> receiver.read(new byte[4096], 4096, 500)); assertEquals(0, fake.copies);
        fails(receiver::close); assertEquals(0, fake.released);
    }
    @Test public void timeoutHasNoImplicitResubmitAndDrainCanStillSucceed() throws Exception {
        Fake fake = new Fake(); fake.failAwait = true; Tbs5530QueuedReceiver receiver = fake.receiver();
        fails(() -> receiver.read(new byte[4096], 4096, 500)); assertEquals(8, fake.submitted);
        fake.failAwait = false; receiver.close(); assertEquals(1, fake.released);
        assertEquals(32768, receiver.snapshot().cleanupBytes);
    }
    @Test public void partialSubmitFailureIsNotTreatedAsKnownUnsubmittedOwnership() throws Exception {
        Fake fake = new Fake(); fake.failSubmit = 3; Tbs5530QueuedReceiver receiver = fake.receiver();
        fails(() -> receiver.read(new byte[4096], 4096, 500)); fails(receiver::close);
        assertEquals(3, fake.submitted); assertEquals(0, fake.copies); assertEquals(0, fake.released);
        assertEquals(1, receiver.snapshot().outstanding); assertTrue(receiver.snapshot().uncertain);
    }
    @Test public void cancelExceptionDoesNotPreventDrainingOtherSlots() throws Exception {
        Fake fake = new Fake(); fake.failCancel = 3; Tbs5530QueuedReceiver receiver = fake.receiver();
        receiver.read(new byte[4096], 4096, 500); fails(receiver::close);
        assertEquals(8, fake.cancelled); assertEquals(0, receiver.snapshot().outstanding); assertEquals(1, fake.released);
    }
    @Test public void closeBeforeStartIsNoOpForUsb() throws Exception {
        Fake fake = new Fake(); Tbs5530QueuedReceiver receiver = fake.receiver(); receiver.close();
        assertEquals(0, fake.submitted); assertEquals(0, fake.cancelled); assertEquals(1, fake.released);
    }
    @Test public void foreignWorkerCannotSubmitOrClose() throws Exception {
        Fake fake = new Fake(); Tbs5530QueuedReceiver receiver = fake.receiver();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { receiver.read(new byte[4096], 4096, 500); } catch (Throwable error) { failure.set(error); }
        }); thread.start(); thread.join(2000);
        assertFalse(thread.isAlive()); assertTrue(failure.get() instanceof IOException); assertEquals(0, fake.submitted);
        receiver.close();
    }
    @Test public void clockRegressionStopsWithoutData() throws Exception {
        Fake fake = new Fake() { @Override public long millis() { return --now; } };
        Tbs5530QueuedReceiver receiver = fake.receiver();
        fails(() -> receiver.read(new byte[4096], 4096, 500)); assertEquals(0, fake.copies);
    }
}
