package com.campus.signup.service;

import com.campus.signup.dto.signup.MySignupResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 报名 / 取消报名。
 *
 * <h2>为什么锁必须包在事务外面</h2>
 * 顺序必须是「加锁 → 开事务 → 提交 → 释放锁」。如果反过来（方法上加 {@code @Transactional}，
 * 锁在方法体内），事务先开始、锁后拿到，那么释放锁的时候事务可能还没提交，
 * 下一个请求拿到锁读到的就是旧数据，锁等于白加。
 * 所以这里<b>不用</b> {@code @Transactional}，而是显式用 {@link TransactionOperations} 把
 * 事务边界画在锁内部。
 *
 * <h2>三重防线</h2>
 * <ol>
 *   <li><b>Redis 锁</b>：同一个活动串行化，把并发冲突挡在数据库之前（性能与体验）；</li>
 *   <li><b>条件 UPDATE</b>：{@code where signed_up_count < capacity}，数据库行锁保证原子性（真正的闸门）；</li>
 *   <li><b>唯一索引</b>：{@code uk_signup_activity_user}，锁失效时也不会出现同一人两条报名（最后兜底）。</li>
 * </ol>
 * 前两道任意一道失效，结果依然正确；这就是「锁是优化、不是正确性依赖」的含义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String LOCK_KEY_PREFIX = "lock:activity:";

    private final ActivityRepository activityRepository;
    private final SignupRepository signupRepository;
    private final RedisDistributedLock distributedLock;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    // ------------------------------------------------------------------
    // 报名
    // ------------------------------------------------------------------

    /**
     * 报名。锁 → 事务 → 提交 → 释放锁。
     */
    public SignupResponse signUp(Long activityId, Long userId) {
        RedisDistributedLock.LockHandle lock = distributedLock.lock(LOCK_KEY_PREFIX + activityId);
        try {
            SignupResponse response = transactionOperations.execute(status -> doSignUp(activityId, userId));
            log.info("报名成功 activityId={} userId={} remaining={}", activityId, userId,
                    response == null ? -1 : response.remaining());
            return response;
        } finally {
            // 走到这里事务已经提交或回滚完毕，此时释放锁才是安全的
            distributedLock.unlock(lock);
        }
    }

    private SignupResponse doSignUp(Long activityId, Long userId) {
        LocalDateTime now = now();

        Activity activity = activityRepository.findById(activityId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
        assertSignupOpen(activity, now);

        Signup existing = signupRepository.findByActivityIdAndUserId(activityId, userId).orElse(null);
        if (existing != null && (existing.getStatus() == SignupStatus.PENDING
                || existing.getStatus() == SignupStatus.CONFIRMED)) {
            throw new BusinessException(ErrorCode.ALREADY_SIGNED_UP);
        }

        // 第二道防线：条件扣减。0 行说明要么满了，要么活动状态在此期间变了
        int updated = activityRepository.incrementSignedUpCount(activityId, ActivityStatus.PUBLISHED);
        if (updated != 1) {
            Activity latest = activityRepository.findById(activityId).orElse(null);
            if (latest == null || latest.getStatus() != ActivityStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.SIGNUP_NOT_OPEN, "活动已结束或已取消");
            }
            throw new BusinessException(ErrorCode.ACTIVITY_FULL);
        }

        Signup signup;
        if (existing == null) {
            signup = new Signup();
            signup.setActivityId(activityId);
            signup.setUserId(userId);
            signup.setStatus(SignupStatus.PENDING);
            try {
                signup = signupRepository.saveAndFlush(signup);
            } catch (DataIntegrityViolationException e) {
                // 第三道防线：锁过期 / Redis 故障时，唯一索引兜住重复报名。
                // 抛出后整个事务回滚，刚扣掉的名额会一起退回，不会漏减也不会超卖。
                log.warn("唯一索引拦下重复报名 activityId={} userId={}", activityId, userId);
                throw new BusinessException(ErrorCode.ALREADY_SIGNED_UP);
            }
        } else {
            // 取消/被拒后重新报名：复用同一行做状态复活，绝不 INSERT（会撞唯一索引）
            int rows = signupRepository.changeStatus(existing.getId(), existing.getStatus(),
                    SignupStatus.PENDING, now);
            if (rows != 1) {
                throw new BusinessException(ErrorCode.SIGNUP_CONFLICT);
            }
            signup = signupRepository.findById(existing.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));
        }

        Activity fresh = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
        return new SignupResponse(signup.getId(), activityId, signup.getStatus(), fresh.remainingQuota());
    }

    // ------------------------------------------------------------------
    // 取消报名
    // ------------------------------------------------------------------

    /**
     * 用户取消自己的报名。先锁活动（因为要改活动名额计数），再进事务。
     */
    public SignupResponse cancel(Long signupId, Long userId) {
        // 先查一次只为拿到 activityId 拼锁 key；真正的归属校验在事务内再做一次
        Signup snapshot = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));
        if (!Objects.equals(snapshot.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.SIGNUP_FORBIDDEN);
        }

        RedisDistributedLock.LockHandle lock = distributedLock.lock(LOCK_KEY_PREFIX + snapshot.getActivityId());
        try {
            SignupResponse response = transactionOperations.execute(status -> doCancel(signupId, userId));
            log.info("取消报名 signupId={} userId={}", signupId, userId);
            return response;
        } finally {
            distributedLock.unlock(lock);
        }
    }

    private SignupResponse doCancel(Long signupId, Long userId) {
        LocalDateTime now = now();

        Signup signup = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));
        if (!Objects.equals(signup.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.SIGNUP_FORBIDDEN);
        }

        SignupStatus from = signup.getStatus();
        // 状态机是唯一入口：CANCELLED / REJECTED 不能再取消，CONFIRMED 可以
        if (!from.canTransitTo(SignupStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.SIGNUP_STATUS_INVALID,
                    "当前状态（" + from + "）不能取消报名");
        }

        int changed = signupRepository.changeStatus(signupId, from, SignupStatus.CANCELLED, now);
        if (changed != 1) {
            // 读到的状态和真正更新时的状态不一致，说明有并发改动
            throw new BusinessException(ErrorCode.SIGNUP_CONFLICT);
        }

        int released = activityRepository.decrementSignedUpCount(signup.getActivityId());
        if (released != 1) {
            // 计数已经是 0 却还有占位报名，说明数据已经不一致；回滚，交给对账任务处理
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "名额释放失败：活动计数异常，本次操作已回滚");
        }

        Activity activity = activityRepository.findById(signup.getActivityId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
        return new SignupResponse(signupId, signup.getActivityId(), SignupStatus.CANCELLED,
                activity.remainingQuota());
    }

    // ------------------------------------------------------------------
    // 系统超时取消（定时任务调用）
    // ------------------------------------------------------------------

    /**
     * 把超时未确认的 PENDING 报名改成 CANCELLED 并释放名额。
     *
     * <p>与用户主动取消走同一套「锁 → 事务 → 状态 CAS → 释放名额」流程，
     * 区别只是这里没有归属校验，多了一道「创建时间必须早于 cutoff」的复核——
     * 因为从扫描到真正执行之间，用户可能刚好被管理员确认了。
     *
     * @param cutoff 早于这个时间的 PENDING 才算超时
     * @return true 表示本次真的取消了，false 表示已被别人处理或不满足条件（属于正常情况）
     */
    public boolean cancelExpired(Long signupId, LocalDateTime cutoff) {
        Signup snapshot = signupRepository.findById(signupId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != SignupStatus.PENDING) {
            return false;
        }

        RedisDistributedLock.LockHandle lock = distributedLock.lock(LOCK_KEY_PREFIX + snapshot.getActivityId());
        try {
            return Boolean.TRUE.equals(
                    transactionOperations.execute(status -> doCancelExpired(signupId, cutoff)));
        } finally {
            distributedLock.unlock(lock);
        }
    }

    private boolean doCancelExpired(Long signupId, LocalDateTime cutoff) {
        Signup signup = signupRepository.findById(signupId).orElse(null);
        if (signup == null || signup.getStatus() != SignupStatus.PENDING) {
            return false;
        }
        if (signup.getCreatedAt() == null || !signup.getCreatedAt().isBefore(cutoff)) {
            // 扫描之后被确认/续期了，放过
            return false;
        }
        if (!signup.getStatus().canTransitTo(SignupStatus.CANCELLED)) {
            return false;
        }

        int changed = signupRepository.changeStatus(signupId, SignupStatus.PENDING,
                SignupStatus.CANCELLED, now());
        if (changed != 1) {
            return false;
        }
        int released = activityRepository.decrementSignedUpCount(signup.getActivityId());
        if (released != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "超时取消释放名额失败，本次操作已回滚");
        }
        log.info("超时自动取消报名 signupId={} activityId={} userId={}",
                signupId, signup.getActivityId(), signup.getUserId());
        return true;
    }

    // ------------------------------------------------------------------
    // 我的报名
    // ------------------------------------------------------------------

    /**
     * 当前用户的全部报名记录。
     *
     * <p>不做分页：一个学生在校期间报过的活动量级很小（几十条），
     * 分页反而让前端为了渲染「我报过哪些」多发几次请求。
     * 如果以后数据量上来了，再换成 PageResult 并保留同样的字段。
     */
    @Transactional(readOnly = true)
    public List<MySignupResponse> listMySignups(Long userId) {
        List<Signup> signups = signupRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (signups.isEmpty()) {
            return List.of();
        }
        // 一次把涉及的活动查出来，避免逐条 N+1（学生最多几十条，IN 查询完全够用）
        Map<Long, Activity> activities = activityRepository
                .findAllById(signups.stream().map(Signup::getActivityId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Activity::getId, activity -> activity));

        return signups.stream()
                .map(signup -> MySignupResponse.of(signup, activities.get(signup.getActivityId())))
                .toList();
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 报名窗口校验。锁内再校验一次，避免「校验时还没开始、真正扣减时已经截止」。
     */
    private static void assertSignupOpen(Activity activity, LocalDateTime now) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_OPEN, "活动未发布或已取消");
        }
        if (now.isBefore(activity.getSignupStartAt())) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_OPEN, "报名尚未开始");
        }
        if (!now.isBefore(activity.getSignupEndAt())) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_OPEN, "报名已截止");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
