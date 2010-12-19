package org.onesocialweb.client.android.activities;

import org.onesocialweb.client.ConnectionStateListener.ConnectionState;
import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.R;
import org.onesocialweb.client.android.activities.common.ConnectionStatusListener;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.activity.ActivityEntry;
import org.onesocialweb.model.activity.ActivityFactory;
import org.onesocialweb.model.activity.ActivityObject;
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

public class UpdateActivity extends Activity {
	
	// Key for Intents
	public static final String PARENT_ACTIVITY_ID = "parentActivityId";
	public static final String STATUS_TEXT = "statusText";
	
	// Keep track of view elements
	private ViewHolder viewHolder;
	
	// Onesocialweb service
	private AndroidOswService service = null;
	
	// A listener for connection state changes (to update the UI)
	private ConnectionStatusListener connectionListener;
	
	private static final int DIALOG_POSTING_UPDATE = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);
		
		// Prepare the window
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.simpleedit);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.oswcustomtitle);
		
		// Keep track of the view elements
		viewHolder = new ViewHolder(this);
		
		// Create the connection state listener
		connectionListener = new ConnectionStatusListener(viewHolder);
		
		// Initialize the view
		initView();
		
		// Customize title bar with the activity name
		viewHolder.title.setText(R.string.update);
		
		CharSequence statusText = getIntent().getStringExtra(STATUS_TEXT);
		viewHolder.statusText.setText(statusText);
		
		// Bind with the OswService
		bindService(new Intent(UpdateActivity.this, AndroidOswService.class),
				mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		// Be sure to call the super class.
		super.onResume();
		
		// Bind with the OswService
		bindService(new Intent(UpdateActivity.this, AndroidOswService.class), mConnection, Context.BIND_AUTO_CREATE);
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
		
		// Text on the post button
		viewHolder.updateButton.setText(R.string.update);
		
		// Click handlers
		viewHolder.updateButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onUpdateActivity();
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
			case DIALOG_POSTING_UPDATE: {
				ProgressDialog dialog = new ProgressDialog(this);
				dialog.setMessage(getResources().getText(R.string.posting_update));
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
	protected ServiceConnection mConnection = new ServiceConnection() {

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

	private void onUpdateActivity() {
		
		// Need to be connected
		if (!service.isConnected() || !service.isAuthenticated()) {
			showError(getResources().getString(R.string.error_unable_to_update));
			return;
		}
		
		// Need to actually post something
		final String statusUpdate = viewHolder.statusText.getText().toString();
		if (statusUpdate == null || statusUpdate.length() == 0) {
			showError(getResources().getString(R.string.error_empty_update));
			return;
		}
		
		String parentActivityId = getIntent().getStringExtra(PARENT_ACTIVITY_ID);
		Onesocialweb osw = (Onesocialweb) getApplication();
		ActivityEntry parentActivity = (ActivityEntry) osw.getSharedObject(parentActivityId);	

		if (parentActivity != null){				
			ActivityFactory activityFactory = new DefaultActivityFactory();
			AtomFactory atomFactory = new DefaultAtomFactory();
			parentActivity.setTitle(statusUpdate);
			//we update the object content too...
			for (ActivityObject object : parentActivity.getObjects()){
				if (object.getType().equals(ActivityObject.STATUS_UPDATE)){					
					parentActivity.getObjects().remove(object);
					object = activityFactory.object();
					object.setType(ActivityObject.STATUS_UPDATE);
					object.addContent(atomFactory.content(statusUpdate, "text/plain", null));
					parentActivity.addObject(object);
				}
			}		
			// Post the update asynchronously
			new AsyncPostUpdate().execute(parentActivity);
		}
	}
	
	public void onCancel() {
		new AsyncSimulateBackKey().execute();
	}
	
	private class AsyncPostUpdate extends AsyncTask<ActivityEntry, Void, Void> {

		private boolean isPosted = false;
		
		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_POSTING_UPDATE);
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(ActivityEntry... activity) {			
			try {
				if (service.updateActivity(activity[0])) {
					isPosted = true;
				}
			} catch (Exception e) {}

			if (isPosted) {
				finish();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// We are done
			dismissDialog(DIALOG_POSTING_UPDATE);
			if (!isPosted) {
				Toast.makeText(UpdateActivity.this, getResources().getString(R.string.error_unable_to_update), Toast.LENGTH_SHORT).show();
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
			simulateKeyStroke(KeyEvent.KEYCODE_BACK, UpdateActivity.this);
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
				UpdateActivity.this,
				error,
				Toast.LENGTH_SHORT).show();
	}
	
	private class ViewHolder extends CommonViewHolder {
		
		protected final Button updateButton, cancelButton;
		protected TextView title;
		protected EditText statusText;
		
		ViewHolder(Activity activity) {
			super(activity);
			title = (TextView) findViewById(R.id.left_text);
			updateButton = (Button) findViewById(R.id.postButton);
			cancelButton = (Button) findViewById(R.id.cancelButton);
			statusText = (EditText) findViewById(R.id.editText);
		}
	}

}
