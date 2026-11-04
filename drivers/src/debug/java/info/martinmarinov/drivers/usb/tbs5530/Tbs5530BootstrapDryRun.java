// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.FailureKind;

/** Package-private construction, fake-only wiring in tests. No Android bootstrap port or app entrypoint. */
public final class Tbs5530BootstrapDryRun {
    public enum State { UNKNOWN, BOOTSTRAP_CANDIDATE, FIRMWARE_VALIDATED, LOADING,
        TRANSITIONING, OPERATIONAL_CANDIDATE, FAULTED, UNCERTAIN }
    interface Delay { void await(int millis) throws IOException; }
    interface Preflight { void requireCurrent() throws IOException; }
    private final Tbs5530Session session;
    private final Tbs5530UsbBinding binding;
    private final Tbs5530Fx2ProtocolPlan plan;
    private final Delay delay;
    private final Preflight preflight;
    private final List<String> trace = new ArrayList<>();
    private State state = State.UNKNOWN;
    private boolean acquired, executed;
    private int attempted;
    private long epoch;
    private FailureKind failure;

    Tbs5530BootstrapDryRun(Tbs5530Session session, Tbs5530UsbBinding binding,
                          Tbs5530Fx2Fingerprint.Result fingerprint, Tbs5530FirmwarePlan plan, Delay delay) {
        this(session, binding, fingerprint, Tbs5530Fx2ProtocolPlan.describe(plan,
                Tbs5530Fx2ProtocolPlan.Id.TBS_SOURCE_DUAL_RESET.name()), delay, () -> { });
    }

    Tbs5530BootstrapDryRun(Tbs5530Session session, Tbs5530UsbBinding binding,
                          Tbs5530Fx2Fingerprint.Result fingerprint, Tbs5530Fx2ProtocolPlan plan,
                          Delay delay, Preflight preflight) {
        if (session==null || binding==null || fingerprint==null || plan==null || delay==null || preflight==null) {
            throw new IllegalArgumentException("Validated offline inputs required");
        }
        if (fingerprint.candidate!=Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE) {
            throw new IllegalArgumentException("Exact bootstrap fingerprint required");
        }
        this.session=session; this.binding=binding; this.plan=plan; this.delay=delay;
        this.preflight=preflight;
        trace.add("OFFLINE plan=" + plan.getId().name() + " provenance=" + plan.getProvenance());
        state=State.BOOTSTRAP_CANDIDATE; trace.add(state.name());
        state=State.FIRMWARE_VALIDATED; trace.add(state.name());
    }

    void acquire(Tbs5530UsbBinding.Device device) throws IOException {
        synchronized(this) {
            if(acquired || executed) throw new IllegalStateException("One-shot acquisition");
            acquired=true;
        }
        try {
            preflight.requireCurrent();
            binding.startBootstrap(device);
            epoch=binding.bootstrapEpoch();
        } catch (IOException | RuntimeException error) {
            synchronized (this) {
                executed = true;
                failure = FailureKind.SESSION_UNAVAILABLE;
            }
            change(State.FAULTED);
            throw error;
        }
    }

    void execute() throws IOException {
        synchronized(this) {
            if(!acquired || executed) throw new IllegalStateException("One-shot dry-run requires acquisition");
            executed=true;
        }
        long token=session.getGeneration();
        Throwable primary=null;
        try {
            Tbs5530ControlIo io=binding.bootstrapControlIo(epoch);
            session.transaction(token,()->{
                session.beginFirmwareTransition(token);
                change(State.LOADING);
                try {
                    for(Tbs5530FirmwarePlan.Step step:plan.getSteps()) {
                        preflight.requireCurrent();
                        session.requireUsable(token);
                        if(Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Cancelled");
                        if(step.getKind()==Tbs5530FirmwarePlan.Kind.DELAY) {
                            record("DELAY ms="+step.getDelayMillis()); delay.await(step.getDelayMillis());
                        } else {
                            byte[] bytes=step.getPayload();
                            synchronized(this){attempted++;}
                            record("OUT type=64 request=160 value="+step.getValue()+" index=0 length="+bytes.length
                                    +" timeout="+step.getTimeoutMillis());
                            int n=io.controlTransfer(step.getRequestType(),step.getRequest(),step.getValue(),step.getIndex(),
                                    bytes,0,bytes.length,step.getTimeoutMillis());
                            if(n!=bytes.length) throw new TransferException(n<0||n>bytes.length
                                    ?FailureKind.IO_FAILURE:FailureKind.SHORT_TRANSFER,"Incomplete firmware step");
                        }
                        preflight.requireCurrent();
                        session.requireUsable(token);
                    }
                    session.firmwareUploaded(token);
                    change(State.TRANSITIONING);
                } catch(IOException | RuntimeException error) {
                    FailureKind kind=error instanceof TransferException?((TransferException)error).getKind()
                            :error instanceof java.net.SocketTimeoutException?FailureKind.TIMEOUT
                            :error instanceof java.io.InterruptedIOException || error instanceof java.util.concurrent.CancellationException
                            ?FailureKind.CANCELLED:FailureKind.IO_FAILURE;
                    session.failed(token,error);
                    throw new TransferException(kind,"Dry-run stopped without corrective writes",error);
                }
            });
        } catch(IOException | RuntimeException error) {
            primary=error;
            synchronized(this) {
                failure=error instanceof TransferException?((TransferException)error).getKind():FailureKind.SESSION_UNAVAILABLE;
                state=attempted==0?State.FAULTED:State.UNCERTAIN;
            }
            throw error;
        } finally {
            binding.cancelBootstrap(epoch);
            IOException cleanup=null;
            try { session.drainCleanup(); } catch(IOException error) { cleanup=error; }
            if(cleanup==null) cleanup=session.getCleanupFailure();
            if(cleanup!=null) {
                change(State.UNCERTAIN);
                if(primary==null) throw cleanup;
                if(primary!=cleanup) primary.addSuppressed(cleanup);
            }
        }
    }
    private synchronized void record(String entry){trace.add(entry);}
    private synchronized void change(State next){state=next;trace.add(next.name());}
    public synchronized State state(){return state;}
    public synchronized FailureKind failure(){return failure;}
    public synchronized List<String> trace(){return Collections.unmodifiableList(new ArrayList<>(trace));}
}
