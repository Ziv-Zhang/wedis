package com.wedis.base.enums;

public enum ResponseCode {
	SUCCESS(200),//
	ERROR(500), // 未知错误
	CONNECTION_FAILED(501),// redis连接错误
	AUTH_FAILED(502),// redis密码错误
	DUPLICATE_KEY(503),// 重复的key
	JDK_DESERIALIZE_FAILED(504), // jdk反序列化异常
	;
	
	private int code;

	private ResponseCode(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
