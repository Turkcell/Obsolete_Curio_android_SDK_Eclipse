/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import com.turkcell.curio.CurioClient;
import com.turkcell.curio.model.OfflineRequest;
import com.turkcell.curio.utils.CurioDBContract.BaseOfflineEntryColumns;
import com.turkcell.curio.utils.CurioDBContract.CurioOfflineCacheEntry;
import com.turkcell.curio.utils.CurioDBContract.CurioPeriodicDispatchEntry;

/**
 * DB Helper class which manages all DB operations for Curio SDK.
 * 
 * @author Can Ciloglu
 * 
 */
public class CurioDBHelper extends SQLiteOpenHelper {
	private static final String TAG = "CurioDBHelper";

	private static final String TEXT_TYPE = " TEXT";
	private static final String INTEGER_TYPE = " INTEGER";
	private static final String COMMA_SEP = ",";
	private static final String SQL_CREATE_TABLE_PERIODIC_DISPATCH = "CREATE TABLE " + CurioPeriodicDispatchEntry.TABLE_NAME + " (" + CurioPeriodicDispatchEntry._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + CurioPeriodicDispatchEntry.COLUMN_NAME_DATA + TEXT_TYPE + COMMA_SEP + CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS + INTEGER_TYPE
			+ COMMA_SEP + CurioPeriodicDispatchEntry.COLUMN_NAME_TIMESTAMP + INTEGER_TYPE + " )";

	private static final String SQL_CREATE_TABLE_OFFLINE_CACHE = "CREATE TABLE " + CurioOfflineCacheEntry.TABLE_NAME + " (" + CurioOfflineCacheEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ CurioOfflineCacheEntry.COLUMN_NAME_SESSION_CODE + TEXT_TYPE + COMMA_SEP + CurioOfflineCacheEntry.COLUMN_NAME_DATA + TEXT_TYPE + COMMA_SEP + CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS
			+ INTEGER_TYPE + COMMA_SEP + CurioOfflineCacheEntry.COLUMN_NAME_TIMESTAMP + INTEGER_TYPE + " )";

	private static final String SQL_DROP_TABLE_PERIODIC_DISPATCH = "DROP TABLE IF EXISTS " + CurioPeriodicDispatchEntry.TABLE_NAME;
	private static final String SQL_DROP_TABLE_OFFLINE_CACHE = "DROP TABLE IF EXISTS " + CurioOfflineCacheEntry.TABLE_NAME;

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "Curio.db";

	private static CurioDBHelper instance;

	/**
	 * Using atomic integer for concurrency!!!
	 */
	private AtomicInteger openDBCount = new AtomicInteger();

	private SQLiteDatabase database;

	private Context context;

	private CurioClient clientInstance;

	/**
	 * Variable to check if any periodic dispatch request stored.
	 */
	private boolean periodicDispatchRequestExists = true;

	/**
	 * Should be called first to create instance.
	 */
	public static synchronized CurioDBHelper createInstance(CurioClient clientInstance) {
		if (instance == null) {
			instance = new CurioDBHelper(clientInstance);
		}
		return instance;
	}

	/**
	 * Be sure that createInstance is called first.
	 * 
	 * Getter for singleton instance of SQLiteOpenHelper for Curio.
	 * 
	 * Same instance should be used through whole SDK.
	 * 
	 * @return
	 */
	public static synchronized CurioDBHelper getInstance() {
		if (instance == null) {
			throw new IllegalStateException("CurioDBHelper is not created. You should call createInstance method first.");
		}
		return instance;
	}

	/**
	 * Private constructor.
	 * 
	 * @param clientInstance
	 */
	private CurioDBHelper(CurioClient clientInstance) {
		super(clientInstance.getContext(), DATABASE_NAME, null, DATABASE_VERSION);
		this.context = clientInstance.getContext();
		this.clientInstance = clientInstance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create periodic dispatch table. Requests (start/end screen, send event) are stored in this table for periodic dispatching.
		db.execSQL(SQL_CREATE_TABLE_PERIODIC_DISPATCH);

		// Create offline cache table. All requests are stored in this table for offline caching.
		db.execSQL(SQL_CREATE_TABLE_OFFLINE_CACHE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(SQL_DROP_TABLE_PERIODIC_DISPATCH);
		db.execSQL(SQL_DROP_TABLE_OFFLINE_CACHE);
		onCreate(db);
	}

	@SuppressLint("NewApi")
	@Override
	public void onConfigure(SQLiteDatabase db) {
		/**
		 * SQLiteDatabase.enableWriteAheadLogging() is not available below API level 11.
		 */
		if (Build.VERSION.SDK_INT >= Constants.HONEYCOMB_SDK_INT) {
			// Enable write ahead logging of sqlite for concurrency.
			db.enableWriteAheadLogging();
		}else{
			CurioLogger.d(TAG, "Write ahead logging for SQLite is not available for this SDK Level. So cannot enable write ahead logging.");
		}
	}

	/**
	 * This method should always be used to get a DB connection since no need to open a DB connection if there is already an open one.
	 * 
	 * @return
	 */
	public synchronized SQLiteDatabase openDatabase() {
		if (openDBCount.incrementAndGet() == 1) {
			// Open new database
			this.database = getWritableDatabase();
		}
		return this.database;
	}

	/**
	 * This method should always be used to close a DB connection since if connection is used it should not be closed until nobody is using it.
	 */
	public synchronized void closeDatabase() {
		if (openDBCount.decrementAndGet() == 0) {
			// Close database
			this.database.close();
		}
	}

	/**
	 * Writes an periodic dispatch request to DB for later dispatch.
	 * 
	 * @param offlineRequest
	 * @return true if write operation is successful.
	 */
	public boolean persistOfflineRequestForPeriodicDispatch(OfflineRequest offlineRequest) {
		// To be on the safe side, set this param before inserting the actual data.
		periodicDispatchRequestExists = true;

		SQLiteDatabase db = null;

		try {
			db = openDatabase();

			ContentValues values = new ContentValues();

			/**
			 * Timestamp field is mandatory (for internal use not for server) for all requests stored in DB for ordered request fetching.
			 */
			values.put(CurioPeriodicDispatchEntry.COLUMN_NAME_TIMESTAMP, Long.toString((Long) offlineRequest.getParams().get(Constants.JSON_NODE_TIMESTAMP)));

			/**
			 * Generate JSON data.
			 */
			values.put(CurioPeriodicDispatchEntry.COLUMN_NAME_DATA, generateJSONData(offlineRequest.getParams()));

			/**
			 * Mark it as not in process
			 */
			values.put(CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS, Constants.NOT_IN_PROCESS);

			long i = db.insert(CurioPeriodicDispatchEntry.TABLE_NAME, null, values);

			CurioLogger.d(TAG, "Row ID of newly inserted periodic dispatch request is " + i);
			return true;
		} catch (SQLiteException e) {
			CurioLogger.e(TAG, e.getMessage());
			return false;
		} finally {
			closeDatabase();
		}
	}

	/**
	 * Writes an offline request to DB for caching
	 * 
	 * @param offlineRequest
	 * 
	 * @return true if write operation is successful.
	 */
	public boolean persistOfflineRequestForCaching(OfflineRequest offlineRequest) {
		/**
		 * Check if max. offline cache size is reached.
		 * If yes, do not store offline requests anymore.
		 */
		if (hasMaxCacheSizeReached()) {
			CurioLogger.i(TAG, "Cache size limit has been reached. No offline request will be stored until device goes online and sends stored analytics to server.");
			return false;
		}

		SQLiteDatabase db = null;

		try {
			db = openDatabase();

			ContentValues values = new ContentValues();

			/**
			 * Timestamp field is mandatory (for internal use not for server) for all requests stored in DB for ordered request fetching.
			 */
			values.put(CurioOfflineCacheEntry.COLUMN_NAME_TIMESTAMP, Long.toString((Long) offlineRequest.getParams().get(Constants.JSON_NODE_TIMESTAMP)));

			/**
			 * Generate JSON data.
			 */
			values.put(CurioOfflineCacheEntry.COLUMN_NAME_DATA, generateJSONData(offlineRequest.getParams()));

			/**
			 * Mark it as not in process
			 */
			values.put(CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS, Constants.NOT_IN_PROCESS);

			long i = db.insert(CurioOfflineCacheEntry.TABLE_NAME, null, values);
			CurioLogger.d(TAG, "Row ID of newly inserted offline request is " + i);
			return true;
		} catch (SQLiteException e) {
			CurioLogger.e(TAG, e.getMessage());
			return false;
		} finally {
			closeDatabase();
		}
	}

	/**
	 * Generates JSON "data" node string from parameter map.
	 * 
	 * @param params
	 * @return JSON string
	 */
	private String generateJSONData(Map<String, Object> params) {
		JSONObject json = new JSONObject();

		try {
			for (String key : params.keySet()) {
				Object paramValue = params.get(key);
				json.put(key, paramValue);
			}
		} catch (JSONException e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		}

		return json.toString();
	}


	/**
	 * Deletes periodic dispatch data which are marked as "in process"
	 */
	public void deleteInProcessPeriodicRequests() {
		SQLiteDatabase db = null;
		try {
			db = openDatabase();

			String whereClause = CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.IN_PROCESS_STR };

			int i = db.delete(CurioPeriodicDispatchEntry.TABLE_NAME, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows deleted, since their process completed.");
		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		} finally {
			closeDatabase();
		}
	}

	/**
	 * Sets "in process" periodic dispatch data as "not in process"
	 */
	public void setInProcessPeriodicRequestsAsNotInProcess() {
		SQLiteDatabase db = null;

		try {
			db = openDatabase();

			ContentValues values = new ContentValues();

			values.put(CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS, Constants.NOT_IN_PROCESS);

			String whereClause = CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.IN_PROCESS_STR };

			int i = db.update(CurioPeriodicDispatchEntry.TABLE_NAME, values, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows updated as NOT in process until next periodic dispatch check.");
		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		} finally {
			closeDatabase();
		}
	}


	/**
	 * Fetches stored requests from given table (periodic dispatch or offline cache)
	 * 
	 * @param tableName
	 * @return
	 */
	public String fetchStoredRequestsFromTable(String tableName) {
		SQLiteDatabase db = openDatabase();
		Cursor cursor = null;

		JSONArray array = null;

		try {
			/**
			 * The whole process will be in a transaction
			 */
			 db.beginTransaction();

			/**
			 * Fetch stored requests
			 */
			String selection = BaseOfflineEntryColumns.COLUMN_NAME_IN_PROCESS + "=?";
			String[] selectionArgs = new String[] { Constants.NOT_IN_PROCESS_STR };
			String orderBy = BaseOfflineEntryColumns.COLUMN_NAME_TIMESTAMP + " DESC";

			cursor = db.query(tableName, null, selection, selectionArgs, null, null, orderBy);

			if (cursor.moveToFirst()) {
				array = new JSONArray();
				do {
					String data = cursor.getString(cursor.getColumnIndex(BaseOfflineEntryColumns.COLUMN_NAME_DATA));
					try {
						array.put(new JSONObject(data));
					} catch (JSONException e) {
						CurioLogger.e(TAG, e.getMessage(), e);
					}
				} while (cursor.moveToNext());
			}

			/**
			 * If no request data stored, abort dispatch.
			 */
			if (array == null || array.length() == 0) {
				CurioLogger.d(TAG, "No stored activity found. Will check in " + CurioClientSettings.getInstance(context).getDispatchPeriod() + " min. again.");
				return null;
			}

			/**
			 * Set fetched requests as "in process"
			 */
			ContentValues values = new ContentValues();
			values.put(BaseOfflineEntryColumns.COLUMN_NAME_IN_PROCESS, Constants.IN_PROCESS);

			String whereClause = BaseOfflineEntryColumns.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.NOT_IN_PROCESS_STR };

			int i = db.update(tableName, values, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows updated as in process until they sent...");

			db.setTransactionSuccessful();
		} catch (Exception e1) {
			CurioLogger.e(TAG, e1.getMessage(), e1);
		} finally {
			db.endTransaction();
			
			if(cursor != null){
				cursor.close();
			}
			
			closeDatabase();
		}

		if(array == null || array.length() == 0){
			return null;
		}
		
		return array.toString();
	}

	/**
	 * Deletes offline requests which are marked as "in process"
	 */
	public void deleteInProcessOfflineRequests() {
		SQLiteDatabase db = null;
		try {
			db = openDatabase();

			String whereClause = CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.IN_PROCESS_STR };

			int i = db.delete(CurioOfflineCacheEntry.TABLE_NAME, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows deleted, since their process completed.");

		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		} finally {
			closeDatabase();
		}
	}

	/**
	 * Sets "in process" offline requests as "not in process"
	 */
	public void setInProcessOfflineRequestsAsNotInProcess() {
		SQLiteDatabase db = null;

		try {
			db = openDatabase();

			ContentValues values = new ContentValues();

			values.put(CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS, Constants.NOT_IN_PROCESS);

			String whereClause = CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.IN_PROCESS_STR };

			int i = db.update(CurioOfflineCacheEntry.TABLE_NAME, values, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows updated as NOT in process and will be send next time device is online");
		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		} finally {
			closeDatabase();
		}
	}

	
	/**
	 * Converts and inserts all stored periodic dispatch data into offline request table.
	 */
	public void moveAllExistingPeriodicDispatchDataToOfflineTable() {
		
		/**
		 * Check if there is any stored periodic request.
		 */
		if (!periodicDispatchRequestExists) {
			return;
		}

		/**
		 * Check if offline cache is full or not.
		 */
		if (hasMaxCacheSizeReached()) {
			CurioLogger.i(TAG, "Cache capacity limit has been reached. No offline request will be stored until device goes online and sends stored analytics to server.");
		}

		SQLiteDatabase db = openDatabase();

		Cursor cursor = null;
		
		List<String[]> rowList = new ArrayList<String[]>();

		try {
			/**
			 * The whole process will be in a transaction
			 */
			 db.beginTransaction();

			/**
			 * Fetch stored requests
			 */
			String selection = CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] selectionArgs = new String[] { Constants.NOT_IN_PROCESS_STR };
			String orderBy = CurioPeriodicDispatchEntry.COLUMN_NAME_TIMESTAMP + " DESC";

			cursor = db.query(CurioPeriodicDispatchEntry.TABLE_NAME, null, selection, selectionArgs, null, null, orderBy);

			if (cursor.moveToFirst()) {
				String[] row = new String[5];
				do {
					row[0] = cursor.getString(cursor.getColumnIndex(CurioPeriodicDispatchEntry.COLUMN_NAME_DATA));
					row[1] = cursor.getString(cursor.getColumnIndex(CurioPeriodicDispatchEntry.COLUMN_NAME_TIMESTAMP));

					rowList.add(row);
				} while (cursor.moveToNext());
			}

			/**
			 * Update them as in process...
			 */
			ContentValues values = new ContentValues();
			values.put(CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS, Constants.IN_PROCESS);

			String whereClause = CurioPeriodicDispatchEntry.COLUMN_NAME_IN_PROCESS + "=?";
			String[] whereArgs = new String[] { Constants.NOT_IN_PROCESS_STR };

			int i = db.update(CurioPeriodicDispatchEntry.TABLE_NAME, values, whereClause, whereArgs);

			CurioLogger.d(TAG, i + " rows updated as in process until processing finishes...");

			/**
			 * Add fetched requests to offline table.
			 * 
			 * Also add session code to the requests while storing them on offline cache table.
			 */
			for (String[] row : rowList) {
				values = new ContentValues();
				values.put(CurioOfflineCacheEntry.COLUMN_NAME_SESSION_CODE, clientInstance.getSessionCode(false));
				values.put(CurioOfflineCacheEntry.COLUMN_NAME_DATA, row[0]);
				values.put(CurioOfflineCacheEntry.COLUMN_NAME_TIMESTAMP, row[1]);
				values.put(CurioOfflineCacheEntry.COLUMN_NAME_IN_PROCESS, Constants.NOT_IN_PROCESS);

				long rowId = db.insert(CurioOfflineCacheEntry.TABLE_NAME, null, values);
				CurioLogger.d(TAG, "Row ID of newly added offline request is " + rowId);
			}

			/**
			 * Delete all from periodic dispatch table
			 */
			deleteInProcessPeriodicRequests();
			periodicDispatchRequestExists = false;

			db.setTransactionSuccessful();
		} catch (Exception e1) {
			CurioLogger.e(TAG, e1.getMessage(), e1);
		} finally {
			db.endTransaction();
			
			if(cursor != null){
				cursor.close();
			}
			
			closeDatabase();
		}
	}

	/**
	 * Checks if offline cache size limit reached or not.
	 * 
	 * @return
	 */
	private boolean hasMaxCacheSizeReached() {
		int rowCount = getRowCount();

		if (rowCount < CurioClientSettings.getInstance(context).getMaxCachedActivityCount()) {
			return false;
		}

		return true;
	}

	/**
	 * Efficient way of getting row count.
	 * 
	 * @param tableName
	 * @return
	 */
	private int getRowCount() {
		int count = 0;
		SQLiteDatabase db = openDatabase();
		Cursor countCursor = null;

		try {
			countCursor = db.query(CurioOfflineCacheEntry.TABLE_NAME, new String[] { "count(*) AS count" }, null, null, null, null, null);

			countCursor.moveToFirst();
			count = countCursor.getInt(0);
		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
		} finally {
			if(countCursor != null){
				countCursor.close();
			}
			
			closeDatabase();
		}

		return count;
	}
}
