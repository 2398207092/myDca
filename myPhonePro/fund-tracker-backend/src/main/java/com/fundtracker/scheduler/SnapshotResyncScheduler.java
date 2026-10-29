package com.fundtracker.scheduler;

import com.fundtracker.service.MonitorLogService;
import com.fundtracker.service.SnapshotGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 当日快照重拍定时任务（P1-22）。
 * 22:00 FundNavScheduler 刷新净值后，白天/当晚早前生成的快照仍是前日净值口径；
 * 本任务在 22:45（净值刷新完成后）对当日已有快照且市值/份额与持仓当前值不一致的持仓
 * 做覆盖式重拍，消除「D 日快照实为 D-1 估值」的错位。
 *
 * 数据驱动：无当日快照或快照已一致时不做任何写入，日常为空转。
 * 与 23:30 的持仓快照任务互不影响（重拍先删后插，满足 uk_holding_date 唯一约束）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotResyncScheduler {

    private final SnapshotGenerationService snapshotGenerationService;
    private final MonitorLogService monitorLogService;

    @Scheduled(cron = "0 45 22 * * ?")
    public void resyncTodaySnapshots() {
        long startMs = System.currentTimeMillis();
        log.info("===== 开始当日快照重拍检查 =====");
        try {
            int count = snapshotGenerationService.resnapshotStaleToday();
            if (count > 0) {
                log.info("===== 当日快照重拍完成：{} 条 =====", count);
                monitorLogService.record("当日快照重拍", true, System.currentTimeMillis() - startMs,
                        "重拍 " + count + " 条过期快照");
            } else {
                log.info("===== 当日快照无过期数据，跳过重拍 =====");
            }
        } catch (Exception e) {
            log.error("当日快照重拍失败", e);
            monitorLogService.record("当日快照重拍", false, System.currentTimeMillis() - startMs,
                    "失败: " + e.getMessage());
        }
    }
}
