/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import com.messageconcept.peoplesyncclient.Constants;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {
	private static AccountAuthenticator accountAuthenticator;
	
	public final static String
		KEY_ACCOUNT_NAME = "account_name",
		KEY_FULL_DATA = "full_data";
		

	private AccountAuthenticator getAuthenticator() {
		if (accountAuthenticator != null)
			return accountAuthenticator;
		return accountAuthenticator = new AccountAuthenticator(this);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT))
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
		public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
			String[] requiredFeatures, Bundle options) throws NetworkErrorException {
			Intent intent = new Intent(context, EditAccountActivity.class);
			intent.setAction(EditAccountActivity.ACTION_ADD_ACCOUNT);
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
			intent.putExtras(options);
			Bundle bundle = new Bundle();
			bundle.putParcelable(AccountManager.KEY_INTENT, intent);
			return bundle;
		}
		
		@Override
		public Bundle getAccountRemovalAllowed (AccountAuthenticatorResponse response, Account account){
			
			AccountManager accountManager = AccountManager.get(context);
			AccountSettings settings = new AccountSettings(context, account);
			String addressBookName = settings.getAddressBookName(0);
			int i = 0;
			Account removeAccount;
			while(addressBookName != null){
				removeAccount = new Account(addressBookName, Constants.ACCOUNT_TYPE_ADDRESSBOOK);
				accountManager.removeAccount(removeAccount, null, null);
				i++;
				addressBookName = settings.getAddressBookName(i);
			}
			
			Bundle result = new Bundle();
			result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
			
			return result;
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
		public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
				Bundle options) throws NetworkErrorException {
			return null;
		}
	}
}
