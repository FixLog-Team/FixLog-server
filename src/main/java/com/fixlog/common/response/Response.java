package com.fixlog.common.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fixlog.common.code.Code;
import org.apache.commons.lang3.StringUtils;

public class Response {
	public Code code;
	public String message;

	public Response() {
	}

	public Response(Code code, String message) {
		this.code = code;
		this.message = message;
	}

	public static Response success() {
		return new Response(Code.SUCCESS, StringUtils.EMPTY);
	}

	public static Response success(String message) {
		return new Response(Code.SUCCESS, message);
	}

	public static Response failure() {
		return new Response(Code.UNKNOWN, StringUtils.EMPTY);
	}

	public static Response failure(Code code) {
		return new Response(code, StringUtils.EMPTY);
	}

	public static Response failure(String message) {
		return new Response(Code.UNKNOWN, message);
	}

	public static Response failure(Code code, String message) {
		return new Response(code, message);
	}

	public Code getCode() {
		return code;
	}

	public void setCode(Code code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@JsonIgnore
	public boolean isFail() {
		return Code.SUCCESS != code;
	}

	@JsonIgnore
	public boolean isSuccess() {
		return Code.SUCCESS == code;
	}

	@Override
	public String toString() {
		return "Response{" +
			"code=" + code +
			", message='" + message + '\'' +
			'}';
	}
}
