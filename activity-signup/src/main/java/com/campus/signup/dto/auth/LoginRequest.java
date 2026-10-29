package com.campus.signup.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>用户名不加格式约束：格式是注册时的事，登录只需要「非空」，
 * 否则将来放宽用户名规则时，老用户会连登录都过不了校验。
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名过长")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码过长")
        String password
) {
}
