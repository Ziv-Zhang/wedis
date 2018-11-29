package com.wedis.web.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.wedis.redis.serializer.ByteRedisSerializer;

import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class JedisConfig {

	@Bean
	@ConfigurationProperties(prefix = "spring.redis.jedis.pool")
	public JedisPoolConfig jedisPoolConfig() {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		return poolConfig;
	}

	@Bean
	public JedisClientConfiguration clientConfiguration(JedisPoolConfig poolConfig) {
		JedisClientConfiguration client = JedisClientConfiguration.builder().usePooling()
			.poolConfig(poolConfig).build();
		return client;
	}

//	@Bean
//	public JedisConnectionFactory jedisPool(JedisPoolConfig poolConfig) {
//		RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("localhost",
//			6379);
//		standalone.setPassword(RedisPassword.of("123"));
//		JedisClientConfiguration client = JedisClientConfiguration.builder().usePooling()
//			.poolConfig(poolConfig).build();
//
//		JedisConnectionFactory factory = new JedisConnectionFactory(standalone, client);
//		return factory;
//	}
//
//	@Bean("aa")
//	@Primary
//	public RedisTemplate redisTemplate(JedisConnectionFactory factory) {
//		RedisTemplate redisTemplate = new RedisTemplate<>();
//		redisTemplate.setConnectionFactory(factory);
//		redisTemplate.setDefaultSerializer(ByteRedisSerializer.INSTANCE);
//		// redisTemplate.setKeySerializer(new StringRedisSerializer());
//		// redisTemplate.setValueSerializer(new StringRedisSerializer());
//		// redisTemplate.setKeySerializer(new GenericToStringSerializer);
//		return redisTemplate;
//	}
}
