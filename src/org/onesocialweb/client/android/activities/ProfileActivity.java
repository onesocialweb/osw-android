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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.XMPPException;
import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.async.AsyncActivitiesLoader;
import org.onesocialweb.client.android.async.AsyncImageLoader;
import org.onesocialweb.client.android.async.AsyncProfileLoader;
import org.onesocialweb.client.android.async.AsyncActivitiesLoader.ActivitiesCallback;
import org.onesocialweb.client.android.async.AsyncImageLoader.ImageCallback;
import org.onesocialweb.client.android.async.AsyncProfileLoader.ProfileCallback;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.client.exception.AuthenticationRequired;
import org.onesocialweb.client.exception.ConnectionRequired;
import org.onesocialweb.client.exception.RequestException;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.atom.AtomReplyTo;
import org.onesocialweb.model.vcard4.Field;
import org.onesocialweb.model.vcard4.Profile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ProfileActivity extends TabActivity {

	// Intent parameters
	public static final String PARAM_USER_JID = "userJid";

	// Menu identifiers
	private static final int MENU_INBOX = Menu.FIRST;
	private static final int MENU_CONTACTS = Menu.FIRST + 1;
	private static final int MENU_COMPOSE = Menu.FIRST + 2;
	private static final int MENU_PREFERENCES = Menu.FIRST + 3;

	// Need handler for callbacks to the UI thread
	private final Handler handler = new Handler();

	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;

	// Keep track of view elements
	private ViewHolder viewHolder;
	
	// The application object
	private Onesocialweb onesocialweb;

	// Onesocialweb service
	private AndroidOswService service;

	// The jid of the profile to display
	private String userJid;

	// The actual profile of the user being viewed
	private Profile profile;

	// The activities of the current user
	private List<ActivityEntry> activities;

	// The adapter backing the list
	private ListModelAdapter listModelAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Keep track of the timing for debugging purposes
		final long start = Calendar.getInstance().getTimeInMillis();
		
		// Assign the view so that we can find the view objects by ID
		super.onCreate(savedInstanceState);
		
		// Keep a reference to the application
		onesocialweb = (Onesocialweb) getApplication();

		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.profile);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.oswcustomtitle);

		// Keep track of the view elements in a holder
		viewHolder = new ViewHolder(this);

		// Customize title bar with the activity name
		viewHolder.activityTitle.setText(R.string.profile);

		// Create the tabs
		initTabs();

		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);

		// Log the time it took us to do this
		Log.d(Onesocialweb.LOGTAG, "ProfileActivity.onCreate() - " + (Calendar.getInstance().getTimeInMillis() - start) + " ms");
	}

	@Override
	protected void onResume() {
		// Keep track of the timing for debugging purposes
		final long start = Calendar.getInstance().getTimeInMillis();

		// Be sure to call the super class.
		super.onResume();

		// Get the intent which has called this activity in order to get the
		userJid = getIntent().getStringExtra(PARAM_USER_JID); 
		
		// Bind with the OswService
		bindService(new Intent(ProfileActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);

		// Log the time it took us to do this
		Log.d(Onesocialweb.LOGTAG, "ProfileActivity.onResume() - " + (Calendar.getInstance().getTimeInMillis() - start) + " ms");
	}

	@Override
	protected void onPause() {
		// Cleanup our listeners and service bindings
		if (service != null) {
			service.removeConnectionStateListener(connectionListener);
			service.removeConnectionProcessListener(connectionListener);
			unbindService(mConnection);
		}
		
		// Be sure to call the super class.
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {	
		// Be sure to call the super class.
		super.onDestroy();
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

	private void initTabs() {

		// Initialize the tabs
		final TabHost tabHost = getTabHost();
		tabHost.addTab(tabHost.newTabSpec("tab_summary").setIndicator(getResources().getText(R.string.summary), getResources().getDrawable(R.drawable.selector_tab_summary)).setContent(R.id.summary));
		tabHost.addTab(tabHost.newTabSpec("tab_activities").setIndicator(getResources().getText(R.string.activities), getResources().getDrawable(R.drawable.selector_tab_activities)).setContent(R.id.posts));
		tabHost.addTab(tabHost.newTabSpec("tab_profile").setIndicator(getResources().getText(R.string.profile), getResources().getDrawable(R.drawable.selector_tab_profile)).setContent(R.id.fullProfile));
		tabHost.addTab(tabHost.newTabSpec("tab_lists").setIndicator(getResources().getText(R.string.lists), getResources().getDrawable(R.drawable.selector_tab_lists)).setContent(R.id.managePerson));

		tabHost.setCurrentTab(0);
	}

	private void update() {
		// Keep track of the timing for debugging purposes
		final long start = Calendar.getInstance().getTimeInMillis();
		
		// Get the singleton data loaders
		final AsyncProfileLoader asyncProfileLoader = AsyncProfileLoader.getInstance();
		final AsyncActivitiesLoader asyncActivitiesLoader = AsyncActivitiesLoader.getInstance();
				
		// Render the profile
		Profile cachedProfile = asyncProfileLoader.getProfileFromCache(userJid);
		if (cachedProfile != null) {
			setProfile(cachedProfile);
		} else {
			viewHolder.showProgressIcon();
			asyncProfileLoader.loadProfile(userJid, new ProfileCallback() {

				@Override
				public void profileLoaded(String userJid, Profile profile) {
					setProfile(profile);
					viewHolder.hideProgressIcon();
				}
			});
		}

		// Render the activities
		List<ActivityEntry> activities = asyncActivitiesLoader.getActivitiesFromCache(userJid);
		if (activities != null) {
			setActivities(activities);
		}

		// Always update the activities
		asyncActivitiesLoader.loadActivities(userJid, false, new ActivitiesCallback() {
			@Override
			public void activitiesLoaded(String userJid, List<ActivityEntry> activities) {
				setActivities(activities);
			}
		});

		// Log the time it took us to do this
		Log.d(Onesocialweb.LOGTAG, "ProfileActivity.update() - " + (Calendar.getInstance().getTimeInMillis() - start) + " ms");
	}

	private void setProfile(Profile profile) {
		this.profile = profile;
		updateProfileSummary();
		updateCompleteProfileTab();
		if (!userJid.equals(service.getBareJID())) {
			updateManagePersonTab();
		}
	}

	private void setActivities(List<ActivityEntry> activities) {
		this.activities = activities;
		updateActivitiesTab();
	}

	private void updateProfileSummary() {
		// User avatar
		viewHolder.avatar.setImageResource(R.drawable.osw_avatar);
		if (profile.getPhotoUri() != null) {
			String avatarUri = profile.getPhotoUri();
			Drawable imageDrawable = AsyncImageLoader.getInstance().getImageFromCache(avatarUri);
			if (imageDrawable != null) {
				viewHolder.avatar.setImageDrawable(imageDrawable);
			} else {
				AsyncImageLoader.getInstance().loadDrawable(avatarUri, new ImageCallback() {

					@Override
					public void imageLoaded(Drawable imageDrawable, String imageUrl) {
						if (imageDrawable != null) {
							viewHolder.avatar.setImageDrawable(imageDrawable);
						}
					}
				});
			}
		}

		// Roster groups for this user
		Roster roster = service.getRoster();
		if (roster != null) {
			RosterEntry rosterEntry = service.getRoster().getEntry(userJid);

			if (rosterEntry != null) {
				Iterator<RosterGroup> groups = rosterEntry.getGroups().iterator();
				StringBuffer listedAs = new StringBuffer();
				while (groups.hasNext()) {
					RosterGroup rosterGroup = (RosterGroup) groups.next();
					if (rosterGroup != null && rosterGroup.getName() != null && rosterGroup.getName().length() > 0) {
						listedAs.append(rosterGroup.getName());
					}
					if (groups.hasNext()) {
						listedAs.append(", ");
					}
				}
				if (listedAs.length() > 0) {
					viewHolder.listedLabel.setVisibility(View.VISIBLE);
					viewHolder.listed.setVisibility(View.VISIBLE);
					viewHolder.listed.setText(listedAs.toString());
				} else {
					viewHolder.listedLabel.setVisibility(View.GONE);
					viewHolder.listed.setVisibility(View.GONE);
				}
			}
		}

		// Username
		String username = userJid;
		if (profile.getFullName() != null && profile.getFullName().length() > 0) {
			// if there is a full name show the jid separately
			viewHolder.jid.setVisibility(View.VISIBLE);
			viewHolder.jid.setText(username);
			// then set the username
			username = profile.getFullName();
		}
		viewHolder.name.setVisibility(View.VISIBLE);
		viewHolder.name.setText(username);

		// Availability
		viewHolder.availability.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance()
				.getContactPresence(userJid)));
		viewHolder.availability.setVisibility(View.VISIBLE);

		// Short user bio
		if (profile.getNote() != null && profile.getNote().length() > 0) {
			String bio = profile.getNote();
			viewHolder.bioTitle.setVisibility(View.VISIBLE);
			viewHolder.bio.setVisibility(View.VISIBLE);
			viewHolder.bio.setText(bio.toString());
		}

		// Display the follow/unfollow buttons and other user actions
		if (!userJid.equals(service.getBareJID())) {
			// Add shout button if this is not you
			viewHolder.buttons.setVisibility(View.VISIBLE);
			viewHolder.shoutButton.setVisibility(View.VISIBLE);

			// Follow/unfollow
			List<String> subscriptions;
			try {
				subscriptions = service.getSubscriptions(service.getBareJID());
				if (subscriptions != null && subscriptions.contains(userJid)) {
					viewHolder.following.setVisibility(View.VISIBLE);
					viewHolder.followingTick.setVisibility(View.VISIBLE);
					viewHolder.unfollowButton.setVisibility(View.VISIBLE);
				} else {
					viewHolder.followButton.setVisibility(View.VISIBLE);
				}
			} catch (Exception e) {
			}
		} else {
			viewHolder.buttons.setVisibility(View.GONE);
		}

		// Click handlers
		viewHolder.followButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AsyncFollow().execute(userJid);
			}
		});

		viewHolder.unfollowButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AsyncUnfollow().execute(userJid);
			}
		});

		viewHolder.shoutButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ProfileActivity.this, ComposeActivity.class);
				intent.putExtra(ComposeActivity.RECIPIENT_JID, userJid);
				startActivity(intent);
			}
		});
	}

	private void updateActivitiesTab() {
		listModelAdapter = new ListModelAdapter(this);
		listModelAdapter.setItem(activities);
		viewHolder.listPosts.setAdapter(this.listModelAdapter);
		viewHolder.listPosts.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
				// Which activity was clicked and what was the ID ?
				final ActivityEntry activity = (ActivityEntry) adapter.getAdapter().getItem(position);
				final String activityId = activity.getId();

				// Store the activity in the shared object store
				onesocialweb.putSharedObject(activityId, activity);
				
				// Fire the intent
				Intent intent = new Intent(ProfileActivity.this, ViewActivity.class);
				intent.putExtra(ViewActivity.PARAM_ACTIVITY_ID, activityId);
				startActivity(intent);
			}
		});
	}

	private void updateCompleteProfileTab() {
		if (profile != null) {
			List<Field> list = profile.getFields();
			
			if (list.size() > 0) {
				for (Field field : list) {
					if (field.getName().equals("fn")) {
						viewHolder.fullNameTitle.setVisibility(View.VISIBLE);
						viewHolder.fullName.setVisibility(View.VISIBLE);
						viewHolder.fullName.setText(field.getValue());
					} else if (field.getName().equals("nickName")) {
						viewHolder.nickNameTitle.setVisibility(View.VISIBLE);
						viewHolder.nickName.setVisibility(View.VISIBLE);
						viewHolder.nickName.setText(field.getValue());
					} else if (field.getName().equals("birthday")) {
						viewHolder.birthdayTitle.setVisibility(View.VISIBLE);
						viewHolder.birthday.setVisibility(View.VISIBLE);
						viewHolder.birthday.setText(field.getValue());
					} else if (field.getName().equals("anniversary")) {
						viewHolder.anniversaryTitle.setVisibility(View.VISIBLE);
						viewHolder.anniversary.setVisibility(View.VISIBLE);
						viewHolder.anniversary.setText(field.getValue());
					} else if (field.getName().equals("gender")) {
						viewHolder.genderTitle.setVisibility(View.VISIBLE);
						viewHolder.gender.setVisibility(View.VISIBLE);
						viewHolder.gender.setText(field.getValue());
					} else if (field.getName().equals("note")) {
						viewHolder.bioTitle2.setVisibility(View.VISIBLE);
						viewHolder.bio2.setVisibility(View.VISIBLE);
						viewHolder.bio2.setText(field.getValue());
					} else if (field.getName().equals("preferredusername")) {
						viewHolder.prefUserNameTitle.setVisibility(View.VISIBLE);
						viewHolder.prefUserName.setVisibility(View.VISIBLE);
						viewHolder.prefUserName.setText(field.getValue());
					}
				}
			}
		}
		
		// If there is any field available show the message:
		// "no currently profile available"
		else {
			viewHolder.noProfAvailable.setVisibility(View.VISIBLE);
		}
	}

	private void updateManagePersonTab() {

		// Roster groups for this user
		Roster roster = service.getRoster();
		if (roster == null)
			return;

		RosterEntry rosterEntry = service.getRoster().getEntry(userJid);
		if (rosterEntry == null)
			return;

		final Collection<RosterGroup> userGroups = rosterEntry.getGroups();
		final Collection<RosterGroup> allGroups = service.getRoster().getGroups();
		if (userGroups == null || allGroups == null)
			return;

		// Clear the view group
		viewHolder.managePersonLayout.removeAllViews();

		// Add some instructions
		TextView txNext = new TextView(this);
		txNext.setText(R.string.add_or_remove);
		txNext.setTextColor(R.color.grey4);
		viewHolder.managePersonLayout.addView(txNext);

		// Add a layout to host the checkboxes
		LinearLayout checksLinearLayout = new LinearLayout(this);
		checksLinearLayout.setPadding(0, 8, 0, 8);
		checksLinearLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		checksLinearLayout.setOrientation(LinearLayout.VERTICAL);
		viewHolder.managePersonLayout.addView(checksLinearLayout);

		// Add the checkboxes
		for (final RosterGroup group : allGroups) {
			CheckBox cb = new CheckBox(this);

			// Check if user member of this group
			if (userGroups.contains(group)) {
				cb.setChecked(true);
			}

			// Custom checkbox to resize them
			cb.setText(group.getName());
			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
						new AsyncAddUserToGroup().execute(group.getName());
					} else {
						new AsyncRemoveUserFromGroup().execute(group.getName());
					}
				}
			});
			cb.setTextColor(R.color.grey4);
			checksLinearLayout.addView(cb);
		}

		// Add the new group widget
		final LinearLayout newGroupsLinearLayout = new LinearLayout(this);
		newGroupsLinearLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		newGroupsLinearLayout.setPadding(0, 6, 0, 6);

		final EditText newGroupEditText = new EditText(this);
		newGroupEditText.setSingleLine(true);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 1.0f);
		params.setMargins(0, 4, 5, 0);
		newGroupsLinearLayout.addView(newGroupEditText, params);

		final Button newGroupButton = new Button(this);
		newGroupButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		newGroupButton.setText(R.string.create_list);
		newGroupsLinearLayout.addView(newGroupButton);

		viewHolder.managePersonLayout.addView(newGroupsLinearLayout);
		
		newGroupButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AsyncCreateNewList().execute(newGroupEditText.getText().toString());
			}
		});
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
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

			// Update content
			update();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			//
		}
	};
	
	// function to add a user from a list
	private boolean addUserToGroup(String group) {
		Roster roster = service.getRoster();
		if (roster != null) {
			RosterEntry rosterEntry = roster.getEntry(userJid);
			RosterGroup rosterGroup = roster.getGroup(group);
			if (rosterGroup != null && rosterEntry != null) {
				try {
					rosterGroup.addEntry(rosterEntry);
					return true;
				} catch (XMPPException e) {
					return false;
				}
			}
		}
		
		return false;
	}

	// List Model Adapter to display the list of posted activities by the user
	private class ListModelAdapter extends BaseAdapter {

		private final LayoutInflater inflater;

		private final SimpleDateFormat dfToday, dfAnotherDay;

		private final Context context;

		private List<ActivityEntry> items = new ArrayList<ActivityEntry>();

		public ListModelAdapter(Context context) {
			// Cache the LayoutInflate to avoid asking for a new one each time.
			this.inflater = LayoutInflater.from(context);

			// Store the context so that we can launch activities from the list
			this.context = context;

			// Cache the date formatters
			this.dfToday = new SimpleDateFormat("'" + getResources().getString(R.string.today_at) + "' hh:mm a");
			this.dfAnotherDay = new SimpleDateFormat("d MMM yyyy '" + getResources().getString(R.string.at) + "' hh:mm a");
		}

		public void setItem(List<ActivityEntry> items) {
			this.items = items;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			if (items != null) {
				return items.size();
			} else {
				return 0;
			}
		}

		@Override
		public Object getItem(int position) {
			if (items != null) {
				return items.get(position);
			} else {
				return null;
			}
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			// A ViewHolder keeps references to children views to avoid
			// unneccessary calls to findViewById() on each row.
			ListViewHolder holder;

			// When convertView is not null, we can reuse it directly, there is
			// no need to reinflate it. We only inflate a new View when the
			// convertView supplied by ListView is null.
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.simplerow, null);

				// Creates a ViewHolder and store references to the two children
				// views we want to bind data to.
				holder = new ListViewHolder();
				holder.date = (TextView) convertView.findViewById(R.id.date);
				holder.comments = (TextView) convertView.findViewById(R.id.comments);
				holder.status = (TextView) convertView.findViewById(R.id.status);
				holder.recipients = (TextView) convertView.findViewById(R.id.recipients);
				holder.shoutedTo = (LinearLayout) convertView.findViewById(R.id.shoutedTo);
				holder.availability = (ImageView) convertView.findViewById(R.id.availability);
				holder.attachment = (ImageView) convertView.findViewById(R.id.attachment);

				// Keep track of the view holder as a tag of the view
				convertView.setTag(holder);
			} else {
				// Get the ViewHolder back to get fast access to the TextView
				// and the ImageView.
				holder = (ListViewHolder) convertView.getTag();
			}

			// Render the activity as quickly as possible
			ActivityEntry activity = items.get(position);

			if (activity != null) {
				// Timestamp
				if (activity.hasPublished()) {
					Date date = activity.getPublished();
					if (date.getDay() == Calendar.getInstance().getTime().getDay()) {
						holder.date.setText(dfToday.format(date));
					} else {
						holder.date.setText(dfAnotherDay.format(date));
					}
				}

				// Shouts
				if (activity.hasRecipients()) {
					StringBuffer recipientsText = new StringBuffer();
					Iterator<AtomReplyTo> recipients = activity.getRecipients().iterator();
					while (recipients.hasNext()) {
						AtomReplyTo recipient = recipients.next();
						if (recipient.hasHref()) {
							recipientsText.append(recipient.getHref());
						}
						if (recipients.hasNext()) {
							recipientsText.append(", ");
						}
					}

					holder.shoutedTo.setVisibility(View.VISIBLE);
					holder.recipients.setText(recipientsText);
				}

				// If the text to display is longer than 200 characters only
				// displays the first 197 + ...
				if (activity.hasTitle()) {
					String title = activity.getTitle();
					if (title.length() > 200) {
						title = title.substring(0, 197) + " ...";
					}
					holder.status.setText(title);
				}
				
				// Status comments number
				if (activity.hasReplies()) {
					holder.comments.setText("Comments: " + activity.getRepliesLink().getCount());
					holder.comments.setVisibility(View.VISIBLE);
				}

				// Add icons attachments if any
				if (activity.hasObjects()) {
					for (ActivityObject object : activity.getObjects()) {
						if (object.getType().equals(ActivityObject.PICTURE)
								|| object.getType().equals(ActivityObject.LINK)) {
							holder.attachment.setVisibility(View.VISIBLE);
						} else {
							holder.attachment.setVisibility(View.GONE);
						}
					}
				}
			}

			return convertView;
		}

		private class ListViewHolder {
			TextView date, comments, status, recipients;
			ImageView availability, attachment;
			LinearLayout shoutedTo;
		}
	}

	private class AsyncFollow extends AsyncTask<String, Void, Void> {

		private boolean isSubscribed = false;

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(ProfileActivity.this, "", getResources().getText(R.string.following), true);
		}

		@Override
		protected Void doInBackground(String... userJid) {

			try {
				isSubscribed = service.subscribe(userJid[0]);
			} catch (RequestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ConnectionRequired e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticationRequired e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
			if (isSubscribed) {
				viewHolder.followingTick.setVisibility(View.VISIBLE);
				viewHolder.following.setVisibility(View.VISIBLE);
				viewHolder.followButton.setVisibility(View.GONE);
				viewHolder.unfollowButton.setVisibility(View.VISIBLE);

				// Clean the manageTab and rewrite it because if the contact is
				// recently added the managePerson will be empty
				updateManagePersonTab();
			} else {
				Toast.makeText(ProfileActivity.this, getResources().getText(R.string.unable_to_follow), Toast.LENGTH_SHORT).show();
			}
			super.onPostExecute(result);
		}
	}

	private class AsyncUnfollow extends AsyncTask<String, Void, Void> {

		private boolean isUnsubscribed = false;

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(ProfileActivity.this, "", getResources().getText(R.string.unfollowing), true);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(String... userJid) {

			try {
				isUnsubscribed = service.unsubscribe(userJid[0]);
			} catch (RequestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ConnectionRequired e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticationRequired e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
			if (isUnsubscribed) {
				viewHolder.followingTick.setVisibility(View.GONE);
				viewHolder.following.setVisibility(View.GONE);
				viewHolder.unfollowButton.setVisibility(View.GONE);
				viewHolder.followButton.setVisibility(View.VISIBLE);
			} else {
				Toast.makeText(ProfileActivity.this, getResources().getText(R.string.unable_to_unfollow), Toast.LENGTH_SHORT).show();
			}
			super.onPostExecute(result);
		}

	}

	private class AsyncAddUserToGroup extends AsyncTask<String, Void, Void> {

		private boolean success = false;

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(ProfileActivity.this, "", getResources().getText(R.string.adding_person), true);
		}

		@Override
		protected Void doInBackground(String... group) {
			
			success = addUserToGroup(group[0]);

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
			if (!success) {
				Toast.makeText(ProfileActivity.this, getResources().getText(R.string.error_add_user_to_list),
								Toast.LENGTH_SHORT).show();
			} else {
				updateProfileSummary();
			}
			super.onPostExecute(result);
		}
	}

	private class AsyncRemoveUserFromGroup extends AsyncTask<String, Void, Void> {

		private boolean success = false;

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(ProfileActivity.this, "", getResources().getText(R.string.removing_person), true);
		}

		@Override
		protected Void doInBackground(String... group) {
			Roster roster = service.getRoster();
			if (roster != null) {
				RosterEntry rosterEntry = roster.getEntry(userJid);
				RosterGroup rosterGroup = roster.getGroup(group[0]);
				if (rosterGroup != null && rosterEntry != null) {
					try {
						rosterGroup.removeEntry(rosterEntry);
						success = true;
					} catch (XMPPException e) {
						success = false;
					}
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
			if (!success) {
				Toast.makeText(
								ProfileActivity.this, getResources().getText(R.string.error_remove_user_from_list),
								Toast.LENGTH_SHORT).show();
			} else {
				updateProfileSummary();
			}
			super.onPostExecute(result);
		}

	}

	private class AsyncCreateNewList extends AsyncTask<String, Void, Void> {

		private boolean newlistSuccess = false;
		private boolean addtogroupSuccess = false;

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(ProfileActivity.this, "", getResources().getText(R.string.creating_list), true);
		}

		@Override
		protected Void doInBackground(String... group) {
			Roster roster = service.getRoster();
			if (roster != null) {
				roster.createGroup(group[0]);
				addtogroupSuccess = addUserToGroup(group[0]);
				newlistSuccess = true;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
			if (!newlistSuccess) {
				Toast.makeText(ProfileActivity.this, getResources().getText(R.string.error_create_list),
						Toast.LENGTH_SHORT).show();
			} else {
				// show the new group
				updateManagePersonTab();
				// update 'On your lists'
				updateProfileSummary();
			}

			super.onPostExecute(result);
		}
	}

	private class ViewHolder extends CommonViewHolder {

		TextView activityTitle;
		TextView nolistPostAvailable;
		TextView name;
		TextView jid;
		TextView bio;
		TextView bioTitle;
		TextView listed;
		TextView listedLabel;
		TextView noProfAvailable;
		TextView fullNameTitle;
		TextView fullName;
		TextView following;
		TextView nickNameTitle;
		TextView nickName;
		TextView birthdayTitle;
		TextView birthday;
		TextView anniversaryTitle;
		TextView anniversary;
		TextView genderTitle;
		TextView gender;
		TextView bioTitle2;
		TextView bio2;
		TextView prefUserNameTitle;
		TextView prefUserName;

		Button shoutButton, unfollowButton, followButton;

		ImageView availability, followingTick, avatar;

		LinearLayout buttons, spacer, managePersonLayout, posts;
		
		RelativeLayout summary;
		
		ScrollView fullProfile, managePerson;
		
		RelativeLayout container;

		ListView listPosts;

		public ViewHolder(Activity activity) {
			super(activity);

			activityTitle = (TextView) findViewById(R.id.left_text);
			posts = (LinearLayout) findViewById(R.id.posts);
			fullProfile = (ScrollView) findViewById(R.id.fullProfile);
			managePerson = (ScrollView) findViewById(R.id.managePerson);
			managePersonLayout = (LinearLayout) findViewById(R.id.managePersonLayout);
			buttons = (LinearLayout) findViewById(R.id.buttons);

			// Basic profile items
			avatar = (ImageView) findViewById(R.id.avatar);
			name = (TextView) findViewById(R.id.name);
			jid = (TextView) findViewById(R.id.jid);
			availability = (ImageView) findViewById(R.id.availability);
			followingTick = (ImageView) findViewById(R.id.ProfileFollowingTick);
			following = (TextView) findViewById(R.id.ProfileFollowing);
			bio = (TextView) findViewById(R.id.ProfileBio);
			bioTitle = (TextView) findViewById(R.id.BioTitle);
			listedLabel = (TextView) findViewById(R.id.listedLabel);
			listed = (TextView) findViewById(R.id.listed);

			// Full profile items
			noProfAvailable = (TextView) findViewById(R.id.noProfileAvailable);
			fullNameTitle = (TextView) findViewById(R.id.fullNameTitle);
			fullName = (TextView) findViewById(R.id.fullName);

			nickNameTitle = (TextView) findViewById(R.id.nicknameTitle);
			nickName = (TextView) findViewById(R.id.nickname);

			birthdayTitle = (TextView) findViewById(R.id.birthdayTitle);
			birthday = (TextView) findViewById(R.id.birthday);

			anniversaryTitle = (TextView) findViewById(R.id.anniversaryTitle);
			anniversary = (TextView) findViewById(R.id.anniversary);

			genderTitle = (TextView) findViewById(R.id.genderTitle);
			gender = (TextView) findViewById(R.id.gender);

			bioTitle2 = (TextView) findViewById(R.id.bioTitle2);
			bio2 = (TextView) findViewById(R.id.bio2);

			prefUserNameTitle = (TextView) findViewById(R.id.prefUserNameTitle);
			prefUserName = (TextView) findViewById(R.id.prefUserName);

			listPosts = (ListView) findViewById(R.id.postslist);
			nolistPostAvailable = (TextView) findViewById(R.id.noPostAvailable);

			followButton = (Button) findViewById(R.id.followButton);
			unfollowButton = (Button) findViewById(R.id.unfollowButton);
			shoutButton = (Button) findViewById(R.id.shoutButton);
		}

	}

}
