package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.AnnualizedReturnDTO;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnualizedReturnServiceTest {

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private AnnualizedReturnService annualizedReturnService;

    @BeforeEach
    void setUp() {
        annualizedReturnService = new AnnualizedReturnService(holdingRepository, transactionRepository);
    }

    @Nested
    @DisplayName("getAnnualizedReturn 年化收益率计算")
    class GetAnnualizedReturn {

        @Test
        @DisplayName("非目标类别（assetCategory=null）→ 返回 null 年化")
        void nonTargetCategory() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory(null)
                    .marketValue(new BigDecimal("10000"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
            assertNull(result.getHoldingDays());
            assertNull(result.getIrr());
        }

        @Test
        @DisplayName("非目标类别（assetCategory=其他值）→ 返回 null 年化")
        void nonTargetCategoryOther() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("crypto")
                    .marketValue(new BigDecimal("10000"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
        }

        @Test
        @DisplayName("持仓不存在 → 抛异常")
        void holdingNotFound() {
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-404", "user-1"))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> annualizedReturnService.getAnnualizedReturn("h-404", "user-1"));
        }

        @Test
        @DisplayName("无交易记录 → 返回 null 年化")
        void noTransactions() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("us_stock")
                    .marketValue(new BigDecimal("10000"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(List.of());

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
            assertNull(result.getHoldingDays());
        }

        @Test
        @DisplayName("只有 1 笔交易 → 返回 null 年化（IRR 边界：少于 2 笔）")
        void singleTransaction() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("us_stock")
                    .marketValue(new BigDecimal("12000"))
                    .netInvestment(new BigDecimal("10000"))
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(30))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
            assertNull(result.getIrr());
            assertNotNull(result.getHoldingDays());
        }

        @Test
        @DisplayName("持有不足 7 天 → 返回 null 年化（三层保护：天数阈值）")
        void holdingDaysLessThan7() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("gold")
                    .marketValue(new BigDecimal("12000"))
                    .netInvestment(new BigDecimal("10000"))
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(3))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();
            Transaction sellTx = Transaction.builder()
                    .id("tx-2").holdingId("h-1")
                    .type(TransactionType.sell)
                    .date(LocalDate.now().minusDays(1))
                    .total(new BigDecimal("5000")).quantity(new BigDecimal("50"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx, sellTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
            assertEquals(3, result.getHoldingDays());
        }

        @Test
        @DisplayName("净投资为 0 → 返回 null 年化（IRR 边界：costBasis=0）")
        void zeroCostBasis() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("dividend")
                    .marketValue(new BigDecimal("12000"))
                    .netInvestment(BigDecimal.ZERO)
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(365))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();
            Transaction sellTx = Transaction.builder()
                    .id("tx-2").holdingId("h-1")
                    .type(TransactionType.sell)
                    .date(LocalDate.now().minusDays(30))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx, sellTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            assertNull(result.getAnnualizedReturn());
            assertNull(result.getIrr());
        }

        @Test
        @DisplayName("IRR 未收敛 → 返回 null 年化（三层保护：收敛检查）")
        void irrNotConverging() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("us_stock")
                    .marketValue(BigDecimal.ZERO)
                    .netInvestment(new BigDecimal("10000"))
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(365))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            // 只有 1 笔交易（少于 2 笔），所以走的是"少于 2 笔"分支
            assertNull(result.getAnnualizedReturn());
            assertNull(result.getIrr());
        }

        @Test
        @DisplayName("年化收益率正常计算 → 返回有效年化值")
        void happyPath() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("us_stock")
                    .marketValue(new BigDecimal("12000"))
                    .netInvestment(new BigDecimal("10000"))
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(730))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();
            Transaction sellTx = Transaction.builder()
                    .id("tx-2").holdingId("h-1")
                    .type(TransactionType.sell)
                    .date(LocalDate.now().minusDays(365))
                    .total(new BigDecimal("2000")).quantity(new BigDecimal("20"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx, sellTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            // 应该有有效的年化收益率
            assertNotNull(result.getAnnualizedReturn());
            assertNotNull(result.getIrr());
            assertNotNull(result.getHoldingDays());
            assertEquals(730, result.getHoldingDays());
            assertTrue(result.getAnnualizedReturn().compareTo(new BigDecimal("-95")) > 0,
                    "年化收益率应 > -95%");
            assertTrue(result.getAnnualizedReturn().compareTo(new BigDecimal("1000")) < 0,
                    "年化收益率应 < 1000%");
            assertEquals(new BigDecimal("10000"), result.getTotalInvested());
            assertEquals(new BigDecimal("2000"), result.getTotalWithdrawn());
            assertEquals(new BigDecimal("12000"), result.getCurrentValue());
        }

        @Test
        @DisplayName("reinvest 交易不影响现金流 → 净现金流为 0")
        void reinvestDoesNotAffectCashflow() {
            Holding holding = Holding.builder()
                    .id("h-1").userId("user-1").type(HoldingType.fund)
                    .assetCategory("us_stock")
                    .marketValue(new BigDecimal("15000"))
                    .netInvestment(new BigDecimal("10000"))
                    .build();

            Transaction buyTx = Transaction.builder()
                    .id("tx-1").holdingId("h-1")
                    .type(TransactionType.buy)
                    .date(LocalDate.now().minusDays(730))
                    .total(new BigDecimal("10000")).quantity(new BigDecimal("100"))
                    .build();
            Transaction reinvestTx = Transaction.builder()
                    .id("tx-2").holdingId("h-1")
                    .type(TransactionType.reinvest)
                    .date(LocalDate.now().minusDays(365))
                    .total(new BigDecimal("500")).quantity(new BigDecimal("5"))
                    .build();

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1"))
                    .thenReturn(Optional.of(holding));
            when(transactionRepository.findByHoldingId("h-1")).thenReturn(new ArrayList<>(List.of(buyTx, reinvestTx)));

            AnnualizedReturnDTO result = annualizedReturnService.getAnnualizedReturn("h-1", "user-1");

            // reinvest 不计入 totalInvested/totalWithdrawn
            assertEquals(new BigDecimal("10000"), result.getTotalInvested());
            assertEquals(BigDecimal.ZERO, result.getTotalWithdrawn());
        }
    }
}