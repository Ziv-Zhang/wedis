package com.wedis.base.exception;

import com.wedis.base.enums.ResponseCode;

public class CoreException extends RuntimeException {

	private static final long serialVersionUID = 3836956201760312261L;

	private ResponseCode code;
	
	public CoreException(ResponseCode code) {
		super(String.valueOf(code));
		this.code = code;
	}

	public CoreException(ResponseCode code, String msg) {
		super(code + " - " + msg);
		this.code = code;
	}

	public ResponseCode getCode() {
		return code;
	}

	public void setCode(ResponseCode code) {
		this.code = code;
	}

	@Override
	public String toString() {
		return "CoreException [code=" + code + ", getMessage()=" + getMessage() + "]";
	}

}
