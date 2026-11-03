// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CancellationException;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

/** TBS5530 EP0 encoding, from official tbs5530.c at 2ce787de9f6e. */
public final class Tbs5530Bridge {
    public static final int WRITE_LIMIT = 48;
    public static final int READ_LIMIT = 50;
    public static final int TIMEOUT_MILLIS = 2000;
    public static final int I2C_READ = 1;
    public static final int DEFAULT_MANUAL_PLP = 0;
    public enum TunerCore { ASCOT3, FREIA }

    private final Tbs5530ControlIo io;
    private final Tbs5530Session session;
    private final long generation;

    public Tbs5530Bridge(Tbs5530ControlIo io, Tbs5530Session session, long generation) {
        if (io == null || session == null) throw new IllegalArgumentException("Transport and session are required");
        this.io = io;
        this.session = session;
        this.generation = generation;
    }

    public void i2cWrite(int address, int flags, byte[] data, int offset, int length) throws TransferException {
        validateAddress(address);
        if (flags != 0) throw new IllegalArgumentException("Only 7-bit I2C writes are supported");
        validateBuffer(data, offset, length, WRITE_LIMIT);
        byte[] payload = new byte[length + 2];
        payload[0] = (byte) (length + 1);
        payload[1] = (byte) (address << 1);
        System.arraycopy(data, offset, payload, 2, length);
        transfer(0x40, 0x80, 0, 0, payload, 0, payload.length, TIMEOUT_MILLIS);
    }

    public void i2cWriteRead(int writeAddress, int writeFlags, byte[] pointer, int pointerOffset, int pointerLength,
                            int readAddress, int readFlags, byte[] result, int resultOffset, int readLength)
            throws TransferException {
        validateAddress(writeAddress);
        validateAddress(readAddress);
        if (writeAddress != readAddress || writeFlags != 0 || readFlags != I2C_READ || pointerLength != 1) {
            throw new IllegalArgumentException("Expected one register pointer followed by a same-address 7-bit read");
        }
        validateBuffer(pointer, pointerOffset, pointerLength, 1);
        validateBuffer(result, resultOffset, readLength, READ_LIMIT);
        byte[] request = {(byte) readLength, (byte) (writeAddress << 1), pointer[pointerOffset]};
        byte[] response = new byte[readLength];
        session.transaction(generation, () -> {
            transfer(0x40, 0x90, 0, 0, request, 0, request.length, TIMEOUT_MILLIS);
            transfer(0xc0, 0x91, 0, 0, response, 0, response.length, TIMEOUT_MILLIS);
            synchronized (session) {
                session.requireUsable(generation);
                System.arraycopy(response, 0, result, resultOffset, readLength);
            }
        });
    }

    public void selectTerrestrialStream() throws TransferException {
        transfer(0x40, 0x8a, 0, 0, new byte[]{0x0a, 0}, 0, 2, TIMEOUT_MILLIS);
    }

    // Narrow package seam for protocol tests; deliberately no firmware execution entrypoint.
    void transfer(int type, int request, int value, int index, byte[] buffer, int offset, int length, int timeout)
            throws TransferException {
        validateBuffer(buffer, offset, length, 50);
        if (timeout <= 0 || value != 0 || index != 0) throw new IllegalArgumentException("Invalid control parameters");
        boolean allowed = request == 0x80 && type == 0x40 && length >= 3
                || request == 0x90 && type == 0x40 && length == 3
                || request == 0x91 && type == 0xc0
                || request == 0x8a && type == 0x40 && length == 2;
        if (!allowed) throw new IllegalArgumentException("Unsupported TBS5530 request or direction");
        if (request == 0x80 && ((buffer[offset] & 0xff) != length - 1
                || (buffer[offset + 1] & 1) != 0)
                || request == 0x90 && ((buffer[offset] & 0xff) < 1
                || (buffer[offset] & 0xff) > READ_LIMIT || (buffer[offset + 1] & 1) != 0)
                || request == 0x8a && (buffer[offset] != 0x0a || buffer[offset + 1] != 0)) {
            throw new IllegalArgumentException("Invalid TBS5530 control payload");
        }
        session.transaction(generation, () -> {
            session.requireUsable(generation);
            if (Thread.currentThread().isInterrupted()) {
                TransferException failure = new TransferException(FailureKind.CANCELLED, "Transfer thread interrupted");
                session.failed(generation, failure);
                throw failure;
            }
            try {
                int count = io.controlTransfer(type, request, value, index, buffer, offset, length, timeout);
                session.requireUsable(generation);
                if (count != length) {
                    FailureKind kind = count < 0 || count > length
                            ? FailureKind.IO_FAILURE : FailureKind.SHORT_TRANSFER;
                    throw new TransferException(kind, "Request 0x" + Integer.toHexString(request)
                            + " transferred " + count + " of " + length);
                }
            } catch (IOException | CancellationException e) {
                FailureKind kind = e instanceof TransferException ? ((TransferException) e).getKind()
                        : e instanceof SocketTimeoutException ? FailureKind.TIMEOUT
                        : e instanceof InterruptedIOException || e instanceof CancellationException
                        ? FailureKind.CANCELLED : FailureKind.IO_FAILURE;
                TransferException failure = new TransferException(kind,
                        "TBS5530 control request 0x" + Integer.toHexString(request) + " failed", e);
                session.failed(generation, failure);
                throw failure;
            } catch (RuntimeException e) {
                TransferException failure = new TransferException(FailureKind.IO_FAILURE, "Control transport failed", e);
                session.failed(generation, failure);
                throw failure;
            }
        });
    }

    private static void validateAddress(int address) {
        if (address < 0 || address > 0x7f) throw new IllegalArgumentException("Expected a 7-bit I2C address");
    }

    private static void validateBuffer(byte[] buffer, int offset, int length, int maximum) {
        if (buffer == null || offset < 0 || length < 1 || length > maximum
                || offset > buffer.length || length > buffer.length - offset) {
            throw new IllegalArgumentException("Invalid buffer range");
        }
    }

    public static TunerCore tunerCoreForChip(int chipId) {
        switch (chipId) {
            case 0x197: return TunerCore.ASCOT3;
            case 0x19c:
            case 0x39a: return TunerCore.FREIA;
            default: throw new IllegalArgumentException("Chip ID not reviewed for this board");
        }
    }

    public static int manualPlp(int plpId) {
        if (plpId < 0 || plpId > 255) throw new IllegalArgumentException("Manual PLP must be 0..255");
        return plpId;
    }

    public static final class Endpoint {
        public final int interfaceId;
        public final int alternateSetting;
        public final int address;
        public final int transferType;

        public Endpoint(int interfaceId, int alternateSetting, int address, int transferType) {
            if (interfaceId < 0 || interfaceId > 255 || alternateSetting < 0 || alternateSetting > 255
                    || address < 0 || address > 255 || transferType < 0 || transferType > 3) {
                throw new IllegalArgumentException("Invalid endpoint descriptor");
            }
            this.interfaceId = interfaceId;
            this.alternateSetting = alternateSetting;
            this.address = address;
            this.transferType = transferType;
        }
    }

    public static Endpoint selectBulkEndpoint(List<Endpoint> endpoints, int alternateSetting) {
        if (endpoints == null || alternateSetting < 0 || alternateSetting > 255) {
            throw new IllegalArgumentException("Endpoint descriptors and alternate setting are required");
        }
        Endpoint found = null;
        for (Endpoint endpoint : endpoints) {
            if (endpoint == null) throw new IllegalArgumentException("Null endpoint");
            if (endpoint.interfaceId == 0 && endpoint.alternateSetting == alternateSetting
                    && endpoint.address == 0x82 && endpoint.transferType == 2) {
                if (found != null) throw new IllegalArgumentException("Ambiguous TS endpoint");
                found = endpoint;
            }
        }
        if (found == null) throw new IllegalArgumentException("Missing bulk IN 0x82 on interface 0");
        return found;
    }
}
