package com.fundtracker.scheduler;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据对账审计
 * 每天凌晨自动检查数据一致性，发现异常直接打 ERROR 日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAuditor {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final DividendEventRepository dividendEventRepository;
    private final FundDividendRecordRepository fundDividendRecordRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    public void auditAll() {
        log.info("===== 开始数据对账 =====");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        auditMarketValue(errors);
        auditCashFlow(errors, warnings);
        auditHoldingShares(errors);
        auditDividendOutliers(warnings);
        auditDividendRate(warnings);
        auditStaleDividendEvents(warnings);

        // 汇总
        if (!errors.isEmpty()) {
            log.error("对账发现 {} 个错误:", errors.size());
            errors.forEach(e -> log.error("  ❌ {}", e));
        }
        if (!warnings.isEmpty()) {
            log.warn("对账发现 {} 个警告:", warnings.size());
            warnings.forEach(w -> log.warn("  ⚠️ {}", w));
        }
        if (errors.isEmpty() && warnings.isEmpty()) {
            log.info("✅ 对账完成，未发现异常");
        }
        log.info("===== 数据对账完成，错误={}, 警告={} =====", errors.size(), warnings.size());
    }

    // ==================== P0: 数据一致性 ====================

    /**
     * 市值对账：市值应等于 份额 × 最新价
     */
    private void auditMarketValue(List<String> errors) {
        for (Holding h : holdingRepository.findByDeletedFalseOrderByMarketValueDesc()) {
            if (h.getLatestPrice() == null || h.getLatestPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal expected = h.getShares()
                    .multiply(h.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual = h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO;
            if (expected.compareTo(actual) != 0) {
                errors.add(String.format(
                        "市值不一致 [%s] shares=%s price=%s actualMV=%s expectedMV=%s",
                        h.getName(), h.getShares(), h.getLatestPrice(), actual, expected));
            }
        }
    }

    /**
     * 现金流水对账：初始现金 + 卖出到账 - 买入支出 + 分红到账 ≈ 当前现金
     */
    private void auditCashFlow(List<String> errors, List<String> warnings) {
        List<ManualAsset> cashAssets = manualAssetRepository.findByType("cash");
        if (cashAssets.isEmpty()) return;

        BigDecimal currentCash = cashAssets.stream()
                .map(ManualAsset::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算交易流水
        List<Transaction> allTx = transactionRepository.findAll();
        BigDecimal totalBuy = BigDecimal.ZERO;
        BigDecimal totalSell = BigDecimal.ZERO;
        for (Transaction tx : allTx) {
            if (tx.getType() == TransactionType.buy || tx.getType() == TransactionType.reinvest) {
                totalBuy = totalBuy.add(tx.getTotal() != null ? tx.getTotal() : BigDecimal.ZERO);
            } else if (tx.getType() == TransactionType.sell) {
                totalSell = totalSell.add(tx.getTotal() != null ? tx.getTotal() : BigDecimal.ZERO);
            }
        }

        // 计算已到账分红
        List<DividendEvent> distributed = dividendEventRepository.findByStatus(EventStatus.distributed);
        BigDecimal totalDividend = distributed.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 预期现金变动 = 卖出到账 - 买入支出 + 分红到账
        BigDecimal cashChange = totalSell.subtract(totalBuy).add(totalDividend);

        // 如果没有初始现金记录，无法对账
        if (currentCash.compareTo(cashChange) != 0) {
            warnings.add(String.format(
                    "现金流水对账: 当前现金=%s, 交易流水净变动=%s(买入=%s 卖出=%s 分红=%s)",
                    currentCash, cashChange, totalBuy, totalSell, totalDividend));
        }
    }

    /**
     * 份额对账：持仓份额 = 所有买入份额 - 所有卖出份额
     */
    private void auditHoldingShares(List<String> errors) {
        for (Holding h : holdingRepository.findByDeletedFalseOrderByMarketValueDesc()) {
            List<Transaction> txs = transactionRepository.findByHoldingId(h.getId());
            BigDecimal buyShares = BigDecimal.ZERO;
            BigDecimal sellShares = BigDecimal.ZERO;
            for (Transaction tx : txs) {
                switch (tx.getType()) {
                    case buy:
                    case reinvest:
                    case bonus_share:
                        buyShares = buyShares.add(tx.getQuantity());
                        break;
                    case sell:
                        sellShares = sellShares.add(tx.getQuantity());
                        break;
                }
            }
            BigDecimal expectedShares = buyShares.subtract(sellShares).max(BigDecimal.ZERO);
            if (expectedShares.compareTo(h.getShares()) != 0) {
                errors.add(String.format(
                        "份额不一致 [%s] DB=%s 交易计算=%s(买入=%s 卖出=%s)",
                        h.getName(), h.getShares(), expectedShares, buyShares, sellShares));
            }
        }
    }

    // ==================== P1: 数据合理性 ====================

    /**
     * 分红异常检测：单次分红 > 该基金近3年均值 × 3
     */
    private void auditDividendOutliers(List<String> warnings) {
        List<Object[]> outliers = fundDividendRecordRepository.findDividendOutliers();
        for (Object[] row : outliers) {
            String fundCode = (String) row[0];
            String exDate = row[1] != null ? row[1].toString() : "";
            BigDecimal amount = (BigDecimal) row[2];
            warnings.add(String.format(
                    "分红异常 [%s] %s 每份分红=%s 超过均值3倍",
                    fundCode, exDate, amount));
        }
    }

    /**
     * 成本息率异常检测：超过 30% 或为负
     */
    private void auditDividendRate(List<String> warnings) {
        for (Holding h : holdingRepository.findByDeletedFalseOrderByMarketValueDesc()) {
            BigDecimal rate = h.getDividendRate();
            if (rate != null && (rate.compareTo(new BigDecimal("30")) > 0 || rate.compareTo(BigDecimal.ZERO) < 0)) {
                warnings.add(String.format(
                        "成本息率异常 [%s] dividendRate=%s%% predictedDividend=%s cost=%s",
                        h.getName(), rate, h.getPredictedDividend(), h.getCost()));
            }
        }
    }

    /**
     * 过期分红事件：发放日已过 7 天但状态仍为 pending
     */
    private void auditStaleDividendEvents(List<String> warnings) {
        LocalDate cutoff = LocalDate.now().minusDays(7);
        List<DividendEvent> stale = dividendEventRepository.findByDateBeforeAndStatus(cutoff, EventStatus.pending);
        for (DividendEvent e : stale) {
            warnings.add(String.format(
                    "分红事件过期 [%s] date=%s amount=%s 状态仍为 pending",
                    e.getHoldingName(), e.getDate(), e.getAmount()));
        }
    }
}
