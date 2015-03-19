/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.utils;

import java.util.UUID;


/**
 * Utility class which holds static methods.
 * 
 * @author Can Ciloglu
 *
 */
public class CurioUtil {
	
//	/**
//	 * !!Legacy method from 8digits. May be removed!!
//	 * @param urlPrefix
//	 * @return
//	 */
//	public static String formatUrlPrefix(String urlPrefix) {
//		if (!urlPrefix.startsWith(Constants.HTTP) && !urlPrefix.startsWith(Constants.HTTPS))
//			urlPrefix = Constants.HTTPS + urlPrefix;
//
//		if (urlPrefix.endsWith(Constants.BACKSLASH))
//			urlPrefix = urlPrefix.substring(0, urlPrefix.length() - 1);
//
//		if (urlPrefix.endsWith(Constants.API))
//			urlPrefix = urlPrefix.substring(0, urlPrefix.length() - (Constants.API.length() + 1));
//
//		return urlPrefix;
//	}
	
	/**
	 * Generates a version 1 Universally Unique Identifier.
	 * 
	 * @return id String.
	 */
	public static String generateTimeBasedUUID(long timeStamp){
		return UUIDGenerator.generateTimeBasedUUID(timeStamp).toString();
	}
	
	/**
	 * Generates a version 4 Universally Unique Identifier.
	 * 
	 * @return
	 */
	public static String generateRandomUUID(){
		return UUID.randomUUID().toString();
	}
	
	/**
	 * Gets request type according to the request url.
	 * 
	 * @param url
	 * @return
	 */
	public static int getRequestType(String url){
		int type = -1;
		if(url.endsWith(Constants.SERVER_URL_SUFFIX_SESSION_START)){
			type = 0;
		}else if(url.endsWith(Constants.SERVER_URL_SUFFIX_SESSION_END)){
			type = 1;
		}else if(url.endsWith(Constants.SERVER_URL_SUFFIX_SCREEN_START)){
			type = 2;
		}else if(url.endsWith(Constants.SERVER_URL_SUFFIX_SCREEN_END)){
			type = 3;
		}else if(url.endsWith(Constants.SERVER_URL_SUFFIX_SEND_EVENT)){
			type = 4;
		}
		
		return type;
	}
}
