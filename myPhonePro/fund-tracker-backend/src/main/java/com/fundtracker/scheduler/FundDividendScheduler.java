package com.fundtracker.scheduler;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.service.DividendEventSyncService;
import com.fundtracker.service.FundDividendScrapeService;
import com.fundtracker.service.HoldingService;
import com.fundtracker.service.MonitorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务：每天凌晨 6:00 检查所有持仓的分红数据更新，并联动刷新持仓指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundDividendScheduler {

    private final FundDividendScrapeService scrapeService;
    private final HoldingRepository holdingRepository;
    private final DividendEventSyncService dividendEventSyncService;
    private final HoldingService holdingService;
    private final MonitorLogService monitorLogService;

    /**
     * 每天早上 6:00 运行，扫描所有持仓并抓取最新分红数据，抓完后刷新持仓指标
     * @return 新增记录数
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public int refreshAllHoldingsDividendData() {
        long startMs = System.currentTimeMillis();
        log.info("===== 开始定时刷新所有持仓分红数据 =====");

        try {
            List<String> fundCodes = holdingRepository.findByDeletedFalseOrderByMarketValueDesc()
                    .stream()
                    .map(Holding::getCode)
                    .distinct()
                    .collect(Collectors.toList());

            if (fundCodes.isEmpty()) {
                log.info("无持仓，跳过定时分红抓取");
                monitorLogService.record("分红数据刷新", true, System.currentTimeMillis() - startMs, "无持仓，跳过");
                return 0;
            }

            log.info("需要刷新 {} 只基金的分红数据", fundCodes.size());
            int total = scrapeService.scrapeMultiple(fundCodes);

            // 抓取完成后同步分红事件到日历（定时任务无 userId，传 null 由内部从持仓获取）
            int synced = dividendEventSyncService.syncAllEvents(null);
            log.info("分红数据刷新完成，新增 {} 条记录，同步 {} 条事件", total, synced);

            // 分红数据变化后，刷新所有持仓的预测分红、已收分红等指标
            log.info("分红数据已更新，联动刷新持仓指标...");
            holdingService.refreshAllHoldings();

            log.info("===== 定时刷新完成 =====");
            monitorLogService.record("分红数据刷新", true, System.currentTimeMillis() - startMs,
                    String.format("刷新%d只基金,新增%d条,同步%d条事件", fundCodes.size(), total, synced));
            return total;

        } catch (Exception e) {
            log.error("定时刷新分红数据失败: {}", e.getMessage());
            monitorLogService.record("分红数据刷新", false, System.currentTimeMillis() - startMs,
                    "失败: " + e.getMessage());
            return 0;
        }
    }
}
