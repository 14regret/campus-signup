package com.campus.signup.job;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import com.campus.signup.service.SignupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三个定时任务对真实 MySQL + Redis 的集成测试。
 *
 * <p>默认不执行（需要中间件），显式开启：
 * <pre>
 * $env:RUN_DB_TEST='true'; $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'
 * .\mvnw.cmd test -Dtest=JobIntegrationTest
 * </pre>
 *
 * <p>cron 全部改成 1 月 1 日零点，保证测试期间调度器不会自己触发，只有测试代码显式调用。
 */
@SpringBootTest(properties = {
        "app.job.pending-timeout=1s",
        "app.job.activity-status-cron=0 0 0 1 1 *",
        "app.job.signup-timeout-cron=0 0 0 1 1 *",
        "app.job.reconcile-cron=0 0 0 1 1 *"
})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TEST", matches = "true")
class JobIntegrationTest {

    @Autowired
    private ActivityStatusJob activityStatusJob;

    @Autowired
    private SignupTimeoutJob signupTimeoutJob;

    @Autowired
    private ActivityReconcileJob activityReconcileJob;

    @Autowired
    private SignupService signupService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SignupRepository signupRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long activityId;

    /**
     * ShedLock 的 lockAtLeastFor 会让同一个任务在 30s/1min 内不再执行。
     * 测试里反复调用同一个 job 时必须先清掉锁记录，否则第二次调用会被静默跳过。
     */
    @BeforeEach
    void clearSchedulerLocks() {
        for (String pattern : Set.of("*finishEndedActivities*", "*cancelTimeoutSignups*", "*reconcileSignedUpCount*")) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    @AfterEach
    void cleanUp() {
        if (activityId != null) {
            signupRepository.deleteByActivityId(activityId);
            activityRepository.deleteById(activityId);
            activityId = null;
        }
    }

    @Test
    @DisplayName("活动已过结束时间：任务把 PUBLISHED 推到 FINISHED")
    void finishesEndedActivity() {
        LocalDateTime now = LocalDateTime.now();
        activityId = saveActivity(ActivityStatus.PUBLISHED,
                now.minusDays(5), now.minusDays(4), now.minusDays(2), now.minusDays(1));

        activityStatusJob.finishEndedActivities();

        assertThat(activityRepository.findById(activityId).orElseThrow().getStatus())
                .isEqualTo(ActivityStatus.FINISHED);
    }

    @Test
    @DisplayName("未到结束时间的活动不会被误关")
    void doesNotFinishUpcomingActivity() {
        LocalDateTime now = LocalDateTime.now();
        activityId = saveActivity(ActivityStatus.PUBLISHED,
                now.minusHours(2), now.plusHours(1), now.plusDays(1), now.plusDays(2));

        activityStatusJob.finishEndedActivities();

        assertThat(activityRepository.findById(activityId).orElseThrow().getStatus())
                .isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    @DisplayName("PENDING 超时：自动改为 CANCELLED 并释放名额")
    void cancelsTimeoutPendingSignup() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        activityId = saveActivity(ActivityStatus.PUBLISHED,
                now.minusHours(2), now.plusHours(1), now.plusDays(1), now.plusDays(2));

        var response = signupService.signUp(activityId, 999L);
        assertThat(response.status()).isEqualTo(SignupStatus.PENDING);
        assertThat(activityRepository.findById(activityId).orElseThrow().getSignedUpCount()).isEqualTo(1);

        // pending-timeout 被设成 1s，等它过期
        Thread.sleep(1500);
        signupTimeoutJob.cancelTimeoutPendingSignups();

        Signup signup = signupRepository.findById(response.signupId()).orElseThrow();
        assertThat(signup.getStatus()).isEqualTo(SignupStatus.CANCELLED);
        assertThat(activityRepository.findById(activityId).orElseThrow().getSignedUpCount())
                .as("超时取消必须把名额退回去")
                .isZero();
    }

    @Test
    @DisplayName("计数漂移：对账任务按明细把 signed_up_count 修回正确值")
    void reconcilesDriftedCount() {
        LocalDateTime now = LocalDateTime.now();
        activityId = saveActivity(ActivityStatus.PUBLISHED,
                now.minusHours(2), now.plusHours(1), now.plusDays(1), now.plusDays(2));

        signupService.signUp(activityId, 998L);

        // 人为制造漂移：直接改库，绕开业务逻辑
        Activity activity = activityRepository.findById(activityId).orElseThrow();
        activity.setSignedUpCount(7);
        activityRepository.saveAndFlush(activity);

        activityReconcileJob.reconcileSignedUpCount();

        assertThat(activityRepository.findById(activityId).orElseThrow().getSignedUpCount())
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private Long saveActivity(ActivityStatus status,
                              LocalDateTime signupStartAt,
                              LocalDateTime signupEndAt,
                              LocalDateTime startAt,
                              LocalDateTime endAt) {
        Activity activity = new Activity();
        activity.setTitle("[JOB-TEST] 定时任务测试");
        activity.setDescription("跑完自动删除");
        activity.setCapacity(100);
        activity.setSignedUpCount(0);
        activity.setStatus(status);
        activity.setSignupStartAt(signupStartAt);
        activity.setSignupEndAt(signupEndAt);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setCreatedBy(1L);
        return activityRepository.saveAndFlush(activity).getId();
    }
}
