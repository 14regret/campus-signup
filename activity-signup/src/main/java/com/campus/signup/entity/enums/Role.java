package com.campus.signup.entity.enums;

/**
 * 用户角色。权限模型刻意简化：
 * <ul>
 *   <li>{@code ADMIN}：创建 / 编辑 / 取消活动，确认 / 拒绝报名</li>
 *   <li>{@code USER}：报名、取消自己的报名</li>
 * </ul>
 *
 * <p>{@code activity.created_by} 只做审计记录，不参与数据权限隔离 ——
 * 任何 ADMIN 都可以管理所有活动。
 */
public enum Role {

    USER,
    ADMIN
}
