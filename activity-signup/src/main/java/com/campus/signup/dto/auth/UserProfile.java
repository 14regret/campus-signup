package com.campus.signup.dto.auth;

import com.campus.signup.entity.SysUser;
import com.campus.signup.entity.enums.Role;

/**
 * 对外暴露的用户信息。<b>绝不包含 password 字段</b>，也不直接把实体丢给前端。
 */
public record UserProfile(
        Long id,
        String username,
        String nickname,
        Role role
) {

    public static UserProfile from(SysUser user) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole());
    }
}
