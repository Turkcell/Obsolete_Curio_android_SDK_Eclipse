/**
 * 
 */
package com.turkcell.curio.utils;

import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.turkcell.curio.CurioClient;

/**
 * 
 * @changed Can Ciloglu
 * @author Ahmet Burak DEMIRKOPARAN
 */
public class PushUtil {

	private static String TAG = "GCMHelper";

	/**
	 * Check the device to make sure it has the Google Play Services APK.
	 * 
	 */
	private static boolean checkPlayServices(Context context) {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
		if (resultCode != ConnectionResult.SUCCESS) {
			Log.e(TAG, "This device does not support Google Play Services.");
			return false;
		}
		return true;
	}

	public static void checkForGCMRegistration(final Context context) {
		if (checkPlayServices(context)) {
			String gcmRegistrationId = getStoredRegistrationId(context);
			if (gcmRegistrationId == null) {
				Thread gcmRegisterThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							String registrationId = GoogleCloudMessaging.getInstance(context).register(CurioClient.getInstance().getStaticFeatureSet().getGcmSenderId());
							CurioLogger.d(TAG, "GCM Registration Id acquired: " + registrationId);
							storeRegistrationId(context, registrationId);
							CurioClient.getInstance().sendRegistrationId(context, registrationId);
						} catch (IOException e) {
							CurioLogger.i(TAG, "An error occured while registering/storing GCM registration id.", e);
						}
					}
				});
				gcmRegisterThread.start();
			}
		}
	}

	private static void storeRegistrationId(Context context, String gcmRegistrationId) {
		SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_NAME_GCM, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(Constants.SHARED_PREF_KEY_GCM_REGID, gcmRegistrationId);
		editor.putInt(Constants.SHARED_PREF_KEY_APP_VERSION, getAppVersion(context));
		editor.commit();
	}

	public static String getStoredRegistrationId(Context context) {
		SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_NAME_GCM, Context.MODE_PRIVATE);
		String registrationId = sharedPreferences.getString(Constants.SHARED_PREF_KEY_GCM_REGID, null);
		int currentAppVersion = sharedPreferences.getInt(Constants.SHARED_PREF_KEY_APP_VERSION, 0);
		if (getAppVersion(context) != currentAppVersion){
			return null;
		}
		return registrationId;
	}

	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}
}