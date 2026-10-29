package com.campus.signup.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 定时任务的多实例互斥（ShedLock）。
 *
 * <p>问题：{@code @Scheduled} 在每个实例上都会触发。部署两台机器，对账任务就会跑两遍，
 * 修复计数时可能出现「两个节点同时改同一行」。
 *
 * <p>为什么不用自己写的 {@code RedisDistributedLock}：那是业务锁，会自旋等待、失败抛异常，
 * 适合「必须拿到锁才能继续」的场景。定时任务需要的是「拿不到就安静跳过」，
 * 并且要记录上次执行时间、支持 lockAtLeastFor（防止时钟漂移导致同一任务在临界点重复执行），
 * 这些正是 ShedLock 的语义。
 *
 * <p>锁记录存在 Redis 的 {@code job-lock:*} 键里，TTL 由注解上的 lockAtMostFor 决定。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT20M")
public class SchedulingConfig {

    private static final String LOCK_ENVIRONMENT = "campus-signup";

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, LOCK_ENVIRONMENT);
    }
}
