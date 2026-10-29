package com.campus.signup.exception;

import com.campus.signup.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理。目标只有一个：不管底层抛什么，前端拿到的永远是同一个 {@code Result} 结构。
 *
 * <p>HTTP 状态码跟着 {@link ErrorCode} 走：业务失败用 4xx，真正的服务端问题才用 500。
 * 未知异常一律记 error 日志 + 返回通用文案，绝不把堆栈或 SQL 片段回给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // 业务异常：可预期，warn 级别即可
    // ------------------------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常 uri={} code={} message={}", request.getRequestURI(), errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.error(errorCode, ex.getMessage()));
    }

    // ------------------------------------------------------------------
    // 参数校验：三种来源分别处理
    //   1) @RequestBody + @Valid        -> MethodArgumentNotValidException
    //   2) 方法参数上的约束（@Validated） -> HandlerMethodValidationException（Spring 6.1+）或 ConstraintViolationException
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .sorted((a, b) -> a.getField().compareTo(b.getField()))
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 uri={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                     HttpServletRequest request) {
        String detail = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 uri={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                 HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .sorted()
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 uri={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("请求体无法解析 uri={} message={}", request.getRequestURI(), ex.getMessage());
        return badRequest("请求体格式错误或缺少必要字段");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex,
                                                               HttpServletRequest request) {
        log.warn("缺少请求参数 uri={} name={}", request.getRequestURI(), ex.getParameterName());
        return badRequest("缺少参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                          HttpServletRequest request) {
        log.warn("参数类型不匹配 uri={} name={}", request.getRequestURI(), ex.getName());
        return badRequest("参数类型不正确: " + ex.getName());
    }

    // ------------------------------------------------------------------
    // 路由层：404 / 405 不该掉进 500
    // ------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在 uri={}", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest request) {
        log.warn("请求方法不支持 uri={} method={}", request.getRequestURI(), ex.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // ------------------------------------------------------------------
    // 兜底：这里出现的基本都是 bug，必须留下完整堆栈
    // ------------------------------------------------------------------

    /**
     * 方法级鉴权（@PreAuthorize）抛出的拒绝异常。
     *
     * <p>必须显式接住：@RestControllerAdvice 位于 DispatcherServlet 内部，优先级高于
     * Security 的 ExceptionTranslationFilter。不加这个分支，越权访问会被下面的 Exception
     * 兜底成 500，而不是 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("权限不足 uri={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(Result.error(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("认证失败 uri={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(Result.error(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        log.warn("乐观锁冲突 uri={}", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.ACTIVITY_CONCURRENT_MODIFIED.getHttpStatus())
                .body(Result.error(ErrorCode.ACTIVITY_CONCURRENT_MODIFIED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 uri={} method={}", request.getRequestURI(), request.getMethod(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.error(ErrorCode.INTERNAL_ERROR));
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Result<Void>> badRequest(String detail) {
        String message = (detail == null || detail.isBlank())
                ? ErrorCode.PARAM_INVALID.getMessage()
                : detail;
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(Result.error(ErrorCode.PARAM_INVALID, message));
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        String field = dot >= 0 ? path.substring(dot + 1) : path;
        return field + ": " + violation.getMessage();
    }
}
