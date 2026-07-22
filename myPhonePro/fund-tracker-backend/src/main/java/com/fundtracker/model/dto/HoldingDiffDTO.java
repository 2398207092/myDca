package com.fundtracker.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单持仓 vs 上期变化响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingDiffDTO {

    private String holdingId;
    private SnapshotSummary current;
    private SnapshotSummary previous;
    private BigDecimal marketValueChange;
    private BigDecimal marketValueChangePct;
    private BigDecimal sharesChange;
    private BigDecimal sharesChangePct;
    private BigDecimal pctOfTotalChange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotSummary {
        private LocalDate date;
        private BigDecimal marketValue;
        private BigDecimal shares;
    }
}
