/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.turkcell.curio.model.OfflineRequest;
import com.turkcell.curio.model.OnlineRequest;
import com.turkcell.curio.utils.Constants;
import com.turkcell.curio.utils.CurioClientSettings;
import com.turkcell.curio.utils.CurioDBContract.CurioOfflineCacheEntry;
import com.turkcell.curio.utils.CurioDBContract.CurioPeriodicDispatchEntry;
import com.turkcell.curio.utils.CurioDBHelper;
import com.turkcell.curio.utils.CurioLogger;
import com.turkcell.curio.utils.NetworkUtil;

/**
 * Processor thread for all requests (periodic/offline/online). Requests pushed to queues and then polled and processed from those queues.
 * 
 * @author Can Ciloglu
 * 
 */
public class CurioRequestProcessor implements Runnable {
	private static final String TAG = "CurioRequestProcessor";
	private static final BlockingQueue<OnlineRequest> firstPriorityQueue = new LinkedBlockingQueue<OnlineRequest>(Constants.REQUEST_QUEUE_CAPACITY);
	private static final BlockingQueue<OnlineRequest> secondPriorityQueue = new LinkedBlockingQueue<OnlineRequest>(Constants.REQUEST_QUEUE_CAPACITY);
	private static final BlockingQueue<OnlineRequest> thirdPriorityQueue = new LinkedBlockingQueue<OnlineRequest>(Constants.REQUEST_QUEUE_CAPACITY);

	public static final int FIRST_PRIORITY = 1;
	public static final int SECOND_PRIORITY = 2;
	public static final int THIRD_PRIORITY = 3;

	private boolean isPeriodicDispatchEnabled;

	private long dispatchPeriod;

	private boolean release = false;

	private CurioClient clientInstance;
	private Context context;
	private long lastPeriodicDispatchCheckTime;
	private int offlineTryCount = 0;
	private boolean lowerPriorityQueueProcessing = true;

	public CurioRequestProcessor(CurioClient clientInstance) {
		this.clientInstance = clientInstance;
		this.context = clientInstance.getContext();
		isPeriodicDispatchEnabled = CurioClientSettings.getInstance(clientInstance.getContext()).isPeriodicDispatchEnabled();

		if (isPeriodicDispatchEnabled) {
			dispatchPeriod = CurioClientSettings.getInstance(clientInstance.getContext()).getDispatchPeriod();
		}
	}

	/**
	 * Pushes request to online queue.
	 * 
	 * @param onlineRequest
	 */
	public static void pushToOnlineQueue(OnlineRequest onlineRequest) {
		BlockingQueue<OnlineRequest> queue = null;

		switch (onlineRequest.getPriority()) {
		case FIRST_PRIORITY:
			queue = firstPriorityQueue;
			break;
		case SECOND_PRIORITY:
			queue = secondPriorityQueue;
			break;
		case THIRD_PRIORITY:
			queue = thirdPriorityQueue;
			break;
		}

		queue.add(onlineRequest);
	}

	public void run() {
		try {

			/**
			 * Do not start processing requests until parameter loading completely finishes, since we need those params for processing.
			 */
			while (!clientInstance.isParamLoadingFinished()) {
				Thread.sleep(100);
			}

			while (true) {
				if (NetworkUtil.getInstance().isConnected()) {
					processStoredOfflineRequests();
					if (isPeriodicDispatchEnabled) {
						processStoredPeriodicDispatchRequests();
					}
					processOnlineQueue(firstPriorityQueue, FIRST_PRIORITY);
					processOnlineQueue(secondPriorityQueue, SECOND_PRIORITY);
					processOnlineQueue(thirdPriorityQueue, THIRD_PRIORITY);
				}

				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			CurioLogger.e(TAG, e.getMessage());
		}
	}

	/**
	 * Processes stored periodic dispatch requests.
	 */
	private void processStoredPeriodicDispatchRequests() {
		if (NetworkUtil.getInstance().isConnected()) {
			
			/**
			 * If release true, send periodic dispatch data immediately or,
			 * wait for the dispatch period.
			 */
			if (!release && !checkPeriodicDispatchTime()) {
				return;
			}

			CurioLogger.d(TAG, "Time for periodic dispatch! Preparing to dispatch stored activities...");

			String jsonData = CurioDBHelper.getInstance().fetchStoredRequestsFromTable(CurioPeriodicDispatchEntry.TABLE_NAME);

			if (jsonData == null) {
				return;
			}

			/**
			 * Send fetched requests
			 */
			String url = CurioClientSettings.getInstance(context).getServerUrl() + Constants.SERVER_URL_SUFFIX_PERIODIC_BATCH;

			CurioLogger.d(TAG, "URL : " + url);

			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(url);

			boolean isRequestSuccessful = false;

			try {
				post.setEntity(new UrlEncodedFormEntity(generatePairsForPeriodicDispatch(jsonData)));
				HttpResponse httpResponse = client.execute(post);

				int statusCode = httpResponse.getStatusLine().getStatusCode();

				CurioLogger.d(TAG, "Periodic batch request sent, and response status code is " + statusCode);

				if (statusCode == HttpStatus.SC_OK) {
					isRequestSuccessful = true;
				} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
					/**
					 * If http status code is 401, this means session has been timed out, so start a new session By setting sessionCode to null, we can ensure to generate a new session code.
					 */
					clientInstance.setSessionCode(null);
					clientInstance.startSession(true);
				}

			} catch (Exception e) {
				CurioLogger.e(TAG, e.getMessage(), e);
				isRequestSuccessful = false;
			}

			client.getConnectionManager().closeExpiredConnections();

			/**
			 * If dispatching successful, delete sent request records from DB. If not, keep them on DB as not in process, so they can be sent on next dispatch.
			 */
			if (isRequestSuccessful) {
				CurioDBHelper.getInstance().deleteInProcessPeriodicRequests();
			} else {
				CurioDBHelper.getInstance().setInProcessPeriodicRequestsAsNotInProcess();
			}

			/**
			 * If exiting from application and release command sent, end session.
			 */
			if (release) {
				release = false;
				clientInstance.endSession();
			}
		} else {
			/**
			 * If no network is available, just set last successful dispatch time to reschedule periodic dispatch check. And when the next dispatch time comes, if the network is available, stored
			 * requests will be sent.
			 */
			CurioLogger.d(TAG, "No network connection available. Periodic dispatch check is aborted. Will check in " + dispatchPeriod + " min.");
		}
	}

	/**
	 * Checks if periodic dispatch time has come.
	 * 
	 * @return
	 */
	private boolean checkPeriodicDispatchTime() {
		long now = System.currentTimeMillis();
		if ((now - lastPeriodicDispatchCheckTime) > (dispatchPeriod * 60 * 1000)) {
			lastPeriodicDispatchCheckTime = now;
			return true;
		}
		return false;
	}

	/**
	 * Processes stored offline cache requests
	 */
	private void processStoredOfflineRequests() {
		if (!clientInstance.offlineRequestExist()) {
			return;
		}

		String jsonData = CurioDBHelper.getInstance().fetchStoredRequestsFromTable(CurioOfflineCacheEntry.TABLE_NAME);

		if (jsonData == null) {
			CurioLogger.d(TAG, "There are no stored offline requests. Aborting offline request dispatch.");
			clientInstance.setOfflineRequestExist(false);
			return;
		}

		/**
		 * Send fetched requests
		 */
		String url = CurioClientSettings.getInstance(context).getServerUrl() + Constants.SERVER_URL_SUFFIX_OFFLINE_CACHE;

		CurioLogger.d(TAG, "URL : " + url);

		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);

		boolean isRequestSuccessful = true;

		try {
			post.setEntity(new UrlEncodedFormEntity(generatePairsForOfflineRequest(jsonData)));
			HttpResponse httpResponse = client.execute(post);

			int statusCode = httpResponse.getStatusLine().getStatusCode();
			CurioLogger.d(TAG, "Offline cache request sent, and response status code is " + statusCode);

			String response = null;

			if (statusCode == HttpStatus.SC_OK) {
				offlineTryCount = 0;
				release = false;
				ResponseHandler<String> responseHandler = new BasicResponseHandler();
				response = responseHandler.handleResponse(httpResponse);

				CurioLogger.d(TAG, "OFFLINE REQ RESPONSE: " + response);

				JSONObject jsonResult = null;

				if (response != null && !(response.trim().length() == 0)) {
					try {
						jsonResult = new JSONObject(response);
						String sessionCode = jsonResult.getString(Constants.JSON_NODE_SESSION_CODE);

						if (sessionCode != null && !(response.trim().length() == 0)) {
							clientInstance.setSessionCode(sessionCode);
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			} else {
				offlineTryCount++;
				CurioLogger.d(TAG, "Offline request has been unsuccessful. Try count is " + offlineTryCount);

				if (offlineTryCount > 4) {
					isRequestSuccessful = true; // Do not try anymore.
				} else {
					isRequestSuccessful = false;
				}
			}
		} catch (Exception e) {
			CurioLogger.e(TAG, e.getMessage(), e);
			
			offlineTryCount++;
			CurioLogger.e(TAG, "Offline request has been unsuccessful. Try count is " + offlineTryCount);
			
			if (offlineTryCount > 4) {
				isRequestSuccessful = true; // Do not try anymore.
			} else {
				isRequestSuccessful = false;
			}
		}

		client.getConnectionManager().closeExpiredConnections();

		/**
		 * If dispatching successful, delete sent request records from DB.
		 */
		if (isRequestSuccessful) {
			CurioDBHelper.getInstance().deleteInProcessOfflineRequests();
		} else {
			CurioDBHelper.getInstance().setInProcessOfflineRequestsAsNotInProcess();
		}

		clientInstance.setOfflineRequestDispatchAsFinished();
	}

	/**
	 * Generates name value pair list from given json data for offline requests.
	 * 
	 * @param trackingCode
	 * @param visitorCode
	 * @param sessionCode
	 * @param sessionTimeout
	 * @param json
	 * @return
	 */
	private List<? extends NameValuePair> generatePairsForOfflineRequest(String jsonData) {
		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_API_KEY, clientInstance.getStaticFeatureSet().getApiKey()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SESSION_TIMEOUT, Integer.toString(clientInstance.getStaticFeatureSet().getSessionTimeout())));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_VISITOR_CODE, clientInstance.getStaticFeatureSet().getVisitorCode()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_TRACKING_CODE, clientInstance.getStaticFeatureSet().getTrackingCode()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SCREEN_WIDTH, clientInstance.getStaticFeatureSet().getDeviceScreenWidth()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SCREEN_HEIGHT, clientInstance.getStaticFeatureSet().getDeviceScreenHeight()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_ACTIVITY_WIDTH, clientInstance.getStaticFeatureSet().getActivityWidth()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_ACTIVITY_HEIGHT, clientInstance.getStaticFeatureSet().getActivityHeight()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_LANG, clientInstance.getStaticFeatureSet().getLanguage()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SIM_OPERATOR, clientInstance.getStaticFeatureSet().getSimOperator()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SIM_COUNTRY_ISO, clientInstance.getStaticFeatureSet().getSimCountryIso()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_NETWORK_OPERATOR_NAME, clientInstance.getStaticFeatureSet().getNetworkOperatorName()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_INTERNET_CONN_TYPE, clientInstance.getStaticFeatureSet().getConnType()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_BRAND, clientInstance.getStaticFeatureSet().getBrand()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_MODEL, clientInstance.getStaticFeatureSet().getModel()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_OS_TYPE, clientInstance.getStaticFeatureSet().getOs()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_OS_VERSION, clientInstance.getStaticFeatureSet().getOsVersion()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_CURIO_SDK_VERSION, clientInstance.getStaticFeatureSet().getSdkVersion()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_APP_VERSION, clientInstance.getStaticFeatureSet().getAppVersionName()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_JSON_DATA, jsonData));

		for (NameValuePair pair : paramList) {
			CurioLogger.d(TAG, "PARAM --> " + pair.getName() + " : " + pair.getValue());
		}
		return paramList;
	}

	/**
	 * Generates name value pair list from given json data for periodic dispatch requests.
	 * 
	 * @param trackingCode
	 * @param visitorCode
	 * @param sessionCode
	 * @param sessionTimeout
	 * @param json
	 * @return
	 */
	private List<? extends NameValuePair> generatePairsForPeriodicDispatch(String jsonData) {
		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SESSION_CODE, clientInstance.getStaticFeatureSet().getSessionCode()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_SESSION_TIMEOUT, Integer.toString(clientInstance.getStaticFeatureSet().getSessionTimeout())));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_VISITOR_CODE, clientInstance.getStaticFeatureSet().getVisitorCode()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_TRACKING_CODE, clientInstance.getStaticFeatureSet().getTrackingCode()));
		paramList.add(new BasicNameValuePair(Constants.HTTP_PARAM_JSON_DATA, jsonData));

		for (NameValuePair pair : paramList) {
			CurioLogger.d(TAG, "PARAM --> " + pair.getName() + " : " + pair.getValue());
		}
		return paramList;
	}

	/**
	 * Processes online request queue.
	 * 
	 * @param queue
	 * @param priority 
	 */
	private void processOnlineQueue(BlockingQueue<OnlineRequest> queue, int priority) {
		OnlineRequest onlineRequest = null;

		if (queue.size() > 0) {
			while (continueProcessing(priority)) {
				onlineRequest = queue.poll();

				if (onlineRequest == null) {
					break;
				}

				try {
					sendRequest(onlineRequest);
				} catch (Exception e) {
					CurioLogger.e(TAG, "" + e.getMessage(), e);
					if (e instanceof IOException) {
						addFailedOnlineRequestToOfflineCache(onlineRequest);
					}
				}

			}
			CurioLogger.v(TAG, "Processing of queue with priority " + priority + " is finished. Queue size is " + queue.size());
		}
	}

	/**
	 * Decides wheter to continue processing second and third priority queues.
	 * Stops processing until session start is successful.
	 */
	private boolean continueProcessing(int priority) {
		if(!shouldLowerPriorityQueuesBeProcessed() && priority > FIRST_PRIORITY){
			return false;
		}
		return true;
	}

	public boolean shouldLowerPriorityQueuesBeProcessed() {
		return lowerPriorityQueueProcessing;
	}
	
	public void setLowerPriorityQueueProcessingStatus(boolean status){
		lowerPriorityQueueProcessing = status;
		CurioLogger.d(TAG, "Second and Third priority queue processing status changed to " + status);
	}

	/**
	 * Adds given online request to offline cache table.
	 * 
	 * @param onlineRequest
	 */
	private void addFailedOnlineRequestToOfflineCache(OnlineRequest onlineRequest) {
		CurioLogger.d(TAG, "Failed to send online request (device may have gone offline during the sending process). Request will be added to offline cache to send it later when network is available.");
		OfflineRequest offlineRequest = new OfflineRequest(onlineRequest.getUrl(), onlineRequest.getParams());
		clientInstance.addRequestToOfflineCache(offlineRequest);
	}

	/**
	 * Sends online request to the server as HTTP Post request. Uses URLEncoded Form, media type: application/x-www-form-urlencoded
	 * 
	 * @param onlineRequest
	 * @throws CurioApiException
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 */
	private void sendRequest(OnlineRequest onlineRequest) throws ClientProtocolException, IOException, UnsupportedEncodingException {
		String url = onlineRequest.getUrl();
		List<NameValuePair> pairs = generatePairsForOnlineRequest(onlineRequest.getParams(), url);
		ICurioResultListener callback = onlineRequest.getCallback();

		CurioLogger.d(TAG, "POST REQUEST for URL: " + url);

		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);
		post.setEntity(new UrlEncodedFormEntity(pairs));

		HttpResponse httpResponse = client.execute(post);

		int statusCode = httpResponse.getStatusLine().getStatusCode();

		String response = null;
		JSONObject jsonResult = null;

		if (statusCode == HttpStatus.SC_OK) {
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			response = responseHandler.handleResponse(httpResponse);

			CurioLogger.d(TAG, "RESPONSE: " + response + " for URL:" + url);

			if (response != null && !(response.trim().length() == 0)) {
				try {
					jsonResult = new JSONObject(response);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		} else {
			CurioLogger.d(TAG, "Status code from server: " + statusCode);
		}

		if (callback != null) {
			callback.handleResult(statusCode, jsonResult);
		}

		client.getConnectionManager().closeExpiredConnections();

		CurioLogger.d(TAG, "-----------------------------------------");
	}

	/**
	 * Generates name value pairs from given params and URL for online requests.
	 * 
	 * @param params
	 * @return
	 */
	private List<NameValuePair> generatePairsForOnlineRequest(Map<String, Object> params, String url) {
		if (url.endsWith(Constants.SERVER_URL_SUFFIX_SESSION_START)) {
			params.put(Constants.HTTP_PARAM_API_KEY, clientInstance.getStaticFeatureSet().getApiKey());
			params.put(Constants.HTTP_PARAM_TRACKING_CODE, clientInstance.getStaticFeatureSet().getTrackingCode());
			params.put(Constants.HTTP_PARAM_VISITOR_CODE, clientInstance.getStaticFeatureSet().getVisitorCode());
			params.put(Constants.HTTP_PARAM_SESSION_TIMEOUT, clientInstance.getStaticFeatureSet().getSessionTimeout());
			params.put(Constants.HTTP_PARAM_SCREEN_WIDTH, clientInstance.getStaticFeatureSet().getDeviceScreenWidth());
			params.put(Constants.HTTP_PARAM_SCREEN_HEIGHT, clientInstance.getStaticFeatureSet().getDeviceScreenHeight());
			params.put(Constants.HTTP_PARAM_ACTIVITY_WIDTH, clientInstance.getStaticFeatureSet().getActivityWidth());
			params.put(Constants.HTTP_PARAM_ACTIVITY_HEIGHT, clientInstance.getStaticFeatureSet().getActivityHeight());
			params.put(Constants.HTTP_PARAM_LANG, clientInstance.getStaticFeatureSet().getLanguage());
			params.put(Constants.HTTP_PARAM_SIM_OPERATOR, clientInstance.getStaticFeatureSet().getSimOperator());
			params.put(Constants.HTTP_PARAM_SIM_COUNTRY_ISO, clientInstance.getStaticFeatureSet().getSimCountryIso());
			params.put(Constants.HTTP_PARAM_NETWORK_OPERATOR_NAME, clientInstance.getStaticFeatureSet().getNetworkOperatorName());
			params.put(Constants.HTTP_PARAM_INTERNET_CONN_TYPE, clientInstance.getStaticFeatureSet().getConnType());
			params.put(Constants.HTTP_PARAM_BRAND, clientInstance.getStaticFeatureSet().getBrand());
			params.put(Constants.HTTP_PARAM_MODEL, clientInstance.getStaticFeatureSet().getModel());
			params.put(Constants.HTTP_PARAM_OS_TYPE, clientInstance.getStaticFeatureSet().getOs());
			params.put(Constants.HTTP_PARAM_OS_VERSION, clientInstance.getStaticFeatureSet().getOsVersion());
			params.put(Constants.HTTP_PARAM_CURIO_SDK_VERSION, clientInstance.getStaticFeatureSet().getSdkVersion());
			params.put(Constants.HTTP_PARAM_APP_VERSION, clientInstance.getStaticFeatureSet().getAppVersionName());
		} else {
			params.put(Constants.HTTP_PARAM_SESSION_CODE, clientInstance.getSessionCode(false));
			params.put(Constants.HTTP_PARAM_TRACKING_CODE, clientInstance.getStaticFeatureSet().getTrackingCode());
			params.put(Constants.HTTP_PARAM_VISITOR_CODE, clientInstance.getStaticFeatureSet().getVisitorCode());
			params.put(Constants.HTTP_PARAM_SESSION_TIMEOUT, clientInstance.getStaticFeatureSet().getSessionTimeout());
		}

		List<NameValuePair> newPairs = new ArrayList<NameValuePair>();

		for (Map.Entry<String, Object> entry : params.entrySet()) {
			NameValuePair newPair = new BasicNameValuePair(entry.getKey(), entry.getValue().toString());

			CurioLogger.d(TAG, "PARAM --> " + newPair.getName() + " : " + newPair.getValue());

			newPairs.add(newPair);
		}

		return newPairs;
	}

	public static BlockingQueue<OnlineRequest> getFirstPriorityQueue() {
		return firstPriorityQueue;
	}

	public static BlockingQueue<OnlineRequest> getSecondPriorityQueue() {
		return secondPriorityQueue;
	}

	public static BlockingQueue<OnlineRequest> getThirdPriorityQueue() {
		return thirdPriorityQueue;
	}

	/**
	 * Sets release flag to true.
	 */
	public void releaseStoredRequests() {
		release = true;
	}
	
	public void cancelReleaseStoredRequestFlag(){
		release = false;
	}
}
