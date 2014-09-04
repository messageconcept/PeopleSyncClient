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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.messageconcept.peoplesyncclient.Constants;
import com.messageconcept.peoplesyncclient.R;
import com.messageconcept.peoplesyncclient.webdav.DavHttpClient;
import com.messageconcept.peoplesyncclient.webdav.WebDavResource;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

public class QueryServerDialogFragment extends DialogFragment implements LoaderCallbacks<ServerInfo> {
	public static final String
		KEY_ACCOUNT_NAME = "account_name",
		KEY_BASE_URL = "base_uri",
		KEY_USER_NAME = "user_name",
		KEY_PASSWORD = "password",
		KEY_AUTH_PREEMPTIVE = "auth_preemptive";
	
	ProgressBar progressBar;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog);
		setCancelable(false);

		Loader<ServerInfo> loader = getLoaderManager().initLoader(0, getArguments(), this);
		if (savedInstanceState == null)		// http://code.google.com/p/android/issues/detail?id=14944
			loader.forceLoad();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.query_server, container, false);
		return v;
	}

	@Override
	public Loader<ServerInfo> onCreateLoader(int id, Bundle args) {
		return new ServerInfoLoader(getActivity(), args);
	}

	@Override
	public void onLoadFinished(Loader<ServerInfo> loader, final ServerInfo serverInfo) {
		if(serverInfo == null){
			return;
		} else if (serverInfo.getErrorMessage() != null){
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		
			builder.setTitle("Error");
			
			builder
				.setMessage("The following error has happened:\n" +
						serverInfo.getErrorMessage() + 
						"\nShould we save the account config anyway?")
				.setCancelable(false)
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {					
						String action = getActivity().getIntent().getAction();
						if (!action.equals(EditAccountActivity.ACTION_EDIT_ACCOUNT)){
							String accountName = getArguments().getString(KEY_ACCOUNT_NAME);
							addAccount(accountName, serverInfo);
						} else {
							String accountName = getArguments().getString(KEY_ACCOUNT_NAME);
							updateAccount(accountName, serverInfo);
						}
					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						getDialog().dismiss();
					}
				}).create().show();
		} else {
			String action = getActivity().getIntent().getAction();
			if (!action.equals(EditAccountActivity.ACTION_EDIT_ACCOUNT)){
				String accountName = getArguments().getString(KEY_ACCOUNT_NAME);
				addAccount(accountName, serverInfo);
			} else {
				String accountName = getArguments().getString(KEY_ACCOUNT_NAME);
				updateAccount(accountName, serverInfo);
			}
			this.getDialog().dismiss();
		}
	}
	
	void addAccount(String accountName, ServerInfo serverInfo) {		
		Bundle options = AccountSettings.createBundle(serverInfo);
		AccountManager accountManager = AccountManager.get(getActivity());
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		
		if (accountManager.addAccountExplicitly(account, serverInfo.getPassword(), options)){
			ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
			ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
			getActivity().finish();
		} else {
			Toast.makeText(getActivity(), getString(R.string.error_account_name_exists), Toast.LENGTH_LONG).show();
			getActivity().finish();
		}
	}
	
	void updateAccount(String accountName, ServerInfo serverInfo){
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		AccountSettings settings = new AccountSettings(getActivity(), account);
		settings.setUsername(serverInfo.getUserName());
		settings.setPassword(serverInfo.getPassword());
		settings.setProvidedURL(serverInfo.getProvidedURL());
		getActivity().finish();
	}

	@Override
	public void onLoaderReset(Loader<ServerInfo> arg0) {
	}
	
	
	static class ServerInfoLoader extends AsyncTaskLoader<ServerInfo> {
		private static final String TAG = "peopleSyncClient.ServerInfoLoader";
		Bundle args;
		
		public ServerInfoLoader(Context context, Bundle args) {
			super(context);
			this.args = args;
		}

		@Override
		public ServerInfo loadInBackground() {
			ServerInfo serverInfo = new ServerInfo(
				args.getString(KEY_BASE_URL),
				args.getString(KEY_USER_NAME),
				args.getString(KEY_PASSWORD),
				args.getBoolean(KEY_AUTH_PREEMPTIVE)
			);
			
			CloseableHttpClient httpClient = DavHttpClient.create();
			try {
				// detect capabilities
				WebDavResource base = new WebDavResource(httpClient, new URI(serverInfo.getProvidedURL()), serverInfo.getUserName(),
						serverInfo.getPassword(), serverInfo.isAuthPreemptive(), true);
				base.options();
				
			} catch (URISyntaxException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_uri_syntax, e.getMessage()));
			}  catch (IOException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_io, e.getLocalizedMessage()));
			} catch (com.messageconcept.peoplesyncclient.webdav.HttpException e) {
				Log.e(TAG, "HTTP error while querying server info", e);
				if (e.getLocalizedMessage().equals("401 Unauthorized"))serverInfo.setErrorMessage(getContext().getString(R.string.exception_unauthorized));
				else serverInfo.setErrorMessage(getContext().getString(R.string.exception_http, e.getLocalizedMessage()));
			}
			
			return serverInfo;
		}
	}
	
	public interface AutoConfig{
		public void nextAutoConfig();
	}
}
