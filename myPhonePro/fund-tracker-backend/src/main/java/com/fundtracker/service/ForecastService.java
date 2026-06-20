package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.ForecastDataPoint;
import com.fundtracker.model.dto.ForecastResp;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ForecastService {

    private final HoldingRepository holdingRepository;

    public ForecastResp getForecast(String holdingId, String period) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId)
                .orElseThrow(BusinessException::holdingNotFound);

        List<ForecastDataPoint> series = new ArrayList<>();
        BigDecimal basePrediction = holding.getPredictedDividend().max(BigDecimal.ONE);

        if ("5y".equals(period)) {
            String[] labels = {"2025", "2026", "2027", "2028", "2029"};
            double[] multipliers = {1.0, 1.12, 1.25, 1.40, 1.55};
            for (int i = 0; i < labels.length; i++) {
                series.add(new ForecastDataPoint(
                        labels[i],
                        basePrediction.multiply(BigDecimal.valueOf(multipliers[i]))
                                .setScale(2, RoundingMode.HALF_UP)
                ));
            }
        } else {
            // 12m
            String[] labels = {"1月", "2月", "3月", "4月", "5月", "6月",
                    "7月", "8月", "9月", "10月", "11月", "12月"};
            BigDecimal monthly = basePrediction.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            for (String label : labels) {
                series.add(new ForecastDataPoint(label, monthly));
            }
        }

        int trendPercentage = 45;  // 简化处理

        return ForecastResp.builder()
                .holdingId(holdingId)
                .period(period)
                .series(series)
                .trendPercentage(trendPercentage)
                .build();
    }
}
