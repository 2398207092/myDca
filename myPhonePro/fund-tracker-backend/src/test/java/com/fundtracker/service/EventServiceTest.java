package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.CreateTransactionReq;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventStatus;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EventService.markDistributed 单元测试
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private DividendEventRepository eventRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private ManualAssetService manualAssetService;
    @Mock private TransactionService transactionService;
    @Mock private FundNavScrapeService fundNavScrapeService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, holdingRepository, manualAssetService,
                transactionService, fundNavScrapeService);
    }

    @Test
    @DisplayName("标记分红到账 → 状态变为 distributed + 现金增加")
    void markDistributed() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .type(EventType.payout)
                .date(LocalDate.of(2026, 6, 29))
                .amount(new BigDecimal("500"))
                .status(EventStatus.pending)
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .name("红利低波")
                .userId("user-1")
                .accountId("acc-1")
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));

        eventService.markDistributed("evt-1", "user-1");

        // 状态变为 distributed
        assertEquals(EventStatus.distributed, event.getStatus());
        // 累计分红增加
        assertEquals(new BigDecimal("500"), holding.getTotalDividendReceived());
        // 现金应增加 500（P1-26：进持仓归属账户，而非主现金）
        verify(manualAssetService).adjustCash("h-1", "acc-1", new BigDecimal("500"), "user-1");
        // 不允许再落到主现金两参重载
        verify(manualAssetService, never()).adjustCash(any(), any(), any());
    }

    @Test
    @DisplayName("标记分红到账 → 持仓未归属账户时退回主现金")
    void markDistributed_noAccount_fallsBackToMainCash() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .type(EventType.payout)
                .date(LocalDate.of(2026, 6, 29))
                .amount(new BigDecimal("300"))
                .status(EventStatus.pending)
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .name("红利低波")
                .userId("user-1")
                .accountId(null)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));

        eventService.markDistributed("evt-1", "user-1");

        // accountId 为空 → 走主现金（历史行为）
        verify(manualAssetService).adjustCash("h-1", null, new BigDecimal("300"), "user-1");
    }

    @Test
    @DisplayName("金额为0的分红 → 不调现金")
    void markDistributed_zeroAmount() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .type(EventType.payout)
                .date(LocalDate.of(2026, 6, 29))
                .amount(BigDecimal.ZERO)
                .status(EventStatus.pending)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));

        eventService.markDistributed("evt-1", "user-1");

        // 金额为0时不调现金
        verify(manualAssetService, never()).adjustCash(any(), any(), any(), any());
        verify(manualAssetService, never()).adjustCash(any(), any(), any());
    }

    @Test
    @DisplayName("分红转复投 → 扣现金与失败回补都走持仓归属账户")
    void convertToReinvest_deductsAndRefundsFromHoldingAccount() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .type(EventType.payout)
                .date(LocalDate.of(2026, 6, 29))
                .amount(new BigDecimal("100"))
                .status(EventStatus.distributed)
                .converted(false)
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .name("红利低波")
                .userId("user-1")
                .accountId("acc-1")
                .latestPrice(new BigDecimal("2.5"))
                .dividendReinvest(false)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        // 复投买入失败 → 触发现金回补
        when(transactionService.createTransaction(any(CreateTransactionReq.class), eq("user-1")))
                .thenThrow(new RuntimeException("净值抓取失败"));

        assertThrows(RuntimeException.class, () -> eventService.convertToReinvest("evt-1", "user-1"));

        // 扣现金 -100 与回补 +100 都必须走持仓归属账户 acc-1
        verify(manualAssetService).adjustCash("h-1", "acc-1", new BigDecimal("-100"), "user-1");
        verify(manualAssetService).adjustCash("h-1", "acc-1", new BigDecimal("100"), "user-1");
    }

    @Test
    @DisplayName("取消已到账分红 → 抛异常")
    void cancelEvent_distributed_fails() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .status(EventStatus.distributed)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));

        assertThrows(BusinessException.class, () -> eventService.cancelEvent("evt-1", "user-1"));
    }

    @Test
    @DisplayName("取消待处理分红 → 状态变为 cancelled")
    void cancelEvent_pending_success() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .status(EventStatus.pending)
                .build();

        when(eventRepository.findByIdAndUserId("evt-1", "user-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));

        eventService.cancelEvent("evt-1", "user-1");

        assertEquals(EventStatus.cancelled, event.getStatus());
    }
}
