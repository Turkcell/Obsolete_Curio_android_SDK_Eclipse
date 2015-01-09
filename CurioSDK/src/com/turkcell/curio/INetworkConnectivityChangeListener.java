/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 16 Haz 2014
 *
 */
package com.turkcell.curio;

/**
 * Listener interface for network connectivity change events. 
 * 
 * @author Can Ciloglu
 *
 */
public interface INetworkConnectivityChangeListener {
	
	/**
	 * Notifies network connectivity changes.
	 * 
	 * @param isConnected
	 */
	public void networkConnectivityChanged(boolean isConnected);
}
