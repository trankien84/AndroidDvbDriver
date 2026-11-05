// SPDX-License-Identifier: GPL-2.0-or-later
package info.tbs5530.approvalbenchmark;

/** Standalone app_process namespace; not an installed app or instrumentation runner. */
public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        info.martinmarinov.dvbdriver.IsolatedApprovalBenchmark.runIsolatedBenchmark(args,
                new info.martinmarinov.dvbdriver.Tbs5530ApprovalSession.Clock() {
                    public long now() { return android.os.SystemClock.elapsedRealtime(); }
                    public void pause(long millis) throws InterruptedException { Thread.sleep(millis); }
                });
    }
}
