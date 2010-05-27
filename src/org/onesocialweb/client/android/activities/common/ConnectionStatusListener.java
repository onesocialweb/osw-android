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
package org.onesocialweb.client.android.activities.common;

import org.onesocialweb.client.ConnectionStateListener;
import org.onesocialweb.client.android.activities.CommonViewHolder;

import android.os.Handler;

public class ConnectionStatusListener implements ConnectionProcessListener, ConnectionStateListener {

	private final Handler handler = new Handler();

	private final CommonViewHolder viewHolder;

	public ConnectionStatusListener(CommonViewHolder viewHolder) {
		this.viewHolder = viewHolder;
	}

	@Override
	public void onStateChanged(ConnectionProcessState newState) {
		switch (newState) {
		case connecting:
			handler.post(new Runnable() {
				public void run() {
					viewHolder.showProgressIcon();
					viewHolder.setConnected(false);
				}
			});
			break;

		case not_connecting:
			handler.post(new Runnable() {
				public void run() {
					viewHolder.hideProgressIcon();
				}
			});
			break;
		}
	}

	@Override
	public void onStateChanged(ConnectionState newState) {
		switch (newState) {
		case disconnected:
			break;

		case disconnectedOnError:
			break;

		case connected:
			handler.post(new Runnable() {
				public void run() {
					viewHolder.setConnected(true);
					viewHolder.hideProgressIcon();
				}
			});
			break;
		}
	};
};