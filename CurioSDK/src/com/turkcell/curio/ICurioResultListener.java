/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio;

import org.json.JSONObject;

/**
 * Callback interface for HTTP requests.
 * 
 * @author Can Ciloglu
 *
 */
public interface ICurioResultListener {
  public void handleResult(int htppStatusCode, JSONObject result);
}
