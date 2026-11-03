// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.IOException;

/** Injected control-transfer boundary. No Android or native implementation is supplied. */
public interface Tbs5530ControlIo {
    int controlTransfer(int requestType, int request, int value, int index,
                        byte[] buffer, int offset, int length, int timeoutMillis) throws IOException;

    enum FailureKind { SHORT_TRANSFER, TIMEOUT, IO_FAILURE, CANCELLED, SESSION_UNAVAILABLE }

    final class TransferException extends IOException {
        private final FailureKind kind;

        public TransferException(FailureKind kind, String message) {
            this(kind, message, null);
        }

        public TransferException(FailureKind kind, String message, Throwable cause) {
            super(message, cause);
            if (kind == null) throw new IllegalArgumentException("Failure kind is required");
            this.kind = kind;
        }

        public FailureKind getKind() { return kind; }
    }
}
