package com.campus.signup.service;

import com.campus.signup.dto.signup.SignupResponse;
import com.campus.signup.entity.Activity;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import com.campus.signup.lock.RedisDistributedLock;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名链路的服务层单测：不碰真实 Redis / MySQL，只验证分支与调用顺序。
 * 真正的并发正确性由 {@code SignupConcurrencyTest} 打真实库验证。
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 9, 0);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SignupRepository signupRepository;

    @Mock
    private RedisDistributedLock distributedLock;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-01T01:00:00Z"), ZONE);
        // 事务模板在单测里直接同步执行回调，不真的开事务
        TransactionOperations inlineTx = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        signupService = new SignupService(activityRepository, signupRepository,
                distributedLock, inlineTx, fixedClock);

        lenient().when(distributedLock.lock(anyString()))
                .thenReturn(new RedisDistributedLock.LockHandle("lock:activity:1", "token-1"));
    }

    private static Activity publishedActivity(int capacity, int signedUp) {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("校园歌手大赛");
        activity.setCapacity(capacity);
        activity.setSignedUpCount(signedUp);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setSignupStartAt(NOW.minusHours(1));
        activity.setSignupEndAt(NOW.plusHours(1));
        activity.setStartAt(NOW.plusDays(1));
        activity.setEndAt(NOW.plusDays(1).plusHours(2));
        activity.setCreatedBy(1L);
        return activity;
    }

    // ------------------------------------------------------------------
    // 报名
    // ------------------------------------------------------------------

    @Test
    @DisplayName("报名成功：先条件扣减，再插入记录，最后释放锁")
    void signUp_success() {
        Activity activity = publishedActivity(10, 0);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(signupRepository.findByActivityIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(activityRepository.incrementSignedUpCount(1L, ActivityStatus.PUBLISHED)).thenReturn(1);
        when(signupRepository.saveAndFlush(any(Signup.class))).thenAnswer(invocation -> {
            Signup signup = invocation.getArgument(0);
            signup.setId(100L);
            return signup;
        });

        SignupResponse response = signupService.signUp(1L, 7L);

        assertThat(response.signupId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(SignupStatus.PENDING);
        verify(activityRepository).incrementSignedUpCount(1L, ActivityStatus.PUBLISHED);
        verify(distributedLock).unlock(any(RedisDistributedLock.LockHandle.class));
    }

    @Test
    @DisplayName("名额已满：条件扣减返回 0，抛 ACTIVITY_FULL 且不插入记录")
    void signUp_full() {
        Activity activity = publishedActivity(10, 10);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(signupRepository.findByActivityIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(activityRepository.incrementSignedUpCount(1L, ActivityStatus.PUBLISHED)).thenReturn(0);

        assertThatThrownBy(() -> signupService.signUp(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_FULL);

        verify(signupRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("重复报名：已有 PENDING 记录时直接拒绝，不会去扣名额")
    void signUp_alreadySignedUp() {
        Activity activity = publishedActivity(10, 1);
        Signup existing = new Signup();
        existing.setId(50L);
        existing.setActivityId(1L);
        existing.setUserId(7L);
        existing.setStatus(SignupStatus.PENDING);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(signupRepository.findByActivityIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> signupService.signUp(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_SIGNED_UP);

        verify(activityRepository, never()).incrementSignedUpCount(anyLong(), any());
    }

    @Test
    @DisplayName("已取消后重新报名：走状态复活，不新增记录")
    void signUp_reviveCancelledRecord() {
        Activity activity = publishedActivity(10, 0);
        Signup cancelled = new Signup();
        cancelled.setId(50L);
        cancelled.setActivityId(1L);
        cancelled.setUserId(7L);
        cancelled.setStatus(SignupStatus.CANCELLED);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(signupRepository.findByActivityIdAndUserId(1L, 7L)).thenReturn(Optional.of(cancelled));
        when(activityRepository.incrementSignedUpCount(1L, ActivityStatus.PUBLISHED)).thenReturn(1);
        when(signupRepository.changeStatus(eq(50L), eq(SignupStatus.CANCELLED), eq(SignupStatus.PENDING), any()))
                .thenReturn(1);

        Signup revived = new Signup();
        revived.setId(50L);
        revived.setActivityId(1L);
        revived.setUserId(7L);
        revived.setStatus(SignupStatus.PENDING);
        when(signupRepository.findById(50L)).thenReturn(Optional.of(revived));

        SignupResponse response = signupService.signUp(1L, 7L);

        assertThat(response.signupId()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo(SignupStatus.PENDING);
        verify(signupRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("活动未发布：抛 SIGNUP_NOT_OPEN")
    void signUp_activityNotPublished() {
        Activity activity = publishedActivity(10, 0);
        activity.setStatus(ActivityStatus.DRAFT);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> signupService.signUp(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_NOT_OPEN);
    }

    @Test
    @DisplayName("报名已截止：抛 SIGNUP_NOT_OPEN，边界是右开区间")
    void signUp_afterDeadline() {
        Activity activity = publishedActivity(10, 0);
        activity.setSignupEndAt(NOW); // 截止时刻本身不可报名
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> signupService.signUp(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_NOT_OPEN);
    }

    // ------------------------------------------------------------------
    // 取消
    // ------------------------------------------------------------------

    @Test
    @DisplayName("取消报名成功：状态 CAS + 名额释放")
    void cancel_success() {
        Signup signup = new Signup();
        signup.setId(50L);
        signup.setActivityId(1L);
        signup.setUserId(7L);
        signup.setStatus(SignupStatus.CONFIRMED);

        when(signupRepository.findById(50L)).thenReturn(Optional.of(signup));
        when(signupRepository.changeStatus(eq(50L), eq(SignupStatus.CONFIRMED), eq(SignupStatus.CANCELLED), any()))
                .thenReturn(1);
        when(activityRepository.decrementSignedUpCount(1L)).thenReturn(1);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(publishedActivity(10, 0)));

        SignupResponse response = signupService.cancel(50L, 7L);

        assertThat(response.status()).isEqualTo(SignupStatus.CANCELLED);
        verify(activityRepository).decrementSignedUpCount(1L);
    }

    @Test
    @DisplayName("取消别人的报名：抛 SIGNUP_FORBIDDEN，且不加锁")
    void cancel_notOwner() {
        Signup signup = new Signup();
        signup.setId(50L);
        signup.setActivityId(1L);
        signup.setUserId(8L);
        signup.setStatus(SignupStatus.PENDING);

        when(signupRepository.findById(50L)).thenReturn(Optional.of(signup));

        assertThatThrownBy(() -> signupService.cancel(50L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_FORBIDDEN);

        verify(distributedLock, never()).lock(anyString());
    }

    @Test
    @DisplayName("重复取消：已取消的记录不能再取消")
    void cancel_alreadyCancelled() {
        Signup signup = new Signup();
        signup.setId(50L);
        signup.setActivityId(1L);
        signup.setUserId(7L);
        signup.setStatus(SignupStatus.CANCELLED);

        when(signupRepository.findById(50L)).thenReturn(Optional.of(signup));

        assertThatThrownBy(() -> signupService.cancel(50L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_STATUS_INVALID);
    }

    @Test
    @DisplayName("名额计数异常：释放返回 0 时抛错回滚，不让计数变负")
    void cancel_quotaInconsistent() {
        Signup signup = new Signup();
        signup.setId(50L);
        signup.setActivityId(1L);
        signup.setUserId(7L);
        signup.setStatus(SignupStatus.PENDING);

        when(signupRepository.findById(50L)).thenReturn(Optional.of(signup));
        when(signupRepository.changeStatus(eq(50L), eq(SignupStatus.PENDING), eq(SignupStatus.CANCELLED), any()))
                .thenReturn(1);
        when(activityRepository.decrementSignedUpCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> signupService.cancel(50L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
