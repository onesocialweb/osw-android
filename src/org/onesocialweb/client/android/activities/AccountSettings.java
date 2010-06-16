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
package org.onesocialweb.client.android.activities;

import org.onesocialweb.client.android.OswBroadcastReceiver;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.client.exception.ConnectionRequired;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class AccountSettings extends Activity {

	private AndroidOswService service = null;
	
	private Dialog dialog;

	// Get sharedPreferences
	public static SharedPreferences mPrefs;

	public static final String PREFERENCES = "preferences";
	public static final String USERNAME = "username";
	public static final String PASSWORD = "password";
	public static final String STARTBOOTCOMPLETE = "checkbox_preference";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		// Assign the view so that we can find the view objects by ID
		setContentView(R.layout.account);

		final EditText usernameEdit = (EditText) findViewById(R.id.username);
		final EditText passwordEdit = (EditText) findViewById(R.id.password);
		final Button login = (Button) findViewById(R.id.login_button);
		final Button cancel = (Button) findViewById(R.id.cancel_button);

		mPrefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE);

		// Prefill with the previous username and password
		usernameEdit.setText(mPrefs.getString(USERNAME, ""));
		passwordEdit.setText(mPrefs.getString(PASSWORD, ""));

		login.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				mPrefs.edit().putString(USERNAME, usernameEdit.getText().toString()).commit();
				mPrefs.edit().putString(PASSWORD, passwordEdit.getText().toString()).commit();

				String userNamePref = usernameEdit.getText().toString();
				String passwordPref = passwordEdit.getText().toString();

				if (userNamePref == "empty" || passwordPref == "empty" || userNamePref.length() == 0
						|| passwordPref.length() == 0) {
					Toast.makeText(AccountSettings.this, R.string.error_username_password_empty,
							Toast.LENGTH_SHORT).show();
				}
				// The user must login using the full jid
				else if (userNamePref.indexOf("@") == -1) {
					Toast.makeText(AccountSettings.this, R.string.supply_username_domain, Toast.LENGTH_LONG)
							.show();
				} else { 
						new Task().execute();	
				}
				return;
			}
		});

		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
				//if the previous activity is the InboxActivity return
				if(OswBroadcastReceiver.SERVICE_CREATED){
					// Return to InboxActivity with the same user account
					startActivity(new Intent(AccountSettings.this, InboxActivity.class));				
				}
				//else return to home
				else{
					Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
					mainIntent.addCategory(Intent.CATEGORY_HOME);
					startActivity(mainIntent);
				}
			}
		});

	}

	/**
	 * Class for interacting with the main interface of the service.
	 */
	public ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder iService) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service.

			service = ((AndroidOswService.LocalBinder) iService).getService();
			if (service.isConnected() && service.isAuthenticated() ) {
				// Before disconnect and reconnect as the new user, disable
				// auto-reconnect
				service.setAutoReconnection(false);
				try {
					service.stopKeepAlives();
					service.disconnect();
					service.connect();
					unbindService(mConnection);
				} catch (ConnectionRequired e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				service.setAutoReconnection(false);
				try {
					service.disconnect();
				} catch (ConnectionRequired e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// Next Heartbeat with the new log in parameters
				service.NextHeartbeat();
				unbindService(mConnection);
			}
			
			startActivity(new Intent(AccountSettings.this, InboxActivity.class));
		}

		public void onServiceDisconnected(ComponentName className) {
		}
	};


	 private class Task extends AsyncTask<Void, Void, Void> {   
	        
		 	protected void onPreExecute(){
			    dialog = new Dialog(AccountSettings.this);
				dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
				dialog.setContentView(R.layout.progressdialog);
				dialog.setCancelable(true);
				dialog.show();
		 	}
			@Override
			protected Void doInBackground(Void... params) {
				bindService(new Intent(AccountSettings.this, AndroidOswService.class),mConnection,AccountSettings.BIND_AUTO_CREATE);
				return null;
			} 
			
	        protected void onPostExecute(Void unused) {  
	            dialog.dismiss();
	          
	        }
	    }  
}
