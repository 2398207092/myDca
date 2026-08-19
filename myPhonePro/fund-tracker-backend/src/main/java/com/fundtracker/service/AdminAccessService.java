package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.entity.User;
import com.fundtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 管理员访问控制：数据库备份、审计日志、监控日志等系统级接口共用。
 * 仅允许配置邮箱（APP_ADMIN_EMAIL）对应的用户访问。
 * 未配置管理员时默认拒绝，确保安全开箱、无默认后门。
 */
@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private final UserRepository userRepository;

    @Value("${app.admin.email:CHANGE_ME}")
    private String adminEmail;

    /**
     * 校验当前用户是否为管理员，非管理员抛 403。
     *
     * @param userId 当前登录用户 ID（应用级 Token 时为 null）
     */
    public void check(String userId) {
        if (!isAdmin(userId)) {
            throw BusinessException.forbidden("无权访问该管理功能");
        }
    }

    /**
     * 判断是否为管理员。
     * 未配置 APP_ADMIN_EMAIL，或传入的应用级 Token（userId 为 null）一律返回 false。
     */
    public boolean isAdmin(String userId) {
        if (adminEmail == null || adminEmail.isBlank()
                || "CHANGE_ME".equalsIgnoreCase(adminEmail.trim())) {
            return false; // 未配置管理员 → 默认拒绝
        }
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Optional<User> user = userRepository.findById(userId);
        return user.isPresent() && adminEmail.trim().equalsIgnoreCase(user.get().getEmail());
    }
}