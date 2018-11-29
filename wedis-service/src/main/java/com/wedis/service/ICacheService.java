package com.wedis.service;

import java.util.List;

import com.wedis.base.dto.CacheDto;

public interface ICacheService {
	List<CacheDto> listCache(Long connId, Long db);
}
