// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

/** Synchronous, generation-bound IN-only transport. Short bulk reads are valid data, unlike EP0 commands. */
public final class Tbs5530BulkTransport {
    interface Worker { void require() throws IOException; }
    interface Read { int read(byte[] bytes, int length, int timeoutMillis) throws IOException; }
    interface EndpointPreparation { void prepare() throws IOException; }
    interface ReadErrno { int value() throws IOException; }
    private final Tbs5530Session session;
    private final long generation;
    private final Tbs5530Session.Resources owner;
    private final Worker worker;
    private final Read read;
    private final EndpointPreparation preparation;
    private final ReadErrno errno;
    private int lastErrno = -1;

    Tbs5530BulkTransport(Tbs5530Session session, long generation, Tbs5530Session.Resources owner, Worker worker, Read read) {
        this(session, generation, owner, worker, read, () -> { throw new IOException("No reviewed endpoint preparation port"); });
    }
    Tbs5530BulkTransport(Tbs5530Session session, long generation, Tbs5530Session.Resources owner, Worker worker, Read read,
                        EndpointPreparation preparation) {
        this(session, generation, owner, worker, read, preparation, () -> -1);
    }
    Tbs5530BulkTransport(Tbs5530Session session, long generation, Tbs5530Session.Resources owner, Worker worker, Read read,
                        EndpointPreparation preparation, ReadErrno errno) {
        this.errno = Objects.requireNonNull(errno);
        this.preparation = Objects.requireNonNull(preparation);
        this.session = Objects.requireNonNull(session); this.generation = generation;
        this.owner = Objects.requireNonNull(owner); this.worker = Objects.requireNonNull(worker); this.read = Objects.requireNonNull(read);
    }

    /** Fixed Linux usb_urb_init preparation, never implicit in read or available as a retry. */
    void prepareEndpoint82() throws IOException {
        worker.require();
        session.transaction(generation, () -> {
            session.requireOwned(generation, owner);
            if (!session.isFrontendInitialized() || !session.isFrontendLocked() || session.isStreaming()) {
                throw new TransferException(FailureKind.SESSION_UNAVAILABLE, "Endpoint preparation requires lock before streaming");
            }
            try {
                preparation.prepare(); session.requireOwned(generation, owner);
            } catch (IOException | RuntimeException error) {
                session.failed(generation, error);
                throw new TransferException(FailureKind.IO_FAILURE, "Endpoint preparation failed; no retry", error);
            }
        });
    }

    public int read(byte[] destination, int length, int timeoutMillis) throws IOException {
        if (destination == null || length < 1 || length > destination.length || length > 16384
                || timeoutMillis < 1 || timeoutMillis > 2000) throw new IllegalArgumentException("Invalid bounded bulk read");
        worker.require();
        byte[] scratch = new byte[length];
        int[] count = new int[1];
        session.transaction(generation, () -> {
            requireStream();
            try {
                if (Thread.currentThread().isInterrupted()) throw new TransferException(FailureKind.CANCELLED, "Bulk read interrupted");
                count[0] = read.read(scratch, length, timeoutMillis);
                lastErrno = errno.value();
                requireStream();
                if (count[0] < -1 || count[0] > length) throw new IOException("Invalid bulk result count");
                if (count[0] > 0) synchronized (session) {
                    requireStream();
                    System.arraycopy(scratch, 0, destination, 0, count[0]);
                }
            } catch (IOException | RuntimeException error) {
                session.failed(generation, error);
                if (error instanceof TransferException) throw (TransferException) error;
                throw new TransferException(FailureKind.IO_FAILURE, "Bulk read failed", error);
            }
        });
        return count[0];
    }

    int lastReadErrno() throws IOException { worker.require(); session.requireOwned(generation, owner); return lastErrno; }

    private void requireStream() throws TransferException {
        session.requireOwned(generation, owner);
        if (!session.isStreaming() || !session.isFrontendInitialized() || !session.isFrontendLocked()) {
            throw new TransferException(FailureKind.SESSION_UNAVAILABLE, "Confirmed frontend lock and active stream required");
        }
    }
}
