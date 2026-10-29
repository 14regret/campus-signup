package com.campus.signup.security;

import com.campus.signup.config.JwtProperties;
import com.campus.signup.entity.enums.Role;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析（JJWT 0.12.x API）。
 *
 * <p>载荷约定：
 * <ul>
 *   <li>{@code sub}  —— 用户名</li>
 *   <li>{@code uid}  —— 用户主键，避免每次鉴权都按用户名回查</li>
 *   <li>{@code role} —— {@link Role} 名称，权限模型靠它区分 ADMIN / USER</li>
 *   <li>{@code iss} / {@code iat} / {@code exp} —— 签发者与时间</li>
 * </ul>
 *
 * <p>0.12.x 与网上大量 0.9/0.11 的写法不兼容：签名用 {@code signWith(key, Jwts.SIG.HS256)}，
 * 解析用 {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 与 application.yml 中的兜底值保持一致，仅用于启动告警判断。 */
    private static final String DEV_FALLBACK_SECRET = "dev-only-please-override-me-0123456789abcdef";

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";

    /** HS256 要求密钥至少 256 位。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration validity;
    private final String issuer;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥太短：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节，当前 " + secretBytes.length + " 字节");
        }
        if (properties.validity() == null || properties.validity().isZero() || properties.validity().isNegative()) {
            throw new IllegalStateException("app.jwt.validity 必须是正数，当前值: " + properties.validity());
        }
        if (DEV_FALLBACK_SECRET.equals(properties.secret())) {
            log.warn("检测到正在使用 application.yml 中的开发默认 JWT 密钥，上线前请通过环境变量 JWT_SECRET 覆盖");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.validity = properties.validity();
        this.issuer = properties.issuer();
    }

    /**
     * 签发令牌。
     *
     * @param userId   用户主键
     * @param username 用户名，写进 sub
     * @param role     角色，写进 role claim
     */
    public String generateToken(Long userId, String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验令牌（签名、issuer、过期时间）。
     *
     * @throws BusinessException TOKEN_EXPIRED / TOKEN_INVALID
     */
    public JwtPayload parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌为空");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 数字 claim 会被反序列化成 Integer 或 Long，取决于大小，不能直接 get("uid", Long.class)
            Object rawUserId = claims.get(CLAIM_USER_ID);
            if (rawUserId == null) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌缺少用户标识");
            }
            Long userId = Long.valueOf(rawUserId.toString());

            String roleName = claims.get(CLAIM_ROLE, String.class);
            Role role = parseRole(roleName);

            return new JwtPayload(
                    userId,
                    claims.getSubject(),
                    role,
                    toInstant(claims.getIssuedAt()),
                    toInstant(claims.getExpiration()));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("令牌解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** 有效期秒数，登录接口直接回给前端用来提前续期。 */
    public long getValiditySeconds() {
        return validity.toSeconds();
    }

    private static Role parseRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌缺少角色信息");
        }
        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌角色非法: " + roleName);
        }
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
