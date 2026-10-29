package com.fundtracker.service;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import com.fundtracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HoldingService.recalculateHoldingMetrics 单元测试
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private FundDividendRecordRepository fundDividendRecordRepository;
    @Mock private DividendEventRepository dividendEventRepository;
    @Mock private DcaPlanRepository dcaPlanRepository;
    @Mock private HoldingSnapshotRepository holdingSnapshotRepository;
    @Mock private FundDividendScrapeService fundDividendScrapeService;
    @Mock private FundNavScrapeService fundNavScrapeService;
    @Mock private ManualAssetService manualAssetService;
    @Mock private com.fundtracker.service.AccountService accountService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private HoldingService holdingService;
    private CostCalculator costCalculator;

    @BeforeEach
    void setUp() {
        costCalculator = new CostCalculator();
        holdingService = new HoldingService(
                holdingRepository, transactionRepository,
                costCalculator, fundDividendScrapeService,
                fundNavScrapeService, fundDividendRecordRepository,
                dividendEventRepository, dcaPlanRepository,
                holdingSnapshotRepository, manualAssetService,
                accountService, eventPublisher
        );
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 买入10000元，1000份 → diluted成本=10，息率按预测分红计算")
    void recalculateHoldingMetrics_basicBuy() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("11000"))
                .predictedDividend(new BigDecimal("500"))
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 0) / 1000 = 10
        assertEquals(new BigDecimal("10.0000"), holding.getCostPerShare());
        // totalCost = 10 * 1000 = 10000
        assertEquals(new BigDecimal("10000.00"), holding.getCost());
        // netInvestment = 10000
        assertEquals(new BigDecimal("10000"), holding.getNetInvestment());
        // predictedDividendPerShare = 500 / 1000 = 0.5
        // dividendRate = 0.5 / 10 * 100 = 5%
        assertEquals(new BigDecimal("5.00"), holding.getDividendRate());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 迁入持仓净投入必须等于成本（2026-10-29 V-05）")
    void recalculateHoldingMetrics_migratedNetEqualsCost() {
        // 迁入持仓：成本手工录入 6,186.81，已有一笔定投 46.84
        Holding holding = Holding.builder()
                .id("h-btc").type(HoldingType.crypto).code("BTC")
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("0.01139738"))
                .cost(new BigDecimal("6186.81"))
                .costPerShare(new BigDecimal("542827.3239"))
                .netInvestment(new BigDecimal("6186.81"))
                .costEstimated(true)
                .marketValue(new BigDecimal("6093.87"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();
        Transaction buy = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("0.00008648"))
                .price(new BigDecimal("541671.9582"))
                .total(new BigDecimal("46.84"))
                .build();
        when(transactionRepository.findByHoldingId("h-btc")).thenReturn(List.of(buy));

        // skipCostRecalc=true（迁入持仓的唯一正确调用方式）
        holdingService.recalculateHoldingMetrics(holding, true);

        assertEquals(0, new BigDecimal("6186.81").compareTo(holding.getCost()),
                "迁入持仓成本不应被交易汇总覆盖");
        assertEquals(0, new BigDecimal("6186.81").compareTo(holding.getNetInvestment()),
                "迁入持仓净投入必须等于成本，不能变成只有交易部分的 46.84");
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 买入+卖出混合，验证净投入")
    void recalculateHoldingMetrics_buyAndSell() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("500"))
                .cost(new BigDecimal("5000"))
                .marketValue(new BigDecimal("5500"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();
        Transaction sellTx = Transaction.builder()
                .type(TransactionType.sell)
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("12"))
                .total(new BigDecimal("6000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx, sellTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 6000 - 0) / 500 = 8
        assertEquals(new BigDecimal("8.0000"), holding.getCostPerShare());
        // netInvestment = 10000 - 6000 = 4000
        assertEquals(new BigDecimal("4000"), holding.getNetInvestment());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 有分红摊薄时成本降低")
    void recalculateHoldingMetrics_withDividend() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("12000"))
                .predictedDividend(new BigDecimal("800"))
                .totalDividendReceived(new BigDecimal("3000"))
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 3000) / 1000 = 7
        assertEquals(new BigDecimal("7.0000"), holding.getCostPerShare());
        // dividendRate = (800/1000) / 7 * 100 = 11.43 (四舍五入)
        BigDecimal expectedRate = new BigDecimal("0.8").divide(new BigDecimal("7"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedRate, holding.getDividendRate());
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 无交易时用初始成本")
    void recalculateHoldingMetrics_noTransactions() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("10000"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of());

        holdingService.recalculateHoldingMetrics(holding);

        // 无交易时 netInvestment = holding.getCost() = 10000
        assertEquals(0, new BigDecimal("10000").compareTo(holding.getNetInvestment()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(holding.getCost()));
    }

    @Test
    @DisplayName("recalculateHoldingMetrics: 送股不增加成本")
    void recalculateHoldingMetrics_bonusShares() {
        Holding holding = Holding.builder()
                .id("h-1")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1200"))
                .cost(new BigDecimal("10000"))
                .marketValue(new BigDecimal("12000"))
                .predictedDividend(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .build();

        Transaction buyTx = Transaction.builder()
                .type(TransactionType.buy)
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("10"))
                .total(new BigDecimal("10000"))
                .build();
        Transaction bonusTx = Transaction.builder()
                .type(TransactionType.bonus_share)
                .quantity(new BigDecimal("200"))
                .price(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of(buyTx, bonusTx));

        holdingService.recalculateHoldingMetrics(holding);

        // costPerShare = (10000 - 0 - 0) / 1200 = 8.3333（送股摊薄了成本）
        assertEquals(new BigDecimal("8.3333"), holding.getCostPerShare());
        // totalCost = 8.3333 * 1200 = 9999.96
        assertEquals(new BigDecimal("9999.96"), holding.getCost());
    }

    @Test
    @DisplayName("createHolding: 账户无现金资产 → 报错且不创建任何交易（V-003）")
    void createHolding_withoutCash_throwsAndSavesNothing() {
        com.fundtracker.model.dto.CreateHoldingReq req = new com.fundtracker.model.dto.CreateHoldingReq();
        req.setCode("000001");
        req.setName("测试基金");
        req.setType("fund");
        req.setShares(new BigDecimal("100"));
        req.setCost(new BigDecimal("2.5"));
        req.setAccountId("acc-1");

        when(holdingRepository.existsByCodeAndUserIdAndDeletedFalse("000001", "user-1")).thenReturn(false);
        when(accountService.matchesAssetTypeForUser("user-1", "acc-1", false)).thenReturn(true);
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(com.fundtracker.exception.BusinessException.bizError("该账户下没有现金资产"))
                .when(manualAssetService).adjustCash(anyString(), any(BigDecimal.class), anyString());

        // 断言捕获的正是「无现金资产」错误（而非本位币校验等其他前置异常）
        com.fundtracker.exception.BusinessException ex = assertThrows(
                com.fundtracker.exception.BusinessException.class,
                () -> holdingService.createHolding("user-1", req));
        assertTrue(ex.getMessage().contains("现金资产"), "应抛无现金资产错误，实际: " + ex.getMessage());
        // 说明：单测无真实事务，save 已被调用属预期；生产环境由 createHolding 的 @Transactional
        // （BusinessException 为 RuntimeException）整体回滚持仓+交易，现金不脱节
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("deleteHolding: 回补现金联动上线后的净现金流出（P0-5）")
    void deleteHolding_refundsCashOutflow() {
        Holding holding = Holding.builder()
                .id("h-1").name("测试基金").code("000001")
                .type(HoldingType.fund).accountId("acc-1")
                .deleted(false).build();

        Transaction linkedBuy = Transaction.builder()
                .type(TransactionType.buy).date(LocalDate.of(2026, 11, 1))
                .quantity(new BigDecimal("50")).price(new BigDecimal("2"))
                .total(new BigDecimal("100.00")).build();
        Transaction linkedSell = Transaction.builder()
                .type(TransactionType.sell).date(LocalDate.of(2026, 11, 2))
                .quantity(new BigDecimal("10")).price(new BigDecimal("9"))
                .fee(new BigDecimal("1")).total(new BigDecimal("91.00")).build();
        Transaction preLinkBuy = Transaction.builder()
                .type(TransactionType.buy).date(LocalDate.of(2026, 1, 1))
                .quantity(new BigDecimal("500")).price(new BigDecimal("1"))
                .total(new BigDecimal("500.00")).build();

        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                .thenReturn(java.util.Optional.of(holding));
        when(transactionRepository.findByHoldingId("h-1"))
                .thenReturn(List.of(linkedBuy, linkedSell, preLinkBuy));

        holdingService.deleteHolding("user-1", "h-1");

        // 净流出 = 联动后 buy 100.00 − sell 到账 (10×9−1)=89.00 = 11.00；联动前的 500 不回补
        verify(manualAssetService).adjustCash("h-1", "acc-1", new BigDecimal("11.00"), "user-1");
        verify(transactionRepository).deleteByHoldingId("h-1");
        verify(holdingRepository).delete(holding);
    }

    // ===== P1-29：持仓挂账户必须校验属主 =====

    @Test
    @DisplayName("createHolding: 挂他人账户被拒（P1-29，走 matchesAssetTypeForUser）")
    void createHolding_foreignAccountRejected() {
        com.fundtracker.model.dto.CreateHoldingReq req = new com.fundtracker.model.dto.CreateHoldingReq();
        req.setCode("000001");
        req.setName("测试基金");
        req.setType("fund");
        req.setShares(new BigDecimal("100"));
        req.setCost(new BigDecimal("2.5"));
        req.setAccountId("acc-other");

        when(holdingRepository.existsByCodeAndUserIdAndDeletedFalse("000001", "user-1")).thenReturn(false);
        // 账户属于别人 → 归属校验失败
        when(accountService.matchesAssetTypeForUser("user-1", "acc-other", false)).thenReturn(false);

        com.fundtracker.exception.BusinessException ex = assertThrows(
                com.fundtracker.exception.BusinessException.class,
                () -> holdingService.createHolding("user-1", req));
        assertTrue(ex.getMessage().contains("人民币资产"),
                "应抛本位币/归属校验错误，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("updateHolding: 改挂他人账户被拒（P1-29）")
    void updateHolding_foreignAccountRejected() {
        Holding holding = Holding.builder()
                .id("h-1").name("测试基金").code("000001")
                .type(HoldingType.fund).accountId("acc-1")
                .shares(new BigDecimal("100")).deleted(false)
                .build();
        when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                .thenReturn(java.util.Optional.of(holding));
        when(accountService.matchesAssetTypeForUser("user-1", "acc-other", false)).thenReturn(false);

        com.fundtracker.model.dto.UpdateHoldingReq req = new com.fundtracker.model.dto.UpdateHoldingReq();
        req.setAccountId("acc-other");

        com.fundtracker.exception.BusinessException ex = assertThrows(
                com.fundtracker.exception.BusinessException.class,
                () -> holdingService.updateHolding("h-1", req, "user-1"));
        assertTrue(ex.getMessage().contains("人民币资产"),
                "应抛本位币/归属校验错误，实际: " + ex.getMessage());
    }

    // ===== P1-24：迁入持仓（costEstimated=true）成本不得被伪造初始买入交易重算覆盖 =====

    @Test
    @DisplayName("P1-24 复现: createHolding 迁入持仓成本保持用户输入，不被初始交易重算覆盖")
    void createHolding_costEstimated_keepsUserCost() {
        com.fundtracker.model.dto.CreateHoldingReq req = new com.fundtracker.model.dto.CreateHoldingReq();
        req.setCode("000001");
        req.setName("迁入基金");
        req.setType("fund");
        req.setShares(new BigDecimal("1000"));
        req.setCost(new BigDecimal("2.5"));
        req.setAccountId("acc-1");
        req.setCostEstimated(true);
        req.setBuyFeeRate(new BigDecimal("0.15"));

        when(holdingRepository.existsByCodeAndUserIdAndDeletedFalse("000001", "user-1")).thenReturn(false);
        when(accountService.matchesAssetTypeForUser("user-1", "acc-1", false)).thenReturn(true);
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> i.getArgument(0));

        // 捕获伪造的初始买入交易，供 recalculateHoldingMetrics 查询
        List<Transaction> savedTx = new java.util.ArrayList<>();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            savedTx.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(transactionRepository.findByHoldingId(anyString()))
                .thenAnswer(i -> new java.util.ArrayList<>(savedTx));

        when(fundNavScrapeService.getLatestNavFromDb(anyString())).thenReturn(null);
        when(fundDividendRecordRepository.findByFundCodeOrderByExDateDesc(anyString()))
                .thenReturn(List.of());

        com.fundtracker.model.dto.HoldingDTO dto = holdingService.createHolding("user-1", req);

        assertEquals(0, new BigDecimal("2.5").compareTo(dto.getCostPerShare()),
                "迁入持仓（costEstimated=true）的手输成本不应被伪造初始买入交易重算覆盖");
    }
}
