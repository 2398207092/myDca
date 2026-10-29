package com.fundtracker.service;

import com.fundtracker.model.dto.AccountDTO;
import com.fundtracker.model.entity.Account;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.AccountRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.ManualAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * AccountService 账户视图单元测试（P1-36：账户总额必须包含归属持仓市值）
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ManualAssetRepository manualAssetRepository;
    @Mock private HoldingRepository holdingRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, manualAssetRepository, holdingRepository);
    }

    @Test
    @DisplayName("listAccounts: totalValueCny = 手动资产 + 归属持仓市值，assetCount 两者相加")
    void listAccounts_includesHoldingValue() {
        Account account = Account.builder()
                .id("a-1").userId("u-1").name("理财账户")
                .purpose("invest").baseCurrency("CNY")
                .isDefault(true).sortOrder(0).deleted(false)
                .build();
        ManualAsset cash = ManualAsset.builder()
                .id("m-1").accountId("a-1").type("cash")
                .amount(new BigDecimal("100.00")).build();
        Holding holding = Holding.builder()
                .id("h-1").accountId("a-1").name("测试基金")
                .marketValue(new BigDecimal("250.50")).deleted(false).build();

        when(accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc("u-1"))
                .thenReturn(List.of(account));
        when(manualAssetRepository.findByUserIdOrderByCreatedAtDesc("u-1"))
                .thenReturn(List.of(cash));
        when(holdingRepository.findByUserIdAndDeletedFalse("u-1"))
                .thenReturn(List.of(holding));

        List<AccountDTO> result = accountService.listAccounts("u-1");

        assertEquals(1, result.size());
        // 100.00（手动现金） + 250.50（持仓市值） = 350.50；修复前只有 100.00
        assertEquals(0, new BigDecimal("350.50").compareTo(result.get(0).getTotalValueCny()));
        assertEquals(2, result.get(0).getAssetCount());
    }

    @Test
    @DisplayName("listAccounts: 无持仓的账户 totalValueCny 仍等于手动资产合计")
    void listAccounts_withoutHoldings_onlyManualAssets() {
        Account account = Account.builder()
                .id("a-1").userId("u-1").name("零钱")
                .purpose("daily").baseCurrency("CNY")
                .isDefault(true).sortOrder(0).deleted(false)
                .build();

        when(accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc("u-1"))
                .thenReturn(List.of(account));
        when(manualAssetRepository.findByUserIdOrderByCreatedAtDesc("u-1"))
                .thenReturn(List.of());
        when(holdingRepository.findByUserIdAndDeletedFalse("u-1"))
                .thenReturn(List.of());

        List<AccountDTO> result = accountService.listAccounts("u-1");

        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getTotalValueCny()));
        assertEquals(0, result.get(0).getAssetCount());
    }
}
