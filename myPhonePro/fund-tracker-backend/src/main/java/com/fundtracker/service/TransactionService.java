package com.fundtracker.service;

import com.fundtracker.event.TransactionChangedEvent;
import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingService holdingService;
    private final ManualAssetService manualAssetService;
    private final FundNavScrapeService fundNavScrapeService;
    private final ApplicationEventPublisher eventPublisher;

    public List<TransactionDTO> listTransactions(String holdingId, String type,
                                                  String dateFrom, String dateTo, String userId) {
        List<Transaction> transactions;
        if (holdingId != null && !holdingId.isEmpty()) {
            transactions = transactionRepository.findByHoldingIdAndUserIdOrderByDateDesc(holdingId, userId);
        } else {
            transactions = transactionRepository.findByUserIdOrderByDateDesc(userId);
        }

        return transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO createTransaction(CreateTransactionReq req, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(req.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        TransactionType type = TransactionType.valueOf(req.getType());

        // 检查卖出份额是否足够
        if (type == TransactionType.sell) {
            if (req.getQuantity().compareTo(holding.getShares()) > 0) {
                throw BusinessException.insufficientShares();
            }
        }

        BigDecimal fee = req.getFee() != null ? req.getFee() : BigDecimal.ZERO;
        BigDecimal total = req.getQuantity().multiply(req.getPrice())
                .add(fee)
                .setScale(2, RoundingMode.HALF_UP);

        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(req.getHoldingId())
                .userId(userId)
                .type(type)
                .date(LocalDate.parse(req.getDate()))
                .quantity(req.getQuantity())
                .price(req.getPrice())
                .fee(fee)
                .total(total)
                .source(req.getSource() != null ? req.getSource() : "manual")
                .dcaPlanId(req.getDcaPlanId())
                .build();

        transaction = transactionRepository.save(transaction);

        // 更新持仓份额
        switch (type) {
            case buy:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
            case sell:
                holding.setShares(holding.getShares().subtract(req.getQuantity()));
                break;
            case reinvest:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
            case bonus_share:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
        }

        // 重新计算预测分红（份额变化后需要重新计算）
        holdingService.calculatePredictedDividend(holding);

        // 重新计算持仓指标
        holdingService.recalculateHoldingMetrics(holding);

        // 用已有最新价更新市值（确保市值与份额一致，即使后续净值刷新失败）
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marketValue = holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            holding.setMarketValue(marketValue);
        }

        holdingRepository.save(holding);

        // 调整现金余额（异常会触发 @Transactional 回滚，交易也不创建）
        // 规则一：只联动人民币标的。加密币买卖动的是交易所 USDT 余额（定投由 DcaPlanService 单独扣数量），
        //         这里若扣人民币现金会造成跨币种双记账。
        // 规则二：优先扣持仓归属账户的现金；未归属账户时退回主现金（历史行为）。
        // 规则三：reinvest 不涉及现金（分红已到账，直接转份额）。
        // 规则四：卖出到账 = 成交额 − 手续费（此前误把手续费加进了到账金额）。
        if (holding.getType() != HoldingType.crypto) {
            if (type == TransactionType.buy) {
                manualAssetService.adjustCash(holding.getId(), holding.getAccountId(), total.negate(), userId);
            } else if (type == TransactionType.sell) {
                BigDecimal netProceeds = req.getQuantity().multiply(req.getPrice())
                        .subtract(fee)
                        .setScale(2, RoundingMode.HALF_UP);
                manualAssetService.adjustCash(holding.getId(), holding.getAccountId(), netProceeds, userId);
            }
        }

        // 刷新最新净值，更新市值 —— 本地优先：交易时不再调用外部净值 API
        // 净值仅在每日定时任务（FundNavScheduler 22:00）喂入 fund_nav_records，
        // 这里直接读取本地表；仅当本地无任何净值时才触发一次外部抓取（冷启动兜底）
        try {
            FundNavScrapeService.LatestNavResult navResult = fundNavScrapeService.getLatestNavFromDb(holding.getCode());
            if (navResult == null) {
                navResult = fundNavScrapeService.incrementalUpdate(holding.getCode());
            }
            if (navResult != null && navResult.unitNav() != null) {
                BigDecimal newMarketValue = holding.getShares()
                        .multiply(navResult.unitNav())
                        .setScale(2, RoundingMode.HALF_UP);
                holding.setMarketValue(newMarketValue);
                holding.setLatestPrice(navResult.unitNav());
                holding.setPriceDate(navResult.navDate());
                holdingRepository.save(holding);
                log.info("交易后刷新持仓 {} 市值: ¥{}", holding.getName(), newMarketValue);
            }
        } catch (Exception e) {
            log.warn("交易后刷新净值失败: {}", e.getMessage());
        }

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holding.getId(), userId));

        return toDTO(transaction);
    }

    @Transactional
    public TransactionDTO updateTransaction(String id, UpdateTransactionReq req, String userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::transactionNotFound);
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(tx.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        // 更新字段（仅非 null 字段）
        if (req.getType() != null) {
            tx.setType(TransactionType.valueOf(req.getType()));
        }
        if (req.getDate() != null) {
            tx.setDate(LocalDate.parse(req.getDate()));
        }
        if (req.getQuantity() != null) {
            tx.setQuantity(req.getQuantity());
        }
        if (req.getPrice() != null) {
            tx.setPrice(req.getPrice());
        }
        if (req.getFee() != null) {
            tx.setFee(req.getFee());
        }

        // 重新计算 total = 数量 × 单价 + 手续费
        BigDecimal total = tx.getQuantity().multiply(tx.getPrice())
                .add(tx.getFee())
                .setScale(2, RoundingMode.HALF_UP);
        tx.setTotal(total);

        transactionRepository.save(tx);

        // 从所有交易重新计算份额
        recalculateSharesFromScratch(holding);
        // 重新计算预测分红和指标
        holdingService.calculatePredictedDividend(holding);
        holdingService.recalculateHoldingMetrics(holding);
        // 用已有最新价更新市值
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            holding.setMarketValue(holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        holdingRepository.save(holding);

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holding.getId(), userId));

        return toDTO(tx);
    }

    private void recalculateSharesFromScratch(Holding holding) {
        List<Transaction> allTx = transactionRepository.findByHoldingId(holding.getId());
        BigDecimal shares = BigDecimal.ZERO;
        for (Transaction t : allTx) {
            switch (t.getType()) {
                case buy:
                case reinvest:
                case bonus_share:
                    shares = shares.add(t.getQuantity());
                    break;
                case sell:
                    shares = shares.subtract(t.getQuantity());
                    break;
            }
        }
        holding.setShares(shares.max(BigDecimal.ZERO));
    }

    @Transactional
    public void deleteTransaction(String id, String userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::transactionNotFound);
        String holdingId = transaction.getHoldingId();
        BigDecimal txTotal = transaction.getTotal();
        TransactionType txType = transaction.getType();
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
                .orElseThrow(BusinessException::holdingNotFound);

        transactionRepository.delete(transaction);

        // 现金反向调整：与 createTransaction 对称（同一条规则，见 createTransaction 注释）。
        // 加密币不动人民币现金；reinvest 删除也不该把现金加回来（当初就没扣）。
        if (holding.getType() != HoldingType.crypto) {
            if (txType == TransactionType.buy) {
                manualAssetService.adjustCash(holdingId, holding.getAccountId(), txTotal, userId);
            } else if (txType == TransactionType.sell) {
                BigDecimal fee = transaction.getFee() != null ? transaction.getFee() : BigDecimal.ZERO;
                BigDecimal netProceeds = transaction.getQuantity().multiply(transaction.getPrice())
                        .subtract(fee)
                        .setScale(2, RoundingMode.HALF_UP);
                manualAssetService.adjustCash(holdingId, holding.getAccountId(), netProceeds.negate(), userId);
            }
        }

        // 删除后重新计算份额和指标
        if (holding.isCostEstimated()) {
            // 迁入持仓（成本/份额手工录入，无买入交易）：不按交易重算份额，
            // 否则删一笔交易会把手工维护的数量清零（2026-10-27 BTC 事故根因）
            log.info("删除交易: 持仓 {} 为迁入（估算成本），保留手工份额与成本，跳过份额重算", holdingId);
        } else {
            recalculateSharesFromScratch(holding);
            holdingService.calculatePredictedDividend(holding);
            holdingService.recalculateHoldingMetrics(holding);
            // 用已有最新价更新市值
            if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                holding.setMarketValue(holding.getShares()
                        .multiply(holding.getLatestPrice())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        holdingRepository.save(holding);

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holdingId, userId));
    }

    /**
     * 获取指定持仓的首笔交易日期
     */
    public LocalDate getFirstTransactionDate(String holdingId) {
        return transactionRepository.findEarliestTransactionDateByHoldingId(holdingId)
                .orElse(null);
    }

    /**
     * 计算指定持仓在特定日期的实时份额
     * 遍历该持仓所有交易（按日期升序），累加到目标日期为止
     */
    public BigDecimal calculateSharesAtDate(String holdingId, LocalDate date) {
        List<Transaction> allTx = transactionRepository.findByHoldingIdOrderByDateAsc(holdingId);
        BigDecimal shares = BigDecimal.ZERO;
        for (Transaction t : allTx) {
            if (t.getDate().isAfter(date)) break;
            switch (t.getType()) {
                case buy:
                case reinvest:
                case bonus_share:
                    shares = shares.add(t.getQuantity());
                    break;
                case sell:
                    shares = shares.subtract(t.getQuantity());
                    break;
            }
        }
        return shares.max(BigDecimal.ZERO);
    }

    private TransactionDTO toDTO(Transaction tx) {
        return TransactionDTO.builder()
                .id(tx.getId())
                .holdingId(tx.getHoldingId())
                .type(tx.getType().name())
                .date(tx.getDate().toString())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .fee(tx.getFee())
                .total(tx.getTotal())
                .source(tx.getSource())
                .dcaPlanId(tx.getDcaPlanId())
                .build();
    }
}
