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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Date;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StatFs;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gcm.GCMRegistrar;
import com.neighbors28.dokdo.otaupdater.FetchRomInfoTask.RomInfoListener;

final class Slugify {

    public static String slugify(String input) {
        if (input == null || input.length() == 0) return "";
        String toReturn = normalize(input);
        toReturn = toReturn.replace(" ", "-");
        toReturn = toReturn.toLowerCase();
        return toReturn;
    }

    @TargetApi(9)
    private static String normalize(String input) {
        if (input == null || input.length() == 0) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            input = Normalizer.normalize(input, Form.NFD);
        }
        return input.replaceAll("[^\\p{ASCII}]","");
    }
}

public class OTAUpdaterActivity extends PreferenceActivity {
    protected static final String NOTIF_ACTION = "com.otaupdater.action.NOTIF_ACTION";

    private boolean dialogFromNotif = false;
    private boolean checkOnResume = false;
    private Config cfg;

    private Preference availUpdatePref;
    private FetchRomInfoTask fetchTask = null;

    private DownloadTask dlTask;

    /** Called when the activity is first created. */
    @Override
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cfg = Config.getInstance(getApplicationContext());

        if (!Utils.isROMSupported()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.alert_unsupported_title);
            alert.setMessage(R.string.alert_unsupported_message);
            alert.setCancelable(false);
            alert.setNegativeButton(R.string.alert_exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
            alert.setPositiveButton(R.string.alert_ignore, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            alert.create().show();

            if (Utils.marketAvailable(this)) {
                GCMRegistrar.checkDevice(getApplicationContext());
                GCMRegistrar.checkManifest(getApplicationContext());
                final String regId = GCMRegistrar.getRegistrationId(getApplicationContext());
                if (regId.length() != 0) {
                    GCMRegistrar.unregister(getApplicationContext());
                }
            }
        } else {
            if (Utils.marketAvailable(this)) {
                GCMRegistrar.checkDevice(getApplicationContext());
                GCMRegistrar.checkManifest(getApplicationContext());
                final String regId = GCMRegistrar.getRegistrationId(getApplicationContext());
                if (regId.length() != 0) {
                    if (cfg.upToDate()) {
                        Log.v("OTA::GCMRegister", "Already registered");
                    } else {
                        Log.v("OTA::GCMRegister", "Already registered, out-of-date, reregistering");
                        GCMRegistrar.unregister(getApplicationContext());
                        GCMRegistrar.register(getApplicationContext(), Config.GCM_SENDER_ID);
                        cfg.setValuesToCurrent();
                        Log.v("OTA::GCMRegister", "GCM registered");
                    }
                } else {
                    GCMRegistrar.register(getApplicationContext(), Config.GCM_SENDER_ID);
                    Log.v("OTA::GCMRegister", "GCM registered");
                }
            } else {
                UpdateCheckReceiver.setAlarm(getApplicationContext());
            }

            addPreferencesFromResource(R.xml.main);

            String romVersion = Utils.getOtaVersion();
            if (romVersion == null) romVersion = android.os.Build.ID;
            Date romDate = Utils.getOtaDate();
            if (romDate != null) {
                romVersion += " (" + DateFormat.getDateTimeInstance().format(romDate) + ")";
            }

            final Preference device = findPreference("device_view");
            device.setSummary(android.os.Build.MODEL);
            final Preference rom = findPreference("rom_view");
            rom.setSummary(android.os.Build.DISPLAY);
            final Preference version = findPreference("version_view");
            version.setSummary(romVersion);
            availUpdatePref = findPreference("avail_updates");

            Object savedInstance = getLastNonConfigurationInstance();
            if (savedInstance != null && savedInstance instanceof DownloadTask) {
                dialogFromNotif = true;
                dlTask = (DownloadTask) savedInstance;

                final ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setTitle(R.string.alert_downloading);
                progressDialog.setMessage("Changelog: " + dlTask.getRomInfo().changelog);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setCancelable(false);
                progressDialog.setProgress(0);
                progressDialog.setButton(Dialog.BUTTON_NEGATIVE, getString(R.string.alert_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        progressDialog.dismiss();
                        dlTask.cancel(true);
                    }
                });
                dlTask.attach(progressDialog);
                progressDialog.show();
            } else {
                Intent i = getIntent();
                if (i != null && i.getAction().equals(NOTIF_ACTION)) {
                    if (Utils.dataAvailable(getApplicationContext())) {
                        dialogFromNotif = true;
                        showUpdateDialog(RomInfo.fromIntent(i));
                    } else {
                        checkOnResume = true;
                    }
                } else {
                    checkOnResume = true;
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean connected = ni != null && ni.isConnected();
        if ((!connected || ni.getType() == ConnectivityManager.TYPE_MOBILE) && !cfg.getIgnoredDataWarn() && !dialogFromNotif && Utils.isROMSupported()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(connected ? R.string.alert_nowifi_title : R.string.alert_nodata_title);
            alert.setMessage(connected ? R.string.alert_nowifi_message : R.string.alert_nodata_message);
            alert.setPositiveButton(R.string.alert_wifi_settings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    cfg.setIgnoredDataWarn(false);
                    checkOnResume = true;
                }
            });
            alert.setNeutralButton(R.string.alert_ignore, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    cfg.setIgnoredDataWarn(true);

                    if (Utils.dataAvailable(getApplicationContext())) {
                        Intent i = getIntent();
                        if (i.getAction().equals(NOTIF_ACTION)) {
                            dialogFromNotif = true;
                            showUpdateDialog(RomInfo.fromIntent(i));
                        } else {
                            checkForRomUpdates();
                        }
                        checkOnResume = false;
                    }
                }
            });
            alert.setNegativeButton(R.string.alert_exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
            alert.create().show();
        } else if (checkOnResume) {
            if (Utils.dataAvailable(getApplicationContext())) {
                Intent i = getIntent();
                if (i.getAction().equals(NOTIF_ACTION)) {
                    dialogFromNotif = true;
                    showUpdateDialog(RomInfo.fromIntent(i));
                } else {
                    checkForRomUpdates();
                }
                checkOnResume = false;
            }
        }
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            if (dlTask != null && !dlTask.isDone()) dlTask.cancel(true);
        }
        if (fetchTask != null) fetchTask.cancel(true);
        super.onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (dlTask == null) return null;

        dlTask.detach();
        if (dlTask.isDone()) return null;

        return dlTask;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == availUpdatePref) {
            if (fetchTask == null) checkForRomUpdates();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        switch (item.getItemId()) {
        case R.id.view:
            i = new Intent(this, ListFilesActivity.class);
            startActivity(i);
            break;
        case R.id.settings:
            i = new Intent(this, UpdaterSettings.class);
            startActivity(i);
            break;
        case R.id.refresh:
            checkForRomUpdates();
            break;
        }
        return true;
    }

    private void checkForRomUpdates() {
        if (fetchTask != null) return;
        if (!Utils.isROMSupported()) return;
        fetchTask = new FetchRomInfoTask(this, new RomInfoListener() {
            @Override
            public void onStartLoading() {
            }
            @Override
            public void onLoaded(RomInfo info) {
                fetchTask = null;
                if (info == null) {
                    availUpdatePref.setSummary(getString(R.string.main_updates_error, "Unknown error"));
                    Toast.makeText(OTAUpdaterActivity.this, R.string.toast_fetch_error, Toast.LENGTH_SHORT).show();
                } else if (Utils.isUpdate(info)) {
                    showUpdateDialog(info);
                } else {
                    availUpdatePref.setSummary(R.string.main_updates_none);
                    Toast.makeText(OTAUpdaterActivity.this, R.string.toast_no_updates, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onError(String error) {
                fetchTask = null;
                availUpdatePref.setSummary(getString(R.string.main_updates_error, error));
                Toast.makeText(OTAUpdaterActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
        fetchTask.execute();
    }

    private void showUpdateDialog(final RomInfo info) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.alert_update_title);
        alert.setMessage(info.changelog);
        availUpdatePref.setSummary(getString(R.string.main_updates_new, info.romName, info.version));

        alert.setPositiveButton(R.string.alert_download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                final ProgressDialog progressDialog = new ProgressDialog(OTAUpdaterActivity.this);
                progressDialog.setTitle(R.string.alert_downloading);
                progressDialog.setMessage(getString(R.string.alert_unsupported_message));
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setCancelable(false);
                progressDialog.setProgress(0);

                final File file = new File(Config.DL_PATH_FILE, Slugify.slugify(info.romName + "_ota_" + "package_" + info.version) + ".zip");
                dlTask = new DownloadTask(progressDialog, info, file);

                progressDialog.setButton(Dialog.BUTTON_NEGATIVE, getString(R.string.alert_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        progressDialog.dismiss();
                        dlTask.cancel(true);
                    }
                });

                dlTask.execute();
            }
        });

        alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    private static class DownloadTask extends AsyncTask<Void, Integer, Integer> {
        private int scale = 1048576;

        private ProgressDialog dialog = null;
        private Context ctx = null;
        private RomInfo info;
        private File destFile;
        private final WakeLock wl;

        private boolean done = false;

        public DownloadTask(ProgressDialog dialog, RomInfo info, File destFile) {
            this.attach(dialog);

            this.info = info;
            this.destFile = destFile;

            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, OTAUpdaterActivity.class.getName());
        }

        public void attach(ProgressDialog dialog) {
            this.dialog = dialog;
            this.ctx = dialog.getContext();
        }

        public void detach() {
            if (this.dialog != null) this.dialog.dismiss();
            this.dialog = null;
            this.ctx = null;
        }

        public boolean isDone() {
            return done;
        }

        public RomInfo getRomInfo() {
            return info;
        }

        @Override
        protected void onPreExecute() {
            done = false;
            dialog.show();
            wl.acquire();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (destFile.exists()) {
                Log.v("OTA::Download", "Found old zip, checking md5");

                InputStream is = null;
                try {
                    is = new FileInputStream(destFile);
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    byte[] data = new byte[4096];
                    int nRead = -1;
                    while ((nRead = is.read(data)) != -1) {
                        digest.update(data, 0, nRead);
                    }
                    String oldMd5 = Utils.byteArrToStr(digest.digest());
                    Log.v("OTA::Download", "old zip md5: " + oldMd5);
                    if (!info.md5.equalsIgnoreCase(oldMd5)) {
                        destFile.delete();
                    } else {
                        return 0;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    destFile.delete();
                } finally {
                    if (is != null) {
                        try { is.close(); }
                        catch (Exception e) { }
                    }
                }
            }

            InputStream is = null;
            OutputStream os = null;
            try {
                URL getUrl = new URL(info.url);
                Log.v("OTA::Download", "downloading from: " + getUrl);
                Log.d("OTA::Download", "downloading to: " + destFile.getAbsolutePath());

                URLConnection conn = getUrl.openConnection();

 		// Start
                if(getUrl.toString().contains("goo.im")){

                    // do a dl then wait for timer

               	    conn.connect();
        	    Log.v("OTA::Download", "Waiting out timer");
                    publishProgress(-1); // pending on dialog

                    // dl the catch page then wait
                    is = new BufferedInputStream(conn.getInputStream());
                    os = new FileOutputStream(destFile);

                    // get the timer page all 15
                    byte[] buf = new byte[4096];
                    int nRead = -1;
                    while ((nRead = is.read(buf)) != -1) {
                        if (this.isCancelled()) break;
                        os.write(buf, 0, nRead);
                    }

                    // got hold page pause

       		    try {
        	    	Thread.sleep(10500); // pause the async task for 10.5 seconds
            	    } catch (InterruptedException e) {
            		// don't panic and reset the link
            	    }

		    Log.v("OTA::Download", "Timer Complete, Continuing with File Download");

		    getUrl = new URL(info.url);
		    conn = getUrl.openConnection();

                }

                // end do your thing as normal

                final int lengthOfFile = conn.getContentLength();

                StatFs stat = new StatFs(Config.DL_PATH);
                long availSpace = ((long) stat.getAvailableBlocks()) * ((long) stat.getBlockSize());
                if (lengthOfFile >= availSpace) {
                    destFile.delete();
                    return 3;
                }

                if (lengthOfFile < 10000000) scale = 1024; //if less than 10 mb, scale using kb
                publishProgress(0, lengthOfFile);

                MessageDigest digest = MessageDigest.getInstance("MD5");

                conn.connect();
                is = new BufferedInputStream(conn.getInputStream());
                os = new FileOutputStream(destFile);

                byte[] buf = new byte[4096];
                int nRead = -1;
                int totalRead = 0;
                while ((nRead = is.read(buf)) != -1) {
                    if (this.isCancelled()) break;
                    os.write(buf, 0, nRead);
                    digest.update(buf, 0, nRead);
                    totalRead += nRead;
                    publishProgress(totalRead, lengthOfFile);
                }

                if (isCancelled()) {
                    destFile.delete();
                    return 2;
                }

                String dlMd5 = Utils.byteArrToStr(digest.digest());
                Log.v("OTA::Download", "downloaded md5: " + dlMd5);
                if (!info.md5.equalsIgnoreCase(dlMd5)) {
                    Log.w("OTA::Download", "downloaded md5 doesn't match " + info.md5);
                    destFile.delete();
                    return 1;
                }

                return 0;
            } catch (Exception e) {
                e.printStackTrace();
                destFile.delete();
            } finally {
                if (is != null) {
                    try { is.close(); }
                    catch (Exception e) { }
                }
                if (os != null) {
                    try { os.flush(); os.close(); }
                    catch (Exception e) { }
                }
            }
            return -1;
        }

        @Override
        protected void onCancelled(Integer result) {
            done = true;
            dialog.dismiss();
            wl.release();
            wl.acquire(Config.WAKE_TIMEOUT);

            if (result == null) {
                Toast.makeText(ctx, R.string.toast_download_error, Toast.LENGTH_SHORT).show();
                return;
            }

            switch (result) {
            case 0:
                break;
            case 1:
                Toast.makeText(ctx, R.string.toast_download_md5_mismatch, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                Toast.makeText(ctx, R.string.toast_download_interrupted, Toast.LENGTH_SHORT).show();
                break;
            case 3:
                Toast.makeText(ctx, R.string.toast_download_nospace, Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(ctx, R.string.toast_download_error, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            done = true;
            dialog.dismiss();
            wl.release();
            wl.acquire(Config.WAKE_TIMEOUT);

            switch (result) {
            case 0:
                ListFilesActivity.installFileDialog(ctx, destFile);
                break;
            case 1:
                Toast.makeText(ctx, R.string.toast_download_md5_mismatch, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                Toast.makeText(ctx, R.string.toast_download_interrupted, Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(ctx, R.string.toast_download_error, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (dialog == null) return;

            if (values[0] == -1) { // i'm not sure yet please hold
            	dialog.setIndeterminate(true);
            	return;
            }

            dialog.setIndeterminate(false); // make sure dialog is ALWAYS sure for progress

            if (values.length == 0) return;
            dialog.setProgress(values[0] / scale);
            if (values.length == 1) return;
            dialog.setMax(values[1] / scale);
        }
    }
}
