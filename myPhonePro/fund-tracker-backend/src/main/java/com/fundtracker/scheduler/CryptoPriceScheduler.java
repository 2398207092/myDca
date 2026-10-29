package com.fundtracker.scheduler;

import com.fundtracker.model.dto.RefreshCryptoPricesResp;
import com.fundtracker.service.CryptoPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天 08:00 刷新加密币价格。
 * 与其他任务错开：6:00 分红、8:00 价格、9:30 分红派发、10:00 定投、22:00 净值、23:30 快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoPriceScheduler {

    private final CryptoPriceService cryptoPriceService;

    @Scheduled(cron = "0 0 8 * * ?")
    public void refreshDaily() {
        try {
            RefreshCryptoPricesResp resp = cryptoPriceService.refresh(null);
            log.info("加密币价格刷新完成：{}，更新 {} 个持仓", resp.getMessage(), resp.getUpdatedHoldings());
        } catch (Exception e) {
            log.error("加密币价格刷新失败", e);
        }
    }
}
