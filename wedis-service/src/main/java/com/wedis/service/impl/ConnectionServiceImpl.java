package com.wedis.service.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wedis.base.dto.ConnectionDto;
import com.wedis.base.enums.ResponseCode;
import com.wedis.base.exception.CoreException;
import com.wedis.dao.mapper.ConnectionInfoMapper;
import com.wedis.redis.factory.WedisConnectionFactory;
import com.wedis.service.IConnectionService;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

@Service
public class ConnectionServiceImpl implements IConnectionService {

	@Autowired
	private ConnectionInfoMapper mapper;
	@Autowired
	private WedisConnectionFactory connFactory;

	private static final Logger logger = LoggerFactory.getLogger(ConnectionServiceImpl.class);

	@Override
	public void saveConnection(ConnectionDto conn) {
		saveSetting(conn);
		connFactory.destroy(conn.getId());
		connFactory.addConnection(conn);
	}

	@Transactional
	private void saveSetting(ConnectionDto conn) {
		try{
			if(conn.getId() == null) {
				mapper.save(conn);
				conn.setId(mapper.loadIdForName(conn.getName()));
			}
			else
				mapper.update(conn);
		}catch(DuplicateKeyException e){
			logger.error("save setting error:{}", e.toString());
			throw new CoreException(ResponseCode.DUPLICATE_KEY);
		}
	}

	@Override
	@Transactional
	public List<ConnectionDto> list() {
		return mapper.list();
	}

	@Override
	public boolean testConnecton(ConnectionDto conn) {
		Jedis jedis = null;
		try{
			jedis = new Jedis(conn.getHost(), conn.getPort());
			if(StringUtils.isNoneBlank(conn.getPwd())){
				jedis.auth(conn.getPwd());
			}
			jedis.echo("1");
			return true;
		}catch(JedisConnectionException e){
			logger.info("redis connection failed", e.toString());
			throw new CoreException(ResponseCode.CONNECTION_FAILED,
				e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
		}catch(JedisDataException e){
			logger.info("redis auth failed", e.toString());
			throw new CoreException(ResponseCode.AUTH_FAILED, e.getMessage());
		}catch(Exception e){
			logger.info("redis connection test failed", e.toString());
			throw new CoreException(ResponseCode.ERROR, e.getMessage());
		}finally{
			jedis.close();
		}
	}

	@Override
	@Transactional(readOnly = true)
	public ConnectionDto load(Long id) {
		return mapper.load(id);
	}
}
