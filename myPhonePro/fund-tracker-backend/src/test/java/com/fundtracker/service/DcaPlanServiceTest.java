package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.DcaFrequency;
import com.fundtracker.model.enums.DcaPlanStatus;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DcaPlanServiceTest {

    @Mock private DcaPlanRepository dcaPlanRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionService transactionService;
    @Mock private FundNavScrapeService fundNavScrapeService;
    @Mock private TradingCalendar tradingCalendar;

    private DcaPlanService dcaPlanService;

    @BeforeEach
    void setUp() {
        dcaPlanService = new DcaPlanService(
                dcaPlanRepository, holdingRepository,
                transactionService, fundNavScrapeService,
                tradingCalendar
        );
    }

    @Nested
    @DisplayName("inferTradingMarket 静态方法")
    class InferTradingMarket {

        @Test
        @DisplayName("美股返回 'us'")
        void usStock() {
            Holding h = Holding.builder().type(HoldingType.美股).build();
            assertEquals("us", DcaPlanService.inferTradingMarket(h));
        }

        @Test
        @DisplayName("基金返回 'china'")
        void fund() {
            Holding h = Holding.builder().type(HoldingType.fund).build();
            assertEquals("china", DcaPlanService.inferTradingMarket(h));
        }

        @Test
        @DisplayName("A股返回 'china'")
        void aStock() {
            Holding h = Holding.builder().type(HoldingType.A股).build();
            assertEquals("china", DcaPlanService.inferTradingMarket(h));
        }
    }

    @Nested
    @DisplayName("calculateBudget 预算计算")
    class CalculateBudget {

        @Test
        @DisplayName("无活跃计划 → 返回零预算")
        void noActivePlans() {
            when(dcaPlanRepository.findByUserId("user-1")).thenReturn(List.of());

            DcaBudgetVO result = dcaPlanService.calculateBudget(2026, 8, "user-1");

            assertEquals("2026-08", result.getMonth());
            assertEquals(0, result.getTradingDays());
            assertEquals(BigDecimal.ZERO, result.getTotalAmount());
            assertTrue(result.getPlans().isEmpty());
        }

        @Test
        @DisplayName("有活跃计划 → 正确计算预算")
        void withActivePlans() {
            Holding holding = Holding.builder().id("h-1").name("测试基金").type(HoldingType.fund).build();
            DcaPlan plan = DcaPlan.builder()
                    .id("p-1").holdingId("h-1").userId("user-1")
                    .amount(new BigDecimal("100")).frequency(DcaFrequency.daily)
                    .status(DcaPlanStatus.active).tradingMarket("china")
                    .build();

            when(dcaPlanRepository.findByUserId("user-1")).thenReturn(List.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
            when(tradingCalendar.countTradingDaysInMonth(2026, 8, "china")).thenReturn(21);

            DcaBudgetVO result = dcaPlanService.calculateBudget(2026, 8, "user-1");

            // daily 频率 × 21 个交易日 = 2100
            assertEquals(21, result.getTradingDays());
            assertEquals(new BigDecimal("2100"), result.getTotalAmount());
            assertEquals(1, result.getPlans().size());
            assertEquals("测试基金", result.getPlans().get(0).getHoldingName());
            assertEquals(21, result.getPlans().get(0).getExecutions());
        }
    }

    @Nested
    @DisplayName("createPlan 创建定投计划")
    class CreatePlan {

        @Test
        @DisplayName("创建成功 → 返回 DcaPlanVO")
        void success() {
            Holding holding = Holding.builder().id("h-1").name("测试基金").type(HoldingType.fund).build();
            CreateDcaPlanReq req = new CreateDcaPlanReq();
            req.setHoldingId("h-1");
            req.setAmount(new BigDecimal("500"));
            req.setFrequency("weekly");
            req.setDay(1);

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
            when(tradingCalendar.nextTradingDay(any(LocalDate.class), eq("china"))).thenAnswer(i -> i.getArgument(0));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));

            DcaPlanVO result = dcaPlanService.createPlan(req, "user-1");

            assertEquals("h-1", result.getHoldingId());
            assertEquals("测试基金", result.getHoldingName());
            assertEquals(new BigDecimal("500"), result.getAmount());
            assertEquals("weekly", result.getFrequency());
            assertEquals("active", result.getStatus());
        }

        @Test
        @DisplayName("持仓不存在 → 抛异常")
        void holdingNotFound() {
            CreateDcaPlanReq req = new CreateDcaPlanReq();
            req.setHoldingId("h-404");

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-404", "user-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> dcaPlanService.createPlan(req, "user-1"));
        }

        @Test
        @DisplayName("频率值无效 → 抛异常")
        void invalidFrequency() {
            Holding holding = Holding.builder().id("h-1").name("测试基金").type(HoldingType.fund).build();
            CreateDcaPlanReq req = new CreateDcaPlanReq();
            req.setHoldingId("h-1");
            req.setAmount(new BigDecimal("500"));
            req.setFrequency("invalid_freq");

            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));

            assertThrows(BusinessException.class, () -> dcaPlanService.createPlan(req, "user-1"));
        }
    }

    @Nested
    @DisplayName("listPlans 查询计划列表")
    class ListPlans {

        @Test
        @DisplayName("指定 holdingId → 过滤查询")
        void filterByHoldingId() {
            DcaPlan plan = DcaPlan.builder().id("p-1").holdingId("h-1").frequency(DcaFrequency.monthly).status(DcaPlanStatus.active).build();
            when(dcaPlanRepository.findByHoldingIdAndUserIdOrderByCreatedAtDesc("h-1", "user-1"))
                    .thenReturn(List.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.empty());

            List<DcaPlanVO> result = dcaPlanService.listPlans("h-1", "user-1");

            assertEquals(1, result.size());
            assertEquals("p-1", result.get(0).getId());
        }

        @Test
        @DisplayName("holdingId 为空 → 查全部")
        void allPlans() {
            when(dcaPlanRepository.findByUserId("user-1")).thenReturn(List.of());

            List<DcaPlanVO> result = dcaPlanService.listPlans(null, "user-1");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getPlan 查询单个计划")
    class GetPlan {

        @Test
        @DisplayName("计划存在 → 返回 VO")
        void planFound() {
            DcaPlan plan = DcaPlan.builder().id("p-1").holdingId("h-1").frequency(DcaFrequency.monthly).status(DcaPlanStatus.active).build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.empty());

            DcaPlanVO result = dcaPlanService.getPlan("p-1", "user-1");

            assertEquals("p-1", result.getId());
        }

        @Test
        @DisplayName("计划不存在 → 抛异常")
        void planNotFound() {
            when(dcaPlanRepository.findByIdAndUserId("p-404", "user-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> dcaPlanService.getPlan("p-404", "user-1"));
        }
    }

    @Nested
    @DisplayName("updatePlan 更新计划")
    class UpdatePlan {

        @Test
        @DisplayName("更新金额 → 成功")
        void updateAmount() {
            DcaPlan plan = DcaPlan.builder().id("p-1").amount(new BigDecimal("500")).frequency(DcaFrequency.monthly).status(DcaPlanStatus.active).build();
            UpdateDcaPlanReq req = new UpdateDcaPlanReq();
            req.setAmount(new BigDecimal("1000"));

            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse(any(), eq("user-1"))).thenReturn(Optional.empty());

            DcaPlanVO result = dcaPlanService.updatePlan("p-1", req, "user-1");

            assertEquals(new BigDecimal("1000"), result.getAmount());
        }

        @Test
        @DisplayName("暂停计划 → status 变为 paused")
        void pausePlan() {
            DcaPlan plan = DcaPlan.builder().id("p-1").status(DcaPlanStatus.active).frequency(DcaFrequency.monthly).build();
            UpdateDcaPlanReq req = new UpdateDcaPlanReq();
            req.setStatus("paused");

            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse(any(), eq("user-1"))).thenReturn(Optional.empty());

            DcaPlanVO result = dcaPlanService.updatePlan("p-1", req, "user-1");

            assertEquals("paused", result.getStatus());
        }

        @Test
        @DisplayName("恢复计划 → 重新计算下次执行日")
        void resumePlan() {
            DcaPlan plan = DcaPlan.builder().id("p-1").status(DcaPlanStatus.paused).tradingMarket("china").frequency(DcaFrequency.monthly).build();
            UpdateDcaPlanReq req = new UpdateDcaPlanReq();
            req.setStatus("active");

            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(tradingCalendar.nextTradingDay(any(LocalDate.class), eq("china"))).thenReturn(LocalDate.of(2026, 8, 3));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse(any(), eq("user-1"))).thenReturn(Optional.empty());

            DcaPlanVO result = dcaPlanService.updatePlan("p-1", req, "user-1");

            assertEquals("active", result.getStatus());
            assertEquals(LocalDate.of(2026, 8, 3), result.getNextExecutionDate());
        }

        @Test
        @DisplayName("终止计划 → 设置 endedAt")
        void endPlan() {
            DcaPlan plan = DcaPlan.builder().id("p-1").status(DcaPlanStatus.active).frequency(DcaFrequency.monthly).build();
            UpdateDcaPlanReq req = new UpdateDcaPlanReq();
            req.setStatus("ended");

            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse(any(), eq("user-1"))).thenReturn(Optional.empty());

            DcaPlanVO result = dcaPlanService.updatePlan("p-1", req, "user-1");

            assertEquals("ended", result.getStatus());
            assertEquals(LocalDate.now(), result.getEndedAt());
        }

        @Test
        @DisplayName("计划不存在 → 抛异常")
        void planNotFound() {
            when(dcaPlanRepository.findByIdAndUserId("p-404", "user-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> dcaPlanService.updatePlan("p-404", new UpdateDcaPlanReq(), "user-1"));
        }
    }

    @Nested
    @DisplayName("deletePlan 删除计划")
    class DeletePlan {

        @Test
        @DisplayName("删除成功")
        void success() {
            DcaPlan plan = DcaPlan.builder().id("p-1").build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            doNothing().when(dcaPlanRepository).delete(plan);

            dcaPlanService.deletePlan("p-1", "user-1");

            verify(dcaPlanRepository).delete(plan);
        }

        @Test
        @DisplayName("计划不存在 → 抛异常")
        void planNotFound() {
            when(dcaPlanRepository.findByIdAndUserId("p-404", "user-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> dcaPlanService.deletePlan("p-404", "user-1"));
        }
    }

    @Nested
    @DisplayName("executePlan 执行定投")
    class ExecutePlan {

        @Test
        @DisplayName("执行成功 → 创建交易并更新统计")
        void success() {
            Holding holding = Holding.builder()
                    .id("h-1").name("测试基金").code("000001")
                    .type(HoldingType.fund).build();
            DcaPlan plan = DcaPlan.builder()
                    .id("p-1").holdingId("h-1").userId("user-1")
                    .amount(new BigDecimal("500")).frequency(DcaFrequency.monthly)
                    .status(DcaPlanStatus.active).tradingMarket("china")
                    .totalInvested(BigDecimal.ZERO).totalShares(BigDecimal.ZERO)
                    .totalExecutions(0).nextExecutionDate(LocalDate.of(2026, 8, 3))
                    .build();

            FundNavScrapeService.LatestNavResult nav =
                    new FundNavScrapeService.LatestNavResult(new BigDecimal("2.5"), LocalDate.of(2026, 7, 31), 1);
            TransactionDTO txDto = new TransactionDTO();
            txDto.setId("tx-1");

            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
            when(fundNavScrapeService.getLatestNavBefore("000001", LocalDate.now())).thenReturn(nav);
            when(transactionService.createTransaction(any(CreateTransactionReq.class), eq("user-1"))).thenReturn(txDto);
            when(tradingCalendar.nextTradingDay(any(LocalDate.class), eq("china")))
                    .thenReturn(LocalDate.of(2026, 9, 1));
            when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(i -> i.getArgument(0));

            DcaExecutionResultVO result = dcaPlanService.executePlan("p-1", "user-1");

            // 份额 = 500 / 2.5 = 200
            assertEquals("tx-1", result.getTransactionId());
            assertEquals(new BigDecimal("500"), result.getAmount());
            assertEquals(0, new BigDecimal("200").compareTo(result.getQuantity()));
            assertEquals(new BigDecimal("2.5"), result.getNavPrice());

            // 计划统计更新
            assertEquals(new BigDecimal("500"), plan.getTotalInvested());
            assertEquals(0, new BigDecimal("200").compareTo(plan.getTotalShares()));
            assertEquals(1, plan.getTotalExecutions());
            assertEquals(LocalDate.of(2026, 9, 1), plan.getNextExecutionDate());
        }

        @Test
        @DisplayName("计划已暂停 → 抛异常")
        void planPaused() {
            DcaPlan plan = DcaPlan.builder().id("p-1").status(DcaPlanStatus.paused).build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));

            assertThrows(BusinessException.class, () -> dcaPlanService.executePlan("p-1", "user-1"));
        }

        @Test
        @DisplayName("计划已终止 → 抛异常")
        void planEnded() {
            DcaPlan plan = DcaPlan.builder().id("p-1").status(DcaPlanStatus.ended).build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));

            assertThrows(BusinessException.class, () -> dcaPlanService.executePlan("p-1", "user-1"));
        }

        @Test
        @DisplayName("持仓不存在 → 抛异常")
        void holdingNotFound() {
            DcaPlan plan = DcaPlan.builder().id("p-1").holdingId("h-404").status(DcaPlanStatus.active).build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-404", "user-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> dcaPlanService.executePlan("p-1", "user-1"));
        }

        @Test
        @DisplayName("无法获取净值 → 抛异常")
        void noNavData() {
            Holding holding = Holding.builder().id("h-1").code("000001").type(HoldingType.fund).build();
            DcaPlan plan = DcaPlan.builder().id("p-1").holdingId("h-1").status(DcaPlanStatus.active).build();
            when(dcaPlanRepository.findByIdAndUserId("p-1", "user-1")).thenReturn(Optional.of(plan));
            when(holdingRepository.findByIdAndUserIdAndDeletedFalse("h-1", "user-1")).thenReturn(Optional.of(holding));
            when(fundNavScrapeService.getLatestNavBefore("000001", LocalDate.now())).thenReturn(null);

            assertThrows(BusinessException.class, () -> dcaPlanService.executePlan("p-1", "user-1"));
        }
    }
}