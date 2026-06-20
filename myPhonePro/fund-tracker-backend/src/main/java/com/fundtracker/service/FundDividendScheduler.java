package com.fundtracker.service;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务：每天凌晨检查所有持仓的分红数据更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundDividendScheduler {

    private final FundDividendScrapeService scrapeService;
    private final HoldingRepository holdingRepository;
    private final DividendEventSyncService dividendEventSyncService;

    /**
     * 每天早上 6:00 运行，扫描所有持仓并抓取最新分红数据
     * @return 新增记录数
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public int refreshAllHoldingsDividendData() {
        log.info("===== 开始定时刷新所有持仓分红数据 =====");

        try {
            List<String> fundCodes = holdingRepository.findByDeletedFalseOrderByMarketValueDesc()
                    .stream()
                    .map(Holding::getCode)
                    .distinct()
                    .collect(Collectors.toList());

            if (fundCodes.isEmpty()) {
                log.info("无持仓，跳过定时分红抓取");
                return 0;
            }

            log.info("需要刷新 {} 只基金的分红数据", fundCodes.size());
            int total = scrapeService.scrapeMultiple(fundCodes);

            // 抓取完成后同步分红事件到日历
            int synced = dividendEventSyncService.syncAllEvents();
            log.info("===== 定时刷新完成，新增 {} 条分红记录，同步 {} 条分红事件 =====", total, synced);
            return total;

        } catch (Exception e) {
            log.error("定时刷新分红数据失败: {}", e.getMessage());
            return 0;
        }
    }
}