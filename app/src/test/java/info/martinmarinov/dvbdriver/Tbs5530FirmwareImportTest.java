// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.Assert.*;

public class Tbs5530FirmwareImportTest {
    private static final class TrackedInput extends ByteArrayInputStream {
        boolean closed;
        TrackedInput() { super(new byte[] { 3, 1, 4 }); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    @Test public void passesOriginalStreamAndClosesItAfterImport() {
        TrackedInput input = new TrackedInput();
        assertEquals(Tbs5530FirmwareImport.Result.SUCCESS, Tbs5530FirmwareImport.run(
                () -> input, selected -> {
                    assertSame(input, selected);
                    assertFalse(input.closed);
                    assertEquals(3, selected.read());
                }));
        assertTrue(input.closed);
    }

    @Test public void refusedDocumentPermissionDoesNotReachStore() {
        assertEquals(Tbs5530FirmwareImport.Result.PERMISSION_DENIED, Tbs5530FirmwareImport.run(
                () -> { throw new SecurityException("revoked"); }, input -> fail("Store called")));
    }

    @Test public void missingDocumentDoesNotReachStore() {
        assertEquals(Tbs5530FirmwareImport.Result.UNREADABLE, Tbs5530FirmwareImport.run(
                () -> { throw new IOException("removed document"); }, input -> fail("Store called")));
        assertEquals(Tbs5530FirmwareImport.Result.UNREADABLE, Tbs5530FirmwareImport.run(
                () -> null, input -> fail("Store called")));
    }

    @Test public void providerRuntimeFailureDoesNotEscapeToWorker() {
        assertEquals(Tbs5530FirmwareImport.Result.UNREADABLE, Tbs5530FirmwareImport.run(
                () -> { throw new IllegalStateException("provider unavailable"); }, input -> fail("Store called")));
    }

    @Test public void invalidFirmwareIsReportedAndInputClosed() {
        TrackedInput input = new TrackedInput();
        assertEquals(Tbs5530FirmwareImport.Result.INVALID_OR_IO, Tbs5530FirmwareImport.run(
                () -> input, selected -> { throw new IOException("invalid size or SHA-256"); }));
        assertTrue(input.closed);
    }

    @Test public void revokedPermissionDuringReadIsReportedAndInputClosed() {
        TrackedInput input = new TrackedInput();
        assertEquals(Tbs5530FirmwareImport.Result.PERMISSION_DENIED, Tbs5530FirmwareImport.run(
                () -> input, selected -> { throw new SecurityException("permission revoked during read"); }));
        assertTrue(input.closed);
    }

    @Test public void storageRuntimeFailureIsReportedAndInputClosed() {
        TrackedInput input = new TrackedInput();
        assertEquals(Tbs5530FirmwareImport.Result.INVALID_OR_IO, Tbs5530FirmwareImport.run(
                () -> input, selected -> { throw new IllegalStateException("storage unavailable"); }));
        assertTrue(input.closed);
    }

    @Test public void closeFailureRequiresCheckingSavedCopyRatherThanClaimingSuccess() {
        InputStream input = new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException { throw new IOException("provider close failed"); }
        };
        assertEquals(Tbs5530FirmwareImport.Result.INVALID_OR_IO,
                Tbs5530FirmwareImport.run(() -> input, selected -> {}));
    }
}
