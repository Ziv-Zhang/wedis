package com.wedis.service;

import java.util.List;

import com.wedis.base.dto.ConnectionDto;

public interface IConnectionService {
	public void saveConnection(ConnectionDto conn);

	public List<ConnectionDto> list();

	public boolean testConnecton(ConnectionDto conn);

	public ConnectionDto load(Long id);
}
