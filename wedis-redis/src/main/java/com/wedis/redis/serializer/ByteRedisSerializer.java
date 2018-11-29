package com.wedis.redis.serializer;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class ByteRedisSerializer implements RedisSerializer<byte[]> {

	public static final ByteRedisSerializer INSTANCE = new ByteRedisSerializer();

	private ByteRedisSerializer() {
	}

	@Override
	public byte[] serialize(byte[] t) throws SerializationException {
		return t;
	}

	@Override
	public byte[] deserialize(byte[] bytes) throws SerializationException {
		return bytes;
	}

}
