package com.wedis.redis.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import com.alibaba.fastjson.JSON;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import com.wedis.redis.io.JdkInputStream;

import redis.clients.jedis.Jedis;

public class JdkConvertTest {
	@Test
	public void test() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);

		JdkConvertBean bean = new JdkConvertBean();

		oos.writeObject(bean);
		System.out.println(JSON.toJSON(new ObjectInputStream(
			new ByteInputStream(baos.toByteArray(), baos.toByteArray().length)).readObject()));
		System.out.println(JSON.toJSON(
			new JdkInputStream(new ByteInputStream(baos.toByteArray(), baos.toByteArray().length))
				.readObject()));
	}

	@Test
	public void inRedis() {
		Jedis jedis = new Jedis();
		jedis.auth("123");
		jedis.set("aa".getBytes(),
			new JdkSerializationRedisSerializer().serialize(new JdkConvertBean()));
	}

	@Test
	public void fromRedis() throws Exception {
		Jedis jedis = new Jedis();
		jedis.auth("123");
		System.out.println(JSON.toJSONString(
			new JdkInputStream(new ByteArrayInputStream(jedis.get("aa".getBytes()))).readObject()));
	}
}

class JdkConvertBean implements Serializable {

	private static final long serialVersionUID = -1888566608793715320L;
	private long bb = 666L;
	private String a = "aaa";
	private int age = 12;
	private int[] arr1 = {1, 2, 3};
	private String[] arr2 = {"11", "aa", "bb"};
	private B[] arr3 = new B[]{new B()};
	private List<AA> list = Arrays.asList(new AA(), new AA(), new AA());
	private C list2 = new C();
	private List<AA> list3 = new ArrayList(Arrays.asList(new AA()));
	private List<D> list4 = new ArrayList<>(Arrays.asList(new D()));
	private D dd = new D();

	public List<D> getList4() {
		return list4;
	}

	public void setList4(List<D> list4) {
		this.list4 = list4;
	}

	public D getDd() {
		return dd;
	}

	public void setDd(D dd) {
		this.dd = dd;
	}

	public long getBb() {
		return bb;
	}

	public void setBb(long bb) {
		this.bb = bb;
	}

	public String getA() {
		return a;
	}

	public void setA(String a) {
		this.a = a;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public int[] getArr1() {
		return arr1;
	}

	public void setArr1(int[] arr1) {
		this.arr1 = arr1;
	}

	public String[] getArr2() {
		return arr2;
	}

	public void setArr2(String[] arr2) {
		this.arr2 = arr2;
	}

	public B[] getArr3() {
		return arr3;
	}

	public void setArr3(B[] arr3) {
		this.arr3 = arr3;
	}

	public List<AA> getList() {
		return list;
	}

	public void setList(List<AA> list) {
		this.list = list;
	}

	public C getList2() {
		return list2;
	}

	public void setList2(C list2) {
		this.list2 = list2;
	}

	public List<AA> getList3() {
		return list3;
	}

	public void setList3(List<AA> list3) {
		this.list3 = list3;
	}

}

class AA implements Serializable {
	private static final long serialVersionUID = 7622970786171127853L;
	private String aa = "vv";

	public String getAa() {
		return aa;
	}

	public void setAa(String aa) {
		this.aa = aa;
	}

}

class B implements Serializable{

	private static final long serialVersionUID = -7555390767675327883L;
	
	private String bb = "bbbb";

	public String getBb() {
		return bb;
	}

	public void setBb(String bb) {
		this.bb = bb;
	}
}

class C implements Serializable {
	private List<AA> cc = new ArrayList<>(Arrays.asList(new AA(), new AA()));

	public List<AA> getCc() {
		return cc;
	}

	public void setCc(List<AA> cc) {
		this.cc = cc;
	}

}

class D extends C implements Serializable {
	private String aa = "vvvsds";

	public String getAa() {
		return aa;
	}

	public void setAa(String aa) {
		this.aa = aa;
	}
}