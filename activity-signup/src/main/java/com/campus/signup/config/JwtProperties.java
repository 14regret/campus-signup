package com.campus.signup.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT 配置，对应 {@code application.yml} 里的 {@code app.jwt.*}。
 *
 * <p>密钥不写死在代码里：yml 中通过 {@code ${JWT_SECRET:...}} 读取环境变量，
 * 兜底值只用于本地开发，启动时会打 warn（见 {@code JwtTokenProvider}）。
 *
 * @param secret   HMAC-SHA256 密钥，UTF-8 编码后至少 32 字节
 * @param validity 有效期，yml 里写 {@code 2h} 这种形式，Boot 会绑成 Duration
 * @param issuer   签发者，解析时会校验，防止别的系统签的 token 混进来
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "app.jwt.secret 不能为空") String secret,
        @NotNull(message = "app.jwt.validity 不能为空") Duration validity,
        @NotBlank(message = "app.jwt.issuer 不能为空") String issuer
) {
}
