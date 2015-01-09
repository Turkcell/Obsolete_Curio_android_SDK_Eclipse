/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.turkcell.curio.model.OfflineRequest;
import com.turkcell.curio.utils.CurioDBHelper;
import com.turkcell.curio.utils.CurioLogger;

/**
 * Processor thread for all requests (periodic/offline/online).
 * Requests pushed to queues and then polled and processed from those queues.
 * 
 * @author Can Ciloglu
 *
 */
public class DBRequestProcessor implements Runnable {
	private static final String TAG = "CurioRequestProcessor";

	private static final BlockingQueue<OfflineRequest> offlineQueue = new LinkedBlockingQueue<OfflineRequest>();
	private static final BlockingQueue<OfflineRequest> periodicDispatchQueue = new LinkedBlockingQueue<OfflineRequest>();
	
	/**
	 * Pushes request to offline cache DB queue.
	 * 
	 * @param offlineRequest
	 */
	public static void pushToOfflineDBQueue(OfflineRequest offlineRequest) {
		offlineQueue.add(offlineRequest);
	}
	
	/**
	 * Pushes request to periaodic dispatch DB  queue.
	 * 
	 * @param offlineRequest
	 */
	public static void pushToPeriodicDispatchDBQueue(OfflineRequest offlineRequest) {
		periodicDispatchQueue.add(offlineRequest);
	}

	public void run() {
		try {
			while (true) {
				processOfflineQueue();
				processPeriodicDispatchQueue();
				Thread.sleep(250);
			}
		} catch (InterruptedException e) {
			CurioLogger.e(TAG, e.getMessage());
		}
	}

	/**
	 * Stores offline request at DB.
	 * 
	 * @param offlineRequest
	 */
	private void storeOfflineRequest(OfflineRequest offlineRequest) {
		/**
		 * Before storing any offline requests, move all periodic dispatch requests to offline request table
		 * to guarantee ordered dispatch of all requests. 
		 */
		CurioDBHelper.getInstance().moveAllExistingPeriodicDispatchDataToOfflineTable();
		
		if (!CurioDBHelper.getInstance().persistOfflineRequestForCaching(offlineRequest)) {
			CurioLogger.e(TAG, "Could not persist offline request.");
		}
	}
	
	/**
	 * Stores periodic dispatch request at DB.
	 * 
	 * @param offlineRequest
	 */
	private void storePeriodicDispatchRequest(OfflineRequest offlineRequest) {
		if (!CurioDBHelper.getInstance().persistOfflineRequestForPeriodicDispatch(offlineRequest)) {
			CurioLogger.e(TAG, "Could not persist periodic dispatch request.");
		}
	}

	/**
	 * Processes offline queue.
	 */
	private void processOfflineQueue() {
		if (offlineQueue.size() > 0) {
			storeOfflineRequest(offlineQueue.poll());
		}
	}
	
	/**
	 * Processes offline queue.
	 */
	private void processPeriodicDispatchQueue() {
		if (periodicDispatchQueue.size() > 0) {
			storePeriodicDispatchRequest(periodicDispatchQueue.poll());
		}
	}
}
