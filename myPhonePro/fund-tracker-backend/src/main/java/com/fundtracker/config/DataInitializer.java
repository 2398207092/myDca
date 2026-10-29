package com.fundtracker.config;

import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AuthTokenRepository authTokenRepository;

    @Override
    public void run(String... args) {
        // 安全加固（2026-08）：启动时禁用所有历史遗留的 dev-token- 弱前缀活跃 Token
        // 该弱 Token 曾因白名单接口长期处于激活态，构成"匿名可拖库"的关键一环
        List<AuthToken> all = authTokenRepository.findAll();
        boolean disabled = false;
        for (AuthToken t : all) {
            boolean weakLegacy = t.isActive() && t.getToken() != null && t.getToken().startsWith("dev-token-");
            // P0-7 修复：userId 为 null 的「应用级匿名 Token」一律禁用——它们曾因启动种子逻辑
            // 和已删除的 GET /api/auth/token 而存在，等价于公开口令（可读写全部非 admin 接口）
            boolean anonymous = t.isActive() && t.getUserId() == null;
            if (weakLegacy || anonymous) {
                t.setActive(false);
                authTokenRepository.save(t);
                disabled = true;
                log.info(">>> 禁用不安全 Token: {} ({})", t.getToken() != null && t.getToken().length() > 12
                        ? t.getToken().substring(0, 10) + "…" : "***", anonymous ? "匿名应用级" : "弱前缀");
            }
        }
        if (disabled) {
            log.info(">>> 启动安全清理完成");
        }

        // P0-7 修复：删除原「无活跃 Token 时自动创建默认 Token」的种子逻辑——
        // 它会在「清库 + 漏配 APP_AUTH_TOKEN」时种下值为 CHANGE_ME 的匿名 Token（等价公开口令）。
        // 现在获取 Token 的唯一途径是登录（验证码 / 密码 / 本地一键登录）。
    }
}
