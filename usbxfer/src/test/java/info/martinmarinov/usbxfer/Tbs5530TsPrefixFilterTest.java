// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.usbxfer;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Tbs5530TsPrefixFilterTest {
    @Test public void locksAt2048EvenWhenPrefixAndSyncsAreSplitAcrossReads() {
        int lock = 2048;
        byte[] raw = sample(lock);
        Tbs5530TsPrefixFilter filter = new Tbs5530TsPrefixFilter(1500);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int offset = 0;
        for (int end : new int[]{1000, 2047, 2048, 2049, 2424, 2800}) {
            assertEquals(0, feed(filter, result, raw, offset, end));
            offset = end;
        }
        assertFalse(filter.isLocked());
        assertEquals(753, feed(filter, result, raw, offset, 2801));
        feed(filter, result, raw, 2801, raw.length);
        assertArrayEquals(Arrays.copyOfRange(raw, lock, raw.length), result.toByteArray());
        assertEquals(lock, filter.discardedBytes());
    }

    @Test public void skipsPartialPacketAndFalseCandidateButNeverResyncsAfterLock() {
        int lock = 2048 + 37;
        byte[] raw = sample(lock);
        for (int i = 0; i < 4; i++) raw[2048 + i * 188] = 0x47;
        raw[lock + 5 * 188] = 0;
        Tbs5530TsPrefixFilter filter = new Tbs5530TsPrefixFilter(97);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (int start = 0; start < raw.length; start += 97) {
            feed(filter, result, raw, start, Math.min(raw.length, start + 97));
        }
        assertArrayEquals(Arrays.copyOfRange(raw, lock, raw.length), result.toByteArray());
        assertEquals(lock, filter.discardedBytes());
        assertTrue(filter.isLocked());
    }

    @Test public void creditsBufferedBytesAsDiscardedWhenClosedWithoutLock() {
        Tbs5530TsPrefixFilter filter = new Tbs5530TsPrefixFilter(1024);
        byte[] junk = new byte[1024];
        for (int i = 0; i < 4; i++) assertEquals(0, filter.filter(junk, junk.length));
        assertEquals(4096 - 752, filter.discardedBytes());
        filter.discardPending();
        assertEquals(4096, filter.discardedBytes());
        assertFalse(filter.isLocked());
    }

    private static byte[] sample(int lock) {
        byte[] raw = new byte[lock + 6 * 188 + 23];
        Arrays.fill(raw, (byte) 0x19);
        for (int i = 0; i < lock; i += 188) raw[i] = 0x47;
        for (int i = lock; i < raw.length - 23; i += 188) raw[i] = 0x47;
        return raw;
    }

    private static int feed(Tbs5530TsPrefixFilter filter, ByteArrayOutputStream result,
                            byte[] raw, int offset, int end) {
        byte[] chunk = Arrays.copyOfRange(raw, offset, end);
        int size = filter.filter(chunk, chunk.length);
        if (size > 0) result.write(filter.output(), 0, size);
        return size;
    }
}
