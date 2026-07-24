package com.fundtracker.scheduler;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 分红自动分发定时任务
 * 每天早上 9:30 检查是否有已到 payout 日期但尚未处理的发放事件，
 * 自动调用 markDistributed()：
 *   - 如果该持仓开启了分红复投开关 → 自动按净值复投
 *   - 如果未开启 → 自动将金额加到现金
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendAutoDistributeScheduler {

    private final DividendEventRepository eventRepository;
    private final EventService eventService;

    @Scheduled(cron = "0 30 9 * * ?")
    public void autoDistributeDividends() {
        LocalDate today = LocalDate.now();
        log.info("[分红自动分发] 开始检查, 日期={}", today);

        // 查询所有 pending 状态的 payout 事件，且日期 <= 今天
        List<DividendEvent> pendingPayouts = eventRepository
                .findByDateBeforeAndStatus(today, EventStatus.pending);

        // 只处理 type = payout 的事件
        List<DividendEvent> dueEvents = pendingPayouts.stream()
                .filter(e -> e.getType() == EventType.payout)
                .toList();

        if (dueEvents.isEmpty()) {
            log.info("[分红自动分发] 无到期分红事件");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (DividendEvent event : dueEvents) {
            try {
                eventService.markDistributed(event.getId(), event.getUserId());
                successCount++;
                log.info("[分红自动分发] 已处理: {} {} 金额={}",
                        event.getHoldingName(), event.getDate(), event.getAmount());
            } catch (Exception e) {
                failCount++;
                log.error("[分红自动分发] 处理失败: {} {}: {}",
                        event.getHoldingName(), event.getId(), e.getMessage());
            }
        }

        log.info("[分红自动分发] 执行完毕, 成功={}, 失败={}", successCount, failCount);
    }
}
