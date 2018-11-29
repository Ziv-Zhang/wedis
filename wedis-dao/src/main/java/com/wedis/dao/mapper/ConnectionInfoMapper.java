package com.wedis.dao.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.wedis.base.dto.ConnectionDto;

public interface ConnectionInfoMapper {
	@Insert("INSERT INTO connection_info (name,host,port,pwd) VALUES (#{name},#{host},#{port},#{pwd})")
	public void save(ConnectionDto info);

	@Select("SELECT * FROM connection_info")
	public List<ConnectionDto> list();

	@Select("SELECT * FROM connection_info WHERE id=#{id}")
	public ConnectionDto load(Long id);

	@Select("SELECT id FROM connection_info WHERE name=#{name}")
	public Long loadIdForName(String name);

	@Update("UPDATE connection_info SET name=#{name},host=#{host},port=#{port},pwd=#{pwd} WHERE id=#{id}")
	public void update(ConnectionDto info);

}
