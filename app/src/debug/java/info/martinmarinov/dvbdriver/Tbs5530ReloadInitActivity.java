// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import info.martinmarinov.drivers.usb.tbs5530.*;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Debug-only first initialization. Launch publishes a challenge, never grants USB or store writes. */
public final class Tbs5530ReloadInitActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Tbs5530AutonomousLaunch launches = new Tbs5530AutonomousLaunch();
    private volatile boolean foreground, terminal, working;
    private File directory;
    private String session, launch;
    private long created;
    private Tbs5530ReloadInitPermit permit;
    private Tbs5530ReloadInitJournal journal;
    private TextView status;
    private final Runnable tick = new Runnable() { public void run() {
        if (terminal || working) return;
        try {
            if (!foreground || SystemClock.elapsedRealtime() - created >= Tbs5530ReloadInitPermit.MAX_MS)
                throw new IOException("Initialization wait expired or background");
            if (new File(directory, "grant.json").isFile()) {
                requireNoTuner(); permit.accept(); working = true;
                status.setText("Đang tạo store lần đầu theo grant đã khóa; giữ tuner rút.");
                new Thread(() -> apply(), "tbs5530-reload-initialize").start(); return;
            }
        } catch (IOException | RuntimeException error) { finishReport("REFUSED_BEFORE_CREATE", error); return; }
        ui.postDelayed(this, 100);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24,24,24,24);
        status = new TextView(this); status.setText("Khởi tạo reload store lần đầu, không tuner. Chờ exact immutable grant từ host; không có quyền firmware hoặc B2."); layout.addView(status);
        Button stop = new Button(this); stop.setText("STOP — dừng khởi tạo"); stop.setOnClickListener(v -> {
            if (permit != null) permit.stop(); if (!working) finishReport("STOPPED_BEFORE_CREATE", null);
        }); layout.addView(stop); setContentView(layout); begin(getIntent());
    }
    private void requireNoTuner() throws IOException {
        if (!Tbs5530AutonomousAndroidAccess.manager(this).devices().isEmpty()) throw new IOException("Initialization requires empty USB inventory; never open a tuner");
    }
    private void begin(Intent intent) {
        try {
            launches.begin(intent.getStringExtra("launch_token"));
            terminal = false; working = false; directory = null; session = null; permit = null; journal = null;
            launch = intent.getStringExtra("launch_token");
            if (!Tbs5530AutonomousLaunch.valid(launch) || !"SM-X926B".equals(Build.MODEL) || Build.VERSION.SDK_INT != 36)
                throw new IOException("Exact Samsung/API36 initialization launch required");
            requireNoTuner(); Tbs5530ReloadStore.requireNeverInitialized(this);
            journal = new Tbs5530ReloadInitJournal(getNoBackupFilesDir());
            if (journal.consumed()) throw new IOException("Initialization tombstone exists; no retry/rearm");
            byte[] history = Tbs5530HistoryRecoveryStore.readSnapshot(this, "autonomous-v1-samsung-sm-x926b-api36");
            if (history.length != 996 || Tbs5530HistoryRecovery.validate(history).size() != 12
                    || !Tbs5530ReloadInitPlan.HISTORY.equals(Tbs5530ReloadLedger.hash(history))) throw new IOException("Initialization history witness mismatch");
            session = UUID.randomUUID().toString(); created = SystemClock.elapsedRealtime();
            directory = new File(getFilesDir(), Tbs5530ReloadInitPlan.PREFIX + "-" + session);
            if (!directory.mkdir()) throw new IOException("Fresh initialization namespace required");
            JSONObject challenge = new JSONObject().put("protocol", Tbs5530ReloadInitPlan.PROTOCOL).put("session", session)
                    .put("launch_token", launch).put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT)
                    .put("boot_count", Settings.Global.getInt(getContentResolver(), "boot_count", -1))
                    .put("apk_sha256", Tbs5530AutonomousSession.installedApkHash(this)).put("source_git_sha", BuildConfig.SOURCE_GIT_SHA)
                    .put("plan_sha256", Tbs5530ReloadInitPlan.sha256()).put("history_sha256", Tbs5530ReloadInitPlan.HISTORY)
                    .put("history_bytes", 996).put("history_records", 12).put("store_sha256", Tbs5530ReloadInitPlan.STORE)
                    .put("anchor_sha256", Tbs5530ReloadInitPlan.ANCHOR).put("marker_sha256", Tbs5530ReloadInitPlan.MARKER)
                    .put("store_bytes", 138).put("anchor_bytes", 94).put("marker_bytes", 99).put("created_ms", created);
            if (challenge.getInt("boot_count") < 0) throw new IOException("Unverified boot identity");
            permit = new Tbs5530ReloadInitPermit(challenge, new Tbs5530ReloadInitPermit.Port() {
                public long now() { return SystemClock.elapsedRealtime(); }
                public boolean foreground() { return foreground && !terminal; }
                public String grant() throws IOException { return Tbs5530AutonomousSession.read(new File(directory,"grant.json"),16384); }
                public String heartbeat() throws IOException { return Tbs5530AutonomousSession.read(new File(directory,"heartbeat.json"),2048); }
                public boolean consumed() throws IOException { return journal.consumed(); }
                public void consume(String record) throws IOException { requireNoTuner(); journal.consume(record); }
                public void requireConsumed(String record) throws IOException { requireNoTuner(); journal.requireRecord(record); }
            });
            write(new File(directory,"challenge.json"),challenge.toString());
            write(new File(getFilesDir(), Tbs5530ReloadInitPlan.PREFIX + "-challenge.json"),challenge.toString());
            setIntent(new Intent(intent)); if (foreground) ui.post(tick);
        } catch (Exception error) { finishReport("REFUSED_BEFORE_CREATE",error); }
    }
    private void apply() {
        try {
            permit.requireCurrent(); requireNoTuner();
            Tbs5530ReloadStore.initializeApproved(this, permit);
            permit.requireCurrent(); requireNoTuner();
            finishReport("INITIALIZATION_VERIFIED_NO_HARDWARE_AUTHORIZATION", null);
        } catch (IOException | RuntimeException error) { finishReport("REFUSED_OR_CREATE_UNKNOWN_NO_RETRY", error); }
    }
    private synchronized void finishReport(String result, Throwable error) {
        if (terminal) return;
        terminal = true; working = false; ui.removeCallbacks(tick); launches.runtimeCleanup(true);
        try {
            boolean consumed = journal != null && journal.consumed();
            String storeHash = "UNAVAILABLE", anchorHash = "UNAVAILABLE", markerHash = "UNAVAILABLE";
            try {
                Tbs5530ReloadStore store = new Tbs5530ReloadStore(this);
                storeHash = Tbs5530ReloadLedger.hash(store.read()); anchorHash = Tbs5530ReloadLedger.hash(store.anchor());
                markerHash = Tbs5530ReloadLedger.hash(Tbs5530ReloadStore.readExisting(new File(getNoBackupFilesDir(),"fx2-reload-v1.initialized")));
            } catch (IOException ignored) { }
            JSONObject report = new JSONObject().put("protocol", Tbs5530ReloadInitPlan.PROTOCOL)
                    .put("session",session == null ? JSONObject.NULL : session).put("launch_token", launch == null ? JSONObject.NULL : launch)
                    .put("terminal",true).put("result",result).put("plan_sha256",Tbs5530ReloadInitPlan.sha256()).put("consumed",consumed)
                    .put("store_sha256",storeHash).put("anchor_sha256",anchorHash).put("marker_sha256",markerHash)
                    .put("hardware_authorized",false).put("usb_io_count",0).put("error_class",error == null ? "NONE" : error.getClass().getSimpleName());
            if (directory != null) write(new File(directory,"report.json"),report.toString());
            write(new File(getFilesDir(),Tbs5530ReloadInitPlan.PREFIX + "-report.json"),report.toString());
        } catch (Exception failure) { ui.post(() -> status.setText("Không chứng nhận report bền. Không retry/reset store.")); return; }
        ui.post(() -> status.setText(result + "\nKhông có quyền firmware hoặc B2. Giữ tuner rút."));
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String token = intent.getStringExtra("launch_token"); if (launches.matches(token)) return;
        try {
            launches.requireFresh(token);
            if (!terminal || working || !launches.cleaned()) throw new IOException("Initialization still active");
            begin(intent);
        } catch (IOException error) { status.setText("Không thay workflow đang chạy hoặc chưa cleanup; không cấp grant mới."); }
    }
    private static void write(File file, String value) throws IOException { Tbs5530DurableFile.write(file,value.getBytes(StandardCharsets.UTF_8)); }
    @Override protected void onResume() { super.onResume(); foreground = true; if (directory != null && !terminal) ui.post(tick); }
    @Override protected void onPause() {
        foreground = false; if (permit != null) permit.stop();
        if (!working && !terminal) finishReport("FOREGROUND_LOST_BEFORE_CREATE",null); super.onPause();
    }
    @Override protected void onDestroy() { ui.removeCallbacks(tick); if (permit != null) permit.stop(); super.onDestroy(); }
}
