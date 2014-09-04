/*******************************************************************************
* Copyright (c) 2014 by messageconcept software GmbH, Cologne, Germany.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;

public class SRVRecorder implements LoaderCallbacks<SRVRecord>{
	public static final String
		KEY_HOSTNAME = "hostname";
	private static final String TAG = "peopleSyncClient.SRVRecorder";
	private static final String SRV_APPLICATION = "_peoplesync._tcp.";
	private Context context;
	private Bundle args;
	private FinishedCallBack callback;
	
	public SRVRecorder(Bundle args, Context context) {
		this.context = context;
		this.args = args;
	}
	
	public void startLoading(FinishedCallBack callback){
		this.callback = callback;
		Loader<SRVRecord> loader = callback.getLoaderManager().initLoader(0, args, this);
		Log.d(TAG, "Start Loading...");
		loader.forceLoad();
	}
	
	@Override
	public Loader<SRVRecord> onCreateLoader(int id, Bundle args) {
		Log.i(TAG, "onCreateLoader");
		return new SRVRecordLoader(context, args);
	}

	@Override
	public void onLoadFinished(Loader<SRVRecord> loader, SRVRecord records) {
		Log.d(TAG, "Finished Loading.");
		callback.getLoaderManager().destroyLoader(0);
		callback.onLoadFinished(records);
	}

	@Override
	public void onLoaderReset(Loader<SRVRecord> loader) {
	}
	
	static class SRVRecordLoader extends AsyncTaskLoader<SRVRecord> {
		private static final String TAG = "peopleSyncClient.SRVRecordLoader";
		private String query;
		
		public SRVRecordLoader (Context context, Bundle args) {
			super(context);
			this.query = SRV_APPLICATION + args.getString(KEY_HOSTNAME);
		}

		@Override
		public SRVRecord loadInBackground() {
			try{
			    Record[] records = new Lookup(query, Type.SRV).run();
			    if (records != null && records.length > 0){
				    SRVRecord srvRecord = (SRVRecord) records[0];
				    return srvRecord;
			    }
		    } catch (Exception e) {
	            Log.e(TAG, e.getMessage());
	        }
			return null;
		}
		
		public void setQuery(String hostname){
			this.query = SRV_APPLICATION + hostname;
		}
	}
	
	public interface FinishedCallBack {
		public void onLoadFinished(SRVRecord records);
		public LoaderManager getLoaderManager();
	}

}
