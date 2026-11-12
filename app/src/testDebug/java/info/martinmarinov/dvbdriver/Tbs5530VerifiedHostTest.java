// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tbs5530VerifiedHostTest {
    @Test public void legacyHostKeepsHistoryAndTabletIsSeparate() throws Exception {
        assertEquals("autonomous-v1", Tbs5530VerifiedHost.historyPrefix(Tbs5530VerifiedHost.profile("GM1900", 30)));
        assertEquals("autonomous-v1-samsung-sm-x926b-api36",
                Tbs5530VerifiedHost.historyPrefix(Tbs5530VerifiedHost.profile("SM-X926B", 36)));
    }
    @Test public void noWildcardModelApiOrUnknownHistory() {
        assertThrows(IOException.class, () -> Tbs5530VerifiedHost.profile("SM-X926B", 35));
        assertThrows(IOException.class, () -> Tbs5530VerifiedHost.profile("GM1900", 36));
        assertThrows(IOException.class, () -> Tbs5530VerifiedHost.profile("SM-X920", 36));
        assertThrows(IOException.class, () -> Tbs5530VerifiedHost.profile(null, 36));
        assertThrows(IOException.class, () -> Tbs5530VerifiedHost.historyPrefix("unknown"));
    }
}
