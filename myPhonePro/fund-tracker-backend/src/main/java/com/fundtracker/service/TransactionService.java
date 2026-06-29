package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final FundNavScrapeService fundNavScrapeService;

    public List<TransactionDTO> listTransactions(String holdingId, String type,
                                                  String dateFrom, String dateTo) {
        List<Transaction> transactions;
        if (holdingId != null && !holdingId.isEmpty()) {
            transactions = transactionRepository.findByHoldingIdOrderByDateDesc(holdingId);
        } else {
            transactions = transactionRepository.findAll();
        }

        return transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO createTransaction(CreateTransactionReq req) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(req.getHoldingId())
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

        // 刷新最新净值，更新市值
        try {
            FundNavScrapeService.LatestNavResult navResult = fundNavScrapeService.incrementalUpdate(holding.getCode());
            if (navResult == null) {
                navResult = fundNavScrapeService.getLatestNavFromDb(holding.getCode());
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

        return toDTO(transaction);
    }

    @Transactional
    public TransactionDTO updateTransaction(String id, UpdateTransactionReq req) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(BusinessException::transactionNotFound);
        Holding holding = holdingRepository.findByIdAndDeletedFalse(tx.getHoldingId())
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
    public void deleteTransaction(String id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(BusinessException::transactionNotFound);
        String holdingId = transaction.getHoldingId();
        transactionRepository.delete(transaction);

        // 删除后重新计算份额和指标
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId)
                .orElseThrow(BusinessException::holdingNotFound);
        recalculateSharesFromScratch(holding);
        holdingService.calculatePredictedDividend(holding);
        holdingService.recalculateHoldingMetrics(holding);
        // 用已有最新价更新市值
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            holding.setMarketValue(holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        holdingRepository.save(holding);
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
