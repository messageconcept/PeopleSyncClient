/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import com.messageconcept.peoplesyncclient.Constants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class AccountSettings {
	private final static String TAG = "peoplesyncclient.syncadapter.AccountSettings";
	
	public final static String
		KEY_USERNAME = "user_name",
		KEY_PROVIDED_URL = "provided_url",
		KEY_AUTH_PREEMPTIVE = "auth_preemptive",
		KEY_ADDRESSBOOK_NAME = "address_book_",
		KEY_ADDRESSBOOK_COUNT = "address_book_count";
	
	Context context;
	AccountManager accountManager;
	Account account;
	
	
	public AccountSettings(Context context, Account account) {
		this.context = context;
		this.account = account;
		
		accountManager = AccountManager.get(context);
	}
	
	
	public static Bundle createBundle(ServerInfo serverInfo) {
		Bundle bundle = new Bundle();
		bundle.putString(KEY_USERNAME, serverInfo.getUserName());
		bundle.putString(KEY_AUTH_PREEMPTIVE, String.valueOf(serverInfo.isAuthPreemptive()));
		bundle.putString(KEY_PROVIDED_URL, serverInfo.getProvidedURL());
		bundle.putString(KEY_ADDRESSBOOK_COUNT, "0");

		return bundle;
	}
	
	public String addAddressBook(String title, String address_book_url){
		Bundle options = new Bundle();
		options.putString(AddressBookAccountSettings.KEY_TITLE, title);
		options.putString(AddressBookAccountSettings.KEY_ADDRESSBOOK_URL, address_book_url);
		options.putString(AddressBookAccountSettings.KEY_PARENT_ACCOUNT_NAME, account.name);
		options.putString(AddressBookAccountSettings.KEY_USERNAME, getUsername());
		options.putString(AddressBookAccountSettings.KEY_PASSWORD, getPassword());
		options.putString(AddressBookAccountSettings.KEY_AUTH_PREEMPTIVE, String.valueOf(getPreemptiveAuth()));
		String name = "";
		AccountManagerFuture<Bundle> accountResult = accountManager.addAccount(Constants.ACCOUNT_TYPE_ADDRESSBOOK, null, null, options, null, null, null);
		try{
			name = accountResult.getResult().getString(AccountManager.KEY_ACCOUNT_NAME);
			int count = Integer.valueOf(accountManager.getUserData(account, KEY_ADDRESSBOOK_COUNT));
			accountManager.setUserData(account, KEY_ADDRESSBOOK_NAME + String.valueOf(count), name);
			accountManager.setUserData(account, KEY_ADDRESSBOOK_COUNT, String.valueOf(count+1));
		} catch (Exception e) {
			Log.e(TAG, "Error while adding address book account", e);
		}
		return name;
	}
	
	public void removeAddressBook(Account addressBook, int index){
		if (addressBook.name.equals(accountManager.getUserData(account, KEY_ADDRESSBOOK_NAME + String.valueOf(index)))){
			String lastIndex = String.valueOf(Integer.valueOf(accountManager.getUserData(account, KEY_ADDRESSBOOK_COUNT))-1);
			// fill hole with last address book account and delete last
			accountManager.setUserData(account, KEY_ADDRESSBOOK_NAME + index, accountManager.getUserData(account, KEY_ADDRESSBOOK_NAME + lastIndex));
			accountManager.setUserData(account, KEY_ADDRESSBOOK_NAME + lastIndex, null);
			accountManager.setUserData(account, KEY_ADDRESSBOOK_COUNT, lastIndex);
			// delete address book account
			accountManager.removeAccount(addressBook, null, null);
		}
	}

	public String getUsername() {
		return accountManager.getUserData(account, KEY_USERNAME);
	}
	
	public void setUsername(String username) {
		accountManager.setUserData(account, KEY_USERNAME, username);
		int count = getAddressBookCount();
		Account addressBook;
		for (int i = 0; i < count; i++){
			addressBook = new Account(getAddressBookName(i), Constants.ACCOUNT_TYPE_ADDRESSBOOK);
			accountManager.setUserData(addressBook, KEY_USERNAME, username);
		}
	}
	
	public String getPassword() {
		return accountManager.getPassword(account);
	}
	
	public void setPassword(String password){
		accountManager.setPassword(account, password);
		int count = getAddressBookCount();
		Account addressBook;
		for (int i = 0; i < count; i++){
			addressBook = new Account(getAddressBookName(i), Constants.ACCOUNT_TYPE_ADDRESSBOOK);
			accountManager.setPassword(addressBook, password);
		}
	}
	
	public String getProvidedURL() {
		return accountManager.getUserData(account, KEY_PROVIDED_URL);
	}
	
	public void setProvidedURL(String provided_url) {
		accountManager.setUserData(account, KEY_PROVIDED_URL, provided_url);
	}

	public String getAddressBookName(int i) {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_NAME + String.valueOf(i));
	}
	
	public int getAddressBookCount(){
		return Integer.valueOf(accountManager.getUserData(account, KEY_ADDRESSBOOK_COUNT));
	}
	
	public boolean getPreemptiveAuth() {
		return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE));
	}
}
