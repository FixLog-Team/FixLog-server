package com.fixlog.common.response;

import com.fixlog.common.code.Code;
import org.apache.commons.lang3.StringUtils;

public class DataResponse<T> extends Response {
    private T result;

    public DataResponse() {
    }

    public DataResponse(Code code, T result) {
        this(code, StringUtils.EMPTY, result);
    }

    public DataResponse(Code code, String message, T result) {
        super(code, message);
        this.result = result;
    }

    public static <T> DataResponse<T> success(T result) {
        return new DataResponse<>(Code.SUCCESS, result);
    }

    public static <T> DataResponse<T> success(String message, T result) {
        return new DataResponse<>(Code.SUCCESS, message, result);
    }

    public static <T> DataResponse<T> failure(Code code, T result) {
        return new DataResponse<>(code, result);
    }

    public static <T> DataResponse<T> failure(Code code, String message, T result) {
        return new DataResponse<>(code, message, result);
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "DataResponse{" +
                "result=" + result +
                ", code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
