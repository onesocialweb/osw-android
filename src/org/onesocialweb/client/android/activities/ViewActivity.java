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
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.OswPreferences;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.async.AsyncAvatarLoader;
import org.onesocialweb.client.android.async.AsyncImageLoader;
import org.onesocialweb.client.android.async.AsyncAvatarLoader.AvatarCallback;
import org.onesocialweb.client.android.async.AsyncImageLoader.ImageCallback;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.acl.AclRule;
import org.onesocialweb.model.acl.AclSubject;
import org.onesocialweb.model.activity.ActivityActor;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.atom.AtomContent;
import org.onesocialweb.model.atom.AtomLink;
import org.onesocialweb.model.atom.AtomReplyTo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ViewActivity extends Activity {

	// Intent parameters
	public static final String PARAM_ACTIVITY_ID = "activityId";
	public static final String PIC_ID = "sharedPictureId";
	public static final String PIC_BITMAP = "sharedPictureBitmap";

	// Menu identifiers
	private static final int MENU_INBOX = Menu.FIRST;
	private static final int MENU_CONTACTS = Menu.FIRST + 1;
	private static final int MENU_COMPOSE = Menu.FIRST + 2;
	private static final int MENU_PREFERENCES = Menu.FIRST + 3;

	// The view holder keep tracks of all view elements and helpers
	private ViewHolder viewHolder;

	// The application object
	private Onesocialweb onesocialweb;
	
	// Onesocialweb service
	private AndroidOswService service;

	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;
	
	// Dialogs
	private static final int DIALOG_DELETING = 0;
	private static final int DIALOG_MORE_ACTIONS = 1;
	
	// Dialog items
	private static final int ITEM_SHOUT = 0;
	private static final int ITEM_UPDATE = 1;
	private static final int ITEM_DELETE = 2;
	private static final int ITEM_REFRESH = 3;
	
	private boolean isOwner = false;
	
	// The id of the activity we must display
	private String activityId;
	
	// The jid of the activity author
	private String userJid;

	// The model for this view
	private Model model;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);

		// Keep a reference to the application
		onesocialweb = (Onesocialweb) getApplication();
		
		// Assign the view so that we can find the view objects by ID
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.activity);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.oswcustomtitle);

		// Keep track of the view elements
		viewHolder = new ViewHolder(this);

		// Initialize the generic connection listener (to update the UI)
		connectionListener = new ConnectionStatusListener(viewHolder);

		// Customize title bar with the activity name
		viewHolder.title.setText("Activity details");
		
		// Add click handler for the comment button
		viewHolder.commentButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ViewActivity.this, CommentActivity.class);
				intent.putExtra(CommentActivity.PARENT_ACTIVITY_ID, activityId);
				startActivity(intent);
			}
		});
		
		// Add click handler for the "More..." button
		viewHolder.moreButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showDialog(DIALOG_MORE_ACTIONS);
			}
		});
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();

		// Get the intent which has called this activity in order to get the
		activityId = getIntent().getStringExtra(PARAM_ACTIVITY_ID);
		
		// Bind with the Notification service
		bindService(new Intent(ViewActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);

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

	private void setActivity(ActivityEntry activity) {
		model = parseActivity(activity);
		
		// Check if the author is the same as person logged in
		if (activity.hasActor() && activity.getActor().hasUri()) {
			
			if (activity.getActor().getUri().equals(service.getBareJID())) {
				isOwner = true;
			} 
		}
		
		render();
		if (activity.hasReplies()) {
			refreshComments(activity);
			viewHolder.commentsList.setVisibility(View.VISIBLE);
		}
	}

	private Model parseActivity(ActivityEntry activity) {
		// Create a new model
		model = new Model();

		// Get the author JID
		if (activity.hasActor() && activity.getActor().hasUri()) {
			model.jid = activity.getActor().getUri();
			userJid = model.jid;
		}

		// Get the author name
		model.author = "unknown";
		if (activity.hasActor()) {
			ActivityActor actor = activity.getActor();
			if (actor.hasName()) {
				model.author = actor.getName();
			} else if (actor.hasUri()) {
				model.author = actor.getUri();
			}
		}

		// Get the date
		if (activity.hasPublished()) {
			model.published = activity.getPublished();
		}

		// Get the acls if any
		if (activity.hasAclRules()) {
			List<AclRule> rule = activity.getAclRules();
			for (AclRule aclRule : rule) {
				List<AclSubject> ruleSubject = aclRule.getSubjects();
				for (AclSubject aclSubject : ruleSubject) {
					if (aclSubject.getType().equals(AclSubject.EVERYONE)) {
						model.visibility.add(getResources().getString(R.string.everyone));
					} else if (aclSubject.getType().equals(AclSubject.GROUP)) {
						model.visibility.add(aclSubject.getName());
					}
				}
			}
		}

		// Get the title
		if (activity.hasTitle()) {
			model.status = activity.getTitle();
		}

		// Fetch the recipients
		if (activity.hasRecipients()) {
			for (AtomReplyTo recipient : activity.getRecipients()) {
				if (recipient.hasHref()) {
					model.recipients.add(recipient.getHref());
				}
			}
		}

		// Fetch the attachments
		if (activity.hasObjects()) {
			for (ActivityObject object : activity.getObjects()) {

				// Picture objects
				if (object.getType().equals(ActivityObject.PICTURE)) {
					PictureModel pictureModel = new PictureModel();
					// Grab the picture uri
					if (object.hasLinks()) {
						AtomLink link = object.getLinks().get(0);
						if (link.hasHref()) {
							pictureModel.uri = link.getHref();
						}
					}
					// Grab the picture title
					if (object.hasTitle()) {
						pictureModel.title = object.getTitle();
					}
					// Grab the picture legend
					if (object.hasContents()) {
						AtomContent content = object.getContents().get(0);
						if (content.hasValue()) {
							pictureModel.legend = content.getValue();
						}
					}
					model.pictures.add(pictureModel);
				}

				// Link objects
				else if (object.getType().equals(ActivityObject.LINK)) {
					LinkModel linkModel = new LinkModel();
					// Grab the link uri
					if (object.hasLinks()) {
						AtomLink link = object.getLinks().get(0);
						if (link.hasHref()) {
							linkModel.uri = link.getHref();
						}
					}
					// Grab the picture title
					if (object.hasTitle()) {
						linkModel.title = object.getTitle();
					}
					// Grab the picture legend
					if (object.hasContents()) {
						AtomContent content = object.getContents().get(0);
						if (content.hasValue()) {
							linkModel.legend = content.getValue();
						}
					}
					model.links.add(linkModel);
				}
			}
		}

		// Return the new model
		return model;
	}

	private void render() {

		// Display the jid
		if (model.jid != null) {
			viewHolder.jid.setText(model.jid);
		}
		
		// Display the author name
		if (model.author != null) {
			viewHolder.actorName.setText(model.author);
		}
		
		// Display availability
		viewHolder.availability.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance().getContactPresence(model.jid)));
		viewHolder.availability.setVisibility(View.VISIBLE); 

		// Display the recipients
		if (model.recipients.size() > 0 && model.recipients != null) {
			viewHolder.shoutedTo.setVisibility(View.VISIBLE);
			viewHolder.recipients.removeAllViewsInLayout();
			final Iterator<String> recipients = model.recipients.iterator();
			while (recipients.hasNext()) {
				// Show the new recipient to the user and enable to delete it
				LayoutInflater inflater = this.getLayoutInflater();
				final View rowView = inflater.inflate(R.layout.profilerecipient, null);
				
				TextView label = (TextView) rowView.findViewById(R.id.label);
				label.setText(recipients.next());
				ImageButton profileButton = (ImageButton) rowView.findViewById(R.id.profileButton);
				
				final String recipientJid = new String(label.getText().toString());
				
				profileButton.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(ViewActivity.this, ProfileActivity.class);
						intent.putExtra(ProfileActivity.PARAM_USER_JID, recipientJid);
						startActivity(intent);
					}
				});
				
				// Add to the container
				viewHolder.recipients.addView(rowView);
			}
		} else {
			viewHolder.shoutedTo.setVisibility(View.GONE);	
		}
		
		// Add a listener to trigger the profile view
		if (model.jid != null) {
			viewHolder.userInfo.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(ViewActivity.this, ProfileActivity.class);
					intent.putExtra(ProfileActivity.PARAM_USER_JID, model.jid);
					startActivity(intent);
				}
			});
		}

		// Display the avatar
		if (model.avatar != null) {
			viewHolder.avatar.setImageDrawable(model.avatar);
		} else {
			viewHolder.avatar.setImageResource(R.drawable.osw_avatar);
			AsyncAvatarLoader.getInstance().loadAvatar(model.jid, new AvatarCallback() {
				
				@Override
				public void avatarLoaded(String userJid, Drawable avatarDrawable) {
					model.avatar = avatarDrawable;
					viewHolder.avatar.setImageDrawable(avatarDrawable);
				}
			});
		}

		// Display the timestamp
		if (model.published != null) {
			SimpleDateFormat df = new SimpleDateFormat("d MMM yyyy '" + getResources().getString(R.string.at) + "' hh:mm a");
			viewHolder.date.setText(df.format(model.published));
		}

		// Display the activity title (which is in fact the status update)
		if (model.status != null) {
			viewHolder.status.setText(model.status);
			//clickable links
			Linkify.addLinks(viewHolder.status, Linkify.WEB_URLS);
			//mentions
			Pattern mentionPattern = Pattern.compile(
					            "@[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
					            "\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
					            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
					            ")+");
			String profileScheme = "osw-android://profile/";
			Linkify.addLinks(viewHolder.status, mentionPattern, profileScheme);



			
		}

		// Display the first picture (should evolve to a proper list)
		if (model.pictures != null && model.pictures.size() > 0) {
			final PictureModel pictureModel = model.pictures.get(0);
			if (pictureModel.legend != null) {
				viewHolder.pictureDesc.setText(pictureModel.legend);
				viewHolder.pictureDesc.setVisibility(View.VISIBLE);
			}
			if (pictureModel.drawable != null) {
				viewHolder.picture.setImageDrawable(pictureModel.drawable);
				viewHolder.pictureContainer.setVisibility(View.VISIBLE);
			} else {
				AsyncImageLoader.getInstance().loadDrawable(pictureModel.uri, new ImageCallback() {
					
					@Override
					public void imageLoaded(Drawable imageDrawable, String imageUrl) {
						pictureModel.drawable = imageDrawable;
						viewHolder.picture.setImageDrawable(imageDrawable);
						viewHolder.pictureContainer.setVisibility(View.VISIBLE);
					}
				});
			}
		} else {
			viewHolder.pictureContainer.setVisibility(View.GONE);
			viewHolder.pictureDesc.setVisibility(View.GONE);
		}

		// Display the first link (should evolve to a proper list)
		if (model.links != null && model.links.size() > 0) {
			LinkModel linkModel = model.links.get(0);
			if (linkModel.uri != null) {
				viewHolder.linkHref.setText(linkModel.uri);
				viewHolder.linkHref.setVisibility(View.VISIBLE);
			}
			if (linkModel.legend != null) {
				viewHolder.linkDesc.setText(linkModel.legend);
				viewHolder.linkDesc.setVisibility(View.VISIBLE);
			}
		} else {
			viewHolder.linkDesc.setVisibility(View.GONE);
			viewHolder.linkHref.setVisibility(View.GONE);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		switch (id) {
			case DIALOG_DELETING: {
				ProgressDialog dialog = new ProgressDialog(this);
				dialog.setMessage(getResources().getText(R.string.deleting));
				dialog.setIndeterminate(true);
				dialog.setCancelable(true);
				return dialog;
			}
			case DIALOG_MORE_ACTIONS: {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						ViewActivity.this);				
				CharSequence[] items = {"Shout", "Update", "Delete", "Refresh"};
				builder.setTitle(R.string.choose_action);
				builder.setItems(items, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						switch (item) {
						case ITEM_SHOUT:
							Intent intent = new Intent(ViewActivity.this, ComposeActivity.class);
							intent.putExtra(ComposeActivity.RECIPIENT_JID, userJid);
							startActivity(intent);
							break;
						
						case ITEM_UPDATE:
							if (isOwner) {								
								Intent intentUpdate = new Intent(ViewActivity.this, UpdateActivity.class);
								intentUpdate.putExtra(UpdateActivity.PARENT_ACTIVITY_ID, activityId);
								intentUpdate.putExtra(UpdateActivity.STATUS_TEXT, viewHolder.status.getText());
								startActivity(intentUpdate);
							} else {
								showError(getResources().getString(R.string.action_not_allowed));
							}
							break;
						
						case ITEM_DELETE:
							if (isOwner) {
								// delete the activity
								onDeleteActivity();
							} else {
								showError(getResources().getString(R.string.action_not_allowed));
							}
							break;
						case ITEM_REFRESH:
							//TODO: refresh view
							break;
						default:
							break;
						}
					}
				});
				AlertDialog alert = builder.create();
				return alert;
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
				setActivity((ActivityEntry) onesocialweb.getSharedObject(activityId));
			} else {
				connectionListener.onStateChanged(ConnectionState.disconnected);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			//
		}
	};
	
	public void onDeleteActivity() {
		
		// Need to be connected
		if (!service.isConnected() || !service.isAuthenticated()) {
			showError(getResources().getString(R.string.error_unable_to_delete));
			return;
		}

		// Post the status update asynchronously
		new AsyncDeleteActivity().execute();
	}
	
	private class AsyncDeleteActivity extends AsyncTask<ActivityEntry, Void, Void> {

		private boolean isDeleted = false;
		
		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_DELETING);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(ActivityEntry... activity) {			
			try {
				if (service.deleteActivity(activityId)) {
					isDeleted = true;
				}
			} catch (Exception e) {}

			// Back to the main activity: the inbox
			if (isDeleted) {
				Intent intent = new Intent(ViewActivity.this, InboxActivity.class);
				startActivity(intent);
				finish();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// We are done
			dismissDialog(DIALOG_DELETING);
			if (!isDeleted) {
				Toast.makeText(ViewActivity.this, getResources().getString(R.string.error_unable_to_delete), Toast.LENGTH_SHORT).show();
			}
			super.onPostExecute(result);
		}

	}
	
	private void showError(String error) {
		Toast.makeText(
				ViewActivity.this,
				error,
				Toast.LENGTH_SHORT).show();
	}
	
	private class ViewHolder extends CommonViewHolder {

		final TextView actorName, title, status, visibleTo, date, pictureDesc, linkHref, linkDesc, jid;
		final ImageView avatar, picture, availability;
		final LinearLayout userInfo, pictureContainer, shoutedTo, recipients;
		final Button commentButton, moreButton;
		final LinearLayout commentsList;
		
		private final SimpleDateFormat dfToday, dfAnotherDay;

		public ViewHolder(Activity activity) {
			super(activity);

			userInfo = (LinearLayout) findViewById(R.id.userInfo);
			pictureContainer = (LinearLayout) findViewById(R.id.pictureContainer);
			jid = (TextView) findViewById(R.id.jid);
			actorName = (TextView) findViewById(R.id.name);
			availability = (ImageView) findViewById(R.id.availability);
			date = (TextView) findViewById(R.id.date);
			visibleTo = (TextView) findViewById(R.id.visibleTo);
			status = (TextView) findViewById(R.id.status);
			shoutedTo = (LinearLayout) findViewById(R.id.shoutedTo);
			recipients = (LinearLayout) findViewById(R.id.recipients);
			picture = (ImageView) findViewById(R.id.pictureShared);
			avatar = (ImageView) findViewById(R.id.avatar);
			pictureDesc = (TextView) findViewById(R.id.imageDesc);
			linkHref = (TextView) findViewById(R.id.link);
			linkDesc = (TextView) findViewById(R.id.linkDesc);
			title = (TextView) findViewById(R.id.left_text);
			commentsList = (LinearLayout) findViewById(R.id.commentList);
			commentButton = (Button) findViewById(R.id.commentButton);
			moreButton = (Button) findViewById(R.id.moreButton);
			
			// Cache the date formatters
			dfToday = new SimpleDateFormat("hh:mm a");
			dfAnotherDay = new SimpleDateFormat("d MMM");
		}
	}

	private class Model {
		String jid;
		String author;
		String status;
		Date published;
		Drawable avatar;
		List<String> recipients = new ArrayList<String>();
		List<String> visibility = new ArrayList<String>();
		List<PictureModel> pictures = new ArrayList<PictureModel>();
		List<LinkModel> links = new ArrayList<LinkModel>();
	}

	private class PictureModel {
		String uri;
		String legend;
		String title;
		Drawable drawable;
	}

	private class LinkModel {
		String uri;
		String title;
		String legend;
	}
	
	private class Comment {
		String id, jid, status, author, timestamp;
		Boolean hasAttachments = false;
		Drawable avatar;
		
		TextView authorView, dateView, statusView;
		ImageView pictureView, availabilityView, attachmentView;
		
		public Comment(ActivityEntry activity) {
			
			final String userJid = (activity.hasActor() && activity.getActor().hasUri()) ? activity.getActor().getUri() : null;
			final String itemId = activity.hasId() ? activity.getId() : null;

			// Item id field and author JID have to be present, or item is skipped
			if (itemId == null || userJid == null) {
				return;
			} else {
				id = itemId;
				jid = userJid;
			}

			// Author field
			if (activity.hasActor()) {
				if (activity.getActor().hasName()) {
					author = activity.getActor().getName();
				} else if (userJid != null) {
					author = userJid;
				}
			}

			// Activity comment status field
			if (activity.hasTitle()) {
				String title = activity.getTitle();
				status = title;
			} else {
				status = "";
			}

			// Activity timestamp field
			if (activity.hasPublished()) {
				Date activityTimestamp = activity.getPublished();
				if (activityTimestamp.getDay() == Calendar.getInstance().getTime().getDay()) {
					timestamp = viewHolder.dfToday.format(activityTimestamp);
				} else {
					timestamp = viewHolder.dfAnotherDay.format(activityTimestamp);
				}
			}

			// Attachments
			if (activity.hasObjects()) {
				for (ActivityObject object : activity.getObjects()) {
					if (object.getType().equals(ActivityObject.PICTURE) || object.getType().equals(ActivityObject.LINK)) {
						hasAttachments = true;
					}
				}
			}
		}
	}
	
	private void refreshComments(ActivityEntry entry) {
		viewHolder.commentsList.removeAllViews();
		List<ActivityEntry> replies;
		try {
			replies = AndroidOswService.getInstance().getReplies(entry);
			// Comments order should be from oldest to newest
			Collections.reverse(replies);
			for (ActivityEntry reply : replies) {
				viewHolder.commentsList.addView(getCommentView(reply));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private View getCommentView(ActivityEntry activity) {
		
		final AsyncAvatarLoader avatarLoader = AsyncAvatarLoader.getInstance();

		LayoutInflater inflater = this.getLayoutInflater();
		View convertView = inflater.inflate(R.layout.row, null);

		final Comment item = new Comment(activity);
		item.authorView = (TextView) convertView.findViewById(R.id.author);
		item.dateView = (TextView) convertView.findViewById(R.id.date);
		item.statusView = (TextView) convertView.findViewById(R.id.status);
		item.pictureView = (ImageView) convertView.findViewById(R.id.picture);
		item.availabilityView = (ImageView) convertView.findViewById(R.id.availability);
		item.attachmentView = (ImageView) convertView.findViewById(R.id.attachment);

		// Bind the data efficiently with the holder.
		item.dateView.setText(item.timestamp);
		item.authorView.setText(item.author);
		item.statusView.setText(item.status);
		//clickable links
		Linkify.addLinks(item.statusView, Linkify.WEB_URLS);
		//mentions
		Pattern mentionPattern = Pattern.compile(
	            "@[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
	            "\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
	            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
	            ")+");
		String profileScheme = "osw-android://profile/";
		Linkify.addLinks(item.statusView, mentionPattern, profileScheme);
		
		item.availabilityView.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance().getContactPresence(item.jid)));

		if (item.hasAttachments) {
			item.attachmentView.setVisibility(View.VISIBLE);
		} else {
			item.attachmentView.setVisibility(View.GONE);
		}

		if (item.avatar != null) {
			item.pictureView.setImageDrawable(item.avatar);
		} else {
			item.pictureView.setImageResource(R.drawable.osw_avatar);
			item.pictureView.setTag(item.id);
			Drawable avatar = avatarLoader.getAvatarFromCache(item.jid); 
			if (avatar != null) {
				item.avatar = avatar;
				item.pictureView.setImageDrawable(avatar);
			} else {
				avatarLoader.loadAvatar(item.jid, new AvatarCallback() {
					@Override
					public void avatarLoaded(String userJid, Drawable avatarDrawable) {
						item.avatar = avatarDrawable;
						ImageView imageView = (ImageView) item.pictureView.findViewWithTag(item.id);
						if (imageView != null && avatarDrawable != null) {
							imageView.setImageDrawable(avatarDrawable);
						}
					}
				});
			}
		}

		convertView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Launch profileActivity with the user jid
				Intent intent = new Intent(ViewActivity.this, ProfileActivity.class);
				intent.putExtra(ProfileActivity.PARAM_USER_JID, item.jid);
				startActivity(intent);
			}
		});

		// Return the constructed view
		return convertView;
	}
}