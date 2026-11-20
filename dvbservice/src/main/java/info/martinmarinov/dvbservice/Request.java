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

package info.martinmarinov.dvbservice;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Set;

import info.martinmarinov.drivers.DeliverySystem;
import info.martinmarinov.drivers.DvbCapabilities;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.DvbStatus;
import info.martinmarinov.drivers.usb.tbs5530.Tbs5530DvbDevice;

/**
 * The client sends a command consisting of a variable number of Longs in the following format:
 * <p>
 * byte 0 will be the Request.ordinal of the request
 * byte 1 will be N the number of longs in the payload
 * byte 2 to 8*N+1 will consist the actual values of the payload
 * <p>
 * After the request has been processed it returns a Response, which is in a similar format.
 * Please refer to the Response documentation for more info
 * <p>
 * Warning: For backwards compatibility order of the enum will be always preserved
 */
enum Request {
    REQ_PROTOCOL_VERSION(
            new Executor() {
                @Override
                public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
                    // Clients can use it to determine whether new features
                    // are available.
                    // WARNING: Backward compatibility should always be ensured
                    return Response.success(
                            0L, // parameter 1, version, when adding capabilities, change that number.
                            ALL_REQUESTS.length // parameter 2, can be useful for determining supported commands
                    );
                }
            }
    ),
    REQ_EXIT(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... payload) {
            Log.d(TAG, "Client requested to close the connection");
            return Response.SUCCESS;
        }
    }),
    REQ_TUNE(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
            DeliverySystem[] systems = DeliverySystem.values();
            if (payload.length != 3 || payload[2] < 0 || payload[2] >= systems.length) return Response.ERROR;
            dvbDevice.tuneForService(payload[0], payload[1], systems[(int) payload[2]]);
            return Response.SUCCESS;
        }
    }),
    REQ_GET_STATUS(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... ignored) throws DvbException {
            int snr = dvbDevice.readSnr();
            int bitErrorRate = dvbDevice.readBitErrorRate();
            int droppedUsbFps = dvbDevice.readDroppedUsbFps();
            int rfStrengthPercentage = dvbDevice.readRfStrengthPercentage();
            Set<DvbStatus> status = dvbDevice.getStatus();
            boolean hasSignal = status.contains(DvbStatus.FE_HAS_SIGNAL);
            boolean hasCarrier = status.contains(DvbStatus.FE_HAS_CARRIER);
            boolean hasSync = status.contains(DvbStatus.FE_HAS_SYNC);
            boolean hasLock = status.contains(DvbStatus.FE_HAS_LOCK);
            if (dvbDevice instanceof Tbs5530DvbDevice) Log.i(TAG, "TBS5530 status lock=" + hasLock
                    + " snr_mdB=" + snr + " ber_u16=" + bitErrorRate + " dropped_fps=" + droppedUsbFps
                    + " rf_percent=" + rfStrengthPercentage);

            return Response.success(
                    (long) snr, // parameter 1
                    (long) bitErrorRate, // parameter 2
                    (long) droppedUsbFps, // parameter 3
                    (long) rfStrengthPercentage, // parameter 4
                    hasSignal ? 1L : 0L, // parameter 5
                    hasCarrier ? 1L : 0L, // parameter 6
                    hasSync ? 1L : 0L, // parameter 7
                    hasLock ? 1L : 0L // parameter 8
            );
        }
    }),
    REQ_SET_PIDS(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
            if (payload.length == 1 && payload[0] == 0x2000) {
                dvbDevice.disablePidFilter();
            } else {
                int[] pids = new int[payload.length];
                for (int i = 0; i < payload.length; i++) {
                    if (payload[i] < 0 || payload[i] > 0x1fff) return Response.ERROR;
                    pids[i] = (int) payload[i];
                }
                dvbDevice.setPidFilter(pids);
            }
            return Response.SUCCESS;
        }
    }),
    REQ_GET_CAPABILITIES(new Executor() {
        @Override
        public Response execute(DvbDevice dvbDevice, long... payload) throws DvbException {
            DvbCapabilities frontendProperties = dvbDevice.readCapabilities();

            // Only up to 62 deliverySystems are supported under current encoding method
            if (frontendProperties.getSupportedDeliverySystems().size() > 62)
                throw new IllegalStateException();
            long supportedDeliverySystems = 0;
            for (DeliverySystem deliverySystem : frontendProperties.getSupportedDeliverySystems()) {
                supportedDeliverySystems |= 1 << deliverySystem.ordinal();
            }

            return Response.success(
                    supportedDeliverySystems, // parameter 1
                    frontendProperties.getFrequencyMin(), // parameter 2
                    frontendProperties.getFrequencyMax(), // parameter 3
                    frontendProperties.getFrequencyStepSize(), // parameter 4
                    (long) dvbDevice.getDeviceFilter().getVendorId(), // parameter 5
                    (long) dvbDevice.getDeviceFilter().getProductId() // parameter 6
            );
        }
    });

    private final static String TAG = Request.class.getSimpleName();
    private final static Request[] ALL_REQUESTS = values();

    private final Executor executor;

    Request(Executor executor) {
        this.executor = executor;
    }

    private Response execute(DvbDevice dvbDevice, long... payload) {
        try {
            return executor.execute(dvbDevice, payload);
        } catch (DvbException e) {
            Log.i(TAG, name() + " failed: " + e.getErrorCode().name());
            return Response.ERROR;
        } catch (RuntimeException e) {
            Log.w(TAG, name() + " failed: " + e.getClass().getSimpleName());
            return Response.ERROR;
        }
    }

    private static void readBytes(InputStream inputStream, byte[] read_buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < read_buffer.length) {
            int count = inputStream.read(read_buffer, bytesRead, read_buffer.length - bytesRead);
            if (count < 0) {
                throw new SocketException("End of stream reached before reading required number of bytes");
            }
            bytesRead += count;
        }
    }

    private static long readLongAtByte(byte[] read_buffer, int offset) {
        return (((long) read_buffer[offset] << 56) +
                ((long) (read_buffer[offset + 1] & 255) << 48) +
                ((long) (read_buffer[offset + 2] & 255) << 40) +
                ((long) (read_buffer[offset + 3] & 255) << 32) +
                ((long) (read_buffer[offset + 4] & 255) << 24) +
                ((read_buffer[offset + 5] & 255) << 16) +
                ((read_buffer[offset + 6] & 255) << 8) +
                ((read_buffer[offset + 7] & 255)));
    }

    private static long[] parsePayload(InputStream inputStream, int size) throws IOException {
        byte[] payload_buffer = new byte[size * 8];
        readBytes(inputStream, payload_buffer);

        long[] payload = new long[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = readLongAtByte(payload_buffer, i * 8);
        }
        return payload;
    }

    public static Request parseAndExecute(InputStream inputStream, OutputStream outputStream, DvbDevice dvbDevice) throws IOException {
        Request req = null;

        byte[] head_buffer = new byte[2];
        readBytes(inputStream, head_buffer);

        int ordinal = head_buffer[0] & 0xFF;
        int size = head_buffer[1] & 0xFF;
        long[] payload = parsePayload(inputStream, size);

        if (ordinal < ALL_REQUESTS.length) {
            req = ALL_REQUESTS[ordinal];

            boolean trace = dvbDevice instanceof Tbs5530DvbDevice;
            long start = trace ? System.nanoTime() : 0;
            if (trace) Log.i(TAG, "TBS5530 " + req.name() + " " + java.util.Arrays.toString(payload));
            Response r = req.execute(dvbDevice, payload);
            r.serialize(req, outputStream);
            if (trace && req == REQ_TUNE) Log.i(TAG, "TBS5530 tune_ms=" + (System.nanoTime() - start) / 1_000_000);
        }

        return req;
    }

    private interface Executor {
        Response execute(DvbDevice dvbDevice, long... payload) throws DvbException;
    }
}
