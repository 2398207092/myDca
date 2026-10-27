package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingDTO {
    private String id;
    private String name;
    private String code;
    private String type;
    private String costAlgorithm;
    private BigDecimal shares;
    private BigDecimal costPerShare;
    private BigDecimal cost;
    private BigDecimal marketValue;
    private BigDecimal latestPrice;
    private LocalDate priceDate;

    /** 原始报价币种：CNY / USDT */
    private String quoteCurrency;
    /** 原始报价（如 79827.4 USDT），CNY 标的为 null */
    private BigDecimal quotePrice;
    /** 成本是否为估算值 */
    private boolean costEstimated;
    private String priceSource;
    private LocalDateTime priceUpdatedAt;
    private BigDecimal predictedDividend;
    private BigDecimal dividendRate;
    private BigDecimal priceDividendRate;
    private BigDecimal totalDividendReceived;
    private BigDecimal netInvestment;
    private BigDecimal dividendRecoveryRate;
    private BigDecimal estimatedRecoveryYears;
    private BigDecimal reinvestRecoveryYears;
    private String color;
    private String assetCategory;
    private Boolean dividendReinvest;
    private BigDecimal buyFeeRate;
    private BigDecimal sellFeeRate;
}
