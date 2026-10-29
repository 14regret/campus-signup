package com.campus.signup.dto.signup;

/** 批量审核的动作。 */
public enum ReviewAction {
    /** 待确认 → 已确认 */
    CONFIRM,
    /** 待确认 → 已拒绝（退还名额） */
    REJECT,
    /** 已确认 → 已取消（管理员强制退名额） */
    REVOKE
}
