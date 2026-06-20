package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetHistoryDTO {
    private List<Point> series;
    private BigDecimal totalChange;
    private BigDecimal totalChangePercent;
    private BigDecimal newInvestment;
    private BigDecimal dividendIncome;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private LocalDate date;
        private BigDecimal value;
    }
}
