package com.wedis.redis.converter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.core.convert.converter.Converter;

import com.wedis.base.enums.ResponseCode;
import com.wedis.base.exception.CoreException;
import com.wedis.redis.io.JdkInputStream;

public class JdkDeserializingConverter implements Converter<byte[], Object> {

	@Override
	public Object convert(byte[] source) {
		JdkInputStream jis = null;
		try{
			jis = new JdkInputStream(new ByteArrayInputStream(source));
			return jis.readObject();
		}catch(Exception e){
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
				"deserializing failed?" + e.toString());
		}finally{
			if(jis != null)
				try{
					jis.close();
				}catch(IOException e){
					throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
						"deserializing failed?" + e.toString());
				}
		}
	}

}
