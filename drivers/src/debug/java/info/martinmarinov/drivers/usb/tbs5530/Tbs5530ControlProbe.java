// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Debug-only exact-plan EP0 gate. A descriptor grant never authorizes this CONTROL_PROBE. */
public final class Tbs5530ControlProbe {
    public interface Authorization {
        void requireCurrent() throws IOException;
        /** Persist consumption of one approved transcript/hash/epoch before any USB OUT. */
        void consume(Tbs5530Frontend.Plan plan) throws IOException;
        void audit(String boundary, int ordinal, int type, int request, int length, int result) throws IOException;
        /** A fresh physical RF IN confirmation is independent of control-write approval. */
        boolean rfInConfirmed();
        default boolean experimentalAutoPlpApproved() { return false; }
    }
    private final Tbs5530Session session;
    private final long generation;
    private final Authorization authorization;
    private final Gate gate;
    private final Tbs5530Frontend frontend;
    private boolean executing;

    public Tbs5530ControlProbe(Tbs5530OperationalHandoff owner, Authorization authorization, Tbs5530Frontend.Clock clock)
            throws IOException {
        this(owner.session(), owner.session().getGeneration(), owner.controlIo(), authorization, clock);
        if (!owner.operationalOwner()) throw new IOException("Fresh operational session required");
    }
    Tbs5530ControlProbe(Tbs5530Session session, long generation, Tbs5530ControlIo io,
                       Authorization authorization, Tbs5530Frontend.Clock clock) throws IOException {
        this.session = Objects.requireNonNull(session); this.generation = generation;
        this.authorization = Objects.requireNonNull(authorization);
        gate = new Gate(Objects.requireNonNull(io));
        frontend = new Tbs5530Frontend(new Tbs5530Bridge(gate, session, generation), session, generation, clock);
    }
    public Tbs5530Frontend.Plan planProbe() throws IOException { return frontend.planProbe(); }
    public Tbs5530Frontend.Plan planInitialize() throws IOException { return frontend.planInitialize(); }
    public void readyForTune() throws IOException { frontend.readyForTune(); }
    public Tbs5530Frontend.Plan planTune(Tbs5530DvbT2TuneParameters parameters) throws IOException { return frontend.planTune(parameters); }
    public Tbs5530Frontend.Plan planExperimentalAutoTune(Tbs5530DvbT2TuneParameters parameters) throws IOException {
        if (parameters == null || parameters.selection.mode != Tbs5530DvbT2TuneParameters.Mode.AUTO) {
            throw new IllegalArgumentException("Explicit AUTO candidate required");
        }
        return frontend.planTune(parameters, true);
    }
    public Tbs5530Frontend.Plan planPollStatus(int maximumSamples) throws IOException { return frontend.planPollStatus(maximumSamples); }
    public Tbs5530Frontend.State state() { return frontend.getState(); }
    public void cancel() { frontend.cancel(); session.requestCancellation(generation, false); }

    public synchronized Tbs5530Frontend.Result execute(Tbs5530Frontend.Plan plan) throws IOException {
        Objects.requireNonNull(plan);
        if (executing) throw new IOException("Control program already running");
        if (plan.generation != generation) throw new IOException("Stale approval plan");
        session.requireUsable(generation);
        authorization.requireCurrent();
        if (needsRf(plan) && !authorization.rfInConfirmed()) throw new IOException("AWAITING_RF_IN_CONNECTION");
        requireExperimentalConsent(plan);
        authorization.consume(plan);
        executing = true;
        gate.begin(plan);
        try {
            Tbs5530Frontend.Result result = frontend.execute(plan);
            gate.complete(plan.countsAreUpperBounds);
            authorization.requireCurrent(); session.requireUsable(generation);
            if (needsRf(plan) && !authorization.rfInConfirmed()) throw new IOException("RF_IN_CONDITIONS_CHANGED");
            switch (result.operation) {
                case PROBE:
                    session.ready(generation); // Only after an allowlisted, content-checked chip-ID transaction.
                    break;
                case INITIALIZE: session.frontendInitialized(generation); break;
                case TUNE: session.frontendLock(generation, false); break;
                case STATUS: case POLL_STATUS: session.frontendLock(generation, result.status.locked); break;
                default: throw new AssertionError(result.operation);
            }
            return result;
        } catch (IOException | RuntimeException error) {
            frontend.cancel(); session.failed(generation, error); throw error;
        } finally { executing = false; gate.end(); }
    }
    private void requireExperimentalConsent(Tbs5530Frontend.Plan plan) throws IOException {
        if (plan.tuneParameters != null && plan.tuneParameters.selection.mode == Tbs5530DvbT2TuneParameters.Mode.AUTO
                && !authorization.experimentalAutoPlpApproved()) throw new IOException("EXPERIMENTAL_AUTO_PLP_NOT_APPROVED");
    }
    private static boolean needsRf(Tbs5530Frontend.Plan plan) {
        return plan.operation == Tbs5530Frontend.Operation.TUNE || plan.operation == Tbs5530Frontend.Operation.STATUS
                || plan.operation == Tbs5530Frontend.Operation.POLL_STATUS;
    }
    private final class Gate implements Tbs5530ControlIo {
        private final Tbs5530ControlIo io;
        private final Map<Integer, Integer> readBytes = new HashMap<>();
        private Tbs5530Frontend.Plan plan;
        private int step, ordinal;
        private boolean readPending;
        Gate(Tbs5530ControlIo io) { this.io = io; }
        void begin(Tbs5530Frontend.Plan value) { plan = value; step = 0; ordinal = 0; readPending = false; readBytes.clear(); }
        void end() { plan = null; readBytes.clear(); }
        void skipNonIo() {
            while (step < plan.getTransactions().size()) {
                Tbs5530Frontend.Kind kind = plan.getTransactions().get(step).kind;
                if (kind == Tbs5530Frontend.Kind.WRITE || kind == Tbs5530Frontend.Kind.READ) return;
                step++;
            }
        }
        void complete(boolean upperBound) throws IOException {
            skipNonIo();
            if (readPending || !upperBound && step != plan.getTransactions().size()) throw new IOException("Incomplete approved program");
        }
        public int controlTransfer(int type, int request, int value, int index, byte[] bytes,
                                   int offset, int length, int timeout) throws IOException {
            if (!executing || plan == null) throw new IOException("No consumed control approval");
            skipNonIo();
            if (step >= plan.getTransactions().size()) throw new IOException("Approved program exhausted");
            Tbs5530Frontend.RegisterTransaction next = plan.getTransactions().get(step);
            int expectedType, expectedRequest, expectedLength;
            byte[] expected = null;
            if (next.kind == Tbs5530Frontend.Kind.WRITE) {
                Integer source = next.sourceStep < 0 ? Integer.valueOf(0) : readBytes.get(next.sourceStep);
                if (source == null) throw new IOException("Missing approved read dependency");
                expected = next.usbWritePayload(source); expectedType = 0x40; expectedRequest = 0x80; expectedLength = expected.length;
            } else if (!readPending) {
                expected = next.usbReadPointerPayload(); expectedType = 0x40; expectedRequest = 0x90; expectedLength = 3;
            } else { expectedType = 0xc0; expectedRequest = 0x91; expectedLength = next.readLength; }
            if (type != expectedType || request != expectedRequest || value != 0 || index != 0 || timeout != 2000
                    || bytes == null || offset != 0 || length != expectedLength || bytes.length != length
                    || expected != null && !Arrays.equals(expected, bytes)) throw new IOException("USB request differs from approved whitelist");
            authorization.requireCurrent(); session.requireUsable(generation);
            if (needsRf(plan) && !authorization.rfInConfirmed()) throw new IOException("RF_IN_CONDITIONS_CHANGED");
            int current = ++ordinal;
            authorization.audit("INTENT", current, type, request, length, -1);
            authorization.requireCurrent(); session.requireUsable(generation);
            requireExperimentalConsent(plan);
            int result = io.controlTransfer(type, request, value, index, bytes, offset, length, timeout);
            authorization.audit("RETURN", current, type, request, length, result);
            authorization.requireCurrent(); session.requireUsable(generation);
            if (result != length) throw new IOException("Approved control transfer incomplete");
            if (next.kind == Tbs5530Frontend.Kind.WRITE) step++;
            else if (!readPending) readPending = true;
            else { readBytes.put(step, bytes[0] & 255); readPending = false; step++; }
            return result;
        }
    }
}
