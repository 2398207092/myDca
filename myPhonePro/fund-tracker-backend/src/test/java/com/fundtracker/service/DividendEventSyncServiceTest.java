package com.fundtracker.service;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DividendEventSyncService 单元测试（P0-6：同步 upsert 化，cancelled 状态必须保留）
 */
@ExtendWith(MockitoExtension.class)
class DividendEventSyncServiceTest {

    @Mock private FundDividendRecordRepository fundDividendRecordRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private DividendEventRepository dividendEventRepository;
    @Mock private TransactionService transactionService;

    private DividendEventSyncService syncService;

    private Holding holding;
    private FundDividendRecord record;

    @BeforeEach
    void setUp() {
        syncService = new DividendEventSyncService(
                fundDividendRecordRepository, holdingRepository,
                dividendEventRepository, transactionService);

        holding = Holding.builder()
                .id("h-1").name("测试基金").code("000001")
                .type(HoldingType.fund).userId("user-1").deleted(false)
                .build();
        record = FundDividendRecord.builder()
                .id("r-1").fundCode("000001")
                .regDate(LocalDate.of(2026, 9, 1))
                .exDate(LocalDate.of(2026, 9, 5))
                .payDate(LocalDate.of(2026, 9, 10))
                .dividendPerShare(new BigDecimal("0.1195"))
                .build();
    }

    private DividendEvent payoutEvent(EventStatus status, String amount) {
        return DividendEvent.builder()
                .id("e-pay").holdingId("h-1").userId("user-1")
                .holdingName("测试基金").type(EventType.payout)
                .date(LocalDate.of(2026, 9, 10))
                .amount(new BigDecimal(amount)).status(status)
                .participated(true).converted(false)
                .build();
    }

    @Test
    @DisplayName("P0-6 核心：已取消（cancelled）的分红事件同步后仍保留 cancelled 与原金额，不被复活为 pending")
    void sync_preservesCancelledStatus() {
        when(holdingRepository.findByCodeAndUserIdAndDeletedFalse("000001", "user-1"))
                .thenReturn(List.of(holding));
        when(fundDividendRecordRepository.findByFundCodeOrderByExDateDesc("000001"))
                .thenReturn(List.of(record));
        when(transactionService.getFirstTransactionDate("h-1")).thenReturn(LocalDate.of(2026, 1, 1));
        when(transactionService.calculateSharesAtDate("h-1", LocalDate.of(2026, 9, 5)))
                .thenReturn(new BigDecimal("1000"));
        DividendEvent cancelled = payoutEvent(EventStatus.cancelled, "119.50");
        when(dividendEventRepository.findByHoldingIdOrderByDateDesc("h-1"))
                .thenReturn(List.of(cancelled));

        syncService.syncEventsForFund("000001", "user-1");

        ArgumentCaptor<DividendEvent> captor = ArgumentCaptor.forClass(DividendEvent.class);
        verify(dividendEventRepository, atLeastOnce()).save(captor.capture());
        DividendEvent savedPayout = captor.getAllValues().stream()
                .filter(e -> e.getType() == EventType.payout)
                .findFirst().orElseThrow();
        assertEquals(EventStatus.cancelled, savedPayout.getStatus(), "取消状态必须保留（不得复活为 pending）");
        assertEquals(0, new BigDecimal("119.50").compareTo(savedPayout.getAmount()), "原金额不得被重算");
        verify(dividendEventRepository, never()).delete(any(DividendEvent.class));
    }

    @Test
    @DisplayName("回归：已到账（distributed）事件同步后仍保留 distributed 与原金额")
    void sync_preservesDistributedStatus() {
        when(holdingRepository.findByCodeAndUserIdAndDeletedFalse("000001", "user-1"))
                .thenReturn(List.of(holding));
        when(fundDividendRecordRepository.findByFundCodeOrderByExDateDesc("000001"))
                .thenReturn(List.of(record));
        when(transactionService.getFirstTransactionDate("h-1")).thenReturn(LocalDate.of(2026, 1, 1));
        when(transactionService.calculateSharesAtDate("h-1", LocalDate.of(2026, 9, 5)))
                .thenReturn(new BigDecimal("1000"));
        DividendEvent distributed = payoutEvent(EventStatus.distributed, "119.50");
        when(dividendEventRepository.findByHoldingIdOrderByDateDesc("h-1"))
                .thenReturn(List.of(distributed));

        syncService.syncEventsForFund("000001", "user-1");

        ArgumentCaptor<DividendEvent> captor = ArgumentCaptor.forClass(DividendEvent.class);
        verify(dividendEventRepository, atLeastOnce()).save(captor.capture());
        DividendEvent savedPayout = captor.getAllValues().stream()
                .filter(e -> e.getType() == EventType.payout)
                .findFirst().orElseThrow();
        assertEquals(EventStatus.distributed, savedPayout.getStatus());
        assertEquals(0, new BigDecimal("119.50").compareTo(savedPayout.getAmount()));
    }

    @Test
    @DisplayName("无已有事件时三条事件全部新建（pending）")
    void sync_createsAllThreeWhenNoneExist() {
        when(holdingRepository.findByCodeAndUserIdAndDeletedFalse("000001", "user-1"))
                .thenReturn(List.of(holding));
        when(fundDividendRecordRepository.findByFundCodeOrderByExDateDesc("000001"))
                .thenReturn(List.of(record));
        when(transactionService.getFirstTransactionDate("h-1")).thenReturn(LocalDate.of(2026, 1, 1));
        when(transactionService.calculateSharesAtDate("h-1", LocalDate.of(2026, 9, 5)))
                .thenReturn(new BigDecimal("1000"));
        when(dividendEventRepository.findByHoldingIdOrderByDateDesc("h-1"))
                .thenReturn(List.of());

        int created = syncService.syncEventsForFund("000001", "user-1");

        assertEquals(3, created);
        ArgumentCaptor<DividendEvent> captor = ArgumentCaptor.forClass(DividendEvent.class);
        verify(dividendEventRepository, times(3)).save(captor.capture());
        captor.getAllValues().forEach(e ->
                assertEquals(EventStatus.pending, e.getStatus(), "新事件应为 pending"));
    }

    @Test
    @DisplayName("源记录为空 → 跳过同步且不清理任何事件（防止源数据异常导致分红复活）")
    void sync_skipsWhenSourceEmpty() {
        when(holdingRepository.findByCodeAndUserIdAndDeletedFalse("000001", "user-1"))
                .thenReturn(List.of(holding));
        when(fundDividendRecordRepository.findByFundCodeOrderByExDateDesc("000001"))
                .thenReturn(List.of());

        int created = syncService.syncEventsForFund("000001", "user-1");

        assertEquals(0, created);
        verify(dividendEventRepository, never()).deleteByHoldingId(any());
        verify(dividendEventRepository, never()).delete(any(DividendEvent.class));
        verify(dividendEventRepository, never()).save(any(DividendEvent.class));
    }
}
