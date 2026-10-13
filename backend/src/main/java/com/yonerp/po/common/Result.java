package com.yonerp.po.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    private long timestamp;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage("success");
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS;
    }
}
