package com.fundtracker.scheduler;

import com.fundtracker.service.ExchangeRateService;
import com.fundtracker.service.MonitorLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExchangeRateScheduler 单元测试
 * 覆盖：P1-28 汇率定时刷新失败必须写入监控日志（不再仅 log.warn 静默）
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateSchedulerTest {

    @Mock private ExchangeRateService exchangeRateService;
    @Mock private MonitorLogService monitorLogService;

    private ExchangeRateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExchangeRateScheduler(exchangeRateService, monitorLogService);
    }

    @Test
    @DisplayName("refresh 抛异常 → 必须记录 MonitorLog 失败，任务不向外抛出")
    void refreshThrowsException_recordsFailure() {
        doThrow(new RuntimeException("API 超时")).when(exchangeRateService).refreshScheduled();

        scheduler.refreshExchangeRates();

        verify(monitorLogService).record(eq("汇率数据刷新"), eq(false), anyLong(), contains("API 超时"));
    }

    @Test
    @DisplayName("API 不可达（refreshScheduled 返回 false）→ 必须记录 MonitorLog 失败")
    void apiUnreachable_recordsFailure() {
        when(exchangeRateService.refreshScheduled()).thenReturn(false);

        scheduler.refreshExchangeRates();

        verify(monitorLogService).record(eq("汇率数据刷新"), eq(false), anyLong(), anyString());
    }

    @Test
    @DisplayName("刷新成功 → 记录 MonitorLog 成功")
    void success_recordsSuccess() {
        when(exchangeRateService.refreshScheduled()).thenReturn(true);

        scheduler.refreshExchangeRates();

        verify(monitorLogService).record(eq("汇率数据刷新"), eq(true), anyLong(), anyString());
    }
}
