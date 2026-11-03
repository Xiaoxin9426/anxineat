package com.anxineat.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构。
 *
 * <p>需求文档约定：{@code { code, message, data }}。
 * code = 0 表示成功，非 0 为业务错误码，前端按 code 做语义化提示。
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(ResultCode.BIZ_ERROR.getCode(), message);
    }
}
