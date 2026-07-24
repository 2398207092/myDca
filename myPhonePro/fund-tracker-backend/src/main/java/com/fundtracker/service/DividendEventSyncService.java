package com.fundtracker.service;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendEventSyncService {

    private final FundDividendRecordRepository fundDividendRecordRepository;
    private final HoldingRepository holdingRepository;
    private final DividendEventRepository dividendEventRepository;
    private final TransactionService transactionService;

    /**
     * 为指定基金代码的所有持仓同步分红事件
     * <p>
     * 保留所有历史分红记录，根据首笔买入日设置 participated 标签：
     * - 首笔买入之前的分红 → participated=false, amount=0（未参与）
     * - 首笔买入之后的分红 → participated=true, 按当时实时份额算金额
     */
    @Transactional
    public int syncEventsForFund(String fundCode, String userId) {
        List<Holding> holdings;
        if (userId != null) {
            // API 调用：只同步当前用户的持仓
            holdings = holdingRepository.findByCodeAndUserIdAndDeletedFalse(fundCode, userId);
            if (holdings.isEmpty()) {
                log.info("用户未持有基金 {}", fundCode);
                return 0;
            }
        } else {
            // 定时任务：同步所有用户的持仓
            holdings = holdingRepository.findByCodeAndDeletedFalse(fundCode);
        }
        List<FundDividendRecord> records = fundDividendRecordRepository.findByFundCodeOrderByExDateDesc(fundCode);
        if (records.isEmpty()) {
            log.info("基金 {} 无分红记录，清理旧事件", fundCode);
            for (Holding holding : holdings) {
                int deleted = dividendEventRepository.deleteByHoldingId(holding.getId());
                if (deleted > 0) {
                    log.info("清理持仓 {} 的 {} 条旧分红事件", holding.getName(), deleted);
                }
            }
            return 0;
        }

        if (holdings.isEmpty()) {
            log.info("基金 {} 无有效持仓，跳过同步", fundCode);
            return 0;
        }

        int created = 0;
        for (Holding holding : holdings) {
            // 定时任务无 userId 时使用持仓的 userId
            String effectiveUserId = userId != null ? userId : holding.getUserId();
            LocalDate firstBuyDate = transactionService.getFirstTransactionDate(holding.getId());

            for (FundDividendRecord record : records) {
                // 检查 payout 事件是否已被标记为"已复投"（防止 sync 时丢失 converted 状态）
                boolean payoutWasConverted = false;
                if (record.getPayDate() != null) {
                    List<DividendEvent> existing = dividendEventRepository
                            .findByHoldingIdAndTypeAndDate(holding.getId(), EventType.payout, record.getPayDate());
                    for (DividendEvent e : existing) {
                        if (Boolean.TRUE.equals(e.getConverted())) {
                            payoutWasConverted = true;
                            log.debug("保留复投状态: holding={}, payDate={}", holding.getName(), record.getPayDate());
                            break;
                        }
                    }
                }

                // 先删除旧的错误事件，再重新创建
                deleteIfExists(holding.getId(), EventType.registration, record.getRegDate());
                deleteIfExists(holding.getId(), EventType.ex_dividend, record.getExDate());
                deleteIfExists(holding.getId(), EventType.payout, record.getPayDate());

                // 判断是否参与本次分红
                boolean participated = false;
                BigDecimal sharesAtDate = BigDecimal.ZERO;

                if (firstBuyDate != null && record.getExDate() != null
                        && !record.getExDate().isBefore(firstBuyDate)) {
                    sharesAtDate = transactionService.calculateSharesAtDate(holding.getId(), record.getExDate());
                    participated = sharesAtDate.compareTo(BigDecimal.ZERO) > 0;
                }

                created += createEvent(holding, record, EventType.registration, record.getRegDate(), sharesAtDate, participated, effectiveUserId, false);
                created += createEvent(holding, record, EventType.ex_dividend, record.getExDate(), sharesAtDate, participated, effectiveUserId, false);
                created += createEvent(holding, record, EventType.payout, record.getPayDate(), sharesAtDate, participated, effectiveUserId, payoutWasConverted);
            }
        }

        log.info("基金 {} 同步完成，共处理 {} 条分红事件", fundCode, created);
        return created;
    }

    /**
     * 为所有持仓同步分红事件
     */
    @Transactional
    public int syncAllEvents(String userId) {
        List<String> fundCodes = holdingRepository.findDistinctCodesByDeletedFalse();
        if (fundCodes.isEmpty()) {
            log.info("无有效持仓，跳过全量同步");
            return 0;
        }
        int total = 0;
        for (String code : fundCodes) {
            total += syncEventsForFund(code, userId);
        }
        log.info("全量同步完成，共新增 {} 条分红事件", total);
        return total;
    }

    /**
     * 为指定持仓ID同步分红事件
     */
    @Transactional
    public int syncEventsForHolding(String holdingId, String userId) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId).orElse(null);
        if (holding == null || holding.getCode() == null) return 0;
        return syncEventsForFund(holding.getCode(), userId);
    }

    /**
     * 删除指定持仓+类型+日期的分红事件（如果存在）
     */
    private void deleteIfExists(String holdingId, EventType type, LocalDate date) {
        if (date == null) return;
        try {
            dividendEventRepository.deleteByHoldingIdAndTypeAndDate(holdingId, type, date);
        } catch (Exception e) {
            log.warn("删除分红事件失败: holdingId={}, type={}, date={}, error={}",
                    holdingId, type, date, e.getMessage());
        }
    }

    /**
     * 创建分红事件
     * <p>
     * participated=true → 按当时的实时份额计算金额
     * participated=false → 金额为 0（未参与该次分红，但保留记录）
     */
    private int createEvent(Holding holding, FundDividendRecord record, EventType type, LocalDate date, BigDecimal sharesAtDate, boolean participated, String userId, boolean converted) {
        if (date == null) return 0;

        BigDecimal amount = participated && record.getDividendPerShare() != null
                ? sharesAtDate.multiply(record.getDividendPerShare()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String description = switch (type) {
            case registration -> "权益登记日 · 每份 " + record.getDividendPerShare() + " 元";
            case ex_dividend -> "除权除息 · 每份 " + record.getDividendPerShare() + " 元";
            case payout -> "分红发放 · 每份 " + record.getDividendPerShare() + " 元";
            default -> "";
        };

        DividendEvent event = DividendEvent.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holding.getId())
                .userId(userId)
                .holdingName(holding.getName())
                .type(type)
                .date(date)
                .amount(amount)
                .status(EventStatus.pending)
                .description(description)
                .participated(participated)
                .converted(converted)
                .build();

        dividendEventRepository.save(event);
        log.debug("创建分红事件: holding={}, type={}, date={}, amount={}, participated={}, converted={}",
                holding.getName(), type, date, amount, participated, converted);
        return 1;
    }
}
