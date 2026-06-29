package com.fundtracker.service;

import com.fundtracker.model.enums.CostAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CostCalculator 单元测试
 * 纯逻辑，不依赖 Spring，运行最快
 */
class CostCalculatorTest {

    private CostCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CostCalculator();
    }

    // ==================== calculateCostPerShare ====================

    @Test
    @DisplayName("diluted: 成本 = (买入 - 卖出 - 分红) / 当前份额")
    void calculateCostPerShare_diluted() {
        // 买入10000, 卖出0, 分红3000, 份额1000 → (10000-0-3000)/1000 = 7.0
        BigDecimal result = calculator.calculateCostPerShare(
                CostAlgorithm.diluted,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("3000"),
                new BigDecimal("1000"),
                new BigDecimal("1000")
        );
        assertEquals(new BigDecimal("7.0000"), result);
    }

    @Test
    @DisplayName("diluted: 份额为0时返回0")
    void calculateCostPerShare_diluted_zeroShares() {
        BigDecimal result = calculator.calculateCostPerShare(
                CostAlgorithm.diluted,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("diluted_only: 不分红摊薄，成本 = (买入 - 卖出) / 当前份额")
    void calculateCostPerShare_dilutedOnly() {
        BigDecimal result = calculator.calculateCostPerShare(
                CostAlgorithm.diluted_only,
                new BigDecimal("10000"),
                new BigDecimal("2000"),
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                new BigDecimal("800")
        );
        // (10000 - 2000) / 800 = 10.0
        assertEquals(new BigDecimal("10.0000"), result);
    }

    @Test
    @DisplayName("weighted_avg: 成本 = 总买入 / 总买入份额")
    void calculateCostPerShare_weightedAvg() {
        BigDecimal result = calculator.calculateCostPerShare(
                CostAlgorithm.weighted_avg,
                new BigDecimal("15000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1500"),
                new BigDecimal("1500")
        );
        // 15000 / 1500 = 10.0
        assertEquals(new BigDecimal("10.0000"), result);
    }

    // ==================== calculateDividendRate ====================

    @Test
    @DisplayName("成本息率: 每份分红1元, 成本10元 → 10%")
    void calculateDividendRate_normal() {
        BigDecimal result = calculator.calculateDividendRate(
                new BigDecimal("1.0"),
                new BigDecimal("10.0")
        );
        assertEquals(new BigDecimal("10.00"), result);
    }

    @Test
    @DisplayName("成本息率: 成本为0 → 返回0")
    void calculateDividendRate_zeroCost() {
        BigDecimal result = calculator.calculateDividendRate(
                new BigDecimal("1.0"),
                BigDecimal.ZERO
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("成本息率: 成本已收回(负成本) → 返回-1")
    void calculateDividendRate_negativeCost() {
        BigDecimal result = calculator.calculateDividendRate(
                new BigDecimal("1.0"),
                new BigDecimal("-5.0")
        );
        assertEquals(new BigDecimal("-1"), result);
    }

    // ==================== calculatePriceDividendRate ====================

    @Test
    @DisplayName("股价息率: 每份分红1元, 市价20元 → 5%")
    void calculatePriceDividendRate_normal() {
        BigDecimal result = calculator.calculatePriceDividendRate(
                new BigDecimal("1.0"),
                new BigDecimal("20.0")
        );
        assertEquals(new BigDecimal("5.00"), result);
    }

    @Test
    @DisplayName("股价息率: 价格为0 → 返回0")
    void calculatePriceDividendRate_zeroPrice() {
        BigDecimal result = calculator.calculatePriceDividendRate(
                new BigDecimal("1.0"),
                BigDecimal.ZERO
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    // ==================== calculateRecoveryRate ====================

    @Test
    @DisplayName("回本进度: 累计分红3000, 净投入10000 → 30%")
    void calculateRecoveryRate() {
        BigDecimal result = calculator.calculateRecoveryRate(
                new BigDecimal("3000"),
                new BigDecimal("10000")
        );
        assertEquals(new BigDecimal("30.00"), result);
    }

    @Test
    @DisplayName("回本进度: 净投入为0 → 0%")
    void calculateRecoveryRate_zeroNetInvestment() {
        BigDecimal result = calculator.calculateRecoveryRate(
                new BigDecimal("1000"),
                BigDecimal.ZERO
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    // ==================== calculateRecoveryYears ====================

    @Test
    @DisplayName("回本年限: 净投入10000, 已分红3000, 年分红2000 → 3.5年")
    void calculateRecoveryYears() {
        BigDecimal result = calculator.calculateRecoveryYears(
                new BigDecimal("10000"),
                new BigDecimal("3000"),
                new BigDecimal("2000")
        );
        // (10000 - 3000) / 2000 = 3.5
        assertEquals(new BigDecimal("3.50"), result);
    }

    @Test
    @DisplayName("回本年限: 年分红为0 → 999年")
    void calculateRecoveryYears_zeroDividend() {
        BigDecimal result = calculator.calculateRecoveryYears(
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        assertEquals(new BigDecimal("999"), result);
    }

    // ==================== calculateNetInvestment ====================

    @Test
    @DisplayName("diluted 净投入: 买入10000, 卖出0, 分红3000 → 7000")
    void calculateNetInvestment_diluted() {
        BigDecimal result = calculator.calculateNetInvestment(
                CostAlgorithm.diluted,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("3000")
        );
        assertEquals(new BigDecimal("7000"), result);
    }

    @Test
    @DisplayName("weighted_avg 净投入: 买入10000, 卖出2000 → 8000")
    void calculateNetInvestment_weightedAvg() {
        BigDecimal result = calculator.calculateNetInvestment(
                CostAlgorithm.weighted_avg,
                new BigDecimal("10000"),
                new BigDecimal("2000"),
                BigDecimal.ZERO
        );
        assertEquals(new BigDecimal("8000"), result);
    }

    // ==================== calculateMarketValue ====================

    @Test
    @DisplayName("市值: 最新价20元 × 份额1000 = 20000")
    void calculateMarketValue() {
        BigDecimal result = calculator.calculateMarketValue(
                new BigDecimal("20.0"),
                new BigDecimal("1000")
        );
        assertEquals(new BigDecimal("20000.00"), result);
    }
}
