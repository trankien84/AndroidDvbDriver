// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.*;
import static info.martinmarinov.drivers.usb.tbs5530.Tbs5530ControlIo.TransferException;

public class Tbs5530SessionTest {
    private static class Owned implements Tbs5530Session.Resources {
        final List<String> calls = new ArrayList<>();
        String fail;
        public void cancel() throws IOException { record("cancel"); }
        public void release() throws IOException { record("release"); }
        public void close() throws IOException { record("close"); }
        private void record(String name) throws IOException {
            calls.add(name);
            if(name.equals(fail)) throw new IOException(name);
        }
    }
    private static long connect(Tbs5530Session s, Owned owned) {
        long token=s.requestPermission();assertTrue(s.permissionResult(token,true));
        assertTrue(s.connected(token,owned));return token;
    }

    @Test public void readinessMilestonesNeverAdvanceEachOtherImplicitly() throws Exception {
        Tbs5530Session s = new Tbs5530Session();
        assertEquals(Tbs5530Session.State.DETECTED, s.getState());
        long token = s.requestPermission(); s.permissionResult(token, true);
        assertEquals(Tbs5530Session.State.PERMISSION_GRANTED, s.getState());
        assertFalse(s.isInterfaceClaimed()); assertFalse(s.isFirmwareUploaded());
        assertThrows(TransferException.class, () -> s.ready(token));
        assertTrue(s.connected(token, new Owned())); s.interfaceClaimed(token);
        assertEquals(Tbs5530Session.State.CONNECTED, s.getState());
        assertThrows(IllegalStateException.class, () -> s.frontendInitialized(token));
        s.beginFirmwareTransition(token); s.firmwareUploaded(token);
        assertTrue(s.isFirmwareUploaded());
        assertEquals(Tbs5530Session.State.FIRMWARE_TRANSITION, s.getState());
        assertThrows(IllegalStateException.class, () -> s.streamingStarted(token));
        s.ready(token);
        assertFalse(s.isFrontendInitialized()); assertFalse(s.isFrontendLocked()); assertFalse(s.isStreaming());
        s.frontendInitialized(token); assertFalse(s.isFrontendLocked());
        s.frontendLock(token, true); assertFalse(s.isStreaming());
        s.streamingStarted(token); assertTrue(s.isStreaming());
        s.frontendLock(token, false); assertTrue(s.isStreaming());
        s.close(); assertFalse(s.isFrontendInitialized()); assertFalse(s.isInterfaceClaimed());
    }

    @Test public void permissionDenialNeverAcquiresOrCallsTransport() throws Exception {
        Tbs5530Session s=new Tbs5530Session();ScriptedFakeControlIo io=new ScriptedFakeControlIo();
        long token=s.requestPermission();assertEquals(Tbs5530Session.State.WAIT_PERMISSION,s.getState());
        assertTrue(s.permissionResult(token,false));assertEquals(Tbs5530Session.State.CLOSED,s.getState());
        assertThrows(TransferException.class,()->new Tbs5530Bridge(io,s,token).selectTerrestrialStream());
        assertTrue(io.calls().isEmpty());s.close();
    }

    @Test public void streamingAndFrontendLockAreIndependent() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
        assertThrows(IllegalStateException.class,()->s.streamingStarted(token));
        s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);
        assertTrue(s.isStreaming());assertFalse(s.isFrontendLocked());
        assertThrows(IllegalStateException.class,()->s.frontendLock(token,true));
        s.frontendInitialized(token);s.frontendLock(token,true);assertTrue(s.isFrontendLocked());
        s.frontendLock(token,false);assertTrue(s.isStreaming());s.close();
        assertFalse(s.isStreaming());assertFalse(s.isFrontendLocked());
        assertEquals(Arrays.asList("cancel","release","close"),owned.calls);
    }

    @Test public void detachRejectsStaleCallbacksAndOldBridgeAfterReconnect() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned first=new Owned();long old=connect(s,first);
        s.interfaceClaimed(old);s.ready(old);s.streamingStarted(old);
        ScriptedFakeControlIo io=new ScriptedFakeControlIo();Tbs5530Bridge oldBridge=new Tbs5530Bridge(io,s,old);
        assertTrue(s.detached(old));assertEquals(Tbs5530Session.State.DISCONNECTED,s.getState());
        Owned second=new Owned();long current=connect(s,second);assertTrue(current>old);
        assertFalse(s.permissionResult(old,true));assertFalse(s.connected(old,new Owned()));
        assertFalse(s.failed(old,new IOException()));assertFalse(s.detached(old));
        assertThrows(TransferException.class,oldBridge::selectTerrestrialStream);assertTrue(io.calls().isEmpty());
        assertEquals(Tbs5530Session.State.CONNECTED,s.getState());s.close();
        assertEquals(Arrays.asList("cancel","release","close"),first.calls);
        assertEquals(Collections.singletonList("close"),second.calls);
    }

    @Test public void cancelWhileWaitingMakesLatePermissionHarmless() throws Exception {
        Tbs5530Session s=new Tbs5530Session();long token=s.requestPermission();assertTrue(s.cancel(token));
        assertFalse(s.permissionResult(token,true));assertFalse(s.connected(token,new Owned()));
        assertEquals(Tbs5530Session.State.CLOSED,s.getState());assertFalse(s.cancel(token));
    }

    @Test public void failedSessionCannotBecomeReadyOrReplayBeforeClose() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
        IOException original=new IOException("broken");assertTrue(s.failed(token,original));
        assertSame(original,s.getFailure());assertFalse(s.failed(token,new IOException("later")));
        assertThrows(TransferException.class,()->s.ready(token));assertThrows(IllegalStateException.class,s::requestPermission);
        s.close();s.close();assertEquals(Collections.singletonList("close"),owned.calls);
    }

    @Test public void partialOwnershipCleansOnlyAcquiredResources() throws Exception {
        for(int level=0;level<4;level++) {
            Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token;
            if(level==0){token=s.requestPermission();s.permissionResult(token,true);}
            else {token=connect(s,owned);if(level>=2)s.interfaceClaimed(token);if(level>=3){s.ready(token);s.streamingStarted(token);}}
            s.close();s.close();
            List<String> expected=level==0?Collections.emptyList():level==1?Collections.singletonList("close"):
                    level==2?Arrays.asList("release","close"):Arrays.asList("cancel","release","close");
            assertEquals(expected,owned.calls);
        }
    }

    @Test public void cleanupContinuesAfterEachFailureAndIsExactlyOnce() throws Exception {
        for(String failing:Arrays.asList("cancel","release","close")) {
            Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();owned.fail=failing;long token=connect(s,owned);
            s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);
            IOException error=assertThrows(IOException.class,s::close);assertEquals(failing,error.getMessage());
            s.close();assertEquals(Arrays.asList("cancel","release","close"),owned.calls);
            assertEquals(Tbs5530Session.State.CLOSED,s.getState());
        }
    }

    @Test public void cleanupDoesNotHoldSessionMonitorWhileJoiningWorker() throws Exception {
        Tbs5530Session s=new Tbs5530Session();AtomicReference<Throwable> failure=new AtomicReference<>();
        Owned owned=new Owned(){@Override public void cancel() throws IOException {
            super.cancel();Thread worker=new Thread(()->{try{assertEquals(Tbs5530Session.State.CLOSED,s.getState());}
                catch(Throwable e){failure.set(e);}});worker.start();
            try{worker.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
            assertFalse("Session monitor deadlocked cancellation",worker.isAlive());
        }};
        long token=connect(s,owned);s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);s.close();assertNull(failure.get());
    }

    @Test public void staleResourceCallbackRetainsOwnershipWithCaller() throws Exception {
        Tbs5530Session s=new Tbs5530Session();long token=s.requestPermission();s.cancel(token);Owned late=new Owned();
        assertFalse(s.connected(token,late));assertTrue(late.calls.isEmpty());
        // A rejected callback never transfers ownership; the callback owner must dispose its result.
        late.close();assertEquals(Collections.singletonList("close"),late.calls);
    }

    @Test public void firmwareFailureAtEveryPlannedStepNeverReachesReady() throws Exception {
        byte[] image=new byte[65];for(int i=0;i<image.length;i++)image[i]=(byte)i;
        Tbs5530FirmwarePlan plan=Tbs5530FirmwarePlan.create(image,new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(image),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,256)),Collections.emptyList(),0,256));
        List<Tbs5530FirmwarePlan.Step> steps=plan.getSteps();
        for(int failAt=0;failAt<steps.size();failAt++) {
            Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
            s.beginFirmwareTransition(token);ScriptedFakeControlIo io=new ScriptedFakeControlIo();
            int expectedCalls=0;
            for(int i=0;i<=failAt;i++) {
                Tbs5530FirmwarePlan.Step step=steps.get(i);
                if(step.getKind()==Tbs5530FirmwarePlan.Kind.DATA){byte[] payload=step.getPayload();
                    io.expect(step.getRequestType(),step.getRequest(),step.getValue(),step.getIndex(),payload,0,payload.length,
                            step.getTimeoutMillis(),null,payload.length,i==failAt?new IOException("injected"):null);expectedCalls++;}
            }
            int completed=0;
            try {
                for(int i=0;i<steps.size();i++) {
                    Tbs5530FirmwarePlan.Step step=steps.get(i);
                    if(step.getKind()==Tbs5530FirmwarePlan.Kind.DELAY){assertEquals(100,step.getDelayMillis());if(i==failAt)throw new IOException("cancel settle");}
                    else {byte[] payload=step.getPayload();int result=io.controlTransfer(step.getRequestType(),step.getRequest(),step.getValue(),
                            step.getIndex(),payload,0,payload.length,step.getTimeoutMillis());assertEquals(payload.length,result);}
                    completed++;
                }
                fail("Injected firmware failure was not propagated");
            }catch(IOException e){assertTrue(s.failed(token,e));}
            assertEquals(failAt,completed);assertEquals(expectedCalls,io.calls().size());io.assertComplete();
            assertEquals(Tbs5530Session.State.UNCERTAIN,s.getState());assertThrows(TransferException.class,()->s.ready(token));
            assertThrows(TransferException.class,()->new Tbs5530Bridge(io,s,token).selectTerrestrialStream());
            assertEquals(expectedCalls,io.calls().size());s.close();assertEquals(Collections.singletonList("close"),owned.calls);
        }
    }

    @Test public void successfulSyntheticFirmwarePlanNeedsExplicitReadyEvent() throws Exception {
        byte[] image={42};
        Tbs5530FirmwarePlan plan=Tbs5530FirmwarePlan.create(image,new Tbs5530FirmwarePlan.Policy(
                MessageDigest.getInstance("SHA-256").digest(image),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,64)),null,0,64));
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
        s.beginFirmwareTransition(token);ScriptedFakeControlIo fake=new ScriptedFakeControlIo();
        long elapsed=0;int transfers=0;
        for(Tbs5530FirmwarePlan.Step step:plan.getSteps()) {
            if(step.getKind()==Tbs5530FirmwarePlan.Kind.DELAY) {elapsed+=step.getDelayMillis();continue;}
            byte[] payload=step.getPayload();
            fake.expect(step.getRequestType(),step.getRequest(),step.getValue(),step.getIndex(),payload,0,payload.length,
                    step.getTimeoutMillis(),null,payload.length,null);
            assertEquals(payload.length,fake.controlTransfer(step.getRequestType(),step.getRequest(),step.getValue(),step.getIndex(),
                    payload,0,payload.length,step.getTimeoutMillis()));transfers++;
        }
        fake.assertComplete();assertEquals(5,transfers);assertEquals(100,elapsed);
        assertEquals(Tbs5530Session.State.FIRMWARE_TRANSITION,s.getState());assertFalse(s.isStreaming());
        s.ready(token);assertEquals(Tbs5530Session.State.READY,s.getState());s.close();
    }

    @Test public void bridgeTimeoutDuringFirmwareMakesSessionUncertainWithoutRecoveryWrites() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
        s.beginFirmwareTransition(token);ScriptedFakeControlIo io=new ScriptedFakeControlIo();
        io.expect(0x40,0x80,0,0,new byte[]{2,0,7},0,3,2000,null,0,new java.net.SocketTimeoutException("deadline"));
        Tbs5530Bridge bridge=new Tbs5530Bridge(io,s,token);
        assertThrows(TransferException.class,()->bridge.i2cWrite(0,0,new byte[]{7},0,1));
        assertEquals(Tbs5530Session.State.UNCERTAIN,s.getState());
        for(int i=0;i<10;i++)assertThrows(TransferException.class,bridge::selectTerrestrialStream);
        s.close();assertEquals(1,io.calls().size());assertEquals(Collections.singletonList("close"),owned.calls);
    }

    @Test public void cleanupAggregatesErrorsAndBlocksReopenUntilFinished() throws Exception {
        Tbs5530Session s=new Tbs5530Session();
        Owned owned=new Owned(){
            @Override public void cancel() throws IOException {calls.add("cancel");
                assertThrows(IllegalStateException.class,s::requestPermission);throw new IOException("cancel");}
            @Override public void release() throws IOException {calls.add("release");throw new IOException("release");}
            @Override public void close() throws IOException {calls.add("close");throw new IOException("close");}
        };
        long token=connect(s,owned);s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);
        IOException error=assertThrows(IOException.class,s::close);
        assertEquals("cancel",error.getMessage());assertEquals(2,error.getSuppressed().length);
        assertEquals(Arrays.asList("cancel","release","close"),owned.calls);s.close();
        assertTrue(s.requestPermission()>token);
    }

    @Test public void transferFailureStillCancelsOwnedStreamingResources() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned();long token=connect(s,owned);
        s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);s.frontendInitialized(token);s.frontendLock(token,true);
        IOException cause=new IOException("transport failed");assertTrue(s.failed(token,cause));
        assertFalse(s.isStreaming());assertFalse(s.isFrontendLocked());
        s.close();s.close();assertEquals(Arrays.asList("cancel","release","close"),owned.calls);
    }

    @Test public void runtimeCleanupFailureStillReleasesAndCloses() throws Exception {
        Tbs5530Session s=new Tbs5530Session();Owned owned=new Owned(){
            @Override public void cancel(){calls.add("cancel");throw new IllegalStateException("cancel bug");}
        };
        long token=connect(s,owned);s.interfaceClaimed(token);s.ready(token);s.streamingStarted(token);
        IOException problem=assertThrows(IOException.class,s::close);
        assertTrue(problem.getCause() instanceof IllegalStateException);
        assertEquals(Arrays.asList("cancel","release","close"),owned.calls);s.close();
    }

    @Test public void permissionWaitTimeoutAndDetachedBeforeConnectionDoNotAcquireResources() throws Exception {
        Tbs5530Session s=new Tbs5530Session();long token=s.requestPermission();
        assertTrue(s.cancel(token));assertFalse(s.permissionResult(token,true));
        long next=s.requestPermission();assertTrue(s.detached(next));
        assertFalse(s.permissionResult(next,true));assertEquals(Tbs5530Session.State.DISCONNECTED,s.getState());
        assertThrows(TransferException.class,()->s.interfaceClaimed(next));
    }
}
