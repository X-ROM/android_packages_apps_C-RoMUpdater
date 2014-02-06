/*
 * Copyright (C) 2013-2014 Dokdo Project - neighbors28
 * Copyright (C) 2012 OTA Update Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may only use this file in compliance with the license and provided you are not associated with or are in co-operation anyone by the name 'X Vanderpoel'.
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

package com.neighbors28.dokdo.otaupdater;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

public class ListFilesActivity extends ListActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private ArrayAdapter<String> fileListAdapter;
    private ArrayList<String> fileList = new ArrayList<String>();
    private ArrayList<String> pathList = new ArrayList<String>();

    public static final int DL_PATH_LEN = Config.DL_PATH.length();

    private void listFiles(File dir) {
        fileList.clear();
        pathList.clear();
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            fileList.add(file.getPath().substring(DL_PATH_LEN));
            pathList.add(file.getPath());
        }
        fileListAdapter.notifyDataSetChanged();
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Config.DL_PATH_FILE.mkdirs(); //just in case
        String extState = Environment.getExternalStorageState();
        if ((!extState.equals(Environment.MEDIA_MOUNTED) && !extState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) || !Config.DL_PATH_FILE.exists()) {
            Toast.makeText(this, extState.equals(Environment.MEDIA_SHARED) ? R.string.toast_nosd_shared : R.string.toast_nosd_error, Toast.LENGTH_LONG).show();
            finish();
        }

        fileListAdapter = new ArrayAdapter<String>(this, R.layout.row, R.id.filename, fileList);
        setListAdapter(fileListAdapter);
        listFiles(Config.DL_PATH_FILE);

        this.getListView().setOnItemClickListener(this);
        this.getListView().setOnItemLongClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.prune:
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.alert_prune_title);
            alert.setCancelable(true);
            alert.setItems(R.array.file_ages, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    long maxAge = 2592000000l; //1 month
                    switch (which) {
                    case 0:
                        maxAge = 604800000l; //1 week
                        break;
                    case 1:
                        maxAge = 1209600000l; //2 weeks
                        break;
                    case 2:
                        maxAge = 2592000000l; //1 month
                        break;
                    case 3:
                        maxAge = 7776000000l; //3 months
                        break;
                    case 4:
                        maxAge = 15552000000l; //6 months
                        break;
                    }
                    pruneFiles(maxAge);
                    listFiles(Config.DL_PATH_FILE);
                }
            });

            alert.create().show();
            break;
        case R.id.list_refresh:
            listFiles(Config.DL_PATH_FILE);
            break;
        }
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
        return true;
    }

    private void processItem(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_options_title);
        builder.setItems(R.array.file_actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                String path = pathList.get(pos);
                final File file = new File(path);

                AlertDialog.Builder alert;
                switch (which) {
                case 0:
                    installFileDialog(ListFilesActivity.this, file);
                    break;
                case 1:
                    alert = new AlertDialog.Builder(ListFilesActivity.this);
                    alert.setTitle(R.string.alert_rename_title);
                    alert.setMessage(R.string.alert_rename_message);

                    final EditText input = new EditText(ListFilesActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton(R.string.alert_rename, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();

                            String newName = input.getText().toString();
                            if (!newName.endsWith(".zip")) newName += ".zip";
                            File newFile = new File(Config.DL_PATH_FILE, newName);
                            boolean renamed = file.renameTo(newFile);

                            if (renamed) {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename_error, Toast.LENGTH_SHORT).show();
                            }

                            listFiles(Config.DL_PATH_FILE);
                            return;
                        }
                    });
                    alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alert.create().show();
                    break;
                case 2:
                    boolean deleted = file.delete();

                    if (deleted) {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete_error, Toast.LENGTH_SHORT).show();
                    }

                    listFiles(Config.DL_PATH_FILE);
                    break;
                }
            }
        });

        builder.setCancelable(true);
        builder.create().show();
    }

    protected static void installFileDialog(final Context ctx, final File file) {
        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        alert.setTitle(R.string.alert_install_title);
        alert.setMessage(R.string.alert_install_message);
        alert.setPositiveButton(R.string.alert_install, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    String rebootCmd = Utils.getRebootCmd();
                    if (!rebootCmd.equals("$$NULL$$")) {
                    if (rebootCmd.endsWith(".sh")) {
                        os.writeBytes("sh " + rebootCmd + "\n");
                    } else {
                        os.writeBytes(rebootCmd + "\n");
                        }
                    }
                    ((PowerManager) ctx.getSystemService(POWER_SERVICE)).reboot("recovery");
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                }
            });
            alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.create().show();
    }

    private void pruneFiles(long maxAge) {
        File dir = new File(Config.DL_PATH);
        File[] files = dir.listFiles();

        boolean success = true;
        for (File f : files) {
            final Long lastmodified = f.lastModified();
            if (lastmodified + maxAge < System.currentTimeMillis()) {
                if (!f.delete()) success = false;
            }
        }

        if (success) {
            Toast.makeText(getApplicationContext(), R.string.toast_prune, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), R.string.toast_prune_error, Toast.LENGTH_SHORT).show();
        }
    }
}