package com.fundtracker.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 总资产走势响应：按快照日期分组聚合，每个日期一个数据点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalAssetSeriesDTO {

    private List<TotalAssetPoint> series;
    private BigDecimal totalChange;
    private BigDecimal totalChangePercent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalAssetPoint {
        private LocalDate date;
        /** 含现金/比特币 */
        private BigDecimal totalMarketValue;
        /** 仅基金持仓 */
        private BigDecimal totalShares;
        /** 仅基金持仓 */
        private BigDecimal totalCostBasis;
        private BigDecimal totalProfitLoss;
        private BigDecimal totalProfitLossPct;
    }
}
