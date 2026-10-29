package com.fundtracker.service;

import com.fundtracker.model.dto.MonthlyDetailResp;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * InsightService.getMonthlyDetail 单元测试（P1-2：月度分红明细只统计 payout 到账金额）
 */
@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private DividendEventRepository eventRepository;
    @Mock private HoldingRepository holdingRepository;

    private InsightService insightService;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(eventRepository, holdingRepository);
    }

    private DividendEvent event(String holdingId, String name, EventType type, String amount) {
        return DividendEvent.builder()
                .id("evt-" + type + "-" + holdingId)
                .holdingId(holdingId)
                .holdingName(name)
                .type(type)
                .date(LocalDate.of(2026, 6, 15))
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    @DisplayName("同一基金三类等额事件 → 月度明细只算 payout 一份（P1-2）")
    void monthlyDetail_countsPayoutOnly() {
        // 一次分红生成 registration / ex_dividend / payout 三条等额事件（各 100）
        List<DividendEvent> events = List.of(
                event("h-1", "红利低波", EventType.registration, "100"),
                event("h-1", "红利低波", EventType.ex_dividend, "100"),
                event("h-1", "红利低波", EventType.payout, "100"));

        when(eventRepository.findByDateBetweenAndUserIdOrderByDate(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "user-1"))
                .thenReturn(events);

        MonthlyDetailResp resp = insightService.getMonthlyDetail(2026, 6, "user-1");

        assertEquals(1, resp.getDetails().size());
        // 修复前：100×3=300；修复后：只算 payout = 100（用户语义 = 当月实际到账）
        assertEquals(0, new BigDecimal("100").compareTo(resp.getDetails().get(0).getAmount()),
                "月度明细金额必须是实际到账的 payout 金额，实际 " + resp.getDetails().get(0).getAmount());
        // 事件类型中文名仍完整展示（去重）
        assertEquals(3, resp.getDetails().get(0).getEventTypes().size());
    }

    @Test
    @DisplayName("多基金混合 → 只按各自 payout 汇总（P1-2）")
    void monthlyDetail_multipleFunds() {
        List<DividendEvent> events = List.of(
                event("h-1", "红利低波", EventType.payout, "100"),
                event("h-2", "沪深300", EventType.registration, "50"),
                event("h-2", "沪深300", EventType.ex_dividend, "50"),
                event("h-2", "沪深300", EventType.payout, "50"));

        when(eventRepository.findByDateBetweenAndUserIdOrderByDate(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "user-1"))
                .thenReturn(events);

        MonthlyDetailResp resp = insightService.getMonthlyDetail(2026, 6, "user-1");

        assertEquals(2, resp.getDetails().size());
        // 按金额降序：红利低波 100 在前
        assertEquals("红利低波", resp.getDetails().get(0).getHoldingName());
        assertEquals(0, new BigDecimal("100").compareTo(resp.getDetails().get(0).getAmount()));
        assertEquals("沪深300", resp.getDetails().get(1).getHoldingName());
        assertEquals(0, new BigDecimal("50").compareTo(resp.getDetails().get(1).getAmount()));
    }
}
