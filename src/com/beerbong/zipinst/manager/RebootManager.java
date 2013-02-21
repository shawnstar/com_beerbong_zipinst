/*
 * Copyright (C) 2013 ZipInstaller
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beerbong.zipinst.manager;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.beerbong.zipinst.R;
import com.beerbong.zipinst.ui.UI;
import com.beerbong.zipinst.ui.UIListener;
import com.beerbong.zipinst.util.Constants;
import com.beerbong.zipinst.util.StoredItems;
import com.beerbong.zipinst.util.ZipItem;

/**
 * @author Yamil Ghazi Kantelinen
 * @version 1.0
 */

public class RebootManager extends Manager implements UIListener {

    private int mSelectedBackup;
    private int mWipeDataIndex;
    private int mWipeCachesIndex;

    protected RebootManager(Context context) {
        super(context);

        UI.getInstance().addUIListener(this);
    }

    public void onButtonClicked(int id) {

        if (id == R.id.install_now) {
            ManagerFactory.getFileManager().checkFilesAndMd5(this);
        }
    }

    @Override
    public void onZipItemClicked(ZipItem item) {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
    }

    @Override
    public void onOptionsItemSelected(MenuItem item) {
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    public void showBackupDialog(Context context) {
        showBackupDialog(context, true, false, false);
    }

    public void showRestoreDialog(Context context) {

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.alert_restore_title);

        final String backupFolder = ManagerFactory.getRecoveryManager().getBackupDir(false);
        final String[] backups = ManagerFactory.getRecoveryManager().getBackupList();
        mSelectedBackup = backups.length > 0 ? 0 : -1;

        alert.setSingleChoiceItems(backups, mSelectedBackup, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                mSelectedBackup = which;
            }
        });

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                if (mSelectedBackup >= 0) {
                    reboot(false, false, null, backupFolder + backups[mSelectedBackup]);
                }
            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();

    }

    public void simpleReboot() {
        try {

            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());

            os.writeBytes("rm -f /cache/recovery/command\n");
            os.writeBytes("rm -f /cache/recovery/extendedcommand\n");
            os.writeBytes("rm -f /cache/recovery/openrecoveryscript\n");

            os.writeBytes("reboot recovery\n");

            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();

            if (Constants.isSystemApp(mContext)) {
                ((PowerManager) mContext.getSystemService(Activity.POWER_SERVICE))
                        .reboot("recovery");
            } else {
                Runtime.getRuntime().exec("/system/bin/reboot recovery");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showBackupDialog(Context context, boolean removePreferences,
            final boolean wipeData, final boolean wipeCaches) {

        if (removePreferences)
            UI.getInstance().removeAllItems();

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.alert_backup_title);
        alert.setMessage(R.string.alert_backup_message);

        final EditText input = new EditText(mContext);
        alert.setView(input);
        input.setText(Constants.getDateAndTime());
        input.selectAll();

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                String text = input.getText().toString();
                text = text.replace(" ", "");

                reboot(wipeData, wipeCaches, text, null);
            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    protected void showRebootDialog() {

        if (StoredItems.size() == 0)
            return;

        AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
        alert.setTitle(R.string.alert_reboot_title);

        final PreferencesManager pManager = ManagerFactory.getPreferencesManager();
        List<String> wipeOpts = new ArrayList<String>();

        mWipeDataIndex = 0;
        mWipeCachesIndex = 1;
        if (pManager.isShowOption("BACKUP")) {
            mWipeDataIndex++;
            mWipeCachesIndex++;
            wipeOpts.add(mContext.getResources().getString(R.string.backup));
        }
        if (pManager.isShowOption("WIPEDATA")) {
            wipeOpts.add(mContext.getResources().getString(R.string.wipe_data));
        }
        if (pManager.isShowOption("WIPECACHES")) {
            wipeOpts.add(mContext.getResources().getString(R.string.wipe_caches));
        }

        final boolean[] wipeOptions = new boolean[wipeOpts.size()];

        alert.setMultiChoiceItems(wipeOpts.toArray(new String[wipeOpts.size()]), wipeOptions,
                new DialogInterface.OnMultiChoiceClickListener() {

                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        wipeOptions[which] = isChecked;
                    }
                });

        alert.setPositiveButton(R.string.alert_reboot_now, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                if (pManager.isShowOption("BACKUP") && wipeOptions[0]) {
                    showBackupDialog(mContext, false, wipeOptions[mWipeDataIndex],
                            wipeOptions[mWipeCachesIndex]);
                } else {
                    reboot(wipeOptions[mWipeDataIndex], wipeOptions[mWipeCachesIndex], null, null);
                }

            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    private void reboot(boolean wipeData, boolean wipeCaches, String backupFolder, String restore) {
        try {

            RecoveryManager manager = ManagerFactory.getRecoveryManager();

            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());

            os.writeBytes("rm -f /cache/recovery/command\n");
            os.writeBytes("rm -f /cache/recovery/extendedcommand\n");
            os.writeBytes("rm -f /cache/recovery/openrecoveryscript\n");

            String file = manager.getCommandsFile();

            String[] commands = manager.getCommands(wipeData, wipeCaches, backupFolder, restore);
            if (commands != null) {
                int size = commands.length, i = 0;
                for (; i < size; i++) {
                    os.writeBytes("echo '" + commands[i] + "' >> /cache/recovery/" + file + "\n");
                }
            }

            os.writeBytes("reboot recovery\n");

            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();

            if (Constants.isSystemApp(mContext)) {
                ((PowerManager) mContext.getSystemService(Activity.POWER_SERVICE))
                        .reboot("recovery");
            } else {
                Runtime.getRuntime().exec("/system/bin/reboot recovery");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}