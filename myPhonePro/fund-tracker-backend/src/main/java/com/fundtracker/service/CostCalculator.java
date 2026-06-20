package com.fundtracker.service;

import com.fundtracker.model.enums.CostAlgorithm;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 三种成本算法
 */
@Component
public class CostCalculator {

    /**
     * 计算每股成本
     * @param algorithm     成本算法
     * @param totalBuy      累计买入金额
     * @param totalSell     累计卖出金额
     * @param totalDividend 累计已收分红
     * @param totalBuyShares 总买入份额
     * @param currentShares 当前持有份额
     * @return 每股成本
     */
    public BigDecimal calculateCostPerShare(CostAlgorithm algorithm,
                                             BigDecimal totalBuy,
                                             BigDecimal totalSell,
                                             BigDecimal totalDividend,
                                             BigDecimal totalBuyShares,
                                             BigDecimal currentShares) {
        if (currentShares.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        switch (algorithm) {
            case diluted:
                // 分红摊薄: (买入 - 卖出 - 分红) / 当前份额
                return totalBuy.subtract(totalSell).subtract(totalDividend)
                        .divide(currentShares, 4, RoundingMode.HALF_UP);

            case diluted_only:
                // 摊薄成本: (买入 - 卖出) / 当前份额
                return totalBuy.subtract(totalSell)
                        .divide(currentShares, 4, RoundingMode.HALF_UP);

            case weighted_avg:
                // 加权平均: 总买入金额 / 总买入份额，成本永不小于 0
                if (totalBuyShares.compareTo(BigDecimal.ZERO) == 0) {
                    return BigDecimal.ZERO;
                }
                return totalBuy.divide(totalBuyShares, 4, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);

            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * 计算总成本
     */
    public BigDecimal calculateTotalCost(BigDecimal costPerShare, BigDecimal currentShares) {
        return costPerShare.multiply(currentShares)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算净投入
     */
    public BigDecimal calculateNetInvestment(CostAlgorithm algorithm,
                                              BigDecimal totalBuy,
                                              BigDecimal totalSell,
                                              BigDecimal totalDividend) {
        switch (algorithm) {
            case diluted:
                return totalBuy.subtract(totalSell).subtract(totalDividend);
            case diluted_only:
                return totalBuy.subtract(totalSell);
            case weighted_avg:
                return totalBuy;
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * 计算成本息率
     * 成本息率 = 预测每股分红 / 每股成本 * 100%
     */
    public BigDecimal calculateDividendRate(BigDecimal predictedDividendPerShare,
                                             BigDecimal costPerShare) {
        if (costPerShare.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return predictedDividendPerShare.divide(costPerShare, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算股价息率
     * 股价息率 = 预测每股分红 / 最新价格 * 100%
     */
    public BigDecimal calculatePriceDividendRate(BigDecimal predictedDividendPerShare,
                                                  BigDecimal latestPrice) {
        if (latestPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return predictedDividendPerShare.divide(latestPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算分红回本进度
     * 回本进度 = 累计已收分红 / 净投入 * 100%
     */
    public BigDecimal calculateRecoveryRate(BigDecimal totalDividend, BigDecimal netInvestment) {
        if (netInvestment.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalDividend.divide(netInvestment, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算预计回本年限
     * 回本年限 = (净投入 - 累计已收分红) / 预测年分红
     */
    public BigDecimal calculateRecoveryYears(BigDecimal netInvestment,
                                              BigDecimal totalDividend,
                                              BigDecimal predictedAnnualDividend) {
        if (predictedAnnualDividend.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(999);
        }
        BigDecimal remaining = netInvestment.subtract(totalDividend)
                .max(BigDecimal.ZERO);
        return remaining.divide(predictedAnnualDividend, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算市值
     */
    public BigDecimal calculateMarketValue(BigDecimal latestPrice, BigDecimal shares) {
        return latestPrice.multiply(shares)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
