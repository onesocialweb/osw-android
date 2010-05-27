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

import org.jivesoftware.smack.packet.Presence;
import org.onesocialweb.client.PresenceListener;
import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.OswBroadcastReceiver;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.client.exception.ConnectionRequired;
import org.onesocialweb.model.activity.ActivityEntry;

import android.app.Activity;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class InboxActivity extends ListActivity {

	// Menu identifiers
	private static final int MENU_CONTACTS = Menu.FIRST;
	private static final int MENU_COMPOSE = Menu.FIRST + 1;
	private static final int MENU_PREFERENCES = Menu.FIRST + 2;
	private static final int MENU_EXIT = Menu.FIRST + 3;

	// Dialogs identifiers
	private static final int DIALOG_UPDATING = 0;
	private static final int DIALOG_REFRESHING = 1;
	private static final int DIALOG_VIEW_ACTIITY = 2;
	
	// A handler for posting back to the UI thread
	private final Handler handler = new Handler();
	
	// Keep track of view elements
	private ViewHolder viewHolder;

	// Preferences
	private SharedPreferences mPrefs;
	private String jidPref;
	private String passwordPref;

	// Inbox adapter for this list view
	private InboxAdapter inboxAdapter;

	// The Application object
	private Onesocialweb onesocialweb;
	
	// Onesocialweb service
	private AndroidOswService service;
	
	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);

		// Keep a reference to the application
		onesocialweb = (Onesocialweb) getApplication();
		
		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.oswcustomtitle);
		
		// Keep track of the view elements
		viewHolder = new ViewHolder(this);
		
		// Customize title bar with the activity name
		viewHolder.title.setText(R.string.inbox);
		
		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);

		// Register the inboxAdapter
		inboxAdapter = new InboxAdapter(InboxActivity.this);
		setListAdapter(inboxAdapter);

		// We'll need these preferences further down the road
		readPreferences();
	}

	@Override
	protected void onStart() {
		// Be sure to call the super class
		super.onStart();

		// Start the service if no service available
		if (AndroidOswService.getInstance() == null) {
			startService(new Intent(InboxActivity.this, AndroidOswService.class));
		}
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();

		// Show to the user that we are doing something
		viewHolder.showProgressIcon();

		// TODO What is this ?
		OswBroadcastReceiver.INBOX_ACTIVITY_CREATED = true;

		// TODO Do we really need to do this again ?
		readPreferences();

		// If no account is set, we direct the user to the AccounSettings
		// activity
		if (jidPref == "empty" || passwordPref == "empty" || jidPref == "" || passwordPref == "") {
			startActivity(new Intent(this, AccountSettings.class));
			// TODO Do we need to return ? I guess it is safer
			return;
		}

		// Bind with the Onesocialweb service
		bindService(new Intent(InboxActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onPause() {
		// If the service exist, it means we have compelted the onResume() and binding() before
		// triggering this onPause()
		if (service != null) {
			if (service.isConnected() && service.isAuthenticated()) {
				// Set Presence Mode as away and schedule pending intent, 15 min
				// later on change status to xa
				// TODO Fix the presence stuff
				//service.setPresenceMode(Presence.Type.available, Presence.Mode.away);
				//new Xaway(InboxActivity.this).scheduleXaPresenceMode();
			}

			// Removed the listeners
			service.removeConnectionStateListener(connectionListener);
			service.removeConnectionProcessListener(connectionListener);
			service.removePresenceListener(presenceListener);

			// Unbind (we HAVE to do this otherwise the Droid is not happy :-)
			unbindService(mConnection);
		}

		// TODO Do we need this ?
		OswBroadcastReceiver.INBOX_ACTIVITY_CREATED = false;

		// Be sure to call the super class.
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// Fetch the activity clicked
		final ActivityEntry entry = inboxAdapter.getActivity(position);
		final String activityId = entry.getId();
		
		// Place the object in the shared storage
		onesocialweb.putSharedObject(activityId, entry);
		
		// Fire the intent
		Intent intent = new Intent(InboxActivity.this, ViewActivity.class);
		intent.putExtra(ViewActivity.PARAM_ACTIVITY_ID, activityId);
		startActivity(intent);
		
		// Propagate the event
		super.onListItemClick(l, v, position, id);
	}

	/* Creates the menu items */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_CONTACTS, MENU_CONTACTS, R.string.contacts).setIcon(R.drawable.ic_menu_friendslist);
		menu.add(0, MENU_COMPOSE, MENU_COMPOSE, R.string.composer).setIcon(R.drawable.ic_menu_compose);
		menu.add(0, MENU_PREFERENCES, MENU_PREFERENCES, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, MENU_EXIT, MENU_EXIT, R.string.exit).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	/* Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case MENU_CONTACTS:
			Intent conctactsIntent = new Intent(InboxActivity.this, ContactsActivity.class);
			startActivity(conctactsIntent);
			return true;
		case MENU_COMPOSE:
			Intent composeIntent = new Intent(InboxActivity.this, ComposeActivity.class);
			startActivity(composeIntent);
			return true;
		case MENU_PREFERENCES:
			startActivity(new Intent(this, OswPreferences.class));
			return true;
		case MENU_EXIT:
			new AsyncExitApplication().execute();
			return true;
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_UPDATING: {
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setMessage(getResources().getText(R.string.updating_status));
			dialog.setIndeterminate(true);
			dialog.setCancelable(true);
			return dialog;
		}
		case DIALOG_REFRESHING: {
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setMessage(getResources().getText(R.string.refreshing_inbox));
			dialog.setIndeterminate(true);
			dialog.setCancelable(true);
			return dialog;
		}
		case DIALOG_VIEW_ACTIITY: {
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setMessage(getResources().getText(R.string.displaying_activity));
			dialog.setIndeterminate(true);
			dialog.setCancelable(true);
			return dialog;
		}

		}
		return null;
	}

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder iService) {
			// Get the service from the binder
			service = ((AndroidOswService.LocalBinder) iService).getService();

			// Set the inbox to the adapter
			inboxAdapter.setInbox(service.getInbox());

			// Register the various listeners
			service.addConnectionStateListener(connectionListener);
			service.addConnectionProcessListener(connectionListener);
			service.addPresenceListener(presenceListener);
			
			// Update the activity based on the current state
			if (service.isConnected() && service.isAuthenticated()) {
				connectionListener.onStateChanged(ConnectionState.connected);
			} else {
				connectionListener.onStateChanged(ConnectionState.disconnected);
			}

		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			Log.d(Onesocialweb.LOGTAG, "onServiceDisconnected");
			service = null;
		}
	};

	private void readPreferences() {
		mPrefs = getSharedPreferences(AccountSettings.PREFERENCES, MODE_PRIVATE);
		jidPref = mPrefs.getString(AccountSettings.USERNAME, "");
		passwordPref = mPrefs.getString(AccountSettings.PASSWORD, "");
	}

	private PresenceListener presenceListener = new PresenceListener() {
		@Override
		public void onPresenceChanged(Presence newPresence) {
			handler.post(new Runnable() {
				
				@Override
				public void run() {
					inboxAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	private class AsyncExitApplication extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			service.setAutoReconnection(false);
			service.cancelHeartbeat();
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(Void... params) {
			Log.d(Onesocialweb.LOGTAG, "InboxActivity exitOswService");
			OswBroadcastReceiver.SERVICE_CREATED = false;
			service.removeConnectionStateListener(connectionListener);
			service.removeConnectionProcessListener(connectionListener);
			service.removePresenceListener(presenceListener);
			if (service.isConnected() && service.isAuthenticated()) {
				try {
					service.stopKeepAlives();
					service.disconnect();
				} catch (ConnectionRequired e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			stopService(new Intent(InboxActivity.this, AndroidOswService.class));
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			finish();
			Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			mainIntent.addCategory(Intent.CATEGORY_HOME);
			startActivity(mainIntent);
			Toast.makeText(InboxActivity.this, getResources().getText(R.string.closed_osw), Toast.LENGTH_SHORT).show();

			super.onPostExecute(result);
		}
	}


	
	private class ViewHolder extends CommonViewHolder {

		TextView title;
		
		public ViewHolder(Activity activity) {
			super(activity);
			title = (TextView) findViewById(R.id.left_text);
		}
		
	}

}
