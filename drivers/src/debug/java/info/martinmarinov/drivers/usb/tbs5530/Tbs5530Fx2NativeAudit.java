// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.drivers.usb.tbs5530;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Each row comes from the actual debug JNI ioctl result, never from the app mirror. */
final class Tbs5530Fx2NativeAudit implements Tbs5530ReloadAndroidManager.Audit {
    private final File target;
    private final String phase;
    private int ordinal;
    Tbs5530Fx2NativeAudit(File directory, String phase) throws IOException {
        if (!directory.isDirectory() || !"RELOAD".equals(phase)) throw new IOException("Only the reload JNI path provides a native audit");
        this.phase = phase; target = new File(directory, phase.toLowerCase(java.util.Locale.ROOT) + "-a0-native.jsonl");
        if (!target.createNewFile()) throw new IOException("Native audit already exists");
        try (FileOutputStream out = new FileOutputStream(target)) { out.getFD().sync(); }
    }
    public synchronized void recorded(Tbs5530Fx2Reload.Boundary b) throws IOException {
        if (b.ordinal != ordinal + 1 || b.ordinal > 132) throw new IOException("Native audit ordinal changed");
        String row = "{\"schema\":\"TBS5530-A0-NATIVE/v1\",\"phase\":\"" + phase
                + "\",\"boundary\":\"USBDEVFS_CONTROL_IOCTL\",\"clock\":\"CLOCK_BOOTTIME\",\"ordinal\":" + b.ordinal
                + ",\"request_type\":64,\"request\":160,\"index\":0,\"timeout_ms\":2000,\"value\":" + b.value
                + ",\"requested_length\":" + b.length + ",\"result\":" + b.result + ",\"errno\":" + b.errno
                + ",\"before_ns\":" + b.beforeNanos + ",\"after_ns\":" + b.afterNanos + ",\"payload_omitted\":true}\n";
        try (FileOutputStream out = new FileOutputStream(target, true)) { out.write(row.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        ordinal++;
    }
}
