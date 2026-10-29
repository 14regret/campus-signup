package com.campus.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 分布式锁参数，对应 {@code app.lock.*}。
 *
 * @param waitTime  等锁的最长时间，超时直接返回「人数较多请稍后重试」，让用户重试而不是无限等
 * @param leaseTime 锁的自动过期时间。**不是**正确性依赖，只是兜底：
 *                  持锁线程崩溃、或卡在慢 SQL 上超过这个时间，锁会自动释放，避免整个活动被永久锁死。
 *                  真正的正确性由数据库的条件 UPDATE 保证。
 */
@ConfigurationProperties(prefix = "app.lock")
public record LockProperties(
        @DefaultValue("3s") Duration waitTime,
        @DefaultValue("10s") Duration leaseTime
) {
}
