package com.wedis.redis.factory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.wedis.base.dto.ConnectionDto;
import com.wedis.redis.factory.loader.ConnectionLoader;
import com.wedis.redis.serializer.ByteRedisSerializer;

@Component
public class WedisConnectionFactory implements InitializingBean {

	private final Map<Long, JedisConnectionFactory> factorys = new ConcurrentHashMap<>();
	// 使用template主要是为了能够方便的管理连接池的获取和释放(ps:比较懒，但是会多一层无用的转换)
	private final Map<Long, RedisTemplate<byte[], byte[]>> templates = new ConcurrentHashMap<>();

	@Autowired
	private ConnectionLoader loader;
	@Autowired
	private JedisClientConfiguration clientConfig;

	@Override
	public void afterPropertiesSet() throws Exception {
		List<ConnectionDto> conns = loader.load();
		for(ConnectionDto c : conns){
			addConnection(c);
		}
	}

	public void addConnection(ConnectionDto conn) {
		addTemplate(conn, addFactory(conn));
	}

	public void destroy(Long id) {
		templates.remove(id);
		JedisConnectionFactory factory = factorys.remove(id);
		if(factory != null)
			factory.destroy();
	}

	private JedisConnectionFactory addFactory(ConnectionDto conn) {
		RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
		standalone.setHostName(conn.getHost());
		standalone.setPort(conn.getPort());
		standalone.setPassword(RedisPassword.of(conn.getPwd()));

		JedisConnectionFactory factory = new JedisConnectionFactory(standalone, clientConfig);
		factory.afterPropertiesSet();
		addFactory(conn.getId(), factory);

		return factory;
	}

	private RedisTemplate<byte[], byte[]> addTemplate(ConnectionDto conn,
		JedisConnectionFactory factory) {
		RedisTemplate<byte[], byte[]> template = new RedisTemplate<>();
		template.setConnectionFactory(factory);
		template.setDefaultSerializer(ByteRedisSerializer.INSTANCE);
		template.afterPropertiesSet();
		addTemplate(conn.getId(), template);

		return template;
	}

	private void addFactory(Long id, JedisConnectionFactory factory) {
		Assert.notNull(id, "Id must not be null");
		Assert.notNull(factory, "Factory must not be null");
		factorys.put(id, factory);
	}
	
	private void addTemplate(Long id, RedisTemplate<byte[], byte[]> template) {
		Assert.notNull(id, "Id must not be null");
		Assert.notNull(template, "RedisTemplate must not be null");
		templates.put(id, template);
	}

	public RedisConnection getConnection(Long id) {
		Assert.notNull(id, "Id must not be null");
		JedisConnectionFactory factory = factorys.get(id);
		if(factory == null) {
			throw new IllegalArgumentException("ConnectionFactory not found, id:"+id);
		}
		return factory.getConnection();
	}

	public RedisTemplate<byte[], byte[]> getTemplate(Long id) {
		Assert.notNull(id, "Id must not be null");
		RedisTemplate<byte[], byte[]> template = templates.get(id);
		if(template == null){
			throw new IllegalArgumentException("RedisTemplate not found, id:" + id);
		}
		return template;
	}

}
