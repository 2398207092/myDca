package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, holdingRepository, manualAssetService);
    }

    @Test
    @DisplayName("标记分红到账 → 状态变为 distributed + 现金增加")
    void markDistributed() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .amount(new BigDecimal("500"))
                .status(EventStatus.pending)
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .name("红利低波")
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));

        eventService.markDistributed("evt-1");

        // 状态变为 distributed
        assertEquals(EventStatus.distributed, event.getStatus());
        // 累计分红增加
        assertEquals(new BigDecimal("500"), holding.getTotalDividendReceived());
        // 现金应增加 500
        verify(manualAssetService).adjustCash("h-1", new BigDecimal("500"));
    }

    @Test
    @DisplayName("金额为0的分红 → 不调现金")
    void markDistributed_zeroAmount() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .holdingId("h-1")
                .amount(BigDecimal.ZERO)
                .status(EventStatus.pending)
                .build();

        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));

        eventService.markDistributed("evt-1");

        // 金额为0时不调现金
        verify(manualAssetService, never()).adjustCash(any(), any());
    }

    @Test
    @DisplayName("取消已到账分红 → 抛异常")
    void cancelEvent_distributed_fails() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .status(EventStatus.distributed)
                .build();

        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(event));

        assertThrows(BusinessException.class, () -> eventService.cancelEvent("evt-1"));
    }

    @Test
    @DisplayName("取消待处理分红 → 状态变为 cancelled")
    void cancelEvent_pending_success() {
        DividendEvent event = DividendEvent.builder()
                .id("evt-1")
                .status(EventStatus.pending)
                .build();

        when(eventRepository.findById("evt-1")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(DividendEvent.class))).thenAnswer(i -> i.getArgument(0));

        eventService.cancelEvent("evt-1");

        assertEquals(EventStatus.cancelled, event.getStatus());
    }
}
