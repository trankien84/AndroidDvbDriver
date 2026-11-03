// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.*;

final class ScriptedFakeControlIo implements Tbs5530ControlIo {
    static final class Call {
        final int type, request, value, index, offset, length, timeout;
        final byte[] before, after;
        Call(int type, int request, int value, int index, int offset, int length, int timeout,
             byte[] before, byte[] after) {
            this.type = type; this.request = request; this.value = value; this.index = index;
            this.offset = offset; this.length = length; this.timeout = timeout;
            this.before = before.clone(); this.after = after.clone();
        }
    }
    private static final class Expected {
        int type, request, value, index, offset, length, timeout, result;
        byte[] before, response;
        IOException error;
    }
    private final Deque<Expected> script = new ArrayDeque<>();
    private final List<Call> calls = new ArrayList<>();

    synchronized void expect(int type, int request, int value, int index, byte[] before,
                             int offset, int length, int timeout, byte[] response, int result, IOException error) {
        Expected e = new Expected();
        e.type = type; e.request = request; e.value = value; e.index = index;
        e.before = before.clone(); e.offset = offset; e.length = length; e.timeout = timeout;
        e.response = response == null ? null : response.clone(); e.result = result; e.error = error;
        script.add(e);
    }
    void write(int request, byte[] data) {
        expect(0x40, request, 0, 0, data, 0, data.length, 2000, null, data.length, null);
    }
    void read(byte[] data) {
        expect(0xc0, 0x91, 0, 0, new byte[data.length], 0, data.length, 2000, data, data.length, null);
    }

    @Override
    public synchronized int controlTransfer(int type, int request, int value, int index, byte[] buffer,
                                            int offset, int length, int timeout) throws IOException {
        Expected e = script.poll();
        assertNotNull("Unexpected control request 0x" + Integer.toHexString(request), e);
        assertEquals(e.type, type); assertEquals(e.request, request); assertEquals(e.value, value);
        assertEquals(e.index, index); assertEquals(e.offset, offset); assertEquals(e.length, length);
        assertEquals(e.timeout, timeout);
        byte[] before = Arrays.copyOfRange(buffer, offset, offset + length);
        assertArrayEquals(e.before, before);
        if (e.response != null) System.arraycopy(e.response, 0, buffer, offset, e.response.length);
        calls.add(new Call(type, request, value, index, offset, length, timeout, before,
                Arrays.copyOfRange(buffer, offset, offset + length)));
        if (e.error != null) throw e.error;
        return e.result;
    }
    synchronized List<Call> calls() { return new ArrayList<>(calls); }
    synchronized void assertComplete() { assertTrue("Expected calls were not consumed", script.isEmpty()); }
}
