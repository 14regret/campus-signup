package com.campus.signup.controller;

import com.campus.signup.audit.OperationLog;
import com.campus.signup.common.PageResult;
import com.campus.signup.common.Result;
import com.campus.signup.dto.activity.ActivityDetail;
import com.campus.signup.dto.activity.ActivityRequest;
import com.campus.signup.dto.activity.ActivitySummary;
import com.campus.signup.entity.enums.ActivityStatus;
import com.campus.signup.security.CurrentUser;
import com.campus.signup.security.JwtPayload;
import com.campus.signup.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动管理接口，仅 ADMIN 可用。
 *
 * <pre>
 * POST   /api/admin/activities            创建（草稿）
 * PUT    /api/admin/activities/{id}       编辑（仅草稿）
 * POST   /api/admin/activities/{id}/publish   发布
 * POST   /api/admin/activities/{id}/cancel    取消
 * GET    /api/admin/activities            列表（全状态）
 * GET    /api/admin/activities/{id}       详情
 * </pre>
 *
 * <p>权限有两道：SecurityConfig 里的 {@code /api/admin/** -> hasRole(ADMIN)} 是主防线，
 * 这里的 {@code @PreAuthorize} 是防止将来接口挪了路径、URL 规则失效。
 */
@Tag(name = "活动管理（管理员）", description = "创建、编辑、发布、取消活动")
@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminActivityController {

    private final ActivityService activityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ActivityDetail> create(@Valid @RequestBody ActivityRequest request,
                                         @CurrentUser JwtPayload admin) {
        return Result.ok(activityService.create(request, admin.userId()));
    }

    @PutMapping("/{id}")
    public Result<ActivityDetail> update(@PathVariable Long id,
                                         @Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityService.update(id, request));
    }

    @OperationLog(action = "ACTIVITY_PUBLISH", targetType = "ACTIVITY", targetId = "#id")
    @PostMapping("/{id}/publish")
    public Result<ActivityDetail> publish(@PathVariable Long id) {
        return Result.ok(activityService.publish(id));
    }

    @OperationLog(action = "ACTIVITY_CANCEL", targetType = "ACTIVITY", targetId = "#id")
    @PostMapping("/{id}/cancel")
    public Result<ActivityDetail> cancel(@PathVariable Long id) {
        return Result.ok(activityService.cancel(id));
    }

    @GetMapping
    public Result<PageResult<ActivitySummary>> list(
            @RequestParam(required = false) ActivityStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.listForAdmin(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetail> detail(@PathVariable Long id) {
        return Result.ok(activityService.getDetailForAdmin(id));
    }
}
