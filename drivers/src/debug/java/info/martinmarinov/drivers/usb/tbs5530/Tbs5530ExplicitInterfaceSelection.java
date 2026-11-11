// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.Objects;

/** Debug-only, one-shot Android API observation. Never asserts a physical USB transaction. */
public final class Tbs5530ExplicitInterfaceSelection {
    public static final String OPTION = "EXPLICIT_SET_INTERFACE_ALT0";
    interface Clock { long nanos(); }
    interface Read { int read() throws IOException; }
    interface Port {
        void requireCurrent() throws IOException;
        Observation observe() throws IOException;
        long[] receiveState();
        boolean setInterfaceAlt0() throws IOException;
    }
    public static final class Observation {
        public final String descriptorHash;
        public final int interfaceId, alternateSetting, streamPacketSize, negotiatedUsbSpeed;
        Observation(byte[] descriptors, int speed, int id, int alt, int[] addresses,
                    int[] directions, int[] types, int[] packetSizes) throws IOException {
            Tbs5530OperationalProfile profile = Tbs5530OperationalProfile.observe(descriptors);
            profile.requireOperational();
            if (profile.streamPacketSize != 512 || profile.controlPacketSize != 512 || speed != 3
                    || id != 0 || alt != 0 || addresses == null || addresses.length != 2
                    || directions == null || directions.length != 2 || types == null || types.length != 2
                    || packetSizes == null || packetSizes.length != 2) {
                throw new IOException("Exact High-Speed config1/interface0/alt0 operational profile required");
            }
            boolean stream = false, control = false;
            for (int i = 0; i < 2; i++) {
                if (directions[i] != 128 || types[i] != 2 || packetSizes[i] != 512)
                    throw new IOException("Raw and Android endpoint metadata differ");
                if (addresses[i] == 0x82 && !stream) stream = true;
                else if (addresses[i] == 0x81 && !control) control = true;
                else throw new IOException("Exact operational endpoints required");
            }
            descriptorHash = profile.descriptorHash; interfaceId = id; alternateSetting = alt;
            streamPacketSize = profile.streamPacketSize; negotiatedUsbSpeed = speed;
        }
    }
    public static final class Snapshot {
        public final String state;
        public final boolean apiCalled;
        public final Boolean apiResult;
        public final long startedNanos, returnedNanos;
        public final Observation before, after;
        public final long outstandingBefore, outstandingAfter;
        private Snapshot(Tbs5530ExplicitInterfaceSelection value) {
            state = value.state; apiCalled = value.apiCalled; apiResult = value.apiResult;
            startedNanos = value.startedNanos; returnedNanos = value.returnedNanos;
            before = value.before; after = value.after;
            outstandingBefore = value.outstandingBefore; outstandingAfter = value.outstandingAfter;
        }
    }
    private final boolean enabled;
    private final Port port;
    private final Clock clock;
    private final Thread worker = Thread.currentThread();
    private boolean used, success, apiCalled;
    private Boolean apiResult;
    private String state;
    private long startedNanos = -1, returnedNanos = -1, outstandingBefore = -1, outstandingAfter = -1;
    private Observation before, after;

    Tbs5530ExplicitInterfaceSelection(boolean enabled, Port port, Clock clock) {
        this.enabled = enabled; this.port = Objects.requireNonNull(port); this.clock = Objects.requireNonNull(clock);
        state = enabled ? "NOT_STARTED" : "OFF";
    }
    int read(Read read) throws IOException {
        if (enabled) selectOnce();
        return read.read();
    }
    private void selectOnce() throws IOException {
        if (Thread.currentThread() != worker) throw new IOException("Interface selection worker changed");
        if (used) {
            if (!success) throw new IOException("Interface selection failed; no retry or rearm");
            try { port.requireCurrent(); }
            catch (IOException | RuntimeException | Error error) {
                success = false; state = "OWNER_REVOKED_AFTER_API"; throw error;
            }
            return;
        }
        used = true; state = "REFUSED_BEFORE_API";
        try {
            port.requireCurrent();
            before = port.observe();
            outstandingBefore = requireIdle(port.receiveState());
            port.requireCurrent();
            startedNanos = clock.nanos();
            if (startedNanos < 0) throw new IOException("Invalid interface selection clock");
            state = "API_EXCEPTION"; apiCalled = true;
            try { apiResult = port.setInterfaceAlt0(); }
            finally { returnedNanos = clock.nanos(); }
            state = Boolean.TRUE.equals(apiResult) ? "POST_API_VALIDATION_FAILED" : "API_FALSE";
            if (!Boolean.TRUE.equals(apiResult)) throw new IOException("setInterface returned false; no Bulk or retry");
            if (returnedNanos < startedNanos || returnedNanos - startedNanos >= 2_000_000_000L)
                throw new IOException("Interface selection exceeded its observation budget");
            port.requireCurrent();
            after = port.observe();
            outstandingAfter = requireIdle(port.receiveState());
            if (!before.descriptorHash.equals(after.descriptorHash)) throw new IOException("Operational descriptor changed after setInterface");
            port.requireCurrent();
            state = "API_TRUE_PROFILE_UNCHANGED"; success = true;
        } catch (IOException | RuntimeException | Error error) {
            success = false;
            throw error;
        }
    }
    private static long requireIdle(long[] receive) throws IOException {
        if (receive == null || receive.length < 7 || receive[0] != 0 || receive[1] != 0
                || receive[2] != 0 || receive[3] != 0 || receive[6] != 0)
            throw new IOException("Interface selection requires no attempted, submitted or outstanding URB");
        return receive[3];
    }
    Snapshot snapshot() { return new Snapshot(this); }
}
