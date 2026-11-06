// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** One approved terrestrial stream select and bounded RAM-only Bulk IN 0x82 observation. */
public final class Tbs5530TsSmoke {
    public enum Status { STRUCTURE_CONFIRMED, INVALID_TS, NO_DATA_OR_USB_FAILURE, LIMIT_REACHED }
    public interface Clock { long nanos(); }
    public interface Permission {
        /** Must durably consume a fresh scope/attachment-bound approval before the vendor write. */
        void consume(Limits limits, long generation) throws IOException;
        /** Check foreground, live host consent, and unchanged RF IN conditions before every call. */
        void requireCurrent(long generation) throws IOException;
        void audit(String event, int ordinal, int count) throws IOException;
    }
    public static final class Limits {
        public final int maxBytes, maxReads, durationMillis, readTimeoutMillis;
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis) {
            if (maxBytes < 564 || maxBytes > 1024 * 1024 || maxReads < 1 || maxReads > 256
                    || durationMillis < 1 || durationMillis > 10000 || readTimeoutMillis < 1 || readTimeoutMillis > 2000
                    || readTimeoutMillis > durationMillis) throw new IllegalArgumentException("Invalid TS smoke bounds");
            this.maxBytes = maxBytes; this.maxReads = maxReads; this.durationMillis = durationMillis;
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }
    public static final class Result {
        public final Status status;
        public final int bytes, reads;
        public final long elapsedNanos;
        public final Tbs5530TsParser.Snapshot packets;
        Result(Status status, int bytes, int reads, long elapsedNanos, Tbs5530TsParser.Snapshot packets) {
            this.status = status; this.bytes = bytes; this.reads = reads; this.elapsedNanos = elapsedNanos; this.packets = packets;
        }
        public double bytesPerSecond() { return elapsedNanos > 0 ? bytes * 1_000_000_000.0 / elapsedNanos : 0; }
    }
    private final Tbs5530Session session;
    private final long generation;
    private final Tbs5530Bridge bridge;
    private final Tbs5530BulkTransport bulk;
    private final Permission permission;
    private final Clock clock;
    private boolean used;

    public Tbs5530TsSmoke(Tbs5530Session session, long generation, Tbs5530Bridge bridge,
                         Tbs5530BulkTransport bulk, Permission permission, Clock clock) {
        this.session = Objects.requireNonNull(session); this.generation = generation;
        this.bridge = Objects.requireNonNull(bridge); this.bulk = Objects.requireNonNull(bulk);
        this.permission = Objects.requireNonNull(permission); this.clock = Objects.requireNonNull(clock);
    }
    public Result run(Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        synchronized (this) {
            if (used) throw new IOException("TS smoke cannot be retried");
            used = true;
        }
        requireLocked(); permission.requireCurrent(generation); permission.consume(limits, generation);
        long started = clock.nanos();
        int bytes = 0, reads = 0;
        Tbs5530TsParser parser = new Tbs5530TsParser();
        Status status = Status.LIMIT_REACHED;
        Throwable failure = null;
        try {
            permission.audit("STREAM_SELECT_INTENT", 0, 2);
            permission.requireCurrent(generation); requireLocked();
            bridge.selectTerrestrialStream();
            permission.audit("STREAM_SELECT_COMPLETE", 0, 2);
            session.streamingStarted(generation);
            byte[] buffer = new byte[Math.min(16384, limits.maxBytes)];
            while (bytes < limits.maxBytes && reads < limits.maxReads) {
                permission.requireCurrent(generation); requireLocked();
                long elapsed = elapsed(started);
                long remaining = limits.durationMillis * 1_000_000L - elapsed;
                if (remaining < 1_000_000L) break;
                int timeout = (int)Math.min(limits.readTimeoutMillis, remaining / 1_000_000L);
                int length = Math.min(buffer.length, limits.maxBytes - bytes);
                permission.audit("BULK_READ_INTENT", reads + 1, length);
                permission.requireCurrent(generation); requireLocked();
                int count = bulk.read(buffer, length, timeout);
                reads++;
                permission.audit("BULK_READ_COMPLETE", reads, count);
                permission.requireCurrent(generation); requireLocked();
                if (count <= 0) { status = Status.NO_DATA_OR_USB_FAILURE; break; }
                bytes += count; parser.accept(buffer, 0, count);
            }
            Tbs5530TsParser.Snapshot parsed = parser.snapshot();
            if (status != Status.NO_DATA_OR_USB_FAILURE && parsed.packets >= 3 && parsed.synchronizedToStream && parsed.syncLosses == 0
                    && parsed.continuityErrors == 0 && parsed.malformedPackets == 0 && parsed.transportErrorPackets == 0) {
                status = Status.STRUCTURE_CONFIRMED;
            } else if (bytes > 0 && status != Status.NO_DATA_OR_USB_FAILURE) status = Status.INVALID_TS;
            long elapsed = elapsed(started);
            permission.audit("TS_SMOKE_FINISHED", reads, bytes);
            return new Result(status, bytes, reads, elapsed, parsed);
        } catch (IOException | RuntimeException error) {
            failure = error;
            session.failed(generation, error);
            throw error;
        } finally {
            // There is no documented independent stop command in the reviewed source.
            // Invalidate and drain the owned lease; never invent a corrective vendor write.
            try { session.cancel(generation); }
            catch (IOException | RuntimeException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
    private long elapsed(long started) throws IOException {
        long elapsed = clock.nanos() - started;
        if (elapsed < 0) throw new IOException("Monotonic clock moved backwards");
        return elapsed;
    }
    private void requireLocked() throws IOException {
        session.requireUsable(generation);
        if (session.getState() != Tbs5530Session.State.READY || !session.isInterfaceClaimed()
                || !session.isFrontendInitialized() || !session.isFrontendLocked()) {
            throw new IOException("Runtime frontend initialization and DVB-T2 lock required before stream select");
        }
    }
}
