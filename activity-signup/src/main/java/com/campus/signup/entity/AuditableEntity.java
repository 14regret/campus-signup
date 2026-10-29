package com.campus.signup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 审计字段基类。时间由 Hibernate 在 Java 侧赋值（{@code @CreationTimestamp} /
 * {@code @UpdateTimestamp}），不依赖数据库默认值 ——
 * Hibernate 的 INSERT 会显式带上所有列，塞 null 进去会直接撞 NOT NULL。
 * 库里的 {@code DEFAULT CURRENT_TIMESTAMP(3)} 只是给手工 SQL 兜底。
 */
@Getter
@MappedSuperclass
public abstract class AuditableEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
