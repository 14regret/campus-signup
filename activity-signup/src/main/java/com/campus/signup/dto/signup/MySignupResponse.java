package com.campus.signup.dto.signup;

import com.campus.signup.entity.Activity;
import com.campus.signup.entity.Signup;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.entity.enums.SignupStatus;

import java.time.LocalDateTime;

/**
 * 「我的报名」列表项。
 *
 * <p>带上活动的标题与时间：否则前端要为每条报名再请求一次活动详情（N+1），
 * 而这些字段本来就是报名页要展示的全部内容。
 *
 * <p>活动被逻辑删除时活动相关字段为 null，前端需要能降级显示「活动已删除」。
 */
public record MySignupResponse(
        Long signupId,
        Long activityId,
        SignupStatus status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String activityTitle,
        ActivityStatus activityStatus,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime signupEndAt
) {

    public static MySignupResponse of(Signup signup, Activity activity) {
        return new MySignupResponse(
                signup.getId(),
                signup.getActivityId(),
                signup.getStatus(),
                signup.getRemark(),
                signup.getCreatedAt(),
                signup.getUpdatedAt(),
                activity == null ? null : activity.getTitle(),
                activity == null ? null : activity.getStatus(),
                activity == null ? null : activity.getStartAt(),
                activity == null ? null : activity.getEndAt(),
                activity == null ? null : activity.getSignupEndAt());
    }
}
