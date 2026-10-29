package com.campus.signup.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>刻意<b>不</b>接收 role：注册一律创建 USER，ADMIN 只能由已有管理员或初始化脚本产生，
 * 否则任何人都能给自己开管理员。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名需为 4-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        String password,

        @Size(max = 50, message = "昵称最多 50 个字符")
        String nickname
) {
}
