// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

/** Protocol constants shared by the debug guard and the isolated Android benchmark. */
final class Tbs5530ApprovalProtocol {
    static final String SHA256 = "b97cf208bb586f706b26aa5450de6bb97375a3e3eabe833cddb0ae3a4f69d0dc";
    static final int VERSION = 2;
    static final int ORDINALS = 132;
    static final long ACK_MS = 1500;
    static final long HEARTBEAT_MS = 2000;
    static final long SESSION_MS = 300000;
    static final long START_BUDGET_MS = 240000;
    private Tbs5530ApprovalProtocol() { }
}
