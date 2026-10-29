package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.LoginReq;
import com.fundtracker.model.entity.EmailVerificationCode;
import com.fundtracker.model.entity.User;
import com.fundtracker.repository.AuthTokenRepository;
import com.fundtracker.repository.EmailVerificationCodeRepository;
import com.fundtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试：P1-31 验证码限流 + P2-22 登录文案统一
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private EmailVerificationCodeRepository verificationCodeRepository;
    @Mock private MailService mailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, authTokenRepository,
                verificationCodeRepository, mailService);
    }

    private EmailVerificationCode liveCode(String code) {
        return EmailVerificationCode.builder()
                .email("user@test.com")
                .code(code)
                .type("login")
                .expireAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
    }

    @Test
    @DisplayName("验证码连续错 5 次 → 验证码作废，第 6 次即使码对也被拒（P1-31）")
    void loginByCode_fiveWrongAttemptsInvalidateCode() {
        EmailVerificationCode vc = liveCode("123456");
        // 前 5 次返回该验证码（每次输入都是错的）；第 6 次模拟库中已无未使用验证码
        when(verificationCodeRepository.findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc("user@test.com", "login"))
                .thenReturn(Optional.of(vc), Optional.of(vc), Optional.of(vc),
                        Optional.of(vc), Optional.of(vc), Optional.empty());

        LoginReq wrong = new LoginReq();
        wrong.setEmail("user@test.com");
        wrong.setCode("000000");
        for (int i = 1; i <= 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginByCode(wrong), "第 " + i + " 次错误尝试应被拒绝");
            assertEquals("验证码错误", ex.getMessage());
        }

        // 第 5 次失败时验证码已被作废
        assertTrue(vc.isUsed(), "连续错 5 次后验证码必须被作废");
        verify(verificationCodeRepository).save(vc);

        // 第 6 次：即使输入正确的码也被拒（码已作废，需重新发送）
        LoginReq correct = new LoginReq();
        correct.setEmail("user@test.com");
        correct.setCode("123456");
        BusinessException ex6 = assertThrows(BusinessException.class,
                () -> authService.loginByCode(correct));
        assertEquals("验证码不存在或已使用，请重新发送", ex6.getMessage());

        // 全程未发出任何 token（未登入任何账号）
        verify(authTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("验证码一次试对 → 计数清零，正常登录（P1-31 误伤回归）")
    void loginByCode_correctFirstAttemptSucceeds() {
        EmailVerificationCode vc = liveCode("123456");
        when(verificationCodeRepository.findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc("user@test.com", "login"))
                .thenReturn(Optional.of(vc));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(authTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginReq req = new LoginReq();
        req.setEmail("user@test.com");
        req.setCode("123456");

        var resp = authService.loginByCode(req);

        assertNotNull(resp.getToken());
        assertTrue(vc.isUsed(), "成功的验证码应被标记已使用");
    }

    @Test
    @DisplayName("邮箱未注册 → 文案统一为「邮箱或密码错误」，不可枚举邮箱（P2-22）")
    void loginByPassword_unknownEmail_genericMessage() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        LoginReq req = new LoginReq();
        req.setEmail("nobody@test.com");
        req.setPassword("whatever");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.loginByPassword(req));

        assertEquals("邮箱或密码错误", ex.getMessage());
        assertFalse(ex.getMessage().contains("未注册"), "文案不得暴露邮箱注册状态");
    }

    @Test
    @DisplayName("密码错误 → 同样提示「邮箱或密码错误」（P2-22）")
    void loginByPassword_wrongPassword_sameGenericMessage() {
        User user = User.builder()
                .email("user@test.com")
                .passwordHash(new BCryptPasswordEncoder().encode("right-password"))
                .hasPassword(true)
                .build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        LoginReq req = new LoginReq();
        req.setEmail("user@test.com");
        req.setPassword("wrong-password");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.loginByPassword(req));

        assertEquals("邮箱或密码错误", ex.getMessage());
    }
}
