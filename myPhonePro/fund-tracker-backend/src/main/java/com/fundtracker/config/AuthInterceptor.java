package com.fundtracker.config;

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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // /api/auth/* 无需认证
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            return true;
        }

        // /api/funds/* 无需认证（分红数据刷新接口）
        if (path.startsWith("/api/funds/")) {
            return true;
        }

        // /api/holdings/dividend-info 无需认证（添加持仓时查询分红信息）
        if (path.startsWith("/api/holdings/dividend-info")) {
            return true;
        }

        // 从 Header 中获取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未认证，请携带 Authorization: Bearer <token> 请求头\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);
        Optional<AuthToken> authToken = authTokenRepository.findByTokenAndActiveTrue(token);

        if (authToken.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\",\"data\":null}");
            return false;
        }

        // 检查是否过期
        if (authToken.get().getExpiresAt() != null && authToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 已过期\",\"data\":null}");
            return false;
        }

        return true;
    }
}
