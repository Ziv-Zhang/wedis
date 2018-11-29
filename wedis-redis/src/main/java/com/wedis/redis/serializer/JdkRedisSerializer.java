package com.wedis.redis.serializer;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.wedis.redis.converter.JdkDeserializingConverter;

public class JdkRedisSerializer implements RedisSerializer<Object> {

	private Converter<byte[], Object> deserializer;
	public static final JdkRedisSerializer INSTANCE = new JdkRedisSerializer();

	private JdkRedisSerializer() {
		this.deserializer = new JdkDeserializingConverter();
	}

	@Override
	public byte[] serialize(Object t) throws SerializationException {
		return null;
	}

	@Override
	public Object deserialize(byte[] bytes) throws SerializationException {
		return deserializer.convert(bytes);
	}

}
