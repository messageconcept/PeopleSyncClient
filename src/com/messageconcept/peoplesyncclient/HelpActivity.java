/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

public class HelpActivity extends Activity {
	
	@Override 
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_help);
		
		WebView txtInfo = (WebView) findViewById(R.id.txtHelp);
		
		txtInfo.loadData(getString(R.string.html_help_general), "text/html", "utf-8");

		setTitle("Info");
	    
	    if (savedInstanceState == null) {
	    }
	}
	
	public void showWebsite(View view){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(Constants.WEB_URL));
		startActivity(intent);
	}

}
