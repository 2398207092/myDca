package com.fundtracker.service;

import com.fundtracker.model.dto.TokenResp;
import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTokenRepository authTokenRepository;

    public TokenResp getToken() {
        // 查找是否有活跃的 Token
        AuthToken token = authTokenRepository.findAll().stream()
                .filter(AuthToken::isActive)
                .findFirst()
                .orElseGet(() -> {
                    AuthToken newToken = AuthToken.builder()
                            .id(UUID.randomUUID().toString())
                            .token("dev-token-" + UUID.randomUUID().toString().substring(0, 8))
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
}
