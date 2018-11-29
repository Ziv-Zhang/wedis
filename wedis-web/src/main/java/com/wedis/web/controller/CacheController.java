package com.wedis.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wedis.base.util.BaseController;
import com.wedis.base.vo.ApiResult;
import com.wedis.service.ICacheService;

import redis.clients.jedis.Jedis;

@RestController
@RequestMapping("/cache")
public class CacheController extends BaseController {

	@Autowired
	private ICacheService cacheService;

	@RequestMapping(value = "/list/{id}/{db}", method = RequestMethod.GET)
	public ApiResult<?> list(@PathVariable("id") Long connectionId, @PathVariable Long db) {
		// cacheService.listCache(connectionId, db);
		byte[] b = new JdkSerializationRedisSerializer().serialize("bb");
		return success(new String(b));
	}

	@RequestMapping(value = "/test")
	public ApiResult<?> t(@RequestParam byte[] b) {
		// cacheService.listCache(connectionId, db);
		System.out.println(b);
		byte[] bs = new JdkSerializationRedisSerializer().serialize("bb");
		Jedis jedis = new Jedis();
		// jedis.set("中国aa".getBytes(), "sss".getBytes());
		// jedis.set((new String(b) + "11").getBytes(), "dfdfd".getBytes());
		System.out.println(new String(b));
		return success();
	}
}
