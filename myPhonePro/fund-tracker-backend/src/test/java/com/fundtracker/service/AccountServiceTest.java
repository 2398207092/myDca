package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.AccountDTO;
import com.fundtracker.model.dto.UpdateAccountReq;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    // ===== P1-29：账户归属校验必须查属主 =====

    @Test
    @DisplayName("matchesAssetTypeForUser: 他人账户被拒（P1-29 核心场景）")
    void matchesAssetTypeForUser_rejectsForeignAccount() {
        // 账户属于 u-2，u-1 来匹配 → 查不到 → false
        when(accountRepository.findByIdAndUserIdAndDeletedFalse("acc-other", "u-1"))
                .thenReturn(Optional.empty());

        assertFalse(accountService.matchesAssetTypeForUser("u-1", "acc-other", false),
                "他人账户必须被判为不匹配，拒绝挂载");
    }

    @Test
    @DisplayName("matchesAssetTypeForUser: 自己的账户正常匹配（USDT 账户只收 crypto）")
    void matchesAssetTypeForUser_ownAccountMatches() {
        Account acc = Account.builder()
                .id("acc-1").userId("u-1").name("交易所")
                .purpose("exchange").baseCurrency("USDT").deleted(false)
                .build();
        when(accountRepository.findByIdAndUserIdAndDeletedFalse("acc-1", "u-1"))
                .thenReturn(Optional.of(acc));

        assertTrue(accountService.matchesAssetTypeForUser("u-1", "acc-1", true));
        assertFalse(accountService.matchesAssetTypeForUser("u-1", "acc-1", false));
    }

    @Test
    @DisplayName("matchesAssetType: 旧接口委托新重载（null userId 跳过归属校验，保持兼容）")
    void matchesAssetType_delegatesWithoutOwnershipCheck() {
        Account acc = Account.builder()
                .id("acc-1").userId("u-2").baseCurrency("CNY").deleted(false)
                .build();
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // 旧接口不传 userId，不做归属校验，仅做本位币匹配
        assertTrue(accountService.matchesAssetType("acc-1", false));
    }

    // ===== P1-30：本位币修改守卫必须可达 =====

    @Test
    @DisplayName("updateAccount: CNY 账户改 USDT 被拒（P1-30 守卫不可再是死代码）")
    void updateAccount_currencyChangeRejected() {
        Account acc = Account.builder()
                .id("acc-1").userId("u-1").name("理财账户")
                .purpose("invest").baseCurrency("CNY")
                .isDefault(true).sortOrder(0).deleted(false)
                .build();
        when(accountRepository.findByIdAndUserIdAndDeletedFalse("acc-1", "u-1"))
                .thenReturn(Optional.of(acc));

        UpdateAccountReq req = new UpdateAccountReq();
        req.setBaseCurrency("USDT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.updateAccount("acc-1", req, "u-1"));
        assertTrue(ex.getMessage().contains("本位币不可直接修改"),
                "应抛本位币守卫错误，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("updateAccount: exchange 用途必须 USDT，联动在 setter 之前校验")
    void updateAccount_exchangeRequiresUsdt() {
        Account acc = Account.builder()
                .id("acc-1").userId("u-1").name("理财账户")
                .purpose("invest").baseCurrency("CNY")
                .isDefault(true).sortOrder(0).deleted(false)
                .build();
        when(accountRepository.findByIdAndUserIdAndDeletedFalse("acc-1", "u-1"))
                .thenReturn(Optional.of(acc));

        UpdateAccountReq req = new UpdateAccountReq();
        req.setPurpose("exchange"); // 本位币不传，保持 CNY

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.updateAccount("acc-1", req, "u-1"));
        assertTrue(ex.getMessage().contains("USDT"),
                "交易所用途必须联动 USDT 校验，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("updateAccount: 不改本位币的正常更新不受守卫影响")
    void updateAccount_normalUpdateStillWorks() {
        Account acc = Account.builder()
                .id("acc-1").userId("u-1").name("理财账户")
                .purpose("invest").baseCurrency("CNY")
                .isDefault(true).sortOrder(0).deleted(false)
                .build();
        when(accountRepository.findByIdAndUserIdAndDeletedFalse("acc-1", "u-1"))
                .thenReturn(Optional.of(acc));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(manualAssetRepository.findByUserIdOrderByCreatedAtDesc("u-1")).thenReturn(List.of());
        when(holdingRepository.findByUserIdAndDeletedFalse("u-1")).thenReturn(List.of());

        UpdateAccountReq req = new UpdateAccountReq();
        req.setName("新名字");

        AccountDTO result = accountService.updateAccount("acc-1", req, "u-1");
        assertEquals("新名字", result.getName());
        assertEquals("CNY", result.getBaseCurrency());
    }
}
