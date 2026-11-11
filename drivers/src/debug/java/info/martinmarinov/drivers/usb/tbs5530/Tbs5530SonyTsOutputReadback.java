// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Debug-only selector writes and source-reviewed register reads; never changes TS configuration. */
public final class Tbs5530SonyTsOutputReadback {
    public static final String OPTION = "SONY_TS_OUTPUT_READBACK";
    public static final long MAXIMUM_MILLIS = 5000;
    public enum Stage { AFTER_LOCK, BEFORE_FIRST_URB }
    public interface Clock { long nanos(); }
    public interface Authorization {
        void requireCurrent() throws IOException;
        void consume(Plan plan) throws IOException;
        void audit(String boundary, int ordinal, int type, int request, int length, int result) throws IOException;
    }
    interface EntryGuard {
        void requireEntry(Stage stage) throws IOException;
        void requireCurrent() throws IOException;
        void bankUnknown();
        void bank20Restored();
    }
    private static final int[][] READS = {{0xc4,1},{0xc6,1},{0x80,1},{0x81,1},{0x95,1},{0xa9,1},{0x33,1}};
    private static final class Control {
        final int type, request, length;
        final byte[] out;
        Control(int type, int request, int length, byte[] out) { this.type=type; this.request=request; this.length=length; this.out=out; }
    }
    public static final class Plan {
        public final Stage stage;
        public final long generation;
        public final int usbOutCount, usbInCount;
        private final Tbs5530SonyTsOutputReadback owner;
        private final String transcript, sha256;
        private final List<Control> controls;
        private Plan(Tbs5530SonyTsOutputReadback owner, Stage stage) throws IOException {
            this.owner=owner; this.stage=stage; generation=owner.generation;
            List<Control> list=new ArrayList<>(); read(list,0x10,1); bank(list,0);
            for(int[] r:READS) read(list,r[0],r[1]);
            bank(list,0x20); read(list,0x10,1); controls=Collections.unmodifiableList(list);
            int out=0,in=0; for(Control c:list) { if(c.type==0x40)out++;else in++; }
            usbOutCount=out; usbInCount=in;
            transcript=transcript(stage,generation); sha256=hash(transcript);
        }
        public String getApprovalTranscript() { return transcript; }
        public String getSha256() { return sha256; }
        public long maximumMillis() { return MAXIMUM_MILLIS; }
        private static void read(List<Control> list,int reg,int length) {
            list.add(new Control(0x40,0x90,3,new byte[]{(byte)length,(byte)0xc8,(byte)reg}));
            list.add(new Control(0xc0,0x91,length,null));
        }
        private static void bank(List<Control> list,int value) {
            list.add(new Control(0x40,0x80,4,new byte[]{3,(byte)0xc8,0,(byte)value}));
        }
    }
    public static String transcript(Stage stage,long generation) {
        Objects.requireNonNull(stage);
        if(generation<0)throw new IllegalArgumentException("Invalid generation");
        return "TBS5530-SONY-TS-READBACK/v1|stage="+stage+"|generation="+generation+"|"+layout();
    }
    public static String layout() {
        return "chip=0197|SLVT=64|entryBank=20|sync=20:10:1|select=00|reads=C4:1,C6:1,80:1,81:1,95:1,A9:1,33:1"
                +"|restore=20|sync=20:10:1|timeoutMs=2000|maxMs=5000|NO_CONFIG_WRITE|NO_URB_PENDING|retry=0"
                +"|bankProvenance=PLAN_INFERRED|snapshot=NONATOMIC|read81=SOURCE_SERIAL_RMW_NOT_PARALLEL_PROOF"
                +"|source=TBS:2ce787de9f6e81b4294692a45de15596ae2e1322,CoreELEC:072f8e99e0ac66d88d7278784e922e08e8253210,Community:c5fa27eecbe80633cbb365cc44656daea1c0e2e3";
    }
    public static String hash(String value) throws IOException {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        } catch(NoSuchAlgorithmException error) { throw new IOException(error); }
    }
    public static final class Snapshot {
        public final Stage stage;
        public final long startedNanos,endedNanos;
        public final int syncBefore,syncAfter,intentTransfers,attemptedTransfers,completedTransfers;
        public final boolean restoreTransferConfirmed,complete;
        public final String outcome,planSha256;
        public final Map<String,Integer> registers;
        private Snapshot(Plan plan,long started,long ended,int before,int after,int intents,int attempted,int completed,
                         boolean restored,boolean complete,String outcome,Map<String,Integer> values) {
            stage=plan.stage;planSha256=plan.sha256;startedNanos=started;endedNanos=ended;
            syncBefore=before;syncAfter=after;intentTransfers=intents;attemptedTransfers=attempted;completedTransfers=completed;
            restoreTransferConfirmed=restored;this.complete=complete;this.outcome=outcome;
            registers=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
    private final Tbs5530Session session;
    private final long generation;
    private final Tbs5530ControlIo io;
    private final Authorization authorization;
    private final EntryGuard entry;
    private final Clock clock;
    private final Thread worker=Thread.currentThread();
    private final EnumSet<Stage> used=EnumSet.noneOf(Stage.class);
    private Snapshot last;
    private boolean executing;
    Tbs5530SonyTsOutputReadback(Tbs5530Session session,long generation,Tbs5530ControlIo io,
                              Authorization authorization,EntryGuard entry,Clock clock) {
        this.session=Objects.requireNonNull(session);this.generation=generation;this.io=Objects.requireNonNull(io);
        this.authorization=Objects.requireNonNull(authorization);this.entry=Objects.requireNonNull(entry);this.clock=Objects.requireNonNull(clock);
    }
    public Plan plan(Stage stage) throws IOException { return new Plan(this,Objects.requireNonNull(stage)); }
    public Snapshot snapshot() { return last; }
    private static boolean locked(int raw) { return raw>=0 && (raw&7)==6 && (raw&0x20)!=0 && (raw&0x10)==0; }
    public synchronized Snapshot execute(Plan plan) throws IOException {
        if(plan==null||plan.owner!=this||plan.generation!=generation||executing||Thread.currentThread()!=worker)
            throw new IOException("Foreign readback plan, worker or active operation");
        if(used.contains(plan.stage)||plan.stage==Stage.BEFORE_FIRST_URB&&(!used.contains(Stage.AFTER_LOCK)
                ||last==null||last.stage!=Stage.AFTER_LOCK||!last.complete||!locked(last.syncBefore)||!locked(last.syncAfter)))
            throw new IOException("Readback stages cannot be reordered or retried");
        session.requireUsable(generation);authorization.requireCurrent();entry.requireEntry(plan.stage);
        used.add(plan.stage);executing=true;
        try { authorization.consume(plan); }
        catch(IOException|RuntimeException error) { executing=false;entry.bankUnknown();session.failed(generation,error);throw error; }
        final long start;
        try { start=clock.nanos(); }
        catch(RuntimeException error) { executing=false; session.failed(generation,error); throw error; }
        final long[] previous={start};final int[] attempted={0},dispatched={0},completed={0};
        final int[] sync={-1,-1};final boolean[] restored={false};final Map<String,Integer> values=new LinkedHashMap<>();
        Tbs5530ControlIo exact=(type,request,value,index,bytes,offset,length,timeout)->{
            if(!executing||attempted[0]>=plan.controls.size())throw new IOException("Readback program exhausted");
            Control expected=plan.controls.get(attempted[0]);
            if(type!=expected.type||request!=expected.request||value!=0||index!=0||offset!=0||length!=expected.length
                    ||timeout!=2000||bytes==null||bytes.length!=length||expected.out!=null&&!Arrays.equals(expected.out,bytes))
                throw new IOException("Readback control outside exact transcript");
            check(start,previous);authorization.requireCurrent();entry.requireCurrent();session.requireUsable(generation);
            int ordinal=++attempted[0];authorization.audit("SONY_READBACK_INTENT",ordinal,type,request,length,Integer.MIN_VALUE);
            check(start,previous);authorization.requireCurrent();entry.requireCurrent();session.requireUsable(generation);
            dispatched[0]++;
            int count=io.controlTransfer(type,request,value,index,bytes,offset,length,timeout);
            if(count==length)completed[0]++;
            authorization.audit("SONY_READBACK_COMPLETE",ordinal,type,request,length,count);
            check(start,previous);authorization.requireCurrent();entry.requireCurrent();return count;
        };
        Tbs5530Bridge bridge=new Tbs5530Bridge(exact,session,generation);
        try {
            if(start<0)throw new IOException("Invalid readback clock");
            session.transaction(generation,()->{
                try {
                    entry.bankUnknown();sync[0]=read(bridge,0x10,1)[0]&255;
                    bridge.i2cWrite(0x64,0,new byte[]{0,0},0,2);
                    for(int[] item:READS){byte[] result=read(bridge,item[0],item[1]);for(int i=0;i<result.length;i++)values.put(String.format(java.util.Locale.ROOT,"00:%02X",item[0]+i),result[i]&255);}
                    bridge.i2cWrite(0x64,0,new byte[]{0,0x20},0,2);restored[0]=true;
                    sync[1]=read(bridge,0x10,1)[0]&255;
                    if(attempted[0]!=plan.controls.size()||completed[0]!=plan.controls.size())throw new IOException("Incomplete readback transcript");
                    authorization.requireCurrent();entry.requireCurrent();check(start,previous);entry.bank20Restored();
                } catch(Tbs5530ControlIo.TransferException error) { throw error; }
                catch(IOException error) { throw new Tbs5530ControlIo.TransferException(Tbs5530ControlIo.FailureKind.IO_FAILURE,"Sony snapshot failed",error); }
            });
            boolean stable=locked(sync[0])&&locked(sync[1]);
            last=new Snapshot(plan,start,previous[0],sync[0],sync[1],attempted[0],dispatched[0],completed[0],restored[0],true,
                    stable?"COMPLETE_NONATOMIC_SNAPSHOT":"LOCK_CHANGED_DURING_SNAPSHOT",values);
            if(!stable)throw new IOException("TS lock changed during readback; no Bulk or retry");
            return last;
        } catch(IOException|RuntimeException error) {
            entry.bankUnknown();
            if(last==null||last.stage!=plan.stage)last=new Snapshot(plan,start,previous[0],sync[0],sync[1],attempted[0],dispatched[0],completed[0],restored[0],false,"READBACK_FAILED_NO_RETRY",values);
            session.failed(generation,error);throw error;
        } finally { executing=false; }
    }
    private static byte[] read(Tbs5530Bridge bridge,int reg,int length) throws IOException {
        byte[] result=new byte[length];bridge.i2cWriteRead(0x64,0,new byte[]{(byte)reg},0,1,0x64,1,result,0,length);return result;
    }
    private void check(long start,long[] previous) throws IOException {
        long now=clock.nanos();if(start<0||now<previous[0]||now-start>=MAXIMUM_MILLIS*1_000_000L)throw new IOException("Readback deadline or clock failure");previous[0]=now;
    }
}
