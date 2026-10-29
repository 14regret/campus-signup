package com.campus.signup.dto.signup;

import com.campus.signup.entity.Signup;
import com.campus.signup.entity.SysUser;
import com.campus.signup.entity.enums.SignupStatus;

import java.time.LocalDateTime;

/**
 * 管理端审核列表项：一条报名 + 报名人信息。
 *
 * <p>管理员要判断「确认还是拒绝」，必须看到是谁报的名和什么时候报的，
 * 只给 userId 等于让人去别的页面查。
 */
public record AdminSignupItem(
        Long signupId,
        Long userId,
        String username,
        String nickname,
        SignupStatus status,
        String remark,
        LocalDateTime createdAt
) {

    public static AdminSignupItem of(Signup signup, SysUser user) {
        return new AdminSignupItem(
                signup.getId(),
                signup.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                signup.getStatus(),
                signup.getRemark(),
                signup.getCreatedAt());
    }
}
