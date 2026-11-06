// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

/** SYNTHETIC_TEST_FIXTURE, Android test DEX only; all bytes and storage are fake. */
public final class Tbs5530Phase31PlanFixture {
    private Tbs5530Phase31PlanFixture() { }
    public static Tbs5530OperationalHandoff owner(android.content.Context context, Tbs5530AndroidRuntime runtime) throws Exception {
        Tbs5530UsbBinding.Device device = new Tbs5530UsbBinding.Device("SYNTHETIC_TEST_FIXTURE", 1, 0x734c, 0x5530);
        Tbs5530AndroidUsbFacade.Manager manager = new Tbs5530AndroidUsbFacade.Manager() {
            public java.util.List<Tbs5530UsbBinding.Device> devices() { return java.util.Collections.singletonList(device); }
            public boolean hasPermission(Tbs5530UsbBinding.Device ignored) { return true; }
            public void requestPermission(Tbs5530UsbBinding.Device ignored, android.app.PendingIntent intent) { throw new AssertionError("No physical permission"); }
            public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device ignored) {
                return new Tbs5530AndroidUsbFacade.RawConnection() {
                    public byte[] rawDescriptors() { return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,
                            9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0}; }
                    public int interfaceCount() { return 1; } public int interfaceId(int i) { return 0; } public int alternateSetting(int i) { return 0; }
                    public int endpointCount(int i) { return 2; } public int endpointAddress(int i, int e) { return e == 0 ? 0x82 : 0x81; }
                    public int endpointDirection(int i, int e) { return 128; } public int endpointType(int i, int e) { return 2; }
                    public int endpointMaxPacketSize(int i, int e) { return 512; }
                    public boolean claim(int i, boolean force) { if (force) throw new AssertionError(); return true; }
                    public boolean release(int i) { return true; } public void close() { }
                    public int controlTransfer(int t, int r, int v, int i, byte[] b, int o, int l, int timeout) { throw new AssertionError("UI lifecycle test must never transfer"); }
                };
            }
        };
        Tbs5530OperationalHandoff owner = new Tbs5530OperationalHandoff(context, runtime, manager, () -> { });
        owner.start(device);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (owner.pending() && System.nanoTime() < deadline) Thread.sleep(5);
        if (!owner.operationalOwner()) throw new AssertionError("Fake acquisition did not complete");
        return owner;
    }
    public static Tbs5530ControlProbe lockedProbe() throws Exception {
        Tbs5530Session session = new Tbs5530Session(); long token = session.requestPermission(); session.permissionResult(token, true);
        session.connected(token, new Tbs5530Session.Resources() { public void cancel() { } public void release() { } public void close() { } });
        session.interfaceClaimed(token); int[] address = {0}, register = {0}; long[] clock = {0};
        Tbs5530ControlProbe probe = new Tbs5530ControlProbe(session, token, (type, request, value, index, bytes, offset, length, timeout) -> {
            if (request == 0x90) { address[0] = bytes[1] & 255; register[0] = bytes[2] & 255; }
            else if (request == 0x91) bytes[0] = (byte)(address[0] == 0xcc
                    ? register[0] == 0xfb ? 1 : register[0] == 0xfd ? 0x97 : 0
                    : address[0] == 0xc0 ? register[0] == 0x7f ? 0xe0 : 0 : register[0] == 0x10 ? 0x26 : 0);
            return length;
        }, new Tbs5530ControlProbe.Authorization() {
            public void requireCurrent() { } public void consume(Tbs5530Frontend.Plan plan) { }
            public void audit(String b, int o, int t, int r, int l, int result) { }
            public boolean rfInConfirmed() { return true; }
        }, new Tbs5530Frontend.Clock() {
            public long elapsedRealtimeMillis() { return clock[0]; }
            public void delayMillis(long duration) { clock[0] += duration; }
        });
        probe.execute(probe.planProbe()); probe.execute(probe.planInitialize()); probe.readyForTune();
        probe.execute(probe.planTune(new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, 42)));
        probe.execute(probe.planPollStatus(1)); return probe;
    }
    public static Tbs5530Frontend.Plan tune(boolean auto) throws Exception {
        Tbs5530Session session = new Tbs5530Session();
        long token = session.requestPermission(); session.permissionResult(token, true);
        session.connected(token, new Tbs5530Session.Resources() {
            public void cancel() { } public void release() { } public void close() { }
        });
        session.interfaceClaimed(token);
        int[] register = {0}, address = {0}; long[] now = {0};
        Tbs5530Bridge bridge = new Tbs5530Bridge((type, request, value, index, bytes, offset, length, timeout) -> {
            if (request == 0x90) { address[0] = bytes[1] & 255; register[0] = bytes[2] & 255; }
            else if (request == 0x91) bytes[0] = (byte)(address[0] == 0xcc
                    ? register[0] == 0xfb ? 1 : register[0] == 0xfd ? 0x97 : 0
                    : address[0] == 0xc0 && register[0] == 0x7f ? 0xe0 : 0);
            return length;
        }, session, token);
        Tbs5530Frontend frontend = new Tbs5530Frontend(bridge, session, token, new Tbs5530Frontend.Clock() {
            public long elapsedRealtimeMillis() { return now[0]; }
            public void delayMillis(long duration) { now[0] += duration; }
        });
        frontend.execute(frontend.planProbe()); frontend.execute(frontend.planInitialize()); frontend.readyForTune();
        return frontend.planTune(new Tbs5530DvbT2TuneParameters(594_000_000L, 8_000_000, auto
                ? Tbs5530DvbT2TuneParameters.Selection.automatic() : Tbs5530DvbT2TuneParameters.Selection.manual(42)), auto);
    }
}
