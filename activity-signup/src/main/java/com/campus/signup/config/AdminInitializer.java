package com.campus.signup.config;

import com.campus.signup.entity.SysUser;
import com.campus.signup.entity.enums.Role;
import com.campus.signup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 启动时确保存在一个管理员账号。
 *
 * <p>行为：{@code app.admin.enabled=true} 且用户名不存在时，用 {@link PasswordEncoder} 现场加密明文密码再入库；
 * 已存在则什么都不做（<b>不会</b>覆盖已有密码，避免重启把线上改过的密码冲掉）。
 *
 * <p>为什么不用 Flyway 插入：BCrypt 哈希不该硬编码进 SQL；
 * 而且密码要能被配置项覆盖，脚本做不到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    /** 与 application.yml 的兜底值一致，仅用于告警判断。 */
    private static final String DEFAULT_PASSWORD = "admin12345";

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("管理员自动初始化已关闭（app.admin.enabled=false）");
            return;
        }

        String username = properties.username() == null ? "" : properties.username().trim();
        String password = properties.password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "app.admin.enabled=true 时必须配置 app.admin.username 与 app.admin.password");
        }
        if (password.length() < 8) {
            throw new IllegalStateException("app.admin.password 至少 8 位");
        }

        if (userRepository.existsByUsername(username)) {
            log.debug("管理员账号已存在，跳过初始化: {}", username);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setNickname(StringUtils.hasText(properties.nickname()) ? properties.nickname().trim() : "系统管理员");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        try {
            userRepository.saveAndFlush(admin);
            log.warn("已创建初始管理员账号 username={}，请登录后立即修改密码，并考虑关闭 app.admin.enabled", username);
            if (DEFAULT_PASSWORD.equals(password)) {
                log.warn("当前管理员使用的是配置中的默认密码，务必尽快更换");
            }
        } catch (DataIntegrityViolationException e) {
            // 多实例同时启动时只有一台能插进去，其余静默跳过即可
            log.info("管理员账号已由其他实例创建: {}", username);
        }
    }
}
