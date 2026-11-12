// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import info.martinmarinov.drivers.usb.tbs5530.Tbs5530ReloadLedger;

/** Deterministic first-initialization plan, never an authorization or a hardware plan. */
final class Tbs5530ReloadInitPlan {
    static final String PROTOCOL = "TBS5530-RELOAD-INITIALIZE/v1";
    static final String PREFIX = "reload-init-v1";
    static final String HISTORY = "b6b5935a2af02fd125565ed24694d4fc4a0cb2616c8445aeaecae94cb829e7fb";
    static final String STORE = Tbs5530ReloadLedger.hash(Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.HEADER));
    static final String ANCHOR = Tbs5530ReloadLedger.hash(Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.ANCHOR_HEADER));
    static final String MARKER = Tbs5530ReloadLedger.hash(Tbs5530ReloadLedger.bytes(Tbs5530ReloadLedger.INITIALIZED_HEADER));
    static final String TOMBSTONE = "fx2-reload-v1-init.consumed.json";
    static String transcript() {
        return PROTOCOL + "|host=SM-X926B:36|history=" + HISTORY + "|historyBytes=996|historyRecords=12"
                + "|store=" + STORE + "|storeBytes=138|anchor=" + ANCHOR + "|anchorBytes=94|marker=" + MARKER
                + "|markerBytes=99|order=APPROVAL_TOMBSTONE,MARKER,JOURNAL,ANCHOR|O_EXCL|FSYNC_FILE_AND_DIRECTORY"
                + "|NO_TUNER|NO_DEVICE_OPEN|hardwareAuthorized=false|retry=0|reset=false|rearm=false";
    }
    static String sha256() { return Tbs5530ReloadLedger.hash(Tbs5530ReloadLedger.bytes(transcript())); }
    private Tbs5530ReloadInitPlan() { }
}
