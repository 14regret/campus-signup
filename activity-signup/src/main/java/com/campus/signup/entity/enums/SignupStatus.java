package com.campus.signup.entity.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 报名状态机。
 *
 * <pre>
 *   报名 ──> PENDING ──确认──> CONFIRMED ──取消──> CANCELLED ──重新报名──┐
 *              │  │                                    ▲                │
 *              │  └──取消──> CANCELLED ────────────────┘                │
 *              └──拒绝──> REJECTED ──重新报名───────────────────────────┘
 * </pre>
 *
 * <p>关键设计：{@code CANCELLED / REJECTED} 不是终态，而是「可复活态」。
 * 配合 {@code uk_signup_activity_user} 唯一索引，重新报名走同一行的条件 UPDATE，
 * 而不是 INSERT —— 这样既保留了唯一索引的并发兜底，又避免了唯一键冲突。
 *
 * <p>名额语义：{@link #PENDING} 即占位（见 {@link #occupiesQuota()}），
 * 否则「待确认」堆积时确认环节会超卖。
 */
public enum SignupStatus {

    PENDING,
    CONFIRMED,
    CANCELLED,
    REJECTED;

    private static final Map<SignupStatus, Set<SignupStatus>> ALLOWED;

    static {
        Map<SignupStatus, Set<SignupStatus>> map = new EnumMap<>(SignupStatus.class);
        map.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED, REJECTED));
        map.put(CONFIRMED, EnumSet.of(CANCELLED));
        map.put(CANCELLED, EnumSet.of(PENDING));
        map.put(REJECTED, EnumSet.of(PENDING));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    /**
     * 是否允许从当前状态流转到 {@code next}。
     *
     * <p>不允许流转到自己：幂等重复请求应在 service 层先行短路（返回成功），
     * 让状态机报错反而能暴露「同一请求被执行两次」的问题。
     */
    public boolean canTransitTo(SignupStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return ALLOWED.get(this).contains(next);
    }

    /** 是否占用活动名额。取消 / 拒绝后释放，重新报名时重新占用。 */
    public boolean occupiesQuota() {
        return this == PENDING || this == CONFIRMED;
    }

    /** 用户是否可以在这个状态下重新发起报名。 */
    public boolean isRevivable() {
        return this == CANCELLED || this == REJECTED;
    }
}
