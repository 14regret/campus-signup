package com.campus.signup.job;

import com.campus.signup.config.JobProperties;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.repository.SignupRepository;
import com.campus.signup.service.SignupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名超时自动取消：PENDING 超过 {@code app.job.pending-timeout}（默认 24h）仍未确认，释放名额。
 *
 * <p>业务动机：PENDING 是占名额的（见 {@code SignupStatus#occupiesQuota}），
 * 如果有人报名后一直不确认，名额就被白占着。这个任务是「占位制」的必要配套。
 *
 * <p>真正的状态变更不在这里做，而是委托给 {@code SignupService#cancelExpired}：
 * 那里会重新加活动锁、在事务里复核状态和创建时间，并保证「改状态」和「释放名额」同生共死。
 * 定时任务只负责「找候选 + 循环 + 记日志」，保持薄。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignupTimeoutJob {

    private final SignupRepository signupRepository;
    private final SignupService signupService;
    private final JobProperties jobProperties;
    private final Clock clock;

    @Scheduled(cron = "${app.job.signup-timeout-cron:0 */10 * * * *}")
    @SchedulerLock(name = "cancelTimeoutSignups", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cancelTimeoutPendingSignups() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(jobProperties.pendingTimeout());

        List<Signup> candidates = signupRepository
                .findByStatusAndCreatedAtBefore(SignupStatus.PENDING, cutoff,
                        PageRequest.of(0, jobProperties.batchSize()))
                .getContent();
        if (candidates.isEmpty()) {
            return;
        }

        int cancelled = 0;
        for (Signup candidate : candidates) {
            try {
                if (signupService.cancelExpired(candidate.getId(), cutoff)) {
                    cancelled++;
                }
            } catch (Exception e) {
                // 单条失败不影响其余记录；下一轮还会再扫到它
                log.error("超时取消报名失败 signupId={}", candidate.getId(), e);
            }
        }
        log.info("超时报名清理：候选 {} 条，取消 {} 条", candidates.size(), cancelled);
    }
}
