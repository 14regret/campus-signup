package com.campus.signup.dto.signup;

/**
 * 批量审核结果。
 *
 * <p>不用「全部成功 / 全部失败」：一条报名可能刚被用户自己取消，
 * 批量操作里出现个别失败是正常的，前端应该如实告诉管理员处理了几条。
 */
public record BatchReviewResponse(int success, int failed) {
}
