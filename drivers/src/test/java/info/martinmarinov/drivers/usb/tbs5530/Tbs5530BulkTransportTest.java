// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530BulkTransportTest {
    private static final class Owner implements Tbs5530Session.Resources {
        int closed, released;
        public void cancel() { }
        public void release() { released++; }
        public void close() { closed++; }
    }
    private static final class Fixture {
        final Tbs5530Session session = new Tbs5530Session();
        final Owner owner = new Owner();
        final long token;
        int count = 3, calls;
        Runnable during;
        final Tbs5530BulkTransport transport;
        Fixture() throws Exception {
            token = session.requestPermission(); session.permissionResult(token, true); session.connected(token, owner);
            session.interfaceClaimed(token);
            transport = new Tbs5530BulkTransport(session, token, owner, () -> { }, (bytes, length, timeout) -> {
                calls++; Arrays.fill(bytes, (byte) 0x47); if (during != null) during.run(); return count;
            });
        }
        void lock() throws Exception {
            session.ready(token); session.frontendInitialized(token); session.frontendLock(token, true); session.streamingStarted(token);
        }
    }
    @Test public void noReadWithoutActualFrontendEvidence() throws Exception {
        Fixture f = new Fixture(); byte[] bytes = new byte[8];
        assertThrows(IOException.class, () -> f.transport.read(bytes, 8, 100)); assertEquals(0, f.calls);
        f.session.ready(f.token); f.session.streamingStarted(f.token);
        assertThrows(IOException.class, () -> f.transport.read(bytes, 8, 100)); assertEquals(0, f.calls);
        f.session.frontendInitialized(f.token); f.session.frontendLock(f.token, true);
        assertEquals(3, f.transport.read(bytes, 8, 100)); assertEquals(0x47, bytes[2]); assertEquals(0, bytes[3]);
        f.session.close();
    }
    @Test public void shortZeroAndNegativeNoDataAreNotEp0ShortTransferFailures() throws Exception {
        Fixture f = new Fixture(); f.lock();
        for (int count : new int[]{3, 0, -1}) {
            f.count = count; byte[] bytes = new byte[8];
            assertEquals(count, f.transport.read(bytes, 8, 200));
            assertEquals(Tbs5530Session.State.READY, f.session.getState());
            if (count <= 0) assertArrayEquals(new byte[8], bytes);
        }
        f.session.close();
    }
    @Test public void detachDrainsNativeReadAndNeverPublishesStaleBytes() throws Exception {
        Fixture f = new Fixture(); f.lock(); byte[] bytes = new byte[8];
        f.during = () -> {
            f.session.requestCancellation(f.token, true);
            assertEquals(0, f.owner.closed); assertTrue(f.session.isCleanupPending());
        };
        assertThrows(IOException.class, () -> f.transport.read(bytes, 8, 200));
        assertArrayEquals(new byte[8], bytes); assertEquals(1, f.owner.closed); assertEquals(1, f.owner.released);
        assertFalse(f.session.isCleanupPending());
    }
    @Test public void invalidCountsFaultAndFiniteBoundsAreRequiredBeforeIo() throws Exception {
        Fixture f = new Fixture(); f.lock();
        assertThrows(IllegalArgumentException.class, () -> f.transport.read(new byte[8], 8, 0));
        assertThrows(IllegalArgumentException.class, () -> f.transport.read(new byte[16385], 16385, 1));
        assertEquals(0, f.calls); f.count = 9;
        assertThrows(IOException.class, () -> f.transport.read(new byte[8], 8, 200));
        assertEquals(Tbs5530Session.State.FAULTED, f.session.getState()); f.session.close();
    }
    @Test public void permissionGenerationCannotReuseOldCapability() throws Exception {
        Fixture f = new Fixture(); f.lock(); f.session.close();
        long next = f.session.requestPermission(); f.session.permissionResult(next, true); f.session.connected(next, new Owner());
        assertThrows(IOException.class, () -> f.transport.read(new byte[8], 8, 200)); assertEquals(0, f.calls); f.session.close();
    }
}
