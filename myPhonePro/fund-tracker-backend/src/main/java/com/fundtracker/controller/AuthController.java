package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 获取应用级 Token（兼容旧版，登录后不再需要） */
    @GetMapping("/token")
    public ApiResponse<TokenResp> getToken() {
        return ApiResponse.success(authService.getToken());
    }

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
    public ApiResponse<Void> setPassword(@RequestBody SetPasswordReq req) {
        authService.setPassword(req);
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
