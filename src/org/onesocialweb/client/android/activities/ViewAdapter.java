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
import java.util.List;

import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.async.AsyncAvatarLoader;
import org.onesocialweb.client.android.async.AsyncAvatarLoader.AvatarCallback;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityObject;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ViewAdapter extends BaseAdapter {

	private final LayoutInflater inflater;

	private final SimpleDateFormat dfToday, dfAnotherDay;

	private final List<Comment> comments = new ArrayList<Comment>();
	
	private final AsyncAvatarLoader avatarLoader = AsyncAvatarLoader.getInstance();
	
	private final Context context;

	public ViewAdapter(Context context) {
		// Cache the LayoutInflate to avoid asking for a new one each time.
		this.inflater = LayoutInflater.from(context);

		// Store the context so that we can launch activities from the list
		this.context = context;

		// Cache the date formatters
		this.dfToday = new SimpleDateFormat("hh:mm a");
		this.dfAnotherDay = new SimpleDateFormat("d MMM");
	}

	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {
		
		// A ViewHolder keeps references to children views to avoid unnecessary
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
			holder.author = (TextView) convertView.findViewById(R.id.author);
			holder.date = (TextView) convertView.findViewById(R.id.date);
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
		final Comment item = comments.get(position);
		holder.date.setText(item.timestamp);
		holder.author.setText(item.author);
		holder.status.setText(item.status);
		holder.availability.setImageResource(PresenceIcon.getPresenceResource(AndroidOswService.getInstance().getContactPresence(item.jid)));

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
		return comments.size();
	}

	@Override
	public Object getItem(int position) {
		return comments.get(position);
	}

	public Object getItem(String itemId) {
		for (Comment item : comments) {
			if (item.id.equals(itemId)) {
				return item;
			}
		}
		
		return null;
	}
	
	public ActivityEntry getActivity(int position) {
		return comments.get(position).activity;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	private class ViewHolder {
		TextView author;
		TextView date;
		TextView status;
		ImageView picture;
		ImageView availability;
		ImageView attachment;
	}

	private class Comment {
		ActivityEntry activity;
		String id;
		String jid;
		Boolean hasAttachments = false;
		String status;
		String author;
		String timestamp;
		Drawable avatar;
		
		public Comment(ActivityEntry activity) {
			
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
	
	public void refreshComments(ActivityEntry entry) {
		comments.clear();
		List<ActivityEntry> replies;
		try {
			replies = AndroidOswService.getInstance().getReplies(entry);
			for (ActivityEntry reply : replies) {
				comments.add(new Comment(reply));
			}
			// Comments order should be from oldest to newest
			Collections.reverse(comments);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		notifyDataSetChanged();
	}
}