package com.fundtracker.service;

import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.AccountRepository;
import com.fundtracker.repository.ManualAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ManualAssetService.adjustCash 单元测试
 * 用 Mockito 模拟数据库层
 */
@ExtendWith(MockitoExtension.class)
class ManualAssetServiceTest {

    @Mock
    private ManualAssetRepository manualAssetRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private com.fundtracker.service.ExchangeRateService exchangeRateService;

    private ManualAssetService service;

    @Captor
    private ArgumentCaptor<ManualAsset> assetCaptor;

    @BeforeEach
    void setUp() {
        service = new ManualAssetService(manualAssetRepository, accountRepository, accountService, exchangeRateService);
    }

    @Test
    @DisplayName("无现金资产时抛出明确异常（V-003：不再静默跳过导致账实脱节）")
    void adjustCash_noAsset() {
        when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of());

        // 旧语义是「跳过不抛」——那会让交易入账而现金不动（账实脱节），已改为明确报错
        assertThrows(com.fundtracker.exception.BusinessException.class,
                () -> service.adjustCash("holding-1", new BigDecimal("-5000"), "user-1"));
    }

    @Test
    @DisplayName("有主账户时操作主账户")
    void adjustCash_primaryFirst() {
        ManualAsset primary = ManualAsset.builder()
                .id("primary")
                .name("活期存款")
                .type("cash")
                .amount(new BigDecimal("50000"))
                .isPrimary(true)
                .build();
        ManualAsset secondary = ManualAsset.builder()
                .id("secondary")
                .name("备用金")
                .type("cash")
                .amount(new BigDecimal("10000"))
                .isPrimary(false)
                .build();

        when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of(secondary, primary));

        service.adjustCash("holding-1", new BigDecimal("-5000"), "user-1");

        verify(manualAssetRepository).save(assetCaptor.capture());
        ManualAsset saved = assetCaptor.getValue();
        assertEquals("primary", saved.getId());
        assertEquals(new BigDecimal("45000"), saved.getAmount());
    }

    @Test
    @DisplayName("没有主账户时操作第一个现金资产")
    void adjustCash_fallbackToFirst() {
        ManualAsset first = ManualAsset.builder()
                .id("first")
                .name("活期存款")
                .type("cash")
                .amount(new BigDecimal("50000"))
                .isPrimary(false)
                .build();
        ManualAsset second = ManualAsset.builder()
                .id("second")
                .name("备用金")
                .type("cash")
                .amount(new BigDecimal("10000"))
                .isPrimary(false)
                .build();

        when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of(first, second));

        service.adjustCash("holding-1", new BigDecimal("-5000"), "user-1");

        verify(manualAssetRepository).save(assetCaptor.capture());
        ManualAsset saved = assetCaptor.getValue();
        assertEquals("first", saved.getId());
        assertEquals(new BigDecimal("45000"), saved.getAmount());
    }

    @Test
    @DisplayName("扣减后余额可以为负")
    void adjustCash_negativeAllowed() {
        ManualAsset asset = ManualAsset.builder()
                .id("cash-1")
                .name("活期存款")
                .type("cash")
                .amount(new BigDecimal("100"))
                .isPrimary(true)
                .build();

        when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of(asset));

        service.adjustCash("holding-1", new BigDecimal("-200"), "user-1");

        verify(manualAssetRepository).save(assetCaptor.capture());
        assertEquals(new BigDecimal("-100"), assetCaptor.getValue().getAmount());
    }

    @Test
    @DisplayName("增加现金: 余额增加")
    void adjustCash_add() {
        ManualAsset asset = ManualAsset.builder()
                .id("cash-1")
                .name("活期存款")
                .type("cash")
                .amount(new BigDecimal("50000"))
                .isPrimary(true)
                .build();

        when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of(asset));

        service.adjustCash("holding-1", new BigDecimal("10000"), "user-1");

        verify(manualAssetRepository).save(assetCaptor.capture());
        assertEquals(new BigDecimal("60000"), assetCaptor.getValue().getAmount());
    }
}
