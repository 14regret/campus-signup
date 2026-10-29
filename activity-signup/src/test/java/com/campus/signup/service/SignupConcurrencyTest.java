package com.campus.signup.service;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实并发抢名额测试：100 个线程同时抢 10 个名额。
 *
 * <p>需要真实的 MySQL + Redis，所以默认<b>不执行</b>，避免 {@code mvn test} 在没有中间件的机器上失败。
 * 显式开启：
 * <pre>
 * $env:RUN_CONCURRENCY_TEST='true'
 * $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'
 * .\mvnw.cmd test -Dtest=SignupConcurrencyTest
 * </pre>
 *
 * <p>断言的是「不变量」而不是实现细节：成功数必须恰好等于名额数，计数与明细必须一致。
 * 只要这三个数对得上，就说明没有超卖、没有漏减、没有重复报名。
 */
@SpringBootTest(properties = {
        // 测试里把等锁时间放大，避免机器慢导致 request 因等锁超时失败，影响计数断言
        "app.lock.wait-time=60s",
        "app.lock.lease-time=60s"
})
@EnabledIfEnvironmentVariable(named = "RUN_CONCURRENCY_TEST", matches = "true")
class SignupConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int REQUESTS = 100;

    @Autowired
    private SignupService signupService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SignupRepository signupRepository;

    private Long activityId;

    @AfterEach
    void cleanUp() {
        if (activityId != null) {
            signupRepository.deleteByActivityId(activityId);
            activityRepository.deleteById(activityId);
            activityId = null;
        }
    }

    @Test
    @DisplayName("100 个并发请求抢 10 个名额：恰好 10 个成功，不超卖")
    void exactlyCapacityRequestsSucceed() throws Exception {
        Long targetId = createPublishedActivity();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        AtomicInteger busy = new AtomicInteger();
        List<String> unexpected = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(REQUESTS);

        for (int i = 1; i <= REQUESTS; i++) {
            long userId = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    signupService.signUp(targetId, userId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ACTIVITY_FULL) {
                        full.incrementAndGet();
                    } else if (e.getErrorCode() == ErrorCode.ACTIVITY_BUSY) {
                        busy.incrementAndGet();
                    } else {
                        synchronized (unexpected) {
                            unexpected.add(e.getErrorCode() + ":" + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    synchronized (unexpected) {
                        unexpected.add(e.getClass().getSimpleName() + ":" + e.getMessage());
                    }
                }
                return null;
            }));
        }

        // 所有线程就位后同时放行，尽量制造真实的并发竞争
        boolean allReady = ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        System.out.printf("并发结果: 成功=%d 名额已满=%d 等锁超时=%d 异常=%d%n",
                success.get(), full.get(), busy.get(), unexpected.size());

        // 1. 成功数恰好等于名额数
        assertThat(allReady).isTrue();
        assertThat(unexpected).as("不应出现意料之外的异常: %s", unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(CAPACITY);

        // 2. 计数没有被超卖
        Activity reloaded = activityRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getSignedUpCount()).isEqualTo(CAPACITY);

        // 3. 明细与计数一致
        List<Signup> occupying = signupRepository.findByActivityIdAndStatusIn(
                targetId, List.of(SignupStatus.PENDING, SignupStatus.CONFIRMED));
        assertThat(occupying).hasSize(CAPACITY);
        assertThat(occupying).extracting(Signup::getUserId).doesNotHaveDuplicates();
    }

    private Long createPublishedActivity() {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = new Activity();
        activity.setTitle("[CONCURRENCY] 抢名额测试");
        activity.setDescription("并发测试专用数据，跑完自动删除");
        activity.setCapacity(CAPACITY);
        activity.setSignedUpCount(0);
        activity.setStatus(ActivityStatus.PUBLISHED);
        // 报名窗口覆盖当前时刻
        activity.setSignupStartAt(now.minusHours(1));
        activity.setSignupEndAt(now.plusHours(1));
        activity.setStartAt(now.plusDays(1));
        activity.setEndAt(now.plusDays(1).plusHours(2));
        activity.setCreatedBy(1L);

        Activity saved = activityRepository.saveAndFlush(activity);
        activityId = saved.getId();
        return activityId;
    }
}
