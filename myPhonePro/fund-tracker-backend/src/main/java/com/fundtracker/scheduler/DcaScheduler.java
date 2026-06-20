package com.fundtracker.scheduler;

import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.service.DcaPlanService;
import com.fundtracker.service.TradingCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 定投定时任务
 * 每天 20:00（北京时间）检查所有活跃定投计划，执行到期的计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DcaScheduler {

    private final DcaPlanRepository dcaPlanRepository;
    private final DcaPlanService dcaPlanService;
    private final TradingCalendar tradingCalendar;

    @Scheduled(cron = "0 0 20 * * ?")
    public void processDcaPlans() {
        LocalDate today = LocalDate.now();
        log.info("[定投定时任务] 开始执行, 日期={}", today);

        // 查询所有活跃且到期（或已过期）的计划
        List<DcaPlan> duePlans = dcaPlanRepository
                .findByStatusAndNextExecutionDateLessThanEqual("active", today);

        if (duePlans.isEmpty()) {
            log.info("[定投定时任务] 无到期计划");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (DcaPlan plan : duePlans) {
            // 检查今天是否为该计划市场的交易日
            if (!tradingCalendar.isTradingDay(today, plan.getTradingMarket())) {
                log.debug("定投计划 {} 市场 {} 今日非交易日, 跳过", plan.getId(), plan.getTradingMarket());
                continue;
            }

            try {
                dcaPlanService.executePlan(plan.getId());
                successCount++;
                log.info("定投计划 {} 自动执行成功", plan.getId());
            } catch (Exception e) {
                failCount++;
                log.error("定投计划 {} 自动执行失败: {}", plan.getId(), e.getMessage());
            }
        }

        log.info("[定投定时任务] 执行完毕, 成功={}, 失败={}", successCount, failCount);
    }
}
