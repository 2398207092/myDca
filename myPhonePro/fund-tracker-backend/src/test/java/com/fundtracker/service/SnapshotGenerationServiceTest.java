package com.fundtracker.service;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import com.fundtracker.repository.ManualAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SnapshotGenerationService 单元测试
 * 覆盖：P1-19 快照份额 8 位精度、P1-22 净值刷新后当日快照重拍
 */
@ExtendWith(MockitoExtension.class)
class SnapshotGenerationServiceTest {

    @Mock private HoldingRepository holdingRepository;
    @Mock private HoldingSnapshotRepository holdingSnapshotRepository;
    @Mock private ManualAssetRepository manualAssetRepository;
    @Mock private AssetOverviewService assetOverviewService;

    private SnapshotGenerationService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotGenerationService(
                holdingRepository, holdingSnapshotRepository, manualAssetRepository, assetOverviewService);
    }

    private Holding holding(String id, String userId, String shares, String marketValue) {
        return Holding.builder()
                .id(id).userId(userId).name("测试持仓").code(id)
                .type(HoldingType.fund).costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal(shares))
                .marketValue(new BigDecimal(marketValue))
                .netInvestment(new BigDecimal("6000"))
                .deleted(false)
                .build();
    }

    private HoldingSnapshot snapshot(String holdingId, String shares, String marketValue) {
        return HoldingSnapshot.builder()
                .holdingId(holdingId)
                .snapshotDate(LocalDate.now())
                .marketValue(new BigDecimal(marketValue))
                .shares(new BigDecimal(shares))
                .costBasis(new BigDecimal("6000"))
                .profitLoss(new BigDecimal("111.11"))
                .profitLossPct(new BigDecimal("1.85"))
                .pctOfTotal(new BigDecimal("61.11"))
                .build();
    }

    @Nested
    @DisplayName("P1-19: 快照份额 8 位精度")
    class SharesPrecision {

        @Test
        @DisplayName("BTC 份额 0.01139738 生成快照必须保留全部 8 位小数（不再被 18,4 截断）")
        void snapshotKeepsEightDecimalShares() {
            Holding btc = holding("h-btc", "u1", "0.01139738", "6210.09");
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("u1"))
                    .thenReturn(List.of(btc));
            when(manualAssetRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of());

            service.snapshotAllHoldings("u1");

            ArgumentCaptor<HoldingSnapshot> captor = ArgumentCaptor.forClass(HoldingSnapshot.class);
            verify(holdingSnapshotRepository).save(captor.capture());
            HoldingSnapshot saved = captor.getValue();
            assertEquals(0, saved.getShares().compareTo(new BigDecimal("0.01139738")),
                    "快照份额必须完整保留 8 位小数（历史 18,4 会截断为 0.0115）");
        }
    }

    @Nested
    @DisplayName("P1-22: 净值刷新后当日快照重拍")
    class ResnapshotStaleToday {

        @Test
        @DisplayName("快照生成于净值到达前（市值过期）→ 重拍后当日快照按最新市值重算")
        void resnapshotsStaleSnapshot() {
            // 快照生成时用的前日口径市值 6111.11；22:00 净值刷新后持仓市值变为 6233.34
            Holding h = holding("h1", "u1", "100", "6233.34");
            HoldingSnapshot stale = snapshot("h1", "100", "6111.11");
            when(holdingRepository.findByDeletedFalseOrderByMarketValueDesc()).thenReturn(List.of(h));
            when(holdingSnapshotRepository.findByHoldingIdAndSnapshotDate("h1", LocalDate.now()))
                    .thenReturn(Optional.of(stale));
            when(manualAssetRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of());

            int count = service.resnapshotStaleToday();

            assertEquals(1, count);
            // uk_holding_date 唯一约束：必须先删旧快照再插新
            verify(holdingSnapshotRepository).delete(stale);
            ArgumentCaptor<HoldingSnapshot> captor = ArgumentCaptor.forClass(HoldingSnapshot.class);
            verify(holdingSnapshotRepository).save(captor.capture());
            HoldingSnapshot resnapped = captor.getValue();
            assertEquals(0, resnapped.getMarketValue().compareTo(new BigDecimal("6233.34")),
                    "重拍后当日快照市值必须按新净值重算");
            assertEquals(LocalDate.now(), resnapped.getSnapshotDate());
            assertEquals("h1", resnapped.getHoldingId());
            assertEquals(0, resnapped.getShares().compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("当日快照与持仓当前值一致 → 不重拍")
        void skipsFreshSnapshot() {
            Holding h = holding("h1", "u1", "100", "6233.34");
            HoldingSnapshot fresh = snapshot("h1", "100", "6233.34");
            when(holdingRepository.findByDeletedFalseOrderByMarketValueDesc()).thenReturn(List.of(h));
            when(holdingSnapshotRepository.findByHoldingIdAndSnapshotDate("h1", LocalDate.now()))
                    .thenReturn(Optional.of(fresh));

            int count = service.resnapshotStaleToday();

            assertEquals(0, count);
            verify(holdingSnapshotRepository, never()).delete(any(HoldingSnapshot.class));
            verify(holdingSnapshotRepository, never()).save(any(HoldingSnapshot.class));
        }

        @Test
        @DisplayName("当日无快照的持仓 → 不生成新快照（重拍只针对已有快照）")
        void skipsHoldingsWithoutTodaySnapshot() {
            Holding h = holding("h1", "u1", "100", "6233.34");
            when(holdingRepository.findByDeletedFalseOrderByMarketValueDesc()).thenReturn(List.of(h));
            when(holdingSnapshotRepository.findByHoldingIdAndSnapshotDate("h1", LocalDate.now()))
                    .thenReturn(Optional.empty());

            int count = service.resnapshotStaleToday();

            assertEquals(0, count);
            verify(holdingSnapshotRepository, never()).save(any(HoldingSnapshot.class));
        }
    }
}
