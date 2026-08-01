package com.fundtracker.integration;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import com.fundtracker.service.SnapshotGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据隔离集成测试：验证 SnapshotGenerationService 在生成快照时
 * 不会误删或误改其他用户的数据。
 * <p>
 * 使用 H2 内存数据库（application-test.yml），无需真实 MySQL。
 */
@SpringBootTest
@ActiveProfiles("test")
class SnapshotGenerationDataIsolationTest {

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private HoldingSnapshotRepository holdingSnapshotRepository;

    @Autowired
    private SnapshotGenerationService snapshotGenerationService;

    @MockBean
    private JavaMailSender javaMailSender;

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    private String holdingAId;
    private String holdingBId;

    @BeforeEach
    void setUp() {
        // 每个测试用例前清理数据库
        holdingSnapshotRepository.deleteAll();
        holdingRepository.deleteAll();

        // 为用户 A 创建持仓
        Holding holdingA = Holding.builder()
                .userId(USER_A)
                .name("用户A持仓")
                .code("CODE_A")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("1000"))
                .costPerShare(new BigDecimal("1.5"))
                .cost(new BigDecimal("1500"))
                .marketValue(new BigDecimal("1600"))
                .predictedDividend(new BigDecimal("50"))
                .dividendRate(new BigDecimal("0.03"))
                .priceDividendRate(new BigDecimal("0.03"))
                .totalDividendReceived(new BigDecimal("30"))
                .netInvestment(new BigDecimal("1500"))
                .dividendRecoveryRate(new BigDecimal("2"))
                .estimatedRecoveryYears(new BigDecimal("5"))
                .reinvestRecoveryYears(new BigDecimal("4"))
                .color("#22C55E")
                .deleted(false)
                .build();
        holdingA = holdingRepository.save(holdingA);
        holdingAId = holdingA.getId();

        // 为用户 B 创建持仓
        Holding holdingB = Holding.builder()
                .userId(USER_B)
                .name("用户B持仓")
                .code("CODE_B")
                .type(HoldingType.fund)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(new BigDecimal("2000"))
                .costPerShare(new BigDecimal("2.0"))
                .cost(new BigDecimal("4000"))
                .marketValue(new BigDecimal("4200"))
                .predictedDividend(new BigDecimal("100"))
                .dividendRate(new BigDecimal("0.025"))
                .priceDividendRate(new BigDecimal("0.025"))
                .totalDividendReceived(new BigDecimal("60"))
                .netInvestment(new BigDecimal("4000"))
                .dividendRecoveryRate(new BigDecimal("1.5"))
                .estimatedRecoveryYears(new BigDecimal("6"))
                .reinvestRecoveryYears(new BigDecimal("5"))
                .color("#3B82F6")
                .deleted(false)
                .build();
        holdingB = holdingRepository.save(holdingB);
        holdingBId = holdingB.getId();
    }

    @Test
    @DisplayName("生成快照时应只删除当前用户的当日快照，不删除其他用户的快照")
    void snapshotGeneration_shouldNotDeleteOtherUserSnapshots() {
        LocalDate today = LocalDate.now();

        // 预先为两个用户创建今日快照
        HoldingSnapshot snapshotA = HoldingSnapshot.builder()
                .holdingId(holdingAId)
                .snapshotDate(today)
                .marketValue(new BigDecimal("1600"))
                .shares(new BigDecimal("1000"))
                .costBasis(new BigDecimal("1500"))
                .profitLoss(new BigDecimal("100"))
                .profitLossPct(new BigDecimal("6.67"))
                .pctOfTotal(new BigDecimal("100"))
                .createdAt(LocalDateTime.now())
                .build();
        holdingSnapshotRepository.save(snapshotA);

        HoldingSnapshot snapshotB = HoldingSnapshot.builder()
                .holdingId(holdingBId)
                .snapshotDate(today)
                .marketValue(new BigDecimal("4200"))
                .shares(new BigDecimal("2000"))
                .costBasis(new BigDecimal("4000"))
                .profitLoss(new BigDecimal("200"))
                .profitLossPct(new BigDecimal("5.00"))
                .pctOfTotal(new BigDecimal("100"))
                .createdAt(LocalDateTime.now())
                .build();
        holdingSnapshotRepository.save(snapshotB);

        // 验证：两个用户各有 1 条快照
        assertEquals(2, holdingSnapshotRepository.count(),
                "初始状态：两个用户应各有 1 条快照");

        // 执行：为用户 A 生成快照（覆盖模式）
        snapshotGenerationService.snapshotAllHoldings(USER_A);

        // 验证 1：用户 B 的快照仍然存在
        List<HoldingSnapshot> userBSnapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingBId, today.minusDays(1));
        assertFalse(userBSnapshots.isEmpty(), "用户 B 的快照应仍然存在");
        assertEquals(1, userBSnapshots.size(), "用户 B 应有 1 条快照（未被删除）");
        assertEquals(0, new BigDecimal("4200").compareTo(userBSnapshots.get(0).getMarketValue()),
                "用户 B 快照的市值应保持不变");

        // 验证 2：用户 A 的快照已被覆盖（新生成）
        List<HoldingSnapshot> userASnapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingAId, today.minusDays(1));
        assertFalse(userASnapshots.isEmpty(), "用户 A 的快照应存在（新生成）");
        // 新快照的市值应等于持仓的当前市值（1600）
        assertEquals(0, new BigDecimal("1600").compareTo(userASnapshots.get(0).getMarketValue()),
                "用户 A 新快照的市值应与持仓市值一致");

        // 验证 3：总快照数 = 用户A新快照(1) + 用户B旧快照(1) = 2
        assertEquals(2, holdingSnapshotRepository.count(),
                "总快照数应保持为 2（用户A覆盖后 1 条 + 用户B保留 1 条）");
    }

    @Test
    @DisplayName("无持仓用户调用快照不应影响其他用户的数据")
    void snapshotGeneration_emptyUser_shouldNotAffectOthers() {
        LocalDate today = LocalDate.now();

        // 为用户 B 创建今日快照
        HoldingSnapshot snapshotB = HoldingSnapshot.builder()
                .holdingId(holdingBId)
                .snapshotDate(today)
                .marketValue(new BigDecimal("4200"))
                .shares(new BigDecimal("2000"))
                .costBasis(new BigDecimal("4000"))
                .profitLoss(new BigDecimal("200"))
                .profitLossPct(new BigDecimal("5.00"))
                .pctOfTotal(new BigDecimal("100"))
                .createdAt(LocalDateTime.now())
                .build();
        holdingSnapshotRepository.save(snapshotB);

        // 执行：为一个不存在的用户生成快照
        snapshotGenerationService.snapshotAllHoldings("non-existent-user");

        // 验证：用户 B 的快照不受影响
        List<HoldingSnapshot> userBSnapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingBId, today.minusDays(1));
        assertFalse(userBSnapshots.isEmpty(), "用户 B 的快照应仍然存在");
        assertEquals(0, new BigDecimal("4200").compareTo(userBSnapshots.get(0).getMarketValue()),
                "用户 B 快照的市值应保持不变");
    }

    @Test
    @DisplayName("多用户数据完全隔离：A 的数据变更不应影响 B 的数据")
    void snapshotGeneration_multiUser_fullIsolation() {
        LocalDate today = LocalDate.now();

        // 为用户 B 创建今日快照
        HoldingSnapshot snapshotB = HoldingSnapshot.builder()
                .holdingId(holdingBId)
                .snapshotDate(today)
                .marketValue(new BigDecimal("4200"))
                .shares(new BigDecimal("2000"))
                .costBasis(new BigDecimal("4000"))
                .profitLoss(new BigDecimal("200"))
                .profitLossPct(new BigDecimal("5.00"))
                .pctOfTotal(new BigDecimal("100"))
                .createdAt(LocalDateTime.now())
                .build();
        holdingSnapshotRepository.save(snapshotB);

        // 执行：为用户 A 生成快照（无持仓快照但会创建新快照）
        snapshotGenerationService.snapshotAllHoldings(USER_A);

        // 验证：总快照数 = 用户A新快照 + 用户B旧快照
        List<HoldingSnapshot> allUserASnapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingAId, today.minusDays(1));
        List<HoldingSnapshot> allUserBSnapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingBId, today.minusDays(1));

        // 用户A 应有1条新快照
        assertEquals(1, allUserASnapshots.size(), "用户 A 应有 1 条快照");
        assertEquals(holdingAId, allUserASnapshots.get(0).getHoldingId());
        assertEquals(today, allUserASnapshots.get(0).getSnapshotDate());

        // 用户B 应有1条旧快照（未被影响）
        assertEquals(1, allUserBSnapshots.size(), "用户 B 应有 1 条快照（未被影响）");
        assertEquals(holdingBId, allUserBSnapshots.get(0).getHoldingId());
        assertEquals(0, new BigDecimal("4200").compareTo(allUserBSnapshots.get(0).getMarketValue()),
                "用户 B 快照市值应保持不变");
    }
}