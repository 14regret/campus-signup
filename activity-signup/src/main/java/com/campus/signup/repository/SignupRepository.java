package com.campus.signup.repository;

import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.SignupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 报名仓储。
 *
 * <p>状态变更一律用「带 {@code where status = 旧值} 的条件 UPDATE」而不是读出来改字段再保存：
 * 后者在并发下会出现「两个请求都读到 PENDING，都改成 CANCELLED，名额被扣两次」。
 */
public interface SignupRepository extends JpaRepository<Signup, Long> {

    Optional<Signup> findByActivityIdAndUserId(Long activityId, Long userId);

    /** 「我的报名」：按报名时间倒序，最近报的排前面。 */
    List<Signup> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 管理端审核：某个活动的全部报名，按提交时间正序（先来先审）。 */
    List<Signup> findByActivityIdOrderByCreatedAtAsc(Long activityId);

    List<Signup> findByActivityIdAndStatusIn(Long activityId, Collection<SignupStatus> statuses);

    /**
     * 状态 CAS：只有当前状态仍是 {@code expected} 时才会更新成功。
     *
     * <p>返回受影响行数：1 表示抢到了这次状态变更，0 表示已经被别人改过，调用方必须按 0 处理。
     *
     * <p>注意：JPQL 批量更新不会触发 {@code @UpdateTimestamp}，所以 updatedAt 要显式赋值。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Signup s
               set s.status = :target,
                   s.updatedAt = :now
             where s.id = :id
               and s.status = :expected
            """)
    int changeStatus(@Param("id") Long id,
                     @Param("expected") SignupStatus expected,
                     @Param("target") SignupStatus target,
                     @Param("now") LocalDateTime now);

    /** 超时取消任务：按状态 + 创建时间分批捞取。 */
    Page<Signup> findByStatusAndCreatedAtBefore(SignupStatus status, LocalDateTime cutoff, Pageable pageable);

    /**
     * 对账任务：一次性统计一批活动的占位报名数。
     *
     * <p>只查一次而不是每个活动查一次，避免 N+1；返回的是接口投影，
     * Spring Data 会把 {@code activityId} / {@code total} 两个别名映射到 getter。
     */
    @Query("""
            select s.activityId as activityId, count(s) as total
              from Signup s
             where s.activityId in :activityIds
               and s.status in :statuses
             group by s.activityId
            """)
    List<ActivitySignupCount> countOccupyingByActivityIds(@Param("activityIds") Collection<Long> activityIds,
                                                          @Param("statuses") Collection<SignupStatus> statuses);

    /** 对账任务在锁内重新计数时用。 */
    @Query("""
            select count(s)
              from Signup s
             where s.activityId = :activityId
               and s.status in :statuses
            """)
    long countOccupyingByActivityId(@Param("activityId") Long activityId,
                                    @Param("statuses") Collection<SignupStatus> statuses);

    /** 仅用于测试清理。生产环境不做物理删除。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Signup s where s.activityId = :activityId")
    @org.springframework.transaction.annotation.Transactional
    int deleteByActivityId(@Param("activityId") Long activityId);

    /** 按活动分组的占位报名数量投影。 */
    interface ActivitySignupCount {
        Long getActivityId();

        long getTotal();
    }
}
