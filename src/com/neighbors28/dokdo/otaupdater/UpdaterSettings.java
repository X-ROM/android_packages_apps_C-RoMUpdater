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

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

@SuppressWarnings("deprecation")
public class UpdaterSettings extends PreferenceActivity implements OnPreferenceClickListener {
    private Config cfg;
    
	private CheckBoxPreference showNotifPref;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		cfg = Config.getInstance(getApplicationContext());
		
		addPreferencesFromResource(R.xml.settings_main);
		
		showNotifPref = (CheckBoxPreference) findPreference("show_notif");
		showNotifPref.setChecked(cfg.getShowNotif());
		showNotifPref.setOnPreferenceClickListener(this);
	}
	
	@Override
    public boolean onPreferenceClick(Preference preference) {
	    if (preference == showNotifPref) {
	        cfg.setShowNotif(showNotifPref.isChecked());
	        return true;
	    }
	    return false;
    }
}
