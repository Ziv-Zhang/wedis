package com.wedis.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wedis.base.dto.ConnectionDto;
import com.wedis.base.exception.CoreException;
import com.wedis.base.util.BaseController;
import com.wedis.base.vo.ApiResult;
import com.wedis.service.IConnectionService;

@RestController
@RequestMapping("/connection")
public class ConnectionController extends BaseController {

	@Autowired
	private IConnectionService connService;

	@PostMapping
	public ApiResult<?> saveConn(@RequestBody ConnectionDto conn) {
		try{
			connService.saveConnection(conn);
		}catch(CoreException e){
			return response(e.getCode());
		}
		return success();
	}

	@GetMapping("/{id}")
	public ApiResult<ConnectionDto> load(@PathVariable Long id) {
		return success(connService.load(id));
	}

	@GetMapping("/list")
	public ApiResult<List<ConnectionDto>> list() {
		return success(connService.list());
	}

	@PostMapping("/test")
	public ApiResult<?> testConn(@RequestBody ConnectionDto conn) {
		try{
			connService.testConnecton(conn);
		}catch(CoreException e){
			return response(e.getCode(), e.getMessage());
		}
		return success();
	}
}
