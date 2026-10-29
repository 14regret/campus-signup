package com.campus.signup.dto.activity;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.enums.ActivityStatus;

import java.time.LocalDateTime;

/**
 * 列表项。只带列表需要的字段，不返回 description，避免一页 20 条把正文全拉回来。
 *
 * @param remaining  剩余名额，由后端算好，前端不用 capacity - signedUpCount
 * @param signupOpen 当前是否可报名（已发布 且 在报名窗口内），前端据此决定按钮状态，
 *                   避免前端自己比对时间时和服务端时钟有偏差
 */
public record ActivitySummary(
        Long id,
        String title,
        ActivityStatus status,
        int capacity,
        int signedUpCount,
        int remaining,
        boolean signupOpen,
        LocalDateTime signupStartAt,
        LocalDateTime signupEndAt,
        LocalDateTime startAt,
        LocalDateTime endAt
) {

    public static ActivitySummary from(Activity activity, LocalDateTime now) {
        return new ActivitySummary(
                activity.getId(),
                activity.getTitle(),
                activity.getStatus(),
                activity.getCapacity(),
                activity.getSignedUpCount(),
                activity.remainingQuota(),
                activity.isSignupOpen(now),
                activity.getSignupStartAt(),
                activity.getSignupEndAt(),
                activity.getStartAt(),
                activity.getEndAt());
    }
}
