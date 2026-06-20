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
public class CoverageDTO {
    private int totalExpenses;
    private int coveredExpenses;
    private BigDecimal totalAnnualExpense;
    private BigDecimal predictedAnnualDividend;
    private BigDecimal totalDividendReceived;
    private BigDecimal remainingDividend;
    private List<ExpenseCoverageItem> expenses;
    private MilestoneInfo currentMilestone;
    private int currentMilestoneIndex;
    private String nextMilestoneName;
    private BigDecimal nextMilestoneRemaining;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseCoverageItem {
        private String id;
        private String name;
        private String icon;
        private BigDecimal annualAmount;
        private boolean covered;
        private boolean inProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneInfo {
        private String name;
        private String icon;
        private int requiredExpenses;
        private String description;
    }
}
