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
public class AnnualInsightResp {
    private Summary summary;
    private List<MonthBar> monthlyBars;
    private List<FundRank> fundRanks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal totalDividend;
        private int totalPayoutCount;
        private int fundCount;
        private String peakMonth;
        private BigDecimal peakMonthAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthBar {
        private int month;
        private BigDecimal amount;
        private int percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FundRank {
        private String holdingName;
        private BigDecimal amount;
        private int percentage;
        private int rank;
    }
}
