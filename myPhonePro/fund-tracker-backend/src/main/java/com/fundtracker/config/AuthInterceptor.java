package com.fundtracker.config;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthTokenRepository authTokenRepository;

    public AuthInterceptor(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<?> resp = ApiResponse.error(401, message);
        response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resp));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // /api/auth/* 无需认证（登录、注册、发验证码等）；但改密必须登录态
        // 2026-10-29 修账号接管洞：此前整段放行，任何人知道邮箱即可改密码
        if (path.startsWith("/api/auth/") && !path.startsWith("/api/auth/set-password")) {
            return true;
        }

        // 安全加固（2026-08）：移除 /api/funds/* 放行白名单。
        // 原白名单导致未认证用户可触发全用户爬虫、删除分红记录，
        // 前端所有请求均已统一携带 Bearer Token，此处收紧为必须认证。

        // 从 Header 中获取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "未认证，请携带 Authorization: Bearer <token> 请求头");
            return false;
        }

        String token = authHeader.substring(7);
        Optional<AuthToken> authToken = authTokenRepository.findByTokenAndActiveTrue(token);

        if (authToken.isEmpty()) {
            sendUnauthorized(response, "Token 无效或已过期");
            return false;
        }

        AuthToken validToken = authToken.get();

        // 检查是否过期
        if (validToken.getExpiresAt() != null && validToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            validToken.setActive(false);
            authTokenRepository.save(validToken);
            sendUnauthorized(response, "Token 已过期，请重新登录");
            return false;
        }

        // P0-7 修复：拒绝 userId 为 null 的「应用级匿名 Token」——它们不对应任何登录用户，
        // 历史上等价于公开口令；现在 Token 必须由登录产生
        if (validToken.getUserId() == null) {
            sendUnauthorized(response, "无效 Token：请使用登录获取的 Token 重新认证");
            return false;
        }

        // 将 userId 设置到请求属性中供 Controller 使用
        request.setAttribute("userId", validToken.getUserId());

        return true;
    }
}
