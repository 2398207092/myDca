package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓级快照表，每 5 天为每个有效持仓生成一条记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "holding_snapshots", indexes = {
    @Index(name = "idx_holding_snapshots_holding_id", columnList = "holdingId"),
    @Index(name = "idx_holding_snapshots_date", columnList = "snapshotDate")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_holding_date", columnNames = {"holdingId", "snapshotDate"})
})
public class HoldingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String holdingId;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    /** 当时市值 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal marketValue;

    /** 当时份额。P1-19：18,4 会把 BTC 份额 0.01139738 截断成 0.0115（+0.9%），放宽到 24,8。
     *  历史截断值不回填（回填当前份额反而伪造历史），自部署起新快照为 8 位。 */
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal shares;

    /** 当时累计投入成本（净投入 = 总买入 - 总卖出） */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal costBasis;

    /** 盈亏金额 = marketValue - costBasis */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal profitLoss;

    /** 盈亏百分比 = profitLoss / costBasis * 100 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal profitLossPct;

    /** 占总资产百分比 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pctOfTotal;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
