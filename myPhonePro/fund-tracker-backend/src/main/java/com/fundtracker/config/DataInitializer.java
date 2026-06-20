package com.fundtracker.config;

import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthTokenRepository authTokenRepository;

    @Value("${app.auth.token}")
    private String configuredToken;

    @Override
    public void run(String... args) {
        // 检查是否已有活跃 Token
        boolean hasActiveToken = authTokenRepository.findAll().stream()
                .anyMatch(AuthToken::isActive);

        if (!hasActiveToken) {
            AuthToken token = AuthToken.builder()
                    .token(configuredToken)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(365))
                    .active(true)
                    .build();
            authTokenRepository.save(token);
            System.out.println(">>> 已初始化默认 AuthToken: " + configuredToken);
        }
    }
}
