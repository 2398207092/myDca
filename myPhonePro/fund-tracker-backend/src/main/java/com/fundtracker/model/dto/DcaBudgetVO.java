package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcaBudgetVO {
    private String month;
    private int tradingDays;
    private BigDecimal totalAmount;
    private List<PlanBudgetItem> plans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanBudgetItem {
        private String holdingName;
        private String frequency;
        private BigDecimal amount;
        private int executions;
        private BigDecimal budgetAmount;
    }
}
