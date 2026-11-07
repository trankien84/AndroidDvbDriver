// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** One approved terrestrial stream select and bounded RAM-only Bulk IN 0x82 observation. */
public final class Tbs5530TsSmoke {
    public enum Status { STRUCTURE_CONFIRMED, INVALID_TS, NO_DATA_OR_USB_FAILURE, LIMIT_REACHED }
    public enum ReadApi { SYNC_NATIVE_ERRNO, QUEUED_USB_REQUEST_8X4096, NATIVE_USBFS_URB_8X4096 }
    public interface Clock { long nanos(); }
    public interface ChunkObserver { void accept(byte[] bytes, int length) throws IOException; }
    public interface Permission {
        /** Must durably consume a fresh scope/attachment-bound approval before the vendor write. */
        void consume(Limits limits, long generation) throws IOException;
        /** Check foreground, live host consent, and unchanged RF IN conditions before every call. */
        void requireCurrent(long generation) throws IOException;
        void audit(String event, int ordinal, int count) throws IOException;
        default boolean endpointPreparationApproved() { return false; }
        default boolean queuedReceptionApproved() { return false; }
        default boolean nativeUrbReceptionApproved() { return false; }
    }
    public static final class Limits {
        public final int maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes;
        public final boolean prepareEndpoint;
        public final ReadApi readApi;
        public long maximumMillis() { return durationMillis + 2000L + (prepareEndpoint ? 5000L : 0) + (readApi != ReadApi.SYNC_NATIVE_ERRNO ? 1000L : 0); }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis) {
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, 4096);
        }
        /** 4096-byte default matches tbs5530.c stream.bulk.buffersize, not a cure for USB -1. */
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes) {
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, false);
        }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes, boolean prepareEndpoint) {
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, prepareEndpoint, ReadApi.SYNC_NATIVE_ERRNO);
        }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes,
                      boolean prepareEndpoint, ReadApi readApi) {
            this.readApi = Objects.requireNonNull(readApi);
            if (readApi == ReadApi.QUEUED_USB_REQUEST_8X4096 && (readChunkBytes != 4096 || maxBytes % 4096 != 0)) {
                throw new IllegalArgumentException("Queued variant requires complete 4096-byte slots");
            }
            if (readApi == ReadApi.NATIVE_USBFS_URB_8X4096 && (prepareEndpoint || readChunkBytes != 4096
                    || maxBytes < 8 * 4096 || maxBytes % 4096 != 0 || maxReads < 8)) {
                throw new IllegalArgumentException("Native URB diagnostic requires 8x4096 minimum and forbids endpoint preparation");
            }
            this.prepareEndpoint = prepareEndpoint;
            if (readChunkBytes < 1 || readChunkBytes > 16384 || maxBytes < 564 || maxBytes > 1024 * 1024 || maxReads < 1 || maxReads > 256
                    || durationMillis < 1 || durationMillis > 10000 || readTimeoutMillis < 1 || readTimeoutMillis > 2000
                    || readTimeoutMillis > durationMillis) throw new IllegalArgumentException("Invalid TS smoke bounds");
            this.maxBytes = maxBytes; this.maxReads = maxReads; this.durationMillis = durationMillis;
            this.readTimeoutMillis = readTimeoutMillis; this.readChunkBytes = readChunkBytes;
        }
    }
    public static final class Result {
        public final Status status;
        public final int bytes, reads;
        public final long elapsedNanos;
        public final Tbs5530TsParser.Snapshot packets;
        public final int lastReadResult, lastReadRequestedBytes, lastReadTimeoutMillis, lastReadErrno;
        /** Managed read wall time includes ownership checks; it is not isolated native latency. */
        public final long lastReadWallNanos;
        public final boolean endpointPrepared;
        Result(Status status, int bytes, int reads, long elapsedNanos, Tbs5530TsParser.Snapshot packets,
               int lastReadResult, int requested, int timeout, long readWallNanos, boolean endpointPrepared, int errno) {
            lastReadErrno = errno;
            this.endpointPrepared = endpointPrepared;
            this.status = status; this.bytes = bytes; this.reads = reads; this.elapsedNanos = elapsedNanos; this.packets = packets;
            this.lastReadResult = lastReadResult; lastReadRequestedBytes = requested;
            lastReadTimeoutMillis = timeout; lastReadWallNanos = readWallNanos;
        }
        public double bytesPerSecond() { return elapsedNanos > 0 ? bytes * 1_000_000_000.0 / elapsedNanos : 0; }
        public boolean usbDataReceived() { return bytes > 0; }
        public boolean structureConfirmed() { return status == Status.STRUCTURE_CONFIRMED; }
        /** A small error-free sample is not a viewing-quality or long-term stability certification. */
        public String qualityObservation() {
            if (!structureConfirmed()) return "NOT_ASSESSED";
            return packets.transportErrorPackets != 0 || packets.continuityErrors != 0 || packets.malformedPackets != 0
                    || packets.syncLosses != 0 ? "ERRORS_OBSERVED" : "NO_ERRORS_IN_BOUNDED_SAMPLE";
        }
    }
    private final Tbs5530Session session;
    private final long generation;
    private final Tbs5530Bridge bridge;
    private final Tbs5530BulkTransport bulk;
    private final Permission permission;
    private final Clock clock;
    private final ReadApi readApi;
    private final ChunkObserver observer;
    private boolean used;

    public Tbs5530TsSmoke(Tbs5530Session session, long generation, Tbs5530Bridge bridge,
                         Tbs5530BulkTransport bulk, Permission permission, Clock clock) {
        this(session, generation, bridge, bulk, permission, clock, ReadApi.SYNC_NATIVE_ERRNO);
    }
    Tbs5530TsSmoke(Tbs5530Session session, long generation, Tbs5530Bridge bridge,
                   Tbs5530BulkTransport bulk, Permission permission, Clock clock, ReadApi readApi) {
        this(session, generation, bridge, bulk, permission, clock, readApi, (bytes, length) -> { });
    }
    Tbs5530TsSmoke(Tbs5530Session session, long generation, Tbs5530Bridge bridge,
                   Tbs5530BulkTransport bulk, Permission permission, Clock clock, ReadApi readApi, ChunkObserver observer) {
        this.observer = Objects.requireNonNull(observer);
        this.readApi = Objects.requireNonNull(readApi);
        this.session = Objects.requireNonNull(session); this.generation = generation;
        this.bridge = Objects.requireNonNull(bridge); this.bulk = Objects.requireNonNull(bulk);
        this.permission = Objects.requireNonNull(permission); this.clock = Objects.requireNonNull(clock);
    }
    public Result run(Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        if (limits.readApi != readApi) throw new IOException("Receive backend and approved transcript differ");
        synchronized (this) {
            if (used) throw new IOException("TS smoke cannot be retried");
            used = true;
        }
        requireLocked(); permission.requireCurrent(generation);
        if (limits.prepareEndpoint && !permission.endpointPreparationApproved()) throw new IOException("Separate endpoint preparation approval required");
        if (readApi == ReadApi.QUEUED_USB_REQUEST_8X4096 && !permission.queuedReceptionApproved()) {
            throw new IOException("Exact queued receive diagnostic approval required");
        }
        if (readApi == ReadApi.NATIVE_USBFS_URB_8X4096 && !permission.nativeUrbReceptionApproved()) {
            throw new IOException("Exact native usbfs URB diagnostic approval required");
        }
        permission.consume(limits, generation);
        long started = clock.nanos(), dataStarted = started;
        boolean endpointPrepared = false;
        int bytes = 0, reads = 0, lastRead = Integer.MIN_VALUE, lastRequested = 0, lastTimeout = 0;
        long lastReadWall = 0;
        int lastErrno = -1;
        Tbs5530TsParser parser = new Tbs5530TsParser();
        Status status = Status.LIMIT_REACHED;
        Throwable failure = null;
        try {
            if (limits.prepareEndpoint) {
                // Official usb-urb.c:usb_urb_init:220, before any URBs, on the same claimed endpoint.
                permission.audit("ENDPOINT82_CLEAR_HALT_INTENT", 0, 0);
                permission.requireCurrent(generation); requireLocked();
                if (!permission.endpointPreparationApproved()) throw new IOException("Endpoint approval revoked");
                bulk.prepareEndpoint82(); endpointPrepared = true;
                permission.audit("ENDPOINT82_CLEAR_HALT_COMPLETE", 0, 0);
                permission.requireCurrent(generation); requireLocked();
                if (elapsed(started) >= (limits.maximumMillis() - limits.durationMillis) * 1_000_000L) {
                    throw new IOException("Insufficient remaining program budget after endpoint preparation");
                }
                dataStarted = clock.nanos();
            }
            permission.audit("STREAM_SELECT_INTENT", 0, 2);
            permission.requireCurrent(generation); requireLocked();
            bridge.selectTerrestrialStream();
            permission.audit("STREAM_SELECT_COMPLETE", 0, 2);
            session.streamingStarted(generation);
            byte[] buffer = new byte[Math.min(limits.readChunkBytes, limits.maxBytes)];
            while (bytes < limits.maxBytes && reads < limits.maxReads) {
                permission.requireCurrent(generation); requireLocked();
                if (readApi != ReadApi.SYNC_NATIVE_ERRNO && limits.maxBytes - bytes < 4096) break;
                long elapsed = elapsed(dataStarted);
                long remaining = limits.durationMillis * 1_000_000L - elapsed;
                if (remaining < 1_000_000L) break;
                int timeout = (int)Math.min(limits.readTimeoutMillis, remaining / 1_000_000L);
                int length = Math.min(buffer.length, limits.maxBytes - bytes);
                permission.audit("BULK_READ_INTENT", reads + 1, length);
                permission.requireCurrent(generation); requireLocked();
                remaining = limits.durationMillis * 1_000_000L - elapsed(dataStarted);
                if (remaining < 1_000_000L) {
                    permission.audit("BULK_READ_NOT_STARTED_DEADLINE", reads + 1, 0);
                    break;
                }
                timeout = (int)Math.min(limits.readTimeoutMillis, remaining / 1_000_000L);
                lastRequested = length; lastTimeout = timeout;
                long readStarted = clock.nanos();
                int count = bulk.read(buffer, length, timeout);
                lastRead = count; lastErrno = bulk.lastReadErrno(); lastReadWall = elapsed(readStarted);
                reads++;
                permission.audit("BULK_READ_COMPLETE", reads, count);
                permission.requireCurrent(generation); requireLocked();
                if (count <= 0) { status = Status.NO_DATA_OR_USB_FAILURE; break; }
                observer.accept(buffer, count);
                bytes += count; parser.accept(buffer, 0, count);
            }
            Tbs5530TsParser.Snapshot parsed = parser.snapshot();
            long elapsed = elapsed(started);
            boolean validStructure = parsed.packets >= 3 && parsed.synchronizedToStream && parsed.hasValidProgramStructure()
                    && parsed.syncLosses == 0 && parsed.malformedPackets == 0;
            if (status != Status.NO_DATA_OR_USB_FAILURE) {
                if (validStructure && elapsed(dataStarted) <= limits.durationMillis * 1_000_000L
                        && elapsed <= limits.maximumMillis() * 1_000_000L) status = Status.STRUCTURE_CONFIRMED;
                else if (!validStructure && bytes > 0) status = Status.INVALID_TS;
            }
            permission.audit("TS_SMOKE_FINISHED", reads, bytes);
            return new Result(status, bytes, reads, elapsed, parsed, lastRead, lastRequested, lastTimeout, lastReadWall, endpointPrepared, lastErrno);
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
