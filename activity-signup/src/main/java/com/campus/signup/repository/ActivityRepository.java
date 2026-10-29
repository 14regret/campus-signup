package com.campus.signup.repository;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.enums.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 活动仓储。
 *
 * <p>列表查询用 {@link JpaSpecificationExecutor} 而不是拼 8 个派生方法名：
 * 「状态 + 关键字 + 是否删除」组合起来，派生方法会爆炸，Specification 里一个方法就能覆盖。
 *
 * <p>名额增减走两条 {@code @Modifying} 的条件 UPDATE。这是防超卖的<b>最后一道闸门</b>：
 * Redis 锁可能因为超时、Redis 抖动、甚至被人手动删 key 而失效，但
 * {@code where signed_up_count < capacity} 这个条件是库层面的原子判断，
 * 只要它还在，就不可能扣出负数或超过上限。
 */
public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    /**
     * 占用一个名额。
     *
     * <p>条件里同时包含 {@code status = PUBLISHED}：活动被取消/结束时，即使锁已经过期，
     * 这条语句也不会再放人进来。
     *
     * <p>{@code version = version + 1} 必须手写：原生/JPQL 批量更新不经过 Hibernate 的
     * {@code @Version} 机制，不自增的话内存实体和库里的版本号会脱节。
     *
     * @return 1 = 占位成功；0 = 名额已满或活动已不可报名
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Activity a
               set a.signedUpCount = a.signedUpCount + 1,
                   a.version = a.version + 1
             where a.id = :activityId
               and a.deleted = false
               and a.status = :status
               and a.signedUpCount < a.capacity
            """)
    int incrementSignedUpCount(@Param("activityId") Long activityId,
                               @Param("status") ActivityStatus status);

    /**
     * 释放一个名额。
     *
     * <p>{@code signedUpCount > 0} 是防御：即便调用方逻辑出错，也不会把计数减成负数。
     *
     * @return 1 = 释放成功；0 = 计数已经为 0（说明数据不一致，调用方应回滚）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Activity a
               set a.signedUpCount = a.signedUpCount - 1,
                   a.version = a.version + 1
             where a.id = :activityId
               and a.signedUpCount > 0
            """)
    int decrementSignedUpCount(@Param("activityId") Long activityId);

    /**
     * 对账任务用：把计数直接改成指定值。
     *
     * <p>{@code and a.signedUpCount <> :count} 让重复执行变成幂等操作——
     * 已经对上的活动不会被无谓地写一次（也就不会白白推高 version）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Activity a
               set a.signedUpCount = :count,
                   a.version = a.version + 1
             where a.id = :activityId
               and a.signedUpCount <> :count
            """)
    int syncSignedUpCount(@Param("activityId") Long activityId, @Param("count") int count);

    /** 状态推进任务：已发布但活动已经结束的。 */
    Page<Activity> findByStatusAndEndAtBeforeAndDeletedFalse(ActivityStatus status,
                                                             LocalDateTime endAt,
                                                             Pageable pageable);

    /** 对账任务：分批扫描所有未删除活动。 */
    Page<Activity> findByDeletedFalse(Pageable pageable);
}
