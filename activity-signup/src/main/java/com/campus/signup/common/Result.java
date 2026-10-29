package com.campus.signup.common;

import com.campus.signup.exception.ErrorCode;

/**
 * 统一返回体。
 *
 * <p>约定：{@code code == 0} 表示成功，其余取 {@link ErrorCode} 里的业务码。
 * HTTP 状态码由 {@code GlobalExceptionHandler} 同步设置，前端既能看 HTTP 状态也能看业务码。
 *
 * <pre>
 * { "code": 0, "message": "ok", "data": { ... } }
 * { "code": 2001, "message": "用户名已存在", "data": null }
 * </pre>
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 需要覆盖默认文案时用（例如参数校验的具体字段错误）。 */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }
}
