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
package org.onesocialweb.client.android.service;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterGroup;
import org.onesocialweb.smack.OswServiceImp;

public class AsmackOswService extends OswServiceImp {
	
	public void sendRawData(String data) {
		connection.sendRawData(data);
	}
	
	public Roster getRoster(){	
		return connection.getRoster();
	}
	
	public Boolean createRosterGroup(String newGroupName){	
		RosterGroup rosterGroup = connection.getRoster().createGroup(newGroupName);
		rosterGroup.setName(newGroupName);
		return connection.getRoster().getGroups().add(rosterGroup);
	}
	
}
