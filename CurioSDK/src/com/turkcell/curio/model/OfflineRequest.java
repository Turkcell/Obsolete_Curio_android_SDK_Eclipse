/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.model;

import java.util.Map;

import com.turkcell.curio.utils.Constants;
import com.turkcell.curio.utils.CurioUtil;

/**
 * Holder class for offline request structure.
 * 
 * @author Can Ciloglu
 *
 */
public class OfflineRequest {
	private String url;
	private Map<String, Object> params;
	
	public OfflineRequest(String url, Map<String, Object> params) {
		/**
		 * Adding timestamp and request type which are required at server.
		 */
		params.put(Constants.JSON_NODE_TIMESTAMP, System.currentTimeMillis());
		params.put(Constants.JSON_NODE_TYPE, CurioUtil.getRequestType(url));
		setParams(params);
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Map<String, Object> getParams() {
		return params;
	}

	public void setParams(Map<String, Object> params) {
		this.params = params;
	}
}
