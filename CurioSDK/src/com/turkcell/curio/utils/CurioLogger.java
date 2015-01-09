/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 22 Tem 2014
 *
 */
package com.turkcell.curio.utils;

import com.turkcell.curio.BuildConfig;

/**
 * Logger class for controlling logging availability.
 * 
 * @author Can Ciloglu
 *
 */
public class CurioLogger {
	public static void i(String tag, String string) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.i(tag, string);
		}
	}

	public static void e(String tag, String string) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.e(tag, string);
		}
	}

	public static void d(String tag, String string) {
		if (CurioClientSettings.isLoggingEnabled() && BuildConfig.DEBUG) {
			android.util.Log.d(tag, string);
		}
	}

	public static void v(String tag, String string) {
		if (CurioClientSettings.isLoggingEnabled() && BuildConfig.DEBUG) {
			android.util.Log.v(tag, string);
		}
	}

	public static void w(String tag, String string) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.w(tag, string);
		}
	}

	public static void i(String tag, String string, Throwable t) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.i(tag, string, t);
		}
	}

	public static void e(String tag, String string, Throwable t) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.e(tag, string, t);
		}
	}

	public static void d(String tag, String string, Throwable t) {
		if (CurioClientSettings.isLoggingEnabled() && BuildConfig.DEBUG) {
			android.util.Log.d(tag, string, t);
		}
	}

	public static void v(String tag, String string, Throwable t) {
		if (CurioClientSettings.isLoggingEnabled() && BuildConfig.DEBUG) {
			android.util.Log.v(tag, string, t);
		}
	}

	public static void w(String tag, String string, Throwable t) {
		if (CurioClientSettings.isLoggingEnabled()) {
			android.util.Log.w(tag, string, t);
		}
	}
}
