package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResp {
    private String holdingId;
    private String period;
    private List<ForecastDataPoint> series;
    private int trendPercentage;
}
