// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Fake USB only; no physical UsbManager lookup, permission request or transfer. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530BootstrapIsolationTest {
    @Test public void productionFacadeCannotOpenBootstrapEvenWithInjectedManager()throws Exception{
        AtomicInteger opens=new AtomicInteger();
        Tbs5530AndroidUsbFacade.Manager manager=new Tbs5530AndroidUsbFacade.Manager(){
            public List<Tbs5530UsbBinding.Device> devices(){throw new AssertionError("No enumeration");}
            public boolean hasPermission(Tbs5530UsbBinding.Device d){throw new AssertionError("No permission lookup");}
            public void requestPermission(Tbs5530UsbBinding.Device d,android.app.PendingIntent p){throw new AssertionError("No prompt");}
            public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device d){opens.incrementAndGet();throw new AssertionError("No hardware open");}
        };
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Tbs5530AndroidUsbFacade facade=new Tbs5530AndroidUsbFacade(context,manager);
        try{facade.openBootstrap(new Tbs5530UsbBinding.Device("fake",1,0x734c,0x5530));fail();}catch(IOException expected){}
        assertEquals(0,opens.get());assertFalse(Tbs5530FirmwareValidation.hardwareUploadAllowed());
    }

    @Test public void unclaimedEp0UsesSingleWorkerAndOwnerAndCannotProvideStream()throws Exception{
        Tbs5530AndroidRuntime runtime=new Tbs5530AndroidRuntime();Tbs5530Session session=new Tbs5530Session();
        Tbs5530UsbBinding.Device device=new Tbs5530UsbBinding.Device("fake",1,0x734c,0x5530);
        AtomicInteger opens=new AtomicInteger(),writes=new AtomicInteger(),closes=new AtomicInteger();
        CountDownLatch opened=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Tbs5530UsbBinding.Facade facade=new Tbs5530UsbBinding.Facade(){
            public List<Tbs5530UsbBinding.Device> devices(){return Collections.singletonList(device);}
            public boolean hasPermission(Tbs5530UsbBinding.Device d){return true;}
            public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device d,String c,Tbs5530UsbBinding.PermissionCallback cb){throw new AssertionError("Already granted");}
            public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events e){return ()->{};}
            public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device d){throw new AssertionError("No operational open");}
            public Tbs5530LifecycleCoordinator.Connection openBootstrap(Tbs5530UsbBinding.Device d){
                assertEquals("tbs5530-usb-worker",Thread.currentThread().getName());opens.incrementAndGet();opened.countDown();
                return new Tbs5530LifecycleCoordinator.Connection(){
                    public void claim(Tbs5530LifecycleCoordinator.Claim c){throw new AssertionError("No claim");}
                    public void cancel(){throw new AssertionError("No stream cancel");}
                    public void release(){throw new AssertionError("No release");}
                    public void close(){assertEquals("tbs5530-usb-worker",Thread.currentThread().getName());closes.incrementAndGet();}
                    public int controlTransfer(int t,int r,int v,int i,byte[] b,int o,int l,int timeout){
                        assertEquals("tbs5530-usb-worker",Thread.currentThread().getName());assertEquals(0x40,t);
                        assertEquals(0xa0,r);assertEquals(0,i);writes.incrementAndGet();return l;
                    }
                };
            }
        };
        Tbs5530UsbBinding binding=new Tbs5530UsbBinding(session,facade,runtime,runtime,failure::set,30000,10000);
        try{
            assertEquals(0,opens.get());binding.startBootstrap(device);assertTrue(opened.await(3,TimeUnit.SECONDS));
            runtime.execute(()->{try{
                assertFalse(session.isInterfaceClaimed());
                try{binding.controlIo();fail();}catch(IOException expected){}
                try{binding.streamEndpoint();fail();}catch(IOException expected){}
                assertEquals(1,binding.bootstrapControlIo().controlTransfer(0x40,0xa0,0,0,new byte[]{0},0,1,2000));
                binding.cancel();session.drainCleanup();
            }catch(Throwable t){failure.set(t);}finally{finished.countDown();}});
            assertTrue(finished.await(3,TimeUnit.SECONDS));if(failure.get()!=null)throw new AssertionError(failure.get());
            assertEquals(1,opens.get());assertEquals(1,writes.get());assertEquals(1,closes.get());
        }finally{binding.close();runtime.close();}
    }
}
