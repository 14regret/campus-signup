package com.campus.signup.controller;

import com.campus.signup.common.PageResult;
import com.campus.signup.common.Result;
import com.campus.signup.dto.activity.ActivityDetail;
import com.campus.signup.dto.activity.ActivitySummary;
import com.campus.signup.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向普通用户的活动浏览接口（需要登录，但不需要 ADMIN）。
 *
 * <pre>
 * GET /api/activities          已发布活动列表，支持 keyword 搜索与分页
 * GET /api/activities/{id}     已发布活动详情；未发布一律 404
 * </pre>
 */
@Tag(name = "活动（学生）", description = "浏览已发布的活动")
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "活动列表", description = "只返回已发布的活动，支持关键词与分页")
    @GetMapping
    public Result<PageResult<ActivitySummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.listPublished(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetail> detail(@PathVariable Long id) {
        return Result.ok(activityService.getDetailForUser(id));
    }
}
