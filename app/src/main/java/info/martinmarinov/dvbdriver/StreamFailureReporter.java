// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

final class StreamFailureReporter {
    interface Receiver { void accept(Exception error); }

    private final Receiver primary, secondary;
    private boolean closing, reported;

    StreamFailureReporter(Receiver primary, Receiver secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    synchronized void beginClosing() { closing = true; }

    synchronized void whileOpen(Runnable action) {
        if (!closing) action.run();
    }

    synchronized void report(Exception error) {
        if (closing || reported) {
            secondary.accept(error);
        } else {
            reported = true;
            primary.accept(error);
        }
    }
}
