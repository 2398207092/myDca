package com.fundtracker.model.enums;

public enum CostAlgorithm {
    diluted,        // 分红摊薄: (买入 - 卖出 - 分红) / 份额
    diluted_only,   // 摊薄成本: (买入 - 卖出) / 份额
    weighted_avg    // 加权平均: 总买入金额 / 总买入份额
}
