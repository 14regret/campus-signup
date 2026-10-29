package com.campus.signup.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>分段约定：{@code 0} 成功；{@code 1xxx} 通用/认证；{@code 2xxx} 用户；后续活动与报名分别占 3xxx / 4xxx，
 * 前端按号段就能判断大类，不用逐条匹配。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    PARAM_INVALID(1001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1002, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1003, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1004, "令牌无效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1005, "没有权限执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(1006, "请求的资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1007, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR(1500, "服务器开小差了，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR),

    USERNAME_EXISTS(2001, "用户名已存在", HttpStatus.CONFLICT),
    LOGIN_FAILED(2002, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(2003, "账号已被禁用", HttpStatus.FORBIDDEN),

    ACTIVITY_NOT_FOUND(3001, "活动不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_STATUS_INVALID(3002, "当前活动状态不允许该操作", HttpStatus.CONFLICT),
    ACTIVITY_NOT_EDITABLE(3003, "只有草稿状态的活动可以编辑", HttpStatus.CONFLICT),
    ACTIVITY_CAPACITY_TOO_SMALL(3004, "名额不能小于已报名人数", HttpStatus.CONFLICT),
    ACTIVITY_TIME_INVALID(3005, "时间设置不合法", HttpStatus.BAD_REQUEST),
    ACTIVITY_CONCURRENT_MODIFIED(3006, "活动已被其他人修改，请刷新后重试", HttpStatus.CONFLICT),

    ACTIVITY_FULL(4001, "名额已满", HttpStatus.CONFLICT),
    ALREADY_SIGNED_UP(4002, "你已经报名过该活动", HttpStatus.CONFLICT),
    SIGNUP_NOT_FOUND(4003, "报名记录不存在", HttpStatus.NOT_FOUND),
    SIGNUP_STATUS_INVALID(4004, "当前报名状态不允许该操作", HttpStatus.CONFLICT),
    SIGNUP_FORBIDDEN(4005, "只能操作自己的报名", HttpStatus.FORBIDDEN),
    ACTIVITY_BUSY(4006, "当前报名人数较多，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    SIGNUP_CONFLICT(4007, "报名状态已变更，请刷新后重试", HttpStatus.CONFLICT),
    SIGNUP_NOT_OPEN(4008, "报名未开始或已截止", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    /** 该错误对应的 HTTP 状态，全局异常处理器直接取这个值。 */
    private final HttpStatus httpStatus;
}
