package com.fundtracker.scheduler;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataAuditorTest {

    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ManualAssetRepository manualAssetRepository;
    @Mock private DividendEventRepository dividendEventRepository;
    @Mock private FundDividendRecordRepository fundDividendRecordRepository;

    private DataAuditor dataAuditor;

    @BeforeEach
    void setUp() {
        dataAuditor = new DataAuditor(
                holdingRepository, transactionRepository,
                manualAssetRepository, dividendEventRepository,
                fundDividendRecordRepository,
                new com.fundtracker.service.MonitorLogService()
        );
        // 默认：无分红异常
        when(fundDividendRecordRepository.findDividendOutliers()).thenReturn(List.of());
    }

    @Nested
    @DisplayName("auditAll 整体审计流程")
    class AuditAll {

        @Test
        @DisplayName("无用户数据 → 跳过审计")
        void noUsers() {
            when(holdingRepository.findDistinctUserIdsByDeletedFalse()).thenReturn(List.of());

            dataAuditor.auditAll();

            verify(holdingRepository).findDistinctUserIdsByDeletedFalse();
            verify(holdingRepository, never()).findByUserIdAndDeletedFalseOrderByMarketValueDesc(any());
        }

        @Test
        @DisplayName("数据一致 → 无异常")
        void consistentData() {
            Holding holding = Holding.builder()
                    .id("h-1").name("测试基金").type(HoldingType.fund)
                    .shares(new BigDecimal("1000")).latestPrice(new BigDecimal("10"))
                    .marketValue(new BigDecimal("10000.00"))
                    .costPerShare(new BigDecimal("10")).cost(new BigDecimal("10000"))
                    .dividendRate(new BigDecimal("5.00"))
                    .build();

            when(holdingRepository.findDistinctUserIdsByDeletedFalse()).thenReturn(List.of("user-1"));
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-1"))
                    .thenReturn(List.of(holding));
            when(transactionRepository.findByHoldingIdAndUserId("h-1", "user-1")).thenReturn(List.of());
            ManualAsset cashAsset = ManualAsset.builder()
                    .id("cash-1").name("现金").type("cash")
                    .amount(new BigDecimal("50000"))
                    .build();
            when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of(cashAsset));
            when(dividendEventRepository.findByStatusAndUserId(EventStatus.distributed, "user-1")).thenReturn(List.of());
            when(dividendEventRepository.findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending))).thenReturn(List.of());

            dataAuditor.auditAll();

            // findByUserIdAndDeletedFalseOrderByMarketValueDesc 被 5 个审计方法调用（含 auditCashFlow）
            verify(holdingRepository, times(5)).findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-1");
            // findByHoldingIdAndUserId 被 3 个审计方法调用（含 auditCashFlow）
            verify(transactionRepository, times(3)).findByHoldingIdAndUserId("h-1", "user-1");
            verify(manualAssetRepository).findByUserIdAndType("user-1", "cash");
            verify(dividendEventRepository).findByStatusAndUserId(EventStatus.distributed, "user-1");
            verify(dividendEventRepository).findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending));
            verify(fundDividendRecordRepository).findDividendOutliers();
        }

        @Test
        @DisplayName("市值不一致 → 记录错误但不抛异常")
        void inconsistentMarketValue() {
            Holding holding = Holding.builder()
                    .id("h-1").name("测试基金").type(HoldingType.fund)
                    .shares(new BigDecimal("1000")).latestPrice(new BigDecimal("10"))
                    .marketValue(new BigDecimal("9999.00"))
                    .costPerShare(new BigDecimal("10")).cost(new BigDecimal("10000"))
                    .dividendRate(new BigDecimal("5.00"))
                    .build();

            when(holdingRepository.findDistinctUserIdsByDeletedFalse()).thenReturn(List.of("user-1"));
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-1"))
                    .thenReturn(List.of(holding));
            when(transactionRepository.findByHoldingIdAndUserId("h-1", "user-1")).thenReturn(List.of());
            when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of());
            when(dividendEventRepository.findByStatusAndUserId(EventStatus.distributed, "user-1")).thenReturn(List.of());
            when(dividendEventRepository.findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending))).thenReturn(List.of());

            dataAuditor.auditAll();

            verify(holdingRepository, atLeastOnce()).findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-1");
        }

        @Test
        @DisplayName("过期分红事件 → 记录警告但不抛异常")
        void staleDividendEvents() {
            Holding holding = Holding.builder()
                    .id("h-1").name("测试基金").type(HoldingType.fund)
                    .shares(new BigDecimal("1000")).latestPrice(new BigDecimal("10"))
                    .marketValue(new BigDecimal("10000.00"))
                    .costPerShare(new BigDecimal("10")).cost(new BigDecimal("10000"))
                    .dividendRate(new BigDecimal("5.00"))
                    .build();

            DividendEvent staleEvent = DividendEvent.builder()
                    .id("e-1").userId("user-1").holdingName("测试基金")
                    .date(LocalDate.now().minusDays(10)).amount(new BigDecimal("100"))
                    .build();

            when(holdingRepository.findDistinctUserIdsByDeletedFalse()).thenReturn(List.of("user-1"));
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-1"))
                    .thenReturn(List.of(holding));
            when(transactionRepository.findByHoldingIdAndUserId("h-1", "user-1")).thenReturn(List.of());
            when(manualAssetRepository.findByUserIdAndType("user-1", "cash")).thenReturn(List.of());
            when(dividendEventRepository.findByStatusAndUserId(EventStatus.distributed, "user-1")).thenReturn(List.of());
            when(dividendEventRepository.findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending)))
                    .thenReturn(List.of(staleEvent));

            dataAuditor.auditAll();

            verify(dividendEventRepository).findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending));
        }

        @Test
        @DisplayName("多用户时每个用户独立审计")
        void multipleUsers() {
            Holding holdingA = Holding.builder()
                    .id("h-a").name("用户A基金").type(HoldingType.fund)
                    .shares(new BigDecimal("100")).latestPrice(new BigDecimal("10"))
                    .marketValue(new BigDecimal("1000.00"))
                    .costPerShare(new BigDecimal("10")).cost(new BigDecimal("1000"))
                    .dividendRate(new BigDecimal("5.00"))
                    .build();
            Holding holdingB = Holding.builder()
                    .id("h-b").name("用户B基金").type(HoldingType.fund)
                    .shares(new BigDecimal("200")).latestPrice(new BigDecimal("10"))
                    .marketValue(new BigDecimal("2000.00"))
                    .costPerShare(new BigDecimal("10")).cost(new BigDecimal("2000"))
                    .dividendRate(new BigDecimal("5.00"))
                    .build();

            when(holdingRepository.findDistinctUserIdsByDeletedFalse()).thenReturn(List.of("user-a", "user-b"));
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-a"))
                    .thenReturn(List.of(holdingA));
            when(holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-b"))
                    .thenReturn(List.of(holdingB));
            when(transactionRepository.findByHoldingIdAndUserId("h-a", "user-a")).thenReturn(List.of());
            when(transactionRepository.findByHoldingIdAndUserId("h-b", "user-b")).thenReturn(List.of());
            when(manualAssetRepository.findByUserIdAndType("user-a", "cash")).thenReturn(List.of());
            when(manualAssetRepository.findByUserIdAndType("user-b", "cash")).thenReturn(List.of());
            when(dividendEventRepository.findByStatusAndUserId(EventStatus.distributed, "user-a")).thenReturn(List.of());
            when(dividendEventRepository.findByStatusAndUserId(EventStatus.distributed, "user-b")).thenReturn(List.of());
            when(dividendEventRepository.findByDateBeforeAndStatus(any(LocalDate.class), eq(EventStatus.pending))).thenReturn(List.of());

            dataAuditor.auditAll();

            // 每个用户都被审计 4 次（marketValue, holdingShares, costConsistency, dividendRate）
            verify(holdingRepository, times(4)).findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-a");
            verify(holdingRepository, times(4)).findByUserIdAndDeletedFalseOrderByMarketValueDesc("user-b");
            // findByHoldingIdAndUserId 被 2 个审计方法调用（holdingShares, costConsistency）
            verify(transactionRepository, times(2)).findByHoldingIdAndUserId("h-a", "user-a");
            verify(transactionRepository, times(2)).findByHoldingIdAndUserId("h-b", "user-b");
        }
    }
}