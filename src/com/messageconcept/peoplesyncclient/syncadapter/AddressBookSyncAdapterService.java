/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

import com.messageconcept.peoplesyncclient.Constants;
import com.messageconcept.peoplesyncclient.R;
import com.messageconcept.peoplesyncclient.webdav.DavException;
import com.messageconcept.peoplesyncclient.webdav.DavHttpClient;
import com.messageconcept.peoplesyncclient.webdav.DavIncapableException;
import com.messageconcept.peoplesyncclient.webdav.WebDavResource;
import com.messageconcept.peoplesyncclient.webdav.HttpPropfind.Mode;

import lombok.Synchronized;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class AddressBookSyncAdapterService extends Service{
	private static AddressBookSyncAdapter syncAdapter;

	@Override @Synchronized
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new AddressBookSyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder();
	}

	private static class AddressBookSyncAdapter extends AbstractThreadedSyncAdapter{
		private static final String TAG = "peopleSyncClient.AddressBookSyncAdapter";
		
		public AddressBookSyncAdapter (Context context) {
			super(context, true);
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
			AccountSettings settings = new AccountSettings(getContext(), account);
			
			String provided_url = settings.getProvidedURL();
			String user_name = settings.getUsername();
			String password = settings.getPassword();
			Boolean auth_preemptive = settings.getPreemptiveAuth();
			
			CloseableHttpClient httpClient = DavHttpClient.create();
			Log.i(TAG, "Perform sync of address books for account: " + account.name);
			try {
				// (1/6) detect capabilities
				WebDavResource base = new WebDavResource(httpClient, new URI(provided_url), user_name,
						password, auth_preemptive, true);
				base.options();
				
				if (!base.supportsMethod("PROPFIND") || !base.supportsMethod("REPORT") || !base.supportsDAV("addressbook"))
					throw new DavIncapableException(getContext().getString(R.string.no_carddav));
				
				// (2/6) get principal URL
				base.propfind(Mode.CURRENT_USER_PRINCIPAL);
				
				String principalPath = base.getCurrentUserPrincipal();
				if (principalPath == null)
					throw new DavIncapableException(getContext().getString(R.string.error_principal_path));
				
				// (3/6) get home sets
				WebDavResource principal = new WebDavResource(base, principalPath);
				principal.propfind(Mode.HOME_SETS);
				
				String pathAddressBooks = null;
				if (base.supportsDAV("addressbook")) {
					pathAddressBooks = principal.getAddressbookHomeSet();
					if (pathAddressBooks == null)
						throw new DavIncapableException(getContext().getString(R.string.error_home_set_address_books));
				
				// (4/6) get address books
					WebDavResource homeSetAddressBooks = new WebDavResource(principal, pathAddressBooks, true);
					homeSetAddressBooks.propfind(Mode.MEMBERS_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetAddressBooks.getMembers() != null)
						for (WebDavResource resource : homeSetAddressBooks.getMembers())
							if (resource.isAddressBook()) {
								Log.i(TAG, "Found address book: " + resource.getLocation().getRawPath());
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
									resource.isReadOnly(),
									resource.getLocation().toASCIIString(),
									resource.getDisplayName(),
									resource.getDescription(), resource.getColor()
								);
								info.setEnabled(true);
								addressBooks.add(info);
							}
					
				// (5/6) remove address book account, if address book no longer available
					Boolean addressBookFound;
					String addressBookAccount_url, addressBookName;
					Account addressBookAccount;
					AddressBookAccountSettings addressBookSettings;
					int count = settings.getAddressBookCount();
					
					for(int i = 0; i < count; i++){
						addressBookName = settings.getAddressBookName(i);
						addressBookAccount = new Account(addressBookName, Constants.ACCOUNT_TYPE_ADDRESSBOOK);
						addressBookSettings = new AddressBookAccountSettings(getContext(), addressBookAccount);
						if(account.name.equals(addressBookSettings.getParentAccountName())){
							addressBookAccount_url = addressBookSettings.getAddressBookURL();
							addressBookFound = false;
							for (ServerInfo.ResourceInfo addressBook : addressBooks){
								if (addressBookAccount_url.equals(addressBook.getURL())) {
									addressBookFound = true;
									break;
								}
							}
							if (!addressBookFound) {
								settings.removeAddressBook(addressBookAccount, i);
								i--; count--;
								Log.i(TAG, "Deleted address book: " + addressBookName);
							}
						}
					}
				
				// (6/6) add new address book accounts / update address book account titles
					count = settings.getAddressBookCount();
					
					for (ServerInfo.ResourceInfo addressBook : addressBooks) {
						addressBookFound = false;
						for(int i = 0; i<count; i++){
							addressBookName = settings.getAddressBookName(i);
							addressBookAccount = new Account(addressBookName, Constants.ACCOUNT_TYPE_ADDRESSBOOK);
							addressBookSettings = new AddressBookAccountSettings(getContext(), addressBookAccount);
							addressBookAccount_url = addressBookSettings.getAddressBookURL();
							if (account.name.equals(addressBookSettings.getParentAccountName()) && addressBookAccount_url.equals(addressBook.getURL())) {
								addressBookFound = true;
								if (!addressBook.getTitle().equals(addressBookSettings.getTitle())) addressBookSettings.setTitle(addressBook.getTitle());
								break;
							}
						}
						if (!addressBookFound) { // add new address book account
							String name = settings.addAddressBook(addressBook.getTitle(), addressBook.getURL());
							Log.i(TAG, "Added new address book: " + name);
						}
					}
				}
			} catch (URISyntaxException e) {
				Log.e(TAG, getContext().getString(R.string.exception_uri_syntax, e.getMessage()), e);
			} catch (IOException e) {
				Log.e(TAG, getContext().getString(R.string.exception_io, e.getLocalizedMessage()), e);
			} catch (DavException e) {
				Log.e(TAG, getContext().getString(R.string.exception_incapable_resource, e.getLocalizedMessage()), e);
			} catch (HttpException e) {
				Log.e(TAG, getContext().getString(R.string.exception_http, e.getLocalizedMessage()), e);
			}
		}
	}
}
