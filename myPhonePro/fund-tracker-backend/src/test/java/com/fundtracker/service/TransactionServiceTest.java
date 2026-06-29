package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.CreateTransactionReq;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TransactionService.createTransaction 单元测试
 * 重点验证：份额更新、现金调整的调用
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private HoldingService holdingService;
    @Mock private ManualAssetService manualAssetService;
    @Mock private FundNavScrapeService fundNavScrapeService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, holdingRepository,
                holdingService, manualAssetService, fundNavScrapeService
        );
    }

    @Test
    @DisplayName("创建买入交易 → 份额增加 + 现金扣减")
    void createTransaction_buy() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("11000"))
                .latestPrice(new BigDecimal("11"))
                .build();

        CreateTransactionReq req = new CreateTransactionReq();
        req.setHoldingId("h-1");
        req.setType("buy");
        req.setDate("2026-06-29");
        req.setQuantity(new BigDecimal("100"));
        req.setPrice(new BigDecimal("10"));
        req.setFee(new BigDecimal("5"));

        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        transactionService.createTransaction(req);

        // 份额应增加 100
        assertEquals(new BigDecimal("1100"), holding.getShares());
        // 应调用 adjustCash 扣减总金额 (= 100*10 + 5 = 1005)
        verify(manualAssetService).adjustCash("h-1", new BigDecimal("-1005.00"));
        // 应调用 recalculateHoldingMetrics
        verify(holdingService).recalculateHoldingMetrics(holding);
        // 应调用 calculatePredictedDividend
        verify(holdingService).calculatePredictedDividend(holding);
    }

    @Test
    @DisplayName("创建卖出交易 → 份额减少 + 现金增加")
    void createTransaction_sell() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("11000"))
                .latestPrice(new BigDecimal("11"))
                .build();

        CreateTransactionReq req = new CreateTransactionReq();
        req.setHoldingId("h-1");
        req.setType("sell");
        req.setDate("2026-06-29");
        req.setQuantity(new BigDecimal("200"));
        req.setPrice(new BigDecimal("15"));
        req.setFee(new BigDecimal("10"));

        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        transactionService.createTransaction(req);

        // 份额应减少 200
        assertEquals(new BigDecimal("800"), holding.getShares());
        // 应调用 adjustCash 增加总金额 (= 200*15 + 10 = 3010)
        verify(manualAssetService).adjustCash("h-1", new BigDecimal("3010.00"));
    }

    @Test
    @DisplayName("卖出份额超过持有 → 抛异常，不调现金")
    void createTransaction_sellInsufficientShares() {
        Holding holding = Holding.builder()
                .id("h-1")
                .shares(new BigDecimal("100"))
                .build();

        CreateTransactionReq req = new CreateTransactionReq();
        req.setHoldingId("h-1");
        req.setType("sell");
        req.setDate("2026-06-29");
        req.setQuantity(new BigDecimal("200"));
        req.setPrice(new BigDecimal("10"));

        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));

        assertThrows(BusinessException.class, () -> transactionService.createTransaction(req));

        // 不应调用 adjustCash
        verify(manualAssetService, never()).adjustCash(any(), any());
    }

    @Test
    @DisplayName("删除买入交易 → 反向加回现金")
    void deleteTransaction_buy_refundsCash() {
        Transaction tx = Transaction.builder()
                .id("tx-1")
                .holdingId("h-1")
                .type(TransactionType.buy)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .fee(new BigDecimal("5"))
                .total(new BigDecimal("1005.00"))
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("900"))
                .build();

        when(transactionRepository.findById("tx-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));
        doNothing().when(transactionRepository).delete(tx);

        transactionService.deleteTransaction("tx-1");

        // 反向加回现金 1005
        verify(manualAssetService).adjustCash("h-1", new BigDecimal("1005.00"));
    }

    @Test
    @DisplayName("删除卖出交易 → 反向扣减现金")
    void deleteTransaction_sell_deductsCash() {
        Transaction tx = Transaction.builder()
                .id("tx-1")
                .holdingId("h-1")
                .type(TransactionType.sell)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("15"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("1500.00"))
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("1000"))
                .build();

        when(transactionRepository.findById("tx-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndDeletedFalse("h-1")).thenReturn(Optional.of(holding));
        doNothing().when(transactionRepository).delete(tx);

        transactionService.deleteTransaction("tx-1");

        // 反向扣减现金 1500
        verify(manualAssetService).adjustCash("h-1", new BigDecimal("-1500.00"));
    }
}
