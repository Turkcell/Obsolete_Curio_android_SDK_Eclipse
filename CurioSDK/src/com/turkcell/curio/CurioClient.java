/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.ads.identifier.AdvertisingIdClient.Info;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.turkcell.curio.model.OfflineRequest;
import com.turkcell.curio.model.OnlineRequest;
import com.turkcell.curio.model.Screen;
import com.turkcell.curio.utils.Constants;
import com.turkcell.curio.utils.CurioClientSettings;
import com.turkcell.curio.utils.CurioDBHelper;
import com.turkcell.curio.utils.CurioLogger;
import com.turkcell.curio.utils.CurioUtil;
import com.turkcell.curio.utils.NetworkUtil;
import com.turkcell.curio.utils.PushUtil;
import com.turkcell.curio.utils.VisitorCodeManager;

/**
 * Main entrance class for the curio client SDK. SDK can be used first by calling createInstance, and then can be called by calling getInstance method.
 * 
 * @author Can Ciloglu
 * 
 */
@SuppressLint("FieldGetter")
public class CurioClient implements INetworkConnectivityChangeListener {
	private static final String TAG = "CurioClient";
	private static boolean getParamsFromResource = true;
	public static CurioClient instance = null;

	private Context context;
	private String urlPrefix;
	private String sessionCode;
	private boolean isPeriodicDispatchEnabled;
	private int dispatchPeriod;
	private Map<String, Screen> contextHitcodeMap = new HashMap<String, Screen>();
	private CurioRequestProcessor curioRequestProcessor;
	private DBRequestProcessor dbRequestProcessor;
	private boolean endingSession = false;
	private boolean isOfflineRequestDispatchInProgress;
	private boolean isOfflineCachingOn;
	private Boolean initialConnectionState;
	private StaticFeatureSet staticFeatureSet;
	private boolean offlineReqExists = true;
	private Boolean isAdIdAvailable = null;
	private String pushMessageId = null;
	private String customId = null;
	private boolean isTriggeredByUnregisterRequest = false;

	protected int unauthCount = 0;
	protected boolean isParamLoadingFinished = false;
	protected boolean isSessionStartSent;
	private String tmpTrackingCode;
	private String tmpApiKey;
	private String tmpGcmSenderId;
	private boolean tmpAutoPushRegistration;
	private int tmpSessionTimeout;
	private boolean tmpLoggingEnabled;

	/**
	 * Be sure to call createInstance first.
	 * 
	 * @return
	 */
	public static synchronized CurioClient getInstance() {
		if (instance == null) {
			throw new IllegalStateException("CurioClient is not created. You should call createInstance method first.");
		}
		return instance;
	}

	/**
	 * Creates a client instance. Should be called first.
	 * 
	 * @param context
	 * @return
	 */
	public static synchronized CurioClient createInstance(Context context) {
		if (instance == null) {
			instance = new CurioClient(context);

			instance.startDBRequestProcessorThread();
			instance.startMainRequestProcessorThread();
		}

		return instance;
	}

	/**
	 * Gets and creates instance if needed.
	 * 
	 * @param context
	 * @return
	 */
	public static synchronized CurioClient getInstance(Context context) {
		if (instance == null) {
			createInstance(context);
		}

		return instance;
	}

	/**
	 * Gets and creates instance if needed.
	 * 
	 * @param context
	 * @return
	 */
	public static synchronized CurioClient getInstance(Context context, boolean loadParamsFromResource) {
		if (instance == null) {
			if (!loadParamsFromResource) {
				getParamsFromResource = loadParamsFromResource;
			}

			createInstance(context);
		}

		return instance;
	}

	/**
	 * Starts main request processor thread.
	 */
	private void startMainRequestProcessorThread() {
		curioRequestProcessor = new CurioRequestProcessor(this);
		new Thread(curioRequestProcessor, Constants.THREAD_NAME_CURIO_REQ_PROC).start();
	}

	/**
	 * Starts DB request processor thread.
	 */
	private void startDBRequestProcessorThread() {
		dbRequestProcessor = new DBRequestProcessor();
		new Thread(dbRequestProcessor, Constants.THREAD_NAME_DB_REQ_PROC).start();
	}

	/**
	 * Private constructor.
	 * 
	 * @param context
	 * @param getParamsFromResource
	 */
	private CurioClient(Context context) {
		this.setContext(context);

		NetworkUtil.createInstance(context, this);

		loadParametersFromResource();

		initialConnectionState = NetworkUtil.getInstance().isConnected();
		isOfflineCachingOn = !initialConnectionState;
		CurioLogger.d(TAG, "Initial network connection state is: " + initialConnectionState);

		CurioDBHelper.createInstance(this);
		CurioLogger.d(TAG, "Finished creating Curio Client on " + System.currentTimeMillis());
	}

	/**
	 * Loads all parameters from curio.xml of parent application.
	 */
	private void loadParametersFromResource() {
		/**
		 * We're loading parameters on a different thread, because big G. wants us to get AdId on a thread other than main.
		 */
		new Thread(new Runnable() {
			@Override
			public void run() {
				String visitorCode = null;

				String apiKey = null;
				String trackingCode = null;
				int sessionTimeout = 0;
				boolean autoPushRegistration = false;
				String gcmSenderId = null;

				if (getParamsFromResource) {
					apiKey = CurioClientSettings.getInstance(context).getApiKey();
					trackingCode = CurioClientSettings.getInstance(context).getTrackingCode();
					sessionTimeout = CurioClientSettings.getInstance(context).getSessionTimeout();
					gcmSenderId = CurioClientSettings.getInstance(context).getGcmSenderId();
					autoPushRegistration = CurioClientSettings.getInstance(context).isAutoPushRegistration();
					setServerUrl(CurioClientSettings.getInstance(context).getServerUrl());
					isPeriodicDispatchEnabled = CurioClientSettings.getInstance(context).isPeriodicDispatchEnabled();

					if (isPeriodicDispatchEnabled) {
						dispatchPeriod = CurioClientSettings.getInstance(context).getDispatchPeriod();

						if (dispatchPeriod >= sessionTimeout) {
							CurioLogger.w(TAG, "Dispatch period cannot be greater or equal to session timeout.");
							dispatchPeriod = sessionTimeout - 1;
							CurioClientSettings.getInstance(context).setDispatchPeriod(dispatchPeriod);
							CurioLogger.i(TAG, "Periodic dispatch is ENABLED.");
							CurioLogger.i(TAG, "Dispatch period is " + dispatchPeriod + " minutes");
						}
					}
				}

				if (Build.VERSION.SDK_INT >= Constants.GINGERBREAD_2_3_3_SDK_INT) {
					Info adInfo = null;
					try {

						CurioLogger.d(TAG, "Trying to get AdId...");
						adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
						CurioLogger.d(TAG, "Fetched AdId is " + adInfo);

					} catch (IOException e) {
						// Unrecoverable error connecting to Google Play services.
						isAdIdAvailable = false;
					} catch (GooglePlayServicesNotAvailableException e) {
						// Google Play services is not available entirely.
						isAdIdAvailable = false;
					} catch (GooglePlayServicesRepairableException e) {
						// Google Play services recoverable exception.
						isAdIdAvailable = false;
						e.printStackTrace();
					} catch (IllegalStateException e) {
						// Ignore since we're not calling it on main thread.
					}

					if (isAdIdAvailable == null && adInfo != null) { // Not set, so we did not get an exception from big G. Play Services.
						isAdIdAvailable = !adInfo.isLimitAdTrackingEnabled(); // Check user prefs.

						if (isAdIdAvailable) {
							visitorCode = adInfo.getId();
						} else {
							visitorCode = VisitorCodeManager.id(trackingCode, context);
						}
					} else { // Not null and it's false, cannot use ad Id as visitor code.
						CurioLogger.d(TAG, "Ad Id is not available on device yet or cannot fetch Ad Id. So generating visitor code manually.");
						visitorCode = VisitorCodeManager.id(trackingCode, context);
					}
				} else {
					CurioLogger.d(TAG, "Ad Id is not available because of SDK level. So generating visitor code manually.");
					visitorCode = VisitorCodeManager.id(trackingCode, context);
				}

				if (getParamsFromResource) {
					staticFeatureSet = new StaticFeatureSet(apiKey, trackingCode, visitorCode, sessionTimeout, gcmSenderId, autoPushRegistration);
				} else {
					staticFeatureSet = new StaticFeatureSet(tmpApiKey, tmpTrackingCode, visitorCode, tmpSessionTimeout, tmpGcmSenderId, tmpAutoPushRegistration);
					CurioClientSettings.getInstance(context).setLoggingEnabled(tmpLoggingEnabled);
					CurioClientSettings.getInstance(context).setServerUrl(urlPrefix);
				}
				setParamLoadingFinished(true);

				CurioLogger.d(TAG, "Finished loading params and created static feature set on " + System.currentTimeMillis());
			}
		}).start();
	}

	/**
	 * Starts a session at server and generates a new session code.
	 * 
	 * Should be called at onCreate of application's main activity. Should always be called before any other analytic methods. Should be called once per application session.
	 */
	public void startSession() {
		startSession(true);
	}

	/**
	 * Starts a session at server.
	 * 
	 * Should be called at onCreate of application's main activity. Should always be called before any other analytic methods. Should be called once per application session.
	 * 
	 * @param generate
	 *            - Generates a new session code if true.
	 */
	public void startSession(final boolean generate) {
		/**
		 * If configuration param loading is not finished, post a delayed request again in 100 ms.
		 */
		if (!isParamLoadingFinished()) {
			CurioLogger.d(TAG, "startSession called but config param loading is not finished yet, will try in 100 ms again.");
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					startSession(generate);
				}
			}, 100);
			return;
		}

		// Create parameter map and add dynamic data values. Static data values will be added before sending request.
		Map<String, Object> params = new HashMap<String, Object>();
		params.put(Constants.HTTP_PARAM_SESSION_CODE, getSessionCode(generate));

		ICurioResultListener callback = new ICurioResultListener() {
			@Override
			public void handleResult(int statusCode, JSONObject result) {
				isSessionStartSent = false; // Reset "session start sent" flag.
				curioRequestProcessor.setLowerPriorityQueueProcessingStatus(true);
				if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
					CurioLogger.e(TAG, "Failed to start session on server due to wrong account parameters");
				} else if (statusCode == HttpStatus.SC_OK) {
					CurioLogger.d(TAG, "Session start is successful. Session code is " + instance.getSessionCode(false));
					if (getStaticFeatureSet().autoPushRegistration) { // If auto push registration enabled
						if (pushMessageId == null) {
							if (!isTriggeredByUnregisterRequest) {
								PushUtil.checkForGCMRegistration(context);
							} else {
								CurioLogger.d(TAG, "Unregister request is called, so will not send registration id to push server this time.");
							}
						} else {
							sendPushOpenedMsg();
						}
					}
				} else {
					CurioLogger.e(TAG, "Failed to start session on server. Server returned status code: " + statusCode);
				}
			}
		};

		// Session start is a first priority request.
		this.pushRequestToQueue(Constants.SERVER_URL_SUFFIX_SESSION_START, params, callback, CurioRequestProcessor.FIRST_PRIORITY);
	}

	/**
	 * Starts a screen at server.
	 * 
	 * Should be called at "onStart" of an activity or fragment. Should be called once per activity or fragment.
	 * 
	 * @param context
	 *            Activity which this method be called at.
	 * @param title
	 *            String that will be defined as title of this activity at server.
	 * @param path
	 *            String that will be defined as path of this activity at server.
	 */
	public void startScreen(Context context, String title, String path) {
		startScreen(context.getClass().getCanonicalName(), title, path);
	}

	/**
	 * Starts a screen at server.
	 * 
	 * Should be called at "onStart" of an activity or fragment. Should be called once per activity or fragment.
	 * 
	 * @param className
	 *            definitive and unique string for the parent which this method be called at.
	 * @param title
	 *            String that will be defined as title of this activity at server.
	 * @param path
	 *            String that will be defined as path of this activity at server.
	 */
	public void startScreen(final String className, final String title, final String path) {
		/**
		 * If configuration param loading is not finished, post a delayed request again in 250 ms.
		 */
		if (!isParamLoadingFinished()) {
			CurioLogger.d(TAG, "startScreen called but config param loading is not finished yet, will try in 250 ms again.");

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					startScreen(className, title, path);
				}
			}, 250);
			return;
		}

		String urlEncodedTitle = "";
		String urlEncodedPath = "";

		try {
			urlEncodedTitle = URLEncoder.encode(title, Constants.UTF8_ENCODING).replace("\\+", " ");
			urlEncodedPath = URLEncoder.encode(path, Constants.UTF8_ENCODING).replace("\\+", " ");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		Map<String, Object> params = new HashMap<String, Object>();
		params.put(Constants.HTTP_PARAM_SESSION_CODE, this.getSessionCode(false));
		params.put(Constants.HTTP_PARAM_PAGE_TITLE, urlEncodedTitle);
		params.put(Constants.HTTP_PARAM_PATH, urlEncodedPath);

		ICurioResultListener callback = null;

		if (isPeriodicDispatchEnabled || isOfflineCachingOn) {
			String generatedHitcode = CurioUtil.generateRandomUUID();
			contextHitcodeMap.put(className, new Screen(generatedHitcode, title, path));
			params.put(Constants.HTTP_PARAM_HIT_CODE, generatedHitcode);
			CurioLogger.d(TAG, "PD: " + isPeriodicDispatchEnabled + ", OC: " + isOfflineCachingOn + ", generatedHitcode " + generatedHitcode + " put into map for " + className);
		} else {
			CurioLogger.d(TAG, "PD: " + isPeriodicDispatchEnabled + ", OC: " + isOfflineCachingOn);
			callback = new ICurioResultListener() {
				@Override
				public void handleResult(int statusCode, JSONObject result) {
					if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
						if (unauthCount <= 5) {
							unauthCount++;
							CurioLogger.d(TAG, "StartScreen - Try count: " + unauthCount);
							/**
							 * If sessionStart request not already sent, 1-Stop second and third priority request queue processing. 2-Send sessionStart request. 3-Set sessionStartsent flag.
							 */
							if (!isSessionStartSent) {
								curioRequestProcessor.setLowerPriorityQueueProcessingStatus(false);
								startSession(true);
								isSessionStartSent = true;
							}
							startScreen(className, title, path);
						}
					} else if (statusCode == HttpStatus.SC_OK) {
						unauthCount = 0;
						if (result != null) {
							try {
								String returnedHitCode = result.getString(Constants.JSON_NODE_HIT_CODE);
								contextHitcodeMap.put(className, new Screen(returnedHitCode, title, path));
							} catch (JSONException e) {
								CurioLogger.e(TAG, e.getMessage());
							}
						} else {
							CurioLogger.d(TAG, "Result is null, will not process.");
						}
					} else {
						CurioLogger.e(TAG, "Failed to start screen. Server responded with status code: " + statusCode);
					}
				}
			};
		}

		this.pushRequestToQueue(Constants.SERVER_URL_SUFFIX_SCREEN_START, params, callback, CurioRequestProcessor.SECOND_PRIORITY);
	}

	/**
	 * Ends the screen at server.
	 * 
	 * Should be called at "onStop" of an activity or fragment. Should be called once per activity or fragment.
	 * 
	 * @param context
	 *            Activity which this method be called at.
	 */
	public void endScreen(Context context) {
		endScreen(context.getClass().getCanonicalName());
	}

	/**
	 * Ends the screen at server.
	 * 
	 * Should be called at "onStop" of an activity or fragment. Should be called once per activity or fragment.
	 * 
	 * @param className
	 *            definitive and unique string for the parent which this method be called at.
	 */
	public void endScreen(final String className) {
		/**
		 * If configuration param loading is not finished, post a delayed request again in 500 ms.
		 */
		if (!isParamLoadingFinished()) {
			CurioLogger.d(TAG, "endScreen called but config param loading is not finished yet, will try in 500 ms again.");

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					endScreen(className);
				}
			}, 500);
			return;
		}

		String urlEncodedTitle = "";
		String urlEncodedPath = "";
		String hitCode = "";

		CurioLogger.d(TAG, "Class name is " + className);

		Screen screen = contextHitcodeMap.get(className);

		if (screen != null) {
			hitCode = screen.getHitCode();

			try {
				urlEncodedTitle = URLEncoder.encode(screen.getTitle(), Constants.UTF8_ENCODING).replace("\\+", " ");
				urlEncodedPath = URLEncoder.encode(screen.getPath(), Constants.UTF8_ENCODING).replace("\\+", " ");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else {
			// Means an invalid call
			CurioLogger.d(TAG, "Screen info is null, so ignoring call to end screen.");
			return;
		}

		Map<String, Object> params = new HashMap<String, Object>();

		params.put(Constants.HTTP_PARAM_SESSION_CODE, this.getSessionCode(false));
		params.put(Constants.HTTP_PARAM_HIT_CODE, hitCode);
		params.put(Constants.HTTP_PARAM_PAGE_TITLE, urlEncodedTitle);
		params.put(Constants.HTTP_PARAM_PATH, urlEncodedPath);

		ICurioResultListener callback = new ICurioResultListener() {
			@Override
			public void handleResult(int statusCode, JSONObject result) {
				if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					if (unauthCount <= 5) {
						unauthCount++;
						CurioLogger.d(TAG, "End Screen - Try count: " + unauthCount);

						/**
						 * If sessionStart request not already sent, 1-Stop second and third priority request queue processing. 2-Send sessionStart request. 3-Set sessionStartsent flag.
						 */
						if (!isSessionStartSent) {
							curioRequestProcessor.setLowerPriorityQueueProcessingStatus(false);
							startSession(true);
							isSessionStartSent = true;
						}
						endScreen(className);
					}
				} else if (statusCode == HttpStatus.SC_OK) {
					CurioLogger.d(TAG, "Server responded OK. Screen ended.");
				} else {
					CurioLogger.d(TAG, "Failed to end screen. Server responded with status code: " + statusCode);
				}
			}
		};

		this.pushRequestToQueue(Constants.SERVER_URL_SUFFIX_SCREEN_END, params, callback, CurioRequestProcessor.SECOND_PRIORITY);
	}

	/**
	 * Ends the session at server.
	 * 
	 * Should be called at onStop of application's main activity. Should be called when user is really exiting from the application. Usage is: <br/>
	 * <br/>
	 * <b>if(isFinishing()){<br/>
	 * CurioClient.getInstance().endSession();<br/>
	 * }</b><br/>
	 * <br/>
	 * Should be called once per application session.
	 */
	public void endSession() {
		/**
		 * Be sure to send all stored offline periodic requests before ending session. So release all stored offline requests and call end session again from CurioRequestProcessor.
		 */
		if (!isOfflineCachingOn && isPeriodicDispatchEnabled) {
			if (!endingSession) {// First call so release stored periodic dispatch data.
				curioRequestProcessor.releaseStoredRequests();
				endingSession = true;
				return;
			} else {
				// Clearing endingSession and release flags. This is important.
				curioRequestProcessor.cancelReleaseStoredRequestFlag();
				endingSession = false;
				CurioLogger.d(TAG, "Cleared endSession and release flags.");
			}
		}

		Map<String, Object> params = new HashMap<String, Object>();

		params.put(Constants.HTTP_PARAM_SESSION_CODE, this.getSessionCode(false));

		ICurioResultListener callback = new ICurioResultListener() {
			@Override
			public void handleResult(int httpStatusCode, JSONObject result) {
				setSessionCode(null);
			}
		};

		this.pushRequestToQueue(Constants.SERVER_URL_SUFFIX_SESSION_END, params, callback, CurioRequestProcessor.THIRD_PRIORITY);
	}

	/**
	 * Sends an event to server.
	 * 
	 * @param key
	 *            Key for the event
	 * @param value
	 *            Value for the event
	 */
	public void sendEvent(final String key, final String value) {
		/**
		 * If configuration param loading is not finished, post a delayed request again in 500 ms.
		 */
		if (!isParamLoadingFinished()) {
			CurioLogger.d(TAG, "sendEvent called but config param loading is not finished yet, will try in 500 ms again.");

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					sendEvent(key, value);
				}
			}, 500);
			return;
		}

		String urlEncodedKey = "";
		String urlEncodedValue = "";

		try {
			urlEncodedKey = URLEncoder.encode(key, Constants.UTF8_ENCODING).replace("\\+", " ");
			urlEncodedValue = URLEncoder.encode(value, Constants.UTF8_ENCODING).replace("\\+", " ");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		Map<String, Object> params = new HashMap<String, Object>();

		params.put(Constants.HTTP_PARAM_SESSION_CODE, this.getSessionCode(false));
		params.put(Constants.HTTP_PARAM_EVENT_KEY, urlEncodedKey);
		params.put(Constants.HTTP_PARAM_EVENT_VALUE, urlEncodedValue);

		ICurioResultListener callback = null;

		callback = new ICurioResultListener() {
			@Override
			public void handleResult(int statusCode, JSONObject result) {
				if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					if (unauthCount <= 5) {
						unauthCount++;
						CurioLogger.d(TAG, "Send Event - Try count: " + unauthCount);

						/**
						 * If sessionStart request not already sent, 1-Stop second and third priority request queue processing. 2-Send sessionStart request. 3-Set sessionStartsent flag.
						 */
						if (!isSessionStartSent) {
							curioRequestProcessor.setLowerPriorityQueueProcessingStatus(false);
							startSession(true);
							isSessionStartSent = true;
						}
						sendEvent(key, value);
					}
				} else if (statusCode == HttpStatus.SC_OK) {
					CurioLogger.d(TAG, "Server responded OK. Event sent.");
				} else {
					CurioLogger.d(TAG, "Failed to send event. Server responded with status code: " + statusCode);
				}
			}
		};

		this.pushRequestToQueue(Constants.SERVER_URL_SUFFIX_SEND_EVENT, params, callback, CurioRequestProcessor.THIRD_PRIORITY);
	}

	/**
	 * Creates request object and pushes it to the appropriate queue.
	 * 
	 * @param path
	 * @param params
	 * @param callback
	 * @param priority
	 */
	private void pushRequestToQueue(String path, Map<String, Object> params, ICurioResultListener callback, Integer priority) {
		String url = this.getUrlPrefix() + path;

		if (NetworkUtil.getInstance().isConnected()) {
			boolean shoulBeOnlineRequest = false;

			// start/end session requests should not be included in periodic dispatch
			if (path.equals(Constants.SERVER_URL_SUFFIX_SESSION_START) || path.equals(Constants.SERVER_URL_SUFFIX_SESSION_END)) {
				shoulBeOnlineRequest = true;
			}

			/**
			 * If the request is not a start/end session type and periodic dispatch is enabled, store it as offline request, else send as online.
			 */
			if (!shoulBeOnlineRequest && CurioClient.getInstance().isPeriodicDispatchEnabled()) {
				OfflineRequest offlineRequest = new OfflineRequest(url, params);
				CurioLogger.d(TAG, "[PERIODIC DISPATCH REQ] added to queue. URL:" + url + ", SC: " + offlineRequest.getParams().get(Constants.HTTP_PARAM_SESSION_CODE) + ", HC:"
						+ offlineRequest.getParams().get(Constants.HTTP_PARAM_HIT_CODE));
				DBRequestProcessor.pushToPeriodicDispatchDBQueue(offlineRequest);
			} else {
				OnlineRequest onlineRequest = new OnlineRequest(url, params, callback, priority);
				CurioLogger.d(
						TAG,
						"[ONLINE REQ] added to queue. URL:" + url + ", SC: " + onlineRequest.getParams().get(Constants.HTTP_PARAM_SESSION_CODE) + ", HC:"
								+ onlineRequest.getParams().get(Constants.HTTP_PARAM_HIT_CODE));
				CurioRequestProcessor.pushToOnlineQueue(onlineRequest);
			}
		} else {
			OfflineRequest offlineRequest = new OfflineRequest(url, params);
			CurioLogger.d(
					TAG,
					"[OFFLINE REQ] added to queue. URL:" + url + ", SC: " + offlineRequest.getParams().get(Constants.HTTP_PARAM_SESSION_CODE) + ", HC:"
							+ offlineRequest.getParams().get(Constants.HTTP_PARAM_HIT_CODE));
			setOfflineRequestExist(true);
			DBRequestProcessor.pushToOfflineDBQueue(offlineRequest);
		}
	}

	/**
	 * Adds given offline request to Offline cache table.
	 * 
	 * @param offlineRequest
	 */
	protected void addRequestToOfflineCache(OfflineRequest offlineRequest) {
		CurioLogger.d(TAG, "[OFFLINE REQ] added to queue. URL:" + offlineRequest.getUrl() + ", SC: " + offlineRequest.getParams().get(Constants.HTTP_PARAM_SESSION_CODE) + ", HC:"
				+ offlineRequest.getParams().get(Constants.HTTP_PARAM_HIT_CODE));
		setOfflineRequestExist(true);
		DBRequestProcessor.pushToOfflineDBQueue(offlineRequest);
	}

	@Override
	public void networkConnectivityChanged(boolean isConnected) {
		CurioLogger.d(TAG, "NETWORK CONNECTIVITY CHANGED, CONNECTION STATE: " + isConnected);

		if (initialConnectionState != null && initialConnectionState.booleanValue() == isConnected) {
			initialConnectionState = null;
			return;
		}

		if (isConnected) {
			isOfflineCachingOn = false;
			CurioLogger.i(TAG, "Offline cache is DISABLED.");
			if (getSessionCode(false) == null) {
				startSession(true);
			} else if (getSessionCode(false) != null && !isOfflineRequestDispatchInProgress) {
				isOfflineRequestDispatchInProgress = true;
			}
		} else {
			isOfflineCachingOn = true;
			CurioLogger.i(TAG, "Offline cache is ENABLED.");
		}
	}

	/**
	 * 
	 * @param customId
	 */
	private void sendPushOpenedMsg() {
		if (!getStaticFeatureSet().autoPushRegistration) {
			CurioLogger.e(TAG, "Curio Auto Push Registration is disabled. You cannot call sendPushOpenedMsg() method.");
			return;
		}

		Map<String, Object> params = new HashMap<String, Object>();
		params.put(Constants.HTTP_PARAM_PUSH_TOKEN, PushUtil.getStoredRegistrationId(context));
		params.put(Constants.HTTP_PARAM_VISITOR_CODE, getStaticFeatureSet().getVisitorCode());
		params.put(Constants.HTTP_PARAM_TRACKING_CODE, getStaticFeatureSet().getTrackingCode());
		params.put(Constants.HTTP_PARAM_SESSION_CODE, sessionCode);
		params.put(Constants.HTTP_PARAM_PUSH_ID, pushMessageId);

		if (customId != null) {
			params.put(Constants.HTTP_PARAM_CUSTOM_ID, customId);
		}

		ICurioResultListener callback = new ICurioResultListener() {
			@Override
			public void handleResult(int statusCode, JSONObject result) {
				if (statusCode == HttpStatus.SC_OK) {
					CurioLogger.d(TAG, "Push message id sent to push server.");
					setPushId(null);
				}
			}
		};

		String pushServerURL = CurioClientSettings.getInstance(context).getServerUrl() + Constants.SERVER_URL_SUFFIX_PUSH_DATA;

		OnlineRequest onlineRequest = new OnlineRequest(pushServerURL, params, callback, CurioRequestProcessor.SECOND_PRIORITY);
		CurioRequestProcessor.pushToOnlineQueue(onlineRequest);
	}

	/**
	 * This method is not intended to be called directly. Calling this method directly can cause invalid session errors.
	 * 
	 * @param context
	 * @param registrationId
	 */
	public void sendRegistrationId(Context context, final String registrationId) {
		if (!getStaticFeatureSet().autoPushRegistration) {
			CurioLogger.e(TAG, "Curio Auto Push Registration is disabled. You cannot call sendRegistrationId() method.");
			return;
		}

		Map<String, Object> params = new HashMap<String, Object>();
		params.put(Constants.HTTP_PARAM_PUSH_TOKEN, registrationId);
		params.put(Constants.HTTP_PARAM_VISITOR_CODE, getStaticFeatureSet().getVisitorCode());
		params.put(Constants.HTTP_PARAM_TRACKING_CODE, getStaticFeatureSet().getTrackingCode());
		params.put(Constants.HTTP_PARAM_SESSION_CODE, sessionCode);

		if (customId != null) {
			params.put(Constants.HTTP_PARAM_CUSTOM_ID, customId);
		}

		ICurioResultListener callback = new ICurioResultListener() {
			@Override
			public void handleResult(int statusCode, JSONObject result) {
				if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					if (unauthCount <= 5) {
						unauthCount++;
						CurioLogger.d(TAG, "SendRegistrationId - Try count: " + unauthCount);
						/**
						 * If sessionStart request not already sent, 1-Stop second and third priority request queue processing. 2-Send sessionStart request. 3-Set sessionStartsent flag.
						 */
						if (!isSessionStartSent) {
							curioRequestProcessor.setLowerPriorityQueueProcessingStatus(false);
							startSession(true);
							isSessionStartSent = true;
						}
						sendRegistrationId(getContext(), registrationId);
					}
				} else if (statusCode == HttpStatus.SC_OK) {
					unauthCount = 0;
					CurioLogger.d(TAG, "Registration id has been successfully sent to push server.");
				} else {
					CurioLogger.e(TAG, "Failed to send registration id. Server responded with status code: " + statusCode);
				}
			}
		};

		String pushServerURL = CurioClientSettings.getInstance(context).getServerUrl() + Constants.SERVER_URL_SUFFIX_PUSH_DATA;

		OnlineRequest onlineRequest = new OnlineRequest(pushServerURL, params, callback, CurioRequestProcessor.SECOND_PRIORITY);
		CurioRequestProcessor.pushToOnlineQueue(onlineRequest);
	}

	/**
	 * Gets the id in push notification payload and send it to push server.
	 * 
	 * Should be used only push notificaton is enabled in the application.
	 * 
	 * @param intent
	 */
	public void getPushData(Intent intent) {
		if (intent.getExtras() != null) {
			String pushId = intent.getExtras().getString(Constants.PAYLOAD_KEY_PUSH_TOKEN);

			if (pushId != null && (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
				setPushId(pushId);
			}
		}
	}

	protected void setOfflineRequestDispatchAsFinished() {
		isOfflineRequestDispatchInProgress = false;
	}

	/**
	 * Dispatch push request result to curio server
	 * 
	 * @param pushId
	 */
	private void setPushId(final String pushId) {
		this.pushMessageId = pushId;
	}

	/**
	 * Custom id. Optional use.
	 * 
	 * @param customParam
	 */
	public void setCustomId(String customId) {
		this.customId = customId;

		// Set this flag false to enable sending registration id to push server.
		// So, if user called setCustomId(), we should set this to false.
		isTriggeredByUnregisterRequest = false;
	}

	/**
	 * Sends custom id to push notification server.
	 * 
	 * Before calling this method GCM registration id should be acquired and ready, or this method will not send custom id to the server.
	 * 
	 * @param customId
	 */
	public void sendCustomId(String customId) {
		if (!getStaticFeatureSet().autoPushRegistration) {
			CurioLogger.e(TAG, "Curio Auto Push Registration is disabled. You cannot call sendCustomId() method.");
			return;
		}

		setCustomId(customId);

		String registrationId = PushUtil.getStoredRegistrationId(context);

		if (registrationId == null) {
			PushUtil.checkForGCMRegistration(context);
		} else {
			sendRegistrationId(context, registrationId);
		}
	}

	/**
	 * Unregisters parent app from push server.
	 */
	public void unregisterFromNotificationServer() {
		if (!getStaticFeatureSet().autoPushRegistration) {
			CurioLogger.e(TAG, "Curio Auto Push Registration is disabled. You cannot call unregisterFromNotificationServer() method.");
			return;
		}

		String registrationId = PushUtil.getStoredRegistrationId(context);

		if (registrationId != null) {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put(Constants.HTTP_PARAM_PUSH_TOKEN, registrationId);
			params.put(Constants.HTTP_PARAM_VISITOR_CODE, getStaticFeatureSet().getVisitorCode());
			params.put(Constants.HTTP_PARAM_TRACKING_CODE, getStaticFeatureSet().getTrackingCode());
			params.put(Constants.HTTP_PARAM_SESSION_CODE, sessionCode);
			params.put(Constants.HTTP_PARAM_CUSTOM_ID, customId);

			ICurioResultListener callback = new ICurioResultListener() {
				@Override
				public void handleResult(int statusCode, JSONObject result) {
					if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
						if (unauthCount <= 5) {
							unauthCount++;
							CurioLogger.d(TAG, "Unregister - Try count: " + unauthCount);
							/**
							 * If sessionStart request not already sent, 1-Stop second and third priority request queue processing. 2-Send sessionStart request. 3-Set sessionStartsent flag.
							 */
							if (!isSessionStartSent) {
								curioRequestProcessor.setLowerPriorityQueueProcessingStatus(false);

								// This flag prevents re-registration at push server when session started again.
								// To enable push server registration again, setCustomId() or sendCustomId() methods should be called,
								// or application instance should be killed.
								isTriggeredByUnregisterRequest = true;
								startSession(true);
								isSessionStartSent = true;
							}
							unregisterFromNotificationServer();
						}
					} else if (statusCode == HttpStatus.SC_OK) {
						unauthCount = 0;
						PushUtil.deleteRegistrationId(context);
						CurioLogger.d(TAG, "Unregister request successfully send to push server.");
					} else {
						CurioLogger.e(TAG, "Failed to unregister. Server responded with status code: " + statusCode);
					}
				}
			};

			String pushServerURL = CurioClientSettings.getInstance(context).getServerUrl() + Constants.SERVER_URL_SUFFIX_UNREGISTER;

			OnlineRequest onlineRequest = new OnlineRequest(pushServerURL, params, callback, CurioRequestProcessor.SECOND_PRIORITY);
			CurioRequestProcessor.pushToOnlineQueue(onlineRequest);
		} else {
			CurioLogger.w(TAG, "No GCM registration id found. Unregister request will not be sent.");
		}
	}

	/**
	 * Returns generated session code if session code is null, or returns existing session code.
	 * 
	 * @return
	 */
	public String getSessionCode(boolean generate) {
		if (sessionCode == null || generate) {
			sessionCode = CurioUtil.generateTimeBasedUUID(System.currentTimeMillis());
		}
		return sessionCode;
	}

	public void setSessionCode(String sessionCode) {
		this.sessionCode = sessionCode;
	}

	public String getUrlPrefix() {
		return urlPrefix;
	}

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public boolean isPeriodicDispatchEnabled() {
		return isPeriodicDispatchEnabled;
	}

	public int getDispatchPeriod() {
		return dispatchPeriod;
	}

	public void setTrackingCode(String trackingCode) {
		if (isParamLoadingFinished) {
			getStaticFeatureSet().setTrackingCode(trackingCode);
		} else {
			tmpTrackingCode = trackingCode;
		}
	}

	public void setApiKey(String apiKey) {
		if (isParamLoadingFinished) {
			getStaticFeatureSet().setApiKey(apiKey);
			;
		} else {
			tmpApiKey = apiKey;
		}
	}

	public void setGcmSenderId(String gcmSenderId) {
		if (isParamLoadingFinished) {
			getStaticFeatureSet().setGcmSenderId(gcmSenderId);
		} else {
			tmpGcmSenderId = gcmSenderId;
		}
	}

	public void setAutoPushRegistrationEnabled(boolean autoPushRegistration) {
		if (isParamLoadingFinished) {
			getStaticFeatureSet().setAutoPushRegistrationEnabled(autoPushRegistration);
		} else {
			tmpAutoPushRegistration = autoPushRegistration;
		}
	}

	public void setSessionTimeout(int sessionTimeout) {
		if (isParamLoadingFinished) {
			getStaticFeatureSet().setSessionTimeout(sessionTimeout);
		} else {
			tmpSessionTimeout = sessionTimeout;
		}
	}

	public void setLoggingEnabled(boolean loggingEnabled) {
		if (isParamLoadingFinished) {
			CurioClientSettings.getInstance(context).setLoggingEnabled(loggingEnabled);
		} else {
			tmpLoggingEnabled = loggingEnabled;
		}
	}

	public void setDispatchPeriod(int dispatchPeriod) {
		this.dispatchPeriod = dispatchPeriod;
	}

	public void setPeriodicDispatchEnabled(boolean isPeriodicDispatchEnabled) {
		this.isPeriodicDispatchEnabled = isPeriodicDispatchEnabled;
	}

	public void setServerUrl(String serverUrl) {
		urlPrefix = serverUrl;
		CurioClientSettings.getInstance(context).setServerUrl(serverUrl);
	}

	public StaticFeatureSet getStaticFeatureSet() {
		return staticFeatureSet;
	}

	/**
	 * Holder class for features that is not changing, mostly device specific features.
	 * 
	 * @author Can Ciloglu
	 *
	 */
	@SuppressLint("NewApi")
	public class StaticFeatureSet {
		private String apiKey;
		private String trackingCode;
		private String visitorCode;
		private int sessionTimeout;
		private String deviceScreenWidth;
		private String deviceScreenHeight;
		private String activityWidth;
		private String activityHeight;
		private String language;
		private String simOperator;
		private String simCountryIso;
		private String networkOperatorName;
		private String connType;
		private String brand;
		private String model;
		private String os;
		private String osVersion;
		private String sdkVersion;
		private String appVersionName;
		private String gcmSenderId;
		private boolean autoPushRegistration;

		public StaticFeatureSet(String apiKey, String trackingCode, String visitorCode, int sessionTimeout, String gcmSenderId, boolean autoPushRegistration) {
			// Get screen sizes
			WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			Display d = wm.getDefaultDisplay();

			int screenWidth = 0;
			int screenHeight = 0;
			int activityWidth = 0;
			int activityHeight = 0;

			/**
			 * Display.getRealSize() is not available below API level 17.
			 */
			if (Build.VERSION.SDK_INT >= Constants.JELLYBEAN_4_2_SDK_INT) {
				// Get real screen size
				Point realSize = new Point();
				d.getRealSize(realSize);
				screenWidth = realSize.x;
				screenHeight = realSize.y;
			} else {
				CurioLogger.d(TAG, "Display.getRealSize() is not available for this SDK Level. Will not get real screen size.");
			}

			/**
			 * Display.getSize() is not available below API level 13.
			 */
			if (Build.VERSION.SDK_INT >= Constants.HONEYCOMB_3_2_SDK_INT) {
				// Get activity screen size
				Point size = new Point();
				d.getSize(size);
				activityWidth = size.x;
				activityHeight = size.y;
			} else {
				CurioLogger.d(TAG, "Display.getSize() is not available for this SDK Level. Will not get screen size.");
			}

			// Get Telephony Service
			TelephonyManager telephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

			// Get application version name string.
			String appVersionName = "0";

			try {
				appVersionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
			} catch (NameNotFoundException e) {
				CurioLogger.e(TAG, e.getMessage(), e);
			}

			this.apiKey = apiKey;
			this.trackingCode = trackingCode;
			this.visitorCode = visitorCode;
			this.sessionTimeout = sessionTimeout;
			this.deviceScreenWidth = Integer.toString(screenWidth);
			this.deviceScreenHeight = Integer.toString(screenHeight);
			this.activityWidth = Integer.toString(activityWidth);
			this.activityHeight = Integer.toString(activityHeight);

			// Get system language ISO code
			this.language = Locale.getDefault().getLanguage();
			this.simOperator = telephonyMgr.getSimOperator();
			this.simCountryIso = telephonyMgr.getSimCountryIso();
			this.networkOperatorName = telephonyMgr.getNetworkOperatorName();
			this.connType = NetworkUtil.getInstance().getConnectionType();
			this.brand = android.os.Build.BRAND;
			this.model = android.os.Build.MODEL;
			this.os = Constants.OS_NAME_STR;
			this.osVersion = Integer.toString(android.os.Build.VERSION.SDK_INT);
			this.sdkVersion = Constants.CURIO_SDK_VER;
			this.appVersionName = appVersionName;
			this.gcmSenderId = gcmSenderId;
			this.autoPushRegistration = autoPushRegistration;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getTrackingCode() {
			return trackingCode;
		}

		public void setTrackingCode(String trackingCode) {
			this.trackingCode = trackingCode;
		}

		public String getVisitorCode() {
			return visitorCode;
		}

		public String getSessionCode() {
			return sessionCode;
		}

		public int getSessionTimeout() {
			return sessionTimeout;
		}

		public void setSessionTimeout(int sessionTimeout) {
			this.sessionTimeout = sessionTimeout;
		}

		public String getDeviceScreenWidth() {
			return deviceScreenWidth;
		}

		public String getDeviceScreenHeight() {
			return deviceScreenHeight;
		}

		public String getActivityWidth() {
			return activityWidth;
		}

		public String getActivityHeight() {
			return activityHeight;
		}

		public String getLanguage() {
			return language;
		}

		public String getSimOperator() {
			return simOperator;
		}

		public String getSimCountryIso() {
			return simCountryIso;
		}

		public String getNetworkOperatorName() {
			return networkOperatorName;
		}

		public String getConnType() {
			return connType;
		}

		public String getBrand() {
			return brand;
		}

		public String getModel() {
			return model;
		}

		public String getOs() {
			return os;
		}

		public String getOsVersion() {
			return osVersion;
		}

		public String getSdkVersion() {
			return sdkVersion;
		}

		public String getAppVersionName() {
			return appVersionName;
		}

		public String getGcmSenderId() {
			return gcmSenderId;
		}

		public void setGcmSenderId(String gcmSenderId) {
			this.gcmSenderId = gcmSenderId;
		}

		public boolean isAutoPushRegistration() {
			return autoPushRegistration;
		}

		public void setAutoPushRegistrationEnabled(boolean autoPushRegistration) {
			this.autoPushRegistration = autoPushRegistration;
		}

	}

	protected boolean offlineRequestExist() {
		return offlineReqExists;
	}

	protected void setOfflineRequestExist(boolean exist) {
		offlineReqExists = exist;
	}

	protected boolean isParamLoadingFinished() {
		return isParamLoadingFinished;
	}

	protected void setParamLoadingFinished(boolean isParamLoadingFinished) {
		this.isParamLoadingFinished = isParamLoadingFinished;
	}
}
