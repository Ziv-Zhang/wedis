package com.wedis.redis.factory.loader;

import java.util.List;

import com.wedis.base.dto.ConnectionDto;

public interface ConnectionLoader {
	public List<ConnectionDto> load();
}
