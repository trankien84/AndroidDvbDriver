// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standard numeric fields only; FX2 TRM v2.2 section 3.3 and Appendix B. Not a silicon probe. */
public final class Tbs5530Fx2Fingerprint {
    public enum Match { MATCH, PARTIAL_MATCH, NO_MATCH }
    public enum Candidate { BOOTSTRAP_CANDIDATE, OPERATIONAL_CANDIDATE, UNKNOWN }
    public static final class Result {
        public final Match match;
        public final Candidate candidate;
        public final List<String> differences;
        private Result(Match match, Candidate candidate, List<String> differences) {
            this.match = match; this.candidate = candidate;
            this.differences = Collections.unmodifiableList(new ArrayList<>(differences));
        }
    }
    private Tbs5530Fx2Fingerprint() { }
    private static void expect(Map<String,Integer> expected, String key, int value) { expected.put(key, value); }
    private static List<Map<String,Integer>> defaults() {
        List<Map<String,Integer>> rows = new ArrayList<>();
        Map<String,Integer> device = new LinkedHashMap<>();
        expect(device,"type",1); expect(device,"length",18); expect(device,"bcdUSB",0x200);
        expect(device,"device_class",255); expect(device,"device_subclass",255); expect(device,"device_protocol",255);
        expect(device,"ep0_packet_size",64); expect(device,"configuration_count",1); rows.add(device);
        Map<String,Integer> config = new LinkedHashMap<>();
        expect(config,"type",2); expect(config,"length",9); expect(config,"wTotalLength",171);
        expect(config,"bNumInterfaces",1); expect(config,"configuration_value",1);
        expect(config,"attributes",0x80); expect(config,"max_power_units_2ma",50); rows.add(config);
        int[] addresses = {1,0x81,2,4,0x86,0x88};
        for (int alt=0;alt<4;alt++) {
            Map<String,Integer> iface = new LinkedHashMap<>();
            expect(iface,"type",4); expect(iface,"length",9); expect(iface,"configuration_value",1);
            expect(iface,"interface_id",0); expect(iface,"alternate",alt); expect(iface,"endpoint_count",alt==0?0:6);
            expect(iface,"class",255); expect(iface,"subclass",255); expect(iface,"protocol",255); rows.add(iface);
            if (alt==0) continue;
            for (int address:addresses) {
                int type = alt==1 || address==4 || address==0x88 ? 2 : alt==3 && (address==2 || address==0x86) ? 1 : 3;
                int packet = alt!=1 && (address==1 || address==0x81) ? 64 : 512;
                Map<String,Integer> ep = new LinkedHashMap<>();
                expect(ep,"type",5); expect(ep,"length",7); expect(ep,"configuration_value",1);
                expect(ep,"interface_id",0); expect(ep,"alternate",alt); expect(ep,"address",address);
                expect(ep,"direction",address&128); expect(ep,"attributes",type); expect(ep,"transfer_type",type);
                expect(ep,"wMaxPacketSize",packet); expect(ep,"additional_transactions",0);
                expect(ep,"interval",type==2?0:1); rows.add(ep);
            }
        }
        return rows;
    }
    public static Result assess(Tbs5530RawDescriptors.Result raw) {
        List<String> diff = new ArrayList<>();
        if (raw==null || !"COMPLETE_RETURNED_TREE".equals(raw.status) || !raw.issues.isEmpty()) {
            diff.add("INCOMPLETE_OR_MALFORMED_TREE");
            return new Result(Match.NO_MATCH,Candidate.UNKNOWN,diff);
        }
        List<Map<String,Integer>> expected = defaults();
        int matches=0;
        if (raw.entries.size()!=expected.size()) diff.add("ENTRY_COUNT");
        for(int i=0;i<expected.size();i++) {
            for(Map.Entry<String,Integer> field:expected.get(i).entrySet()) {
                Integer actual=i<raw.entries.size()?raw.entries.get(i).fields.get(field.getKey()):null;
                if(field.getValue().equals(actual)) matches++;
                else diff.add("ENTRY_"+i+"_"+field.getKey()+"_EXPECTED_"+field.getValue()+"_ACTUAL_"+actual);
            }
        }
        Match match=diff.isEmpty()?Match.MATCH:matches>0?Match.PARTIAL_MATCH:Match.NO_MATCH;
        boolean operational=false;
        for(Tbs5530RawDescriptors.Entry entry:raw.entries) {
            Map<String,Integer> f=entry.fields;
            if(Integer.valueOf(5).equals(f.get("type")) && Integer.valueOf(0).equals(f.get("interface_id"))
                    && Integer.valueOf(0).equals(f.get("alternate")) && Integer.valueOf(1).equals(f.get("configuration_value"))
                    && Integer.valueOf(0x82).equals(f.get("address")) && Integer.valueOf(128).equals(f.get("direction"))
                    && Integer.valueOf(2).equals(f.get("attributes")) && Integer.valueOf(0).equals(f.get("interval"))
                    && (Integer.valueOf(64).equals(f.get("wMaxPacketSize")) || Integer.valueOf(512).equals(f.get("wMaxPacketSize")))) {
                operational=true;
            }
        }
        return new Result(match,match==Match.MATCH?Candidate.BOOTSTRAP_CANDIDATE
                :operational?Candidate.OPERATIONAL_CANDIDATE:Candidate.UNKNOWN,diff);
    }
}
