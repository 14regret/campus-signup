package com.campus.signup.security;

import com.campus.signup.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证时的响应（401）。
 *
 * <p><b>这个类不配就会出现「没带 token 却返回 403」</b>：Spring Security 在没配
 * AuthenticationEntryPoint 时默认用 Http403ForbiddenEntryPoint。前端据此判断要不要跳登录页，
 * 401 和 403 混用会导致「登录过期不跳转」。
 *
 * <p>如果过滤器解析 token 失败，会把具体原因放进 request attribute，这里优先用它，
 * 这样前端能区分「过期（1003）」和「伪造（1004）」。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.ATTR_JWT_ERROR);
        ErrorCode errorCode = reason instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;
        errorWriter.write(response, errorCode);
    }
}
