package com.campus.signup.service;

import com.campus.signup.common.PageResult;
import com.campus.signup.dto.activity.ActivityDetail;
import com.campus.signup.dto.activity.ActivityRequest;
import com.campus.signup.dto.activity.ActivitySummary;
import com.campus.signup.entity.Activity;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import com.campus.signup.lock.RedisDistributedLock;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动管理。管理员可以增删改查（逻辑删暂未开放），普通用户只能看已发布的。
 *
 * <p>几条不变量：
 * <ul>
 *   <li>新建的活动一律是 {@link ActivityStatus#DRAFT}，必须显式发布才对用户可见；</li>
 *   <li>只有草稿能编辑——已发布的活动改时间/名额会直接影响正在报名的人；</li>
 *   <li>发布前校验报名截止时间和活动开始时间都还没过，否则刚发布就是死活动；</li>
 *   <li>取消活动会级联取消该活动下所有占位中的报名，并把计数清零（名额一致性）。</li>
 * </ul>
 *
 * <p>注意读取方法没加 {@code @Transactional}：每个都是单条查询，让 repository 自己开只读事务即可。
 * 写方法按是否涉及锁分成两类——{@link #cancel(Long)} 需要先加锁再开事务，所以显式用
 * {@link TransactionOperations}，其余三个没有跨行并发语义，直接 {@code @Transactional}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String LOCK_KEY_PREFIX = "lock:activity:";

    private final ActivityRepository activityRepository;
    private final SignupRepository signupRepository;
    private final RedisDistributedLock distributedLock;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    // ------------------------------------------------------------------
    // 写操作（管理员）
    // ------------------------------------------------------------------

    @Transactional
    public ActivityDetail create(ActivityRequest request, Long creatorId) {
        validateTimeRange(request);

        Activity activity = new Activity();
        applyRequest(activity, request);
        activity.setStatus(ActivityStatus.DRAFT);
        activity.setSignedUpCount(0);
        activity.setCreatedBy(creatorId);

        // 用 saveAndFlush 而不是 save：@CreationTimestamp 在 flush 时才赋值，
        // 只 save 的话返回给前端的 createdAt 会是 null
        Activity saved = activityRepository.saveAndFlush(activity);
        log.info("创建活动 id={} title={} creator={}", saved.getId(), saved.getTitle(), creatorId);
        return ActivityDetail.from(saved, now());
    }

    @Transactional
    public ActivityDetail update(Long id, ActivityRequest request) {
        Activity activity = getOrThrow(id);

        if (activity.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_EDITABLE);
        }
        if (request.capacity() < activity.getSignedUpCount()) {
            throw new BusinessException(ErrorCode.ACTIVITY_CAPACITY_TOO_SMALL);
        }
        validateTimeRange(request);

        applyRequest(activity, request);
        Activity saved = activityRepository.saveAndFlush(activity);
        log.info("更新活动 id={} title={}", saved.getId(), saved.getTitle());
        return ActivityDetail.from(saved, now());
    }

    @Transactional
    public ActivityDetail publish(Long id) {
        Activity activity = getOrThrow(id);
        LocalDateTime now = now();

        if (!activity.getStatus().canTransitTo(ActivityStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "只有草稿状态的活动可以发布");
        }
        if (!activity.getSignupEndAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACTIVITY_TIME_INVALID, "报名截止时间已过，无法发布");
        }
        if (!activity.getStartAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACTIVITY_TIME_INVALID, "活动开始时间已过，无法发布");
        }

        activity.setStatus(ActivityStatus.PUBLISHED);
        Activity saved = activityRepository.saveAndFlush(activity);
        log.info("发布活动 id={} title={}", saved.getId(), saved.getTitle());
        return ActivityDetail.from(saved, now);
    }

    /**
     * 取消活动：锁 → 事务（级联取消报名 + 计数清零）→ 提交 → 释放锁。
     *
     * <p>为什么必须加锁：这里既要改活动状态，又要改它下面所有报名记录和计数。
     * 如果不锁，一个用户可能正在报名（扣了名额），管理员同时在取消（清零），
     * 两边交错就会留下「活动已取消但计数非 0」或「报名成功后活动没了」的脏数据。
     * 报名与取消报名用的是同一把 {@code lock:activity:{id}}，所以三方天然互斥。
     */
    public ActivityDetail cancel(Long id) {
        RedisDistributedLock.LockHandle lock = distributedLock.lock(LOCK_KEY_PREFIX + id);
        try {
            return transactionOperations.execute(status -> doCancel(id));
        } finally {
            distributedLock.unlock(lock);
        }
    }

    private ActivityDetail doCancel(Long id) {
        LocalDateTime now = now();
        Activity activity = getOrThrow(id);

        if (!activity.getStatus().canTransitTo(ActivityStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "当前状态不能取消");
        }

        // 逐条走状态机而不是一条 UPDATE 全改：校园活动量级下（几百条）完全够用，
        // 而且能把「非法流转」当场暴露出来。量级上万时应改成带 status IN (...) 条件的批量 UPDATE。
        List<Signup> occupying = signupRepository.findByActivityIdAndStatusIn(
                id, List.of(SignupStatus.PENDING, SignupStatus.CONFIRMED));
        for (Signup signup : occupying) {
            signup.transitionTo(SignupStatus.CANCELLED);
        }
        signupRepository.saveAll(occupying);

        int countedBefore = activity.getSignedUpCount();
        if (countedBefore != occupying.size()) {
            // 冗余计数和明细对不上，说明之前有过异常中断；取消活动时以明细为准并把计数归零
            log.warn("取消活动时发现计数不一致 activityId={} 计数={} 实际占位={}",
                    id, countedBefore, occupying.size());
        }

        activity.setStatus(ActivityStatus.CANCELLED);
        activity.setSignedUpCount(0);
        Activity saved = activityRepository.saveAndFlush(activity);

        log.info("取消活动 id={} 级联取消报名 {} 条", id, occupying.size());
        return ActivityDetail.from(saved, now);
    }

    // ------------------------------------------------------------------
    // 读操作
    // ------------------------------------------------------------------

    /** 用户侧列表：只返回已发布的活动，按活动开始时间正序（最近的排前面）。 */
    public PageResult<ActivitySummary> listPublished(String keyword, int page, int size) {
        Pageable pageable = pageableOf(page, size, Sort.by(Sort.Direction.ASC, "startAt"));
        Page<Activity> result = activityRepository.findAll(
                buildSpec(keyword, ActivityStatus.PUBLISHED), pageable);
        return toSummaryPage(result);
    }

    /** 管理员列表：所有状态都能看到，按创建时间倒序。 */
    public PageResult<ActivitySummary> listForAdmin(ActivityStatus status, String keyword, int page, int size) {
        Pageable pageable = pageableOf(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Activity> result = activityRepository.findAll(buildSpec(keyword, status), pageable);
        return toSummaryPage(result);
    }

    public ActivityDetail getDetailForAdmin(Long id) {
        return ActivityDetail.from(getOrThrow(id), now());
    }

    /**
     * 用户侧详情。未发布的活动一律返回 404 而不是 403：
     * 403 等于告诉对方「这里确实有个草稿」，属于信息泄露。
     */
    public ActivityDetail getDetailForUser(Long id) {
        Activity activity = getOrThrow(id);
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return ActivityDetail.from(activity, now());
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private Activity getOrThrow(Long id) {
        return activityRepository.findById(id)
                .filter(activity -> !activity.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
    }

    private PageResult<ActivitySummary> toSummaryPage(Page<Activity> page) {
        LocalDateTime now = now();
        // 一次把 now 算好传进去，避免同一个响应里不同条目用了不同时刻
        return PageResult.from(page.map(activity -> ActivitySummary.from(activity, now)));
    }

    /**
     * 状态 + 关键字 + 未删除。
     *
     * <p>用 Specification 而不是 JPQL 的 {@code (:status is null or ...)}：
     * 后者在部分数据库/驱动上会因为 null 参数推断不出类型而报错。
     */
    private static Specification<Activity> buildSpec(String keyword, ActivityStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(3);
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                predicates.add(criteriaBuilder.like(root.get("title"), "%" + keyword.trim() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Pageable pageableOf(int page, int size, Sort sort) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage - 1, safeSize, sort);
    }

    private static void applyRequest(Activity activity, ActivityRequest request) {
        activity.setTitle(request.title().trim());
        activity.setDescription(request.description());
        activity.setCapacity(request.capacity());
        activity.setSignupStartAt(request.signupStartAt());
        activity.setSignupEndAt(request.signupEndAt());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
    }

    /**
     * 时间先后关系：报名开始 &lt; 报名截止 &lt;= 活动开始 &lt; 活动结束。
     *
     * <p>报名截止允许等于活动开始（同一时刻截止报名、马上开场），所以用 {@code isAfter} 而不是 {@code !isBefore}。
     */
    private static void validateTimeRange(ActivityRequest request) {
        if (!request.signupStartAt().isBefore(request.signupEndAt())) {
            throw new BusinessException(ErrorCode.ACTIVITY_TIME_INVALID, "报名开始时间必须早于报名截止时间");
        }
        if (request.signupEndAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.ACTIVITY_TIME_INVALID, "报名截止时间不能晚于活动开始时间");
        }
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.ACTIVITY_TIME_INVALID, "活动开始时间必须早于活动结束时间");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
