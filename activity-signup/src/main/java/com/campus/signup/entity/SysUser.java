package com.campus.signup.entity;

import com.campus.signup.entity.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * 用户。
 *
 * <p>表名用 {@code sys_user} 而不是 {@code user}：后者是 MySQL 关键字，
 * 到处要反引号，Hibernate 方言和多数据源场景下容易踩坑。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class SysUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50, updatable = false)
    private String username;

    /** BCrypt 哈希，固定 60 字符；列宽留到 100 以便将来换强度或换算法。 */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    /** 禁用而不是删除：活动创建人、操作日志都引用了 user.id，物理删除会让历史数据悬空。 */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SysUser other)) {
            return false;
        }
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return SysUser.class.hashCode();
    }

    /** 刻意不打印 password，避免哈希出现在日志里。 */
    @Override
    public String toString() {
        return "SysUser{id=" + id
                + ", username='" + username + '\''
                + ", nickname='" + nickname + '\''
                + ", role=" + role
                + ", enabled=" + enabled
                + '}';
    }
}
