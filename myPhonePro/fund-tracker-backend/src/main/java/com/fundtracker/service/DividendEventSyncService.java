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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            // P0-6 修复：源记录为空（可能是接口异常/被风控）时不再全删用户事件——
            // 全删会让 distributed/cancelled 全部丢失，源数据恢复后重建 pending 造成分红复活
            log.warn("基金 {} 无分红记录，跳过同步（不清理旧事件，防止源数据异常导致分红复活）", fundCode);
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

            // #4 N+1 优化：预取该持仓全部事件到内存 Map（key = type + "|" + date），
            // 替换循环内的 findByHoldingIdAndTypeAndDate 逐条查询
            Map<String, List<DividendEvent>> existingByKey = new HashMap<>();
            for (DividendEvent e : dividendEventRepository.findByHoldingIdOrderByDateDesc(holding.getId())) {
                String key = e.getType() + "|" + (e.getDate() != null ? e.getDate() : "");
                existingByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }

            for (FundDividendRecord record : records) {
                // 状态保留检测（payout）：distributed / cancelled / converted 都保留，
                // P0-6 修复的核心 = cancelled 也保留（此前用户取消的分红被删后重建 pending → 次日复活）
                boolean payoutConverted = false;
                boolean payoutDistributed = false;
                boolean payoutCancelled = false;
                BigDecimal payoutOriginalAmount = null;
                if (record.getPayDate() != null) {
                    List<DividendEvent> existing = existingByKey.getOrDefault(
                            EventType.payout + "|" + record.getPayDate(), List.of());
                    for (DividendEvent e : existing) {
                        if (Boolean.TRUE.equals(e.getConverted())) {
                            payoutConverted = true;
                            log.debug("保留复投状态: holding={}, payDate={}", holding.getName(), record.getPayDate());
                        }
                        if (e.getStatus() == EventStatus.distributed) {
                            payoutDistributed = true;
                            payoutOriginalAmount = e.getAmount();
                            log.debug("保留到账状态: holding={}, payDate={}", holding.getName(), record.getPayDate());
                        }
                        if (e.getStatus() == EventStatus.cancelled && payoutOriginalAmount == null) {
                            payoutCancelled = true;
                            payoutOriginalAmount = e.getAmount();
                            log.debug("保留取消状态: holding={}, payDate={}", holding.getName(), record.getPayDate());
                        }
                    }
                }

                // 判断是否参与本次分红
                boolean participated = false;
                BigDecimal sharesAtDate = BigDecimal.ZERO;

                if (firstBuyDate != null && record.getExDate() != null
                        && !record.getExDate().isBefore(firstBuyDate)) {
                    sharesAtDate = transactionService.calculateSharesAtDate(holding.getId(), record.getExDate());
                    participated = sharesAtDate.compareTo(BigDecimal.ZERO) > 0;
                }

                // P0-6 修复：delete+recreate 改为 upsert——同键事件存在则保留终态（distributed/cancelled）
                // 并更新元数据，不存在才新建；配合 uk_holding_type_date 唯一约束消除「删除失败→双事件」路径
                EventStatus payoutStatus = payoutDistributed ? EventStatus.distributed
                        : payoutCancelled ? EventStatus.cancelled
                        : EventStatus.pending;
                BigDecimal payoutAmountOverride = (payoutDistributed || payoutCancelled) ? payoutOriginalAmount : null;

                created += upsertEvent(holding, record, EventType.registration, record.getRegDate(),
                        sharesAtDate, participated, effectiveUserId, false, EventStatus.pending, null,
                        existingByKey.getOrDefault(EventType.registration + "|" + record.getRegDate(), List.of()));
                created += upsertEvent(holding, record, EventType.ex_dividend, record.getExDate(),
                        sharesAtDate, participated, effectiveUserId, false, EventStatus.pending, null,
                        existingByKey.getOrDefault(EventType.ex_dividend + "|" + record.getExDate(), List.of()));
                created += upsertEvent(holding, record, EventType.payout, record.getPayDate(),
                        sharesAtDate, participated, effectiveUserId, payoutConverted, payoutStatus, payoutAmountOverride,
                        existingByKey.getOrDefault(EventType.payout + "|" + record.getPayDate(), List.of()));
            }

            // 孤儿清理：数据源可能改了分红日期，旧日期的 pending 事件残留会污染日历；
            // 终态事件（distributed/cancelled）与 converted 一律保留
            java.util.Set<String> validKeys = new java.util.HashSet<>();
            for (FundDividendRecord r : records) {
                validKeys.add(EventType.registration + "|" + r.getRegDate());
                validKeys.add(EventType.ex_dividend + "|" + r.getExDate());
                validKeys.add(EventType.payout + "|" + r.getPayDate());
            }
            for (DividendEvent e : dividendEventRepository.findByHoldingIdOrderByDateDesc(holding.getId())) {
                String key = e.getType() + "|" + (e.getDate() != null ? e.getDate() : "");
                if (!validKeys.contains(key) && e.getStatus() == EventStatus.pending) {
                    dividendEventRepository.delete(e);
                    log.info("清理孤儿 pending 事件: holding={}, type={}, date={}", holding.getName(), e.getType(), e.getDate());
                }
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
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId).orElse(null);
        if (holding == null || holding.getCode() == null) return 0;
        return syncEventsForFund(holding.getCode(), userId);
    }

    /**
     * upsert 分红事件（P0-6 修复：替代原「deleteIfExists + createEvent」的先删后建）
     * <p>
     * 同键（holdingId + type + date）事件存在 → **保留终态**（distributed / cancelled 不被重置，
     * converted 不被冲掉），仅更新元数据（名称/参与标记/描述/金额）；不存在 → 新建。
     * 配合 dividend_events 的 uk_holding_type_date 唯一约束，消除「删除失败 → 同键双事件」路径。
     *
     * @param status        新建时的初始状态；已存在且非终态时作为更新后的状态
     * @param amountOverride 非空时沿用原金额（已到账/已取消事件禁止重算，防止份额变化导致金额漂移）
     * @param existingSameKey 同键的已有事件（由调用方从预取 Map 中取，避免 N+1）
     */
    private int upsertEvent(Holding holding, FundDividendRecord record, EventType type, LocalDate date,
                            BigDecimal sharesAtDate, boolean participated, String userId, boolean converted,
                            EventStatus status, BigDecimal amountOverride, List<DividendEvent> existingSameKey) {
        if (date == null) return 0;

        BigDecimal amount = amountOverride != null ? amountOverride
                : participated && record.getDividendPerShare() != null
                ? sharesAtDate.multiply(record.getDividendPerShare()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String description = switch (type) {
            case registration -> "权益登记日 · 每份 " + record.getDividendPerShare() + " 元";
            case ex_dividend -> "除权除息 · 每份 " + record.getDividendPerShare() + " 元";
            case payout -> "分红发放 · 每份 " + record.getDividendPerShare() + " 元";
            default -> "";
        };

        DividendEvent existing = existingSameKey.isEmpty() ? null : existingSameKey.get(0);
        if (existing != null) {
            boolean terminal = existing.getStatus() == EventStatus.distributed
                    || existing.getStatus() == EventStatus.cancelled;
            existing.setHoldingName(holding.getName());
            existing.setParticipated(participated);
            existing.setDescription(description);
            existing.setAmount(amountOverride != null ? amountOverride
                    : (terminal ? existing.getAmount() : amount));
            if (!terminal) {
                existing.setStatus(status);
                existing.setConverted(converted);
            }
            dividendEventRepository.save(existing);
            log.debug("更新分红事件: holding={}, type={}, date={}, status={}, amount={}",
                    holding.getName(), type, date, existing.getStatus(), existing.getAmount());
            return 0;
        }

        DividendEvent event = DividendEvent.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holding.getId())
                .userId(userId)
                .holdingName(holding.getName())
                .type(type)
                .date(date)
                .amount(amount)
                .status(status)
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
