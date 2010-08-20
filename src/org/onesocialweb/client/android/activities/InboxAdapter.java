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
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.onesocialweb.client.Inbox;
import org.onesocialweb.client.InboxEventHandler;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.async.AsyncAvatarLoader;
import org.onesocialweb.client.android.async.AsyncAvatarLoader.AvatarCallback;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.atom.AtomReplyTo;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InboxAdapter extends BaseAdapter implements InboxEventHandler {

	private final LayoutInflater inflater;

	private final SimpleDateFormat dfToday, dfAnotherDay;

	private final List<InboxItem> items = new ArrayList<InboxItem>();
	
	private final AsyncAvatarLoader avatarLoader = AsyncAvatarLoader.getInstance();
	
	private final Context context;
	
	private final Handler uiHandler;
	
	private Inbox mInbox;

	public InboxAdapter(Context context) {
		// Cache the LayoutInflate to avoid asking for a new one each time.
		this.inflater = LayoutInflater.from(context);

		// Store the context so that we can launch activities from the list
		this.context = context;

		// Cache the date formatters
		this.dfToday = new SimpleDateFormat("hh:mm a");
		this.dfAnotherDay = new SimpleDateFormat("d MMM");
		
		// Get a handler for changing the model in the UI thread
		this.uiHandler = new Handler();
	}

	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {
		
		// A ViewHolder keeps references to children views to avoid unneccessary
		// calls
		// to findViewById() on each row.
		ViewHolder holder;

		// When convertView is not null, we can reuse it directly, there is no
		// need to reinflate it. We only inflate a new View when the convertView
		// supplied by ListView is null.
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.row, null);

			// Creates a ViewHolder and store references to the two children
			// views
			// we want to bind data to.
			holder = new ViewHolder();
			holder.toLabel = (TextView) convertView.findViewById(R.id.tolabel);
			holder.author = (TextView) convertView.findViewById(R.id.author);
			holder.date = (TextView) convertView.findViewById(R.id.date);
			holder.shoutedTo = (LinearLayout) convertView.findViewById(R.id.shoutedTo);
			holder.recipients = (TextView) convertView.findViewById(R.id.recipients);
			holder.status = (TextView) convertView.findViewById(R.id.status);
			holder.picture = (ImageView) convertView.findViewById(R.id.picture);
			holder.availability = (ImageView) convertView.findViewById(R.id.availability);
			holder.attachment = (ImageView) convertView.findViewById(R.id.attachment);

			convertView.setTag(holder);
		} else {
			// Get the ViewHolder back to get fast access to the TextView
			// and the ImageView.
			holder = (ViewHolder) convertView.getTag();
		}

		// Bind the data efficiently with the holder.
		final InboxItem item = items.get(position);
		holder.date.setText(item.timestamp);
		holder.author.setText(item.author);
		holder.status.setText(item.status);
		holder.availability.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance().getContactPresence(item.jid)));

		if (item.recipients != null) {
			holder.shoutedTo.setVisibility(View.VISIBLE);
			holder.recipients.setText(item.recipients);
		} else {
			holder.shoutedTo.setVisibility(View.GONE);
		}

		if (item.hasAttachments) {
			holder.attachment.setVisibility(View.VISIBLE);
		} else {
			holder.attachment.setVisibility(View.GONE);
		}

		if (item.avatar != null) {
			holder.picture.setImageDrawable(item.avatar);
		} else {
			holder.picture.setImageResource(R.drawable.osw_avatar);
			holder.picture.setTag(item.id);
			Drawable avatar = avatarLoader.getAvatarFromCache(item.jid); 
			if (avatar != null) {
				item.avatar = avatar;
				holder.picture.setImageDrawable(avatar);
			} else {
				avatarLoader.loadAvatar(item.jid, new AvatarCallback() {
					@Override
					public void avatarLoaded(String userJid, Drawable avatarDrawable) {
						item.avatar = avatarDrawable;
						ImageView imageView = (ImageView) parent.findViewWithTag(item.id);
						if (imageView != null && avatarDrawable != null) {
							imageView.setImageDrawable(avatarDrawable);
						}
					}
				});
			}
		}

		holder.picture.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Launch profileActivity with the user jid
				Intent intent = new Intent(context, ProfileActivity.class);
				intent.putExtra(ProfileActivity.PARAM_USER_JID, item.jid);
				context.startActivity(intent);
			}
		});

		// Return the constructed view
		return convertView;
	}

	@Override
	public int getCount() {
		return items.size();
	}

	@Override
	public Object getItem(int position) {
		return items.get(position);
	}

	public Object getItem(String itemId) {
		for (InboxItem item : items) {
			if (item.id.equals(itemId)) {
				return item;
			}
		}
		
		return null;
	}
	
	public ActivityEntry getActivity(int position) {
		return items.get(position).activity;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public void setInbox(Inbox inbox) {
		// Remove handler on previous inbox if set
		if (mInbox != null) {
			mInbox.unregisterInboxEventHandler(this);
		}
		
		// Remember this new inbox
		mInbox = inbox;
		
		// Add a listener for inbox changes
		inbox.registerInboxEventHandler(this);
		
		// Initial refresh of the inbox
		refreshActivities(inbox.getEntries());
	}

	private class ViewHolder {
		TextView author;
		TextView date;
		TextView recipients;
		TextView status;
		TextView toLabel;
		ImageView picture;
		ImageView availability;
		ImageView attachment;
		LinearLayout shoutedTo;
	}

	private class InboxItem {
		ActivityEntry activity;
		String id;
		String jid;
		Boolean hasAttachments = false;
		String status;
		String author;
		String timestamp;
		String recipients;
		Drawable avatar;
		
		public InboxItem(ActivityEntry activity) {

			final AndroidOswService service = AndroidOswService.getInstance();
			final String userJid = (activity.hasActor() && activity.getActor().hasUri()) ? activity.getActor().getUri() : null;
			final String itemId = activity.hasId() ? activity.getId() : null;

			// Item id field and author JID have to be present, or item is skipped
			if (itemId == null || userJid == null) {
				return;
			} else {
				id = itemId;
				jid = userJid;
				this.activity = activity;
			}

			// Author field
			if (activity.hasActor()) {
				if (activity.getActor().hasName()) {
					author = activity.getActor().getName();
				} else if (userJid != null) {
					author = userJid;
				} else {
					author = "unknown author";
				}
			}

			// Activity status field
			if (activity.hasTitle()) {
				String title = activity.getTitle();
				if (title.length() > 200) {
					status = title.substring(0, 197) + "...";
				} else {
					status = title;
				}
			} else {
				status = "";
			}

			// Activity timestamp field
			if (activity.hasPublished()) {
				Date activityTimestamp = activity.getPublished();
				if (activityTimestamp.getDay() == Calendar.getInstance().getTime().getDay()) {
					timestamp = dfToday.format(activityTimestamp);
				} else {
					timestamp = dfAnotherDay.format(activityTimestamp);
				}
			}

			// List of recipients
			if (activity.hasRecipients()) {
				final Iterator<AtomReplyTo> activityRecipients = activity.getRecipients().iterator();
				StringBuffer recipientsText = new StringBuffer();
				int counter = 0;
				int nrOfRecipientsDisplayed = 2;
				
				while (activityRecipients.hasNext()) {
					AtomReplyTo recipient = activityRecipients.next();
					// we're only going to show two recipients if there are more than 2
					if (counter < nrOfRecipientsDisplayed) {
						if (recipient.hasHref()) {
							recipientsText.append(recipient.getHref());
							counter++;
						}
						if (activityRecipients.hasNext() && counter < nrOfRecipientsDisplayed) {
							recipientsText.append(", ");
						}
					} else {
						counter = 0;
						recipientsText.append(" and more ... ");
						break;
					}
				}
				recipients = recipientsText.toString();
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

	@Override
	public void onMessageDeleted(final ActivityEntry entry) {
		Runnable deleteActivity = new Runnable() {
			
			@Override
			public void run() {
				deleteActivity(entry);
			}
		};
		
		uiHandler.post(deleteActivity);
	}

	@Override
	public void onMessageReceived(final ActivityEntry entry) {
		Runnable updateActivity = new Runnable() {
			
			@Override
			public void run() {
				addActivity(entry);
			}
		};
		
		uiHandler.post(updateActivity);
	}
	
	@Override
	public void onMessageUpdated(ActivityEntry entry) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onRefresh(final List<ActivityEntry> activities) {
		Runnable refresh = new Runnable() {
			
			@Override
			public void run() {
				refreshActivities(activities);
			}
		};
		
		uiHandler.post(refresh);
	}

	private void addActivity(ActivityEntry activity) {
		items.add(0, new InboxItem(activity));
		notifyDataSetChanged();
	}
	
	private void deleteActivity(ActivityEntry activity) {
		String itemId = activity.getId();
		Object item = getItem(itemId);
		if (item != null) {
			items.remove(item);
			notifyDataSetChanged();
		}
	}
	
	private void refreshActivities(List<ActivityEntry> activities) {
		items.clear();
		for (ActivityEntry activityEntry : activities) {
			if ((activityEntry==null) || (activityEntry.getId()==null) || (activityEntry.getActor()==null) || (activityEntry.getActor().getUri()==null))				
				Log.d("osw-client", "null activity found!");						
			else
				items.add(new InboxItem(activityEntry));
		}
		notifyDataSetChanged();
	}
}