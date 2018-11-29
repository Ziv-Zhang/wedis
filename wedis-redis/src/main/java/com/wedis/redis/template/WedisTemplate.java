package com.wedis.redis.template;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import javax.xml.bind.DatatypeConverter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import com.wedis.base.bean.RedisInfo;
import com.wedis.base.enums.SerializeType;
import com.wedis.redis.factory.WedisConnectionFactory;
import com.wedis.redis.serializer.ByteRedisSerializer;
import com.wedis.redis.serializer.JdkRedisSerializer;

import redis.clients.jedis.Jedis;

@Component
public class WedisTemplate {
	@Autowired
	private WedisConnectionFactory factory;

	public void selectDb(RedisInfo info) {
		if(info.getId() == null || info.getDb() == null)
			return;
		RedisConnection conn = factory.getConnection(info.getId());
		if(conn != null)
			conn.select(info.getDb());
	}

	public void resetSerialize(RedisInfo info) {
		if(info.getId() == null || info.getSerializeType() == null)
			return;
		RedisTemplate<byte[], byte[]> template = factory.getTemplate(info.getId());
		template.setDefaultSerializer(getSerializer(info.getSerializeType()));
	}

	private RedisSerializer<?> getSerializer(SerializeType type) {
		switch(type){
		case JDK:
			return JdkRedisSerializer.INSTANCE;
		default:
			return ByteRedisSerializer.INSTANCE;
		}
	}

	public <K, V> RedisTemplate<K, V> getTemplate(RedisInfo<K, V> info) {
		// selectDb(info);
		// resetSerialize(info);
		// return factory.getTemplate(info.getId());
		return null;
	}

	public static void main(String[] args) throws UnsupportedEncodingException {
		//[-84, -19, 0, 5, 116, 0, 2, 98, 98]
		byte[] b = new JdkSerializationRedisSerializer().serialize("bb");
		Jedis jedis = new Jedis();
		// jedis.set("中国aa".getBytes(), "sss".getBytes());
		// jedis.set((new String(b) + "11").getBytes(), "dfdfd".getBytes());
		System.out.println(new String(b, "unicode"));
		System.out.println(Arrays.toString("中国".getBytes()));
		System.out.println(DatatypeConverter.printHexBinary("中国".getBytes()));

		String s = "\\xe4\\xb8\\xad\\xe5\\x9b\\xbd";
		System.out.println(s);

		for(byte i = 32; i < 127; i++){
			System.out.print(new String(new byte[]{i}));
		}
		/**
		 * 对32-126的数据进行转换字符，其他的转换成16进制
		 */
	}
}

