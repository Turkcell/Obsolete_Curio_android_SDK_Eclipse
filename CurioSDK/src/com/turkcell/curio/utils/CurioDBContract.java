/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.utils;

import android.provider.BaseColumns;

/**
 * Curio DB contract class that holds database table schemas.
 * 
 * @author Can Ciloglu
 *
 */
public class CurioDBContract {

	public CurioDBContract() {
	}
	
	/**
	 * Base interface for tables that we store requests. 
	 * 
	 * @author Can Ciloglu
	 *
	 */
	public interface BaseOfflineEntryColumns {
		public static final String COLUMN_NAME_DATA = "data";
		public static final String COLUMN_NAME_IN_PROCESS = "in_process";
		public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
	}
	
	/**
	 * Interface for periodic dispatch request table.
	 * 
	 * @author Can Ciloglu
	 *
	 */
	public static abstract class CurioPeriodicDispatchEntry implements BaseOfflineEntryColumns, BaseColumns{
		public static final String TABLE_NAME = "curio_periodic_dispatch";
	}
	
	/**
	 * Interface for offline cache table.
	 * 
	 * @author Can Ciloglu
	 *
	 */
	public static abstract class CurioOfflineCacheEntry implements BaseOfflineEntryColumns, BaseColumns{
		public static final String TABLE_NAME = "curio_offline_cache";
		public static final String COLUMN_NAME_SESSION_CODE = "session_code";
	}
}
