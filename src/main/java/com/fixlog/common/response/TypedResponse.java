package com.fixlog.common.response;

import com.fixlog.common.code.Code;

public abstract class TypedResponse<T> extends Response {
    private T result;

    public TypedResponse() {
    }

    public TypedResponse(Code code, String message) {
        super(code, message);
    }

    public TypedResponse(Code code, String message, T result) {
        super(code, message);
        this.result = result;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "TypedResponse{" +
                "result=" + result +
                ", code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
