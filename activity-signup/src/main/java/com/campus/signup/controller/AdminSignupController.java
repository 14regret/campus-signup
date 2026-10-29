package com.campus.signup.controller;

import com.campus.signup.audit.OperationLog;
import com.campus.signup.common.Result;
import com.campus.signup.dto.signup.AdminSignupItem;
import com.campus.signup.dto.signup.BatchReviewRequest;
import com.campus.signup.dto.signup.BatchReviewResponse;
import com.campus.signup.service.AdminSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端报名审核接口。
 *
 * <pre>
 * GET  /api/admin/activities/{activityId}/signups   某活动的报名名单（含用户名）
 * POST /api/admin/signups/{signupId}/confirm        确认单条（PENDING → CONFIRMED）
 * POST /api/admin/signups/{signupId}/reject         拒绝单条（PENDING → REJECTED，退名额）
 * POST /api/admin/signups/{signupId}/revoke         撤销单条（CONFIRMED → CANCELLED，退名额）
 * POST /api/admin/signups/batch-review              批量确认 / 拒绝 / 撤销
 * </pre>
 *
 * <p>写操作全部带 {@code @OperationLog}，由切面异步旁路记录到 operation_log。
 */
@Tag(name = "报名审核（管理员）", description = "查看名单、确认、拒绝、撤销")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSignupController {

    private final AdminSignupService adminSignupService;

    @GetMapping("/activities/{activityId}/signups")
    public Result<List<AdminSignupItem>> list(@PathVariable Long activityId) {
        return Result.ok(adminSignupService.listActivitySignups(activityId));
    }

    @OperationLog(action = "SIGNUP_CONFIRM", targetType = "SIGNUP", targetId = "#signupId")
    @PostMapping("/signups/{signupId}/confirm")
    public Result<Void> confirm(@PathVariable Long signupId) {
        adminSignupService.confirm(signupId);
        return Result.ok();
    }

    @OperationLog(action = "SIGNUP_REJECT", targetType = "SIGNUP", targetId = "#signupId")
    @PostMapping("/signups/{signupId}/reject")
    public Result<Void> reject(@PathVariable Long signupId) {
        adminSignupService.reject(signupId);
        return Result.ok();
    }

    @Operation(summary = "撤销已确认报名", description = "CONFIRMED → CANCELLED，并退还名额")
    @OperationLog(action = "SIGNUP_REVOKE", targetType = "SIGNUP", targetId = "#signupId")
    @PostMapping("/signups/{signupId}/revoke")
    public Result<Void> revoke(@PathVariable Long signupId) {
        adminSignupService.revoke(signupId);
        return Result.ok();
    }

    /**
     * 批量审核。targetId 留空——一次动的是多条，具体 id 在 detail 里，
     * 单条级审计请查每条单独调用的记录。
     */
    @OperationLog(action = "SIGNUP_BATCH_REVIEW", targetType = "SIGNUP")
    @PostMapping("/signups/batch-review")
    public Result<BatchReviewResponse> batchReview(@Valid @RequestBody BatchReviewRequest request) {
        return Result.ok(adminSignupService.reviewBatch(request));
    }
}
