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
import java.util.List;

import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.acl.AclAction;
import org.onesocialweb.model.acl.AclFactory;
import org.onesocialweb.model.acl.AclRule;
import org.onesocialweb.model.acl.AclSubject;
import org.onesocialweb.model.acl.DefaultAclFactory;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityFactory;
import org.onesocialweb.model.activity.ActivityObject;
import org.onesocialweb.model.activity.ActivityVerb;
import org.onesocialweb.model.activity.DefaultActivityFactory;
import org.onesocialweb.model.atom.AtomFactory;
import org.onesocialweb.model.atom.DefaultAtomFactory;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class CommentActivity extends Activity {
	
	// Key for Intents
	public static final String PARENT_ACTIVITY_ID = "parentActivityId";
	
	// Keep track of view elements
	private ViewHolder viewHolder;
	private EditText commentText;
	
	// Onesocialweb service
	private AndroidOswService service = null;
	
	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;
	
	private static final int DIALOG_POSTING_COMMENT = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);
		
		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.comment);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.oswcustomtitle);
		
		// Keep track of the view elements
		viewHolder = new ViewHolder(this);
		
		// Initialize the view
		initView();
		
		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);
		
		// Customize title bar with the activity name
		viewHolder.title.setText(R.string.comment);

		// Bind with the OswService
		bindService(new Intent(CommentActivity.this, AndroidOswService.class),
				mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();
		
		// Bind with the OswService
		bindService(new Intent(CommentActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);
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
	
	private void initView() {
		
		// Click handlers
		viewHolder.commentButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onCommentActivity();
			}
		});

		viewHolder.cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onCancel();
			}
		});		
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		switch (id) {
			case DIALOG_POSTING_COMMENT: {
				ProgressDialog dialog = new ProgressDialog(this);
				dialog.setMessage(getResources().getText(R.string.posting_comment));
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

	public void onCommentActivity() {
		
		// Need to be connected
		if (!service.isConnected() || !service.isAuthenticated()) {
			showError(getResources().getString(R.string.error_unable_to_comment));
			return;
		}
		
		// Need to actually post something
		final String comment = commentText.getText().toString();
		if (comment == null || comment.length() == 0) {
			showError(getResources().getString(R.string.error_empty_update));
			return;
		}

		// Good to go !
		ActivityFactory activityFactory = new DefaultActivityFactory();
		AtomFactory atomFactory = new DefaultAtomFactory();
		ActivityObject object = activityFactory.object();
		ActivityEntry entry = activityFactory.entry();
		String parentActivityId = getIntent().getStringExtra(PARENT_ACTIVITY_ID);
		Onesocialweb osw = (Onesocialweb) getApplication();
		ActivityEntry parentActivity = (ActivityEntry) osw.getSharedObject(parentActivityId);
		
		// Assign default privacy rules
		// TODO: handle other privacy rules
		AclFactory aclFactory = new DefaultAclFactory();
		List<AclRule> defaultRules = new ArrayList<AclRule>();
		AclRule rule = new DefaultAclFactory().aclRule();
		rule.addSubject(aclFactory.aclSubject(null, AclSubject.EVERYONE));
		rule.addAction(aclFactory.aclAction(AclAction.ACTION_VIEW, AclAction.PERMISSION_GRANT));
		defaultRules.add(rule);
		
		// Prepare comment body
		object.setType(ActivityObject.COMMENT);
		object.addContent(atomFactory.content(comment, "text/plain", null));
		entry.setPublished(Calendar.getInstance().getTime());
		entry.addVerb(activityFactory.verb(ActivityVerb.POST));
		entry.addObject(object);
		entry.setAclRules(defaultRules);
		entry.setTitle(comment);
		entry.setParentId(parentActivity.getId());
		entry.setParentJID(parentActivity.getActor().getUri());

		// Post the comment asynchronously
		new AsyncPostComment().execute(entry);
	}
	
	private class AsyncPostComment extends AsyncTask<ActivityEntry, Void, Void> {

		private boolean isPosted = false;
		
		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_POSTING_COMMENT);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(ActivityEntry... activity) {			
			try {
				if (service.postComment(activity[0])) {
					isPosted = true;
				}
			} catch (Exception e) {}

			if (isPosted) {
				//TODO: refresh view activity after return
				finish();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// We are done
			dismissDialog(DIALOG_POSTING_COMMENT);
			if (!isPosted) {
				Toast.makeText(CommentActivity.this, getResources().getString(R.string.error_unable_to_comment), Toast.LENGTH_SHORT).show();
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
			simulateKeyStroke(KeyEvent.KEYCODE_BACK, CommentActivity.this);
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
	
	private void showError(String error) {
		Toast.makeText(
				CommentActivity.this,
				error,
				Toast.LENGTH_SHORT).show();
	}
	
	private class ViewHolder extends CommonViewHolder {
		
		private final Button commentButton, cancelButton;
		private TextView title;
		
		public ViewHolder(Activity activity) {
			super(activity);
			title = (TextView) findViewById(R.id.left_text);
			commentButton = (Button) findViewById(R.id.commentButton);
			cancelButton = (Button) findViewById(R.id.cancelButton);
			commentText = (EditText) findViewById(R.id.commentText);
		}
		
	}
}