package com.campus.signup.security;

import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 把 {@code @CurrentUser JwtPayload} 解析成 SecurityContext 里的 principal。
 *
 * <p>参数类型写错（比如写成 SysUser）会在启动阶段就抛 {@link IllegalStateException}，
 * 而不是等到某个请求打进来才 500。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        if (!JwtPayload.class.isAssignableFrom(parameter.getParameterType())) {
            throw new IllegalStateException(
                    "@CurrentUser 只支持 JwtPayload 类型，实际为: " + parameter.getParameterType().getName());
        }
        return true;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPayload payload) {
            return payload;
        }
        // 正常情况走不到这里：能进来的请求一定已经过了 Security 认证
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
