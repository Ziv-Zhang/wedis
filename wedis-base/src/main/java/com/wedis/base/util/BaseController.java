package com.wedis.base.util;

import com.wedis.base.enums.ResponseCode;
import com.wedis.base.vo.ApiResult;

public class BaseController {
	protected <T> ApiResult<T> success(T data) {
		return response(ResponseCode.SUCCESS, null, data);
	}

	protected ApiResult<?> success() {
		return success(null);
	}

	protected ApiResult<?> response(ResponseCode code) {
		return response(code, null);
	}

	protected ApiResult<?> response(ResponseCode code, String msg) {
		return response(code, msg, null);
	}

	protected <T> ApiResult<T> response(ResponseCode code, String msg, T data) {
		ApiResult<T> result = new ApiResult<>();
		result.setCode(code);
		result.setMsg(msg);
		result.setContent(data);
		return result;
	}
}
