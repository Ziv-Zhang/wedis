package com.wedis.base.bean;

import com.wedis.base.enums.SerializeType;

public class RedisInfo<K, V> {
	private Long id; // connection id
	private Integer db = 0; // 操作的库，默认选择0
	private SerializeType serializeType;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getDb() {
		return db;
	}

	public void setDb(Integer db) {
		this.db = db;
	}

	public SerializeType getSerializeType() {
		return serializeType;
	}

	public void setSerializeType(SerializeType serializeType) {
		this.serializeType = serializeType;
	}

}
