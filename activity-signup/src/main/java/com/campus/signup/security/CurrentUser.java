package com.campus.signup.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户。
 *
 * <p>只支持 {@link JwtPayload} 类型的参数：
 * <pre>
 * &#64;GetMapping("/api/activities/mine")
 * public Result&lt;...&gt; mine(&#64;CurrentUser JwtPayload user) { ... }
 * </pre>
 *
 * <p>注意它拿到的是 <b>token 里的快照</b>（userId / username / role），不是数据库里的最新用户。
 * 需要最新的昵称、启用状态时，请用 {@code user.userId()} 再查一次库。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
