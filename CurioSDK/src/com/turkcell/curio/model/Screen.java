/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 10 Haz 2014
 *
 */
package com.turkcell.curio.model;

/**
 * Holder class for screen request structure.
 * 
 * @author Can Ciloglu
 *
 */
public class Screen {
	private String hitCode;
	private String title;
	private String path;
	
	public Screen(String hitCode, String title, String path) {
		this.hitCode = hitCode;
		this.title = title;
		this.path = path;
	}

	public String getHitCode() {
		return hitCode;
	}

	public void setHitCode(String hitCode) {
		this.hitCode = hitCode;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

}
