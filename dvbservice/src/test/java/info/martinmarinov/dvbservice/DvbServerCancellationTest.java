// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbservice;

import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import info.martinmarinov.drivers.DvbDevice;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DvbServerCancellationTest {
    // The method timeout is a backstop; the individual waits still have shorter deadlines.
    private static final long TEST_TIMEOUT_MS = 20_000;
    private DvbDevice device;

    @Before public void setUp() {
        device = mock(DvbDevice.class);
    }

    @Test(timeout = TEST_TIMEOUT_MS) public void cancelUnblocksControlAccept() throws Exception {
        DvbServer server = new DvbServer(device);
        server.bind(InetAddress.getLoopbackAddress());
        Thread worker = new Thread(() -> {
            try { server.serve(); } catch (IOException expected) { }
        });
        try {
            worker.start();
            server.cancel();
            worker.join(2000);
            assertFalse(worker.isAlive());
        } finally {
            server.cancel();
            server.close();
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS) public void interruptClosesSocketBlockedInClientWrite() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        when(device.getTransportStream(any())).thenReturn(new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] bytes) { bytes[0] = 0x47; return 1; }
        });
        OutputStream blockedOutput = new OutputStream() {
            @Override public void write(int value) { }
            @Override public void write(byte[] bytes, int start, int length) throws IOException {
                writing.countDown();
                boolean interrupted = false;
                // Interrupt alone must not release a blocked socket write.
                while (closed.getCount() != 0) {
                    try { closed.await(); } catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
                throw new IOException("fixture socket closed");
            }
        };
        try (Socket client = new Socket() {
            @Override public void setTcpNoDelay(boolean enabled) { }
            @Override public OutputStream getOutputStream() { return blockedOutput; }
            @Override public synchronized void close() throws IOException {
                super.close();
                closed.countDown();
            }
        }; ServerSocket listener = new ServerSocket() {
            @Override public Socket accept() { return client; }
        }) {
            TransferThread worker = new TransferThread(device, listener, () -> { });
            try {
                worker.start();
                assertTrue(writing.await(2, TimeUnit.SECONDS));
                worker.interrupt();
                worker.join(2000);
                assertFalse("Transfer worker remained blocked in socket write", worker.isAlive());
                assertEquals("Client socket was not closed", 0L, closed.getCount());
            } finally {
                closed.countDown();
                worker.interrupt();
                worker.join(1000);
            }
        }
    }
}
