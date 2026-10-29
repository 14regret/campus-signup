package com.campus.signup.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写操作日志的接口方法。
 *
 * <p>只标注「管理员对业务有实际影响」的动作（发布、取消、确认、拒绝、撤销），
 * 查询类接口不记——日志表的价值在于回答「谁在什么时候动了什么」，
 * 把浏览行为也记进去只会淹没有效信息。
 *
 * <pre>
 * &#64;OperationLog(action = "ACTIVITY_PUBLISH", targetType = "ACTIVITY", targetId = "#id")
 * </pre>
 *
 * <p>{@code targetId} 是 SpEL，可以用方法参数名引用入参（项目已开启 {@code -parameters} 编译）。
 * 日志写入失败只会打 warn，绝不影响主业务。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 动作编码，如 ACTIVITY_PUBLISH / SIGNUP_CONFIRM */
    String action();

    /** 目标类型，如 ACTIVITY / SIGNUP */
    String targetType();

    /** 目标 ID 的 SpEL 表达式，如 {@code "#id"}；留空则不记录 */
    String targetId() default "";
}
