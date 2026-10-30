package com.fundtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.model.dto.AssetOverviewDTO;
import com.fundtracker.model.entity.AssetSnapshot;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.AssetSnapshotRepository;
import com.fundtracker.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AssetOverviewService 单元测试
 * 重点验证 #12：weeklyChange 对比 7 天前快照、monthlyChange 对比 30 天前快照
 */
@ExtendWith(MockitoExtension.class)
class AssetOverviewServiceTest {

    @Mock private HoldingRepository holdingRepository;
    @Mock private ManualAssetService manualAssetService;
    @Mock private AssetSnapshotRepository assetSnapshotRepository;

    private AssetOverviewService assetOverviewService;

    @BeforeEach
    void setUp() {
        assetOverviewService = new AssetOverviewService(
                holdingRepository, manualAssetService, assetSnapshotRepository, new ObjectMapper());
        // 稳定币口径（2026-10-30）引入的新依赖：默认无稳定币，个别用例按需覆盖
        org.mockito.Mockito.lenient()
                .when(manualAssetService.getStablecoinTotal(anyString()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Nested
    @DisplayName("加密币计入总资产")
    class CryptoIncluded {

        @Test
        @DisplayName("assetCategory=crypto 的持仓必须计入 cryptoValue 与 totalValue")
        void cryptoHoldingsCounted() {
            // 手动资产 1000 + 持仓市值 6000 = 7000
            when(manualAssetService.getTotalByType(eq("cash"), eq("u1"))).thenReturn(BigDecimal.ZERO);
            when(manualAssetService.getTotalByType(eq("crypto"), eq("u1"))).thenReturn(new BigDecimal("1000"));
            when(manualAssetService.listManualAssets("u1")).thenReturn(List.of());

            Holding btc = Holding.builder()
                    .id("h1").name("比特币").code("BTC")
                    .type(HoldingType.crypto).costAlgorithm(CostAlgorithm.diluted)
                    .shares(new BigDecimal("0.01145646"))
                    .costPerShare(new BigDecimal("542060.10"))
                    .cost(new BigDecimal("6210.09"))
                    .marketValue(new BigDecimal("6000"))
                    .predictedDividend(BigDecimal.ZERO)
                    .dividendRate(BigDecimal.ZERO)
                    .priceDividendRate(BigDecimal.ZERO)
                    .totalDividendReceived(BigDecimal.ZERO)
                    .netInvestment(new BigDecimal("6210.09"))
                    .dividendRecoveryRate(BigDecimal.ZERO)
                    .estimatedRecoveryYears(BigDecimal.ZERO)
                    .reinvestRecoveryYears(BigDecimal.ZERO)
                    .color("#f7931a")
                    .assetCategory("crypto")
                    .deleted(false)
                    .build();

            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse("u1", "crypto"))
                    .thenReturn(List.of(btc));
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse("u1", "us_stock")).thenReturn(List.of());
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse("u1", "gold")).thenReturn(List.of());
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse("u1", "dividend")).thenReturn(List.of());
            when(assetSnapshotRepository.findTopByUserIdAndDateLessThanEqualOrderByDateDesc(eq("u1"), any()))
                    .thenReturn(Optional.empty());

            AssetOverviewDTO dto = assetOverviewService.getOverview("u1");

            assertEquals(0, new BigDecimal("7000").compareTo(dto.getCryptoValue()),
                    "cryptoValue 应包含 holdings，实际：" + dto.getCryptoValue());
            assertEquals(0, new BigDecimal("7000").compareTo(dto.getTotalValue()),
                    "totalValue 应包含加密持仓，实际：" + dto.getTotalValue());

            AssetOverviewDTO.CategoryDetail crypto = dto.getCategories().stream()
                    .filter(c -> "crypto".equals(c.getType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("缺少 crypto 分类"));
            assertTrue(crypto.getItems().stream().anyMatch(i -> "比特币".equals(i.getName())),
                    "分类明细应列出加密持仓，否则 value 与明细对不上");
        }
    }

    @Nested
    @DisplayName("周/月变化口径（#12）")
    class ChangeMetrics {

        @Test
        @DisplayName("weeklyChange 对比 7 天前快照，monthlyChange 对比 30 天前快照")
        void weeklyAndMonthly() {
            // 当前总资产 = 现金 1000
            when(manualAssetService.getTotalByType(eq("cash"), eq("u1"))).thenReturn(new BigDecimal("1000"));
            when(manualAssetService.getTotalByType(eq("crypto"), eq("u1"))).thenReturn(BigDecimal.ZERO);
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(List.of());

            // 7 天前快照 = 800，30 天前快照 = 500
            AssetSnapshot weekAgo = AssetSnapshot.builder()
                    .date(LocalDate.now().minusDays(7))
                    .totalValue(new BigDecimal("800"))
                    .build();
            AssetSnapshot monthAgo = AssetSnapshot.builder()
                    .date(LocalDate.now().minusDays(30))
                    .totalValue(new BigDecimal("500"))
                    .build();
            when(assetSnapshotRepository.findTopByUserIdAndDateLessThanEqualOrderByDateDesc(eq("u1"), any()))
                    .thenAnswer(inv -> {
                        LocalDate target = inv.getArgument(1);
                        if (target.isBefore(LocalDate.now().minusDays(20))) {
                            return Optional.of(monthAgo);
                        }
                        return Optional.of(weekAgo);
                    });

            AssetOverviewDTO result = assetOverviewService.getOverview("u1");

            // 周变化 = 1000 - 800 = 200，月变化 = 1000 - 500 = 500
            assertEquals(0, new BigDecimal("200").compareTo(result.getWeeklyChange()));
            assertEquals(0, new BigDecimal("25.00").compareTo(result.getWeeklyChangePercent()));
            assertEquals(0, new BigDecimal("500").compareTo(result.getMonthlyChange()));
            assertEquals(0, new BigDecimal("100.00").compareTo(result.getMonthlyChangePercent()));
        }

        @Test
        @DisplayName("无任何快照 → 周/月变化为 0")
        void noSnapshots() {
            when(manualAssetService.getTotalByType(eq("cash"), eq("u1"))).thenReturn(new BigDecimal("1000"));
            when(manualAssetService.getTotalByType(eq("crypto"), eq("u1"))).thenReturn(BigDecimal.ZERO);
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(List.of());
            when(assetSnapshotRepository.findTopByUserIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                    .thenReturn(Optional.empty());

            AssetOverviewDTO result = assetOverviewService.getOverview("u1");

            assertEquals(BigDecimal.ZERO, result.getWeeklyChange());
            assertEquals(BigDecimal.ZERO, result.getWeeklyChangePercent());
            assertEquals(BigDecimal.ZERO, result.getMonthlyChange());
            assertEquals(BigDecimal.ZERO, result.getMonthlyChangePercent());
        }

        @Test
        @DisplayName("7 天前快照为 0 → 周变化百分比为 0（避免除零）")
        void weekAgoZero() {
            when(manualAssetService.getTotalByType(eq("cash"), eq("u1"))).thenReturn(new BigDecimal("1000"));
            when(manualAssetService.getTotalByType(eq("crypto"), eq("u1"))).thenReturn(BigDecimal.ZERO);
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(List.of());

            AssetSnapshot weekAgo = AssetSnapshot.builder()
                    .date(LocalDate.now().minusDays(7))
                    .totalValue(BigDecimal.ZERO)
                    .build();
            AssetSnapshot monthAgo = AssetSnapshot.builder()
                    .date(LocalDate.now().minusDays(30))
                    .totalValue(new BigDecimal("500"))
                    .build();
            when(assetSnapshotRepository.findTopByUserIdAndDateLessThanEqualOrderByDateDesc(eq("u1"), any()))
                    .thenAnswer(inv -> {
                        LocalDate target = inv.getArgument(1);
                        if (target.isBefore(LocalDate.now().minusDays(20))) {
                            return Optional.of(monthAgo);
                        }
                        return Optional.of(weekAgo);
                    });

            AssetOverviewDTO result = assetOverviewService.getOverview("u1");

            // 周变化 = 1000 - 0 = 1000，但百分比不计算（避免除零）
            assertEquals(0, new BigDecimal("1000").compareTo(result.getWeeklyChange()));
            assertEquals(BigDecimal.ZERO, result.getWeeklyChangePercent());
        }
    }

    @Nested
    @DisplayName("稳定币计入现金（2026-10-30 用户定口径）")
    class StablecoinAsCash {

        @Test
        @DisplayName("USDT 从加密资产挪到现金：现金翻桶、总资产不变")
        void stablecoinMovesToCash() {
            // 现金 100；crypto 手动资产合计 160（其中 USDT 稳定币 60、其他 100）；无 crypto 持仓
            when(manualAssetService.getTotalByType("cash", "u-1")).thenReturn(new BigDecimal("100.00"));
            when(manualAssetService.getTotalByType("crypto", "u-1")).thenReturn(new BigDecimal("160.00"));
            when(manualAssetService.getStablecoinTotal("u-1")).thenReturn(new BigDecimal("60.00"));
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(List.of());

            AssetOverviewDTO dto = assetOverviewService.getOverview("u-1");

            // 现金 = 100 + 60(USDT) = 160；加密资产 = 160 − 60 = 100；总资产 = 260（口径切换不影响总额）
            assertEquals(0, new BigDecimal("160.00").compareTo(dto.getCashValue()));
            assertEquals(0, new BigDecimal("100.00").compareTo(dto.getCryptoValue()));
            assertEquals(0, new BigDecimal("260.00").compareTo(dto.getTotalValue()));
        }

        @Test
        @DisplayName("无稳定币时行为不变（回归）")
        void noStablecoinUnchanged() {
            when(manualAssetService.getTotalByType("cash", "u-1")).thenReturn(new BigDecimal("100.00"));
            when(manualAssetService.getTotalByType("crypto", "u-1")).thenReturn(new BigDecimal("50.00"));
            when(manualAssetService.getStablecoinTotal("u-1")).thenReturn(BigDecimal.ZERO);
            when(holdingRepository.findByUserIdAndAssetCategoryAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(List.of());

            AssetOverviewDTO dto = assetOverviewService.getOverview("u-1");

            assertEquals(0, new BigDecimal("100.00").compareTo(dto.getCashValue()));
            assertEquals(0, new BigDecimal("50.00").compareTo(dto.getCryptoValue()));
        }
    }
}
