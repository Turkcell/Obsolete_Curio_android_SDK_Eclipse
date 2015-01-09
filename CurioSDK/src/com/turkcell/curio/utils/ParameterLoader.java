/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.utils;

import android.content.Context;

/**
 * Provides a practical way for loading parameters from curio.xml configuration file.
 * 
 * @author Can Ciloglu
 *
 */
public class ParameterLoader {

	private static final String TAG = "ParameterLoader";
	private final Context context;

	public ParameterLoader(Context ctx) {
		if (ctx == null) {
			throw new NullPointerException("Context cannot be null");
		}
		this.context = ctx;
	}

	private int getResourceIdForType(String key, String type) {
		if (context == null) {
			return 0;
		}
		return context.getResources().getIdentifier(key, type, context.getPackageName());
	}

	public String getString(String key, String defaultValue) {
		int id = getResourceIdForType(key, "string");
		if (id == 0) {
			return defaultValue;
		} else {
			return context.getString(id);
		}
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		int id = getResourceIdForType(key, "bool");
		if (id == 0) {
			return defaultValue;
		} else {
			return "true".equalsIgnoreCase(context.getString(id));
		}
	}

	public int getInteger(String key, int defaultValue) {
		int id = getResourceIdForType(key, "integer");
		if (id == 0) {
			return defaultValue;
		} else {
			try {
				return Integer.parseInt(context.getString(id));
			} catch (NumberFormatException e) {
				CurioLogger.w(TAG, "NumberFormatException parsing " + context.getString(id));
				return defaultValue;
			}
		}
	}

}
