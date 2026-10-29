package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.CreateTransactionReq;
import com.fundtracker.model.dto.UpdateTransactionReq;
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

import java.time.LocalDate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.fundtracker.repository.DcaPlanRepository dcaPlanRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, holdingRepository, dcaPlanRepository,
                holdingService, manualAssetService, fundNavScrapeService,
                eventPublisher
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

        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        transactionService.createTransaction(req, "user-1");

        // 份额应增加 100
        assertEquals(new BigDecimal("1100"), holding.getShares());
        // 应调用 adjustCash 扣减总金额 (= 100*10 + 5 = 1005)
        verify(manualAssetService).adjustCash("h-1", null, new BigDecimal("-1005.00"), "user-1");
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

        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        transactionService.createTransaction(req, "user-1");

        // 份额应减少 200
        assertEquals(new BigDecimal("800"), holding.getShares());
        // 应调用 adjustCash 增加总金额 (= 200*15 + 10 = 3010)
        verify(manualAssetService).adjustCash("h-1", null, new BigDecimal("2990.00"), "user-1");
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

        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));

        assertThrows(BusinessException.class, () -> transactionService.createTransaction(req, "user-1"));

        // 不应调用 adjustCash
        verify(manualAssetService, never()).adjustCash(any(), any(), any(), any());
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

        when(transactionRepository.findByIdAndUserId("tx-1", "user-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        doNothing().when(transactionRepository).delete(tx);

        transactionService.deleteTransaction("tx-1", "user-1");

        // 反向加回现金 1005
        verify(manualAssetService).adjustCash("h-1", null, new BigDecimal("1005.00"), "user-1");
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

        when(transactionRepository.findByIdAndUserId("tx-1", "user-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        doNothing().when(transactionRepository).delete(tx);

        transactionService.deleteTransaction("tx-1", "user-1");

        // 反向扣减现金 1500
        verify(manualAssetService).adjustCash("h-1", null, new BigDecimal("-1500.00"), "user-1");
    }
    @Test
    @DisplayName("删除 dca 买入交易 → 回退计划计数器（P1-20）")
    void deleteTransaction_dca_rollsBackPlanCounters() {
        Transaction tx = Transaction.builder()
                .id("tx-1")
                .holdingId("h-1")
                .type(TransactionType.buy)
                .date(LocalDate.of(2026, 10, 20))
                .quantity(new BigDecimal("2.5"))
                .price(new BigDecimal("18"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("46.84"))
                .source("dca")
                .dcaPlanId("p-1")
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("100"))
                .build();

        com.fundtracker.model.entity.DcaPlan plan = com.fundtracker.model.entity.DcaPlan.builder()
                .id("p-1")
                .totalExecutions(36)
                .totalInvested(new BigDecimal("470"))
                .totalShares(new BigDecimal("100"))
                .nextExecutionDate(LocalDate.of(2026, 11, 5))
                .lastExecutedAt(java.time.LocalDateTime.of(2026, 10, 20, 9, 0))
                .build();

        when(transactionRepository.findByIdAndUserId("tx-1", "user-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.findByHoldingId("h-1")).thenReturn(java.util.List.of());
        doNothing().when(transactionRepository).delete(tx);
        when(dcaPlanRepository.findById("p-1")).thenReturn(Optional.of(plan));
        when(dcaPlanRepository.save(any(com.fundtracker.model.entity.DcaPlan.class)))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.deleteTransaction("tx-1", "user-1");

        assertEquals(35, plan.getTotalExecutions());
        assertEquals(0, new BigDecimal("423.16").compareTo(plan.getTotalInvested()));
        assertEquals(0, new BigDecimal("97.5").compareTo(plan.getTotalShares()));
        // last_executed_at / next_execution_date 不回退
        assertEquals(LocalDate.of(2026, 11, 5), plan.getNextExecutionDate());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 20, 9, 0), plan.getLastExecutedAt());
        verify(dcaPlanRepository).save(plan);
    }

    @Test
    @DisplayName("删除 dca 交易且计划计数不足以回退 → 钳 0 防负（P1-20）")
    void deleteTransaction_dca_countersClampedAtZero() {
        Transaction tx = Transaction.builder()
                .id("tx-1")
                .holdingId("h-1")
                .type(TransactionType.buy)
                .date(LocalDate.of(2026, 10, 20))
                .quantity(new BigDecimal("2.5"))
                .price(new BigDecimal("18"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("46.84"))
                .source("dca")
                .dcaPlanId("p-1")
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("100"))
                .build();

        com.fundtracker.model.entity.DcaPlan plan = com.fundtracker.model.entity.DcaPlan.builder()
                .id("p-1")
                .totalExecutions(0)
                .totalInvested(BigDecimal.ZERO)
                .totalShares(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByIdAndUserId("tx-1", "user-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.findByHoldingId("h-1")).thenReturn(java.util.List.of());
        doNothing().when(transactionRepository).delete(tx);
        when(dcaPlanRepository.findById("p-1")).thenReturn(Optional.of(plan));
        when(dcaPlanRepository.save(any(com.fundtracker.model.entity.DcaPlan.class)))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.deleteTransaction("tx-1", "user-1");

        assertEquals(0, plan.getTotalExecutions());
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.getTotalInvested()));
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.getTotalShares()));
    }

    @Test
    @DisplayName("删除非 dca 交易 → 不触碰计划仓库（P1-20）")
    void deleteTransaction_manual_doesNotTouchPlan() {
        Transaction tx = Transaction.builder()
                .id("tx-1")
                .holdingId("h-1")
                .type(TransactionType.buy)
                .date(LocalDate.of(2026, 10, 20))
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("1000.00"))
                .source("manual")
                .dcaPlanId(null)
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .shares(new BigDecimal("1000"))
                .build();

        when(transactionRepository.findByIdAndUserId("tx-1", "user-1")).thenReturn(Optional.of(tx));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        when(transactionRepository.findByHoldingId("h-1")).thenReturn(java.util.List.of());
        doNothing().when(transactionRepository).delete(tx);

        transactionService.deleteTransaction("tx-1", "user-1");

        verifyNoInteractions(dcaPlanRepository);
    }

    @Test
    @DisplayName("非迁入持仓编辑卖单超额 → 抛业务异常而非静默钳 0（P1-27）")
    void updateTransaction_nonMigrated_sellOverrun_rejected() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costEstimated(false)
                .shares(new BigDecimal("500"))
                .cost(new BigDecimal("5000"))
                .build();
        Transaction sell = Transaction.builder()
                .id("tx-s").holdingId("h-1").userId("user-1")
                .type(TransactionType.sell)
                .date(LocalDate.of(2026, 10, 20))
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("1000.00"))
                .source("manual")
                .build();
        UpdateTransactionReq req = new UpdateTransactionReq();
        req.setQuantity(new BigDecimal("700"));

        when(transactionRepository.findByIdAndUserId("tx-s", "user-1")).thenReturn(Optional.of(sell));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.updateTransaction("tx-s", req, "user-1"));

        // 报错文案必须带可卖份额，而非静默钳 0
        assertTrue(ex.getMessage().contains("超过可卖份额"), "实际文案: " + ex.getMessage());
        assertEquals(0, new BigDecimal("500").compareTo(holding.getShares()), "校验失败时份额不得被改动");
        assertEquals(0, new BigDecimal("5000").compareTo(holding.getCost()));
    }

    @Test
    @DisplayName("非迁入持仓删除卖单 → 份额从交易流重算恢复，不被静默钳 0（P1-27）")
    void deleteTransaction_nonMigrated_sellRestoresShares() {
        Transaction sell = Transaction.builder()
                .id("tx-s")
                .holdingId("h-1")
                .type(TransactionType.sell)
                .date(LocalDate.of(2026, 10, 20))
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("5000.00"))
                .source("manual")
                .build();
        Transaction buy = Transaction.builder()
                .id("tx-b")
                .holdingId("h-1")
                .type(TransactionType.buy)
                .date(LocalDate.of(2026, 1, 10))
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .total(new BigDecimal("10000.00"))
                .source("manual")
                .build();

        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costEstimated(false)
                .shares(new BigDecimal("500"))
                .build();

        when(transactionRepository.findByIdAndUserId("tx-s", "user-1")).thenReturn(Optional.of(sell));
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
        // 删除卖单后，交易流里只剩这笔买入
        when(transactionRepository.findByHoldingId("h-1")).thenReturn(java.util.List.of(buy));
        doNothing().when(transactionRepository).delete(sell);

        transactionService.deleteTransaction("tx-s", "user-1");

        assertEquals(0, new BigDecimal("1000").compareTo(holding.getShares()));
    }

    @Test
    @DisplayName("updateTransaction: 迁入持仓只改日期 → 份额/成本/净投入都不变（V-06）")
    void updateTransaction_migratedDateOnlyKeepsNumbers() {
        Holding holding = Holding.builder()
                .id("h-btc").type(HoldingType.crypto).code("BTC")
                .shares(new java.math.BigDecimal("0.01139738"))
                .cost(new java.math.BigDecimal("6186.81"))
                .costPerShare(new java.math.BigDecimal("542827.3239"))
                .netInvestment(new java.math.BigDecimal("6186.81"))
                .costEstimated(true)
                .build();
        Transaction tx = Transaction.builder()
                .id("tx-1").holdingId("h-btc").userId("u1")
                .type(TransactionType.buy)
                .date(LocalDate.of(2026, 10, 29))
                .quantity(new java.math.BigDecimal("0.00008648"))
                .price(new java.math.BigDecimal("541671.9582"))
                .fee(java.math.BigDecimal.ZERO)
                .total(new java.math.BigDecimal("46.84"))
                .build();
        UpdateTransactionReq req = new UpdateTransactionReq();
        req.setDate("2026-10-28");

        org.mockito.Mockito.when(transactionRepository.findByIdAndUserId("tx-1", "u1"))
                .thenReturn(java.util.Optional.of(tx));
        org.mockito.Mockito.when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-btc", "u1"))
                .thenReturn(java.util.Optional.of(holding));
        org.mockito.Mockito.when(transactionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(holdingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.updateTransaction("tx-1", req, "u1");

        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("0.01139738").compareTo(holding.getShares()),
                "改动日期不应影响迁入份额");
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("6186.81").compareTo(holding.getCost()),
                "改动日期不应影响迁入成本");
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("6186.81").compareTo(holding.getNetInvestment()),
                "改动日期不应影响迁入净投入");
    }

    @Test
    @DisplayName("迁入持仓卖出后编辑卖价 → 份额与成本都不变（E2-23）")
    void migratedPartialSell_editPrice_keepsCost() {
        Holding holding = Holding.builder()
                .id("h-btc").type(HoldingType.crypto).code("BTC")
                .shares(new java.math.BigDecimal("0.005"))
                .cost(new java.math.BigDecimal("2714.14"))
                .costPerShare(new java.math.BigDecimal("542827.00"))
                .netInvestment(new java.math.BigDecimal("2714.14"))
                .costEstimated(true)
                .build();
        Transaction tx = Transaction.builder()
                .id("tx-s").holdingId("h-btc").userId("u1")
                .type(TransactionType.sell)
                .date(LocalDate.of(2026, 10, 29))
                .quantity(new java.math.BigDecimal("0.005"))
                .price(new java.math.BigDecimal("600000"))
                .fee(java.math.BigDecimal.ZERO)
                .total(new java.math.BigDecimal("6000"))
                .build();
        UpdateTransactionReq req = new UpdateTransactionReq();
        req.setPrice(new java.math.BigDecimal("610000"));

        org.mockito.Mockito.when(transactionRepository.findByIdAndUserId("tx-s", "u1"))
                .thenReturn(java.util.Optional.of(tx));
        org.mockito.Mockito.when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-btc", "u1"))
                .thenReturn(java.util.Optional.of(holding));
        org.mockito.Mockito.when(transactionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(holdingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.updateTransaction("tx-s", req, "u1");

        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("0.005").compareTo(holding.getShares()),
                "编辑卖价不应改变剩余份额");
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("2714.14").compareTo(holding.getCost()),
                "编辑卖价不应改变剩余成本（成本按每份成本扣减，与卖价无关）");
        org.junit.jupiter.api.Assertions.assertEquals(0,
                holding.getCost().compareTo(holding.getNetInvestment()),
                "净投入必须始终等于成本");
    }

    @Test
    @DisplayName("迁入持仓清仓后删除卖单 → 份额与成本应完整恢复（E2-24 守门）")
    void migratedClearThenDeleteSell_restoresCost() {
        // 清仓后的持仓状态：shares/cost/net 均为 0，但保留最后已知的每份成本
        Holding holding = Holding.builder()
                .id("h-btc").type(HoldingType.crypto).code("BTC")
                .shares(java.math.BigDecimal.ZERO)
                .cost(java.math.BigDecimal.ZERO)
                .costPerShare(new java.math.BigDecimal("542827.3239"))
                .netInvestment(java.math.BigDecimal.ZERO)
                .costEstimated(true)
                .build();
        Transaction sell = Transaction.builder()
                .id("tx-s").holdingId("h-btc").userId("u1")
                .type(TransactionType.sell)
                .date(LocalDate.of(2026, 10, 29))
                .quantity(new java.math.BigDecimal("0.01139738"))
                .price(new java.math.BigDecimal("534672.69"))
                .fee(java.math.BigDecimal.ZERO)
                .total(new java.math.BigDecimal("6093.87"))
                .build();
        org.mockito.Mockito.when(transactionRepository.findByIdAndUserId("tx-s", "u1"))
                .thenReturn(java.util.Optional.of(sell));
        org.mockito.Mockito.when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-btc", "u1"))
                .thenReturn(java.util.Optional.of(holding));
        org.mockito.Mockito.when(holdingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.deleteTransaction("tx-s", "u1");

        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("0.01139738").compareTo(holding.getShares()),
                "删除卖单后份额应恢复");
        // cost = 最后已知 cps × 数量，允许 1 分钱四舍五入误差
        org.junit.jupiter.api.Assertions.assertTrue(
                holding.getCost().subtract(new java.math.BigDecimal("6186.81")).abs()
                        .compareTo(new java.math.BigDecimal("0.01")) <= 0,
                "删除卖单后成本应恢复约 6186.81，实际 " + holding.getCost());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                holding.getCost().compareTo(holding.getNetInvestment()),
                "净投入必须与成本同步恢复");
    }

    @Test
    @DisplayName("经 updateTransaction 清仓 → 保留 cps → 再删卖单可恢复成本（E2-24 守门·update 路径）")
    void migratedClearViaUpdate_keepsCps() {
        // 迁入持仓：原 0.01，已卖 0.005，剩 0.005
        Holding holding = Holding.builder()
                .id("h-btc").type(HoldingType.crypto).code("BTC")
                .shares(new java.math.BigDecimal("0.005"))
                .cost(new java.math.BigDecimal("2714.14"))
                .costPerShare(new java.math.BigDecimal("542827.3239"))
                .netInvestment(new java.math.BigDecimal("2714.14"))
                .costEstimated(true)
                .build();
        Transaction sell = Transaction.builder()
                .id("tx-s").holdingId("h-btc").userId("u1")
                .type(TransactionType.sell)
                .date(LocalDate.of(2026, 10, 29))
                .quantity(new java.math.BigDecimal("0.005"))
                .price(new java.math.BigDecimal("534672.69"))
                .fee(java.math.BigDecimal.ZERO)
                .total(new java.math.BigDecimal("2673.36"))
                .build();
        // 把卖单数量改成 0.01（卖掉剩余全部）→ 触发清仓分支
        UpdateTransactionReq req = new UpdateTransactionReq();
        req.setQuantity(new java.math.BigDecimal("0.01"));

        org.mockito.Mockito.when(transactionRepository.findByIdAndUserId("tx-s", "u1"))
                .thenReturn(java.util.Optional.of(sell));
        org.mockito.Mockito.when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-btc", "u1"))
                .thenReturn(java.util.Optional.of(holding));
        org.mockito.Mockito.when(transactionRepository.save(org.mockito.Mockito.any()))
                .thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(holdingRepository.save(org.mockito.Mockito.any()))
                .thenAnswer(i -> i.getArgument(0));

        transactionService.updateTransaction("tx-s", req, "u1");

        org.junit.jupiter.api.Assertions.assertEquals(0,
                java.math.BigDecimal.ZERO.compareTo(holding.getShares()), "清仓后份额应为 0");
        org.junit.jupiter.api.Assertions.assertEquals(0,
                java.math.BigDecimal.ZERO.compareTo(holding.getCost()), "清仓后成本应为 0");
        // 关键：每份成本必须保留，否则后续删除卖单无法恢复成本
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("542827.3239").compareTo(holding.getCostPerShare()),
                "清仓后必须保留最后已知每份成本（E2-24）");
    }

}
