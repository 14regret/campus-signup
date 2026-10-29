package com.campus.signup.service;

import com.campus.signup.dto.signup.AdminSignupItem;
import com.campus.signup.dto.signup.BatchReviewRequest;
import com.campus.signup.dto.signup.BatchReviewResponse;
import com.campus.signup.dto.signup.ReviewAction;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.SysUser;
import com.campus.signup.entity.enums.SignupStatus;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import com.campus.signup.lock.RedisDistributedLock;
import com.campus.signup.repository.ActivityRepository;
import com.campus.signup.repository.SignupRepository;
import com.campus.signup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端报名审核。
 *
 * <p>三个动作的成本完全不同：
 * <ul>
 *   <li><b>确认</b>：只改状态，不动名额（PENDING 本来就占着位），不需要锁；</li>
 *   <li><b>拒绝</b>：要退还名额，必须和报名、取消走同一把活动锁，否则计数会被并发改乱；</li>
 *   <li><b>批量</b>：逐条独立处理，不追求整体原子——一条失败（比如用户刚好自己取消了）
 *       不该让其余 99 条回滚。</li>
 * </ul>
 *
 * <p>已确认（CONFIRMED）的报名不能被拒绝：状态机里 CONFIRMED 只能走到 CANCELLED，
 * 撤销已确认的报名属于另一个业务动作（需不需要通知、算不算违约），当前版本不做。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSignupService {

    private static final String LOCK_KEY_PREFIX = "lock:activity:";

    private final SignupRepository signupRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final RedisDistributedLock distributedLock;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /** 某个活动的报名列表，带上报名的用户名。 */
    public List<AdminSignupItem> listActivitySignups(Long activityId) {
        List<Signup> signups = signupRepository.findByActivityIdOrderByCreatedAtAsc(activityId);
        if (signups.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = userRepository
                .findAllById(signups.stream().map(Signup::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user));

        return signups.stream()
                .map(signup -> AdminSignupItem.of(signup, users.get(signup.getUserId())))
                .toList();
    }

    /**
     * 确认报名：PENDING → CONFIRMED。
     *
     * <p>不加锁：不改名额，纯状态 CAS，冲突时返回 4007 让管理员重试。
     */
    public void confirm(Long signupId) {
        transactionOperations.executeWithoutResult(status -> {
            Signup signup = signupRepository.findById(signupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));

            if (!signup.getStatus().canTransitTo(SignupStatus.CONFIRMED)) {
                throw new BusinessException(ErrorCode.SIGNUP_STATUS_INVALID,
                        "只有待确认的报名可以确认（当前：" + signup.getStatus() + "）");
            }

            int changed = signupRepository.changeStatus(signupId, SignupStatus.PENDING,
                    SignupStatus.CONFIRMED, LocalDateTime.now(clock));
            if (changed != 1) {
                throw new BusinessException(ErrorCode.SIGNUP_CONFLICT);
            }
            log.info("管理员确认报名 signupId={} activityId={}", signupId, signup.getActivityId());
        });
    }

    /**
     * 拒绝报名：PENDING → REJECTED，并把名额退回去。
     *
     * <p>必须锁活动：要同时改报名状态和活动计数，和用户取消走同一把锁才不会打架。
     */
    public void reject(Long signupId) {
        Signup snapshot = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));

        RedisDistributedLock.LockHandle lock =
                distributedLock.lock(LOCK_KEY_PREFIX + snapshot.getActivityId());
        try {
            transactionOperations.executeWithoutResult(status -> doReject(signupId));
        } finally {
            distributedLock.unlock(lock);
        }
    }

    private void doReject(Long signupId) {
        Signup signup = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));

        if (!signup.getStatus().canTransitTo(SignupStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.SIGNUP_STATUS_INVALID,
                    "只有待确认的报名可以拒绝（当前：" + signup.getStatus() + "）");
        }

        int changed = signupRepository.changeStatus(signupId, SignupStatus.PENDING,
                SignupStatus.REJECTED, LocalDateTime.now(clock));
        if (changed != 1) {
            throw new BusinessException(ErrorCode.SIGNUP_CONFLICT);
        }

        int released = activityRepository.decrementSignedUpCount(signup.getActivityId());
        if (released != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "名额释放失败，本次操作已回滚");
        }
        log.info("管理员拒绝报名 signupId={} activityId={}", signupId, signup.getActivityId());
    }

    /**
     * 撤销已确认的报名：CONFIRMED → CANCELLED，并退还名额。
     *
     * <p>和拒绝的区别只在入口状态不同：
     * 拒绝处理「还没确认的申请」，撤销处理「已经确认、但管理员要收回」的情况（比如现场名额调整）。
     * 两者都必须锁活动，因为都要改计数。
     *
     * <p>状态机里 CONFIRMED 只能走到 CANCELLED，不能直接走到 REJECTED——
     * 拒绝意味着「没通过审核」，而撤销意味着「通过过，但现在不算数了」，语义上不该混用。
     */
    public void revoke(Long signupId) {
        Signup snapshot = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));

        RedisDistributedLock.LockHandle lock =
                distributedLock.lock(LOCK_KEY_PREFIX + snapshot.getActivityId());
        try {
            transactionOperations.executeWithoutResult(status -> doRevoke(signupId));
        } finally {
            distributedLock.unlock(lock);
        }
    }

    private void doRevoke(Long signupId) {
        Signup signup = signupRepository.findById(signupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_NOT_FOUND));

        if (signup.getStatus() != SignupStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.SIGNUP_STATUS_INVALID,
                    "只有已确认的报名可以撤销（当前：" + signup.getStatus() + "）");
        }

        int changed = signupRepository.changeStatus(signupId, SignupStatus.CONFIRMED,
                SignupStatus.CANCELLED, LocalDateTime.now(clock));
        if (changed != 1) {
            throw new BusinessException(ErrorCode.SIGNUP_CONFLICT);
        }

        int released = activityRepository.decrementSignedUpCount(signup.getActivityId());
        if (released != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "名额释放失败，本次操作已回滚");
        }
        log.info("管理员撤销已确认报名 signupId={} activityId={}", signupId, signup.getActivityId());
    }

    /**
     * 批量审核。逐条独立事务，返回成功/失败条数。
     */
    public BatchReviewResponse reviewBatch(BatchReviewRequest request) {
        int success = 0;
        int failed = 0;

        for (Long signupId : request.signupIds()) {
            try {
                switch (request.action()) {
                    case CONFIRM -> confirm(signupId);
                    case REJECT -> reject(signupId);
                    case REVOKE -> revoke(signupId);
                }
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("批量审核单条失败 signupId={} action={} reason={}",
                        signupId, request.action(), e.getMessage());
            }
        }

        log.info("批量审核完成 action={} 成功={} 失败={}", request.action(), success, failed);
        return new BatchReviewResponse(success, failed);
    }
}
