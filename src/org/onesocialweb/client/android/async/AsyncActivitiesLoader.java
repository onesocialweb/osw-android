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
package org.onesocialweb.client.android.async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.activity.ActivityEntry;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AsyncActivitiesLoader {

	private final HashMap<String, List<ActivityEntry>> activitiesCache = new HashMap<String, List<ActivityEntry>>();
	
	private static AsyncActivitiesLoader instance;
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	
	public static AsyncActivitiesLoader getInstance() {
		if (instance == null) {
			instance = new AsyncActivitiesLoader();
		}
		return instance;
	}
	
	public List<ActivityEntry> getActivitiesFromCache(String userJid) {
		if (activitiesCache.containsKey(userJid)) {
			return activitiesCache.get(userJid);
		} else {
			return null;
		}
	}

    public synchronized void loadActivities(final String userJid, final boolean useCache, final ActivitiesCallback activitiesCallback) {
    	    	
    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
                activitiesCallback.activitiesLoaded(userJid, (List<ActivityEntry>) message.obj);
    		}
    	};
    	
    	final Runnable task = new Runnable() {
    		@Override
    		public void run() {
    			// First check if not in the cache
    	    	if (useCache && activitiesCache.containsKey(userJid)) {
    	            List<ActivityEntry> activities = activitiesCache.get(userJid);
    	            if (activities != null) {
    	                Message message = handler.obtainMessage(0, activities);
    	                handler.sendMessage(message);
    	                return;
    	            }
    	    	}
    	    	
    	    	// Othwerwise we load from the network
    	    	Log.d(Onesocialweb.LOGTAG, "Fetching the activities of " + userJid);
                List<ActivityEntry> activities;
				try {
					activities = AndroidOswService.getInstance().getActivities(userJid);
				} catch (Exception e) {
					activities = new ArrayList<ActivityEntry>();
				}
                activitiesCache.put(userJid, activities);
                Message message = handler.obtainMessage(0, activities);
                handler.sendMessage(message);
    		}
    	};
    	
    	executor.submit(task);
    }
    
    private AsyncActivitiesLoader() {
    	//
    }

    public interface ActivitiesCallback {
        public void activitiesLoaded(String userJid, List<ActivityEntry> activities);
    }
}