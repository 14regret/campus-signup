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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 注册 / 登录的服务层单测。Repository 与 JWT 工具都用 Mock，
 * 密码编码器用真实 BCrypt（强度调到 4 只为让测试快，不影响断言的有效性）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    private static SysUser user(Long id, String username, String rawPassword, Role role, boolean enabled,
                                PasswordEncoder encoder) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        user.setNickname(username + "-nick");
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    @Test
    @DisplayName("注册成功：角色固定 USER，密码入库存的是 BCrypt 哈希")
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.saveAndFlush(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        UserProfile profile = authService.register(new RegisterRequest("alice", "password123", null));

        assertThat(profile.id()).isEqualTo(1L);
        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.role()).isEqualTo(Role.USER);
        // 昵称为空时回退成用户名
        assertThat(profile.nickname()).isEqualTo("alice");
    }

    @Test
    @DisplayName("注册：用户名已存在抛 USERNAME_EXISTS")
    void register_duplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "password123", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USERNAME_EXISTS);
    }

    @Test
    @DisplayName("注册：密码 UTF-8 超过 72 字节被拒绝（BCrypt 会静默截断）")
    void register_passwordTooLongInBytes() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        // 30 个汉字 = 90 字节，字符数没过 64，但字节数超了
        String chinesePassword = "密".repeat(30);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", chinesePassword, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    @Test
    @DisplayName("登录成功返回 token 与用户信息")
    void login_success() {
        SysUser alice = user(1L, "alice", "password123", Role.USER, true, passwordEncoder);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(jwtTokenProvider.generateToken(1L, "alice", Role.USER)).thenReturn("signed-token");
        when(jwtTokenProvider.getValiditySeconds()).thenReturn(7200L);

        LoginResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("登录：密码错误抛 LOGIN_FAILED")
    void login_wrongPassword() {
        SysUser alice = user(1L, "alice", "password123", Role.USER, true, passwordEncoder);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("登录：用户不存在与密码错误返回同一个错误码，避免暴露用户名是否注册")
    void login_userNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("登录：账号被禁用抛 USER_DISABLED")
    void login_disabledUser() {
        SysUser alice = user(1L, "alice", "password123", Role.USER, false, passwordEncoder);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_DISABLED);
    }
}
