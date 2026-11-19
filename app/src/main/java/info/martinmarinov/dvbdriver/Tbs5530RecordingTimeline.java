// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

/** Bounded consumer observations for choosing an auditable post-start TS byte range. */
final class Tbs5530RecordingTimeline {
    static final int CAPACITY = 4096;
    private final long[] times = new long[CAPACITY];
    private final long[] offsets = new long[CAPACITY];
    private int count;
    private boolean invalid, truncated;
    private long lastTime, lastBytes;

    Tbs5530RecordingTimeline(long startNanos) {
        lastTime = startNanos;
        add(startNanos, 0);
    }

    void observe(long timeNanos, long bytes, boolean force) {
        if (timeNanos < lastTime || bytes < lastBytes || bytes < 0) invalid = true;
        lastTime = timeNanos;
        lastBytes = bytes;
        if (force || count == 0 || timeNanos - times[count - 1] >= 100_000_000L) add(timeNanos, bytes);
    }

    private void add(long time, long offset) {
        if (count == CAPACITY) { truncated = true; return; }
        times[count] = time;
        offsets[count++] = offset;
    }

    void invalidate() { invalid = true; }
    int size() { return count; }
    long time(int index) { return times[index]; }
    long offset(int index) { return offsets[index]; }
    boolean complete() { return !invalid && !truncated && count >= 2; }
}
