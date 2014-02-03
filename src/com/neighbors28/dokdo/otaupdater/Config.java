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

import java.io.File;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;

public class Config {
    public static final String GCM_SENDER_ID = "1068482628480";
    public static final String GCM_REGISTER_URL = "https://www.otaupdatecenter.pro/pages/regdevice2.php";
    public static final String PULL_URL = "https://www.otaupdatecenter.pro/pages/romupdate.php";
    public static final String DONATE_URL = "https://www.otaupdatecenter.pro/?page=paypal_donate";
    public static final String OTA_ID_PROP = "otaupdater.otaid";
    public static final String OTA_VER_PROP = "otaupdater.otaver";
    public static final String OTA_DATE_PROP = "otaupdater.otatime";
    public static final String OTA_SD_PATH_OS_PROP = "otaupdater.sdcard.os";
    public static final String OTA_SD_PATH_RECOVERY_PROP = "otaupdater.sdcard.recovery";
    public static final String OTA_REBOOT_CMD_PROP = "otaupdater.rebootcmd";
    public static final String OTA_NOFLASH_PROP = "otaupdater.noflash";

    public static final int WAKE_TIMEOUT = 30000;

    public static final String DL_PATH = "/" + Utils.getOSSdPath() + "/DokdoOTA/download/";
    public static final File DL_PATH_FILE = new File(Config.DL_PATH);

    static {
        if (DL_PATH_FILE.exists()) {
            if (!DL_PATH_FILE.isDirectory()) {
                DL_PATH_FILE.delete();
                DL_PATH_FILE.mkdirs();
            }
        } else {
            DL_PATH_FILE.mkdirs();
        }
    }

    private boolean showNotif = true;
    private boolean ignoredDataWarn = false;

    private int lastVersion = -1;
    private String lastDevice = null;
    private String lastRomID = null;

    private int curVersion = -1;
    private String curDevice = null;
    private String curRomID = null;

    private RomInfo storedUpdate = null;

    private static final String PREFS_NAME = "prefs";
    private final SharedPreferences PREFS;

    private Config(Context ctx) {
        PREFS = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, 0);

        showNotif = PREFS.getBoolean("showNotif", showNotif);
        ignoredDataWarn = PREFS.getBoolean("ignoredDataWarn", ignoredDataWarn);

        lastVersion = PREFS.getInt("version", lastVersion);
        lastDevice = PREFS.getString("device", lastDevice);
        lastRomID = PREFS.getString("romid", lastRomID);

        if (PREFS.contains("info_rom")) {
            storedUpdate = new RomInfo(PREFS.getString("info_rom", null),
                    PREFS.getString("info_version", null),
                    PREFS.getString("info_changelog", null),
                    PREFS.getString("info_url", null),
                    PREFS.getString("info_md5", null),
                    Utils.parseDate(PREFS.getString("info_date", null)));
        }

        try {
            curVersion = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
        }
        curDevice = android.os.Build.DEVICE.toLowerCase();
        curRomID = Utils.getRomID();

        if (!upToDate()) {
            setIgnoredDataWarn(false);
        }
    }
    private static Config instance = null;
    public static synchronized Config getInstance(Context ctx) {
        if (instance == null) instance = new Config(ctx);
        return instance;
    }

    public boolean getShowNotif() {
        return showNotif;
    }

    public void setShowNotif(boolean showNotif) {
        this.showNotif = showNotif;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean("showNotif", showNotif);
            editor.commit();
        }
    }

    public boolean getIgnoredDataWarn() {
        return ignoredDataWarn;
    }

    public void setIgnoredDataWarn(boolean ignored) {
        this.ignoredDataWarn = ignored;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean("ignoredDataWarn", ignored);
            editor.commit();
        }
    }

    public int getLastVersion() {
        return lastVersion;
    }

    public String getLastDevice() {
        return lastDevice;
    }

    public String getLastRomID() {
        return lastRomID;
    }

    public void setValuesToCurrent() {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putInt("version", curVersion);
            editor.putString("device", curDevice);
            editor.putString("romid", curRomID);
            editor.commit();
        }
    }

    public boolean upToDate() {
        if (lastDevice == null) return false;
        if (lastRomID == null) return false;
        if (curRomID == null) return false;
        return curVersion == lastVersion && curDevice.equals(lastDevice) && curRomID.equals(lastRomID);
    }

    public boolean hasStoredUpdate() {
        return storedUpdate != null;
    }

    public RomInfo getStoredUpdate() {
        return storedUpdate;
    }

    public void storeUpdate(RomInfo info) {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putString("info_rom", info.romName);
            editor.putString("info_version", info.version);
            editor.putString("info_changelog", info.changelog);
            editor.putString("info_url", info.url);
            editor.putString("info_md5", info.md5);
            editor.putString("info_date", Utils.formatDate(info.date));
            editor.commit();
        }
    }

    public void clearStoredUpdate() {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.remove("info_rom");
            editor.remove("info_version");
            editor.remove("info_changelog");
            editor.remove("info_url");
            editor.remove("info_md5");
            editor.remove("info_date");
            editor.commit();
        }
    }
}
