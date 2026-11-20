// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers;

import org.junit.Test;
import org.mockito.MockedConstruction;
import info.martinmarinov.drivers.tools.io.NativePipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import info.martinmarinov.usbxfer.ByteSource;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DvbDeviceDataPumpTest {
    private static final class Device extends DvbDevice {
        final ByteSource source;
        boolean creationFails;
        Device(DvbDemux demux, ByteSource source) { super(demux); this.source = source; }
        @Override public void open() { }
        @Override protected ByteSource createTsSource() { if (creationFails) throw new IllegalStateException("fixture"); return source; }
        @Override public DeviceFilter getDeviceFilter() { return null; }
        @Override public DvbCapabilities readCapabilities() { return null; }
        @Override public int readSnr() { return -1; }
        @Override public int readRfStrengthPercentage() { return -1; }
        @Override public int readBitErrorRate() { return 0xffff; }
        @Override public Set<DvbStatus> getStatus() { return Collections.emptySet(); }
        @Override public String getDebugString() { return "fixture"; }
        @Override protected void tuneTo(long f, long b, DeliverySystem d) { }
    }
    @Test public void resetDiscardsPartialMuxPacketButKeepsSelectedPids() throws Exception {
        List<byte[]> packets = new ArrayList<>();
        try (MockedConstruction<NativePipe> ignored = mockConstruction(NativePipe.class, (pipe, context) -> {
            doAnswer(call -> {
                byte[] data = call.getArgument(0);
                int offset = call.getArgument(1);
                packets.add(Arrays.copyOfRange(data, offset, offset + (int) call.getArgument(2)));
                return null;
            }).when(pipe).write(any(byte[].class), anyInt(), eq(188));
        }); DvbDemux demux = DvbDemux.DvbDmxSwfilter()) {
            byte[] retained = packet(101, 10);
            byte[] removed = packet(102, 20);
            byte[] added = packet(103, 30);
            demux.setPidFilter(101, 102);
            demux.consume(retained, 50);
            demux.resetTransportFraming();
            demux.setPidFilter(101, 103);
            demux.consume(removed, removed.length);
            demux.consume(retained, retained.length);
            demux.consume(added, added.length);
            demux.disablePidFilter();
            demux.consume(removed, removed.length);
            assertEquals(3, packets.size());
            assertArrayEquals(retained, packets.get(0));
            assertArrayEquals(added, packets.get(1));
            assertArrayEquals(removed, packets.get(2));
        }
    }

    private static byte[] packet(int pid, int value) {
        byte[] packet = new byte[188];
        Arrays.fill(packet, (byte) value);
        packet[0] = 0x47;
        packet[1] = (byte) (pid >> 8);
        packet[2] = (byte) pid;
        packet[3] = 0x10;
        return packet;
    }

    @Test public void closingDuringWriteDoesNotReportStreamClosed() throws Exception {
        for (int run = 0; run < 200; run++) {
            DvbDemux demux = mock(DvbDemux.class);
            when(demux.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            ByteSource source = mock(ByteSource.class);
            byte[] packet = new byte[188];
            CountDownLatch writing = new CountDownLatch(1);
            CountDownLatch stopped = new CountDownLatch(1);
            AtomicReference<IOException> reported = new AtomicReference<>();
            doAnswer(call -> {
                writing.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    throw new IOException("Stream Closed", e);
                }
                return null;
            }).when(demux).consume(packet, packet.length);
            doAnswer(call -> {
                demux.consume(packet, packet.length);
                return null;
            }).when(source).readNext(demux);
            Device device = new Device(demux, source);
            device.getTransportStream(new DvbDevice.StreamCallback() {
                @Override public void onStreamException(IOException error) { reported.set(error); }
                @Override public void onStoppedStreaming() { stopped.countDown(); }
            });
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            device.close();
            assertEquals(0, stopped.getCount());
            assertNull("Stop must not report a pipe write failure", reported.get());
            org.mockito.InOrder order = inOrder(source, demux);
            order.verify(source).close();
            order.verify(demux).close();
            verify(demux, times(1)).close();
        }
    }

    @Test public void nativeReadFailureReachesCallbackClosesSourceAndStops() throws Exception {
        DvbDemux demux = mock(DvbDemux.class);
        when(demux.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        ByteSource source = mock(ByteSource.class);
        IOException failure = new IOException("native URB fixture");
        doThrow(failure).when(source).readNext(demux);
        doThrow(new IOException("secondary drain failure")).when(source).close();
        Device device = new Device(demux,source);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<IOException> observed = new AtomicReference<>();
        AtomicInteger errors = new AtomicInteger();
        device.getTransportStream(new DvbDevice.StreamCallback() {
            @Override public void onStreamException(IOException error) { observed.set(error); errors.incrementAndGet(); }
            @Override public void onStoppedStreaming() { stopped.countDown(); }
        });
        assertTrue(stopped.await(2,TimeUnit.SECONDS)); device.close();
        assertSame(failure,observed.get()); assertEquals(1,errors.get()); verify(source).close();
    }
    @Test public void sourceCreationFailureStillNotifiesStoppedAndCallbackCanCloseWithoutSelfJoin() throws Exception {
        DvbDemux demux = mock(DvbDemux.class);
        when(demux.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        Device device = new Device(demux,null); device.creationFails=true;
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<IOException> observed = new AtomicReference<>();
        device.getTransportStream(new DvbDevice.StreamCallback() {
            @Override public void onStreamException(IOException error) { observed.set(error); }
            @Override public void onStoppedStreaming() {
                try { device.close(); } catch (IOException error) { observed.set(error); }
                stopped.countDown();
            }
        });
        assertTrue(stopped.await(2,TimeUnit.SECONDS));
        assertNotNull(observed.get()); assertTrue(observed.get().getCause() instanceof IllegalStateException);
    }
}
