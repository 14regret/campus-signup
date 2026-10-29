package com.campus.signup.security;

import com.campus.signup.common.Result;
import com.campus.signup.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Servlet 过滤器层写 JSON 错误响应。
 *
 * <p>过滤器跑在 DispatcherServlet 之前，{@code @RestControllerAdvice} 根本管不到这里，
 * 所以 401/403 必须手写响应体，否则前端会收到 Tomcat 默认的 HTML 错误页。
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.error(errorCode));
    }
}
