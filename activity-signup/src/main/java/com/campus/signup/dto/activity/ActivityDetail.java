package com.campus.signup.dto.activity;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.enums.ActivityStatus;

import java.time.LocalDateTime;

/**
 * 活动详情。比列表项多出描述与审计字段。
 */
public record ActivityDetail(
        Long id,
        String title,
        String description,
        ActivityStatus status,
        int capacity,
        int signedUpCount,
        int remaining,
        boolean signupOpen,
        LocalDateTime signupStartAt,
        LocalDateTime signupEndAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ActivityDetail from(Activity activity, LocalDateTime now) {
        return new ActivityDetail(
                activity.getId(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getStatus(),
                activity.getCapacity(),
                activity.getSignedUpCount(),
                activity.remainingQuota(),
                activity.isSignupOpen(now),
                activity.getSignupStartAt(),
                activity.getSignupEndAt(),
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getCreatedBy(),
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }
}
