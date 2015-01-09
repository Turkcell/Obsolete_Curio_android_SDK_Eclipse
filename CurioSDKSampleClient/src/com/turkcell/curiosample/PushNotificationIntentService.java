/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.turkcell.curiosample;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class PushNotificationIntentService extends IntentService {

	private int notificationId;
	private NotificationManager notificationManager;

	public PushNotificationIntentService() {
		super("PushNotificationIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		String messageType = gcm.getMessageType(intent);

		if (!extras.isEmpty()) {
			if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
				createNotification(extras);
			}
		}

		PushNotificationBroadcastReceiver.completeWakefulIntent(intent);
	}

	/**
	 * Create notification after push received.
	 * 
	 * @param extras
	 */
	private void createNotification(Bundle extras) {
		String message = extras.getString("collapse_key");
		notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.putExtras(extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.ic_launcher).setContentTitle("Sample App Notification").setContentText(message);

		notificationBuilder.setContentIntent(contentIntent);
		notificationBuilder.setAutoCancel(true);
		Notification notification = notificationBuilder.build();
		notification.defaults |= Notification.DEFAULT_VIBRATE;

		notificationManager.notify(notificationId, notification);
		notificationId++;
	}
}
