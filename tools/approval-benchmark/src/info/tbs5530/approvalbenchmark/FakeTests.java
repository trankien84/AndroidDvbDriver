// SPDX-License-Identifier: GPL-2.0-or-later
package info.tbs5530.approvalbenchmark;

/** Runs only in-memory, fake-clock tests. No instrumentation, Context, preferences or USB. */
public final class FakeTests {
    private FakeTests() { }
    public static void main(String[] args) {
        org.junit.runner.Result result = org.junit.runner.JUnitCore.runClasses(
                info.martinmarinov.dvbdriver.Tbs5530ApprovalDeterministicTest.class);
        for (org.junit.runner.notification.Failure failure : result.getFailures())
            System.out.println(failure.getTrace());
        System.out.println("FAKE_TESTS run=" + result.getRunCount() + " failed=" + result.getFailureCount());
        if (!result.wasSuccessful()) System.exit(1);
    }
}
