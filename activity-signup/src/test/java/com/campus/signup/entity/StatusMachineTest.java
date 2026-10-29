package com.campus.signup.entity;

import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态机与实体领域行为的单元测试。不启动 Spring 上下文，不依赖 MySQL / Redis，
 * 所以 {@code .\mvnw.cmd test} 在任何机器上都能跑通。
 */
class StatusMachineTest {

    // ------------------------------------------------------------------
    // ActivityStatus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("活动状态：只允许 DRAFT->PUBLISHED/CANCELLED、PUBLISHED->CANCELLED/FINISHED")
    void activityStatus_allowedTransitions() {
        assertThat(ActivityStatus.DRAFT.canTransitTo(ActivityStatus.PUBLISHED)).isTrue();
        assertThat(ActivityStatus.DRAFT.canTransitTo(ActivityStatus.CANCELLED)).isTrue();
        assertThat(ActivityStatus.PUBLISHED.canTransitTo(ActivityStatus.CANCELLED)).isTrue();
        assertThat(ActivityStatus.PUBLISHED.canTransitTo(ActivityStatus.FINISHED)).isTrue();
    }

    @Test
    @DisplayName("活动状态：终态不可再流转，且不允许流转到自身")
    void activityStatus_forbiddenTransitions() {
        // 终态
        assertThat(ActivityStatus.FINISHED.isTerminal()).isTrue();
        assertThat(ActivityStatus.CANCELLED.isTerminal()).isTrue();
        for (ActivityStatus target : ActivityStatus.values()) {
            assertThat(ActivityStatus.FINISHED.canTransitTo(target)).isFalse();
            assertThat(ActivityStatus.CANCELLED.canTransitTo(target)).isFalse();
        }

        // 跨级 / 反向
        assertThat(ActivityStatus.DRAFT.canTransitTo(ActivityStatus.FINISHED)).isFalse();
        assertThat(ActivityStatus.PUBLISHED.canTransitTo(ActivityStatus.DRAFT)).isFalse();

        // 自身
        for (ActivityStatus status : ActivityStatus.values()) {
            assertThat(status.canTransitTo(status)).isFalse();
            assertThat(status.canTransitTo(null)).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // SignupStatus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("报名状态：PENDING 可确认/取消/拒绝，CONFIRMED 只能取消")
    void signupStatus_allowedTransitions() {
        assertThat(SignupStatus.PENDING.canTransitTo(SignupStatus.CONFIRMED)).isTrue();
        assertThat(SignupStatus.PENDING.canTransitTo(SignupStatus.CANCELLED)).isTrue();
        assertThat(SignupStatus.PENDING.canTransitTo(SignupStatus.REJECTED)).isTrue();
        assertThat(SignupStatus.CONFIRMED.canTransitTo(SignupStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("报名状态：CANCELLED / REJECTED 是可复活态，只能回到 PENDING")
    void signupStatus_revivable() {
        assertThat(SignupStatus.CANCELLED.canTransitTo(SignupStatus.PENDING)).isTrue();
        assertThat(SignupStatus.REJECTED.canTransitTo(SignupStatus.PENDING)).isTrue();
        assertThat(SignupStatus.CANCELLED.isRevivable()).isTrue();
        assertThat(SignupStatus.REJECTED.isRevivable()).isTrue();
        assertThat(SignupStatus.PENDING.isRevivable()).isFalse();
        assertThat(SignupStatus.CONFIRMED.isRevivable()).isFalse();

        // 已确认的不能直接改回待确认，也不能从取消跳到已确认
        assertThat(SignupStatus.CONFIRMED.canTransitTo(SignupStatus.PENDING)).isFalse();
        assertThat(SignupStatus.CANCELLED.canTransitTo(SignupStatus.CONFIRMED)).isFalse();
        assertThat(SignupStatus.REJECTED.canTransitTo(SignupStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("报名状态：PENDING 即占名额，取消/拒绝后释放")
    void signupStatus_quotaSemantics() {
        Set<SignupStatus> occupying = EnumSet.noneOf(SignupStatus.class);
        for (SignupStatus status : SignupStatus.values()) {
            if (status.occupiesQuota()) {
                occupying.add(status);
            }
        }
        assertThat(occupying).containsExactlyInAnyOrder(SignupStatus.PENDING, SignupStatus.CONFIRMED);
    }

    @Test
    @DisplayName("报名状态：self-transition 与 null 一律拒绝")
    void signupStatus_selfAndNull() {
        for (SignupStatus status : SignupStatus.values()) {
            assertThat(status.canTransitTo(status)).isFalse();
            assertThat(status.canTransitTo(null)).isFalse();
        }
    }

    @Test
    @DisplayName("Signup.transitionTo 走通合法流转，非法流转抛 IllegalStateException")
    void signupEntity_transitionGuard() {
        Signup signup = new Signup();
        signup.setStatus(SignupStatus.PENDING);

        signup.transitionTo(SignupStatus.CONFIRMED);
        assertThat(signup.getStatus()).isEqualTo(SignupStatus.CONFIRMED);

        signup.transitionTo(SignupStatus.CANCELLED);
        assertThat(signup.getStatus()).isEqualTo(SignupStatus.CANCELLED);

        // 复活：取消后重新报名
        signup.transitionTo(SignupStatus.PENDING);
        assertThat(signup.getStatus()).isEqualTo(SignupStatus.PENDING);

        // 非法一：流转到自身（幂等请求应由 service 短路，不该让状态机静默吞掉）
        assertThatThrownBy(() -> signup.transitionTo(SignupStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING -> PENDING");

        // 非法二：已取消的记录不能直接确认，必须先重新报名回到 PENDING
        Signup cancelled = new Signup();
        cancelled.setStatus(SignupStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.transitionTo(SignupStatus.CONFIRMED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED -> CONFIRMED");
    }

    // ------------------------------------------------------------------
    // Activity 领域方法
    // ------------------------------------------------------------------

    private Activity publishedActivity(LocalDateTime signupStart, LocalDateTime signupEnd, int capacity) {
        Activity activity = new Activity();
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setCapacity(capacity);
        activity.setSignedUpCount(0);
        activity.setSignupStartAt(signupStart);
        activity.setSignupEndAt(signupEnd);
        activity.setStartAt(signupEnd.plusDays(1));
        activity.setEndAt(signupEnd.plusDays(2));
        return activity;
    }

    @Test
    @DisplayName("报名窗口左闭右开：开始时刻可报，截止时刻不可报")
    void activity_signupWindowBoundary() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 5, 18, 0);
        Activity activity = publishedActivity(start, end, 50);

        assertThat(activity.isSignupOpen(start)).isTrue();
        assertThat(activity.isSignupOpen(start.plusSeconds(1))).isTrue();
        assertThat(activity.isSignupOpen(end.minusSeconds(1))).isTrue();

        assertThat(activity.isSignupOpen(start.minusSeconds(1))).isFalse();
        assertThat(activity.isSignupOpen(end)).isFalse();
        assertThat(activity.isSignupOpen(end.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("未发布的活动无论时间都不算报名开放")
    void activity_notPublished() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 5, 18, 0);
        Activity activity = publishedActivity(start, end, 50);

        activity.setStatus(ActivityStatus.DRAFT);
        assertThat(activity.isSignupOpen(start.plusSeconds(1))).isFalse();

        activity.setStatus(ActivityStatus.FINISHED);
        assertThat(activity.isSignupOpen(start.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("名额判定与剩余名额不会出现负数")
    void activity_quota() {
        Activity activity = publishedActivity(
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 5, 18, 0),
                5);

        activity.setSignedUpCount(4);
        assertThat(activity.isFull()).isFalse();
        assertThat(activity.remainingQuota()).isEqualTo(1);

        activity.setSignedUpCount(5);
        assertThat(activity.isFull()).isTrue();
        assertThat(activity.remainingQuota()).isZero();

        // 防御性：脏数据导致计数超过名额时，剩余名额不返回负数
        activity.setSignedUpCount(6);
        assertThat(activity.remainingQuota()).isZero();
    }
}
