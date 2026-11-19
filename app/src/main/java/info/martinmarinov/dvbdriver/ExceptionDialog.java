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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatDialogFragment;

import info.martinmarinov.drivers.DvbException;
import info.martinmarinov.dvbservice.tools.StackTraceSerializer;

public class ExceptionDialog extends AppCompatDialogFragment {
    private static final String DIALOG_TAG = ExceptionDialog.class.getSimpleName();

    private static final String ARGS_EXC = "argsExc";
    private static final String ARGS_LAST_DEV = "argsLastDev";

    public static void showOneInstanceOnly(FragmentManager fragmentManager, Exception e, String lastDeviceDebugString) {
        if (fragmentManager.isStateSaved() || fragmentManager.isDestroyed()
                || fragmentManager.findFragmentByTag(DIALOG_TAG) != null) return;
        ExceptionDialog dialog = new ExceptionDialog();

        Bundle args = new Bundle();
        args.putSerializable(ARGS_EXC, e);
        args.putSerializable(ARGS_LAST_DEV, lastDeviceDebugString);

        dialog.setArguments(args);
        dialog.showNow(fragmentManager, DIALOG_TAG);
    }

    private Exception e;
    private String lastDeviceDebugString;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        e = (Exception) args.getSerializable(ARGS_EXC);
        lastDeviceDebugString = args.getString(ARGS_LAST_DEV);
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
        final String summary = e instanceof DvbException
                && ((DvbException) e).getErrorCode() == DvbException.ErrorCode.DEVICE_DISCONNECTED
                ? getString(R.string.tuner_disconnected) : e.getLocalizedMessage();
        final String message = summary == null ? getString(android.R.string.dialog_alert_title) : summary;
        return new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setMessage(BuildConfig.DEBUG ? withDetails(message, e) : message)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(android.R.string.dialog_alert_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                })
                .setNeutralButton(R.string.send_email, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendEmail(
                                new String[] { getString(R.string.email_feedback_address) },
                                getString(R.string.email_subject),
                                getString(R.string.email_body, withDetails(message, e), getConstants())
                        );
                    }
                })
                .create();
    }

    private String getConstants() {
        StringBuilder res = new StringBuilder();

        res.append("Last Device: ").append(lastDeviceDebugString).append('\n');
        res.append("Build.MANUFACTURER: ").append(Build.MANUFACTURER).append('\n');
        res.append("Build.MODEL: ").append(Build.MODEL).append('\n');
        res.append("Build.PRODUCT: ").append(Build.PRODUCT).append('\n');
        res.append("Build.VERSION.SDK_INT: ").append(Build.VERSION.SDK_INT).append('\n');
        res.append("Build.VERSION.RELEASE: ").append(Build.VERSION.RELEASE).append('\n');

        try {
            PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            res.append("Driver versionName: ").append(packageInfo.versionName).append('\n');
            res.append("Driver versionCode: ").append(packageInfo.versionCode).append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return res.toString();
    }

    private static String withDetails(String summary, Exception e) {
        return summary + "\n\n" + StackTraceSerializer.serialize(e);
    }

    private void sendEmail(String[] addresses, String subject, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), R.string.cannot_send_email, Toast.LENGTH_LONG).show();
        }
    }
}
