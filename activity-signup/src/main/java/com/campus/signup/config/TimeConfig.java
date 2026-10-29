package com.campus.signup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 统一的时间源。
 *
 * <p>业务代码一律用 {@code LocalDateTime.now(clock)}，不要直接 {@code LocalDateTime.now()}：
 * 单测里注入 {@code Clock.fixed(...)} 就能把「报名开始前 1 秒」「截止那一刻」这类边界
 * 精确构造出来，不用 sleep，也不会出现跨零点跑的假失败。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
