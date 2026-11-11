// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530SonyTsOutputReadback;
import java.io.IOException;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** One stage-specific, owner-bound readback permit. It cannot authorize a frontend write program. */
final class Tbs5530SonyReadbackApproval implements Tbs5530SonyTsOutputReadback.Authorization {
    interface Port {
        void requireCurrent() throws IOException;
        void consume(String attempt,String plan,long maximumMillis) throws IOException;
        void write(String name,String text) throws IOException;
        long now();
    }
    private final Tbs5530SonyTsOutputReadback.Plan plan;
    private final Port port;
    private final String attempt=UUID.randomUUID().toString(),owner;
    private final long created;
    private final StringBuilder audit=new StringBuilder();
    private boolean consumed;
    Tbs5530SonyReadbackApproval(Tbs5530SonyTsOutputReadback.Plan plan,String owner,Port port) throws IOException {
        this.plan=java.util.Objects.requireNonNull(plan);this.port=java.util.Objects.requireNonNull(port);
        if(owner==null||!owner.matches("[a-f0-9]{64}"))throw new IOException("Readback owner identity unavailable");
        this.owner=owner;created=port.now();if(created<0)throw new IOException("Invalid readback approval clock");
        port.requireCurrent();
        try {
            port.write("sony-challenge-"+attempt+".json",new JSONObject().put("protocol","TBS5530-SONY-READBACK-PERMIT/v1")
                    .put("attempt",attempt).put("owner_identity",owner).put("generation",plan.generation).put("stage",plan.stage.name())
                    .put("plan_sha256",plan.getSha256()).put("transcript",plan.getApprovalTranscript())
                    .put("maximum_ms",plan.maximumMillis()).put("created_elapsed_ms",created).toString());
        }catch(JSONException error){throw new IOException(error);}
    }
    public void requireCurrent() throws IOException {
        port.requireCurrent();long now=port.now();
        if(now<created||now-created>=plan.maximumMillis()+5000)throw new IOException("Readback approval expired");
    }
    public synchronized void consume(Tbs5530SonyTsOutputReadback.Plan value) throws IOException {
        requireCurrent();if(consumed||value!=plan||!value.getSha256().equals(plan.getSha256()))throw new IOException("Foreign or consumed readback stage");
        consumed=true;port.consume(attempt,plan.getSha256(),plan.maximumMillis());
    }
    public synchronized void audit(String boundary,int ordinal,int type,int request,int length,int result) throws IOException {
        requireCurrent();if(!consumed||ordinal<1||ordinal>plan.usbOutCount+plan.usbInCount
                ||!boundary.matches("SONY_READBACK_(INTENT|COMPLETE)"))throw new IOException("Readback audit outside consumed plan");
        try{
            String line=new JSONObject().put("boundary",boundary).put("ordinal",ordinal).put("request_type",type).put("request",request)
                    .put("length",length).put("result",result).put("elapsed_ms",port.now()).put("owner_identity",owner).toString()+"\n";
            if(audit.length()+line.length()>65536)throw new IOException("Readback audit capacity exhausted");
            audit.append(line);port.write("sony-audit-"+attempt+".jsonl",audit.toString());
        }catch(JSONException error){throw new IOException(error);}
    }
}
