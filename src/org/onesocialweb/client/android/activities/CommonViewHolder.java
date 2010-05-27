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

import org.onesocialweb.client.android.R;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

public abstract class CommonViewHolder {

	final ImageView connectionStatus;

	final ProgressBar progressIcon;

	public CommonViewHolder(Activity activity) {
		progressIcon = (ProgressBar) activity.findViewById(R.id.progress_small_title);
		connectionStatus = (ImageView) activity.findViewById(R.id.conn_disconn_icon);
	}

	public void showProgressIcon() {
		progressIcon.setVisibility(View.VISIBLE);
	}

	public void hideProgressIcon() {
		progressIcon.setVisibility(View.GONE);
	}

	public void setConnected(boolean isConnected) {
		if (isConnected) {
			connectionStatus.setImageResource(R.drawable.ic_connected);
		} else {
			connectionStatus.setImageResource(R.drawable.ic_notconnected);
		}
	}
}
