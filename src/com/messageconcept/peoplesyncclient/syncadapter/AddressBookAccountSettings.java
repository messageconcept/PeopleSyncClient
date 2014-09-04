/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

public class AddressBookAccountSettings {	
	public final static String
		KEY_TITLE = "title",
		KEY_USERNAME = "user_name",
		KEY_PASSWORD = "password",
		KEY_AUTH_PREEMPTIVE = "auth_preemptive",
		KEY_PROVIDED_URL = "provided_url",
		KEY_PARENT_ACCOUNT_NAME = "parent_account_name",
		KEY_ADDRESSBOOK_URL = "addressbook_url",
		KEY_ADDRESSBOOK_CTAG = "addressbook_ctag";
	
	Context context;
	AccountManager accountManager;
	Account account;
	
	
	public AddressBookAccountSettings(Context context, Account account) {
		this.context = context;
		this.account = account;
		
		accountManager = AccountManager.get(context);
	}
	
	public static Bundle createBundle(String parent_account_name, String user_name, String password, boolean auth_preemptive) {
		Bundle bundle = new Bundle();
		bundle.putString(KEY_PARENT_ACCOUNT_NAME, parent_account_name);
		bundle.putString(KEY_USERNAME, user_name);
		bundle.putString(KEY_PASSWORD, password);
		bundle.putBoolean(KEY_AUTH_PREEMPTIVE, auth_preemptive);

		return bundle;
	}
	
	public String getTitle(){
		return accountManager.getUserData(account, KEY_TITLE);
	}
	
	public void setTitle(String title){
		accountManager.setUserData(account, KEY_TITLE, title);
	}
	
	public String getUserName() {
		return accountManager.getUserData(account, KEY_USERNAME);		
	}
	
	public void setUserName(String username){
		accountManager.setUserData(account, KEY_USERNAME, username);
	}
	
	public String getPassword() {
		return accountManager.getPassword(account);
	}
	
	public void setPassword(String password){
		accountManager.setPassword(account, password);
	}
	
	public String getParentAccountName(){
		return accountManager.getUserData(account, KEY_PARENT_ACCOUNT_NAME);
	}
	
	public boolean getPreemptiveAuth() {
		return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE));
	}
	
	public String getAddressBookURL() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_URL);
	}
	
	public String getAddressBookCTag() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_CTAG);
	}
	
	public void setAddressBookCTag(String cTag) {
		accountManager.setUserData(account, KEY_ADDRESSBOOK_CTAG, cTag);
	}
}
