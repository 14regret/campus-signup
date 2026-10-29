package com.campus.signup.entity.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 活动状态机。只持久化 4 个「业务态」，「报名中 / 进行中 / 已结束」这类展示态由时间推导，
 * 避免状态与时间两个真相打架（定时任务晚跑一分钟不该让用户在截止后还能报名）。
 *
 * <pre>
 *   DRAFT ──发布──> PUBLISHED ──end_at 已过──> FINISHED
 *     │                  │
 *     └──取消──> CANCELLED <──取消──┘
 * </pre>
 *
 * <p>FINISHED / CANCELLED 是终态，不可再流转。
 */
public enum ActivityStatus {

    DRAFT,
    PUBLISHED,
    CANCELLED,
    FINISHED;

    private static final Map<ActivityStatus, Set<ActivityStatus>> ALLOWED;

    static {
        Map<ActivityStatus, Set<ActivityStatus>> map = new EnumMap<>(ActivityStatus.class);
        map.put(DRAFT, EnumSet.of(PUBLISHED, CANCELLED));
        map.put(PUBLISHED, EnumSet.of(CANCELLED, FINISHED));
        map.put(FINISHED, EnumSet.noneOf(ActivityStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(ActivityStatus.class));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    /**
     * 是否允许从当前状态流转到 {@code next}。
     *
     * <p>刻意不允许「流转到自己」：调用方应先判断是否为幂等重复请求，
     * 而不是让状态机替它兜底，否则真正的重复提交会被静默吞掉。
     */
    public boolean canTransitTo(ActivityStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return ALLOWED.get(this).contains(next);
    }

    /** 终态不可再变，用于「重复操作直接返回」「定时任务跳过」这类短路判断。 */
    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }

    /** 只有已发布的活动才接受报名。 */
    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
