package com.campus.signup.job;

import com.campus.signup.config.JobProperties;
import com.campus.signup.entity.Activity;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.lock.RedisDistributedLock;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 计数对账：按 signup 明细重算每个活动的 signed_up_count。
 *
 * <p>为什么要对账：signed_up_count 是冗余字段，正常情况下和明细同事务更新，不该漂移；
 * 但掉电、手动改库、极端并发下的异常回滚都可能留下不一致。
 * 与其相信它永远正确，不如每天凌晨重算一次，让「错的」自己收敛回「对的」。
 *
 * <p>做法分两步：先批量扫描找出「疑似漂移」的活动（一次 group by 查一批，避免 N+1），
 * 再对疑似项逐个加活动锁、在锁内重新计数、用条件 UPDATE 修正。
 * 锁内重算是关键——否则从「扫描」到「修正」之间如果有人报名，就会把新计数覆盖成旧值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityReconcileJob {

    private static final List<SignupStatus> OCCUPYING_STATUSES =
            List.of(SignupStatus.PENDING, SignupStatus.CONFIRMED);
    private static final String LOCK_KEY_PREFIX = "lock:activity:";
    /** 分页安全上限，避免数据异常时死循环。 */
    private static final int MAX_PAGES = 5000;

    private final ActivityRepository activityRepository;
    private final SignupRepository signupRepository;
    private final RedisDistributedLock distributedLock;
    private final TransactionOperations transactionOperations;
    private final JobProperties jobProperties;

    @Scheduled(cron = "${app.job.reconcile-cron:0 0 3 * * *}")
    @SchedulerLock(name = "reconcileSignedUpCount", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void reconcileSignedUpCount() {
        int pageIndex = 0;
        int scanned = 0;
        int fixed = 0;

        while (pageIndex < MAX_PAGES) {
            Page<Activity> page = activityRepository.findByDeletedFalse(
                    PageRequest.of(pageIndex, jobProperties.batchSize(), Sort.by(Sort.Direction.ASC, "id")));
            if (page.isEmpty()) {
                break;
            }

            Map<Long, Long> actualCounts = loadActualCounts(page.getContent());
            for (Activity activity : page.getContent()) {
                scanned++;
                long expected = actualCounts.getOrDefault(activity.getId(), 0L);
                if (activity.getSignedUpCount() == expected) {
                    continue;
                }
                log.warn("发现计数漂移 activityId={} 计数={} 明细={}",
                        activity.getId(), activity.getSignedUpCount(), expected);
                if (reconcileOne(activity.getId())) {
                    fixed++;
                }
            }
            pageIndex++;
        }

        log.info("计数对账完成：扫描 {} 个活动，修复 {} 个", scanned, fixed);
    }

    private Map<Long, Long> loadActualCounts(List<Activity> activities) {
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return signupRepository.countOccupyingByActivityIds(ids, OCCUPYING_STATUSES).stream()
                .collect(Collectors.toMap(
                        SignupRepository.ActivitySignupCount::getActivityId,
                        SignupRepository.ActivitySignupCount::getTotal));
    }

    /**
     * 修正单个活动。加业务锁 + 锁内重新计数，保证不会把并发中的新报名覆盖掉。
     *
     * <p>拿不到锁（有用户正在报名这个活动）就跳过，明天再修——对账不是实时业务，没必要抢。
     */
    private boolean reconcileOne(Long activityId) {
        RedisDistributedLock.LockHandle lock = null;
        try {
            lock = distributedLock.lock(LOCK_KEY_PREFIX + activityId);
            Boolean corrected = transactionOperations.execute(status -> {
                Activity activity = activityRepository.findById(activityId).orElse(null);
                if (activity == null) {
                    return false;
                }
                long actual = signupRepository.countOccupyingByActivityId(activityId, OCCUPYING_STATUSES);
                int before = activity.getSignedUpCount();
                if (before == actual) {
                    // 扫描之后已经被别的路径修正了
                    return false;
                }
                int rows = activityRepository.syncSignedUpCount(activityId, (int) actual);
                if (rows == 1) {
                    log.info("修复计数 activityId={} {} -> {}", activityId, before, actual);
                }
                return rows == 1;
            });
            return Boolean.TRUE.equals(corrected);
        } catch (Exception e) {
            log.warn("对账修正失败，跳过 activityId={} reason={}", activityId, e.getMessage());
            return false;
        } finally {
            if (lock != null) {
                distributedLock.unlock(lock);
            }
        }
    }
}
