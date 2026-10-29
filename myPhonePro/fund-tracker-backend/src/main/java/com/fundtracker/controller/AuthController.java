package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 本地开发一键登录开关：只在 start.bat 显式设置 AUTH_QUICK_LOGIN=true 时可用 */
    @Value("${app.auth.quick-login:false}")
    private boolean quickLoginEnabled;

    /** 本地开发一键登录：免去每次输账号密码。生产环境不开启，调用会报「接口不存在」 */
    @PostMapping("/dev-quick-login")
    public ApiResponse<LoginResp> devQuickLogin() {
        if (!quickLoginEnabled) {
            throw new com.fundtracker.exception.BusinessException(404, "接口不存在");
        }
        return ApiResponse.success(authService.quickLogin());
    }

    // 2026-10-29 移除 GET /api/auth/token：匿名可领 365 天应用级 Token（userId=null），且前端已无调用

    /** 发送验证码 */
    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@RequestBody SendCodeReq req) {
        authService.sendCode(req);
        return ApiResponse.success(null);
    }

    /** 验证码登录（首次登录自动注册） */
    @PostMapping("/login")
    public ApiResponse<LoginResp> loginByCode(@RequestBody LoginReq req) {
        return ApiResponse.success(authService.loginByCode(req));
    }

    /** 密码登录 */
    @PostMapping("/login-pwd")
    public ApiResponse<LoginResp> loginByPassword(@RequestBody LoginReq req) {
        return ApiResponse.success(authService.loginByPassword(req));
    }

    /** 密码注册（第一步：发送验证码到邮箱） */
    @PostMapping("/register-pwd")
    public ApiResponse<Void> registerByPassword(@RequestBody SendCodeReq req) {
        authService.registerByPassword(req);
        return ApiResponse.success(null);
    }

    /** 密码注册（第二步：验证码确认） */
    @PostMapping("/register-pwd-confirm")
    public ApiResponse<LoginResp> confirmRegisterByPassword(@RequestBody SetPasswordReq req) {
        return ApiResponse.success(authService.confirmRegisterByPassword(req));
    }

    /** 设置密码（已登录用户） */
    @PostMapping("/set-password")
    public ApiResponse<Void> setPassword(@RequestBody SetPasswordReq req, HttpServletRequest request) {
        // 必须登录态，且只能改自己（AuthInterceptor 已把本路径移出白名单）
        String userId = (String) request.getAttribute("userId");
        authService.setPassword(req, userId);
        return ApiResponse.success(null);
    }

    /** 获取当前用户信息（需 Bearer Token） */
    @GetMapping("/user-info")
    public ApiResponse<UserInfoResp> getUserInfo(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return ApiResponse.success(authService.getUserInfoByToken(token));
        }
        throw new com.fundtracker.exception.BusinessException(401, "未认证");
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ApiResponse.success(null);
    }
}
