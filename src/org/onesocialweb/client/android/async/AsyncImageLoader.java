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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.onesocialweb.client.android.Onesocialweb;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AsyncImageLoader {
	
	private static AsyncImageLoader instance;
	
	private final HashMap<String, SoftReference<Drawable>> imageCache = new HashMap<String, SoftReference<Drawable>>();
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	
	public static AsyncImageLoader getInstance() {
		if (instance == null) {
			instance = new AsyncImageLoader();
		}
		return instance;
	}

    public static Drawable loadImageFromUrl(String url) {
    	try {
    		Log.d(Onesocialweb.LOGTAG, "Fetching " + url);
			URL urlObject = new URL(url);
			InputStream is = urlObject.openStream();
			Bitmap b = BitmapFactory.decodeStream(new BufferedInputStream(is));
	    	if (b != null) {
	    		return new BitmapDrawable(b);
	    	} else {
	    		return null;
	    	}
		} catch (MalformedURLException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
    }
    
    public Drawable getImageFromCache(String url) {
    	if (imageCache.containsKey(url)) {
    		return imageCache.get(url).get();
    	} else {
    		return null;
    	}
    }
	
    public synchronized void loadDrawable(final String imageUrl, final ImageCallback imageCallback) {
    	
    	final Handler handler = new Handler() {
    		@Override
    		public void handleMessage(Message message) {
                imageCallback.imageLoaded((Drawable) message.obj, imageUrl);
    		}
    	};
    	
    	final Runnable task = new Runnable() {
    		@Override
    		public void run() {
    			// First check if not in the cache
    			if (imageCache.containsKey(imageUrl)) {
    	            SoftReference<Drawable> softReference = imageCache.get(imageUrl);
    	            Drawable drawable = softReference.get();
    	            if (drawable != null) {
    	                Message message = handler.obtainMessage(0, drawable);
    	                handler.sendMessage(message);
    	                return;
    	            }
    			}
    			
    			// Otherwise we load it from the network
                Drawable drawable = loadImageFromUrl(imageUrl);
                imageCache.put(imageUrl, new SoftReference<Drawable>(drawable));
                Message message = handler.obtainMessage(0, drawable);
                handler.sendMessage(message);
    		}
    	};
    	
    	executor.submit(task);
    }
    
    private AsyncImageLoader() {
    	//
    }

    public interface ImageCallback {
        public void imageLoaded(Drawable imageDrawable, String imageUrl);
    }
}