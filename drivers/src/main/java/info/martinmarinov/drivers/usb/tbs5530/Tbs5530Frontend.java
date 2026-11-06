// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

/**
 * Source-bounded CXD6802/ASCOT3I terrestrial path, without Android or firmware operations.
 * Creation is package-only: the operational wrapper must first establish bridge evidence.
 * This class never calls Session.ready/frontendInitialized/frontendLock or reads descriptors.
 * A full-length USB result is transport evidence, not a silicon ID or RF lock.
 *
 * Official: tbsdtv/linux_media 2ce787de9f6e81b4294692a45de15596ae2e1322,
 * drivers/media/dvb-frontends/cxd2878.c (Davin Zhang / TurboSight, GPL-2.0-or-later).
 * Comparison: koreapyj/cxd28xx c5fa27eecbe80633cbb365cc44656daea1c0e2e3,
 * Till0196/coax 11228b05fb1626bf41c4e1879ace10cd0a8d575c; see sequence provenance.
 */
public final class Tbs5530Frontend {
    public enum State {
        BRIDGE_VERIFIED, DEMOD_IDENTIFIED, FRONTEND_INITIALIZED,
        READY_FOR_TUNE, DVBT2_LOCKED, UNCERTAIN
    }
    public enum Operation { PROBE, INITIALIZE, TUNE, STATUS, POLL_STATUS }
    public enum Kind { WRITE, READ, DELAY, CHECK_ID, CHECK_TUNER, CHECK_ZERO, CHECK_PARALLEL, CHECK_TS, STATUS }
    public enum Metric { NOT_AVAILABLE }

    /** Monotonic clock and interruptible delay; no wall-clock time or implicit sleeps. */
    public interface Clock {
        long elapsedRealtimeMillis();
        void delayMillis(long millis) throws InterruptedException;
    }

    /**
     * One immutable register action, including explicit bank-select writes (register 00).
     * A derived WRITE's single byte is ((read[sourceStep][0] & sourceMask) >>> shift) | value.
     * These data-dependent RMW/calibration bytes are not falsely advertised as known constants.
     * READ is precisely OUT 40/90 [length,address<<1,register], IN C0/91 [length].
     * WRITE is precisely OUT 40/80 [data.length+2,address<<1,register,data...].
     * All requests have value/index zero and timeout 2000 ms. No retry is present.
     */
    public static final class RegisterTransaction {
        public final Kind kind;
        public final int address;
        public final int bank;
        public final int register;
        public final int readLength;
        public final int sourceStep;
        public final int secondSourceStep;
        public final int sourceMask;
        public final int shift;
        public final int value;
        public final int delayMillis;
        public final String provenance;
        private final byte[] data;

        private RegisterTransaction(Kind kind, int address, int bank, int register, byte[] data,
                                    int readLength, int sourceStep, int secondSourceStep,
                                    int mask, int shift, int value, int delayMillis, String provenance) {
            this.kind = kind;
            this.address = address;
            this.bank = bank;
            this.register = register;
            this.data = data == null ? null : data.clone();
            this.readLength = readLength;
            this.sourceStep = sourceStep;
            this.secondSourceStep = secondSourceStep;
            this.sourceMask = mask;
            this.shift = shift;
            this.value = value;
            this.delayMillis = delayMillis;
            this.provenance = provenance;
        }

        /** Null only for non-write steps or a write derived from a preceding read. */
        public byte[] getData() { return data == null ? null : data.clone(); }
        public boolean isReadDerivedWrite() { return kind == Kind.WRITE && sourceStep >= 0; }
        public int getUsbOutCount() { return kind == Kind.WRITE || kind == Kind.READ ? 1 : 0; }
        public int getUsbInCount() { return kind == Kind.READ ? 1 : 0; }
        public int getUsbOutLength() {
            return kind == Kind.READ ? 3 : kind == Kind.WRITE ? (sourceStep < 0 ? data.length : 1) + 3 : 0;
        }
        public int getUsbInLength() { return kind == Kind.READ ? readLength : 0; }
        public byte[] usbReadPointerPayload() {
            if (kind != Kind.READ) throw new IllegalStateException("Not a register read");
            return new byte[]{(byte) readLength, (byte) (address << 1), (byte) register};
        }

        /** Resolve an approved read-dependent formula; the input is the referenced one-byte read. */
        public byte[] resolveWriteData(int sourceByte) {
            if (kind != Kind.WRITE) throw new IllegalStateException("Not a register write");
            if (sourceByte < 0 || sourceByte > 255) throw new IllegalArgumentException("Expected one byte");
            return sourceStep < 0 ? data.clone()
                    : new byte[]{(byte) (((sourceByte & sourceMask) >>> shift) | value)};
        }

        /** Exact USB write payload after resolving the explicitly declared read dependency. */
        public byte[] usbWritePayload(int sourceByte) {
            byte[] body = resolveWriteData(sourceByte);
            byte[] payload = new byte[body.length + 3];
            payload[0] = (byte) (body.length + 2);
            payload[1] = (byte) (address << 1);
            payload[2] = (byte) register;
            System.arraycopy(body, 0, payload, 3, body.length);
            return payload;
        }
    }

    /** Single-use, instance/epoch/state-bound program. Construction never touches the transport. */
    public static final class Plan {
        public final Operation operation;
        public final long generation;
        public final int usbOutCount;
        public final int usbInCount;
        public final long delayMillis;
        public final long maximumMillis;
        /** Polling can stop early; all other successful paths use exactly the declared counts. */
        public final boolean countsAreUpperBounds;
        public final Tbs5530DvbT2TuneParameters tuneParameters;
        private final Tbs5530Frontend owner;
        private final long revision;
        private final List<RegisterTransaction> transactions;

        private Plan(Tbs5530Frontend owner, Operation operation, Builder builder,
                     Tbs5530DvbT2TuneParameters parameters) {
            this.owner = owner;
            this.operation = operation;
            generation = owner.generation;
            revision = owner.revision;
            transactions = Collections.unmodifiableList(new ArrayList<>(builder.steps));
            tuneParameters = parameters;
            int out = 0, in = 0;
            long delay = 0;
            for (RegisterTransaction step : transactions) {
                out += step.getUsbOutCount();
                in += step.getUsbInCount();
                delay += step.delayMillis;
            }
            usbOutCount = out;
            usbInCount = in;
            delayMillis = delay;
            maximumMillis = delay + (long) (out + in) * Tbs5530Bridge.TIMEOUT_MILLIS;
            countsAreUpperBounds = operation == Operation.POLL_STATUS;
        }
        public List<RegisterTransaction> getTransactions() { return transactions; }

        /** Canonical, versioned approval input, including read-dependent formulae, not resolved bytes. */
        public String getApprovalTranscript() {
            StringBuilder text = new StringBuilder("TBS5530-CXD2878-PLAN/v1\n");
            text.append(operation).append('|').append(generation).append('|').append(revision)
                    .append('|').append(usbOutCount).append('|').append(usbInCount)
                    .append('|').append(delayMillis).append('|').append(maximumMillis)
                    .append('|').append(countsAreUpperBounds).append('\n');
            text.append("EP0|WRITE=40/80|READ=40/90,C0/91|value=0|index=0|timeoutMs=2000|retry=0\n");
            if (tuneParameters != null) text.append(tuneParameters.frequencyHz).append('|')
                    .append(tuneParameters.bandwidthHz).append('|').append(tuneParameters.plpId).append('\n');
            for (RegisterTransaction step : transactions) {
                text.append(step.kind).append('|').append(step.address).append('|').append(step.bank)
                        .append('|').append(step.register).append('|').append(step.readLength)
                        .append('|').append(step.sourceStep).append('|').append(step.secondSourceStep)
                        .append('|').append(step.sourceMask).append('|').append(step.shift)
                        .append('|').append(step.value).append('|').append(step.delayMillis).append('|');
                if (step.data != null) for (byte value : step.data) {
                    text.append("0123456789abcdef".charAt((value & 255) >>> 4))
                            .append("0123456789abcdef".charAt(value & 15));
                }
                text.append('|').append(step.provenance).append('\n');
            }
            return text.toString();
        }
        public String getSha256() {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(getApprovalTranscript().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder text = new StringBuilder(64);
                for (byte value : digest) text.append("0123456789abcdef".charAt((value & 255) >>> 4))
                        .append("0123456789abcdef".charAt(value & 15));
                return text.toString();
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
    }

    public static final class Status {
        public final int raw;
        public final int syncState;
        public final boolean tsLock;
        public final boolean unlockDetected;
        public final boolean locked;
        public final Metric signalStrength = Metric.NOT_AVAILABLE;
        public final Metric cnr = Metric.NOT_AVAILABLE;
        public final Metric bitErrorRate = Metric.NOT_AVAILABLE;
        public final Metric packetErrorRate = Metric.NOT_AVAILABLE;

        private Status(int raw) {
            this.raw = raw;
            syncState = raw & 7;
            tsLock = (raw & 0x20) != 0;
            unlockDetected = (raw & 0x10) != 0;
            // cxd2878_read_status:3312-3323; this is not a TS packet/continuity observation.
            locked = syncState >= 6 && tsLock && !unlockDetected;
        }
    }

    public static final class Result {
        public final Operation operation;
        public final State state;
        public final int chipId;
        public final Status status;
        public final int usbOutCompleted;
        public final int usbInCompleted;
        private Result(Operation operation, State state, int chipId, Status status, int out, int in) {
            this.operation = operation;
            this.state = state;
            this.chipId = chipId;
            this.status = status;
            usbOutCompleted = out;
            usbInCompleted = in;
        }
    }

    private final Tbs5530Bridge bridge;
    private final Tbs5530Session session;
    private final long generation;
    private final Clock clock;
    private State state = State.BRIDGE_VERIFIED;
    private long revision;
    private int chipId = -1;
    private boolean tuned;
    private final Object publicationLock = new Object();
    private volatile boolean cancelled;

    /** The caller owns approval and must supply the same bridge/session/generation. */
    Tbs5530Frontend(Tbs5530Bridge bridge, Tbs5530Session session, long generation, Clock clock)
            throws TransferException {
        if (bridge == null || session == null || clock == null) throw new IllegalArgumentException("Dependencies required");
        session.requireUsable(generation);
        this.bridge = bridge;
        this.session = session;
        this.generation = generation;
        this.clock = clock;
    }

    public synchronized State getState() {
        try { session.requireUsable(generation); }
        catch (TransferException e) { state = State.UNCERTAIN; }
        if (cancelled) state = State.UNCERTAIN;
        return state;
    }
    public synchronized int getChipId() { return chipId; }
    /** Logical cancellation only. No corrective write, repeater cleanup write, or automatic retry. */
    public void cancel() { synchronized (publicationLock) { cancelled = true; } }

    public synchronized Plan planProbe() throws TransferException {
        requireState(State.BRIDGE_VERIFIED);
        Builder b = new Builder();
        b.source("cxd2878.c:cxd2878_attach:4010-4014,4066-4070");
        b.bank(Tbs5530Cxd2878Sequences.X, 0);
        int high = b.read(Tbs5530Cxd2878Sequences.X, 0xfb, 1);
        int low = b.read(Tbs5530Cxd2878Sequences.X, 0xfd, 1);
        b.check(Kind.CHECK_ID, high, low);
        return new Plan(this, Operation.PROBE, b, null);
    }

    public synchronized Plan planInitialize() throws TransferException {
        requireState(State.DEMOD_IDENTIFIED);
        if (chipId != 0x197) throw new UnsupportedOperationException("Freia 0x19c/0x39a init/tune is not ported");
        Builder b = new Builder();
        Tbs5530Cxd2878Sequences.initialize(b);
        return new Plan(this, Operation.INITIALIZE, b, null);
    }

    /** Only a completed initialization can reach this milestone; never a descriptor or ID probe. */
    public synchronized void readyForTune() throws TransferException {
        requireState(State.FRONTEND_INITIALIZED);
        state = State.READY_FOR_TUNE;
        revision++;
    }

    public synchronized Plan planTune(Tbs5530DvbT2TuneParameters parameters) throws TransferException {
        if (parameters == null) throw new IllegalArgumentException("Explicit tune parameters required");
        requireState(State.READY_FOR_TUNE, State.DVBT2_LOCKED);
        Builder b = new Builder();
        Tbs5530Cxd2878Sequences.tune(b, parameters, tuned);
        return new Plan(this, Operation.TUNE, b, parameters);
    }

    public synchronized Plan planStatus() throws TransferException { return statusPlan(1, false); }
    /** HZ (1000 ms) from cxd2878_tune:3775-3796, at most 20 samples, stop on lock/unlock. */
    public synchronized Plan planPollStatus(int maximumSamples) throws TransferException {
        if (maximumSamples < 1 || maximumSamples > 20) throw new IllegalArgumentException("Poll bound must be 1..20");
        return statusPlan(maximumSamples, true);
    }
    private Plan statusPlan(int samples, boolean poll) throws TransferException {
        requireState(State.READY_FOR_TUNE, State.DVBT2_LOCKED);
        if (!tuned) throw new IllegalStateException("No completed DVB-T2 tune in this epoch");
        Builder b = new Builder();
        b.source("cxd2878.c:cxd2878_read_status:3312-3323; cxd2878_tune:3775-3796");
        for (int i = 0; i < samples; i++) {
            if (i != 0) b.delay(1000);
            b.bank(Tbs5530Cxd2878Sequences.T, 0x20);
            b.check(Kind.STATUS, b.read(Tbs5530Cxd2878Sequences.T, 0x10, 1), -1);
        }
        return new Plan(this, poll ? Operation.POLL_STATUS : Operation.STATUS, b, null);
    }

    /**
     * Execute only this instance's reviewed, unchanged plan. The wrapper must approve its exact
     * register list, dependent-byte formulae, USB counts and time bound before calling this method.
     * The entire plan shares Session.transaction, not just the pointer/read pair. No bank cache
     * survives a plan boundary, external bus use, cancellation, detach, or session generation.
     */
    public synchronized Result execute(Plan plan) throws TransferException {
        if (plan == null || plan.owner != this || plan.generation != generation || plan.revision != revision) {
            throw new IllegalArgumentException("Foreign, consumed, or stale plan");
        }
        ensureUsable();
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Plan revision exhausted");
        revision++;
        final List<byte[]> reads = new ArrayList<>(Collections.nCopies(plan.transactions.size(), null));
        final Status[] status = {null};
        final int[] counts = {0, 0};
        try {
            session.transaction(generation, () -> {
                long start = clock.elapsedRealtimeMillis();
                if (start < 0 || start > Long.MAX_VALUE - plan.maximumMillis) throw problem("Invalid monotonic clock");
                long[] previous = {start};
                for (int i = 0; i < plan.transactions.size(); i++) {
                    checkBound(start, plan.maximumMillis, previous);
                    RegisterTransaction step = plan.transactions.get(i);
                    switch (step.kind) {
                        case WRITE:
                            byte[] data = step.resolveWriteData(step.sourceStep < 0 ? 0 : reads.get(step.sourceStep)[0] & 255);
                            byte[] buffer = new byte[data.length + 1];
                            buffer[0] = (byte) step.register;
                            System.arraycopy(data, 0, buffer, 1, data.length);
                            bridge.i2cWrite(step.address, 0, buffer, 0, buffer.length);
                            counts[0]++;
                            break;
                        case READ:
                            byte[] answer = new byte[step.readLength];
                            bridge.i2cWriteRead(step.address, 0, new byte[]{(byte) step.register}, 0, 1,
                                    step.address, Tbs5530Bridge.I2C_READ, answer, 0, answer.length);
                            reads.set(i, answer);
                            counts[0]++;
                            counts[1]++;
                            break;
                        case DELAY:
                            delay(step.delayMillis, start, plan.maximumMillis, previous);
                            break;
                        case CHECK_ID:
                            int id = ((reads.get(step.sourceStep)[0] & 3) << 8)
                                    | (reads.get(step.secondSourceStep)[0] & 255);
                            if (id != 0x197 && id != 0x19c && id != 0x39a) throw problem("Unreviewed demod ID 0x" + Integer.toHexString(id));
                            if (plan.operation != Operation.PROBE && id != chipId) throw problem("Demod ID changed after reset");
                            chipId = id;
                            break;
                        case CHECK_TUNER:
                            if ((reads.get(step.sourceStep)[0] & 0xf0) != 0xe0) {
                                throw problem("Only ASCOT3I E-family tuner initialization is ported");
                            }
                            break;
                        case CHECK_ZERO:
                            if (reads.get(step.sourceStep)[0] != 0) throw problem("ASCOT3 CPU boot status is not zero");
                            break;
                        case CHECK_PARALLEL:
                            if ((reads.get(step.sourceStep)[0] & 0x80) != 0) throw problem("Only board parallel TS mode is ported");
                            break;
                        case CHECK_TS:
                            if ((reads.get(step.sourceStep)[0] & 3) != 0) throw problem("Only TS output is ported, not TLV");
                            break;
                        case STATUS:
                            status[0] = new Status(reads.get(step.sourceStep)[0] & 255);
                            break;
                        default: throw new AssertionError(step.kind);
                    }
                    checkBound(start, plan.maximumMillis, previous);
                    if (step.kind == Kind.STATUS && plan.countsAreUpperBounds
                            && (status[0].locked || status[0].unlockDetected)) break;
                }
                ensureUsable();
            });
            synchronized (publicationLock) {
                synchronized (session) {
                    ensureUsable();
                    switch (plan.operation) {
                        case PROBE: state = State.DEMOD_IDENTIFIED; break;
                        case INITIALIZE: state = State.FRONTEND_INITIALIZED; break;
                        case TUNE: tuned = true; state = State.READY_FOR_TUNE; break;
                        case STATUS: case POLL_STATUS:
                            state = status[0].locked ? State.DVBT2_LOCKED : State.READY_FOR_TUNE;
                            break;
                        default: throw new AssertionError(plan.operation);
                    }
                    return new Result(plan.operation, state, chipId, status[0], counts[0], counts[1]);
                }
            }
        } catch (TransferException e) {
            state = State.UNCERTAIN;
            tuned = false;
            session.failed(generation, e);
            throw e;
        } catch (RuntimeException e) {
            state = State.UNCERTAIN;
            tuned = false;
            TransferException failure = new TransferException(FailureKind.IO_FAILURE, "Frontend execution failed", e);
            session.failed(generation, failure);
            throw failure;
        }
    }

    private void requireState(State... allowed) throws TransferException {
        ensureUsable();
        for (State value : allowed) if (state == value) return;
        throw new IllegalStateException("Frontend is " + state);
    }
    private void ensureUsable() throws TransferException {
        try {
            session.requireUsable(generation);
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new TransferException(FailureKind.CANCELLED, "Frontend cancelled");
            }
            if (state == State.UNCERTAIN) throw problem("Frontend requires a new verified session epoch");
        } catch (TransferException e) {
            state = State.UNCERTAIN;
            tuned = false;
            throw e;
        }
    }
    private void checkBound(long start, long maximum, long[] previous) throws TransferException {
        ensureUsable();
        long now = clock.elapsedRealtimeMillis();
        if (now < previous[0] || now - start > maximum) throw new TransferException(FailureKind.TIMEOUT, "Frontend time bound exceeded or clock regressed");
        previous[0] = now;
    }
    private void delay(int millis, long start, long maximum, long[] previous) throws TransferException {
        for (int left = millis; left > 0;) {
            checkBound(start, maximum, previous);
            int slice = Math.min(left, 10);
            long before = previous[0];
            try { clock.delayMillis(slice); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransferException(FailureKind.CANCELLED, "Frontend delay interrupted", e);
            }
            checkBound(start, maximum, previous);
            if (previous[0] - before < slice) throw problem("Delay returned before its source-required interval");
            left -= slice;
        }
    }
    private static TransferException problem(String message) { return new TransferException(FailureKind.IO_FAILURE, message); }

    static final class Builder {
        final List<RegisterTransaction> steps = new ArrayList<>();
        private String provenance;
        private final int[] banks = new int[128];
        Builder() { java.util.Arrays.fill(banks, -1); }
        void source(String source) { provenance = Tbs5530Cxd2878Sequences.OFFICIAL + source; }
        void bank(int address, int bank) {
            banks[address] = bank;
            write(address, 0, bank);
        }
        void write(int address, int register, int... values) {
            byte[] data = new byte[values.length];
            for (int i = 0; i < values.length; i++) data[i] = (byte) values[i];
            steps.add(new RegisterTransaction(Kind.WRITE, address, banks[address], register, data,
                    0, -1, -1, 0, 0, 0, 0, provenance));
        }
        int read(int address, int register, int length) {
            int index = steps.size();
            steps.add(new RegisterTransaction(Kind.READ, address, banks[address], register, null,
                    length, -1, -1, 0, 0, 0, 0, provenance));
            return index;
        }
        void masked(int address, int register, int value, int mask) {
            if (mask == 255) write(address, register, value);
            else derived(address, register, read(address, register, 1), mask ^ 255, 0, value & mask);
        }
        void bankMasked(int address, int bank, int register, int value, int mask) {
            bank(address, bank);
            masked(address, register, value, mask);
        }
        void derived(int address, int register, int source, int mask, int shift, int value) {
            steps.add(new RegisterTransaction(Kind.WRITE, address, banks[address], register, null,
                    0, source, -1, mask, shift, value, 0, provenance));
        }
        void delay(int millis) {
            steps.add(new RegisterTransaction(Kind.DELAY, -1, -1, -1, null,
                    0, -1, -1, 0, 0, 0, millis, provenance));
        }
        void check(Kind kind, int source, int secondSource) {
            steps.add(new RegisterTransaction(kind, -1, -1, -1, null,
                    0, source, secondSource, 0, 0, 0, 0, provenance));
        }
    }
}
