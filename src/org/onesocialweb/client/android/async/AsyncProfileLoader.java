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

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.service.AndroidOswService;
import org.onesocialweb.model.vcard4.DefaultProfile;
import org.onesocialweb.model.vcard4.Profile;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AsyncProfileLoader {

	private final HashMap<String, Profile> profileCache = new HashMap<String, Profile>();
	
	private static AsyncProfileLoader instance;
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	
	public static AsyncProfileLoader getInstance() {
		if (instance == null) {
			instance = new AsyncProfileLoader();
		}
		return instance;
	}
	
	public Profile getProfileFromCache(String userJid) {
		if (profileCache.containsKey(userJid)) {
			return profileCache.get(userJid);
		} else {
			return null;
		}
	}

    public synchronized void loadProfile(final String userJid, final ProfileCallback profileCallback) {
    	   	
    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
                profileCallback.profileLoaded(userJid, (Profile) message.obj);
    		}
    	};
    	
    	final Runnable task = new Runnable() {
    		@Override
    		public void run() {
    			// First check if not in the cache
    	    	if (profileCache.containsKey(userJid)) {
    	            Profile profile = profileCache.get(userJid);
    	            if (profile != null) {
    	                Message message = handler.obtainMessage(0, profile);
    	                handler.sendMessage(message);
    	                return;
    	            }
    	    	}
    	    	
    	    	// Othwerwise we load from the network
    	    	Log.d(Onesocialweb.LOGTAG, "Fetching the profile of " + userJid);
                Profile profile = AndroidOswService.getInstance().getProfile(userJid);
                profileCache.put(userJid, profile);
                Message message = handler.obtainMessage(0, profile);
                handler.sendMessage(message);
    		}
    	};
    	
    	executor.submit(task);
    }
    
    private Profile defaultProfile(String userJid) {
    	Profile profile = new DefaultProfile();
    	profile.setUserId(userJid);
    	return profile;
    }
    
    private AsyncProfileLoader() {
    	//
    }

    public interface ProfileCallback {
        public void profileLoaded(String userJid, Profile profile);
    }
}