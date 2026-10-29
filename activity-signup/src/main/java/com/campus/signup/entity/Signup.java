package com.campus.signup.entity;

import com.campus.signup.entity.enums.SignupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * 报名记录。
 *
 * <p>{@code (activity_id, user_id)} 上有唯一索引，所以这张表对同一个用户同一个活动
 * <b>永远只有一行</b>：取消只改状态，重新报名是把 CANCELLED/REJECTED 改回 PENDING。
 * service 层不能用 INSERT 表达「重新报名」，否则必然撞 {@code uk_signup_activity_user}。
 *
 * <p>这里不放 {@code @Version}：并发由「唯一索引 + 状态 CAS UPDATE」兜住，
 * 再加乐观锁只会多一层必然失败的冲突。
 */
@Getter
@Setter
@Entity
@Table(name = "signup")
public class Signup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联活动。刻意用裸 ID 而不是 @ManyToOne：避免 N+1 和 JSON 序列化时的懒加载陷阱。 */
    @Column(name = "activity_id", nullable = false, updatable = false)
    private Long activityId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SignupStatus status = SignupStatus.PENDING;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 内存态流转。真正的并发正确性靠 service 层的条件 UPDATE
     * （{@code WHERE status = 期望旧值}），这里只是拦住明显写错的调用路径。
     *
     * @throws IllegalStateException 非法流转
     */
    public void transitionTo(SignupStatus target) {
        if (!status.canTransitTo(target)) {
            throw new IllegalStateException(
                    "非法状态流转: " + status + " -> " + target + " (signupId=" + id + ")");
        }
        this.status = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Signup other)) {
            return false;
        }
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Signup.class.hashCode();
    }

    @Override
    public String toString() {
        return "Signup{id=" + id
                + ", activityId=" + activityId
                + ", userId=" + userId
                + ", status=" + status
                + '}';
    }
}
