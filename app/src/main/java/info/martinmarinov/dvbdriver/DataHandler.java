/*
 * This is an Android user space port of DVB-T Linux kernel modules.
 *
 * Copyright (C) 2022 by Signalware Ltd <driver at aerialtv.eu>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package info.martinmarinov.dvbdriver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

class DataHandler extends Thread {
    private final DvbFrontendActivity dvbFrontendActivity;
    private final Object recordingLock = new Object();
    private final InputStream is;
    private final boolean collectDiagnostics;

    private volatile boolean recording = false;
    private File file;
    private FileOutputStream fos;
    private int recordedSize = 0;
    private int tsPacketOffset;

    private long readB = 0;
    private long currFreq;
    private long currBandwidth;
    private long recordingStart;
    private Tbs5530RecordingTimeline recordingTimeline;
    private final java.util.List<RecordingWindow> finishedRecordings = new java.util.ArrayList<>();

    private static final class RecordingWindow {
        final File file;
        final long start, end, frequency, bandwidth;
        final Tbs5530RecordingTimeline timeline;
        RecordingWindow(File file, long start, long end, long frequency, long bandwidth, Tbs5530RecordingTimeline timeline) {
            this.file = file; this.start = start; this.end = end;
            this.frequency = frequency; this.bandwidth = bandwidth; this.timeline = timeline;
        }
    }

    void exportTbs5530Diagnostics(info.martinmarinov.usbxfer.NativeUsbfsByteSource.CompletionSnapshot snapshot) throws IOException {
        synchronized (recordingLock) {
            for (RecordingWindow window : finishedRecordings) {
                Tbs5530DumpDiagnostics.write(window.file, window.start, window.end,
                        window.frequency, window.bandwidth, snapshot, window.timeline);
            }
            finishedRecordings.clear();
        }
    }

    DataHandler(DvbFrontendActivity dvbFrontendActivity, InputStream is, boolean collectDiagnostics) {
        this.dvbFrontendActivity = dvbFrontendActivity;
        this.is = is;
        this.collectDiagnostics = collectDiagnostics;
    }

    void reset() {
        readB = 0;
    }

    void setFreqAndBandwidth(long freq, long bandwidth) {
        synchronized (recordingLock) {
            if (recording && (currFreq != freq || currBandwidth != bandwidth)) recordingTimeline.invalidate();
            this.currFreq = freq;
            this.currBandwidth = bandwidth;
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            is.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            long nextUpdate = System.currentTimeMillis() + 1_000;
            byte[] b = new byte[1024];
            while (!isInterrupted()) {

                int read = is.read(b);
                if (read > 0) {
                    synchronized (recordingLock) {
                        int skip = 0;
                        if (collectDiagnostics) {
                            // The demux writes whole TS packets, but reads from its pipe can split them.
                            if (recording && recordedSize == 0 && tsPacketOffset != 0) {
                                skip = Math.min(read, 188 - tsPacketOffset);
                            }
                            tsPacketOffset = (tsPacketOffset + read) % 188;
                        }
                        if (recording && skip < read) {
                            try {
                                fos.write(b, skip, read - skip);
                            } catch (IOException e) {
                                recordingTimeline.invalidate();
                                throw e;
                            }
                            recordedSize += read - skip;
                            recordingTimeline.observe(System.nanoTime(), recordedSize, false);
                        }
                    }

                    readB += read;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        interrupt();
                    }
                    continue;
                }

                if (System.currentTimeMillis() > nextUpdate) {
                    nextUpdate = System.currentTimeMillis() + 1_000;
                    synchronized (recordingLock) {
                        if (recording) {
                            dvbFrontendActivity.announceRecorded(file, recordedSize);
                        } else {
                            dvbFrontendActivity.announceBitRate(readB);
                        }
                    }
                    readB = 0;
                }
            }
        } catch (IOException e) {
            if (!isInterrupted()) {
                dvbFrontendActivity.handleException(e);
                interrupt();
            }
        } finally {
            try {
                stopRecording();
            } catch (IOException e) {
                dvbFrontendActivity.handleException(e);
            }
        }
    }

    boolean isRecording() {
        return recording;
    }

    void startRecording() throws IOException {
        synchronized (recordingLock) {
            if (recording) return;
            if (collectDiagnostics && finishedRecordings.size() >= 16) throw new IOException(dvbFrontendActivity.getString(R.string.recording_diagnostics_full));
            file = TsDumpFileUtils.getFor(dvbFrontendActivity.getApplicationContext(), currFreq, currBandwidth, new Date());
            fos = new FileOutputStream(file, false);
            recordedSize = 0;
            recordingStart = System.nanoTime();
            recordingTimeline = new Tbs5530RecordingTimeline(recordingStart);
            recording = true;
        }
    }

    void stopRecording() throws IOException {
        synchronized (recordingLock) {
            if (!recording) return;
            recording = false;
            long end = System.nanoTime();
            recordingTimeline.observe(end, recordedSize, true);
            try {
                fos.flush();
                fos.getFD().sync();
            } catch (IOException e) {
                recordingTimeline.invalidate();
                throw e;
            } finally {
                try {
                    fos.close();
                } catch (IOException e) {
                    recordingTimeline.invalidate();
                    throw e;
                } finally {
                    fos = null;
                    if (collectDiagnostics) finishedRecordings.add(new RecordingWindow(file, recordingStart, end, currFreq, currBandwidth, recordingTimeline));
                    file = null;
                }
            }
        }
    }
}
