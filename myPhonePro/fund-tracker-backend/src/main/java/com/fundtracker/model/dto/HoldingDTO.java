package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

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
}
