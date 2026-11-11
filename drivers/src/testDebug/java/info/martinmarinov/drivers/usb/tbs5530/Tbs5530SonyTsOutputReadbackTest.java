// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530SonyTsOutputReadbackTest {
    private static final class Fixture implements Tbs5530SonyTsOutputReadback.Authorization,
            Tbs5530SonyTsOutputReadback.EntryGuard,Tbs5530SonyTsOutputReadback.Clock {
        final Tbs5530Session session=new Tbs5530Session();final long generation;
        final Tbs5530SonyTsOutputReadback monitor;
        final List<String> calls=new ArrayList<>();final Map<Integer,Integer> registers=new HashMap<>();
        final EnumSet<Tbs5530SonyTsOutputReadback.Stage> consumed=EnumSet.noneOf(Tbs5530SonyTsOutputReadback.Stage.class);
        int bank=0x20,pointer,ioCalls,faultAt=-1,faultKind,sync=0x26,pending,chip=0x197,auditFault=-1,revokeIntent=-1;
        boolean approved=true,present=true,knownBank=true,restoreAcknowledged,failConsume;
        long now=1000000000L,advancePerCall;
        Fixture() throws Exception {
            generation=session.requestPermission();session.permissionResult(generation,true);
            session.connected(generation,new Tbs5530Session.Resources(){public void cancel(){}public void release(){}public void close(){}});
            session.interfaceClaimed(generation);session.ready(generation);session.frontendInitialized(generation);session.frontendLock(generation,true);
            registers.put(0xc4,0x29);registers.put(0xc6,1);registers.put(0x80,0x28);registers.put(0x81,0);
            registers.put(0x95,0x15);registers.put(0xa9,0);registers.put(0x33,1);
            monitor=new Tbs5530SonyTsOutputReadback(session,generation,(type,request,value,index,bytes,offset,length,timeout)->{
                assertEquals(0,value);assertEquals(0,index);assertEquals(0,offset);assertEquals(2000,timeout);
                assertTrue(approved);assertTrue(present);assertEquals(0,pending);assertFalse(consumed.isEmpty());
                int ordinal=++ioCalls;calls.add(String.format("%02x:%02x:%02x:%d",type,request,bank,length));now+=advancePerCall;
                if(ordinal==faultAt){
                    if(faultKind==0)return -1;if(faultKind==1)return length-1;if(faultKind==2)return length+1;
                    if(faultKind==3)throw new IOException("Fake control failure");if(faultKind==4){present=false;return length;}
                }
                if(request==0x80){assertEquals(0x40,type);assertEquals(4,length);assertEquals(3,bytes[0]);assertEquals(0xc8,bytes[1]&255);assertEquals(0,bytes[2]);int next=bytes[3]&255;assertTrue(next==0||next==0x20);bank=next;}
                else if(request==0x90){assertEquals(0x40,type);assertEquals(3,length);assertEquals(1,bytes[0]);assertEquals(0xc8,bytes[1]&255);pointer=bytes[2]&255;if(bank==0)assertTrue(registers.containsKey(pointer));else assertEquals(0x10,pointer);}
                else if(request==0x91){assertEquals(0xc0,type);assertEquals(1,length);bytes[0]=(byte)(bank==0x20?sync:registers.get(pointer));}
                else fail("Unexpected USB request");return length;
            },this,this,this);
        }
        public void requireCurrent() throws IOException {if(!approved||!present||pending!=0||chip!=0x197)throw new IOException("Readback scope revoked");}
        public void requireEntry(Tbs5530SonyTsOutputReadback.Stage stage) throws IOException {
            requireCurrent();if(!knownBank||stage==Tbs5530SonyTsOutputReadback.Stage.AFTER_LOCK&&session.isStreaming()
                    ||stage==Tbs5530SonyTsOutputReadback.Stage.BEFORE_FIRST_URB&&!session.isStreaming())throw new IOException("Bank or stage unavailable");
        }
        public void bankUnknown(){knownBank=false;}
        public void bank20Restored(){knownBank=true;restoreAcknowledged=true;}
        public void consume(Tbs5530SonyTsOutputReadback.Plan plan) throws IOException {requireCurrent();if(failConsume||!consumed.add(plan.stage))throw new IOException("Consumed or persistence failed");}
        public void audit(String boundary,int ordinal,int type,int request,int length,int result) throws IOException {
            if(ordinal==auditFault)throw new IOException("Fake audit failure");
            if(ordinal==revokeIntent&&boundary.equals("SONY_READBACK_INTENT"))approved=false;
        }
        public long nanos(){return now;}
        Tbs5530SonyTsOutputReadback.Plan after() throws IOException{return monitor.plan(Tbs5530SonyTsOutputReadback.Stage.AFTER_LOCK);}
    }
    @Test public void exactNonatomicSnapshotHasTwentyControlsAndOnlySelectorWrites() throws Exception {
        Fixture f=new Fixture();Tbs5530SonyTsOutputReadback.Plan plan=f.after();
        assertEquals(11,plan.usbOutCount);assertEquals(9,plan.usbInCount);assertEquals(5000,plan.maximumMillis());
        Tbs5530SonyTsOutputReadback.Snapshot s=f.monitor.execute(plan);
        assertTrue(s.complete);assertTrue(s.restoreTransferConfirmed);assertEquals(20,s.completedTransfers);assertEquals(20,f.ioCalls);
        assertEquals(7,s.registers.size());assertEquals(Integer.valueOf(0),s.registers.get("00:81"));assertEquals(Integer.valueOf(0x29),s.registers.get("00:C4"));
        assertFalse(s.registers.containsKey("00:C3"));assertFalse(s.registers.containsKey("00:96"));assertFalse(s.registers.containsKey("00:97"));assertFalse(s.registers.containsKey("00:D9"));
        assertEquals(0x26,s.syncBefore);assertEquals(0x26,s.syncAfter);assertEquals(0x20,f.bank);assertTrue(f.knownBank);
        assertTrue(plan.getApprovalTranscript().contains("bankProvenance=PLAN_INFERRED"));assertTrue(plan.getApprovalTranscript().contains("NONATOMIC"));
        assertTrue(plan.getApprovalTranscript().contains("read81=SOURCE_SERIAL_RMW"));assertThrows(UnsupportedOperationException.class,()->s.registers.put("00:C3",0));
        f.session.close();
    }
    @Test public void twoStagesAreSeparatePermitsAndCannotReplayOrReorder() throws Exception {
        Fixture f=new Fixture();assertThrows(IOException.class,()->f.monitor.execute(f.monitor.plan(Tbs5530SonyTsOutputReadback.Stage.BEFORE_FIRST_URB)));assertEquals(0,f.ioCalls);
        Tbs5530SonyTsOutputReadback.Plan a=f.after();f.monitor.execute(a);assertThrows(IOException.class,()->f.monitor.execute(a));
        f.session.streamingStarted(f.generation);Tbs5530SonyTsOutputReadback.Plan b=f.monitor.plan(Tbs5530SonyTsOutputReadback.Stage.BEFORE_FIRST_URB);
        assertNotEquals(a.getSha256(),b.getSha256());f.monitor.execute(b);assertEquals(40,f.ioCalls);assertEquals(2,f.consumed.size());
        assertThrows(IOException.class,()->f.monitor.execute(b));assertEquals(40,f.ioCalls);f.session.close();
    }
    @Test public void everyShortNegativeOversizedExceptionOrDisconnectStopsWithoutRecoveryWrites() throws Exception {
        for(int kind=0;kind<5;kind++)for(int ordinal=1;ordinal<=20;ordinal++){
            Fixture f=new Fixture();f.faultAt=ordinal;f.faultKind=kind;
            assertThrows(IOException.class,()->f.monitor.execute(f.after()));assertEquals(ordinal,f.ioCalls);assertFalse(f.knownBank);
            Tbs5530SonyTsOutputReadback.Snapshot s=f.monitor.snapshot();assertNotNull(s);assertFalse(s.complete);
            assertThrows(IOException.class,()->f.monitor.execute(f.after()));assertEquals(ordinal,f.ioCalls);f.session.close();
        }
    }
    @Test public void ownerPermissionUnknownBankAndPendingUrbsDenyBeforeAnyControl() throws Exception {
        for(int bad=0;bad<5;bad++){
            Fixture f=new Fixture();if(bad==0)f.approved=false;if(bad==1)f.present=false;if(bad==2)f.knownBank=false;if(bad==3)f.pending=1;if(bad==4)f.chip=0x19c;
            assertThrows(IOException.class,()->f.monitor.execute(f.after()));assertEquals(0,f.ioCalls);assertTrue(f.consumed.isEmpty());f.session.close();
        }
    }
    @Test public void auditFailureAndRevocationBeforeDispatchNeverSendTheNextControl() throws Exception {
        for(int ordinal=1;ordinal<=20;ordinal++){
            Fixture audit=new Fixture();audit.auditFault=ordinal;assertThrows(IOException.class,()->audit.monitor.execute(audit.after()));assertEquals(ordinal-1,audit.ioCalls);audit.session.close();
            Fixture revoked=new Fixture();revoked.revokeIntent=ordinal;assertThrows(IOException.class,()->revoked.monitor.execute(revoked.after()));assertEquals(ordinal-1,revoked.ioCalls);
            assertEquals(ordinal,revoked.monitor.snapshot().intentTransfers);assertEquals(ordinal-1,revoked.monitor.snapshot().attemptedTransfers);revoked.session.close();
        }
    }
    @Test public void clockAndFiveSecondBoundFailClosedAndNeverStartBulk() throws Exception {
        Fixture expired=new Fixture();expired.advancePerCall=1_000_000_000L;assertThrows(IOException.class,()->expired.monitor.execute(expired.after()));assertEquals(5,expired.ioCalls);assertFalse(expired.knownBank);expired.session.close();
        Fixture invalid=new Fixture();invalid.now=-1;assertThrows(IOException.class,()->invalid.monitor.execute(invalid.after()));assertEquals(0,invalid.ioCalls);invalid.session.close();
    }
    @Test public void lostLockIsReportedOnlyAfterACompleteRestoredSnapshotAndNoRetry() throws Exception {
        Fixture f=new Fixture();f.sync=0x25;assertThrows(IOException.class,()->f.monitor.execute(f.after()));
        assertEquals(20,f.ioCalls);assertTrue(f.monitor.snapshot().complete);assertTrue(f.monitor.snapshot().restoreTransferConfirmed);
        assertEquals("LOCK_CHANGED_DURING_SNAPSHOT",f.monitor.snapshot().outcome);assertFalse(f.knownBank);f.session.close();
    }
    @Test public void failedDurableConsumptionCannotAuthorizeEitherStageAndClockCannotMoveBackwards() throws Exception {
        Fixture failed=new Fixture();failed.failConsume=true;assertThrows(IOException.class,()->failed.monitor.execute(failed.after()));assertEquals(0,failed.ioCalls);
        assertThrows(IOException.class,()->failed.monitor.execute(failed.monitor.plan(Tbs5530SonyTsOutputReadback.Stage.BEFORE_FIRST_URB)));assertEquals(0,failed.ioCalls);failed.session.close();
        Fixture backwards=new Fixture();backwards.advancePerCall=-1;assertThrows(IOException.class,()->backwards.monitor.execute(backwards.after()));assertEquals(1,backwards.ioCalls);assertFalse(backwards.knownBank);backwards.session.close();
    }
    @Test public void foreignPlanAndWorkerCannotUseTheOwnedTransport() throws Exception {
        Fixture a=new Fixture(),b=new Fixture();assertThrows(IOException.class,()->b.monitor.execute(a.after()));assertEquals(0,b.ioCalls);
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread t=new Thread(()->{try{a.monitor.execute(a.after());}catch(Throwable error){failure.set(error);}});t.start();t.join();assertTrue(failure.get() instanceof IOException);assertEquals(0,a.ioCalls);a.session.close();b.session.close();
    }
}
