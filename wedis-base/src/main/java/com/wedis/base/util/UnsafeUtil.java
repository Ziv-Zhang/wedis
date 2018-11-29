package com.wedis.base.util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class UnsafeUtil {

	public static Unsafe unsafe;

	static{
		try{
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe)f.get(null);
		}catch(Exception e){
			unsafe = null;
		}
	}
}
