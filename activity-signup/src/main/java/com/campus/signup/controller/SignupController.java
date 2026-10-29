package com.campus.signup.controller;

import com.campus.signup.common.Result;
import com.campus.signup.dto.signup.MySignupResponse;
import com.campus.signup.dto.signup.SignupResponse;
import com.campus.signup.security.CurrentUser;
import com.campus.signup.security.JwtPayload;
import com.campus.signup.service.SignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报名接口（登录用户可用）。
 *
 * <pre>
 * GET  /api/signups/my             我的报名记录（前端据此渲染「已报名」按钮状态）
 * POST /api/signups/{activityId}   报名
 * PUT  /api/signups/{id}/cancel    取消自己的报名（id 是报名记录 id，不是活动 id）
 * </pre>
 *
 * <p>三个接口都不接收「用户 id」参数，一律从 token 里取，
 * 否则前端传别人的 id 就能替别人报名/取消。
 *
 * <p>{@code /my} 是字面量路径，优先级高于 {@code /{activityId}}，不会互相抢路由。
 */
@Tag(name = "报名（学生）", description = "报名、取消报名、我的报名")
@RestController
@RequestMapping("/api/signups")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @Operation(summary = "我的报名", description = "返回当前用户全部报名记录，含活动标题与时间")
    @GetMapping("/my")
    public Result<List<MySignupResponse>> mySignups(@CurrentUser JwtPayload user) {
        return Result.ok(signupService.listMySignups(user.userId()));
    }

    @Operation(summary = "报名", description = "占名额并生成待确认记录；满员返回 4001，重复报名返回 4002")
    @PostMapping("/{activityId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<SignupResponse> signUp(@PathVariable Long activityId, @CurrentUser JwtPayload user) {
        return Result.ok(signupService.signUp(activityId, user.userId()));
    }

    @PutMapping("/{id}/cancel")
    public Result<SignupResponse> cancel(@PathVariable Long id, @CurrentUser JwtPayload user) {
        return Result.ok(signupService.cancel(id, user.userId()));
    }
}
