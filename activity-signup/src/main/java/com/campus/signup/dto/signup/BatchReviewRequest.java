package com.campus.signup.dto.signup;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量审核请求。
 *
 * @param signupIds 要处理的报名 id，一次最多 100 条（再多就该分批，避免长事务和大响应）
 * @param action    CONFIRM 或 REJECT
 */
public record BatchReviewRequest(
        @NotEmpty(message = "请选择要处理的报名")
        @Size(max = 100, message = "一次最多处理 100 条")
        List<Long> signupIds,

        @NotNull(message = "缺少审核动作")
        ReviewAction action
) {
}
