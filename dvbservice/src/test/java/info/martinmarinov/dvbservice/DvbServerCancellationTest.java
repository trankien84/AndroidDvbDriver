// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbservice;

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
    @Test(timeout = 5000) public void cancelUnblocksControlAccept() throws Exception {
        DvbServer server = new DvbServer(mock(DvbDevice.class));
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

    @Test(timeout = 5000) public void interruptClosesSocketBlockedInClientWrite() throws Exception {
        DvbDevice device = mock(DvbDevice.class);
        ServerSocket listener = mock(ServerSocket.class);
        Socket client = mock(Socket.class);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        when(listener.accept()).thenReturn(client);
        when(device.getTransportStream(any())).thenReturn(new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] bytes) { bytes[0] = 0x47; return 1; }
        });
        when(client.getOutputStream()).thenReturn(new OutputStream() {
            @Override public void write(int value) { }
            @Override public void write(byte[] bytes, int start, int length) throws IOException {
                writing.countDown();
                boolean interrupted = false;
                while (closed.getCount() != 0) {
                    try { closed.await(); } catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
                throw new IOException("fixture socket closed");
            }
        });
        doAnswer(invocation -> { closed.countDown(); return null; }).when(client).close();
        TransferThread worker = new TransferThread(device, listener, () -> { });
        try {
            worker.start();
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(2000);
            assertFalse("Transfer worker remained blocked in socket write", worker.isAlive());
            verify(client, atLeastOnce()).close();
        } finally {
            closed.countDown();
            worker.interrupt();
            worker.join(1000);
        }
    }
}
