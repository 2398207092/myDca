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
public class MonthlyInsightResp {
    private RichestSource richestSource;
    private MonthlyActivity monthlyActivity;
    private NextDividend nextDividend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RichestSource {
        private String holdingName;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyActivity {
        private int payoutCount;
        private int fundCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextDividend {
        private String holdingName;
        private BigDecimal amount;
        private int daysRemaining;
    }
}
