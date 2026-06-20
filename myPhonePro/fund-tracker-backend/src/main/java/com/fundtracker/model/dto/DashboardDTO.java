package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private int consecutiveDays;
    private BigDecimal predictedAnnualDividend;
    private BigDecimal tenYearExpectedReturn;
    private BigDecimal monthlyPredictedDividend;
    private String monthlyMessage;
    private int totalHoldings;
    private int coveredCategories;
    
    private BigDecimal totalDividendReceived;
    private BigDecimal totalCost;
    private BigDecimal totalMarketValue;
    private BigDecimal overallDividendRate;
    private BigDecimal priceDividendRate;
}
