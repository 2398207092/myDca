package com.fundtracker.scheduler;

import com.fundtracker.service.ExchangeRateService;
import com.fundtracker.service.MonitorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：每天 8:00 刷新汇率（P1-28）。
 * 此前 refresh 只有手动接口，失败仅 log.warn，汇率最长 19 天未更新无任何告警。
 * 失败（API 不可达或抛异常）时写入监控日志，使汇率停更对监控可见。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

    private final ExchangeRateService exchangeRateService;
    private final MonitorLogService monitorLogService;

    @Scheduled(cron = "0 0 8 * * ?")
    public void refreshExchangeRates() {
        long startMs = System.currentTimeMillis();
        log.info("===== 开始定时刷新汇率 =====");
        try {
            boolean ok = exchangeRateService.refreshScheduled();
            if (ok) {
                log.info("===== 定时刷新汇率完成 =====");
                monitorLogService.record("汇率数据刷新", true, System.currentTimeMillis() - startMs, "汇率刷新完成");
            } else {
                log.error("定时刷新汇率失败: API 不可达，沿用本地缓存值");
                monitorLogService.record("汇率数据刷新", false, System.currentTimeMillis() - startMs,
                        "失败: API 不可达，沿用本地缓存值");
            }
        } catch (Exception e) {
            log.error("定时刷新汇率失败: {}", e.getMessage());
            monitorLogService.record("汇率数据刷新", false, System.currentTimeMillis() - startMs,
                    "失败: " + e.getMessage());
        }
    }
}
