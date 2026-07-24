package com.fundtracker.listener;

import com.fundtracker.event.TransactionChangedEvent;
import com.fundtracker.service.DividendEventSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final DividendEventSyncService dividendEventSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionChanged(TransactionChangedEvent event) {
        try {
            int count = dividendEventSyncService.syncEventsForHolding(event.holdingId(), event.userId());
            if (count > 0) {
                log.info("交易变更后同步分红事件完成: {} 条", count);
            }
        } catch (Exception e) {
            log.warn("同步分红事件失败: {}", e.getMessage());
        }
    }
}
