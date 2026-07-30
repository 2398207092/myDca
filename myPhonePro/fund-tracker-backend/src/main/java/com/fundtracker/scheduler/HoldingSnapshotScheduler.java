package com.fundtracker.scheduler;

import com.fundtracker.service.HoldingSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 持仓快照定时任务：每 5 天凌晨 1:00 执行。
 * cron "0 0 1 1/5 * ?" = 每月 1、6、11、16、21、26 日凌晨 1:00。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingSnapshotScheduler {

    private final HoldingSnapshotService holdingSnapshotService;

    @Scheduled(cron = "0 0 1 1/5 * ?")
    public void snapshotTask() {
        log.info("开始执行持仓快照定时任务...");
        try {
            holdingSnapshotService.snapshotAllHoldings();
            log.info("持仓快照定时任务完成");
        } catch (Exception e) {
            log.error("持仓快照定时任务执行失败", e);
        }
    }
}
