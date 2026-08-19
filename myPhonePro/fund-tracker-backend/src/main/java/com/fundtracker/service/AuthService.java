package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.model.entity.EmailVerificationCode;
import com.fundtracker.model.entity.User;
import com.fundtracker.repository.AuthTokenRepository;
import com.fundtracker.repository.EmailVerificationCodeRepository;
import com.fundtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final MailService mailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.auth.token:CHANGE_ME}")
    private String configuredToken;

    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 每邮箱每天最大发送次数 */
    private static final int DAILY_MAX_SEND = 5;
    /** Token 有效期（天） */
    private static final int TOKEN_EXPIRE_DAYS = 30;
    /** 密码登录失败最大次数 */
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    /** 超过最大次数后的锁定时长（分钟） */
    private static final long LOCK_MINUTES = 15;

    /** 密码登录失败计数（内存，email → 尝试记录） */
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    /** 密码登录失败尝试记录 */
    private static class LoginAttempt {
        int count;
        LocalDateTime lockUntil;
    }

    // ======================================================================
    // 应用级 Token（兼容旧版）
    // ======================================================================

    public TokenResp getToken() {
        AuthToken token = authTokenRepository.findAll().stream()
                .filter(t -> t.isActive() && t.getUserId() == null)
                .findFirst()
                .orElseGet(() -> {
                    String tokenValue = !configuredToken.equals("CHANGE_ME") && !configuredToken.isBlank()
                            ? configuredToken
                            : generateRandomToken("ftk_");
                    AuthToken newToken = AuthToken.builder()
                            .id(UUID.randomUUID().toString())
                            .token(tokenValue)
                            .createdAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusDays(365))
                            .active(true)
                            .build();
                    return authTokenRepository.save(newToken);
                });
        return TokenResp.builder().token(token.getToken()).build();
    }

    // ======================================================================
    // 发送验证码
    // ======================================================================

    @Transactional
    public void sendCode(SendCodeReq req) {
        String email = req.getEmail();
        String type = req.getType();

        if (email == null || email.isBlank()) {
            throw BusinessException.invalidParam("邮箱不能为空");
        }

        // 验证邮箱格式
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw BusinessException.invalidParam("邮箱格式不正确");
        }

        // 检查当天发送次数
        LocalDate today = LocalDate.now();
        long sentToday = verificationCodeRepository.countByEmailAndCreatedAtAfter(email, today.atStartOfDay());
        if (sentToday >= DAILY_MAX_SEND) {
            throw BusinessException.bizError("今日验证码发送已达上限（" + DAILY_MAX_SEND + "次），请明天再试");
        }

        // 生成验证码 + 保存
        String code = mailService.generateCode();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .email(email)
                .code(code)
                .type(type)
                .expireAt(LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES))
                .used(false)
                .build();
        verificationCodeRepository.save(vc);

        // 发送邮件
        mailService.sendVerificationCode(email, code, type);
    }

    // ======================================================================
    // 验证码登录（首次登录自动注册）
    // ======================================================================

    @Transactional
    public LoginResp loginByCode(LoginReq req) {
        String email = req.getEmail();
        String code = req.getCode();

        if (email == null || code == null) {
            throw BusinessException.invalidParam("邮箱和验证码不能为空");
        }

        // 校验验证码
        verifyCode(email, code, "login");

        // 查找或创建用户
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .hasPassword(false)
                    .build();
            return userRepository.save(newUser);
        });

        // 生成用户 Token
        String tokenValue = generateUserToken(user);

        return LoginResp.builder()
                .token(tokenValue)
                .email(user.getEmail())
                .hasPassword(user.isHasPassword())
                .build();
    }

    // ======================================================================
    // 密码登录
    // ======================================================================

    @Transactional
    public LoginResp loginByPassword(LoginReq req) {
        String email = req.getEmail();
        String password = req.getPassword();

        if (email == null || password == null) {
            throw BusinessException.invalidParam("邮箱和密码不能为空");
        }

        // 安全加固（2026-08）：密码登录限流，防暴力破解
        // 同一邮箱连续失败 5 次后锁定 15 分钟
        LoginAttempt attempt = loginAttempts.get(email);
        if (attempt != null && attempt.lockUntil != null && attempt.lockUntil.isAfter(LocalDateTime.now())) {
            long minutes = Duration.between(LocalDateTime.now(), attempt.lockUntil).toMinutes() + 1;
            throw BusinessException.bizError("登录失败次数过多，请 " + minutes + " 分钟后再试");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.bizError("该邮箱未注册，请先注册"));

        if (!user.isHasPassword() || user.getPasswordHash() == null) {
            throw BusinessException.bizError("该账号未设置密码，请使用验证码登录");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 密码错误：递增失败计数
            LoginAttempt a = loginAttempts.computeIfAbsent(email, k -> new LoginAttempt());
            a.count++;
            if (a.count >= MAX_LOGIN_ATTEMPTS) {
                a.lockUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
                a.count = 0;
                log.warn("账号 {} 密码连续失败 {} 次，锁定 {} 分钟", email, MAX_LOGIN_ATTEMPTS, LOCK_MINUTES);
            }
            throw BusinessException.bizError("密码错误");
        }

        // 登录成功：清除失败记录
        loginAttempts.remove(email);

        String tokenValue = generateUserToken(user);

        return LoginResp.builder()
                .token(tokenValue)
                .email(user.getEmail())
                .hasPassword(true)
                .build();
    }

    // ======================================================================
    // 密码注册（第一步：发验证码到邮箱）
    // ======================================================================

    @Transactional
    public void registerByPassword(SendCodeReq req) {
        String email = req.getEmail();

        if (email == null || email.isBlank()) {
            throw BusinessException.invalidParam("邮箱不能为空");
        }

        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw BusinessException.invalidParam("邮箱格式不正确");
        }

        // 检查邮箱是否已被注册
        if (userRepository.existsByEmail(email)) {
            throw BusinessException.bizError("该邮箱已注册，请直接登录");
        }

        // 发送验证码（type = register）
        SendCodeReq codeReq = new SendCodeReq();
        codeReq.setEmail(email);
        codeReq.setType("register");
        sendCode(codeReq);
    }

    // ======================================================================
    // 密码注册（第二步：验证码确认，创建账号）
    // ======================================================================

    @Transactional
    public LoginResp confirmRegisterByPassword(SetPasswordReq req) {
        String email = req.getEmail();
        String password = req.getPassword();
        String code = req.getCode();

        if (email == null || password == null || code == null) {
            throw BusinessException.invalidParam("邮箱、密码和验证码不能为空");
        }

        if (password.length() < 6 || password.length() > 20) {
            throw BusinessException.invalidParam("密码长度需为 6-20 位");
        }

        // 校验验证码
        verifyCode(email, code, "register");

        // 创建用户
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .hasPassword(true)
                .build();
        user = userRepository.save(user);

        String tokenValue = generateUserToken(user);

        return LoginResp.builder()
                .token(tokenValue)
                .email(user.getEmail())
                .hasPassword(true)
                .build();
    }

    // ======================================================================
    // 设置密码（已登录用户）
    // ======================================================================

    @Transactional
    public void setPassword(SetPasswordReq req) {
        String email = req.getEmail();
        String password = req.getPassword();

        if (email == null || password == null) {
            throw BusinessException.invalidParam("邮箱和密码不能为空");
        }

        if (password.length() < 6 || password.length() > 20) {
            throw BusinessException.invalidParam("密码长度需为 6-20 位");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.bizError("用户不存在"));

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setHasPassword(true);
        userRepository.save(user);
    }

    // ======================================================================
    // 获取当前用户信息
    // ======================================================================
    // 获取当前用户信息（通过 Token）
    // ======================================================================

    public UserInfoResp getUserInfoByToken(String token) {
        AuthToken authToken = authTokenRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() -> BusinessException.bizError("Token 无效或已过期"));

        if (authToken.getExpiresAt() != null && authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            authToken.setActive(false);
            authTokenRepository.save(authToken);
            throw BusinessException.bizError("Token 已过期");
        }

        if (authToken.getUserId() == null) {
            return UserInfoResp.builder()
                    .email("unknown")
                    .hasPassword(false)
                    .build();
        }

        return getUserInfo(authToken.getUserId());
    }

    public UserInfoResp getUserInfo(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.bizError("用户不存在"));
        return UserInfoResp.builder()
                .email(user.getEmail())
                .hasPassword(user.isHasPassword())
                .build();
    }

    // ======================================================================
    // 退出登录
    // ======================================================================

    @Transactional
    public void logout(String token) {
        authTokenRepository.findByTokenAndActiveTrue(token).ifPresent(t -> {
            t.setActive(false);
            authTokenRepository.save(t);
        });
    }

    // ======================================================================
    // 内部方法
    // ======================================================================

    /** 校验验证码 */
    private void verifyCode(String email, String code, String type) {
        EmailVerificationCode vc = verificationCodeRepository
                .findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(email, type)
                .orElseThrow(() -> BusinessException.bizError("验证码不存在或已使用，请重新发送"));

        if (vc.getExpireAt().isBefore(LocalDateTime.now())) {
            vc.setUsed(true);
            verificationCodeRepository.save(vc);
            throw BusinessException.bizError("验证码已过期，请重新发送");
        }

        if (!vc.getCode().equals(code)) {
            throw BusinessException.bizError("验证码错误");
        }

        // 标记为已使用
        vc.setUsed(true);
        verificationCodeRepository.save(vc);
    }

    /** 生成用户专属 Token */
    private String generateUserToken(User user) {
        String tokenValue = "u_" + generateRandomToken("");

        AuthToken authToken = AuthToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS))
                .active(true)
                .build();
        authTokenRepository.save(authToken);
        return tokenValue;
    }

    private String generateRandomToken(String prefix) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(prefix);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
