package com.wedis.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import com.wedis.base.dto.CacheDto;
import com.wedis.redis.factory.WedisConnectionFactory;
import com.wedis.service.ICacheService;

@Service
public class CacheServiceImpl implements ICacheService {

	@Autowired
	private WedisConnectionFactory factorys;

	@Override
	public List<CacheDto> listCache(Long connId, Long db) {
		RedisTemplate<byte[], byte[]> template = factorys.getTemplate(connId);
		String key = new StringRedisSerializer()
			.deserialize(new JdkSerializationRedisSerializer().serialize("bb"));
		return null;
	}
}
