package com.fundtracker.service;

import com.fundtracker.model.dto.TokenResp;
import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTokenRepository authTokenRepository;

    @Value("${app.auth.token:CHANGE_ME}")
    private String configuredToken;

    public TokenResp getToken() {
        // 查找是否有活跃的 Token
        AuthToken token = authTokenRepository.findAll().stream()
                .filter(AuthToken::isActive)
                .findFirst()
                .orElseGet(() -> {
                    // 配置了真实 Token 才使用，否则生成一次性随机 Token（避免启动时无 Token 可用）
                    String tokenValue = !configuredToken.equals("CHANGE_ME") && !configuredToken.isBlank()
                            ? configuredToken
                            : generateRandomToken();
                    AuthToken newToken = AuthToken.builder()
                            .id(UUID.randomUUID().toString())
                            .token(tokenValue)
                            .createdAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusDays(365))
                            .active(true)
                            .build();
                    return authTokenRepository.save(newToken);
                });

        return TokenResp.builder()
                .token(token.getToken())
                .build();
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder("ftk_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
