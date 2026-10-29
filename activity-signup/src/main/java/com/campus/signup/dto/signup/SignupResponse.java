package com.campus.signup.dto.signup;

import com.campus.signup.entity.enums.SignupStatus;

/**
 * 报名操作结果。
 *
 * @param remaining 本次操作后活动的剩余名额，前端直接拿来刷新按钮状态
 */
public record SignupResponse(
        Long signupId,
        Long activityId,
        SignupStatus status,
        int remaining
) {
}
