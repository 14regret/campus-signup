package com.campus.signup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>项目只引了 {@code spring-security-crypto}，没有引 {@code spring-boot-starter-security}——
 * 后者会自动装配一整套 FilterChain，把所有接口默认拦成 401，而当前阶段只需要 BCrypt。
 * 等第三步做接口鉴权时再决定是补 starter 还是自己写拦截器。
 *
 * <p>BCrypt 强度保持默认 10：调到 12 以上单次登录要 200ms+，压测时会变成瓶颈。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
