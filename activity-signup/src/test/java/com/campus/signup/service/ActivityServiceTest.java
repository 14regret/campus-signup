package com.campus.signup.service;

import com.campus.signup.common.PageResult;
import com.campus.signup.dto.activity.ActivityDetail;
import com.campus.signup.dto.activity.ActivityRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动管理的服务层单测。时间用固定 Clock，边界可精确构造，不依赖 sleep。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 固定「现在」：2026-03-01 09:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 9, 0);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SignupRepository signupRepository;

    @Mock
    private RedisDistributedLock distributedLock;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-01T01:00:00Z"), ZONE);
        TransactionOperations inlineTx = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        activityService = new ActivityService(activityRepository, signupRepository,
                distributedLock, inlineTx, fixedClock);

        lenient().when(distributedLock.lock(anyString()))
                .thenReturn(new RedisDistributedLock.LockHandle("lock:activity:1", "token-1"));
    }

    private static ActivityRequest validRequest() {
        return new ActivityRequest(
                "校园歌手大赛",
                "报名后请准时参加",
                100,
                NOW.plusDays(1),   // 报名开始
                NOW.plusDays(3),   // 报名截止
                NOW.plusDays(5),   // 活动开始
                NOW.plusDays(5).plusHours(3));
    }

    private static Activity existing(ActivityStatus status) {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("校园歌手大赛");
        activity.setCapacity(100);
        activity.setSignedUpCount(0);
        activity.setStatus(status);
        activity.setCreatedBy(10L);
        activity.setSignupStartAt(NOW.plusDays(1));
        activity.setSignupEndAt(NOW.plusDays(3));
        activity.setStartAt(NOW.plusDays(5));
        activity.setEndAt(NOW.plusDays(5).plusHours(3));
        return activity;
    }

    // ------------------------------------------------------------------
    // 创建
    // ------------------------------------------------------------------

    @Test
    @DisplayName("创建活动：默认草稿、初始报名数 0、记录创建人")
    void create_defaultsToDraft() {
        when(activityRepository.saveAndFlush(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId(100L);
            return activity;
        });

        ActivityDetail detail = activityService.create(validRequest(), 10L);

        assertThat(detail.id()).isEqualTo(100L);
        assertThat(detail.status()).isEqualTo(ActivityStatus.DRAFT);
        assertThat(detail.signedUpCount()).isZero();
        assertThat(detail.remaining()).isEqualTo(100);
        assertThat(detail.createdBy()).isEqualTo(10L);
        assertThat(detail.signupOpen()).isFalse();
    }

    @Test
    @DisplayName("创建活动：报名截止早于报名开始 -> ACTIVITY_TIME_INVALID")
    void create_invalidTimeRange() {
        ActivityRequest request = new ActivityRequest(
                "错误时间", null, 10,
                NOW.plusDays(3), NOW.plusDays(1), NOW.plusDays(5), NOW.plusDays(6));

        assertThatThrownBy(() -> activityService.create(request, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_TIME_INVALID);

        verify(activityRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("创建活动：报名截止晚于活动开始 -> ACTIVITY_TIME_INVALID")
    void create_signupEndAfterActivityStart() {
        ActivityRequest request = new ActivityRequest(
                "错误时间", null, 10,
                NOW.plusDays(1), NOW.plusDays(6), NOW.plusDays(5), NOW.plusDays(7));

        assertThatThrownBy(() -> activityService.create(request, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_TIME_INVALID);
    }

    // ------------------------------------------------------------------
    // 编辑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑：已发布的活动不允许改 -> ACTIVITY_NOT_EDITABLE")
    void update_publishedIsNotEditable() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.PUBLISHED)));

        assertThatThrownBy(() -> activityService.update(1L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_NOT_EDITABLE);
    }

    @Test
    @DisplayName("编辑：草稿可以改，返回值反映新标题")
    void update_draftIsEditable() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.DRAFT)));
        when(activityRepository.saveAndFlush(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityRequest request = new ActivityRequest(
                "改名后的活动", "新的描述", 50,
                NOW.plusDays(1), NOW.plusDays(2), NOW.plusDays(4), NOW.plusDays(4).plusHours(2));

        ActivityDetail detail = activityService.update(1L, request);

        assertThat(detail.title()).isEqualTo("改名后的活动");
        assertThat(detail.capacity()).isEqualTo(50);
    }

    // ------------------------------------------------------------------
    // 发布 / 取消
    // ------------------------------------------------------------------

    @Test
    @DisplayName("发布：草稿 -> 已发布")
    void publish_fromDraft() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.DRAFT)));
        when(activityRepository.saveAndFlush(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityDetail detail = activityService.publish(1L);

        assertThat(detail.status()).isEqualTo(ActivityStatus.PUBLISHED);
        // 报名窗口从明天才开始，所以此刻还不可报名
        assertThat(detail.signupOpen()).isFalse();
    }

    @Test
    @DisplayName("发布：报名截止时间已过 -> ACTIVITY_TIME_INVALID")
    void publish_afterSignupEnd() {
        Activity activity = existing(ActivityStatus.DRAFT);
        activity.setSignupEndAt(NOW.minusHours(1));
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_TIME_INVALID);
    }

    @Test
    @DisplayName("发布：已发布的活动不能重复发布 -> ACTIVITY_STATUS_INVALID")
    void publish_alreadyPublished() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.PUBLISHED)));

        assertThatThrownBy(() -> activityService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_STATUS_INVALID);
    }

    @Test
    @DisplayName("取消：已发布 -> 已取消")
    void cancel_published() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.PUBLISHED)));
        when(activityRepository.saveAndFlush(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(activityService.cancel(1L).status()).isEqualTo(ActivityStatus.CANCELLED);
    }

    @Test
    @DisplayName("取消活动：级联把占位中的报名置为 CANCELLED，并把计数清零")
    void cancel_cascadesSignups() {
        Activity activity = existing(ActivityStatus.PUBLISHED);
        activity.setSignedUpCount(2);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        Signup pending = new Signup();
        pending.setId(50L);
        pending.setActivityId(1L);
        pending.setUserId(7L);
        pending.setStatus(SignupStatus.PENDING);

        Signup confirmed = new Signup();
        confirmed.setId(51L);
        confirmed.setActivityId(1L);
        confirmed.setUserId(8L);
        confirmed.setStatus(SignupStatus.CONFIRMED);

        when(signupRepository.findByActivityIdAndStatusIn(1L, List.of(SignupStatus.PENDING, SignupStatus.CONFIRMED)))
                .thenReturn(List.of(pending, confirmed));
        when(activityRepository.saveAndFlush(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityDetail detail = activityService.cancel(1L);

        assertThat(detail.status()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(detail.signedUpCount()).isZero();
        // 两条报名都走了状态机
        assertThat(pending.getStatus()).isEqualTo(SignupStatus.CANCELLED);
        assertThat(confirmed.getStatus()).isEqualTo(SignupStatus.CANCELLED);
        verify(signupRepository).saveAll(any());
    }

    @Test
    @DisplayName("取消：已结束的活动不能取消 -> ACTIVITY_STATUS_INVALID")
    void cancel_finished() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.FINISHED)));

        assertThatThrownBy(() -> activityService.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_STATUS_INVALID);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("用户查看未发布活动 -> 404，不暴露草稿是否存在")
    void userDetail_draftIsHidden() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.DRAFT)));

        assertThatThrownBy(() -> activityService.getDetailForUser(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("用户查看已发布活动 -> 正常返回")
    void userDetail_published() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(existing(ActivityStatus.PUBLISHED)));

        assertThat(activityService.getDetailForUser(1L).status()).isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    @DisplayName("分页参数兜底：size 超上限被压到 50，page=0 被当成第 1 页")
    @SuppressWarnings("unchecked")
    void list_pageParamsAreClamped() {
        when(activityRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(Page.empty());

        activityService.listPublished(null, 0, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), captor.capture());

        PageRequest pageRequest = (PageRequest) captor.getValue();
        assertThat(pageRequest.getPageNumber()).isZero();
        assertThat(pageRequest.getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("列表把实体映射成摘要，remaining 由后端算")
    void list_mapsToSummary() {
        Page<Activity> page = new PageImpl<>(List.of(existing(ActivityStatus.PUBLISHED)), PageRequest.of(0, 10), 1);
        when(activityRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(page);

        PageResult<?> result = activityService.listPublished(null, 1, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
    }
}
