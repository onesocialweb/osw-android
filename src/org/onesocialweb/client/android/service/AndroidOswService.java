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
package org.onesocialweb.client.android.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.onesocialweb.client.ConnectionStateListener;
import org.onesocialweb.client.Inbox;
import org.onesocialweb.client.InboxEventHandler;
import org.onesocialweb.client.OswService;
import org.onesocialweb.client.PresenceListener;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.OswBroadcastReceiver;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.AccountSettings;
import org.onesocialweb.client.android.activities.InboxActivity;
import org.onesocialweb.client.android.activities.common.ConnectionProcessListener;
import org.onesocialweb.client.exception.AuthenticationRequired;
import org.onesocialweb.client.exception.ConnectionException;
import org.onesocialweb.client.exception.ConnectionRequired;
import org.onesocialweb.client.exception.RequestException;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.relation.Relation;
import org.onesocialweb.model.vcard4.Profile;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class AndroidOswService extends Service implements OswService {

	public static final String ACTION_RECONNECT_AFTER_XMPPCLOSED = "action_reconnect_after_xmppclosed";

	static final String ACTION_RELOGIN = "action_relogin";

	static final String ACTION_KEEPALIVE = "action_keepalive";

	// public static Boolean mToggleIndeterminate = false;

	private static final int RECONNECTING = 0;

	// Defines the interval for our external keep alive process
	private static final long KEEP_ALIVE_INTERVAL = 1000 * 60 * 5;

	private static final int NOTIFICATION_ID = 1;
	
	// Keep track of the service singleton
	private static AndroidOswService instance;

	// This is the object that receives interactions from clients.
	private final IBinder mBinder = new LocalBinder();

	// The Osw smack service powered by Smack
	private final AsmackOswService service = new AsmackOswService();
	
	// Keep a local copy of subscriptions and subscribers
	private List<String> subscribers;

	private List<String> subscriptions;
	
	// Pointer to the notification inbox
	private Inbox inbox;

	// Tracks the user prefs for logging
	private String userNamePref;

	private String passwordPref;

	private String serverPref;

	private String jidPref;

	SharedPreferences sharedPrefs;

	// Customized notification settings

	private Boolean notifVibrate = false;

	private Boolean notifFLashing = false;

	private String notifRingtone = "";

	private String ledColor = "";

	private String vibratePattern = "";
	
	// Get sharedPreferences
	public SharedPreferences mPrefs;

	// Set up the indeterminate title progress bar of the activity

	public static final String RECONNECTION = "reconnection";

	/** Keep track of the connection process state listeners */
	private List<ConnectionProcessListener> connectionProcessListener = new ArrayList<ConnectionProcessListener>();

	// Reference to inbox handler (so that we can easily unregister the handler)
	private InboxHandler inboxHandler;

	// Holds the current number of reconnection attempts
	private int attempts = 0;
	
	private Roster roster;
	
	// Singleton service pattern
	public static AndroidOswService getInstance() {
		return instance;
	}

	public ConnectionStateListener Statelistener = new ConnectionStateListener() {
		@Override
		public void onStateChanged(ConnectionState newState) {

			Log.d(Onesocialweb.LOGTAG, "New XMPPConnection status:" + newState.toString());

			switch (newState) {

			case disconnected:
				fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.not_connecting);
				handleCrashedService();
				break;

			case disconnectedOnError:
				fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.not_connecting);
				// Stop the keepAlive until recover the connection
				stopKeepAlives();
				// Start heart beats to try to recover the xmpp connection
				NextHeartbeat();
				break;

			case connected:
				// If the inboxActivity has not been created but the service is
				// connected, send an xaway presence, the user wont be seen the
				// screen
				fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.not_connecting);
				cancelHeartbeat();
				break;
			}
		}
	};

	@Override
	public void onCreate() {
		// Keep track of the timing for debugging purposes
		final long start = Calendar.getInstance().getTimeInMillis();
		
		// Always call the super method
		super.onCreate();

		// Keep track of the service singleton
		AndroidOswService.instance = this;
		
		// Load the preferences, we'll need them later on
		mPrefs = getSharedPreferences(AccountSettings.PREFERENCES, MODE_PRIVATE);
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// TODO Huh ?
		OswBroadcastReceiver.SERVICE_CREATED = true;

		// Smack settings
		XMPPConnection.DEBUG_ENABLED = false;
		SmackConfiguration.setKeepAliveInterval(0);

		// Register the handler and get the inbox
		inboxHandler = new InboxHandler();
		inbox = service.getInbox();
		inbox.registerInboxEventHandler(inboxHandler);
		
		// Connect !
		connect();
		
		// Log the time it took us to do this
		Log.d(Onesocialweb.LOGTAG, "AndroidOswService.onCreate() - " + (Calendar.getInstance().getTimeInMillis() - start) + " ms");
	}

	@Override
	public void onStart(Intent intent, int startId) {
		// Keep track of the timing for debugging purposes
		final long start = Calendar.getInstance().getTimeInMillis();
		
		// Always call the super method
		super.onStart(intent, startId);

		if (intent.getAction() != null) {

			if (intent.getAction().equals(OswBroadcastReceiver.SHUTTING_DOWN)) {
				Log.d(Onesocialweb.LOGTAG, "SHUTTING DOWN...broadcast received");
				// If the user shuts down the device, disconnect the service
				// manually
				service.setPresenceMode(Presence.Type.unavailable, null);
			} else if (intent.getAction().equals(
					OswBroadcastReceiver.START_SERVICE_BOOT_COMPLETED)) {
				
				// TODO Wonder if it makese sense, we won't ever be connected here...
				inbox.refresh();

			} else if (intent.getAction().equals(
					OswBroadcastReceiver.RECONNECTION_ATTEMPT)) {
				// If the connection is already stablish is not necesary to
				// reconnect
				if (!service.isConnected() && !service.isAuthenticated()) {
					// Manage network and xmpp connection events
					ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
					NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
					if (activeNetInfo != null) {
						if (activeNetInfo.isConnected()) {
							connect();
						} else {
							cancelHeartbeat();
						}
					}
				}
			}

			else if (intent.getAction().equals(ACTION_KEEPALIVE)) {
				keepAlive();
			}

			else if (intent.getAction().equals(OswBroadcastReceiver.AWAY_TO_XA)) {
				if (service.isConnected() && service.isAuthenticated()) {
					setPresenceMode(Presence.Type.available, Presence.Mode.xa);
				}
			}
		}
		
		// Log the time it took us to do this
		Log.d(Onesocialweb.LOGTAG, "AndroidOswService.onStart() - " + (Calendar.getInstance().getTimeInMillis() - start) + " ms");
	}

	@Override
	public void onDestroy() {

		Log.d(Onesocialweb.LOGTAG, "Service onDestroy()");
		OswBroadcastReceiver.SERVICE_CREATED = false;

		mPrefs.edit().putBoolean(RECONNECTION, false).commit();
		service.removeConnectionStateListener(Statelistener);

		if (service.isConnected() && service.isAuthenticated()) {
			try {
				stopKeepAlives();
				inbox.unregisterInboxEventHandler(inboxHandler);
				service.disconnect();

			} catch (ConnectionRequired e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.onDestroy();
	}

	@Override
	public boolean connect(String server, Integer port,
			Map<String, String> parameters) throws ConnectionException {
		return false;
	}

	@Override
	public boolean deleteActivity(String activityId) throws ConnectionRequired,
	AuthenticationRequired, RequestException {
		return service.deleteActivity(activityId);
	}

	@Override
	public boolean disconnect() throws ConnectionRequired {
		return service.disconnect();
	}

	@Override
	public List<ActivityEntry> getActivities(String userJid)
			throws RequestException, ConnectionRequired, AuthenticationRequired {
		return service.getActivities(userJid);
	}

	@Override
	public String getHostname() {
		return service.getHostname();
	}

	@Override
	public Inbox getInbox() {
		return inbox;
	}

	@Override
	public Profile getProfile(String userJid) {
		try {
			return service.getProfile(userJid);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String getUser() {
		return service.getUser();
	}

	@Override
	public boolean isAuthenticated() {
		return service.isAuthenticated();
	}

	@Override
	public boolean isConnected() {
		return service.isConnected();
	}

	@Override
	public boolean login(String username, String password, String resource)
			throws ConnectionRequired, RequestException {
	
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postActivity(ActivityEntry entry) throws ConnectionRequired,
			AuthenticationRequired, RequestException {
		return service.postActivity(entry);
	}

	@Override
	public boolean register(String username, String password, String name,
			String email) throws ConnectionRequired {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean setProfile(Profile profile) throws RequestException,
			AuthenticationRequired, ConnectionRequired {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateActivity(ActivityEntry entry) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void addConnectionStateListener(ConnectionStateListener listener) {
		service.addConnectionStateListener(listener);
	}

	@Override
	public void removeConnectionStateListener(ConnectionStateListener listener) {
		service.removeConnectionStateListener(listener);
	}

	@Override
	public void addPresenceListener(PresenceListener listener) {
		service.addPresenceListener(listener);
	}

	@Override
	public void removePresenceListener(PresenceListener listener) {
		service.removePresenceListener(listener);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void setPresenceMode(Presence.Type pres, Presence.Mode mod) {
		if (service.isConnected() && service.isAuthenticated()) {
			Log.d(Onesocialweb.LOGTAG, "AndroidOswService change presence mode to:" + pres.toString() + ", " + mod.toString());
			service.setPresenceMode(pres, mod);
		}

	}

	@Override
	public Presence getContactPresence(String user) {
		if (service.isConnected() && service.isAuthenticated()) {
			return service.getContactPresence(user);
		}
		return new Presence(Presence.Type.unavailable);
	}

	@Override
	public List<Relation> getRelations(String userJid) throws RequestException,
			ConnectionRequired, AuthenticationRequired {
		return service.getRelations(userJid);
	}

	@Override
	public boolean addRelation(Relation relation) throws RequestException,
			AuthenticationRequired, ConnectionRequired {
		return service.addRelation(relation);
	}

	@Override
	public boolean updateRelation(Relation relation) throws RequestException,
			AuthenticationRequired, ConnectionRequired {
		return service.updateRelation(relation);
	}

	@Override
	public String getUploadToken(String requestID) throws RequestException,
			AuthenticationRequired, ConnectionRequired {

		return service.getUploadToken(requestID);
	}

	@Override
	public boolean subscribe(String userJid) throws RequestException,
			ConnectionRequired, AuthenticationRequired {
		if (service.subscribe(userJid)) {
			if (subscriptions != null && !subscriptions.contains(userJid)) {
				subscriptions.add(userJid);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean unsubscribe(String userJid) throws RequestException,
			ConnectionRequired, AuthenticationRequired {
		if (service.unsubscribe(userJid)) {
			if (subscriptions != null && subscriptions.contains(userJid)) {
				subscriptions.remove(userJid);
				return true;
			}
		}
		return false;
	}
	

	public List<String> getSubscribers(String userJID) throws RequestException,
			ConnectionRequired, AuthenticationRequired {
			return service.getSubscribers(userJID);
	}

	public List<String> getSubscriptions(String userJID) throws RequestException,
			ConnectionRequired, AuthenticationRequired {
		if (userJID.equals(getBareJID())) {
			if (subscriptions == null) {
				subscriptions = service.getSubscriptions(userJID);
			}
			return subscriptions;
		} else {
			return service.getSubscriptions(userJID);
		}
	}

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case RECONNECTING: {
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setMessage("Attempting to connect...");
			dialog.setIndeterminate(true);
			dialog.setCancelable(true);
			return dialog;
		}
		}
		return null;
	}

	/**
	 * Manage connection closed event,if the user has manually closed not try to
	 * reconnect
	 */
	public void handleCrashedService() {
		if (getAutoReconnection() == true) {
			// Stop the keepAlive until recover the connection
			stopKeepAlives();
			// Start heartbeats to try to recover the xmpp connection
			NextHeartbeat();
		}
	}

	/**
	 * Using AndroidOswService preference in PRIVATE mode to enable and disable
	 * auto-reconnection
	 */
	public void setAutoReconnection(boolean var) {
		mPrefs.edit().putBoolean(AndroidOswService.RECONNECTION, var).commit();
	}

	/**
	 * If the user closes the connection munually the auto-reconnection is
	 * disabled
	 */
	private boolean getAutoReconnection() {
		return mPrefs.getBoolean(RECONNECTION, false);
	}

	private class InboxHandler implements InboxEventHandler {

		@Override
		public void onMessageDeleted(ActivityEntry entry) {}

		@Override
		public void onMessageReceived(ActivityEntry entry) {
			displayNotification();			
		}

		@Override
		public void onRefresh(List<ActivityEntry> activities) {}

		@Override
		public void onMessageUpdated(ActivityEntry entry) {}
		
	}

	/** Get login preferences: jid and password */
	private void loadPreferences() {
		jidPref = mPrefs.getString(AccountSettings.USERNAME, "");
		passwordPref = mPrefs.getString(AccountSettings.PASSWORD, "");
		// Notification settings
		notifVibrate = sharedPrefs.getBoolean(
				OswPreferences.NOTIFICATION_VIBRATE, false);
		notifFLashing = sharedPrefs.getBoolean(
				OswPreferences.NOTIFICATION_FLASHING, false);
		notifRingtone = sharedPrefs.getString(
				OswPreferences.NOTIFICATION_RINGTONE, "");

		ledColor = sharedPrefs.getString(OswPreferences.NOTIFICATION_LED_COLOR,
				"");
		vibratePattern = sharedPrefs.getString(
				OswPreferences.NOTIFICATION_VIBRATE_PATTERN, "");
	}

	/** Clear login preferences: jid and password */
	private void clearPrefs() {
		mPrefs.edit().putString(AccountSettings.USERNAME, "").commit();
		mPrefs.edit().putString(AccountSettings.PASSWORD, "").commit();
	}

	/** Display a status bar notification with a status update */
	private void displayNotification() {
		Log.d(Onesocialweb.LOGTAG, "AndroidOswService DisplayNotification");
		List<ActivityEntry> messages = inbox.getEntries();
		if (messages.size() != 0) {
			ActivityEntry activity = messages.get(0);

			// Filter popups, if the message is from the user not
			// notification
			if (!filterPopUpNotification(activity)) {

				if (messages.size() > 0) {
					String ns = AndroidOswService.NOTIFICATION_SERVICE;
					NotificationManager notificationManager = (NotificationManager) getSystemService(ns);

					int icon = R.drawable.ic_stat_notify_new;
					CharSequence tickerText = "New status update";
					long when = System.currentTimeMillis();

					Notification notification = new Notification(icon,
							tickerText, when);

					if (notifVibrate) {
						if (vibratePattern.equals("default")) {
							notification.defaults |= Notification.DEFAULT_VIBRATE;
						} else {

							if (!vibratePattern.equals("default")) {
								notification.vibrate = OswPreferences
										.parseVibratePattern(vibratePattern);

							} else {
								notification.defaults |= Notification.DEFAULT_VIBRATE;
							}
						}
					}

					if (notifFLashing) {
						if (ledColor.equals("default")) {
							notification.defaults |= Notification.DEFAULT_LIGHTS;
						} else {
							int color = Color.parseColor(ledColor);

							notification.ledARGB = color;
							notification.ledOnMS = 300;
							notification.ledOffMS = 1000;
							notification.flags |= Notification.FLAG_SHOW_LIGHTS;
						}
					}

					// If the ringtone selected is "silent" do not use any
					// sound
					// for the notification

					if (notifRingtone.length() != 0) {
						notification.sound = Uri.parse(notifRingtone);
					}

					// To clear the notification just after been read
					notification.flags |= Notification.FLAG_AUTO_CANCEL;

					// Notification's expanded message
					Context context = getApplicationContext();
					CharSequence contentTitle = "New status update";
					CharSequence contentText = "default text";

					contentText = activity.getTitle().toString();

					Intent notificationIntent = new Intent(this,
							InboxActivity.class);
					PendingIntent contentIntent = PendingIntent.getActivity(
							this, 0, notificationIntent, 0);
					notification.setLatestEventInfo(context, contentTitle,
							contentText, contentIntent);
					notificationManager.notify(NOTIFICATION_ID, notification);
					
					return;
				}

			}

		}

	}

	/** Compares the notification sender and the current user */
	private boolean filterPopUpNotification(ActivityEntry activity) {
		String currentUser = service.getUser();
		String notificationFrom = activity.getActor().getUri();

		return currentUser.split("/")[0].equals(notificationFrom.split("/")[0]);
	}

	/** Connection and login using user preferences */
	public void connect() {
		fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.connecting);
		new AsyncConnection().execute();
		return;
	}

	/** Just send an empty xmpp packet to keep alive the TCP socket. */
	private void keepAlive() {
		Log.d(Onesocialweb.LOGTAG, "Keep alive");
		((AsmackOswService) service).sendRawData(" ");
	}

	/** Start our own keep alive process using pending intents. */
	private void startKeepAlives() {
		Intent i = new Intent();
		i.setClass(this, AndroidOswService.class);
		i.setAction(ACTION_KEEPALIVE);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, System
				.currentTimeMillis()
				+ KEEP_ALIVE_INTERVAL, KEEP_ALIVE_INTERVAL, pi);
	}

	/** Stop our own keep alive process */
	public void stopKeepAlives() {
		Intent i = new Intent();
		i.setClass(this, AndroidOswService.class);
		i.setAction(ACTION_KEEPALIVE);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmMgr.cancel(pi);
	}

	/**
	 * Set next heart beat to try to recover the xmpp connection usign scheduled
	 * pending intent
	 */
	public void NextHeartbeat() {

		Log.d(Onesocialweb.LOGTAG, "OswService NextHeartBeat");

		fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.connecting);
		// Get the alarm manager and schedule a pending intent
		final AlarmManager alarmMgr = (AlarmManager) getSystemService(Service.ALARM_SERVICE);
		final long time = System.currentTimeMillis() + timeDelayReconnection();
		alarmMgr.set(AlarmManager.RTC_WAKEUP, time,
				getPendingIntent(ACTION_RECONNECT_AFTER_XMPPCLOSED));
		attempts++;

	}

	/** Cancel xmpp reconnection process */
	public void cancelHeartbeat() {
		Log.d(Onesocialweb.LOGTAG, "OswService CancelHeartBeat");
		fireConnectionStateChanged(ConnectionProcessListener.ConnectionProcessState.not_connecting);
		// mToggleIndeterminate = false;
		attempts = 0;
		// Get the alarm manager and cancel the pending intent
		final AlarmManager alarmMgr = (AlarmManager) getSystemService(Service.ALARM_SERVICE);
		alarmMgr.cancel(getPendingIntent(ACTION_RECONNECT_AFTER_XMPPCLOSED));
	}

	/**
	 * Create new pending intent to send a broadcast intent to try to reconnect
	 * with the xmpp server
	 */
	public PendingIntent getPendingIntent(String action) {
		// create explicit intent for the auto-reconnect receiver component
		final Intent intent = new Intent(action, null, this,
				OswBroadcastReceiver.class);
		// prepare pending intent that wakes up service after a specified time
		final int flags = PendingIntent.FLAG_UPDATE_CURRENT
				| PendingIntent.FLAG_UPDATE_CURRENT;
		final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
				intent, flags);
		return pendingIntent;
	}

	/** Returns this service to make it accessible to the activity */
	public class LocalBinder extends Binder {
		public AndroidOswService getService() {
			return AndroidOswService.this;
		}
	}

	/** Get the roster of the connection */
	public Roster getRoster() {		
		roster = ((AsmackOswService) service).getRoster();
		return roster;
	}

	private class AsyncConnection extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {

			// Fetch the preferences
			loadPreferences();

			// The user must login using the full jid
			// TODO This filtering must happen at UI.. not here !!!
			if (jidPref.indexOf("@") == -1) {
				Intent intent = new Intent(AndroidOswService.this, AccountSettings.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				//Toast.makeText(AndroidOswService.this, "You must specify user@domain to login", Toast.LENGTH_SHORT).show();
				return false;
			}

			// Parse the username and domain
			String[] jidElements = jidPref.split("@");
			userNamePref = jidPref.split("@")[0];
			serverPref = jidPref.split("@")[1];

			// Connect the XMPP service if is not already connected
			if (!service.isConnected()) {
				try {
					service.setCompressionEnabled(false);
					service.setReconnectionAllowed(false);
					service.connect(serverPref, 5222, null);
					
					/** 
					 * If debugging on a local system without network access
					 * you can bind to the local server using the IP 10.0.2.2.
					 * service.connect("10.0.2.2", 5222, null);
					 * 
					 */
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Authenticate if we were not already authenticated
			// TODO Should we not re-authenticate if it is the case ?
			if (!service.isAuthenticated()) {
				try {
					service.login(userNamePref, passwordPref, "mobile_" + (Calendar.getInstance().getTimeInMillis() / 1000));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			// On succes, we add our listeners and prime the cache
			if (service.isConnected() && service.isAuthenticated()) {
				// Add a state listener to the service
				service.addConnectionStateListener(Statelistener);
				
				// Pre-fetch the subscriptions and subscribers
				try {
					subscribers = service.getSubscribers(getBareJID());
					subscriptions = service.getSubscriptions(getBareJID());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				// Start the keep alive process
				startKeepAlives();
				
				// Enable auto-reconnection
				setAutoReconnection(true);
				
				// We were successfull
				return true;
				
			} 
			// On failure, we schedule the next heartbeat to try the reconnection
			// TODO I wonder if we should do this here. 
			else {
				NextHeartbeat();
				return false;
			}
		}

	}

	public void addConnectionProcessListener(ConnectionProcessListener listener) {
		connectionProcessListener.add(listener);
	}
	
	public void removeConnectionProcessListener(ConnectionProcessListener listener) {
		connectionProcessListener.remove(listener);
	}

	public void fireConnectionStateChanged(
			ConnectionProcessListener.ConnectionProcessState state) {
		for (ConnectionProcessListener listener : connectionProcessListener) {
			listener.onStateChanged(state);
		}
	}

	public String getBareJID() {
		return service.getBareJID();
	}

	@Override
	public void setCompressionEnabled(boolean compressionEnabled) {
		service.setCompressionEnabled(compressionEnabled);
	}

	@Override
	public void setReconnectionAllowed(boolean isAllowed) {
		service.setReconnectionAllowed(isAllowed);
	}
	

	
	// Returns the number of miliseconds until the next reconnection attempt.
	private int timeDelayReconnection() {
		if (attempts > 13) {
			return 60 * 5 * 1000; // 5 minutes
		}
		if (attempts > 7) {
			return 60 * 1000; // 1 minute
		}
		return 10 * 1000; // 10 seconds
	}

}
