/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.StringUtils;
import org.xbill.DNS.SRVRecord;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.messageconcept.peoplesyncclient.Constants;
import com.messageconcept.peoplesyncclient.R;
import com.messageconcept.peoplesyncclient.URIUtils;
import com.messageconcept.peoplesyncclient.syncadapter.SRVRecorder.FinishedCallBack;

public class EnterCredentialsFragment extends Fragment implements TextWatcher, OnClickListener, FinishedCallBack {
	public final static String
		TAG = "peoplesyncclient.syncadapter.EnterCredentialsFragment",
		KEY_ACCOUNT_NAME = "account_name",
		KEY_USER_NAME = "user_name",
		KEY_PASSWORD = "password",
		KEY_PROVIDED_URL = "provided_url",
		KEY_USE_SSL = "ssl",
		KEY_ACCOUNT = "account";
	
	String protocol;
	
	EditText editName, editServerName, editUsername, editPassword, editPasswordConfirm;
	CheckBox useSSL;
	ImageButton btnSave, btnCancel, btnReset;
	ImageView serverNameOK, passOK, passConfirmOK;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.enter_credentials, container, false);

		String action = getActivity().getIntent().getAction();
		
		editName = (EditText) v.findViewById(R.id.edit_account_name);
		editName.setEnabled(action.equals(EditAccountActivity.ACTION_ADD_ACCOUNT));
		
		editServerName = (EditText) v.findViewById(R.id.edit_server_name);
		editServerName.addTextChangedListener(this);
		serverNameOK = (ImageView) v.findViewById(R.id.serverNameOK);
		
		useSSL = (CheckBox) v.findViewById(R.id.check_ssl);
		
		editUsername = (EditText) v.findViewById(R.id.edit_username);
		editUsername.addTextChangedListener(this);
		
		editPassword = (EditText) v.findViewById(R.id.edit_pass);
		editPassword.addTextChangedListener(this);
		passOK = (ImageView) v.findViewById(R.id.passOK);
		
		editPasswordConfirm = (EditText) v.findViewById(R.id.edit_pass_confirm);
		editPasswordConfirm.addTextChangedListener(this);
		passConfirmOK = (ImageView) v.findViewById(R.id.passConfirmOK);
		
		btnSave = (ImageButton) v.findViewById(R.id.btn_save);
		btnSave.setOnClickListener(this);
		
		btnCancel = (ImageButton) v.findViewById(R.id.btn_cancel);
		btnCancel.setOnClickListener(this);
		
		btnReset = (ImageButton) v.findViewById(R.id.btn_reset);
		btnReset.setOnClickListener(this);
		
		Bundle args = getArguments();

		if (args != null) {
			if (args.containsKey(KEY_ACCOUNT_NAME)) editName.setText(args.getString(KEY_ACCOUNT_NAME));
			if (args.containsKey(KEY_USE_SSL)) useSSL.setChecked(args.getBoolean(KEY_USE_SSL));
			if (args.containsKey(KEY_PROVIDED_URL)) editServerName.setText(args.getString(KEY_PROVIDED_URL));
			if (args.containsKey(KEY_USER_NAME)) editUsername.setText(args.getString(KEY_USER_NAME));
			if (args.containsKey(KEY_PASSWORD)){
				editPassword.setText(args.getString(KEY_PASSWORD));
				editPasswordConfirm.setText(args.getString(KEY_PASSWORD));
			}
		}
		
		validateInput();

		return v;
	}

	void queryServer() {
		String action = getActivity().getIntent().getAction();
		if (action.equals(EditAccountActivity.ACTION_ADD_ACCOUNT)){
			AccountManager accountManager = AccountManager.get(getActivity());
			Account[] accounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE);
			for(Account account : accounts) {
				if (account.name.equals(editName.getText().toString())){
					Toast.makeText(getActivity(), getString(R.string.error_account_name_exists), Toast.LENGTH_LONG).show();
					return;
				}
			}
		}
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		
		Bundle args = new Bundle();
		
		String host_path = editServerName.getText().toString().replaceFirst("https?://", "");
		args.putString(QueryServerDialogFragment.KEY_ACCOUNT_NAME, editName.getText().toString());
		args.putString(QueryServerDialogFragment.KEY_BASE_URL, URIUtils.sanitize(protocol + host_path));
		args.putString(QueryServerDialogFragment.KEY_USER_NAME, editUsername.getText().toString());
		args.putString(QueryServerDialogFragment.KEY_PASSWORD, editPassword.getText().toString());
		args.putBoolean(QueryServerDialogFragment.KEY_AUTH_PREEMPTIVE, true);
		
		DialogFragment dialog = new QueryServerDialogFragment();
		dialog.setArguments(args);
	    dialog.show(ft, QueryServerDialogFragment.class.getName());
	}
	
	void askForReset(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
		
		builder.setTitle("Reset?");
		
		builder
			.setMessage("Should we try to reset server settings and discard all changes?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {					
					reset();
				}
			})
			.setNegativeButton("No", null).create().show();
	}

	void reset() {
		Bundle args = new Bundle();
		args.putString(SRVRecorder.KEY_HOSTNAME, this.editUsername.getText().toString().replaceFirst(".*@", ""));
		
		SRVRecorder recorder = new SRVRecorder(args, this.getActivity());
	    recorder.startLoading(this);
	}
	
	public void validateInput() {
		boolean ok = (editUsername.getText().length() > 0);
		if (editPassword.getText().length() > 0){
			passOK.setImageResource(R.drawable.ok);
			passOK.setContentDescription(getString(R.string.label_ok));
		} else {
			passOK.setImageResource(R.drawable.failure);
			passOK.setContentDescription(getString(R.string.label_failure));
			ok = false;
		}
		if (editPasswordConfirm.getText().length() > 0 && editPassword.getText().toString().equals(editPasswordConfirm.getText().toString())){
			passConfirmOK.setImageResource(R.drawable.ok);
			passConfirmOK.setContentDescription(getString(R.string.label_ok));
		} else {
			passConfirmOK.setImageResource(R.drawable.failure);
			passConfirmOK.setContentDescription(getString(R.string.label_failure));
			ok = false;
		}

		if(useSSL.isChecked()) protocol = "https://";
		else protocol = "http://";

		// check host name
		try {
			URI uri = new URI(URIUtils.sanitize(protocol + editServerName.getText().toString().replace("https?://", "")));
			if (StringUtils.isBlank(uri.getHost())){
				serverNameOK.setImageResource(R.drawable.failure);
				serverNameOK.setContentDescription(getString(R.string.label_failure));
				ok = false;
			}
		} catch (URISyntaxException e) {
			serverNameOK.setImageResource(R.drawable.failure);
			serverNameOK.setContentDescription(getString(R.string.label_failure));
			ok = false;
		}
		
		if (ok){
			serverNameOK.setImageResource(R.drawable.ok);
			serverNameOK.setContentDescription(getString(R.string.label_ok));
		}
			
		btnSave.setEnabled(ok);
		buttonEnabledChange(btnSave, ok);
	}
	
	private void buttonEnabledChange(ImageButton button, Boolean enabled){
		if (enabled) button.setImageResource(R.drawable.navigation_accept);
		else button.setImageResource(R.drawable.navigation_accept_disabled);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		validateInput();
	}

	@Override
	public void afterTextChanged(Editable s) {
	}

	@Override
	public void onClick(View v) {
		if(v == btnSave) queryServer();
		else if(v == btnCancel) this.getActivity().finish();
		else if(v == btnReset) askForReset();
	}

	@Override
	public void onLoadFinished(SRVRecord record) {
		if (record != null) {
			this.editServerName.setText(record.getTarget().toString().replaceAll("\\.$", "") + ":" + String.valueOf(record.getPort()));
			this.useSSL.setChecked(record.getWeight() == 0);
		} else {
			Toast.makeText(this.getActivity(), "Sorry, we could not find any server settings.", Toast.LENGTH_LONG).show();
		}
	}
}
