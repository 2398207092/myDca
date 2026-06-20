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

    /**
     * 为指定基金代码的所有持仓同步分红事件
     */
    @Transactional
    public int syncEventsForFund(String fundCode) {
        List<FundDividendRecord> records = fundDividendRecordRepository.findByFundCodeOrderByExDateDesc(fundCode);
        if (records.isEmpty()) {
            log.info("基金 {} 无分红记录，跳过同步", fundCode);
            return 0;
        }

        List<Holding> holdings = holdingRepository.findByCodeAndDeletedFalse(fundCode);
        if (holdings.isEmpty()) {
            log.info("基金 {} 无有效持仓，跳过同步", fundCode);
            return 0;
        }

        int created = 0;
        for (Holding holding : holdings) {
            for (FundDividendRecord record : records) {
                created += createIfNotExists(holding, record, EventType.registration, record.getRegDate());
                created += createIfNotExists(holding, record, EventType.ex_dividend, record.getExDate());
                created += createIfNotExists(holding, record, EventType.payout, record.getPayDate());
            }
        }

        log.info("基金 {} 同步完成，新增 {} 条分红事件", fundCode, created);
        return created;
    }

    /**
     * 为所有持仓同步分红事件
     */
    @Transactional
    public int syncAllEvents() {
        List<String> fundCodes = holdingRepository.findDistinctCodesByDeletedFalse();
        if (fundCodes.isEmpty()) {
            log.info("无有效持仓，跳过全量同步");
            return 0;
        }
        int total = 0;
        for (String code : fundCodes) {
            total += syncEventsForFund(code);
        }
        log.info("全量同步完成，共新增 {} 条分红事件", total);
        return total;
    }

    /**
     * 为指定持仓ID同步分红事件
     */
    @Transactional
    public int syncEventsForHolding(String holdingId) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId).orElse(null);
        if (holding == null || holding.getCode() == null) return 0;
        return syncEventsForFund(holding.getCode());
    }

    /**
     * 去重创建分红事件（同一持仓+同一类型+同一日期的只创建一次）
     */
    private int createIfNotExists(Holding holding, FundDividendRecord record, EventType type, LocalDate date) {
        if (date == null) return 0;

        boolean exists = dividendEventRepository.existsByHoldingIdAndTypeAndDate(holding.getId(), type, date);
        if (exists) return 0;

        BigDecimal amount = record.getDividendPerShare() != null
                ? holding.getShares().multiply(record.getDividendPerShare()).setScale(2, java.math.RoundingMode.HALF_UP)
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
                .holdingName(holding.getName())
                .type(type)
                .date(date)
                .amount(amount)
                .status(EventStatus.pending)
                .description(description)
                .build();

        dividendEventRepository.save(event);
        return 1;
    }
}
