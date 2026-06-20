package com.fundtracker.service;

import com.fundtracker.model.dto.ValueChangeDTO;
import com.fundtracker.model.entity.FundNavRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.FundNavRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValueChangeService {

    private final HoldingRepository holdingRepository;
    private final FundNavRecordRepository fundNavRecordRepository;

    public ValueChangeDTO getValueChange() {
        List<Holding> holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        if (holdings.isEmpty()) {
            return ValueChangeDTO.builder()
                    .currentValue(BigDecimal.ZERO)
                    .periods(Map.of())
                    .build();
        }

        // Current total value
        BigDecimal currentValue = holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        Map<String, ValueChangeDTO.PeriodChange> periods = new LinkedHashMap<>();
        periods.put("week", computePeriodChange(holdings, today, 7, currentValue));
        periods.put("month", computePeriodChange(holdings, today, 30, currentValue));
        periods.put("year", computePeriodChange(holdings, today, 365, currentValue));

        return ValueChangeDTO.builder()
                .currentValue(currentValue)
                .periods(periods)
                .build();
    }

    private ValueChangeDTO.PeriodChange computePeriodChange(List<Holding> holdings, LocalDate today, int daysAgo, BigDecimal currentValue) {
        LocalDate pastDate = today.minusDays(daysAgo);

        BigDecimal pastValue = BigDecimal.ZERO;
        List<ValueChangeDTO.HoldingDetail> details = new ArrayList<>();

        for (Holding h : holdings) {
            String code = h.getCode();
            BigDecimal shares = h.getShares() != null ? h.getShares() : BigDecimal.ZERO;
            BigDecimal currentMV = h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO;

            BigDecimal pastMV;
            if (code != null && !code.isBlank() && shares.compareTo(BigDecimal.ZERO) > 0) {
                // Fund holding — look up historical NAV
                Optional<FundNavRecord> navOpt = fundNavRecordRepository
                        .findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(code, pastDate);
                if (navOpt.isPresent()) {
                    BigDecimal pastNav = navOpt.get().getUnitNav();
                    pastMV = shares.multiply(pastNav).setScale(2, RoundingMode.HALF_UP);
                } else {
                    // No NAV at that date — use earliest available NAV, or 0
                    Optional<FundNavRecord> earliestNav = fundNavRecordRepository
                            .findTopByFundCodeOrderByNavDateAsc(code);
                    if (earliestNav.isPresent()) {
                        pastMV = shares.multiply(earliestNav.get().getUnitNav()).setScale(2, RoundingMode.HALF_UP);
                    } else {
                        pastMV = BigDecimal.ZERO;
                    }
                }
            } else {
                // Manual asset — assume value unchanged
                pastMV = currentMV;
            }

            BigDecimal change = currentMV.subtract(pastMV);
            BigDecimal percent = pastMV.compareTo(BigDecimal.ZERO) > 0
                    ? change.divide(pastMV, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            pastValue = pastValue.add(pastMV);

            // Include in details only if the holding has non-trivial value or change
            if (currentMV.compareTo(BigDecimal.ZERO) > 0 || change.compareTo(BigDecimal.ZERO) != 0) {
                details.add(ValueChangeDTO.HoldingDetail.builder()
                        .holdingId(h.getId())
                        .name(h.getName())
                        .code(code)
                        .change(change.setScale(2, RoundingMode.HALF_UP))
                        .percent(percent.setScale(2, RoundingMode.HALF_UP))
                        .currentValue(currentMV)
                        .pastValue(pastMV)
                        .build());
            }
        }

        BigDecimal totalChange = currentValue.subtract(pastValue);
        BigDecimal totalPercent = pastValue.compareTo(BigDecimal.ZERO) > 0
                ? totalChange.divide(pastValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return ValueChangeDTO.PeriodChange.builder()
                .change(totalChange.setScale(2, RoundingMode.HALF_UP))
                .percent(totalPercent.setScale(2, RoundingMode.HALF_UP))
                .pastValue(pastValue.setScale(2, RoundingMode.HALF_UP))
                .details(details)
                .build();
    }
}
