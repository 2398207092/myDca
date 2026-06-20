package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.service.DividendEventSyncService;
import com.fundtracker.service.FundDividendScrapeService;
import com.fundtracker.service.FundDividendScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundDividendController {

    private final FundDividendScrapeService scrapeService;
    private final FundDividendScheduler scheduler;
    private final DividendEventSyncService dividendEventSyncService;

    /**
     * 手动刷新指定基金的分红数据
     */
    @PostMapping("/{code}/dividends/refresh")
    public ApiResponse<Map<String, Object>> refreshDividendData(@PathVariable String code) {
        int saved = scrapeService.scrapeAndSave(code);
        int synced = dividendEventSyncService.syncEventsForFund(code);
        List<FundDividendRecord> records = scrapeService.getRecords(code);
        return ApiResponse.success(Map.of(
                "fundCode", code,
                "newRecords", saved,
                "syncedEvents", synced,
                "totalRecords", records.size()
        ));
    }

    /**
     * 手动刷新所有持仓基金的分红数据
     */
    @PostMapping("/dividends/refresh-all")
    public ApiResponse<Map<String, Object>> refreshAllDividends() {
        int total = scheduler.refreshAllHoldingsDividendData();
        return ApiResponse.success(Map.of("totalNewRecords", total));
    }

    /**
     * 查询基金的历史分红记录
     */
    @GetMapping("/{code}/dividends")
    public ApiResponse<List<FundDividendRecord>> getDividendRecords(@PathVariable String code) {
        return ApiResponse.success(scrapeService.getRecords(code));
    }
}