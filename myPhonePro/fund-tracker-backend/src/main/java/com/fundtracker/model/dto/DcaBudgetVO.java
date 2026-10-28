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
        /** 加密币计划为 USDT，其余为人民币（前端按 tradingMarket 显示单位） */
        private BigDecimal amount;
        private int executions;
        /** 预算金额，统一人民币口径（加密币计划已按汇率折算） */
        private BigDecimal budgetAmount;
        private String tradingMarket;
    }
}
