// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

/** Debug-only stop provenance. Messages and exception chains stay local, not in exported reports. */
public final class Tbs5530BootstrapStop extends IOException {
    public enum Kind {
        PRE_NATIVE_ACK_TIMEOUT, AUTHORIZATION_REVOKED, HEARTBEAT_STALE, FOREGROUND_LOST,
        PERMIT_EXPIRED, ADB_IDENTITY_LOST, USB_DETACHED, USER_CANCELLED, HASH_PLAN_MISMATCH,
        JAVA_IO_EXCEPTION, NATIVE_NEGATIVE, NATIVE_SHORT, NATIVE_EXCEPTION, UNKNOWN_IN_FLIGHT,
        CLEANUP_FAILED, ATTEMPT_CONSUMED, AUDIT_FAILED, PREFLIGHT_FAILED, OBSERVATION_FAILED
    }
    public final Kind kind;
    public Tbs5530BootstrapStop(Kind kind, String message) { super(message); this.kind = kind; }
    public Tbs5530BootstrapStop(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
    public static java.util.List<String> sanitizedChain(Throwable error) {
        java.util.List<String> result = new java.util.ArrayList<>();
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        collect(error, result, seen);
        return java.util.Collections.unmodifiableList(result);
    }
    private static void collect(Throwable error, java.util.List<String> result, java.util.Set<Throwable> seen) {
        if (error == null || !seen.add(error)) return;
        result.add(error instanceof Tbs5530BootstrapStop ? ((Tbs5530BootstrapStop) error).kind.name()
                : error instanceof IOException ? "IOException" : "RuntimeException");
        collect(error.getCause(), result, seen);
        for (Throwable suppressed : error.getSuppressed()) collect(suppressed, result, seen);
    }
    public static Kind kindOf(Throwable error) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof Tbs5530BootstrapStop) return ((Tbs5530BootstrapStop) cause).kind;
        }
        return error instanceof IOException ? Kind.JAVA_IO_EXCEPTION : Kind.NATIVE_EXCEPTION;
    }
}
