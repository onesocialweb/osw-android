/*
 *  Copyright 2010 Vodafone Group Services Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *    
 */
package org.onesocialweb.client.android;

import java.util.ArrayList;

import org.onesocialweb.client.android.activities.AccountSettings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class OswPreferences extends PreferenceActivity{
	
	// Get sharedPreferences
	public static SharedPreferences mPrefs;
	public static final String PREFERENCES = "preferences";
	public static final String NOTIFICATION_VIBRATE = "notification_vibration";
	public static final String NOTIFICATION_FLASHING = "notification_flash_led";
	public static final String NOTIFICATION_RINGTONE = "notification_ringtone";
	public static final String NOTIFICATION_LED_COLOR = "pref_key_notification_led_color";
	public static final String NOTIFICATION_VIBRATE_PATTERN = "pref_key_notification_vibrate_pattern";

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		//Set up the intent to launch the activity to modify the user account
		Intent intent = new Intent(this, AccountSettings.class);
		getPreferenceScreen().findPreference("log_preference").setIntent(intent);
		
		//Intent to open a browser with the link for the About preference
		Intent i = new Intent(Intent.ACTION_VIEW); 
		Uri u = Uri.parse("http://onesocialweb.org/index.html"); 
		i.setData(u); 
		getPreferenceScreen().findPreference("about_preference").setIntent(i);
		
		mPrefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE);

	}

	@Override
	protected void onStop() {
		super.onStop();
	}
	
	  /**
     * Parse the user provided custom vibrate pattern into a long[]
     */
    public static long[] parseVibratePattern(String stringPattern) {
      ArrayList<Long> arrayListPattern = new ArrayList<Long>();
      Long l;
      String[] splitPattern = stringPattern.split(",");
      int VIBRATE_PATTERN_MAX_SECONDS = 60000;
      int VIBRATE_PATTERN_MAX_PATTERN = 100;
 
      for (int i = 0; i < splitPattern.length; i++) {
        try {
          l = Long.parseLong(splitPattern[i].trim());
        } catch (NumberFormatException e) {
          return null;
        }
        if (l > VIBRATE_PATTERN_MAX_SECONDS) {
          return null;
        }
        arrayListPattern.add(l);
      }
 
      // TODO: can i just cast the whole ArrayList into long[]?
      int size = arrayListPattern.size();
      if (size > 0 && size < VIBRATE_PATTERN_MAX_PATTERN) {
        long[] pattern = new long[size];
        for (int i = 0; i < pattern.length; i++) {
          pattern[i] = arrayListPattern.get(i);
        }
        return pattern;
      }
 
      return null;
    }

}
