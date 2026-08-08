package com.fundtracker.scheduler;

import com.fundtracker.service.HoldingService;
import com.fundtracker.service.MonitorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：每天 22:00 全量刷新所有持仓的净值、市值、成本指标、分红预测
 * 22:00 覆盖 A 股(15:00)、港股(16:00)、美股(次日凌晨)三市场收盘后的最新净值
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundNavScheduler {

    private final HoldingService holdingService;
    private final MonitorLogService monitorLogService;

    @Scheduled(cron = "0 0 22 * * ?")
    public void refreshAllNav() {
        long startMs = System.currentTimeMillis();
        log.info("===== 开始定时全量刷新持仓净值 =====");
        try {
            holdingService.refreshAllHoldings();
            log.info("===== 定时全量刷新持仓净值完成 =====");
            monitorLogService.record("净值数据刷新", true, System.currentTimeMillis() - startMs, "全部持仓净值刷新完成");
        } catch (Exception e) {
            log.error("定时全量刷新持仓净值失败: {}", e.getMessage());
            monitorLogService.record("净值数据刷新", false, System.currentTimeMillis() - startMs,
                    "失败: " + e.getMessage());
        }
    }
}