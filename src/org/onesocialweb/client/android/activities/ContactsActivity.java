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
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.ContactsActivity.RosterAdapter.EntryModel;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.async.AsyncAvatarLoader;
import org.onesocialweb.client.android.async.AsyncProfileLoader;
import org.onesocialweb.client.android.async.AsyncAvatarLoader.AvatarCallback;
import org.onesocialweb.client.android.async.AsyncProfileLoader.ProfileCallback;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.vcard4.Profile;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class ContactsActivity extends ListActivity {
	
	// Menu identifiers
	private static final int MENU_INBOX = Menu.FIRST;
	private static final int MENU_COMPOSE = Menu.FIRST + 1;
	private static final int MENU_PREFERENCES = Menu.FIRST + 2;
	
	// A queue for loading avatars and profile
	private final AsyncAvatarLoader avatarLoader = AsyncAvatarLoader.getInstance();
	private final AsyncProfileLoader profileLoader = AsyncProfileLoader.getInstance();
	
	// Keep track of view elements
	private ViewHolder viewHolder;
	
	// Onesocialweb service
	private AndroidOswService service;
	
	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;
	
	// The adapter wrapping the roster and backing this view
	RosterAdapter rosterAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);

		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.contacts);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.oswcustomtitle);
		
		// Keep track of the view elements
		viewHolder = new ViewHolder(this);
			
		// Initialize the view
		initView();
		
		// Create the list adapter
		rosterAdapter = new RosterAdapter(ContactsActivity.this);
		setListAdapter(rosterAdapter);
		
		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);
	}
	
	@Override
	protected void onStart() {
		// Be sure to call the super class.
		super.onStart();
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();
		
		// Show the progress icon
		viewHolder.showProgressIcon();
		
		// Bind with the Notification service
		bindService(new Intent(this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onPause() {
		// If the service exist, it means we have compelted the onResume() and binding() before
		// triggering this onPause()
		if (service != null) {

			// Removed the listeners
			service.removeConnectionStateListener(connectionListener);
			service.removeConnectionProcessListener(connectionListener);

			// Unbind (we HAVE to do this otherwise the Droid is not happy :-)
			unbindService(mConnection);
		}

		// Be sure to call the super class.
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// Launch profileActivity with the user jid
		Intent intent = new Intent(this, ProfileActivity.class);
		EntryModel entry = (EntryModel) rosterAdapter.getItem(position);
		intent.putExtra(ProfileActivity.PARAM_USER_JID, entry.jid);
		startActivity(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_INBOX, MENU_INBOX, getResources().getText(R.string.inbox)).setIcon(R.drawable.ic_menu_home);
		menu.add(0, MENU_COMPOSE, MENU_COMPOSE, getResources().getText(R.string.composer)).setIcon(R.drawable.ic_menu_compose);
		menu.add(0, MENU_PREFERENCES, MENU_PREFERENCES, getResources().getText(R.string.settings)).setIcon(android.R.drawable.ic_menu_preferences);
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH) {
			// if the search panel is showed, hide it
			searchUser(viewHolder.searchEditText.getText().toString());
			return true;
		}
		
		// Else we leave it to the parent to deal with it (especially to take care of backspace);
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case MENU_INBOX:
			Intent intent = new Intent(this, InboxActivity.class);
			startActivity(intent);
			break;
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

		// Customize title bar with the activity name
		viewHolder.title.setText(R.string.contacts);
		
		//Display the search icon at the keyboard to make easier the search process
		viewHolder.searchEditText.setHint(R.string.search_user);
		viewHolder.searchEditText.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if(EditorInfo.IME_ACTION_SEARCH == actionId){
					searchUser(viewHolder.searchEditText.getText().toString());
				}
				return true;
			}
		});
	
		//Same funcionality to the to search button	
		viewHolder.searchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				searchUser(viewHolder.searchEditText.getText().toString());
			}
		});
		
	}

	private void searchUser(String jid) {
		if (jid.indexOf("@") == -1) {
			Toast.makeText(ContactsActivity.this, R.string.specify_user_domain, Toast.LENGTH_SHORT).show();
			return;
		} else {
			Intent intent = new Intent(ContactsActivity.this, ProfileActivity.class);
			intent.putExtra(ProfileActivity.PARAM_USER_JID, jid);
			startActivity(intent);
		}
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder iService) {

			// Get the service from the binder
			service = ((AndroidOswService.LocalBinder) iService).getService();

			// Register the various listeners
			service.addConnectionStateListener(connectionListener);
			service.addConnectionProcessListener(connectionListener);

			// Update the activity based on the current state
			if (service.isConnected() && service.isAuthenticated()) {
				connectionListener.onStateChanged(ConnectionState.connected);
			} else {
				connectionListener.onStateChanged(ConnectionState.disconnected);
			}
			
			// Prepare the list view and add an observer
			rosterAdapter.setRoster(service.getRoster());
			
			// TODO refactor: this is not MVC
			viewHolder.numberConnections.setText(getResources().getString(R.string.you_have) + " " + rosterAdapter.getCount() + " " + getResources().getString(R.string.connections));
			
			// We are done !
			viewHolder.hideProgressIcon();
		}

		public void onServiceDisconnected(ComponentName className) {
			//
		}
	};

	public class RosterAdapter extends BaseAdapter {

		private final LayoutInflater inflater;

		private final Context context;

		private final List<EntryModel> model = new ArrayList<EntryModel>();
		
		private final Handler handler = new Handler();

		public RosterAdapter(Context context) {
			// Cache the LayoutInflate to avoid asking for a new one each time.
			this.inflater = LayoutInflater.from(context);

			// Store the context so that we can launch activities from the list
			this.context = context;
		}

		@Override
		public View getView(int position, View convertView, final ViewGroup parent) {
			
			// A ViewHolder keeps references to children views to avoid
			// unneccessary calls to findViewById() on each row.
			ContactViewHolder holder;

			// When convertView is not null, we can reuse it directly, there is
			// no need to reinflate it. We only inflate a new View when the
			// convertView supplied by ListView is null.
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.contactrow, null);

				// Creates a ViewHolder and store references to the two children
				// views we want to bind data to.
				holder = new ContactViewHolder();
				holder.name = (TextView) convertView.findViewById(R.id.name);
				holder.listed = (TextView) convertView.findViewById(R.id.listed);
				holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
				holder.availability = (ImageView) convertView.findViewById(R.id.availability);
				
				// Keep track of the view holder as a tag of the view
				convertView.setTag(holder);
			} else {
				// Get the ViewHolder back to get fast access to the TextView
				// and the ImageView.
				holder = (ContactViewHolder) convertView.getTag();
			}
			
			// Render the item as quickly as possible
			final EntryModel entry = model.get(position);
			
			// Username
			if (entry.name != null) {
				holder.name.setText(entry.name);
			} else {
				holder.name.setText(entry.jid);
				holder.name.setTag("name_" + entry.jid);
				AsyncProfileLoader.getInstance().loadProfile(entry.jid, new ProfileCallback() {
					@Override
					public void profileLoaded(String userJid, Profile profile) {
						if (profile != null) {
							String username = profile.getFullName();
							if (username != null) {
								entry.name = username;
								TextView textView = (TextView) parent.findViewWithTag("name_" + entry.jid);
								if (textView != null) {
									textView.setText(entry.name);
								}
							}
						}
					}
				});
			}
			
			// Avatar
			if (entry.avatar != null) {
				holder.avatar.setImageDrawable(entry.avatar);
			} else {
				Drawable avatar = avatarLoader.getAvatarFromCache(entry.jid);
				if (avatar != null) {
					entry.avatar = avatar;
					holder.avatar.setImageDrawable(entry.avatar);
				} else {
					holder.avatar.setImageResource(R.drawable.osw_avatar);
					holder.avatar.setTag(entry.jid);
					AsyncAvatarLoader.getInstance().loadAvatar(entry.jid, new AvatarCallback() {
						
						@Override
						public void avatarLoaded(String userJid, Drawable avatarDrawable) {
							entry.avatar = avatarDrawable;
							ImageView imageView = (ImageView) parent.findViewWithTag(entry.jid);
							if (imageView != null) {
								imageView.setImageDrawable(avatarDrawable);
							}
						}
					});
				}
			}

			// List in which the user belongs
			if (entry.lists != null && !entry.lists.isEmpty()) {
				Iterator<String> lists = entry.lists.iterator();
				StringBuffer listsText = new StringBuffer(getResources().getString(R.string.listed_as) + " ");
				while (lists.hasNext()) {
					listsText.append(lists.next());
					if (lists.hasNext()) {
						listsText.append(", ");
					}
				}
				holder.listed.setVisibility(View.VISIBLE);
				holder.listed.setText(listsText);
			} else {
				holder.listed.setVisibility(View.GONE);
			}
			
			// Contact presence
			holder.availability.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance().getContactPresence(entry.jid)));

			return convertView;
		}

		@Override
		public int getCount() {
			return model.size();
		}

		@Override
		public Object getItem(int position) {
			return model.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
		
		public void setRoster(Roster roster) {			
			// Flush the model
			model.clear();
			
			// Rebuild as fast as possible
			if (roster != null) {
				for (RosterEntry rosterEntry : roster.getEntries()) {
					model.add(new EntryModel(rosterEntry));
				}
			}
			
			// Notify the view of the changes
			notifyDataSetChanged();
		}
		
		protected class EntryModel {
			String name;
			String jid;
			Drawable avatar;
			List<String> lists;
			
			public EntryModel(RosterEntry rosterEntry) {
				lists = new ArrayList<String>();
				for (RosterGroup group : rosterEntry.getGroups()) {
					String name = group.getName();
					if (name != null && name.length() > 0) {
						lists.add(group.getName());
					}
				}
				jid = rosterEntry.getUser();
			}
		}
		
		private class ContactViewHolder {
			TextView name, listed;
			ImageView avatar, availability;
		}
	}

	private class ViewHolder extends CommonViewHolder {

		EditText searchEditText;
		TextView title;
		Button searchButton;
		TextView numberConnections;
		
		public ViewHolder(Activity activity) {
			super(activity);
			title = (TextView) findViewById(R.id.left_text);
			searchEditText = (EditText) findViewById(R.id.findUserInput);
			searchButton = (Button) findViewById(R.id.findUserButton);
			numberConnections = (TextView) findViewById(R.id.numberConnections);
		}
		
	}

}
