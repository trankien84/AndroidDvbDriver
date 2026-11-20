// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbservice;

import android.util.Log;
import org.junit.Before;
import org.mockito.MockedStatic;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DvbDevice;
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

    @Test public void secondClientIsBusyUntilFirstSessionFinishesCleanup() throws Exception {
        DvbService.OpenRequest first = DvbService.reserve("busy");
        try {
            first.cancel();
            DvbException busy = assertThrows(DvbException.class, () -> DvbService.reserve("busy"));
            assertEquals(DvbException.ErrorCode.DEVICE_BUSY, busy.getErrorCode());
        } finally {
            DvbService.release(first);
        }
        DvbService.OpenRequest next = DvbService.reserve("busy");
        try {
            DvbService.release(first);
            assertThrows(DvbException.class, () -> DvbService.reserve("busy"));
        } finally {
            DvbService.release(next);
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS) public void eofOnDataClosesIdleControlAndDevice() throws Exception {
        when(device.getTransportStream(any())).thenReturn(new ByteArrayInputStream(new byte[0]));
        try (DvbServer server = new DvbServer(device)) {
            DvbServerPorts ports = server.bind(InetAddress.getLoopbackAddress());
            Thread thread = startServer(server, new AtomicReference<>());
            try (Socket control = connect(ports.getControlPort()); Socket data = connect(ports.getTransferPort())) {
                assertEquals(-1, control.getInputStream().read());
                assertEquals(-1, data.getInputStream().read());
                thread.join(2000);
                assertFalse(thread.isAlive());
                verify(device).close();
            }
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS) public void clientScansRetunesAndReceivesDisconnectOnDetach() throws Exception {
        DvbDevice tuner = mock(Tbs5530DvbDevice.class);
        when(tuner.getDeviceFilter()).thenReturn(new DeviceFilter(0x734c, 0x5530, "fixture"));
        when(tuner.readCapabilities()).thenReturn(new DvbCapabilities(174_000_000, 868_000_000, 1000,
                Collections.singleton(DeliverySystem.DVBT2)));
        AtomicLong frequency = new AtomicLong();
        doAnswer(call -> {
            if (call.getArgument(2) != DeliverySystem.DVBT2) {
                throw new DvbException(DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED, "DVB-T2 only");
            }
            frequency.set(call.getArgument(0));
            return null;
        }).when(tuner).tuneForService(anyLong(), eq(8_000_000L), any());
        when(tuner.getStatus()).thenAnswer(call -> {
            long hz = frequency.get();
            return hz == 474_000_000L || hz == 506_000_000L || hz == 546_000_000L
                    ? EnumSet.of(DvbStatus.FE_HAS_SIGNAL, DvbStatus.FE_HAS_SYNC, DvbStatus.FE_HAS_LOCK)
                    : Collections.emptySet();
        });
        AtomicReference<DvbDevice.StreamCallback> callback = new AtomicReference<>();
        try (PipedInputStream source = new PipedInputStream(); PipedOutputStream feed = new PipedOutputStream(source);
             DvbServer server = new DvbServer(tuner)) {
            when(tuner.getTransportStream(any())).thenAnswer(call -> {
                callback.set(call.getArgument(0));
                return source;
            });
            DvbServerPorts ports = server.bind(InetAddress.getLoopbackAddress());
            AtomicReference<IOException> failure = new AtomicReference<>();
            Thread thread = startServer(server, failure);
            try (Socket control = connect(ports.getControlPort()); Socket data = connect(ports.getTransferPort())) {
                assertEquals(2, exchange(control, 5)[1]);
                int locks = 0;
                for (int i = 0; i < 10; i++) {
                    assertEquals(1, exchange(control, 2, 474_000_000L + i * 8_000_000L, 8_000_000L, 1)[0]);
                    assertEquals(1, exchange(control, 4, 0, 17, 18)[0]);
                    locks += (int) exchange(control, 3)[8];
                }
                assertEquals(3, locks);
                assertEquals(0, exchange(control, 2, 506_000_000L, 8_000_000L, 0)[0]);
                assertEquals(1, exchange(control, 2, 506_000_000L, 8_000_000L, 1)[0]);
                assertEquals(1, exchange(control, 4, 0, 101)[0]);
                feed.write(tsPacket(101)); feed.flush();
                receivePacket(data, 101);
                assertEquals(1, exchange(control, 4, 101, 102)[0]);
                feed.write(tsPacket(101)); feed.flush();
                receivePacket(data, 101);
                assertEquals(1, exchange(control, 2, 546_000_000L, 8_000_000L, 1)[0]);
                assertEquals(1, exchange(control, 4, 0x2000)[0]);
                feed.write(tsPacket(201)); feed.flush();
                receivePacket(data, 201);
                DvbException detached = new DvbException(DvbException.ErrorCode.DEVICE_DISCONNECTED, "fixture");
                callback.get().onStreamException(detached);
                feed.close();
                assertEquals(-1, control.getInputStream().read());
                assertEquals(-1, data.getInputStream().read());
                thread.join(2000);
                assertFalse(thread.isAlive());
                assertSame(detached, failure.get());
                verify(tuner).close();
                verify(tuner, times(1)).getTransportStream(any());
                verify(tuner).disablePidFilter();
            }
        }
    }

    private static Thread startServer(DvbServer server, AtomicReference<IOException> failure) {
        Thread thread = new Thread(() -> {
            try (DvbServer session = server; MockedStatic<Log> ignored = mockStatic(Log.class)) {
                session.open();
                session.serve();
            } catch (IOException e) {
                failure.set(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(2000);
        return socket;
    }

    private static long[] exchange(Socket socket, int opcode, long... values) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeByte(opcode);
        out.writeByte(values.length);
        for (long value : values) out.writeLong(value);
        out.flush();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        assertEquals(opcode, in.readUnsignedByte());
        long[] reply = new long[in.readUnsignedByte()];
        for (int i = 0; i < reply.length; i++) reply[i] = in.readLong();
        return reply;
    }

    private static byte[] tsPacket(int pid) {
        byte[] packet = new byte[188];
        packet[0] = 0x47;
        packet[1] = (byte) (pid >> 8);
        packet[2] = (byte) pid;
        return packet;
    }

    private static void receivePacket(Socket data, int expectedPid) throws IOException {
        byte[] packet = new byte[188];
        new DataInputStream(data.getInputStream()).readFully(packet);
        assertArrayEquals(tsPacket(expectedPid), packet);
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
