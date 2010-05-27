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
package org.onesocialweb.client.android;

import java.util.WeakHashMap;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

public class Onesocialweb extends Application {

	public static final String LOGTAG = "osw-client";

	private WeakHashMap<String, Object> sharedObjects = new WeakHashMap<String, Object>();
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d(LOGTAG, "Onesocialweb.onConfigurationChanged()");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(LOGTAG, "Onesocialweb.onCreate()");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.d(LOGTAG, "Onesocialweb.onLowMemory()");
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		Log.d(LOGTAG, "Onesocialweb.onTerminate()");
	}
	
	public void putSharedObject(String key, Object value) {
		sharedObjects.put(key, value);
	}
	
	public Object getSharedObject(String key) {
		return sharedObjects.get(key);
	}

}
