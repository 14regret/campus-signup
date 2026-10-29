package com.campus.signup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 操作日志。只增不改，没有 updated_at，也不建外键。
 *
 * <p>{@code detail} 用 MySQL 原生 JSON 列：Hibernate 6 里 {@code String} + {@code SqlTypes.JSON}
 * 表示「这一段字符串本身就是合法 JSON」，由调用方负责序列化（service 层统一用 ObjectMapper 写快照）。
 * 需要按内容检索时可直接 {@code JSON_EXTRACT(detail, '$.oldStatus')}。
 */
@Getter
@Setter
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 系统任务写 NULL，表示不是人触发的。 */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 冗余快照：用户改名或删号后，日志仍然能读懂是谁干的。 */
    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "json")
    private String detail;

    @Column(name = "ip", length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "OperationLog{id=" + id
                + ", operatorId=" + operatorId
                + ", action='" + action + '\''
                + ", targetType='" + targetType + '\''
                + ", targetId=" + targetId
                + '}';
    }
}
