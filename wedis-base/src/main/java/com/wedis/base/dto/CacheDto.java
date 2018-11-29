package com.wedis.base.dto;

import java.io.Serializable;

public class CacheDto implements Serializable {

	private static final long serialVersionUID = -661965120840403114L;

	private String key;
	private String value;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
