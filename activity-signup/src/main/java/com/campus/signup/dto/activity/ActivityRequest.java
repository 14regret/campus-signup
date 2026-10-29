package com.campus.signup.dto.activity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建 / 编辑活动的入参（两个场景共用一个结构，字段完全一致）。
 *
 * <p>字段级约束在这里，<b>跨字段的时间先后关系</b>（报名开始 < 截止 <= 活动开始 < 结束）
 * 放在 service 里校验，因为 Bean Validation 表达这个要写自定义注解，收益不大。
 */
public record ActivityRequest(

        @NotBlank(message = "活动标题不能为空")
        @Size(max = 120, message = "活动标题最多 120 个字符")
        String title,

        @Size(max = 2000, message = "活动描述最多 2000 个字符")
        String description,

        @NotNull(message = "名额不能为空")
        @Min(value = 1, message = "名额至少为 1")
        @Max(value = 100000, message = "名额最多 100000")
        Integer capacity,

        @NotNull(message = "报名开始时间不能为空")
        LocalDateTime signupStartAt,

        @NotNull(message = "报名截止时间不能为空")
        LocalDateTime signupEndAt,

        @NotNull(message = "活动开始时间不能为空")
        LocalDateTime startAt,

        @NotNull(message = "活动结束时间不能为空")
        LocalDateTime endAt
) {
}
