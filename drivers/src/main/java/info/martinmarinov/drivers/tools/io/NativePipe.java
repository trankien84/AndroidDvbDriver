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

package info.martinmarinov.drivers.tools.io;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NativePipe implements Closeable {
    private final ParcelFileDescriptor.AutoCloseInputStream inputStream;
    private final ParcelFileDescriptor writeEnd;
    private final StructPollfd[] legacyPoll;
    private final AtomicBoolean closed = new AtomicBoolean();

    public NativePipe() {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            FileDescriptor fd = pipe[1].getFileDescriptor();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    int flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0);
                    Os.fcntlInt(fd, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                    legacyPoll = null;
                } else {
                    StructPollfd poll = new StructPollfd();
                    poll.fd = fd;
                    poll.events = (short) OsConstants.POLLOUT;
                    legacyPoll = new StructPollfd[] {poll};
                }
            } catch (ErrnoException e) {
                try { pipe[0].close(); } finally { pipe[1].close(); }
                throw new IOException("Cannot set up cancellable TS pipe", e);
            }
            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
            writeEnd = pipe[1];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void write(byte[] buf, int offset, int length) throws IOException {
        // Before API 30 use bounded poll and a single writer with chunks no larger than PIPE_BUF.
        while (length > 0 && !Thread.currentThread().isInterrupted()) {
            if (closed.get()) throw new IOException("TS pipe is closed");
            try {
                if (legacyPoll != null) {
                    if (Os.poll(legacyPoll, 10) == 0) continue;
                    if ((legacyPoll[0].revents & OsConstants.POLLOUT) == 0) throw new IOException("TS pipe is not writable");
                    if (Thread.currentThread().isInterrupted()) break;
                }
                int written = Os.write(writeEnd.getFileDescriptor(), buf, offset,
                        legacyPoll == null ? length : Math.min(length, 512));
                offset += written;
                length -= written;
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.EAGAIN) throw new IOException("Cannot write TS pipe", e);
                try { Thread.sleep(1); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
        if (length > 0) throw new InterruptedIOException("TS pipe write cancelled");
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        try { writeEnd.close(); }
        finally { inputStream.close(); }
    }
}
