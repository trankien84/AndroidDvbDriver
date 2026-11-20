/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2022 by Signalware Ltd <driver at aerialtv.eu>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package info.martinmarinov.drivers;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;

import info.martinmarinov.usbxfer.ByteSource;

import static info.martinmarinov.drivers.DvbException.ErrorCode.BAD_API_USAGE;

public abstract class DvbDevice implements Closeable {
    private DvbDemux dvbDemux;

    private volatile DataPump dataPump;

    protected DvbDevice(DvbDemux dvbDemux) {
        this.dvbDemux = dvbDemux;
    }

    /** Reopenable drivers may replace their closed pipe only after the previous pump has joined. */
    protected final void resetClosedDemux() {
        if (dataPump != null && dataPump.isAlive()) throw new IllegalStateException("Data stream is still running");
        dvbDemux = DvbDemux.DvbDmxSwfilter();
    }

    public abstract void open() throws DvbException;

    public abstract DeviceFilter getDeviceFilter();

    public abstract DvbCapabilities readCapabilities() throws DvbException;

    public abstract int readSnr() throws DvbException;

    public abstract int readRfStrengthPercentage() throws DvbException;

    public abstract int readBitErrorRate() throws DvbException;

    public abstract Set<DvbStatus> getStatus() throws DvbException;

    // Debug string to identify device for debugging purposes
    public abstract String getDebugString();

    protected abstract void tuneTo(long freqHz, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException;

    public final void tune(long freqHz, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        tuneTo(freqHz, bandwidthHz, deliverySystem);
        if (dvbDemux != null && !resetsDemuxOnReceive()) dvbDemux.reset();
    }

    public void tuneForService(long freqHz, long bandwidthHz, @NonNull DeliverySystem deliverySystem) throws DvbException {
        tune(freqHz, bandwidthHz, deliverySystem);
    }

    protected boolean resetsDemuxOnReceive() { return false; }

    protected final void resetTransportFraming() { dvbDemux.resetTransportFraming(); }

    public int readDroppedUsbFps() throws DvbException {
        return dvbDemux.getDroppedUsbFps();
    }

    public void setPidFilter(int... pids) throws DvbException {
        dvbDemux.setPidFilter(pids);
    }

    public void disablePidFilter()throws DvbException {
        dvbDemux.disablePidFilter();
    }

    protected final void beginStreamStop() {
        DataPump pump = dataPump;
        if (pump != null) pump.stopping = true;
    }

    protected final void reportStreamFailure(IOException failure) {
        DataPump pump = dataPump;
        if (pump != null && pump.isAlive()) pump.reportFailure(failure);
    }

    @Override
    public void close() throws IOException {
        DataPump pump = dataPump;
        if (pump != null) pump.stopping = true;
        boolean interrupted = false;
        if (pump != null && Thread.currentThread() != pump) {
            pump.interrupt();
            while (pump.isAlive()) {
                try {
                    pump.join();
                } catch (InterruptedException ignored) { interrupted = true; }
            }
        }
        try { dvbDemux.close(); }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    public InputStream getTransportStream(StreamCallback streamCallback) throws DvbException {
        if (dataPump != null && dataPump.isAlive()) throw new DvbException(BAD_API_USAGE, "Data stream is still running. Please close the input stream first to start a new one");
        DataPump pump = new DataPump(streamCallback);
        DvbDemux demux = dvbDemux;
        InputStream input = new FilterInputStream(demux.getInputStream()) {
            @Override public void close() throws IOException {
                pump.stopping = true;
                pump.interrupt();
                demux.close();
            }
        };
        dataPump = pump;
        pump.start();
        return input;
    }

    public interface StreamCallback {
        void onStreamException(IOException exception);
        void onStoppedStreaming();
    }

    protected abstract ByteSource createTsSource();

    /** This thread reads from the USB device as quickly as possible and puts it into the circular buffer.
     * This thread also does pid filtering. **/
    private class DataPump extends Thread {
        private final StreamCallback callback;
        private final AtomicBoolean failureReported = new AtomicBoolean();
        private volatile boolean stopping;

        private DataPump(StreamCallback callback) {
            this.callback = callback;
        }

        private void reportFailure(IOException error) {
            if (!stopping && failureReported.compareAndSet(false, true)) callback.onStreamException(error);
            else Logger.getLogger(DvbDevice.class.getName()).log(Level.FINE,
                    "TS error during cleanup or after a primary failure", error);
        }

        @Override
        public void run() {
            setName(DataPump.class.getSimpleName());
            setPriority(MAX_PRIORITY);

            ByteSource tsSource = null;
            try {
                if (stopping) return;
                tsSource = createTsSource();
                if (stopping) return;
                tsSource.open();
                if (!resetsDemuxOnReceive()) dvbDemux.reset();
                while (!stopping && !isInterrupted()) tsSource.readNext(dvbDemux);
            } catch (InterruptedException ignored) {
                // The pump was cancelled.
            } catch (IOException e) {
                reportFailure(e);
            } catch (RuntimeException e) {
                reportFailure(new IOException("Cannot create or run TS source", e));
            } finally {
                try {
                    if (tsSource != null) tsSource.close();
                } catch (IOException e) {
                    reportFailure(e);
                } finally {
                    try {
                        if (!stopping) dvbDemux.close();
                    } catch (IOException e) {
                        reportFailure(e);
                    } finally {
                        callback.onStoppedStreaming();
                    }
                }
            }
        }
    }
}
