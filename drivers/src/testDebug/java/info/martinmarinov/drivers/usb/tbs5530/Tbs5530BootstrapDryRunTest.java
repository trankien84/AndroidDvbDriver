// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class Tbs5530BootstrapDryRunTest {
    static byte[] image(int size){byte[] data=new byte[size];for(int i=0;i<size;i++)data[i]=(byte)(i*37);return data;}
    static Tbs5530FirmwarePlan plan(byte[] bytes)throws Exception{
        return Tbs5530FirmwarePlan.create(bytes,new Tbs5530FirmwarePlan.Policy(MessageDigest.getInstance("SHA-256").digest(bytes),
                Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,0x2000)),Collections.emptyList(),0,0x2000));
    }
    private static final class Rig implements Tbs5530UsbBinding.Facade,Tbs5530LifecycleCoordinator.Connection {
        final Tbs5530Session session=new Tbs5530Session();
        final Tbs5530UsbBinding.Device device=new Tbs5530UsbBinding.Device("fake",1,0x734c,0x5530);
        final List<Runnable> timers=new ArrayList<>();final List<Throwable> errors=new ArrayList<>();
        final Tbs5530UsbBinding binding;
        final Tbs5530BootstrapDryRun runner;
        final Tbs5530FirmwarePlan plan;
        final Tbs5530Fx2ProtocolPlan protocol;
        Tbs5530UsbBinding.Events events;boolean permitted=true,present=true,closeFail,delayFail,adbOnline=true;
        int opens,closes,calls,failAt=-1;String mode="short";Runnable during=()->{};
        Rig(int size)throws Exception{ this(size, Tbs5530Fx2ProtocolPlan.Id.TBS_SOURCE_DUAL_RESET); }
        Rig(int size,Tbs5530Fx2ProtocolPlan.Id id)throws Exception{
            plan=plan(image(size));
            protocol=Tbs5530Fx2ProtocolPlan.describe(plan,id.name());
            binding=new Tbs5530UsbBinding(session,this,Runnable::run,(r,d)->{timers.add(r);return ()->{};},errors::add,30000,10000);
            runner=new Tbs5530BootstrapDryRun(session,binding,Tbs5530Fx2FingerprintTest.assess(Tbs5530Fx2FingerprintTest.bootstrap()),
                    protocol,millis->{assertEquals(100,millis);if(delayFail)throw new IOException("delay");},
                    ()->{if(!adbOnline)throw new IOException("Wireless ADB unavailable");});
        }
        public List<Tbs5530UsbBinding.Device> devices(){return present?Collections.singletonList(device):Collections.emptyList();}
        public boolean hasPermission(Tbs5530UsbBinding.Device d){return permitted;}
        public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device d,String c,Tbs5530UsbBinding.PermissionCallback cb){cb.result(c,d,false);return ()->{};}
        public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events e){events=e;return ()->{};}
        public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device d){throw new AssertionError("Operational open is forbidden");}
        public Tbs5530LifecycleCoordinator.Connection openBootstrap(Tbs5530UsbBinding.Device d){assertEquals(device,d);opens++;return this;}
        public void claim(Tbs5530LifecycleCoordinator.Claim c){throw new AssertionError("EP0 must not claim");}
        public void cancel(){throw new AssertionError("No streaming cancel");}
        public void release(){throw new AssertionError("No interface release");}
        public void close()throws IOException{closes++;if(closeFail)throw new IOException("close");}
        public int controlTransfer(int type,int request,int value,int index,byte[] data,int offset,int length,int timeout)throws IOException{
            assertEquals(0,closes);int ordinal=calls++;
            if(ordinal>=protocol.getSteps().size()-1)throw new AssertionError("Unexpected call");
            Tbs5530FirmwarePlan.Step expected=protocol.getSteps().get(ordinal);
            assertEquals(expected.getRequestType(),type);assertEquals(expected.getRequest(),request);
            assertEquals(expected.getValue(),value);assertEquals(0,index);assertEquals(0,offset);assertEquals(2000,timeout);
            assertEquals(expected.getPayload().length,length);assertArrayEquals(expected.getPayload(),data);
            during.run();
            if(ordinal==failAt){switch(mode){case "negative":return -110;case "timeout":throw new SocketTimeoutException("explicit");
                case "io":throw new IOException("io");case "cancel":throw new java.io.InterruptedIOException("cancel");default:return length-1;}}
            return length;
        }
        void acquire()throws IOException{runner.acquire(device);}
        void fails()throws IOException{try{runner.execute();fail("Expected stop");}catch(IOException expected){}}
    }
    @Test public void exactBytesOrderTimeoutsAndFinalPartialChunk()throws Exception{
        for(int size:new int[]{1,63,64,65,128,8192}){
            Rig r=new Rig(size);assertEquals(0,r.opens);assertEquals(0,r.calls);r.acquire();
            assertEquals(1,r.opens);assertFalse(r.session.isInterfaceClaimed());r.runner.execute();
            assertEquals((size+63)/64+4,r.calls);assertEquals(1,r.closes);
            assertEquals(Tbs5530BootstrapDryRun.State.TRANSITIONING,r.runner.state());
            assertFalse(r.session.isFirmwareUploaded());assertFalse(r.session.isStreaming());
            assertTrue(r.runner.trace().contains("DELAY ms=100"));
            try{r.runner.execute();fail();}catch(IllegalStateException expected){}
        }
    }
    @Test public void stopAtEveryWriteBoundaryNeverReleaseCpuToRecover()throws Exception{
        for(int step=0;step<6;step++){
            Rig r=new Rig(65);r.failAt=step;r.acquire();r.fails();
            assertEquals(step+1,r.calls);assertEquals(1,r.closes);
            assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
            assertEquals(Tbs5530ControlIo.FailureKind.SHORT_TRANSFER,r.runner.failure());
        }
    }
    @Test public void negativeIsIoFailureNotTimeoutAndExplicitErrorsPreserveKinds()throws Exception{
        String[] modes={"negative","timeout","io","cancel"};
        Tbs5530ControlIo.FailureKind[] kinds={Tbs5530ControlIo.FailureKind.IO_FAILURE,Tbs5530ControlIo.FailureKind.TIMEOUT,
                Tbs5530ControlIo.FailureKind.IO_FAILURE,Tbs5530ControlIo.FailureKind.CANCELLED};
        for(int i=0;i<modes.length;i++){Rig r=new Rig(64);r.failAt=1;r.mode=modes[i];r.acquire();r.fails();
            assertEquals(2,r.calls);assertEquals(kinds[i],r.runner.failure());assertEquals(1,r.closes);}
    }
    @Test public void permissionDeniedNoOpenAndNoWrites()throws Exception{
        Rig r=new Rig(64);r.permitted=false;r.acquire();r.fails();assertEquals(0,r.opens);assertEquals(0,r.calls);
        assertEquals(Tbs5530BootstrapDryRun.State.FAULTED,r.runner.state());
    }
    @Test public void attachmentVidPidAndPermissionChangesBlockAllFurtherWrites()throws Exception{
        for(int cause=0;cause<4;cause++){
            Rig r=new Rig(65);r.acquire();final int c=cause;
            r.during=()->{if(c==0)r.permitted=false;else if(c==1)r.present=false;
                else if(c==2)r.events.attached(r.device);else r.binding.cancel();};
            r.fails();assertEquals(1,r.calls);assertEquals(1,r.closes);
            assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
        }
        Rig r=new Rig(64);
        try{r.runner.acquire(new Tbs5530UsbBinding.Device("fake",1,1,2));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,r.calls);assertEquals(0,r.opens);
    }
    @Test public void noSecondConnectionAndOperationalCapabilityStillDenied()throws Exception{
        Rig r=new Rig(64);r.acquire();
        try{r.binding.start(r.device);fail();}catch(IllegalStateException expected){}
        try{r.binding.controlIo();fail();}catch(IOException expected){}
        try{r.binding.streamEndpoint();fail();}catch(IOException expected){}
        assertEquals(1,r.opens);assertEquals(0,r.calls);r.runner.execute();
    }
    @Test public void staleGenerationAndReusedOsIdentityDoNotReuseCapability()throws Exception{
        Rig r=new Rig(64);r.acquire();r.events.attached(r.device);r.fails();
        assertEquals(0,r.calls);assertEquals(1,r.closes);
        assertEquals(Tbs5530BootstrapDryRun.State.FAULTED,r.runner.state());
    }
    @Test public void oldRunnerCannotWriteOrCancelReusedIdentityNewEpoch()throws Exception{
        Rig r=new Rig(64);r.acquire();Tbs5530ControlIo old=r.binding.bootstrapControlIo();
        r.binding.cancel();r.binding.startBootstrap(r.device);assertEquals(2,r.opens);
        r.fails();assertEquals(0,r.calls);assertEquals(1,r.closes);
        assertEquals(Tbs5530Session.State.CONNECTED,r.session.getState());
        try{old.controlTransfer(0x40,0xa0,0,0,new byte[]{0},0,1,2000);fail();}catch(IOException expected){}
        assertEquals(0,r.calls);r.binding.cancel();assertEquals(2,r.closes);
    }
    @Test public void cancelledDeadlineDoesNotInvalidateLaterStage()throws Exception{
        Rig r=new Rig(64);r.acquire();for(Runnable timer:r.timers)timer.run();
        assertEquals(Tbs5530Session.State.CONNECTED,r.session.getState());r.runner.execute();
    }
    @Test public void cleanupFailureIsNotSuccessOrReopenPermission()throws Exception{
        Rig r=new Rig(64);r.closeFail=true;r.acquire();r.fails();assertEquals(1,r.closes);
        assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
        assertNotNull(r.session.getCleanupFailure());
        try{r.binding.startBootstrap(r.device);fail();}catch(IllegalStateException expected){}
    }
    @Test public void delayFailureCannotTriggerCorrectiveReset()throws Exception{
        Rig r=new Rig(64);r.delayFail=true;r.acquire();r.fails();assertEquals(5,r.calls);assertEquals(1,r.closes);
        assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
    }
    @Test public void hardwareValidationRejectsEmptyWrongFormatSizeSourceAndDigest()throws Exception{
        assertFalse(Tbs5530FirmwareValidation.hardwareUploadAllowed());
        for(int size:new int[]{0,1,8191,8192,8193}){
            try{Tbs5530FirmwareValidation.validate(image(size),Tbs5530FirmwareValidation.SOURCE,
                    Tbs5530FirmwareValidation.ENTRY,Tbs5530FirmwareValidation.Format.RAW_LINEAR);fail();}
            catch(IllegalArgumentException expected){}
        }
        for(Tbs5530FirmwareValidation.Format format:Tbs5530FirmwareValidation.Format.values()){
            try{Tbs5530FirmwareValidation.validate(image(8192),"untrusted",Tbs5530FirmwareValidation.ENTRY,format);fail();}
            catch(IllegalArgumentException expected){}
        }
        try{Tbs5530FirmwareValidation.validate(null,Tbs5530FirmwareValidation.SOURCE,Tbs5530FirmwareValidation.ENTRY,
                Tbs5530FirmwareValidation.Format.RAW_LINEAR);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void bothPlansStopAtEveryRealImageBoundaryWithoutRetry()throws Exception{
        for(Tbs5530Fx2ProtocolPlan.Id id:Tbs5530Fx2ProtocolPlan.Id.values()){
            int writes=id==Tbs5530Fx2ProtocolPlan.Id.TBS_SOURCE_DUAL_RESET?132:130;
            for(int step=0;step<writes;step++){
                Rig r=new Rig(8192,id);r.failAt=step;r.acquire();r.fails();
                assertEquals(step+1,r.calls);assertEquals(1,r.closes);
                assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
                try{r.runner.execute();fail();}catch(IllegalStateException expected){}
                assertEquals(step+1,r.calls);
            }
        }
    }
    @Test public void documentedPlanFinishesOnlyTransitioningWithOrWithoutReattachment()throws Exception{
        Rig r=new Rig(8192,Tbs5530Fx2ProtocolPlan.Id.DOCUMENTED_FX2_E600_ONLY);
        r.acquire();r.runner.execute();assertEquals(130,r.calls);
        assertEquals(Tbs5530BootstrapDryRun.State.TRANSITIONING,r.runner.state());
        assertEquals(Tbs5530Session.State.CLOSED,r.session.getState());
        assertFalse(r.session.isStreaming());
        r.events.attached(r.device);
        assertEquals(Tbs5530BootstrapDryRun.State.TRANSITIONING,r.runner.state());
        assertNotEquals(Tbs5530Session.State.READY,r.session.getState());
        assertEquals(130,r.calls);
    }
    @Test public void wirelessLossBeforeAcquisitionConsumesAttemptWithoutOpening()throws Exception{
        Rig r=new Rig(64,Tbs5530Fx2ProtocolPlan.Id.DOCUMENTED_FX2_E600_ONLY);r.adbOnline=false;
        try{r.acquire();fail();}catch(IOException expected){}
        r.adbOnline=true;
        try{r.acquire();fail();}catch(IllegalStateException expected){}
        assertEquals(0,r.opens);assertEquals(0,r.calls);
    }
    @Test public void wirelessLossAfterSynchronousWriteDoesNotSendCorrectiveWrites()throws Exception{
        Rig r=new Rig(8192,Tbs5530Fx2ProtocolPlan.Id.DOCUMENTED_FX2_E600_ONLY);r.acquire();
        r.during=()->r.adbOnline=false;r.fails();
        assertEquals(1,r.calls);assertEquals(1,r.closes);
        assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
        r.adbOnline=true;
        try{r.runner.execute();fail();}catch(IllegalStateException expected){}
        assertEquals(1,r.calls);
    }
    @Test(timeout=5000) public void inFlightInvalidationDefersCloseAndStopsNextWrite()throws Exception{
        Rig r=new Rig(65);r.acquire();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        r.during=()->{entered.countDown();try{assertTrue(release.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}};
        Thread t=new Thread(()->{try{r.runner.execute();}catch(Throwable e){error.set(e);}});t.start();
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));r.binding.cancel();assertTrue(r.session.isCleanupPending());
            assertEquals(0,r.closes);assertEquals(1,r.calls);}
        finally{release.countDown();t.join(2000);}
        assertFalse(t.isAlive());assertTrue(error.get() instanceof IOException);assertEquals(1,r.closes);assertEquals(1,r.calls);
        assertFalse(r.session.isCleanupPending());assertEquals(Tbs5530BootstrapDryRun.State.UNCERTAIN,r.runner.state());
    }
}
