package com.fundtracker.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单持仓走势响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingSeriesDTO {

    private HoldingInfo holding;
    private List<HoldingPoint> series;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingInfo {
        private String id;
        private String name;
        private String code;
        private String assetCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingPoint {
        private LocalDate date;
        private BigDecimal marketValue;
        private BigDecimal shares;
        private BigDecimal costBasis;
        private BigDecimal profitLoss;
        private BigDecimal profitLossPct;
        private BigDecimal pctOfTotal;
    }
}
