package com.campus.signup.security;

import com.campus.signup.entity.enums.Role;
import com.campus.signup.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 {@code Authorization: Bearer <token>} 解析 JWT 并填充 SecurityContext。
 *
 * <p>关键取舍：<b>解析失败不在这里直接返回 401</b>，而是清空上下文、把错误码挂到 request attribute
 * 后继续放行。原因有两点：
 * <ol>
 *   <li>{@code /api/auth/**} 是放行的，如果带了个坏 token 就直接被拦截，用户反而登录不了；</li>
 *   <li>真正的 401 由 {@link RestAuthenticationEntryPoint} 统一产出，响应格式只在一个地方维护。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 解析失败原因，供 AuthenticationEntryPoint 产出精确错误码。 */
    public static final String ATTR_JWT_ERROR = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtPayload payload = jwtTokenProvider.parseToken(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    payload,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + payload.role().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(ATTR_JWT_ERROR, e.getErrorCode());
        }

        filterChain.doFilter(request, response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
