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

import org.onesocialweb.client.android.activities.InboxActivity;
import org.onesocialweb.client.android.service.AndroidOswService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class OswBroadcastReceiver extends BroadcastReceiver {

	public static final String RECONNECTION_ATTEMPT = "attempt_to_reconnect";
	public static final String RELOGIN = "relogin";
	public static final String SHUTTING_DOWN = "shutting_down";
	public static final String START_SERVICE_BOOT_COMPLETED = "start_service_boot_completed";
	public static final String NO_START_SERVICE_BOOT_COMPLETED = "no_start_service_boot_completed";
	public static final String AWAY_TO_XA = "away_to_xa";
	public static Boolean SERVICE_CREATED = false;
	public static Boolean INBOX_ACTIVITY_CREATED  = false;
	@Override
	public void onReceive(final Context context, Intent intent) {

		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean active = preferences.getBoolean("checkbox_preference", false);

		if (!TextUtils.isEmpty(intent.getAction())) {
			//android.intent.action.ACTION_SHUTDOWN
			if(intent.getAction().equals(intent.ACTION_SHUTDOWN))
			{
				context.startService(new Intent(SHUTTING_DOWN, null, context,
							AndroidOswService.class));
			}
			// android.intent.action.BOOT_COMPLETED
			if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
				if (active) {
					Log.d(Onesocialweb.LOGTAG, "BroadCastReceiver: BOOT COMPLETED and preference checked");
					// Start the AndroidOswService
					context.startService(new Intent(START_SERVICE_BOOT_COMPLETED, null, context,
							AndroidOswService.class));

				} else {
					Log.d(Onesocialweb.LOGTAG, "BroadCastReceiver: BOOT COMPLETED but preference unchecked");
				}
			}

			// reconnect_after_xmppclosed
			else if (intent.getAction().equals(AndroidOswService.ACTION_RECONNECT_AFTER_XMPPCLOSED) ) {
				Log.d(Onesocialweb.LOGTAG, "BroadCastReceiver: xmpp closed event");
				
				// Start the AndroidOswService
				context.startService(new Intent(RECONNECTION_ATTEMPT, null, context, AndroidOswService.class));
			}

			// android.net.conn.CONNECTIVITY_CHANGE
			else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION) && SERVICE_CREATED) {
				// Manage Network changes
				ConnectivityManager connectivityManager = (ConnectivityManager) context
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();

				if (activeNetInfo != null) {
					if (activeNetInfo.isConnected()) {
						Log.d(Onesocialweb.LOGTAG, "BroadCastReceiver: network event, connected");
						// Start the AndroidOswService
						context.startService(new Intent(RECONNECTION_ATTEMPT, null, context, AndroidOswService.class));
					}
				}
			}

			// action_away_to_xa
			else if (intent.getAction().equals(OswBroadcastReceiver.AWAY_TO_XA)) {
				Log.d(Onesocialweb.LOGTAG, "BroadCastReceiver: change from away to xa");
				// Start the AndroidOswService
				context.startService(new Intent(AWAY_TO_XA, null, context, AndroidOswService.class));
			}
		}
	}
}
