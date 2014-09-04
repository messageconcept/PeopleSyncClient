/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import java.util.ArrayList;
import java.util.List;

import org.xbill.DNS.SRVRecord;

import com.messageconcept.peoplesyncclient.R;
import com.messageconcept.peoplesyncclient.syncadapter.SRVRecorder.FinishedCallBack;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

public class AddAccountFragment extends Fragment implements OnCheckedChangeListener, TextWatcher, FinishedCallBack{
	public final static String
		TAG = "peoplesyncclient.syncadapter.AddAccountFragment";
	RadioButton choiceExisting;
	Spinner spinnerEmail;
	EditText editEmail;
	EditText editPassword;
	EditText editPasswordConfirm;
	ImageButton btnBack;
	ImageButton btnNext;
	ImageView passOK;
	ImageView passConfirmOK;
	
	List<String> emailList;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.add_account, container, false);
		
		//collect views
		choiceExisting = (RadioButton) v.findViewById(R.id.choice_existing);
		spinnerEmail = (Spinner) v.findViewById(R.id.spinner_accounts);
		editEmail = (EditText) v.findViewById(R.id.edit_email);
		editPassword = (EditText) v.findViewById(R.id.edit_pass);
		editPasswordConfirm = (EditText) v.findViewById(R.id.edit_pass_confirm);
		btnBack = (ImageButton) v.findViewById(R.id.btn_back);
		btnNext = (ImageButton) v.findViewById(R.id.btn_next);
		passOK = (ImageView) v.findViewById(R.id.passOK);
		passConfirmOK = (ImageView) v.findViewById(R.id.passConfirmOK);
		
		btnBack.setOnClickListener(new OnClickListener() {
			public void onClick(View v){
				back();
			}
		});
		btnNext.setOnClickListener(new OnClickListener() {
			public void onClick(View v){
				next();
			}
		});
		
		editEmail.addTextChangedListener(this);
		editPassword.addTextChangedListener(this);
		editPasswordConfirm.addTextChangedListener(this);
		
		choiceExisting.setOnCheckedChangeListener(this);
		AccountManager accountManager = AccountManager.get(this.getActivity());
		Account[] accounts = accountManager.getAccountsByType("com.android.exchange");
		if (accounts.length > 0){
			//fill email spinner		
			emailList = new ArrayList<String>();
			for (Account account : accounts) emailList.add(account.name);
		
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(),
					android.R.layout.simple_spinner_item, emailList);
			spinnerEmail.setAdapter(adapter);
			choiceExisting.setChecked(true);
		} else {
			choiceExisting.setChecked(false);
			choiceExisting.setEnabled(false);
		}
		
		// hook into action bar
		setHasOptionsMenu(true);
		
		validateInput();
		
		return v;
	}

	@Override
	public void onCheckedChanged(CompoundButton btn, boolean checked) {
		if (btn == this.choiceExisting) {
			this.editEmail.setEnabled(!checked);
			this.spinnerEmail.setEnabled(checked);
			validateInput();
		}
		
	}
	
	public void validateInput() {
		boolean ok = (choiceExisting.isChecked() || editEmail.getText().length() > 0);
		if (editPassword.getText().length() > 0){
			passOK.setImageResource(R.drawable.ok);
			passOK.setContentDescription(getString(R.string.label_ok));
		} else {
			passOK.setImageResource(R.drawable.failure);
			passOK.setContentDescription(getString(R.string.label_failure));
			ok = false;
		}
		
		if (editPassword.getText().toString().equals(editPasswordConfirm.getText().toString()) && editPasswordConfirm.getText().length() > 0){
			passConfirmOK.setImageResource(R.drawable.ok);
			passConfirmOK.setContentDescription(getString(R.string.label_ok));
		} else {
			passConfirmOK.setImageResource(R.drawable.failure);
			passConfirmOK.setContentDescription(getString(R.string.label_failure));
			ok = false;
		}
			
		btnNext.setEnabled(ok);
		buttonEnabledChange(btnNext, ok);
	}
	
	private void buttonEnabledChange(ImageButton button, Boolean enabled){
		if (enabled) button.setImageResource(R.drawable.navigation_forward);
		else button.setImageResource(R.drawable.navigation_forward_disabled);
	}
	
	public void back(){
		this.getActivity().onBackPressed();
	}
	
	public void next(){
		String email;
		
		if(choiceExisting.isChecked()) email = spinnerEmail.getSelectedItem().toString();
		else email = editEmail.getText().toString();

		//search for configuration with domain from email
		Bundle args = new Bundle();
		args.putString(SRVRecorder.KEY_HOSTNAME, email.replaceFirst(".*@", ""));
		
		SRVRecorder recorder = new SRVRecorder(args, this.getActivity());
	    recorder.startLoading((this));
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.add_account, menu);
	}

	@Override
	public void onLoadFinished(SRVRecord record) {
		Bundle args = new Bundle();
		String email;
		if(choiceExisting.isChecked()) email = spinnerEmail.getSelectedItem().toString();
		else email = editEmail.getText().toString();
		args.putString(EnterCredentialsFragment.KEY_ACCOUNT_NAME, "PeopleSync " + email);
		args.putString(EnterCredentialsFragment.KEY_USER_NAME, email);
		args.putString(EnterCredentialsFragment.KEY_PASSWORD, editPassword.getText().toString());
		if (record != null) {
			args.putString(EnterCredentialsFragment.KEY_PROVIDED_URL, record.getTarget().toString().replaceAll("\\.$", "") + ":" + String.valueOf(record.getPort()));
			args.putBoolean(EnterCredentialsFragment.KEY_USE_SSL, record.getWeight() == 0);
		}
		
		EnterCredentialsFragment enterCredentials = new EnterCredentialsFragment();
		enterCredentials.setArguments(args);
		
		getFragmentManager().beginTransaction()
			.replace(R.id.fragment_container, enterCredentials, EnterCredentialsFragment.TAG)
			.addToBackStack(null)
			.commitAllowingStateLoss();
	}

	@Override
	public void afterTextChanged(Editable s) {}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		validateInput();
	}
	
}
