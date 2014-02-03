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

import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService {
		
    public GCMIntentService() {
        super(Config.GCM_SENDER_ID);
    }

    @Override
    protected void onError(Context ctx, String errorID) {
        Log.e("OTA::GCMError", errorID);
    }

    @Override
    protected void onMessage(Context ctx, Intent payload) {
        final Config cfg = Config.getInstance(getApplicationContext());
        RomInfo info = RomInfo.fromIntent(payload);

        if (!Utils.isUpdate(info)) {
            Log.v("OTA::GCM", "got GCM message, not update");
            cfg.clearStoredUpdate();
            return;
        }

        cfg.storeUpdate(info);
        if (cfg.getShowNotif()) {
            Log.v("OTA::GCM", "got GCM message");
            Utils.showUpdateNotif(ctx, info);
        } else {
            Log.v("OTA::GCM", "got GCM message, notif not shown");
        }
    }

    @Override
    protected void onRegistered(Context ctx, String regID) {
        Log.v("OTA::GCMRegister", "GCM registered - ID=" + regID);
        ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("do", "register"));
        params.add(new BasicNameValuePair("reg_id", regID));
        params.add(new BasicNameValuePair("device", android.os.Build.DEVICE.toLowerCase()));
        params.add(new BasicNameValuePair("rom_id", Utils.getRomID()));
        params.add(new BasicNameValuePair("device_id", ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId()));

        try {
            HttpClient http = new DefaultHttpClient();
            HttpPost req = new HttpPost(Config.GCM_REGISTER_URL);
            req.setEntity(new UrlEncodedFormEntity(params));

            HttpResponse r = http.execute(req);
            int status = r.getStatusLine().getStatusCode();
            HttpEntity e = r.getEntity();
            if (status == 200) {
                String data = EntityUtils.toString(e);
                if (data.length() == 0) {
                    Log.w("OTA::GCMRegister", "No response to registration");
                    return;
                }
                JSONObject json = new JSONObject(data);

                if (json.length() == 0) {
                    Log.w("OTA::GCMRegister", "Empty response to registration");
                    return;
                }

                if (json.has("error")) {
                    Log.e("OTA::GCMRegister", json.getString("error"));
                    return;
                }

                RomInfo info = new RomInfo(
                        json.getString("rom"),
                        json.getString("version"),
                        json.getString("changelog"),
                        json.getString("url"),
                        json.getString("md5"),
                        Utils.parseDate(json.getString("date")));

                final Config cfg = Config.getInstance(getApplicationContext());
                if (Utils.isUpdate(info)) {
                    cfg.storeUpdate(info);
                    if (cfg.getShowNotif()) {
                    	Utils.showUpdateNotif(getApplicationContext(), info);
                    } else {
                        Log.v("OTA::GCMRegister", "got update response, notif not shown");
                    }
                } else {
                    cfg.clearStoredUpdate();
                }
            } else {
                if (e != null) e.consumeContent();
                Log.w("OTA::GCMRegister", "registration response " + status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onUnregistered(Context ctx, String regID) {
        Log.v("OTA::GCMRegister", "GCM unregistered - ID=" + regID);
        ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("do", "unregister"));
        params.add(new BasicNameValuePair("reg_id", regID));

        try {
            HttpClient http = new DefaultHttpClient();
            HttpPost req = new HttpPost(Config.GCM_REGISTER_URL);
            req.setEntity(new UrlEncodedFormEntity(params));
            HttpResponse resp = http.execute(req);
            if (resp.getStatusLine().getStatusCode() != 200) {
                Log.w("OTA::GCMRegister", "unregistration response non-200");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
