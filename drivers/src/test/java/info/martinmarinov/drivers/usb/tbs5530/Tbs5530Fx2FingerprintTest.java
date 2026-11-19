// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

public class Tbs5530Fx2FingerprintTest {
    static void append(ByteArrayOutputStream out,int... values){for(int value:values)out.write(value);}
    // Reconstructed standard fields from the Phase 2.6 report, not captured raw bytes or strings.
    static byte[] bootstrap(){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        append(out,18,1,0,2,255,255,255,64,0x4c,0x73,0x30,0x55,1,0,0,0,0,1);
        append(out,9,2,171,0,1,1,0,0x80,50);
        int[] addresses={1,0x81,2,4,0x86,0x88};
        for(int alt=0;alt<4;alt++){
            append(out,9,4,0,alt,alt==0?0:6,255,255,255,0);
            if(alt==0)continue;
            for(int a:addresses){
                int type=alt==1||a==4||a==0x88?2:alt==3&&(a==2||a==0x86)?1:3;
                int packet=alt!=1&&(a==1||a==0x81)?64:512;
                append(out,7,5,a,type,packet&255,packet>>8,type==2?0:1);
            }
        }
        return out.toByteArray();
    }
    static byte[] operational(){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] dev=Arrays.copyOf(bootstrap(),18);out.write(dev,0,dev.length);
        append(out,9,2,25,0,1,1,0,0x80,50);append(out,9,4,0,0,1,255,255,255,0);
        append(out,7,5,0x82,2,0,2,0);return out.toByteArray();
    }
    static Tbs5530Fx2Fingerprint.Result assess(byte[] data){return Tbs5530Fx2Fingerprint.assess(Tbs5530RawDescriptors.parse(data));}
    @Test public void historicalStandardFieldsMatchDefaultHighSpeed(){
        byte[] data=bootstrap();assertEquals(189,data.length);
        Tbs5530Fx2Fingerprint.Result result=assess(data);
        assertEquals(Tbs5530Fx2Fingerprint.Match.MATCH,result.match);
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE,result.candidate);
        assertTrue(result.differences.isEmpty());
    }
    @Test public void vidPidDeviceReleaseAndAndroidIdentityCannotDefineFingerprint(){
        byte[] data=bootstrap();Arrays.fill(data,8,17,(byte)0);
        assertEquals(Tbs5530Fx2Fingerprint.Match.MATCH,assess(data).match);
        for(String host:new String[]{"GM1900/API30","other/API21","emulator/API37"}){
            assertEquals(host,Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE,assess(data).candidate);
        }
    }
    @Test public void changesToEveryRelevantFieldAreReported(){
        int[] offsets={2,4,5,6,7,17,22,23,25,26,29,30,32,33,34,47,48,49,50,51,52};
        for(int offset:offsets){byte[] data=bootstrap();data[offset]^=1;
            Tbs5530Fx2Fingerprint.Result r=assess(data);
            assertNotEquals("offset="+offset,Tbs5530Fx2Fingerprint.Match.MATCH,r.match);
            assertNotEquals(Tbs5530Fx2Fingerprint.Candidate.BOOTSTRAP_CANDIDATE,r.candidate);
            assertFalse(r.differences.isEmpty());}
    }
    @Test public void onlyOperational82ProfileIsCandidateNotReady(){
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.OPERATIONAL_CANDIDATE,assess(operational()).candidate);
        byte[] data=operational();data[38]=(byte)0x88;
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.UNKNOWN,assess(data).candidate);
        data=operational();data[38]=(byte)0x81;
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.UNKNOWN,assess(data).candidate);
    }
    @Test public void malformedIncompleteUnknownAndEveryTruncationAreRejected(){
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.UNKNOWN,Tbs5530Fx2Fingerprint.assess(null).candidate);
        byte[] data=bootstrap();
        for(int n=0;n<data.length;n++)assertEquals(Tbs5530Fx2Fingerprint.Candidate.UNKNOWN,assess(Arrays.copyOf(data,n)).candidate);
        data[0]=0;assertEquals(Tbs5530Fx2Fingerprint.Match.NO_MATCH,assess(data).match);
    }
    @Test public void nearMatchHasFieldDifferencesAndDoesNotAuthorizeBootstrap(){
        byte[] data=bootstrap();data[51]=2;
        Tbs5530Fx2Fingerprint.Result result=assess(data);
        assertEquals(Tbs5530Fx2Fingerprint.Match.PARTIAL_MATCH,result.match);
        assertEquals(Tbs5530Fx2Fingerprint.Candidate.UNKNOWN,result.candidate);
        assertTrue(result.differences.toString().contains("interval"));
        try{result.differences.clear();fail();}catch(UnsupportedOperationException expected){}
    }
}
