package com.campus.signup.controller;

import com.campus.signup.common.Result;
import com.campus.signup.dto.auth.LoginRequest;
import com.campus.signup.dto.auth.LoginResponse;
import com.campus.signup.dto.auth.RegisterRequest;
import com.campus.signup.dto.auth.UserProfile;
import com.campus.signup.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <pre>
 * POST /api/auth/register  注册（固定 USER 角色）
 * POST /api/auth/login     登录，返回 JWT
 * </pre>
 */
@Tag(name = "认证", description = "注册与登录")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册", description = "固定创建 USER 角色，不接受前端传 role")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "登录", description = "成功后返回 JWT，前端存入 localStorage 并用于后续请求")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
