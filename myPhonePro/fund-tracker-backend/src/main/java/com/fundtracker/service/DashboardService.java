package com.fundtracker.service;

import com.fundtracker.model.dto.DashboardDTO;
import com.fundtracker.model.entity.CoverageCategory;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.CoverageCategoryRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final HoldingRepository holdingRepository;
    private final CoverageCategoryRepository coverageCategoryRepository;
    private final TransactionRepository transactionRepository;

    public DashboardDTO getDashboard() {
        List<Holding> holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();

        // 计算预测年度分红总额
        BigDecimal predictedAnnualDividend = holdings.stream()
                .map(Holding::getPredictedDividend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算当月预计派息（按年/12 估算）
        BigDecimal monthlyPredicted = predictedAnnualDividend.divide(
                BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // 覆盖类目数
        List<CoverageCategory> categories = coverageCategoryRepository.findAll();
        long coveredCount = categories.stream()
                .filter(c -> c.getPercentage().compareTo(BigDecimal.ZERO) > 0)
                .count();

        // 计算总已收分红
        BigDecimal totalDividendReceived = holdings.stream()
                .map(Holding::getTotalDividendReceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总成本
        BigDecimal totalCost = holdings.stream()
                .map(Holding::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总市值
        BigDecimal totalMarketValue = holdings.stream()
                .map(Holding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算成本息率 (预测年度分红 / 总成本)
        BigDecimal overallDividendRate = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? predictedAnnualDividend.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // 计算市值息率 (预测年度分红 / 总市值)
        BigDecimal priceDividendRate = totalMarketValue.compareTo(BigDecimal.ZERO) > 0
                ? predictedAnnualDividend.divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // 连续收息天数 = 从第一笔交易到今天
        long consecutiveDays = 0;
        if (!holdings.isEmpty()) {
            List<String> holdingIds = holdings.stream().map(Holding::getId).toList();
            // 确保 holdingIds 参数正确传递
            LocalDate earliestDate = transactionRepository.findEarliestTransactionDateByHoldingIds(holdingIds);
            if (earliestDate != null) {
                consecutiveDays = ChronoUnit.DAYS.between(earliestDate, LocalDate.now());
            }
        }

        // 10年预期收益 = 成本息率 × 10
        BigDecimal tenYearReturn = overallDividendRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.TEN);

        return DashboardDTO.builder()
                .consecutiveDays((int) consecutiveDays)
                .predictedAnnualDividend(predictedAnnualDividend)
                .tenYearExpectedReturn(tenYearReturn)
                .monthlyPredictedDividend(monthlyPredicted)
                .monthlyMessage("稳稳的幸福，本月预计收息" + monthlyPredicted.setScale(0, RoundingMode.HALF_UP) + "元")
                .totalHoldings(holdings.size())
                .coveredCategories((int) coveredCount)
                .totalDividendReceived(totalDividendReceived)
                .totalCost(totalCost)
                .totalMarketValue(totalMarketValue)
                .overallDividendRate(overallDividendRate)
                .priceDividendRate(priceDividendRate)
                .build();
    }
}
