package com.campus.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 初始管理员账号配置，对应 {@code app.admin.*}。
 *
 * <p>只在「账号不存在」时创建一次，之后改配置不会覆盖已有密码。
 * 字段刻意不加 {@code @NotBlank}：{@code enabled=false} 时允许留空，
 * 真正的必填校验在 {@code AdminInitializer} 里按开关判断。
 *
 * @param enabled  是否启用自动初始化，生产环境建议首次部署后关掉
 * @param username 管理员用户名
 * @param password 明文密码，启动时用 BCrypt 加密后入库，库里不存明文
 * @param nickname 昵称
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        @DefaultValue("true") boolean enabled,
        String username,
        String password,
        String nickname
) {
}
