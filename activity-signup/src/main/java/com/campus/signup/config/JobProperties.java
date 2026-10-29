package com.campus.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 定时任务参数，对应 {@code app.job.*}。
 *
 * <p>cron 表达式没有放在这里：{@code @Scheduled} 的 cron 必须是编译期可解析的占位符，
 * 直接从 yml 取（{@code ${app.job.xxx-cron:默认值}}），不走配置类。
 *
 * @param batchSize      每批处理条数，避免一次把全表拉进内存
 * @param pendingTimeout PENDING 报名超过这个时长还没被确认，就自动取消并释放名额
 */
@ConfigurationProperties(prefix = "app.job")
public record JobProperties(
        @DefaultValue("200") int batchSize,
        @DefaultValue("24h") Duration pendingTimeout
) {
}
