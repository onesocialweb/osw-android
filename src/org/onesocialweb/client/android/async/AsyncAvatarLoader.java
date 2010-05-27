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

import org.onesocialweb.client.android.Onesocialweb;
import org.onesocialweb.client.android.async.AsyncImageLoader.ImageCallback;
import org.onesocialweb.client.android.async.AsyncProfileLoader.ProfileCallback;
import org.onesocialweb.model.vcard4.Profile;

import android.graphics.drawable.Drawable;
import android.util.Log;

public class AsyncAvatarLoader {
	
	private static AsyncAvatarLoader instance;
	
	private final HashMap<String, Drawable> avatarCache = new HashMap<String, Drawable>();
	
	public static AsyncAvatarLoader getInstance() {
		if (instance == null) {
			instance = new AsyncAvatarLoader();
		}
		return instance;
	}
	
	public Drawable getAvatarFromCache(String userJid) {
		if (avatarCache.containsKey(userJid)) {
			return avatarCache.get(userJid);
		} else {
			return null;
		}
	}
	
    public void loadAvatar(final String userJid, final AvatarCallback avatarCallback) {
    	final AsyncProfileLoader profileLoader = AsyncProfileLoader.getInstance();
    	final AsyncImageLoader imageLoader = AsyncImageLoader.getInstance();
       	
    	profileLoader.loadProfile(userJid, new ProfileCallback() {
			@Override
			public void profileLoaded(final String userJid, Profile profile) {
				if (profile != null) {
					String avatarUri = profile.getPhotoUri();
					if (avatarUri != null) {
						imageLoader.loadDrawable(avatarUri, new ImageCallback() {
							@Override
							public void imageLoaded(Drawable imageDrawable, String imageUrl) {
								if (imageDrawable != null) {
					                avatarCache.put(userJid, imageDrawable);
									avatarCallback.avatarLoaded(userJid, imageDrawable);
								}
							}
						});
					}
				}
			}
		});	
    }
    
    private AsyncAvatarLoader() {
    	//
    }

    public interface AvatarCallback {
        public void avatarLoaded(String userJid, Drawable avatarDrawable);
    }
}