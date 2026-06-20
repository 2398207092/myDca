package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueChangeDTO {
    private BigDecimal currentValue;
    private Map<String, PeriodChange> periods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodChange {
        private BigDecimal change;
        private BigDecimal percent;
        private BigDecimal pastValue;
        private List<HoldingDetail> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingDetail {
        private String holdingId;
        private String name;
        private String code;
        private BigDecimal change;
        private BigDecimal percent;
        private BigDecimal currentValue;
        private BigDecimal pastValue;
    }
}
