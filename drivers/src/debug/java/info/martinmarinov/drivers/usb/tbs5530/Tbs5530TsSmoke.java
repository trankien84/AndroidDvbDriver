// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** One approved terrestrial stream select and bounded RAM-only Bulk IN 0x82 observation. */
public final class Tbs5530TsSmoke {
    public enum Status { STRUCTURE_CONFIRMED, INVALID_TS, NO_DATA_OR_USB_FAILURE, LIMIT_REACHED }
    public enum ReadApi {
        SYNC_NATIVE_ERRNO, QUEUED_USB_REQUEST_8X4096, NATIVE_USBFS_URB_8X4096, NATIVE_USBFS_URB_8X8192, NATIVE_USBFS_STREAM;
        public boolean isNativeUrb() {
            return this == NATIVE_USBFS_URB_8X4096 || this == NATIVE_USBFS_URB_8X8192 || this == NATIVE_USBFS_STREAM;
        }
    }
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
        default boolean explicitSetInterfaceAlt0Approved() { return false; }
        default boolean sonyTsOutputReadbackApproved() { return false; }
        default void beforeFirstBulk() throws IOException { throw new IOException("Readback hook unavailable"); }
    }
    public static final class Limits {
        public final int maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, queueDepth;
        public final boolean prepareEndpoint, explicitSetInterfaceAlt0, sonyTsOutputReadback;
        public final boolean b4Capture, tunerRssi;
        public final ReadApi readApi;
        public static Limits b4(int durationMillis, int queueDepth, boolean tunerRssi) {
            return new Limits(256 * 1024 * 1024, 65536, durationMillis, 500, 16384,
                    false, ReadApi.NATIVE_USBFS_STREAM, false, false, queueDepth, true, tunerRssi);
        }
        public long maximumMillis() { return durationMillis + 2000L + (prepareEndpoint ? 5000L : 0)
                + (explicitSetInterfaceAlt0 ? 2000L : 0) + (sonyTsOutputReadback ? Tbs5530SonyTsOutputReadback.MAXIMUM_MILLIS : 0)
                + (readApi != ReadApi.SYNC_NATIVE_ERRNO ? 1000L : 0); }
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
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, prepareEndpoint, readApi, false);
        }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes,
                      boolean prepareEndpoint, ReadApi readApi, boolean explicitSetInterfaceAlt0) {
            this(maxBytes,maxReads,durationMillis,readTimeoutMillis,readChunkBytes,prepareEndpoint,readApi,explicitSetInterfaceAlt0,false);
        }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes,
                      boolean prepareEndpoint, ReadApi readApi, boolean explicitSetInterfaceAlt0, boolean sonyReadback) {
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, prepareEndpoint,
                    readApi, explicitSetInterfaceAlt0, sonyReadback, readApi == ReadApi.NATIVE_USBFS_STREAM ? 16 : 8);
        }
        public Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes,
                      boolean prepareEndpoint, ReadApi readApi, boolean explicitSetInterfaceAlt0, boolean sonyReadback, int queueDepth) {
            this(maxBytes, maxReads, durationMillis, readTimeoutMillis, readChunkBytes, prepareEndpoint,
                    readApi, explicitSetInterfaceAlt0, sonyReadback, queueDepth, false, false);
        }
        private Limits(int maxBytes, int maxReads, int durationMillis, int readTimeoutMillis, int readChunkBytes,
                       boolean prepareEndpoint, ReadApi readApi, boolean explicitSetInterfaceAlt0, boolean sonyReadback,
                       int queueDepth, boolean b4Capture, boolean tunerRssi) {
            this.b4Capture = b4Capture; this.tunerRssi = tunerRssi;
            this.readApi = Objects.requireNonNull(readApi);
            this.queueDepth = queueDepth;
            if (b4Capture && (readApi != ReadApi.NATIVE_USBFS_STREAM || (queueDepth != 16 && queueDepth != 32)
                    || readChunkBytes != 16384 || durationMillis < 1000 || durationMillis > 60000
                    || maxBytes != 256 * 1024 * 1024))
                throw new IllegalArgumentException("Invalid B4 file capture configuration");
            if (readApi == ReadApi.NATIVE_USBFS_STREAM) {
                if (queueDepth < 8 || queueDepth > 32 || readChunkBytes < 4096 || readChunkBytes > 65536
                        || readChunkBytes % 512 != 0 || maxBytes < queueDepth * readChunkBytes || maxBytes > (b4Capture ? 256 : 16) * 1024 * 1024
                        || maxReads < 1 || maxReads > 65536 || durationMillis < 1 || durationMillis > (b4Capture ? 60000 : 10000)
                        || readTimeoutMillis < 1 || readTimeoutMillis > 2000 || prepareEndpoint || explicitSetInterfaceAlt0 || sonyReadback)
                    throw new IllegalArgumentException("Invalid bounded native stream configuration");
                this.maxBytes = maxBytes; this.maxReads = maxReads; this.durationMillis = durationMillis;
                this.readTimeoutMillis = readTimeoutMillis; this.readChunkBytes = readChunkBytes;
                this.prepareEndpoint = false; this.explicitSetInterfaceAlt0 = false; sonyTsOutputReadback = false;
                return;
            }
            if (queueDepth != 8) throw new IllegalArgumentException("Legacy queue depth must remain eight");
            this.explicitSetInterfaceAlt0 = explicitSetInterfaceAlt0; sonyTsOutputReadback=sonyReadback;
            if (sonyReadback && (explicitSetInterfaceAlt0 || prepareEndpoint || readApi != ReadApi.NATIVE_USBFS_URB_8X4096
                    || maxBytes != 65536 || maxReads != 16 || durationMillis != 5000 || readTimeoutMillis != 500 || readChunkBytes != 4096))
                throw new IllegalArgumentException("Sony readback requires the exact native4096 baseline without endpoint changes");
            if (explicitSetInterfaceAlt0 && (readApi != ReadApi.NATIVE_USBFS_URB_8X4096 || prepareEndpoint
                    || maxBytes != 65536 || maxReads != 16 || durationMillis != 5000
                    || readTimeoutMillis != 500 || readChunkBytes != 4096)) {
                throw new IllegalArgumentException("EXPLICIT_SET_INTERFACE_ALT0 requires the exact native 8x4096 baseline");
            }
            if (readApi == ReadApi.QUEUED_USB_REQUEST_8X4096 && (readChunkBytes != 4096 || maxBytes % 4096 != 0)) {
                throw new IllegalArgumentException("Queued variant requires complete 4096-byte slots");
            }
            int nativeChunkBytes = readApi == ReadApi.NATIVE_USBFS_URB_8X8192 ? 8192 : 4096;
            if (readApi.isNativeUrb() && (prepareEndpoint || readChunkBytes != nativeChunkBytes
                    || maxBytes < 8 * nativeChunkBytes || maxBytes % nativeChunkBytes != 0 || maxReads < 8)) {
                throw new IllegalArgumentException("Native URB diagnostic requires eight complete exact-sized slots and forbids endpoint preparation");
            }
            this.prepareEndpoint = prepareEndpoint;
            if (readChunkBytes < 1 || readChunkBytes > 16384 || maxBytes < 564 || maxBytes > 1024 * 1024 || maxReads < 1 || maxReads > 256
                    || durationMillis < 1 || durationMillis > 10000 || readTimeoutMillis < 1 || readTimeoutMillis > 2000
                    || readTimeoutMillis > durationMillis) throw new IllegalArgumentException("Invalid TS smoke bounds");
            this.maxBytes = maxBytes; this.maxReads = maxReads; this.durationMillis = durationMillis;
            this.readTimeoutMillis = readTimeoutMillis; this.readChunkBytes = readChunkBytes;
        }
    }
    public static final class StreamMetrics {
        public final int bytes, minOutstanding, firstStatus, firstErrno, outstanding;
        public final long submitted, completed, elapsedNanos, starvationCount, maxResubmitNanos, medianResubmitNanos;
        public final boolean quarantined;
        public final long writerWrittenBytes, writerDropBytes;
        public final String writerFailure;
        public final String stopReason;
        StreamMetrics(info.martinmarinov.usbxfer.NativeUsbfsStream.Result stream) {
            writerWrittenBytes = stream.writtenBytes; writerDropBytes = stream.dropBytes; writerFailure = stream.writerFailure;
            bytes = stream.byteCount; minOutstanding = stream.minOutstanding; firstStatus = stream.firstStatus;
            firstErrno = stream.firstErrno; outstanding = stream.outstanding; submitted = stream.submitted;
            completed = stream.completed; elapsedNanos = stream.elapsedNanos; starvationCount = stream.starvationCount;
            maxResubmitNanos = stream.maxResubmitNanos; medianResubmitNanos = stream.medianResubmitNanos;
            quarantined = stream.quarantined; stopReason = stream.stopReason;
        }
        public double bytesPerSecond() { return elapsedNanos > 0 ? bytes * 1_000_000_000.0 / elapsedNanos : 0; }
    }
    public static final class Result {
        public final Status status;
        public final StreamMetrics nativeStream;
        public final int bytes, reads;
        public final long elapsedNanos;
        public final Tbs5530TsParser.Snapshot packets;
        public final int lastReadResult, lastReadRequestedBytes, lastReadTimeoutMillis, lastReadErrno;
        /** Managed read wall time includes ownership checks; it is not isolated native latency. */
        public final long lastReadWallNanos;
        public final boolean endpointPrepared;
        Result(Status status, int bytes, int reads, long elapsedNanos, Tbs5530TsParser.Snapshot packets,
               int lastReadResult, int requested, int timeout, long readWallNanos, boolean endpointPrepared, int errno) {
            this(status, bytes, reads, elapsedNanos, packets, lastReadResult, requested, timeout, readWallNanos, endpointPrepared, errno, null);
        }
        Result(Status status, int bytes, int reads, long elapsedNanos, Tbs5530TsParser.Snapshot packets,
               int lastReadResult, int requested, int timeout, long readWallNanos, boolean endpointPrepared, int errno,
               info.martinmarinov.usbxfer.NativeUsbfsStream.Result stream) {
            nativeStream = stream == null ? null : new StreamMetrics(stream);
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
    interface StreamReceiver { info.martinmarinov.usbxfer.NativeUsbfsStream.Result receive(Limits limits) throws IOException; }
    private StreamReceiver streamReceiver;
    void streamReceiver(StreamReceiver receiver) { streamReceiver = Objects.requireNonNull(receiver); }
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
        if (readApi.isNativeUrb() && !permission.nativeUrbReceptionApproved()) {
            throw new IOException("Exact native usbfs URB diagnostic approval required");
        }
        if (limits.explicitSetInterfaceAlt0 && !permission.explicitSetInterfaceAlt0Approved()) {
            throw new IOException("Exact EXPLICIT_SET_INTERFACE_ALT0 diagnostic approval required");
        }
        if (limits.sonyTsOutputReadback && !permission.sonyTsOutputReadbackApproved())
            throw new IOException("Exact SONY_TS_OUTPUT_READBACK diagnostic approval required");
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
            if (limits.sonyTsOutputReadback) {
                permission.audit("SONY_READBACK_BEFORE_FIRST_URB_INTENT",0,0);
                permission.beforeFirstBulk();
                permission.requireCurrent(generation); requireLocked();
                if (!permission.sonyTsOutputReadbackApproved()) throw new IOException("Sony readback grant revoked");
                permission.audit("SONY_READBACK_BEFORE_FIRST_URB_COMPLETE",0,0);
                dataStarted = clock.nanos();
            }
            if (readApi == ReadApi.NATIVE_USBFS_STREAM) {
                if (streamReceiver == null) throw new IOException("Native stream receiver unavailable");
                permission.requireCurrent(generation); requireLocked();
                info.martinmarinov.usbxfer.NativeUsbfsStream.Result stream = streamReceiver.receive(limits);
                if (stream.quarantined || stream.outstanding != 0) throw new IOException("Native stream drain not certified");
                int saved = 0, prefixRemaining = 2048;
                if (stream.dataFile == null) {
                    byte[] data = stream.data;
                    if (data.length > 0) {
                        observer.accept(data, data.length); saved = data.length;
                        int skip = Math.min(prefixRemaining, data.length);
                        parser.accept(data, skip, data.length - skip);
                    }
                } else {
                    byte[] chunk = new byte[65536];
                    try (java.io.InputStream input = stream.openData()) {
                        for (int count; (count = input.read(chunk)) != -1; ) {
                            if (count == 0) continue;
                            if (count > limits.maxBytes - saved) throw new IOException("B4 raw exceeds approved cap");
                            observer.accept(chunk, count); saved += count;
                            int skip = Math.min(prefixRemaining, count); prefixRemaining -= skip;
                            if (count > skip) parser.accept(chunk, skip, count - skip);
                        }
                    }
                    if (saved != stream.writtenBytes) throw new IOException("B4 raw writer accounting mismatch");
                }
                Tbs5530TsParser.Snapshot parsed = parser.snapshot();
                boolean clean = stream.firstStatus == 0 && stream.firstErrno == 0
                        && ("BYTE_LIMIT".equals(stream.stopReason) || "DEADLINE".equals(stream.stopReason));
                boolean writerClean = stream.dropBytes == 0 && stream.writerFailure == null && saved == stream.byteCount;
                Status result = !clean ? Status.NO_DATA_OR_USB_FAILURE
                        : writerClean && stream.starvationCount == 0 && b3StructureConfirmed(parsed) ? Status.STRUCTURE_CONFIRMED
                        : saved == 0 ? Status.NO_DATA_OR_USB_FAILURE : Status.INVALID_TS;
                permission.audit("TS_SMOKE_FINISHED", (int)stream.completed, saved);
                return new Result(result, saved, (int)stream.completed, stream.elapsedNanos, parsed,
                        stream.firstStatus == 0 ? saved : -1, limits.readChunkBytes, limits.durationMillis,
                        stream.elapsedNanos, false, stream.firstErrno, stream);
            }
            byte[] buffer = new byte[Math.min(limits.readChunkBytes, limits.maxBytes)];
            while (bytes < limits.maxBytes && reads < limits.maxReads) {
                permission.requireCurrent(generation); requireLocked();
                if (readApi != ReadApi.SYNC_NATIVE_ERRNO && limits.maxBytes - bytes < limits.readChunkBytes) break;
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
    static boolean b3StructureConfirmed(Tbs5530TsParser.Snapshot parsed) {
        return parsed.packets >= 3 && parsed.synchronizedToStream && parsed.hasValidProgramStructure()
                && parsed.syncLosses == 0 && parsed.continuityErrors == 0 && parsed.transportErrorPackets == 0
                && parsed.malformedPackets == 0 && parsed.scrambledPackets == 0 && parsed.discardedBytes <= 187;
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
