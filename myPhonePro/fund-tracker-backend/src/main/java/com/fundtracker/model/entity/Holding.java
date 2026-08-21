package com.fundtracker.model.entity;

import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "holdings", indexes = {
    @Index(name = "idx_holdings_deleted", columnList = "deleted")
})
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CostAlgorithm costAlgorithm;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal shares;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal costPerShare;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal marketValue;

    @Column(precision = 18, scale = 4)
    private BigDecimal latestPrice;

    private LocalDate priceDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal predictedDividend;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal dividendRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal priceDividendRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDividendReceived;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netInvestment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dividendRecoveryRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedRecoveryYears;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal reinvestRecoveryYears;

    @Column(nullable = false, length = 10)
    private String color;

    @Column(nullable = false)
    private boolean deleted;

    @Column(length = 20)
    private String assetCategory; // us_stock / gold / dividend / null=未分类

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean dividendReinvest = false;

    /** 买入手续费率（百分数值，如 0.15 表示 0.15%）。新建标的默认 0.15，可改 */
    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal buyFeeRate = new BigDecimal("0.15");

    /** 卖出手续费率（百分数值，如 0 表示无赎回费）。默认 0，可改 */
    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal sellFeeRate = BigDecimal.ZERO;
}
