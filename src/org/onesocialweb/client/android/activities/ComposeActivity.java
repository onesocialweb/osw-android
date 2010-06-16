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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.acl.AclAction;
import org.onesocialweb.model.acl.AclFactory;
import org.onesocialweb.model.acl.AclRule;
import org.onesocialweb.model.acl.AclSubject;
import org.onesocialweb.model.acl.DefaultAclFactory;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.activity.ActivityVerb;
import org.onesocialweb.model.activity.DefaultActivityFactory;
import org.onesocialweb.model.atom.AtomFactory;
import org.onesocialweb.model.atom.DefaultAtomFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ComposeActivity extends Activity {
	
	// Key for Intents
	public static final String RECIPIENT_JID = "recipient_jid";
	
	// Menu identifiers
	private static final int MENU_INBOX = Menu.FIRST;
	private static final int MENU_CONTACTS = Menu.FIRST + 1;
	private static final int MENU_COMPOSE = Menu.FIRST + 2;
	private static final int MENU_PREFERENCES = Menu.FIRST + 3;
	
	// Keep track of view elements
	private ViewHolder viewHolder;
	private EditText shareText;
	
	// Onesocialweb service
	private AndroidOswService service = null;
	
	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;
	
	private static final int DIALOG_SHARING = 0;
	private static final int DIALOG_ADD_RECIPIENT = 1;
	
	private List<String> recipients = new ArrayList<String>();

	// The lists for setting privacy
	private int selectedPrivacyGroup = -1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);
		
		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.compose);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.oswcustomtitle);
		
		// Keep track of the view elements
		viewHolder = new ViewHolder(this);
		
		// Initialize the view
		initView();
		
		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);
		
		// Customize title bar with the activity name
		viewHolder.title.setText(R.string.composer);

		// Bind with the OswService
		bindService(new Intent(ComposeActivity.this, AndroidOswService.class),
				mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();

		// Fetch a recipient if any
		recipients.clear();
		String recipient = getIntent().getStringExtra(RECIPIENT_JID);
		if (recipient != null && recipient.length() > 0) {
			onAddRecipient(recipient);
		}
		
		// Bind with the OswService
		bindService(new Intent(ComposeActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onPause() {
		// Be sure to call the super class.
		super.onPause();

		// Cleanup our listeners and service bindings
		if (service != null) {
			service.removeConnectionStateListener(connectionListener);
			service.removeConnectionProcessListener(connectionListener);
			unbindService(mConnection);
		}
	}

	 /* Creates the menu items */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_INBOX, MENU_INBOX, getResources().getText(R.string.inbox)).setIcon(R.drawable.ic_menu_home);
		menu.add(0, MENU_CONTACTS, MENU_CONTACTS, getResources().getText(R.string.contacts)).setIcon(R.drawable.ic_menu_friendslist);
		menu.add(0, MENU_COMPOSE, MENU_COMPOSE, getResources().getText(R.string.composer)).setIcon(R.drawable.ic_menu_compose);
		menu.add(0, MENU_PREFERENCES, MENU_PREFERENCES, getResources().getText(R.string.settings)).setIcon(android.R.drawable.ic_menu_preferences);
		return true;
	}

	/* Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case MENU_INBOX:
			Intent intent = new Intent(this, InboxActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		case MENU_CONTACTS:
			Intent conctactsIntent = new Intent(this, ContactsActivity.class);
			startActivity(conctactsIntent);
			return true;
		case MENU_COMPOSE:
			Intent composeIntent = new Intent(this, ComposeActivity.class);
			startActivity(composeIntent);
			return true;
		case MENU_PREFERENCES:
			startActivity(new Intent(this, OswPreferences.class));
			return true;
		}
		return false;
	}
	
	private void initView() {
		
		// Click handlers
		viewHolder.shareButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onShareActivity();
			}
		});

		viewHolder.cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onCancel();
			}
		});

		// Add recipient clickhandler
		viewHolder.shoutButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_ADD_RECIPIENT);
			}
		});
		
		// Privacy clickhandler
		viewHolder.privacyButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				final ArrayList<RosterGroup> groups = new ArrayList<RosterGroup>(
						service.getRoster().getGroups());
				
				final String[] privacyGroups;
				
				privacyGroups = new String[groups.size() + 1];
				privacyGroups[0] = getResources().getString(R.string.everyone);

				for (int j = 0; j < groups.size(); j++) {
					final int i = j;
					privacyGroups[i + 1] = groups.get(j).getName();
				}
				
				AlertDialog.Builder builder = new AlertDialog.Builder(
						ComposeActivity.this);
				builder.setTitle(R.string.visible_to);
				builder.setSingleChoiceItems(privacyGroups, selectedPrivacyGroup + 1, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						viewHolder.privacyLabel.setText(getResources().getString(R.string.visible_to) + " '" + privacyGroups[item] + "'");
						selectedPrivacyGroup = item - 1;
						dialog.cancel();
					}
				});
				AlertDialog alert = builder.create();
				alert.show();
			}
		});
		
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		switch (id) {
			case DIALOG_SHARING: {
				ProgressDialog dialog = new ProgressDialog(this);
				dialog.setMessage(getResources().getText(R.string.sharing));
				dialog.setIndeterminate(true);
				dialog.setCancelable(true);
				return dialog;
			}
			case DIALOG_ADD_RECIPIENT: {
				// new custom dialog
				final Dialog dialog = new Dialog(this);
				
				// pick the right layout and set the title
				dialog.setContentView(R.layout.recipientsdialog);
				dialog.setTitle(R.string.add_recipient);
				
				// make sure the dialog fills the screen horizontally
				Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
				int width = display.getWidth() - 50;
				LinearLayout wrapper = (LinearLayout) dialog.findViewById(R.id.wrapper);
				wrapper.setLayoutParams(new FrameLayout.LayoutParams(width,FrameLayout.LayoutParams.WRAP_CONTENT));
				
				// initiate the objects
				final AutoCompleteTextView recipient = (AutoCompleteTextView) dialog.findViewById(R.id.recipient);
				Button okButton = (Button) dialog.findViewById(R.id.okButton);
				Button cancelButton = (Button) dialog.findViewById(R.id.cancelButton);
				
				// the adapter to hold the jids
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line);
				
				// get the jids from the contacts
				Collection<RosterEntry> contacts = service.getRoster().getEntries();
				ArrayList<RosterEntry> contactsList = new ArrayList<RosterEntry>(
						contacts);
	
				for (int i = 0; i < contactsList.size(); i++) {
					adapter.add(contactsList.get(i).getUser().toString());
				}
				
				recipient.setAdapter(adapter);
				
				okButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onAddRecipient(recipient.getText().toString());
						recipient.setText(null);
						dialog.dismiss();
					}
				});
				
				cancelButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						recipient.setText(null);
						dialog.dismiss();
					}
				});
				
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

			// Keep track the connectivity to display at title bar
			service.addConnectionStateListener(connectionListener);
			service.addConnectionProcessListener(connectionListener);

			// Update the activity based on the current state
			if (service.isConnected() && service.isAuthenticated()) {
				connectionListener.onStateChanged(ConnectionState.connected);
			} else {
				connectionListener.onStateChanged(ConnectionState.disconnected);
			}

		}
		
		public void onServiceDisconnected(ComponentName className) {
			//
		}
	};


	public void onCancel() {
			new AsyncSimulateBackKey().execute();
	}

	public void onShareActivity() {
		
		// Need to be connected
		if (!service.isConnected() || !service.isAuthenticated()) {
			showError(getResources().getString(R.string.error_unable_to_share));
			return;
		}
		
		// Need to actually post something
		final String statusUpdate = shareText.getText().toString();
		if (statusUpdate == null || statusUpdate.length() == 0) {
			showError(getResources().getString(R.string.error_empty_update));
			return;
		}

		// Good to go !
		ActivityObject object = new DefaultActivityFactory().object();
		ActivityEntry entry = new DefaultActivityFactory().entry();

		// Assign recipients
		final AtomFactory atomFactory = new DefaultAtomFactory();
		if (recipients != null && recipients.size() > 0) {
			for (String recipient : recipients) {
				entry.addRecipient(atomFactory.reply(null, recipient, null, null));
			}
		}
		
		// Assign privacy
		// If everyone can see this
		AclFactory aclFactory = new DefaultAclFactory();
		List<AclRule> privacyRules = new ArrayList<AclRule>();
		if (selectedPrivacyGroup == -1) {
			// Build the default ACL
			AclRule rule = new DefaultAclFactory().aclRule();
			rule.addSubject(aclFactory.aclSubject(null, AclSubject.EVERYONE));
			rule.addAction(aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT));
			privacyRules.add(rule);
		} else {
			final ArrayList<RosterGroup> groups = new ArrayList<RosterGroup>(service.getRoster().getGroups());
			// Build the default ACL
			AclRule rule = new DefaultAclFactory().aclRule();
			rule.addSubject(aclFactory.aclSubject(groups.get(selectedPrivacyGroup).getName(), AclSubject.GROUP));
			rule.addAction(aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT));
			privacyRules.add(rule);
		}
		entry.setAclRules(privacyRules);
		
		// Just a new status, no pic to share
		object.setType(ActivityObject.STATUS_UPDATE);
		object.addContent(new DefaultAtomFactory().content(statusUpdate, "text/plain", null));
		entry.setPublished(Calendar.getInstance().getTime());
		entry.addVerb(new DefaultActivityFactory().verb(ActivityVerb.POST));
		entry.addObject(object);
		entry.setTitle(statusUpdate);

		// Post the status update asynchronously
		new AsyncShareActivity().execute(entry);
	}

	private void onAddRecipient(final String recipient) {
		// Add the recipient to the recipients list
		recipients.add(recipient);

		// Show the new recipient to the user and enable to delete it
		LayoutInflater inflater = this.getLayoutInflater();
		final View rowView = inflater.inflate(R.layout.deleterecipient, null);
		
		TextView label = (TextView) rowView.findViewById(R.id.label);
		label.setText(recipient);
		ImageButton deleteButton = (ImageButton) rowView.findViewById(R.id.deleteButton);
		deleteButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				recipients.remove(recipient);
				viewHolder.recipientsContainer.removeView(rowView);
				if (recipients.isEmpty()) {
					viewHolder.shoutsContainer.setVisibility(View.GONE);
				}
			}
		});
		
		// Add to the container
		viewHolder.recipientsContainer.addView(rowView);
		viewHolder.shoutsContainer.setVisibility(View.VISIBLE);
		
	}
	
	private class AsyncShareActivity extends AsyncTask<ActivityEntry, Void, Void> {

		private boolean isPosted = false;
		
		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_SHARING);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(ActivityEntry... activity) {			
			try {
				if (service.postActivity(activity[0])) {
					isPosted = true;
				}
			} catch (Exception e) {}

			// Back to the main activity: the inbox
			if (isPosted) {
				Intent intent = new Intent(ComposeActivity.this, InboxActivity.class);
				startActivity(intent);
				finish();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// We are done
			dismissDialog(DIALOG_SHARING);
			if (!isPosted) {
				Toast.makeText(ComposeActivity.this, getResources().getString(R.string.error_unable_to_share), Toast.LENGTH_SHORT).show();
			}
			super.onPostExecute(result);
		}

	}

	private class AsyncSimulateBackKey extends AsyncTask<String, Void, Void> {
		// Simulate the back key behavior to use at cancel button
		private void simulateKeyStroke(int keyCode, Activity parent) {
			injectKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode), parent);
			injectKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode), parent);
		}

		private void injectKeyEvent(KeyEvent keyEvent, Activity parent) {
			parent.dispatchKeyEvent(keyEvent);
		}

		@Override
		protected void onPreExecute() {
			simulateKeyStroke(KeyEvent.KEYCODE_BACK, ComposeActivity.this);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(String... params) {
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
		}

	}

	public static int countMatches(String str, String sub) {
		if (isEmpty(str) || isEmpty(sub)) {
			return 0;
		}
		int count = 0;
		int idx = 0;
		while ((idx = str.indexOf(sub, idx)) != -1) {
			count++;
			idx += sub.length();
		}
		return count;
	}

	public static boolean isEmpty(String str) {
		return str == null || str.length() == 0;
	}
	
	private void showError(String error) {
		Toast.makeText(
				ComposeActivity.this,
				error,
				Toast.LENGTH_SHORT).show();
	}
	
	private class ViewHolder extends CommonViewHolder {
		
		private final Button shareButton, cancelButton;
		private ImageButton shoutButton, privacyButton;
		private TextView title, privacyLabel;
		private LinearLayout recipientsContainer, shoutsContainer;
		
		public ViewHolder(Activity activity) {
			super(activity);
			title = (TextView) findViewById(R.id.left_text);
			privacyLabel = (TextView) findViewById(R.id.privacyLabel);
			shareButton = (Button) findViewById(R.id.shareButton);
			cancelButton = (Button) findViewById(R.id.cancelButton);
			shareText = (EditText) findViewById(R.id.shareText);
			shoutButton = (ImageButton) findViewById(R.id.shoutButton);
			privacyButton = (ImageButton) findViewById(R.id.privacyButton);
			recipientsContainer = (LinearLayout) findViewById(R.id.recipients);
			shoutsContainer = (LinearLayout) findViewById(R.id.shoutContainer);
		}
		
	}
}