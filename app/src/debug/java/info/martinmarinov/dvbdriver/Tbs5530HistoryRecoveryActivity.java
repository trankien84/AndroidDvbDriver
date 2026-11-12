// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import info.martinmarinov.drivers.usb.tbs5530.*;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Debug-only exact deny-list reconciliation. No firmware, USB connection or tuner operation. */
public final class Tbs5530HistoryRecoveryActivity extends Activity {
    static final String BEFORE="e398459ceddb2c8f35ddc61b81ee66a31ec1aee300c33bb603dc9f593b0b31f9";
    static final String AFTER="fe9ab4fc788822da4553afd8f40d576ceb768b030c949a8a1b80931e77281612";
    static final String PLAN="28138fcf310666a0a1d2d2b79ae98eaed8e5aae734befabdb3926af8d27f3ba7";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private File directory;
    private String session, launch;
    private long created;
    private volatile boolean foreground, terminal, working;
    private Tbs5530RecoveryPermit permit;
    private Tbs5530RecoveryJournal journal;
    private JSONObject challenge;
    private boolean witness;
    private String beforeHash, afterHash, planHash, protocol;
    private int afterBytes;
    private TextView status;
    private final Tbs5530AutonomousLaunch launches=new Tbs5530AutonomousLaunch();
    private final Runnable tick=new Runnable(){public void run(){
        if(terminal||working)return;
        try{
            if(!foreground||SystemClock.elapsedRealtime()-created>=Tbs5530RecoveryPermit.MAX_MS)throw new IOException("Recovery wait expired");
            if(new File(directory,"grant.json").isFile()){
                permit.accept(); working=true;status.setText(witness ? "Đang nối lại năm khóa cấm từ witness đã duyệt; giữ tuner rút." : "Đang bổ sung đúng hai khóa cấm; không rút/kết nối tuner.");
                new Thread(()->apply(),"tbs5530-history-recovery").start();return;
            }
        }catch(IOException|RuntimeException error){finishReport("REFUSED_BEFORE_APPEND",error);return;}
        ui.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,24,24,24);
        status=new TextView(this);status.setText("Recovery debug: chỉ nối thêm hai khóa cấm, không khôi phục toàn lịch sử hoặc cấp quyền B1. Chờ grant chính xác từ host.");layout.addView(status);
        Button stop=new Button(this);stop.setText("STOP — dừng recovery");stop.setOnClickListener(v->{if(permit!=null)permit.stop();if(!working)finishReport("STOPPED_BEFORE_APPEND",null);});layout.addView(stop);setContentView(layout);
        begin(getIntent());
    }
    private void begin(android.content.Intent intent){
        try{
            launches.begin(intent.getStringExtra("launch_token"));
            terminal=false;working=false;directory=null;session=null;permit=null;journal=null;challenge=null;
            launch=intent.getStringExtra("launch_token");
            String mode=intent.getStringExtra("recovery_mode");
            if(mode!=null&&!"EXACT_HOST_WITNESS_V2".equals(mode))throw new IOException("Unknown recovery mode");
            witness="EXACT_HOST_WITNESS_V2".equals(mode);
            beforeHash=witness?Tbs5530HistoryRecovery.WITNESS_BEFORE:BEFORE;
            afterHash=witness?Tbs5530HistoryRecovery.WITNESS_AFTER:AFTER;
            planHash=witness?Tbs5530HistoryRecovery.witnessPlanSha256():PLAN;
            afterBytes=witness?996:747;
            protocol=witness?"TBS5530-HISTORY-WITNESS-APPLY/v2":"TBS5530-HISTORY-APPLY/v1";
            if(witness)status.setText("Recovery witness: chỉ nối năm khóa cấm còn thiếu từ bản host đã duyệt; không cấp quyền firmware hoặc B2. Giữ tuner rút.");
            if(!Tbs5530AutonomousLaunch.valid(launch)||!"SM-X926B".equals(Build.MODEL)||Build.VERSION.SDK_INT!=36)
                throw new IOException("Exact Samsung recovery launch required");
            if(!Tbs5530AutonomousAndroidAccess.manager(this).devices().isEmpty())throw new IOException("Tuner must be disconnected");
            journal=new Tbs5530RecoveryJournal(getNoBackupFilesDir());
            if(journal.consumed(planHash))throw new IOException("Recovery plan already consumed");
            byte[] before=Tbs5530HistoryRecoveryStore.readSnapshot(this,"autonomous-v1-samsung-sm-x926b-api36");
            if(!beforeHash.equals(Tbs5530HistoryRecovery.hash(before)))throw new IOException("Recovery baseline mismatch");
            session=UUID.randomUUID().toString();created=SystemClock.elapsedRealtime();
            directory=new File(getFilesDir(),"history-recovery-v1-"+session);if(!directory.mkdir())throw new IOException("Fresh namespace required");
            challenge=new JSONObject().put("protocol",protocol).put("session",session).put("launch_token",launch)
                    .put("model",Build.MODEL).put("api",Build.VERSION.SDK_INT)
                    .put("boot_count",Settings.Global.getInt(getContentResolver(),"boot_count",-1))
                    .put("apk_sha256",Tbs5530AutonomousSession.installedApkHash(this)).put("source_git_sha",BuildConfig.SOURCE_GIT_SHA)
                    .put("plan_sha256",planHash).put("before_sha256",beforeHash).put("after_sha256",afterHash)
                    .put("before_bytes",581).put("after_bytes",afterBytes).put("created_ms",created);
            if(challenge.getInt("boot_count")<0)throw new IOException("Boot identity unavailable");
            permit=new Tbs5530RecoveryPermit(challenge,new Tbs5530RecoveryPermit.Port(){
                public long now(){return SystemClock.elapsedRealtime();}
                public boolean foreground(){return foreground&&!terminal;}
                public String grant()throws IOException{return Tbs5530AutonomousSession.read(new File(directory,"grant.json"),16384);}
                public String heartbeat()throws IOException{return Tbs5530AutonomousSession.read(new File(directory,"heartbeat.json"),2048);}
                public boolean consumed()throws IOException{return journal.consumed(planHash);}
                public void consume(String record)throws IOException{journal.consume(planHash,record);}
            });
            write(new File(directory,"challenge.json"),challenge.toString());
            write(new File(getFilesDir(),"history-recovery-v1-challenge.json"),challenge.toString());
            setIntent(new android.content.Intent(intent));
            if(foreground)ui.post(tick);
        }catch(Exception error){finishReport("REFUSED_BEFORE_APPEND",error);}
    }
    @Override protected void onNewIntent(android.content.Intent intent){
        super.onNewIntent(intent);
        String token=intent.getStringExtra("launch_token");
        if(launches.matches(token))return;
        try{
            launches.requireFresh(token);
            if(!terminal||working||!launches.cleaned())throw new IOException("Recovery workflow still active");
            begin(intent);
        }catch(IOException error){status.setText("Không thay recovery đang chạy hoặc chưa cleanup. Không cấp grant mới.");}
    }
    private void apply(){
        try{
            permit.requireCurrent();
            File candidateFile=new File(directory,"candidate.txt");
            byte[] candidate=Tbs5530AutonomousSession.read(candidateFile,afterBytes).getBytes(StandardCharsets.UTF_8);
            byte[] before=Tbs5530HistoryRecoveryStore.readSnapshot(this,"autonomous-v1-samsung-sm-x926b-api36");
            Tbs5530HistoryRecovery.Plan plan=witness?Tbs5530HistoryRecovery.prepareWitness(before,candidate):Tbs5530HistoryRecovery.prepare(before,candidate,BEFORE,AFTER);
            if(!planHash.equals(plan.sha256))throw new IOException("Pinned recovery plan changed");
            Tbs5530HistoryRecovery.State state=Tbs5530HistoryRecoveryStore.append(this,plan,permit);
            if(state!=Tbs5530HistoryRecovery.State.APPEND_VERIFIED)throw new IOException("Recovery commit unknown");
            finishReport("APPEND_VERIFIED_NO_HARDWARE_AUTHORIZATION",null);
        }catch(IOException|RuntimeException error){finishReport("REFUSED_OR_COMMIT_UNKNOWN_NO_RETRY",error);}
    }
    private synchronized void finishReport(String result,Throwable error){
        if(terminal)return;terminal=true;working=false;ui.removeCallbacks(tick);
        launches.runtimeCleanup(true);
        boolean consumed=false;String current="UNAVAILABLE";
        try{consumed=journal!=null&&journal.consumed(planHash);}catch(IOException ignored){}
        try{current=Tbs5530HistoryRecovery.hash(Tbs5530HistoryRecoveryStore.readSnapshot(this,"autonomous-v1-samsung-sm-x926b-api36"));}catch(IOException ignored){}
        try{
            JSONObject report=new JSONObject().put("protocol",protocol).put("session",session==null?JSONObject.NULL:session)
                    .put("launch_token",launch==null?JSONObject.NULL:launch).put("terminal",true).put("result",result)
                    .put("plan_sha256",planHash).put("history_sha256",current).put("consumed",consumed).put("hardware_authorized",false)
                    .put("full_history_proven",false).put("usb_io_count",0).put("error_class",error==null?"NONE":error.getClass().getSimpleName());
            if(directory!=null)write(new File(directory,"report.json"),report.toString());
            write(new File(getFilesDir(),"history-recovery-v1-report.json"),report.toString());
        }catch(Exception failure){ui.post(()->status.setText("Recovery chưa chứng nhận report bền. Không retry hoặc rollback."));return;}
        ui.post(()->status.setText(result+"\nKhông có quyền B1 hoặc firmware. Giữ tuner rút."));
    }
    private static void write(File file,String text)throws IOException{Tbs5530DurableFile.write(file,text.getBytes(StandardCharsets.UTF_8));}
    @Override protected void onResume(){super.onResume();foreground=true;if(directory!=null&&!terminal)ui.post(tick);}
    @Override protected void onPause(){foreground=false;if(permit!=null)permit.stop();if(!working&&!terminal)finishReport("FOREGROUND_LOST_BEFORE_APPEND",null);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacks(tick);if(permit!=null)permit.stop();super.onDestroy();}
}
