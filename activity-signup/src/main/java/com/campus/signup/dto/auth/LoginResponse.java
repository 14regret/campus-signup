package com.campus.signup.dto.auth;

/**
 * 登录结果。
 *
 * @param token      JWT
 * @param tokenType  固定 Bearer，前端拼 {@code Authorization: Bearer <token>}
 * @param expiresIn  有效期（秒），前端可据此提前静默续期
 * @param user       登录用户信息
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserProfile user
) {

    public static LoginResponse of(String token, long expiresIn, UserProfile user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}
