package com.messageconcept.peoplesyncclient.syncadapter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper{
	
	private static final String DATABASE_NAME = "peopleSync.db";
	private static final int DATABASE_VERSION = 1;
	private static final String TABLE_NEVER = "never";
	private static final String COLUMN_ID = "_id";
	private static final String COLUMN_ACCOUNT_NAME = "account_name";
	
	// Database creation sql statement
	private static final String DATABASE_CREATE = "CREATE TABLE "
	  + TABLE_NEVER + "(" + COLUMN_ID
	  + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_ACCOUNT_NAME
	  + " text not null);";
	
	public DatabaseHelper(Context context) {
	    super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db){
		db.execSQL(DATABASE_CREATE);
	}
	
    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NEVER);
 
        // Create tables again
        onCreate(db);
    }
    
	public void storeNever(String accountName){
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(COLUMN_ACCOUNT_NAME, accountName);
		
		db.insert(TABLE_NEVER, null, values);
		db.close();
	}
	
	public Boolean isStoredNever(String accountName){
		SQLiteDatabase db = this.getReadableDatabase();
	
		Cursor cursor = db.query(TABLE_NEVER, new String[] {COLUMN_ID, COLUMN_ACCOUNT_NAME}, COLUMN_ACCOUNT_NAME + "=?", new String[] {accountName}, null, null, null, null);
		
		Boolean isStored = cursor != null && cursor.getCount() > 0;
		db.close();
		return isStored;
	}
}
