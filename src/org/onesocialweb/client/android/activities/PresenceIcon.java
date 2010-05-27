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

import org.jivesoftware.smack.packet.Presence;
import org.onesocialweb.client.android.R;

public class PresenceIcon {

	public static int getPresenceResource(Presence presence) {

		int presenceResource = R.drawable.ic_notavailable;

		// If the user is available, show the mode, if not show the type
		if (presence != null && presence.isAvailable()) {
			// User is online but we don't know his state yet
			presenceResource = R.drawable.ic_available;

			// If we know more, we leverage it
			if (presence.getMode() != null) {
				switch (presence.getMode()) {
				case available:
					presenceResource = R.drawable.ic_available;
					break;
				case away:
					presenceResource = R.drawable.ic_away;
					break;
				case chat:
					presenceResource = R.drawable.ic_chatty;
					break;
				case xa:
					presenceResource = R.drawable.ic_xaway;
					break;
				}
			}
		}

		return presenceResource;
	}
}
