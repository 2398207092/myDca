package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.ForecastDataPoint;
import com.fundtracker.model.dto.ForecastResp;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final HoldingRepository holdingRepository;
    private final FundDividendRecordRepository fundDividendRecordRepository;

    public ForecastResp getForecast(String holdingId, String period, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
                .orElseThrow(BusinessException::holdingNotFound);

        List<ForecastDataPoint> series = new ArrayList<>();
        BigDecimal basePrediction = holding.getPredictedDividend().max(BigDecimal.ONE);

        // 基于历史分红数据计算 CAGR（复合年增长率）
        double cagr = calculateHistoricalCAGR(holding.getCode());

        if ("5y".equals(period)) {
            int currentYear = LocalDate.now().getYear();
            for (int i = 0; i < 5; i++) {
                int year = currentYear + i;
                double multiplier = Math.pow(1 + cagr, i);
                BigDecimal predicted = basePrediction.multiply(BigDecimal.valueOf(multiplier))
                        .setScale(2, RoundingMode.HALF_UP);
                series.add(new ForecastDataPoint(String.valueOf(year), predicted));
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

        // trendPercentage 基于历史 CAGR 推算
        int trendPercentage = (int) Math.round(cagr * 100);

        return ForecastResp.builder()
                .holdingId(holdingId)
                .period(period)
                .series(series)
                .trendPercentage(trendPercentage)
                .build();
    }

    /**
     * 基于历史分红数据计算 CAGR（复合年增长率）
     * 取近3年每年度分红总额，计算年均增长率
     * 
     * @return CAGR 值，如 0.12 表示 12% 年增长；无足够数据时返回 0
     */
    private double calculateHistoricalCAGR(String fundCode) {
        try {
            List<FundDividendRecord> records = fundDividendRecordRepository
                    .findByFundCodeOrderByExDateDesc(fundCode);

            if (records == null || records.size() < 2) {
                return 0;
            }

            // 按年度汇总分红金额
            Map<Integer, BigDecimal> yearlyTotals = new TreeMap<>();
            LocalDate threeYearsAgo = LocalDate.now().minusYears(3);
            for (FundDividendRecord r : records) {
                if (r.getExDate() == null || r.getExDate().isBefore(threeYearsAgo)) continue;
                int year = r.getExDate().getYear();
                BigDecimal amount = r.getDividendPerShare() != null ? r.getDividendPerShare() : BigDecimal.ZERO;
                yearlyTotals.merge(year, amount, BigDecimal::add);
            }

            if (yearlyTotals.size() < 2) {
                return 0;
            }

            // 取最早和最近两个完整年度
            List<Integer> years = new ArrayList<>(yearlyTotals.keySet());
            int firstYear = years.get(0);
            int lastYear = years.get(years.size() - 1);
            BigDecimal firstYearTotal = yearlyTotals.get(firstYear);
            BigDecimal lastYearTotal = yearlyTotals.get(lastYear);

            if (firstYearTotal.compareTo(BigDecimal.ZERO) <= 0
                    || lastYearTotal.compareTo(BigDecimal.ZERO) <= 0) {
                return 0;
            }

            int yearSpan = lastYear - firstYear;
            if (yearSpan <= 0) {
                return 0;
            }

            // CAGR = (末值/初值)^(1/年数) - 1
            double ratio = lastYearTotal.divide(firstYearTotal, 6, RoundingMode.HALF_UP).doubleValue();
            double cagr = Math.pow(ratio, 1.0 / yearSpan) - 1;

            // 钳制到合理范围 [-20%, 50%]
            cagr = Math.max(-0.20, Math.min(0.50, cagr));

            log.info("基金 {} 历史 CAGR: {:.2f}% ({}年→{}年, {}→{})",
                    fundCode, cagr * 100, firstYear, lastYear, firstYearTotal, lastYearTotal);

            return cagr;
        } catch (Exception e) {
            log.warn("计算基金 {} CAGR 失败: {}", fundCode, e.getMessage());
            return 0;
        }
    }
}
