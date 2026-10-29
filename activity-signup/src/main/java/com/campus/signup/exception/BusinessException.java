package com.campus.signup.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。只用于「可以预期、需要告诉用户原因」的失败，例如用户名重复、密码错误。
 *
 * <p>不要用它包裹编程错误（空指针、SQL 语法错）——那些走未知异常分支，统一记 error 日志并返回 500，
 * 免得把内部细节暴露给前端。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认文案，例如把「参数校验失败」换成具体到字段的提示。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
