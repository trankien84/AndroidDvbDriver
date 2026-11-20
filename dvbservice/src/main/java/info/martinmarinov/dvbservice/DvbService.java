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

package info.martinmarinov.dvbservice;

import static info.martinmarinov.drivers.DvbException.ErrorCode.CANNOT_OPEN_USB;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import info.martinmarinov.drivers.DeviceFilter;
import info.martinmarinov.drivers.DvbDevice;
import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.drivers.usb.DvbUsbDeviceRegistry;
import info.martinmarinov.dvbservice.tools.InetAddressTools;
import info.martinmarinov.dvbservice.tools.TsDumpFileUtils;

public class DvbService extends Service {
    private static final String TAG = DvbService.class.getSimpleName();
    private final static int ONGOING_NOTIFICATION_ID = 743713489; // random id

    public static final String BROADCAST_ACTION = "info.martinmarinov.dvbservice.DvbService.BROADCAST";

    private final static String DEVICE_FILTER = "DeviceFilter";
    private final static String STATUS_MESSAGE = "StatusMessage";

    private static final String REQUEST_ID = "RequestId";
    private static OpenRequest reservation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private OpenRequest active;

    static final class OpenRequest {
        final String id = UUID.randomUUID().toString();
        volatile boolean cancelled;
        volatile Thread worker;
        volatile DvbServer server;

        void cancel() {
            cancelled = true;
            Thread running = worker;
            if (running != null) running.interrupt();
            DvbServer current = server;
            if (current != null) current.cancel();
        }
    }

    @Override
    public void onDestroy() {
        if (active != null) active.cancel();
        stopForeground(true);
        super.onDestroy();
    }

    static OpenRequest reserve(String busyMessage) throws DvbException {
        if (reservation != null) throw new DvbException(DvbException.ErrorCode.DEVICE_BUSY, busyMessage);
        return reservation = new OpenRequest();
    }

    static void release(OpenRequest request) {
        if (reservation == request) reservation = null;
    }

    static OpenRequest requestOpen(Activity activity, DeviceFilter deviceFilter) throws DvbException {
        OpenRequest request = reserve(activity.getString(R.string.device_busy));
        Intent intent = new Intent(activity, DvbService.class)
                .putExtra(DEVICE_FILTER, deviceFilter)
                .putExtra(REQUEST_ID, request.id);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.startForegroundService(intent);
            else activity.startService(intent);
        } catch (RuntimeException e) {
            release(request);
            throw new DvbException(CANNOT_OPEN_USB, e);
        }
        return request;
    }

    static StatusMessage parseMessage(Intent intent) {
        return (StatusMessage) intent.getSerializableExtra(STATUS_MESSAGE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        OpenRequest request = reservation;
        if (intent == null || request == null || !request.id.equals(intent.getStringExtra(REQUEST_ID))) {
            if (active == null) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (active != null) return START_NOT_STICKY;
        startForeground(false);
        active = request;
        DeviceFilter filter = (DeviceFilter) intent.getSerializableExtra(DEVICE_FILTER);
        request.worker = new Thread(() -> runSession(request, filter), "DvbServiceSession");
        request.worker.start();
        return START_NOT_STICKY;
    }

    private void runSession(OpenRequest request, DeviceFilter filter) {
        boolean ready = false;
        DvbDevice device = null;
        try {
            if (request.cancelled) return;
            device = getDeviceFromFilter(filter);
            try (DvbServer server = new DvbServer(device)) {
                device = null;
                request.server = server;
                if (request.cancelled) return;
                DvbServerPorts ports = server.bind(InetAddressTools.getLocalLoopback());
                server.open();
                if (request.cancelled) return;
                startForeground(filter.getVendorId() == 0x734c && filter.getProductId() == 0x5530);
                if (request.cancelled) return;
                broadcastStatus(new StatusMessage(request.id, null, ports, filter));
                ready = true;
                server.serve();
            }
        } catch (Exception e) {
            if (!request.cancelled) {
                if (!ready) broadcastStatus(new StatusMessage(request.id, e, null, filter));
                else Log.i(TAG, "Session ended: " + (e instanceof DvbException
                        ? ((DvbException) e).getErrorCode().name() : e.getClass().getSimpleName()));
            }
        } finally {
            try {
                if (device != null) device.close();
            } catch (java.io.IOException e) {
                Log.w(TAG, "Failed to close unopened device", e);
            } finally {
                request.server = null;
                main.post(() -> {
                    release(request);
                    if (active == request) {
                        active = null;
                        stopSelf();
                    }
                });
            }
        }
    }

    private void startForeground(boolean connectedDevice) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String NOTIFICATION_CHANNEL_ID = "DvbDriver";

        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "DVB-T driver notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );

            // Configure the notification channel.
            notificationChannel.setDescription("When DVB-T driver operates");
            notificationChannel.enableVibration(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.driver_description))
                .setSmallIcon(R.drawable.ic_notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder = builder
                    .setPriority(Notification.PRIORITY_MAX);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if (connectedDevice) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            startForeground(ONGOING_NOTIFICATION_ID, builder.build(), type);
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, builder.build());
        }
    }

    private DvbDevice getDeviceFromFilter(DeviceFilter deviceFilter) throws DvbException {
        List<DvbDevice> dvbDevices = DvbUsbDeviceRegistry.getUsbDvbDevices(this, deviceFilter);
        if (dvbDevices.isEmpty()) dvbDevices.addAll(TsDumpFileUtils.getDevicesForAllRecordings(this));

        DvbDevice selected = null;
        for (DvbDevice dvbDevice : dvbDevices) {
            if (selected == null && dvbDevice.getDeviceFilter().equals(deviceFilter)) {
                selected = dvbDevice;
            } else {
                try {
                    dvbDevice.close();
                } catch (java.io.IOException e) {
                    Log.w(TAG, "Unused device cleanup failed", e);
                }
            }
        }
        if (selected != null) return selected;
        throw new DvbException(CANNOT_OPEN_USB, getString(R.string.device_no_longer_available));
    }

    private void broadcastStatus(StatusMessage statusMessage) {
        Intent intent = new Intent(BROADCAST_ACTION)
                        .putExtra(STATUS_MESSAGE, statusMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static class StatusMessage implements Serializable {
        final String requestId;
        final Exception exception;
        final DvbServerPorts serverAddresses;
        final DeviceFilter deviceFilter;

        private StatusMessage(String requestId, Exception exception, DvbServerPorts serverAddresses, DeviceFilter deviceFilter) {
            this.requestId = requestId;
            this.exception = exception;
            this.serverAddresses = serverAddresses;
            this.deviceFilter = deviceFilter;
        }
    }
}
