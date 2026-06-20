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
public class AssetOverviewDTO {
    private BigDecimal totalValue;
    private BigDecimal cashValue;
    private BigDecimal cryptoValue;
    private BigDecimal usStockValue;
    private BigDecimal goldValue;
    private BigDecimal dividendValue;
    private BigDecimal weeklyChange;
    private BigDecimal weeklyChangePercent;
    private BigDecimal monthlyChange;
    private BigDecimal monthlyChangePercent;
    private List<CategoryDetail> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDetail {
        private String name;
        private String type;
        private BigDecimal value;
        private double percentage;
        private String color;
        private List<HoldingItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingItem {
        private String id;
        private String name;
        private BigDecimal value;
    }
}
