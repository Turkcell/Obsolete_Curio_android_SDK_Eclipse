/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.utils;

import android.content.Context;


/**
 * Singleton settings class. Holds parameter values that read from config file curio.xml of the app.
 * 
 * @author Can Ciloglu
 *
 */
public class CurioClientSettings {
	private static final String TAG = "CurioClientSettings";
	private static CurioClientSettings instance;
	private String apiKey;
	private int sessionTimeout;
	private boolean isPeriodicDispatchEnabled;
	private String gcmSenderId;
	private int dispatchPeriod;
	private String trackingCode;
	private String serverUrl;
	private int maxCachedActivityCount;
	private static boolean loggingEnabled = true;
	private boolean autoPushRegistration;

	/**
	 * Private constructor.
	 * 
	 * @param context
	 */
	private CurioClientSettings(Context context){
		ParameterLoader paramLoader = new ParameterLoader(context);
		
		loggingEnabled = paramLoader.getBoolean(Constants.CONFIG_PARAM_LOGGING_ENABLED, true);
		apiKey = paramLoader.getString(Constants.CONFIG_PARAM_API_KEY, null);
		gcmSenderId = paramLoader.getString(Constants.CONFIG_PARAM_GCM_SENDER_ID, null);
		trackingCode = paramLoader.getString(Constants.CONFIG_PARAM_TRACKING_CODE, null);
		serverUrl = paramLoader.getString(Constants.CONFIG_PARAM_SERVER_URL, null);
		autoPushRegistration = paramLoader.getBoolean(Constants.CONFIG_PARAM_AUTO_PUSH_REGISTRATION, false);

//		if(apiKey == null || trackingCode == null || serverUrl == null){
//			throw new IllegalStateException("api_key, tracking_code and server_url are required parameters, they can NOT be null. Please be sure that you defined those parameters in curio.xml config file.");
//		}
		
		sessionTimeout = paramLoader.getInteger(Constants.CONFIG_PARAM_SESSION_TIMEOUT, Constants.CONFIG_PARAM_DEFAULT_VALUE_SESSION_TIMEOUT_IN_MINUTES);
		isPeriodicDispatchEnabled = paramLoader.getBoolean(Constants.CONFIG_PARAM_PERIODIC_DISPATCH, false);
		dispatchPeriod = paramLoader.getInteger(Constants.CONFIG_PARAM_DISPATCH_PERIOD, Constants.CONFIG_PARAM_DEFAULT_VALUE_DISPATCH_PERIOD_IN_MINUTES);
		maxCachedActivityCount = paramLoader.getInteger(Constants.CONFIG_PARAM_MAX_CACHED_ACTIVITY_COUNT, Constants.CONFIG_PARAM_DEFAULT_VALUE_MAX_CACHED_ACTIVITY_COUNT);
		
		/**
		 * User defined max. cached activity count cannot be greater than defined max.
		 */
		if(maxCachedActivityCount > Constants.CONFIG_PARAM_MAX_VALUE_MAX_CACHED_ACTIVITY_COUNT){
			CurioLogger.w(TAG, "Max number of cached activity cannot be greater then " + Constants.CONFIG_PARAM_MAX_VALUE_MAX_CACHED_ACTIVITY_COUNT + ". Will be set to max value.");
			maxCachedActivityCount = Constants.CONFIG_PARAM_MAX_VALUE_MAX_CACHED_ACTIVITY_COUNT;
		}
	}
	
	/**
	 * Static instance getter.
	 * 
	 * @param context
	 * @return
	 */
	public static CurioClientSettings getInstance(Context context){
		if(instance == null){
			instance = new CurioClientSettings(context);
		}
		return instance;
	}

	
	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	/**
	 * Gets session timeout in minutes
	 * 
	 * @return
	 */
	public int getSessionTimeout() {
		return sessionTimeout;
	}

	public void setSessionTimeout(int sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}

	public boolean isPeriodicDispatchEnabled() {
		return isPeriodicDispatchEnabled;
	}

	public void setPeriodicDispatchEnabled(boolean isPeriodicDispatchEnabled) {
		this.isPeriodicDispatchEnabled = isPeriodicDispatchEnabled;
	}

	public int getDispatchPeriod() {
		return dispatchPeriod;
	}

	public void setDispatchPeriod(int dispatchPeriod) {
		this.dispatchPeriod = dispatchPeriod;
	}

	public String getTrackingCode() {
		return trackingCode;
	}

	public void setTrackingCode(String trackingCode) {
		this.trackingCode = trackingCode;
	}

	public String getServerUrl() {
		return serverUrl;
	}
	
	public String getGcmSenderId() {
		return gcmSenderId;
	}

	public void setGcmSenderId(String gcmSenderId) {
		this.gcmSenderId = gcmSenderId;
	}

	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public int getMaxCachedActivityCount() {
		return maxCachedActivityCount;
	}

	public void setMaxCachedActivityCount(int maxCachedActivityCount) {
		this.maxCachedActivityCount = maxCachedActivityCount;
	}

	public static boolean isLoggingEnabled() {
		return loggingEnabled;
	}

	public boolean isAutoPushRegistration() {
		return autoPushRegistration;
	}

	public void setAutoPushRegistration(boolean autoPushRegistration) {
		this.autoPushRegistration = autoPushRegistration;
	}

	@SuppressWarnings("static-access")
	public void setLoggingEnabled(boolean loggingEnabled) {
		this.loggingEnabled = loggingEnabled;
	}
	
}
