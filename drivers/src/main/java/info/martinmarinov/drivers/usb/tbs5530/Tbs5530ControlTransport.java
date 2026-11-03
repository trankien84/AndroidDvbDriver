// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Generation-bound capability, created only from the coordinator's claimed connection. */
public final class Tbs5530ControlTransport implements Tbs5530ControlIo {
    interface Connection extends Tbs5530Session.Resources {
        void requireControlWorker() throws IOException;
        int controlTransfer(int type, int request, int value, int index,
                            byte[] buffer, int offset, int length, int timeout) throws IOException;
    }
    private final Tbs5530Session session;
    private final long generation;
    private final Connection connection;

    Tbs5530ControlTransport(Tbs5530Session session, long generation, Connection connection) {
        this.session = Objects.requireNonNull(session);
        this.generation = generation;
        this.connection = Objects.requireNonNull(connection);
    }

    @Override public int controlTransfer(int type, int request, int value, int index,
                                         byte[] buffer, int offset, int length, int timeout) throws IOException {
        if (type < 0 || type > 255 || request < 0 || request > 255 || value < 0 || value > 65535
                || index < 0 || index > 65535 || timeout <= 0 || length < 0 || length > 65535 || offset < 0
                || (buffer == null ? length != 0 || offset != 0 : offset > buffer.length || length > buffer.length - offset)) {
            throw new IllegalArgumentException("Invalid USB control parameters or buffer range");
        }
        connection.requireControlWorker();
        boolean input = (type & 0x80) != 0;
        byte[] scratch = buffer == null ? null : buffer.clone();
        session.transaction(generation, () -> {
            session.requireOwned(generation, connection);
            try {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Control thread interrupted");
                int count = connection.controlTransfer(type, request, value, index, scratch, offset, length, timeout);
                session.requireOwned(generation, connection);
                if (count < 0 || count > length) {
                    throw new TransferException(FailureKind.IO_FAILURE, "USB control returned " + count + " for " + length);
                }
                if (count != length) throw new TransferException(FailureKind.SHORT_TRANSFER,
                        "USB control transferred " + count + " of " + length);
                if (input && length > 0) synchronized (session) {
                    session.requireOwned(generation, connection);
                    System.arraycopy(scratch, offset, buffer, offset, length);
                }
            } catch (IOException | RuntimeException error) {
                FailureKind kind = error instanceof TransferException ? ((TransferException) error).getKind()
                        : error instanceof SocketTimeoutException ? FailureKind.TIMEOUT
                        : error instanceof InterruptedIOException || error instanceof CancellationException
                        ? FailureKind.CANCELLED : FailureKind.IO_FAILURE;
                IOException failure = error instanceof IOException ? (IOException) error
                        : new TransferException(kind, "USB control failed", error);
                session.failed(generation, failure);
                throw failure instanceof TransferException ? (TransferException) failure
                        : new TransferException(kind, "USB control failed", failure);
            }
        });
        return length;
    }
}
