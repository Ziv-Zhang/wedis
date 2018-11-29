package com.wedis.base.vo;

import com.wedis.base.enums.ResponseCode;

public class ApiResult<T> {
	private ResponseCode code;
	private String msg;
	private T content;

	public int getCode() {
		return code == null ? -1 : code.getCode();
	}

	public void setCode(ResponseCode code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public T getContent() {
		return content;
	}

	public void setContent(T content) {
		this.content = content;
	}
}
