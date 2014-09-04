/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import java.net.URI;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.messageconcept.peoplesyncclient.Constants;
import com.messageconcept.peoplesyncclient.HelpActivity;
import com.messageconcept.peoplesyncclient.InfoActivity;
import com.messageconcept.peoplesyncclient.R;

public class EditAccountActivity extends Activity{
	
	public static final String 
		ACTION_EDIT_ACCOUNT = "com.messageconcept.peopleSyncClient.action_edit_account",
		ACTION_ADD_ACCOUNT = "com.messageconcept.peopleSyncClient.action_add_account",
		KEY_AUTO_CONFIG = "isAutoConfig";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_account);
		
		if (savedInstanceState == null) {
			String action = this.getIntent().getAction();
			if(action.equals(ACTION_ADD_ACCOUNT) || action.equals(ACCOUNT_SERVICE)){
				setTitle(getString(R.string.title_add_account));
				
				Bundle args = this.getIntent().getExtras();
				if(args.getBoolean(KEY_AUTO_CONFIG)){
					EnterCredentialsFragment fragment = new EnterCredentialsFragment();
					fragment.setArguments(args);
					
					getFragmentManager().beginTransaction()
						.add(R.id.fragment_container, fragment, EnterCredentialsFragment.TAG)
						.commit();
				} else {
					AddAccountFragment fragment = new AddAccountFragment();
					fragment.setArguments(args);
					
					getFragmentManager().beginTransaction()
						.add(R.id.fragment_container, fragment, AddAccountFragment.TAG)
						.commit();
				}
			} else if(action.equals(ACTION_EDIT_ACCOUNT)){
				setTitle(getString(R.string.title_edit_account));
				
				Bundle accountDetails = new Bundle();
				Account account = (Account) this.getIntent().getExtras().getParcelable(EnterCredentialsFragment.KEY_ACCOUNT);
				AccountSettings settings = new AccountSettings(this, account);
				
				URI provided_url = URI.create(settings.getProvidedURL());
				Boolean use_SSL = provided_url.getScheme().equals("https");
				
				accountDetails.putString(EnterCredentialsFragment.KEY_ACCOUNT_NAME, account.name);
				accountDetails.putString(EnterCredentialsFragment.KEY_PROVIDED_URL, provided_url.toString().replace(provided_url.getScheme() + "://", ""));
				accountDetails.putBoolean(EnterCredentialsFragment.KEY_USE_SSL, use_SSL);
				accountDetails.putString(EnterCredentialsFragment.KEY_USER_NAME, settings.getUsername());
				accountDetails.putString(EnterCredentialsFragment.KEY_PASSWORD, settings.getPassword());
				
				EnterCredentialsFragment fragment = new EnterCredentialsFragment();
				fragment.setArguments(accountDetails);
				getFragmentManager().beginTransaction()
					.add(R.id.fragment_container, fragment, EnterCredentialsFragment.TAG)
					.commit();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		String action = this.getIntent().getAction();
		if(action.equals(ACTION_EDIT_ACCOUNT)) inflater.inflate(R.menu.edit_account, menu);
	    inflater.inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onBackPressed(){
		String action = this.getIntent().getAction();
		if (action.equals(ACTION_ADD_ACCOUNT)) this.finish();
		else super.onBackPressed();
	}

	public void showHelp(MenuItem item) {
		Intent help = new Intent(this, HelpActivity.class);
		startActivity(help);
	}
	
	public void showInfo(MenuItem item) {
		Intent info = new Intent(this, InfoActivity.class);
		startActivity(info);
	}
	
	public void showWebsite(View view){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(Constants.WEB_URL));
		startActivity(intent);
	}
	
	public boolean deleteAccount(MenuItem item) {
		Account account = (Account) this.getIntent().getExtras().getParcelable(EnterCredentialsFragment.KEY_ACCOUNT);
		AccountManager accountManager = AccountManager.get(this);
		accountManager.removeAccount(account, null, null);
		this.finish();
		return true;
	}
	
	void onSizeChanged (int w, int h, int oldw, int oldh){
		
	}
}
