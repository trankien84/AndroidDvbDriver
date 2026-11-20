// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import android.content.Context;
import java.io.IOException;
import java.io.InterruptedIOException;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.R;

/** Typed failure provenance survives transport wrappers; UI text never parses English root messages. */
final class Tbs5530ProductionFailure extends IOException {
    enum Kind { BUSY, DRAIN_UNCONFIRMED, PERMISSION, PERMISSION_TIMEOUT, HANDOFF_TIMEOUT, FIRMWARE, DESCRIPTOR, BOOTSTRAP, RELOAD, REMOVED, INTERRUPTED }
    final Kind kind;
    Tbs5530ProductionFailure(Kind kind, String detail) { super(detail); this.kind = kind; }
    Tbs5530ProductionFailure(Kind kind, String detail, Throwable cause) { super(detail,cause); this.kind = kind; }

    static Kind classify(Throwable failure) {
        Kind found = null;
        for (int depth=0; failure != null && depth<32; depth++, failure=failure.getCause()) {
            if (failure instanceof InterruptedException || failure instanceof InterruptedIOException) return Kind.INTERRUPTED;
            if (failure instanceof Tbs5530ProductionFailure) found=((Tbs5530ProductionFailure)failure).kind;
        }
        return found;
    }

    static DvbException localized(Context context, DvbException.ErrorCode fallback, int fallbackText, Throwable cause) {
        Kind kind=classify(cause);
        DvbException.ErrorCode code=fallback;
        int text=fallbackText;
        if (kind != null) switch(kind) {
            case BUSY: code=DvbException.ErrorCode.DEVICE_BUSY; text=R.string.tbs5530_busy; break;
            case DRAIN_UNCONFIRMED: code=DvbException.ErrorCode.HARDWARE_EXCEPTION; text=R.string.tbs5530_drain_unconfirmed; break;
            case PERMISSION: code=DvbException.ErrorCode.USB_PERMISSION_DENIED; text=R.string.tbs5530_permission_denied; break;
            case PERMISSION_TIMEOUT: code=DvbException.ErrorCode.USB_PERMISSION_DENIED; text=R.string.tbs5530_permission_timeout; break;
            case HANDOFF_TIMEOUT: code=DvbException.ErrorCode.CANNOT_OPEN_USB; text=R.string.tbs5530_handoff_timeout; break;
            case FIRMWARE: code=DvbException.ErrorCode.FIRMWARE_UNAVAILABLE; text=R.string.tbs5530_firmware_failed; break;
            case DESCRIPTOR: code=DvbException.ErrorCode.DVB_DEVICE_UNSUPPORTED; text=R.string.tbs5530_descriptor_failed; break;
            case BOOTSTRAP: code=DvbException.ErrorCode.HARDWARE_EXCEPTION; text=R.string.tbs5530_bootstrap_failed; break;
            case RELOAD: code=DvbException.ErrorCode.HARDWARE_EXCEPTION; text=R.string.tbs5530_reload_failed; break;
            case REMOVED: code=DvbException.ErrorCode.DEVICE_DISCONNECTED; text=R.string.tbs5530_removed; break;
            case INTERRUPTED: code=DvbException.ErrorCode.IO_EXCEPTION; text=R.string.tbs5530_interrupted; break;
            default: throw new AssertionError(kind);
        }
        return new DvbException(code,context.getString(text),cause == null ? null
                : cause instanceof Exception ? (Exception)cause : new IOException(cause));
    }
}
