package com.campus.signup.entity;

import com.campus.signup.entity.enums.ActivityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 活动。
 *
 * <p>名额并发控制有两条独立防线，别混用：
 * <ol>
 *   <li>{@code @Version} 只保护「管理员编辑活动」时的丢失更新（两个人同时改名额/时间）；</li>
 *   <li>报名扣减走 {@code ActivityRepository#deductQuota} 的条件原子 UPDATE，
 *       <b>不</b>走乐观锁 —— 乐观锁在高并发下会大量失败重试，条件 UPDATE 只是一次行锁等待。</li>
 * </ol>
 *
 * <p>手写原生 UPDATE 时务必同时 {@code version = version + 1}，否则本字段会和库里的值脱节。
 */
@Getter
@Setter
@Entity
@Table(name = "activity")
public class Activity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    /** 2000 字符足够放活动详情；真要富文本再换 TEXT + @JdbcTypeCode(SqlTypes.LONGVARCHAR)。 */
    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** 冗余计数：PENDING 与 CONFIRMED 都计入，和 signup 明细在同一个事务里改。 */
    @Column(name = "signed_up_count", nullable = false)
    private Integer signedUpCount = 0;

    @Column(name = "signup_start_at", nullable = false)
    private LocalDateTime signupStartAt;

    @Column(name = "signup_end_at", nullable = false)
    private LocalDateTime signupEndAt;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivityStatus status = ActivityStatus.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** 仅审计用途，不参与数据权限隔离（任何 ADMIN 都能管理所有活动）。 */
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    // ------------------------------------------------------------------
    // 领域行为：放在实体里，service 与定时任务共用同一套判定，避免两份时间逻辑走偏
    // ------------------------------------------------------------------

    /**
     * 当前时刻是否处于可报名窗口。
     *
     * <p>左闭右开：{@code signupStartAt <= now < signupEndAt}。
     * 截止时刻整点之后立即不可报，避免「最后一毫秒」的边界争议。
     */
    public boolean isSignupOpen(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        return status == ActivityStatus.PUBLISHED
                && !now.isBefore(signupStartAt)
                && now.isBefore(signupEndAt);
    }

    /** 名额是否已占满。只作快速判断，真正的并发闸门在条件 UPDATE 上。 */
    public boolean isFull() {
        return signedUpCount >= capacity;
    }

    /** 剩余名额，最小为 0。 */
    public int remainingQuota() {
        return Math.max(0, capacity - signedUpCount);
    }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // hashCode 用类常量，保证实体在持久化前后（id 由 null 变有值）哈希不变，
    // 否则放进 HashSet 后 add 再 contains 会找不到。
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Activity other)) {
            return false;
        }
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Activity.class.hashCode();
    }

    @Override
    public String toString() {
        return "Activity{id=" + id
                + ", title='" + title + '\''
                + ", status=" + status
                + ", capacity=" + capacity
                + ", signedUpCount=" + signedUpCount
                + '}';
    }
}
