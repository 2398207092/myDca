package com.fundtracker.service;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HoldingService.recalculateHoldingMetrics 单元测试
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private FundDividendRecordRepository fundDividendRecordRepository;
    @Mock private DividendEventRepository dividendEventRepository;
    @Mock private FundDividendScrapeService fundDividendScrapeService;
    @Mock private FundNavScrapeService fundNavScrapeService;

    private HoldingService holdingService;
    private CostCalculator costCalculator;

    @BeforeEach
    void setUp() {
        costCalculator = new CostCalculator();
        holdingService = new HoldingService(
                holdingRepository, transactionRepository,
                costCalculator, fundDividendScrapeService,
                fundNavScrapeService, fundDividendRecordRepository,
                dividendEventRepository
        );
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 买入10000元，1000份 → diluted成本=10，息率按预测分红计算")
    void recalculateHoldingMetrics_basicBuy() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("11000"))
                .predictedDividend(new BigDecimal("500"))
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 0) / 1000 = 10
        assertEquals(new BigDecimal("10.0000"), holding.getCostPerShare());
        // totalCost = 10 * 1000 = 10000
        assertEquals(new BigDecimal("10000.00"), holding.getCost());
        // netInvestment = 10000
        assertEquals(new BigDecimal("10000"), holding.getNetInvestment());
        // predictedDividendPerShare = 500 / 1000 = 0.5
        // dividendRate = 0.5 / 10 * 100 = 5%
        assertEquals(new BigDecimal("5.00"), holding.getDividendRate());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 买入+卖出混合，验证净投入")
    void recalculateHoldingMetrics_buyAndSell() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("500"))
                .cost(new BigDecimal("5000"))
                .marketValue(new BigDecimal("5500"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();
        Transaction sellTx = Transaction.builder()
                .type(TransactionType.sell)
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("12"))
                .total(new BigDecimal("6000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx, sellTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 6000 - 0) / 500 = 8
        assertEquals(new BigDecimal("8.0000"), holding.getCostPerShare());
        // netInvestment = 10000 - 6000 = 4000
        assertEquals(new BigDecimal("4000"), holding.getNetInvestment());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 有分红摊薄时成本降低")
    void recalculateHoldingMetrics_withDividend() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("12000"))
                .predictedDividend(new BigDecimal("800"))
                .totalDividendReceived(new BigDecimal("3000"))
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 3000) / 1000 = 7
        assertEquals(new BigDecimal("7.0000"), holding.getCostPerShare());
        // dividendRate = (800/1000) / 7 * 100 = 11.43 (四舍五入)
        BigDecimal expectedRate = new BigDecimal("0.8").divide(new BigDecimal("7"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedRate, holding.getDividendRate());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 无交易时用初始成本")
    void recalculateHoldingMetrics_noTransactions() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("10000"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of());

        holdingService.recalculateHoldingMetrics(holding);

        // 无交易时 netInvestment = holding.getCost() = 10000
        assertEquals(new BigDecimal("10000"), holding.getNetInvestment());
        assertEquals(new BigDecimal("10000.00"), holding.getCost());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 送股不增加成本")
    void recalculateHoldingMetrics_bonusShares() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1200"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("12000"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();
        Transaction bonusTx = Transaction.builder()
                .type(TransactionType.bonus_share)
                .quantity(new BigDecimal("200"))
                .price(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx, bonusTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 0) / 1200 = 8.3333（送股摊薄了成本）
        assertEquals(new BigDecimal("8.3333"), holding.getCostPerShare());
        // totalCost = 8.3333 * 1200 = 9999.96
        assertEquals(new BigDecimal("9999.96"), holding.getCost());
    }
}
