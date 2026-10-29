package com.fundtracker.config;

import com.fundtracker.model.entity.AuthToken;
import com.fundtracker.repository.AuthTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DataInitializer 启动安全清理测试（P0-7：种子逻辑已删、匿名 Token 启动即禁用）
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private AuthTokenRepository authTokenRepository;

    @Test
    @DisplayName("P0-7：无任何 Token 时不再自动创建默认 Token（种子逻辑已删）")
    void run_doesNotSeedToken() {
        when(authTokenRepository.findAll()).thenReturn(List.of());

        new DataInitializer(authTokenRepository).run();

        verify(authTokenRepository, never()).save(any(AuthToken.class));
    }

    @Test
    @DisplayName("P0-7：启动时禁用 userId=null 的匿名应用级 Token")
    void run_disablesAnonymousTokens() {
        AuthToken anonymous = AuthToken.builder()
                .token("CHANGE_ME").userId(null).active(true).build();
        when(authTokenRepository.findAll()).thenReturn(List.of(anonymous));

        new DataInitializer(authTokenRepository).run();

        verify(authTokenRepository).save(anonymous);
        org.junit.jupiter.api.Assertions.assertFalse(anonymous.isActive());
    }

    @Test
    @DisplayName("正常登录 Token（userId 非空）不受影响")
    void run_keepsUserTokens() {
        AuthToken userToken = AuthToken.builder()
                .token("valid-user-token").userId("u-1").active(true).build();
        when(authTokenRepository.findAll()).thenReturn(List.of(userToken));

        new DataInitializer(authTokenRepository).run();

        verify(authTokenRepository, never()).save(any(AuthToken.class));
        org.junit.jupiter.api.Assertions.assertTrue(userToken.isActive());
    }
}
