/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;

public class AddressBookAccountAuthenticatorService extends Service {
	private static AccountAuthenticator accountAuthenticator;
	
	private AccountAuthenticator getAuthenticator() {
		if (accountAuthenticator != null)
			return accountAuthenticator;
		return accountAuthenticator = new AccountAuthenticator(this);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		if(intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT))
			return getAuthenticator().getIBinder();
		return null;
	}
	
	private static class AccountAuthenticator extends AbstractAccountAuthenticator {
		Context context;
		
		public AccountAuthenticator(Context context) {
			super(context);
			this.context = context;
		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
				throws NetworkErrorException {
			if (options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME).equals("com.messageconcept.peoplesyncclient")){
				String password = options.getString("password");
				String title = options.getString("title");
				
				options.remove("password");
				
				AccountManager accountManager = AccountManager.get(context);
				Account account = new Account(title, accountType);
	
				int namesTried = 0;
				while(!accountManager.addAccountExplicitly(account, password, options)) {
					account = new Account(title + "_" + String.valueOf(namesTried), accountType);
					namesTried++;
				}
				
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
				ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
				
				Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
				return result;
			} else {
				return null;
			}
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) 
				throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
			return null;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			return null;
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			return null;
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
				throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			return null;
		}
	}
}
