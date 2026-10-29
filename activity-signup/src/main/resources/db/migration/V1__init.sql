-- =====================================================================
-- V1__init.sql  校园活动报名系统 · 初始化表结构
--
-- 目标库   ：MySQL 8.0.16+（CHECK 约束需 8.0.16 及以上才真正生效）
-- 统一约定 ：InnoDB / utf8mb4 / DATETIME(3) / 主键 BIGINT 有符号自增 / 不建物理外键
-- 状态列   ：VARCHAR(20) 存枚举名，可读性优先，代价仅几十字节
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. activity 活动表
--    signed_up_count 是冗余计数，与 signup 明细在同一个事务里维护，
--    由定时任务按明细重算对账（见后续 SignupReconcileJob）。
-- ---------------------------------------------------------------------
CREATE TABLE `activity`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`           VARCHAR(120) NOT NULL COMMENT '活动标题',
    `description`     VARCHAR(2000) DEFAULT NULL COMMENT '活动描述',
    `capacity`        INT          NOT NULL COMMENT '名额上限',
    `signed_up_count` INT          NOT NULL DEFAULT 0 COMMENT '已占用名额（PENDING/CONFIRMED 计入）',
    `signup_start_at` DATETIME(3)  NOT NULL COMMENT '报名开始时间',
    `signup_end_at`   DATETIME(3)  NOT NULL COMMENT '报名截止时间',
    `start_at`        DATETIME(3)  NOT NULL COMMENT '活动开始时间',
    `end_at`          DATETIME(3)  NOT NULL COMMENT '活动结束时间',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CANCELLED/FINISHED',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（仅保护活动编辑）',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人 user.id',
    `deleted`         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '逻辑删除',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity_status_signup` (`status`, `signup_start_at`),
    KEY `idx_activity_created_by` (`created_by`),
    KEY `idx_activity_start_at` (`start_at`),
    CONSTRAINT `ck_activity_capacity` CHECK (`capacity` > 0),
    CONSTRAINT `ck_activity_count` CHECK (`signed_up_count` >= 0 AND `signed_up_count` <= `capacity`),
    CONSTRAINT `ck_activity_time` CHECK (`signup_start_at` < `signup_end_at`
        AND `signup_end_at` <= `start_at`
        AND `start_at` < `end_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动';

-- ---------------------------------------------------------------------
-- 2. signup 报名表
--    uk_signup_activity_user 是「防重复报名」的数据库级兜底：
--    并发下两个请求同时通过前面所有校验时，只有一条 INSERT 能成功。
--    注意：取消后重新报名不是 INSERT，而是复用同一行做状态复活，
--    否则会撞唯一索引（这是设计决策，不是 bug）。
-- ---------------------------------------------------------------------
CREATE TABLE `signup`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id` BIGINT      NOT NULL COMMENT '活动 ID',
    `user_id`     BIGINT      NOT NULL COMMENT '报名用户 ID',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/CANCELLED/REJECTED',
    `remark`      VARCHAR(255)         DEFAULT NULL COMMENT '备注 / 拒绝理由',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次报名时间',
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后状态变更时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_signup_activity_user` (`activity_id`, `user_id`),
    KEY `idx_signup_user_time` (`user_id`, `created_at`),
    KEY `idx_signup_activity_status` (`activity_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报名记录';

-- ---------------------------------------------------------------------
-- 3. sys_user 用户表
--    表名避开 MySQL 关键字 user；password 存 BCrypt 哈希（固定 60 字符）。
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50) NOT NULL COMMENT '登录名',
    `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `nickname`   VARCHAR(50)          DEFAULT NULL COMMENT '昵称',
    `role`       VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
    `enabled`    BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

-- ---------------------------------------------------------------------
-- 4. operation_log 操作日志表
--    只增不改：没有 updated_at，没有外键，写入失败不能影响主业务。
--    operator_name 是冗余快照，用户改名/删号后日志仍然可读。
-- ---------------------------------------------------------------------
CREATE TABLE `operation_log`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_id`   BIGINT               DEFAULT NULL COMMENT '操作人 user.id，系统任务为 NULL',
    `operator_name` VARCHAR(50)          DEFAULT NULL COMMENT '操作人快照',
    `action`        VARCHAR(40) NOT NULL COMMENT '动作，如 ACTIVITY_CREATE / SIGNUP_CONFIRM',
    `target_type`   VARCHAR(20) NOT NULL COMMENT 'ACTIVITY/SIGNUP/USER',
    `target_id`     BIGINT               DEFAULT NULL COMMENT '目标主键',
    `detail`        JSON                 DEFAULT NULL COMMENT '变更快照 / 参数摘要',
    `ip`            VARCHAR(45)          DEFAULT NULL COMMENT '客户端 IP，兼容 IPv6',
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_operator_time` (`operator_id`, `created_at`),
    KEY `idx_log_target` (`target_type`, `target_id`),
    KEY `idx_log_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作日志';
