// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic image/USB only. Reflection exercises the private receipt validator, never the real image allowlist. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530OriginalBootstrapReloadTest {
    private static final String EPOCH = Tbs5530AutonomousPlans.hash("fake fixed physical epoch");
    private static final class Rig implements Tbs5530AndroidUsbFacade.Manager {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Tbs5530UsbBinding.Device device = new Tbs5530UsbBinding.Device("/fake/b2-original",17,0x734c,0x5530);
        final Tbs5530Session session = new Tbs5530Session();
        final List<String> events = new ArrayList<>();
        final Tbs5530FirmwarePlan source;
        final Tbs5530AndroidBootstrap.Port port;
        final Tbs5530UsbBinding binding;
        final Tbs5530OneShotBootstrap runner;
        Tbs5530UsbBinding.Events bootstrapEvents;
        boolean permission = true, present = true, operational, consumed;
        int writes, reloadWrites, closes, claims, frontends, failAt = -1;
        Rig() throws Exception {
            byte[] image = new byte[8192]; for(int i=0;i<image.length;i++)image[i]=(byte)(i*37);
            source = Tbs5530FirmwarePlan.create(image,new Tbs5530FirmwarePlan.Policy(MessageDigest.getInstance("SHA-256").digest(image),
                    Collections.singletonList(new Tbs5530FirmwarePlan.Range(0,8192)),Collections.emptyList(),0,8192));
            port = new Tbs5530AndroidBootstrap.Port(context,this,Tbs5530Fx2ProtocolPlan.describe(source,"TBS_SOURCE_DUAL_RESET"),()->{});
            Tbs5530BootstrapTelemetry telemetry = new Tbs5530BootstrapTelemetry(s->{},System::nanoTime,"APP_WRAPPER_TEST"); port.telemetry(telemetry);
            Tbs5530UsbBinding.Facade facade = new Tbs5530UsbBinding.Facade() {
                public List<Tbs5530UsbBinding.Device> devices() { return Rig.this.devices(); }
                public boolean hasPermission(Tbs5530UsbBinding.Device d) { return permission; }
                public Tbs5530UsbBinding.Registration requestPermission(Tbs5530UsbBinding.Device d,String c,Tbs5530UsbBinding.PermissionCallback cb) { throw new AssertionError("No permission bypass"); }
                public Tbs5530UsbBinding.Registration listen(Tbs5530UsbBinding.Events e) throws IOException { bootstrapEvents=e; return port.listen(e); }
                public Tbs5530LifecycleCoordinator.Connection open(Tbs5530UsbBinding.Device d) { throw new AssertionError("Bootstrap never claims"); }
                public Tbs5530LifecycleCoordinator.Connection openBootstrap(Tbs5530UsbBinding.Device d) throws IOException { return port.openBootstrap(d); }
            };
            binding = new Tbs5530UsbBinding(session,facade,Runnable::run,(r,d)->()->{},e->{},30000,10000);
            runner = new Tbs5530OneShotBootstrap(session,binding,facade,device,Tbs5530Fx2ProtocolPlan.describe(source,"TBS_SOURCE_DUAL_RESET"),
                    ()->{},()->{assertFalse(consumed);consumed=true;},ms->assertEquals(100,ms),binding::bootstrapRawDescriptors);
            runner.telemetry(telemetry);
        }
        public List<Tbs5530UsbBinding.Device> devices() { return present ? Collections.singletonList(device) : Collections.emptyList(); }
        public boolean hasPermission(Tbs5530UsbBinding.Device d) { events.add("PERMISSION_CHECK:"+permission);return permission; }
        public void requestPermission(Tbs5530UsbBinding.Device d,android.app.PendingIntent p) { throw new AssertionError("No real permission UI"); }
        public Tbs5530AndroidUsbFacade.RawConnection open(Tbs5530UsbBinding.Device d) {
            return new Tbs5530AndroidUsbFacade.RawConnection() {
                public byte[] rawDescriptors() { return operational ? operationalDescriptor() : defaultDescriptor(); }
                public int negotiatedUsbSpeed() { return 3; }
                public int interfaceCount(){return 1;} public int interfaceId(int i){return 0;} public int alternateSetting(int i){return 0;}
                public int endpointCount(int i){return 2;} public int endpointAddress(int i,int e){return e==0?0x82:0x81;}
                public int endpointDirection(int i,int e){return 128;} public int endpointType(int i,int e){return 2;}
                public int endpointMaxPacketSize(int i,int e){return 512;}
                public boolean claim(int i,boolean force){assertTrue(permission);assertFalse(force);claims++;events.add("CLAIM");return true;}
                public boolean release(int i){events.add("RELEASE");return true;}
                public int controlTransfer(int t,int r,int v,int i,byte[] b,int o,int l,int timeout) {
                    if(operational){assertNotEquals(0xa0,r);frontends++;events.add("FRONTEND");return l;}
                    assertTrue(consumed); Tbs5530FirmwarePlan.Step step=source.getSteps().get(writes++);
                    assertEquals(step.getRequestType(),t);assertEquals(step.getRequest(),r);assertEquals(step.getValue(),v);
                    assertEquals(0,i);assertEquals(0,o);assertEquals(2000,timeout);assertArrayEquals(step.getPayload(),b);events.add("BOOTSTRAP:"+writes);
                    if(writes==failAt)return l-1;
                    if(writes==132){present=false;permission=false;events.add("NATURAL_DETACH");bootstrapEvents.detached(device);}
                    return l;
                }
                public void close(){closes++;events.add("CLOSE");}
            };
        }
        Tbs5530AndroidBootstrap.CleanProof proof(boolean pending) throws Exception {
            Method method=Tbs5530AndroidBootstrap.class.getDeclaredMethod("proveOriginal",Tbs5530AndroidBootstrap.Port.class,
                    Tbs5530OneShotBootstrap.Snapshot.class,boolean.class,String.class);method.setAccessible(true);
            try{return (Tbs5530AndroidBootstrap.CleanProof)method.invoke(null,port,runner.snapshot(),pending,EPOCH);}
            catch(InvocationTargetException error){if(error.getCause() instanceof IOException)throw(IOException)error.getCause();throw error;}
        }
        void load() throws IOException {
            runner.acquire(); try{runner.execute();}finally{port.finishWrites();runner.releaseUpload();runner.finished();}
        }
        void reload(Tbs5530AndroidBootstrap.CleanProof proof,String epoch) throws Exception {
            Tbs5530OperationalHandoff.requireOriginalBootstrapProof(proof,runner.snapshot().boundaries.runEpoch,epoch);
            final byte[][] journal={Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER)},anchor={Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER)};
            Tbs5530ReloadLedger ledger=new Tbs5530ReloadLedger(new Tbs5530ReloadLedger.Storage(){
                public byte[] read(){return journal[0].clone();}public byte[] anchor(){return anchor[0].clone();}
                public void appendAndSync(byte[] e,byte[] a,byte[] s,byte[] as){journal[0]=join(e,s);anchor[0]=join(a,as);}
            });
            long[] now={0}; Tbs5530Fx2Reload.Boundary[] boundary={null};
            Tbs5530Fx2Reload reload=new Tbs5530Fx2Reload(source,()->{if(!permission||claims!=1)throw new IOException("Fresh permission and claim required");},
                    new Tbs5530Fx2Reload.Clock(){public long nanos(){return now[0];}public void pause(int ms){now[0]+=ms*1000000L;}},
                    new Tbs5530Fx2Reload.Port(){
                        public int write(Tbs5530FirmwarePlan.Step s){reloadWrites++;events.add("RELOAD:"+reloadWrites);boundary[0]=new Tbs5530Fx2Reload.Boundary(reloadWrites,s.getValue(),s.getPayload().length,s.getPayload().length,0,now[0],++now[0]);return s.getPayload().length;}
                        public Tbs5530Fx2Reload.Boundary boundary(){return boundary[0];}public void cleanup(){events.add("RELOAD_CLEANUP");}
                    },ledger,EPOCH);
            reload.execute();reload.requireComplete();
        }
    }
    @Test public void originalApi132NaturalTransitionFreshPermissionClaimThenReloadThenFrontend() throws Exception {
        Rig r=new Rig();try{
            r.load();assertEquals(132,r.writes);assertEquals(1,r.closes);assertFalse(r.permission);assertFalse(r.binding.isPending());
            Tbs5530AndroidBootstrap.CleanProof proof=r.proof(false);
            r.operational=true;r.present=true;assertFalse(r.hasPermission(r.device));
            r.permission=true;r.events.add("OS_PERMISSION_FRESH");
            Tbs5530AndroidRuntime runtime=new Tbs5530AndroidRuntime();Tbs5530OperationalHandoff handoff=new Tbs5530OperationalHandoff(r.context,runtime,r,()->{});
            try{
                handoff.start(r.device);long until=System.nanoTime()+5000000000L;while(handoff.pending()&&System.nanoTime()<until)Thread.sleep(5);
                assertTrue(handoff.operationalOwner());assertEquals(1,r.claims);
                java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
                runtime.execute(()->{try{r.reload(proof,EPOCH);assertEquals(132,r.reloadWrites);assertEquals(0,r.frontends);
                    handoff.controlIo().controlTransfer(0x40,0x80,0,0,new byte[]{3,(byte)0xc8,0,0},0,4,2000);
                }catch(Throwable e){error.set(e);}finally{done.countDown();}});
                assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());
                assertEquals(1,r.frontends);assertTrue(r.events.indexOf("NATURAL_DETACH")<r.events.indexOf("OS_PERMISSION_FRESH"));
                assertTrue(r.events.indexOf("CLAIM")<r.events.indexOf("RELOAD:1"));assertTrue(r.events.indexOf("RELOAD:132")<r.events.indexOf("FRONTEND"));
            }finally{handoff.close();runtime.close();assertTrue(runtime.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
        }finally{r.binding.close();}
    }
    @Test public void missingWrongEpochOrUndrainedOriginalProofNeverReloads() throws Exception {
        Rig r=new Rig();try{r.load();Tbs5530AndroidBootstrap.CleanProof proof=r.proof(false);
            assertThrows(IOException.class,()->r.reload(null,EPOCH));assertThrows(IOException.class,()->r.reload(proof,Tbs5530AutonomousPlans.hash("wrong physical epoch")));
            assertThrows(IOException.class,()->r.proof(true));assertEquals(0,r.reloadWrites);assertEquals(0,r.frontends);
        }finally{r.binding.close();}
    }
    @Test public void uncertainOriginalBootstrapOrUnapprovedImageCannotIssueProofOrReload() throws Exception {
        Rig r=new Rig();try{r.failAt=66;assertThrows(IOException.class,r::load);assertThrows(IOException.class,()->r.proof(false));assertEquals(0,r.reloadWrites);
            try(Tbs5530AndroidRuntime runtime=new Tbs5530AndroidRuntime()){
                assertThrows(IllegalArgumentException.class,()->new Tbs5530AndroidBootstrap(r.context,runtime,r.device,r.source,()->{},()->{}));
            }
            assertEquals(66,r.writes);
        }finally{r.binding.close();}
    }
    private static byte[] join(byte[] a,byte[] b){byte[] n=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,n,a.length,b.length);return n;}
    private static byte[] operationalDescriptor(){return new byte[]{18,1,0,2,0,0,0,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1,9,2,32,0,1,1,0,(byte)128,50,9,4,0,0,2,(byte)255,0,0,0,7,5,(byte)0x82,2,0,2,1,7,5,(byte)0x81,2,0,2,0};}
    private static void append(ByteArrayOutputStream out,int...v){for(int b:v)out.write(b);}
    private static byte[] defaultDescriptor(){ByteArrayOutputStream out=new ByteArrayOutputStream();append(out,18,1,0,2,255,255,255,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1);append(out,9,2,171,0,1,1,0,128,50);int[] addresses={1,129,2,4,134,136};for(int alt=0;alt<4;alt++){append(out,9,4,0,alt,alt==0?0:6,255,255,255,0);if(alt==0)continue;for(int a:addresses){int type=alt==1||a==4||a==136?2:alt==3&&(a==2||a==134)?1:3;int packet=alt!=1&&(a==1||a==129)?64:512;append(out,7,5,a,type,packet&255,packet>>8,type==2?0:1);}}return out.toByteArray();}
}
