// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530HistoryRecoveryTest {
    private static byte[] record(char key) {
        char[] text = new char[64]; Arrays.fill(text,key);
        return ("FIRMWARE_CONSUMED " + new String(text) + "\n").getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] join(byte[]... values) {
        int size = 0; for(byte[] bytes:values)size+=bytes.length;
        byte[] result=new byte[size];int at=0;for(byte[] bytes:values){System.arraycopy(bytes,0,result,at,bytes.length);at+=bytes.length;}
        return result;
    }
    private static final class Rig implements Tbs5530HistoryRecovery.Port {
        byte[] current=record('a'); final byte[] candidate=join(current,record('b'),record('c'));
        int consumes,appends;boolean allowed=true,failConsume,partial,changeAfterConsume,failAfterAppend;
        final Tbs5530HistoryRecovery operation;
        Rig() throws IOException { operation=new Tbs5530HistoryRecovery(Tbs5530HistoryRecovery.prepare(current,candidate,
                Tbs5530HistoryRecovery.hash(current),Tbs5530HistoryRecovery.hash(candidate)),this); }
        public void requireCurrent() throws IOException {if(!allowed||failAfterAppend&&appends>0)throw new IOException("Synthetic authority loss");}
        public byte[] readCurrent(){return current.clone();}
        public void consume(String hash) throws IOException {
            consumes++;if(failConsume)throw new IOException("Synthetic durable consume failed");
            if(changeAfterConsume)current=record('d');
        }
        public void appendAndSync(String expected,byte[] suffix) throws IOException {
            assertEquals(expected,Tbs5530HistoryRecovery.hash(current)); appends++;
            current=join(current,partial?Arrays.copyOf(suffix,10):suffix);
            if(partial)throw new IOException("Synthetic partial write");
        }
    }
    @Test public void exactApprovedAppendConsumesFirstPreservesPrefixAndCannotRetry() throws Exception {
        Rig r=new Rig();r.operation.apply();assertEquals(1,r.consumes);assertEquals(1,r.appends);
        assertArrayEquals(r.candidate,r.current);assertEquals(Tbs5530HistoryRecovery.State.APPEND_VERIFIED,r.operation.state());
        assertThrows(IOException.class,r.operation::apply);assertEquals(1,r.appends);
    }
    @Test public void permissionAndBaselineMismatchRefuseBeforeConsumeOrAppend() throws Exception {
        for(int mode=0;mode<2;mode++){
            Rig r=new Rig();if(mode==0)r.allowed=false;else r.current=record('d');
            assertThrows(IOException.class,r.operation::apply);assertEquals(0,r.consumes);assertEquals(0,r.appends);
            assertEquals(Tbs5530HistoryRecovery.State.REFUSED,r.operation.state());assertThrows(IOException.class,r.operation::apply);
        }
    }
    @Test public void consumeFailureOrConcurrentStoreChangeNeverAppends() throws Exception {
        for(int mode=0;mode<2;mode++){
            Rig r=new Rig();if(mode==0)r.failConsume=true;else r.changeAfterConsume=true;
            assertThrows(IOException.class,r.operation::apply);assertEquals(1,r.consumes);assertEquals(0,r.appends);
            assertThrows(IOException.class,r.operation::apply);
        }
    }
    @Test public void partialWriteOrAuthorityLossAfterAppendIsUnknownAndNeverRolledBack() throws Exception {
        for(int mode=0;mode<2;mode++){
            Rig r=new Rig();if(mode==0)r.partial=true;else r.failAfterAppend=true;
            assertThrows(IOException.class,r.operation::apply);
            assertEquals(Tbs5530HistoryRecovery.State.COMMIT_UNKNOWN,r.operation.state());assertEquals(1,r.appends);
            assertTrue(r.current.length>record('a').length);assertThrows(IOException.class,r.operation::apply);
        }
    }
    @Test public void planCannotRemoveRewriteReorderOrAcceptHashDriftAndMalformedRecords() throws Exception {
        byte[] before=record('a');byte[] after=join(before,record('b'),record('c'));
        for(byte[] bad:new byte[][]{join(record('b'),before,record('c')),join(before,record('b')),join(before,before,record('b')),
                join(before,record('b'),record('c'),record('d')),new byte[0],new byte[]{(byte)0xff}})
            assertThrows(IOException.class,()->Tbs5530HistoryRecovery.prepare(before,bad,Tbs5530HistoryRecovery.hash(before),Tbs5530HistoryRecovery.hash(bad)));
        assertThrows(IOException.class,()->Tbs5530HistoryRecovery.prepare(before,after,"wrong",Tbs5530HistoryRecovery.hash(after)));
    }
    @Test public void fenceRejectsRollbackAfterAnOwnedAppendAndNeverAcceptsReorderedPrefix() throws Exception {
        Tbs5530HistoryFence fence=new Tbs5530HistoryFence(record('a'));
        fence.require(record('a')); fence.advanced(join(record('a'),record('b')));
        fence.require(join(record('a'),record('b')));
        assertThrows(IOException.class,()->fence.require(record('a')));
        assertThrows(IOException.class,()->fence.require(join(record('a'),record('b'))));
        assertThrows(IOException.class,()->fence.advanced(join(record('a'),record('b'),record('c'))));
        Tbs5530HistoryFence reordered=new Tbs5530HistoryFence(record('a'));
        assertThrows(IOException.class,()->reordered.advanced(join(record('b'),record('a'),record('c'))));
        assertThrows(IOException.class,()->reordered.require(record('a')));
    }
    @Test public void pinnedPrivateCandidateCanBeReviewedWithoutWritingAnyStore() throws Exception {
        String beforePath=System.getenv("TBS5530_HISTORY_BASELINE_FIXTURE"),afterPath=System.getenv("TBS5530_HISTORY_CANDIDATE_FIXTURE");
        org.junit.Assume.assumeTrue("Private recovery fixture not configured",beforePath!=null&&afterPath!=null);
        byte[] before=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(beforePath));
        byte[] after=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(afterPath));
        Tbs5530HistoryRecovery.Plan p=Tbs5530HistoryRecovery.prepare(before,after,
                "e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9",
                "fe9ab4fc788822da4553afd8f40d576ceb768b030c949a8a1b80931e77281612");
        assertEquals(581,p.baselineBytes);assertEquals(747,p.candidateBytes);
        System.out.println("RECOVERY_OFFLINE_PLAN_SHA256="+p.sha256+" APPEND_NOT_APPLIED NO_HARDWARE_AUTHORIZATION");
    }
}
