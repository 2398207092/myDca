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

    @Value("${app.auth.token}")
    private String configuredToken;

    @Override
    public void run(String... args) {
        // 安全加固（2026-08）：启动时禁用所有历史遗留的 dev-token- 弱前缀活跃 Token
        // 该弱 Token 曾因白名单接口长期处于激活态，构成"匿名可拖库"的关键一环
        List<AuthToken> all = authTokenRepository.findAll();
        boolean disabledWeak = false;
        for (AuthToken t : all) {
            if (t.isActive() && t.getToken() != null && t.getToken().startsWith("dev-token-")) {
                t.setActive(false);
                authTokenRepository.save(t);
                disabledWeak = true;
            }
        }
        if (disabledWeak) {
            log.info(">>> 已禁用历史遗留弱 Token (dev-token-*)");
        }

        // 检查是否已有活跃 Token
        boolean hasActiveToken = all.stream().anyMatch(AuthToken::isActive);

        if (!hasActiveToken) {
            AuthToken token = AuthToken.builder()
                    .token(configuredToken)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(365))
                    .active(true)
                    .build();
            authTokenRepository.save(token);
            log.info(">>> 已初始化默认 AuthToken (length={})", configuredToken.length());
        }
    }
}
