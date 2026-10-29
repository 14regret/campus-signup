package com.campus.signup.service;

import com.campus.signup.dto.auth.LoginRequest;
import com.campus.signup.dto.auth.LoginResponse;
import com.campus.signup.dto.auth.RegisterRequest;
import com.campus.signup.dto.auth.UserProfile;
import com.campus.signup.entity.SysUser;
import com.campus.signup.entity.enums.Role;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import com.campus.signup.repository.UserRepository;
import com.campus.signup.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 注册与登录。
 *
 * <p>两个安全细节：
 * <ol>
 *   <li>注册接口永远只创建 {@link Role#USER}，不接受前端传角色；</li>
 *   <li>登录失败不区分「用户不存在」和「密码错误」，并且用户不存在时也跑一次 BCrypt 比对，
 *       避免通过响应时间反推用户名是否注册过。</li>
 * </ol>
 */
@Slf4j
@Service
public class AuthService {

    /**
     * BCrypt 只使用密码的前 72 字节，超长部分被静默截断。
     * 不拦住的话，「同一个密码 + 不同长后缀」会被当成同一个密码。
     */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /** 仅用于登录失败路径的等时比对，没有真实含义。 */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.dummyPasswordHash = passwordEncoder.encode("timing-attack-placeholder");
    }

    /**
     * 注册。角色固定 USER，昵称缺省回退成用户名。
     */
    @Transactional
    public UserProfile register(RegisterRequest request) {
        String username = request.username();

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        checkPasswordByteLength(request.password());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username);
        user.setRole(Role.USER);
        user.setEnabled(true);

        try {
            SysUser saved = userRepository.saveAndFlush(user);
            log.info("新用户注册 id={} username={}", saved.getId(), saved.getUsername());
            return UserProfile.from(saved);
        } catch (DataIntegrityViolationException e) {
            // existsByUsername 和 save 之间仍有并发窗口，最终由 uk_user_username 兜底
            log.warn("注册撞上唯一索引 username={}", username);
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
    }

    /**
     * 登录，成功返回 JWT。
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.username()).orElse(null);

        if (user == null) {
            // 等时处理：不让「用户不存在」比「密码错误」返回得更快
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功 id={} username={} role={}", user.getId(), user.getUsername(), user.getRole());
        return LoginResponse.of(token, jwtTokenProvider.getValiditySeconds(), UserProfile.from(user));
    }

    private static void checkPasswordByteLength(String rawPassword) {
        int bytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "密码过长：UTF-8 编码后最多 " + BCRYPT_MAX_PASSWORD_BYTES + " 字节，当前 " + bytes + " 字节");
        }
    }
}
