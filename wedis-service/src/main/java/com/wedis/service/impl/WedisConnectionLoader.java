package com.wedis.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wedis.base.dto.ConnectionDto;
import com.wedis.dao.mapper.ConnectionInfoMapper;
import com.wedis.redis.factory.loader.ConnectionLoader;

@Service
public class WedisConnectionLoader implements ConnectionLoader {

	@Autowired
	private ConnectionInfoMapper connMapper;

	@Override
	@Transactional(readOnly = true)
	public List<ConnectionDto> load() {
		return connMapper.list();
	}

}
