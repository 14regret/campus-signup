package com.campus.signup.job;

import com.campus.signup.config.JobProperties;
import com.campus.signup.entity.Activity;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动状态推进：把「已过结束时间但还停在 PUBLISHED」的活动推到 FINISHED。
 *
 * <h2>为什么按 endAt 扫而不是按 signupEndAt 扫</h2>
 * 本项目的状态机只持久化 4 个业务态（DRAFT / PUBLISHED / CANCELLED / FINISHED），
 * 「报名已截止」不是一个状态，而是 {@code signupOpen} 这个由时间推导出来的展示属性：
 * 过了 signupEndAt 之后 {@code Activity#isSignupOpen} 自动返回 false，
 * 报名链路也会被 {@code assertSignupOpen} 挡掉。
 * 如果为此加一个 SIGNUP_CLOSED 状态，就会重新引入「状态和时间两个真相」的问题
 * （定时任务晚跑一分钟，用户就能在截止后报名）。
 *
 * <p>所以这里的职责只有一件事：活动真正结束后落到终态。
 * 晚跑几分钟没有副作用——查询侧本来就在按时间判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusJob {

    private final ActivityRepository activityRepository;
    private final TransactionOperations transactionOperations;
    private final JobProperties jobProperties;
    private final Clock clock;

    /**
     * 默认每 5 分钟跑一次。
     *
     * <p>进度锁语义：多个实例只有一个执行；lockAtMostFor 覆盖单次执行的最长耗时（防止节点崩溃后锁不释放），
     * lockAtLeastFor 防止时钟漂移导致两个节点在临界点都认为自己该跑。
     */
    @Scheduled(cron = "${app.job.activity-status-cron:0 */5 * * * *}")
    @SchedulerLock(name = "finishEndedActivities", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void finishEndedActivities() {
        LocalDateTime now = LocalDateTime.now(clock);
        Pageable pageable = PageRequest.of(0, jobProperties.batchSize(), Sort.by(Sort.Direction.ASC, "id"));

        List<Activity> candidates = activityRepository
                .findByStatusAndEndAtBeforeAndDeletedFalse(ActivityStatus.PUBLISHED, now, pageable)
                .getContent();
        if (candidates.isEmpty()) {
            return;
        }

        int finished = 0;
        for (Activity candidate : candidates) {
            try {
                // 每个活动一个独立事务：一条失败不能把整批回滚掉
                Boolean done = transactionOperations.execute(status -> finishOne(candidate.getId()));
                if (Boolean.TRUE.equals(done)) {
                    finished++;
                }
            } catch (Exception e) {
                log.error("推进活动状态失败 activityId={}", candidate.getId(), e);
            }
        }
        log.info("活动状态推进：候选 {} 个，实际结束 {} 个", candidates.size(), finished);
    }

    private Boolean finishOne(Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null || activity.isDeleted()) {
            return false;
        }
        // 走状态机：已经是 FINISHED/CANCELLED 的不会被覆盖。
        // 并发场景下（比如管理员刚取消）实体上的 @Version 会抛出乐观锁异常，
        // 由调用方 catch 住跳过，CANCELLED 不会被改成 FINISHED。
        if (!activity.getStatus().canTransitTo(ActivityStatus.FINISHED)) {
            return false;
        }
        activity.setStatus(ActivityStatus.FINISHED);
        activityRepository.saveAndFlush(activity);
        log.debug("活动到达结束时间，置为 FINISHED activityId={}", activityId);
        return true;
    }
}
