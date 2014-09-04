/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient;

import com.messageconcept.peoplesyncclient.syncadapter.AddressBookAccountSettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AccountListAdapter extends ArrayAdapter<Account>{

	Context context;
	int resource;
	Account[] accounts = null;
	
	public AccountListAdapter(Context context, int resource, Account[] accounts) {
		super(context, resource, accounts);
		
		this.context = context;
		this.resource = resource;
		this.accounts = accounts;
	}
	
	@Override
	public View getView (int position, View convertView, ViewGroup parent){
		View row = convertView;
		AccountHolder holder = null;
		if (row == null){
			LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(this.resource, parent, false);
           
            holder = new AccountHolder();
            holder.txtAccountName = (TextView)row.findViewById(R.id.txtAccountName);
            holder.listAddressBooks = (LinearLayout)row.findViewById(R.id.listAddressBooks);
            
            row.setTag(holder);
        }
        else
        {
            holder = (AccountHolder)row.getTag();
        }
       
		holder.listAddressBooks.removeAllViews();
		
        Account account = accounts[position];
        holder.txtAccountName.setText(account.name);
        //scrolling for long texts
        holder.txtAccountName.setSelected(true);
        
        AccountManager accountManager = AccountManager.get(context);
        Account[] addressBookAccounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE_ADDRESSBOOK);
        TextView txtAddressBook;
        AddressBookAccountSettings addressBookSettings;
        for (Account addressBookAccount : addressBookAccounts) {
        	addressBookSettings = new AddressBookAccountSettings(getContext(), addressBookAccount);
        	if (account.name.equals(addressBookSettings.getParentAccountName())){
	        	txtAddressBook = new TextView(context);
	        	txtAddressBook.setText(addressBookAccount.name);
	        	holder.listAddressBooks.addView(txtAddressBook);
        	}
        }
        if (holder.listAddressBooks.getChildCount() == 0){
        	txtAddressBook = new TextView(context);
        	txtAddressBook.setText(R.string.label_noAddressBooks);
        	holder.listAddressBooks.addView(txtAddressBook);
        }
       
        return row;
	}
	
	static class AccountHolder
    {
        TextView txtAccountName;
        LinearLayout listAddressBooks;
    }
	
}
