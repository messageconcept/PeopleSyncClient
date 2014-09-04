/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.xbill.DNS.SRVRecord;

import com.messageconcept.peoplesyncclient.R;
import com.messageconcept.peoplesyncclient.syncadapter.DatabaseHelper;
import com.messageconcept.peoplesyncclient.syncadapter.EditAccountActivity;
import com.messageconcept.peoplesyncclient.syncadapter.EnterCredentialsFragment;
import com.messageconcept.peoplesyncclient.syncadapter.QueryServerDialogFragment.AutoConfig;
import com.messageconcept.peoplesyncclient.syncadapter.SRVRecorder;
import com.messageconcept.peoplesyncclient.syncadapter.SRVRecorder.FinishedCallBack;

import edu.emory.mathcs.backport.java.util.Collections;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class MainActivity extends Activity implements AccountManagerCallback<Bundle>, OnItemClickListener, FinishedCallBack, AutoConfig{
	private static final String TAG = "peopleSyncClient.MainActivity";
	private ListView accountList;
	private TextView txtNoAccounts;
	private Account[] accounts;
	private static final String[] accountTypes = new String[] {"com.android.email", "com.android.exchange"};
	private LinkedList<Account> autoConfigAccountList;
	private Iterator<Account> autoConfigIterator;
	private Account autoConfigCurrentAccount;
	private boolean autoConfigNothingFoundNotification;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		setTitle(getString(R.string.title_main));
	    
	    if (savedInstanceState == null) {
	    	autoConfigNothingFoundNotification = false;
	    	startAutoConfig();
	    }
	}
	
	@Override
	protected void onPostResume(){
		super.onPostResume();
		
		accountList = (ListView)findViewById(R.id.account_list);
		txtNoAccounts = (TextView)findViewById(R.id.txtNoAccounts);
		
		//update account list
    	AccountManager accountManager = AccountManager.get(this);
		accounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE);

		if (accounts.length > 0){		
			AccountListAdapter adapter = new AccountListAdapter(this, R.layout.account_list_item, accounts);
			accountList.setAdapter(adapter);
			accountList.setOnItemClickListener(this);
			accountList.setVisibility(ListView.VISIBLE);
			txtNoAccounts.setVisibility(TextView.GONE);
		} else {
			txtNoAccounts.setText(R.string.label_noAccounts);
			accountList.setVisibility(ListView.GONE);
			txtNoAccounts.setVisibility(TextView.VISIBLE);
		}
		
		autoConfigNothingFoundNotification = false;
		nextAutoConfig();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity, menu);
	    inflater.inflate(R.menu.main, menu);
	    return true;
	}

	
	public void addAccount(MenuItem item) {
		AccountManager accountManager = AccountManager.get(this);
		accountManager.addAccount(Constants.ACCOUNT_TYPE, null, null, null, this, this, null);
	}
	
	public void autoConfig(MenuItem item){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle("Auto Configuration");
		
		builder
			.setMessage("Should we search for server settings?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {	
					autoConfigNothingFoundNotification = true;
					startAutoConfig();
				}
			})
			.setNegativeButton("No", null).create().show();
	}
	
	public void startAutoConfig() {
		if (this.autoConfigAccountList == null){
			//create list of interesting accounts
			this.autoConfigAccountList = new LinkedList<Account>();
			AccountManager accountManager = AccountManager.get(this);
			for(String accountType : accountTypes){
				Collections.addAll(this.autoConfigAccountList, accountManager.getAccountsByType(accountType));
			}
			this.autoConfigIterator = this.autoConfigAccountList.iterator();
			//iterate through account list
			nextAutoConfig();
		}
	}
	
	public void nextAutoConfig(){
		if(this.autoConfigIterator != null && this.autoConfigIterator.hasNext()){
			DatabaseHelper dbHelper = new DatabaseHelper(this);
			this.autoConfigCurrentAccount = this.autoConfigIterator.next();
			// Should ask never again?
			if(!dbHelper.isStoredNever(this.autoConfigCurrentAccount.name)){
				Bundle args = new Bundle();
				args.putString(SRVRecorder.KEY_HOSTNAME, this.autoConfigCurrentAccount.name.replaceFirst(".*@", ""));
				SRVRecorder recorder = new SRVRecorder(args, this);
				
				recorder.startLoading(this);
			} else nextAutoConfig();
		} else {
			//all accounts looked up -> reset
			this.autoConfigIterator = null;
			this.autoConfigAccountList = null;
			if (this.autoConfigNothingFoundNotification) Toast.makeText(this, "Sorry, we could not find any server settings.", Toast.LENGTH_LONG).show();
		}
	}
	
	//ask for adding account because config was found
	public void askAddAccount(final String provided_url, final boolean use_ssl, final String accountName){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle("Found server");
		
		builder
			.setMessage("We found a PeopleSync Server for \"" + accountName + "\". Would You like to add?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {					
					Bundle args = new Bundle();
					
					args.putString(EnterCredentialsFragment.KEY_ACCOUNT_NAME, "PeopleSync " + accountName);
					args.putString(EnterCredentialsFragment.KEY_PROVIDED_URL, provided_url);
					args.putBoolean(EnterCredentialsFragment.KEY_USE_SSL, use_ssl);
					args.putString(EnterCredentialsFragment.KEY_USER_NAME, accountName);
					args.putBoolean(EditAccountActivity.KEY_AUTO_CONFIG, true);
					
					AccountManager accountManager = AccountManager.get(getBaseContext());
					accountManager.addAccount(Constants.ACCOUNT_TYPE, null, null, args, getThis(), getThis(), null);
				}
			})
			.setNegativeButton("Never", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					DatabaseHelper dbHelper = new DatabaseHelper(getBaseContext());
					
					dbHelper.storeNever(accountName);
					
					nextAutoConfig();
				}
			})
			.setNeutralButton("No", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					nextAutoConfig();
					dialog.dismiss();
				}
			}).create().show();
	}
	
	public void showWebsite(View view){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse("http://messageconcept.com/peoplesync#Technical_Specs"));
		startActivity(intent);
	}
	
	public void showHelp(MenuItem item) {
		Intent help = new Intent(this, HelpActivity.class);
		startActivity(help);
	}
	
	public void showInfo(MenuItem item) {
		Intent info = new Intent(this, InfoActivity.class);
		startActivity(info);
	}

	@Override
	public void run(AccountManagerFuture<Bundle> future) {
		Bundle result;
		try {
			result = future.getResult();
			
			if (result.containsKey(AccountManager.KEY_INTENT)){
				Intent intent = result.getParcelable(AccountManager.KEY_INTENT);
				startActivity(intent);
			}
		} catch (OperationCanceledException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		} catch (AuthenticatorException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View item, int position, long id) {
		Intent intent = new Intent(this, EditAccountActivity.class);
		intent.setAction(EditAccountActivity.ACTION_EDIT_ACCOUNT);
		intent.putExtra("account", accounts[position]);
		startActivity(intent);
	}

	@Override
	public void onLoadFinished(SRVRecord record) {
		if (record != null){
			String hostname = record.getTarget().toString().replaceAll("\\.$", "") + ":" + String.valueOf(record.getPort());
			this.autoConfigNothingFoundNotification = false;
			askAddAccount(hostname, record.getWeight() == 0, autoConfigCurrentAccount.name);
		} else this.nextAutoConfig();
	}
	
	private MainActivity getThis() {
		return this;
	}
}
