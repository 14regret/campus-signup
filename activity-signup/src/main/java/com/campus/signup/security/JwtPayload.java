package com.campus.signup.security;

import com.campus.signup.entity.enums.Role;

import java.time.Instant;

/**
 * 解析出来的令牌内容。
 *
 * <p>{@code role} 来自签发时写入的 claim，是「这个 token 声称自己是什么角色」，
 * 不是「数据库里现在是什么角色」。用户被降级或禁用后，旧 token 在过期前依然有效——
 * 这是无状态 JWT 的固有代价，第三步做权限校验时会用启用状态再兜一层。
 */
public record JwtPayload(
        Long userId,
        String username,
        Role role,
        Instant issuedAt,
        Instant expiresAt
) {
}
