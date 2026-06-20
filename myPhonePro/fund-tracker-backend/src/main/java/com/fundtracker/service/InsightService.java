package com.fundtracker.service;

import com.fundtracker.model.dto.AnnualInsightResp;
import com.fundtracker.model.dto.MonthlyDetailResp;
import com.fundtracker.model.dto.MonthlyInsightResp;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightService {

    private final DividendEventRepository eventRepository;
    private final HoldingRepository holdingRepository;

    public MonthlyInsightResp getMonthlyInsight(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<DividendEvent> monthEvents = eventRepository.findByDateBetweenOrderByDate(start, end);

        // 最丰厚来源：当月事件中预测分红最大的持仓
        List<Holding> holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        Map<String, Holding> holdingMap = holdings.stream()
                .collect(Collectors.toMap(Holding::getId, h -> h));

        String richestHoldingName = "暂无";
        BigDecimal richestAmount = BigDecimal.ZERO;

        // 本月动态
        long payoutCount = monthEvents.stream()
                .filter(e -> e.getType() == EventType.payout)
                .count();
        Set<String> fundIdsWithEvents = monthEvents.stream()
                .map(DividendEvent::getHoldingId)
                .collect(Collectors.toSet());

        // 下次分红：取未来最近的 payout 事件
        LocalDate today = LocalDate.now();
        String nextName = "--";
        BigDecimal nextAmount = BigDecimal.ZERO;
        int nextDays = 0;

        for (DividendEvent event : monthEvents) {
            Holding h = holdingMap.get(event.getHoldingId());
            if (h != null && h.getPredictedDividend().compareTo(richestAmount) > 0) {
                richestAmount = h.getPredictedDividend();
                richestHoldingName = h.getName();
            }

            // 找最近 payout
            if (event.getType() == EventType.payout) {
                LocalDate eventDate = event.getDate();
                if (!eventDate.isBefore(today)) {
                    int days = (int) ChronoUnit.DAYS.between(today, eventDate);
                    if (nextDays == 0 || days < nextDays) {
                        nextDays = days;
                        nextName = h != null ? h.getName() : "--";
                        nextAmount = event.getAmount();
                    }
                }
            }
        }

        return MonthlyInsightResp.builder()
                .richestSource(MonthlyInsightResp.RichestSource.builder()
                        .holdingName(richestHoldingName)
                        .amount(richestAmount)
                        .build())
                .monthlyActivity(MonthlyInsightResp.MonthlyActivity.builder()
                        .payoutCount((int) payoutCount)
                        .fundCount(fundIdsWithEvents.size())
                        .build())
                .nextDividend(MonthlyInsightResp.NextDividend.builder()
                        .holdingName(nextName)
                        .amount(nextAmount)
                        .daysRemaining(nextDays)
                        .build())
                .build();
    }

    public MonthlyDetailResp getMonthlyDetail(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<DividendEvent> monthEvents = eventRepository.findByDateBetweenOrderByDate(start, end);

        // 按 holdingId 分组
        Map<String, List<DividendEvent>> grouped = monthEvents.stream()
                .collect(Collectors.groupingBy(DividendEvent::getHoldingId));

        List<MonthlyDetailResp.FundDividendDetail> details = new ArrayList<>();

        for (Map.Entry<String, List<DividendEvent>> entry : grouped.entrySet()) {
            List<DividendEvent> events = entry.getValue();
            if (events.isEmpty()) continue;

            DividendEvent first = events.get(0);

            // 收集事件类型中文名（去重）
            List<String> eventTypeNames = events.stream()
                    .map(e -> {
                        switch (e.getType()) {
                            case registration: return "权益登记";
                            case ex_dividend:  return "除权除息";
                            case payout:       return "派息发放";
                            case announcement: return "公告";
                            default:           return "其他";
                        }
                    })
                    .distinct()
                    .collect(Collectors.toList());

            // 总金额 = sum of all event amounts for this fund
            BigDecimal totalAmount = events.stream()
                    .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            details.add(MonthlyDetailResp.FundDividendDetail.builder()
                    .holdingName(first.getHoldingName())
                    .amount(totalAmount)
                    .code("")
                    .eventTypes(eventTypeNames)
                    .build());
        }

        // 按金额降序排序
        details.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));

        return MonthlyDetailResp.builder().details(details).build();
    }

    public AnnualInsightResp getAnnualInsight(int year) {
        // 全年12个月的数据
        List<AnnualInsightResp.MonthBar> bars = new ArrayList<>();
        BigDecimal peakAmount = BigDecimal.ZERO;
        int peakMonth = 0;

        for (int m = 1; m <= 12; m++) {
            LocalDate start = LocalDate.of(year, m, 1);
            LocalDate end = start.plusMonths(1).minusDays(1);

            BigDecimal monthAmount = eventRepository.findByDateBetweenOrderByDate(start, end)
                    .stream()
                    .filter(e -> e.getType() == EventType.payout)
                    .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            bars.add(AnnualInsightResp.MonthBar.builder()
                    .month(m)
                    .amount(monthAmount)
                    .percentage(0) // 暂时填0，下面统一计算
                    .build());

            if (monthAmount.compareTo(peakAmount) > 0) {
                peakAmount = monthAmount;
                peakMonth = m;
            }
        }

        // 计算百分比
        BigDecimal finalPeak = peakAmount.compareTo(BigDecimal.ZERO) > 0 ? peakAmount : BigDecimal.ONE;
        for (AnnualInsightResp.MonthBar bar : bars) {
            bar.setPercentage(bar.getAmount().multiply(BigDecimal.valueOf(100))
                    .divide(finalPeak, 0, java.math.RoundingMode.HALF_UP).intValue());
        }

        // 基金排名
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<DividendEvent> yearEvents = eventRepository.findByDateBetweenOrderByDate(yearStart, yearEnd)
                .stream()
                .filter(e -> e.getType() == EventType.payout)
                .collect(Collectors.toList());

        Map<String, BigDecimal> fundTotals = new HashMap<>();
        Map<String, String> fundNameMap = new HashMap<>();
        for (DividendEvent e : yearEvents) {
            fundTotals.merge(e.getHoldingId(), e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
            fundNameMap.putIfAbsent(e.getHoldingId(), e.getHoldingName());
        }

        BigDecimal maxFundAmount = fundTotals.values().stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

        BigDecimal finalMaxFund = maxFundAmount.compareTo(BigDecimal.ZERO) > 0 ? maxFundAmount : BigDecimal.ONE;

        List<AnnualInsightResp.FundRank> ranks = fundTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    BigDecimal amt = entry.getValue();
                    return AnnualInsightResp.FundRank.builder()
                            .holdingName(fundNameMap.get(entry.getKey()))
                            .amount(amt)
                            .percentage(amt.multiply(BigDecimal.valueOf(100))
                                    .divide(finalMaxFund, 0, java.math.RoundingMode.HALF_UP).intValue())
                            .rank(0) // 事后赋值
                            .build();
                })
                .collect(Collectors.toList());

        for (int i = 0; i < ranks.size(); i++) {
            ranks.get(i).setRank(i + 1);
        }

        // 统计
        long totalPayoutCount = yearEvents.size();
        int fundCount = fundTotals.size();
        java.math.BigDecimal totalDividend = bars.stream()
                .map(AnnualInsightResp.MonthBar::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String peakMonthLabel = peakMonth > 0 ? peakMonth + "月" : "--";

        return AnnualInsightResp.builder()
                .summary(AnnualInsightResp.Summary.builder()
                        .totalDividend(totalDividend)
                        .totalPayoutCount((int) totalPayoutCount)
                        .fundCount(fundCount)
                        .peakMonth(peakMonthLabel)
                        .peakMonthAmount(peakAmount)
                        .build())
                .monthlyBars(bars)
                .fundRanks(ranks)
                .build();
    }
}
