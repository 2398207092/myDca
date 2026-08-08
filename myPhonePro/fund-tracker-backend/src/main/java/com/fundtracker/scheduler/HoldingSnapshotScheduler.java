package com.fundtracker.scheduler;

import com.fundtracker.service.SnapshotGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 持仓快照定时任务：每 5 天 23:30 执行（在 22:00 净值刷新之后）。
 * cron "0 30 23 1/5 * ?" = 每月 1、6、11、16、21、26 日 23:30。
 * 顺序保证：6:00 分红刷新 → 22:00 净值刷新 → 23:30 快照（当天最新数据）→ 次日 3:00 审计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingSnapshotScheduler {

    private final SnapshotGenerationService snapshotGenerationService;

    @Scheduled(cron = "0 30 23 1/5 * ?")
    public void snapshotTask() {
        log.info("开始执行持仓快照定时任务...");
        try {
            snapshotGenerationService.snapshotAllHoldings();
            log.info("持仓快照定时任务完成");
        } catch (Exception e) {
            log.error("持仓快照定时任务执行失败", e);
        }
    }
}