package com.campus.signup.security;

import com.campus.signup.config.JwtProperties;
import com.campus.signup.entity.enums.Role;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与解析的单测，不启动 Spring 上下文。
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghij";
    private static final String ISSUER = "campus-signup-test";

    private static JwtTokenProvider provider() {
        return new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), ISSUER));
    }

    @Test
    @DisplayName("签发后能原样解析出 userId / username / role")
    void generateAndParse() {
        JwtTokenProvider provider = provider();
        String token = provider.generateToken(42L, "alice", Role.ADMIN);

        JwtPayload payload = provider.parseToken(token);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.username()).isEqualTo("alice");
        assertThat(payload.role()).isEqualTo(Role.ADMIN);
        assertThat(payload.issuedAt()).isNotNull();
        assertThat(payload.expiresAt()).isAfter(payload.issuedAt());
    }

    @Test
    @DisplayName("过期令牌抛出 TOKEN_EXPIRED")
    void expiredToken() throws InterruptedException {
        // JJWT 的 exp 精度是秒，1 秒有效期 + 1.1 秒等待是稳定可复现的写法
        JwtTokenProvider shortLived = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofSeconds(1), ISSUER));
        String token = shortLived.generateToken(7L, "bob", Role.USER);

        Thread.sleep(1100);

        assertThatThrownBy(() -> shortLived.parseToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("被篡改的令牌抛出 TOKEN_INVALID")
    void tamperedToken() {
        String token = provider().generateToken(1L, "bob", Role.USER);
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThatThrownBy(() -> provider().parseToken(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("其他密钥签发的令牌不通过校验")
    void tokenFromAnotherSecret() {
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties("another-secret-0123456789abcdefghijkl", Duration.ofMinutes(30), ISSUER));
        String token = other.generateToken(1L, "bob", Role.USER);

        assertThatThrownBy(() -> provider().parseToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("其他 issuer 签发的令牌不通过校验")
    void tokenFromAnotherIssuer() {
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), "someone-else"));
        String token = other.generateToken(1L, "bob", Role.USER);

        assertThatThrownBy(() -> provider().parseToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("空令牌直接拒绝")
    void blankToken() {
        assertThatThrownBy(() -> provider().parseToken("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("密钥不足 32 字节时启动即失败，而不是运行期偶发异常")
    void weakSecretRejected() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                new JwtProperties("too-short", Duration.ofMinutes(30), ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
